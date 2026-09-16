package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.dto.ConversationVO;
import com.gxu.aihall.dto.PublicUserVO;
import com.gxu.aihall.entity.PrivateMessage;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.repository.PrivateMessageRepository;
import com.gxu.aihall.repository.UserRepository;
import com.gxu.aihall.util.UserPrivacyUtil;
import com.gxu.aihall.ws.ChatBroadcaster;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私聊服务。
 * 设计要点：
 * - REST 是消息的「唯一写入口」（保证一定落库），发送成功后由 ChatBroadcaster 投递给在线接收方；
 *   走 ChatBroadcaster 而不是直接用 ChatSessionRegistry，是为了多实例部署时能把消息广播到
 *   接收方实际连着的那个实例（详见 ChatBroadcaster 的类注释）；
 * - 接收方离线时消息仅落库（isRead=0），上线后通过会话列表 / 历史消息拉取；
 * - 会话 id 由双方 id 升序拼接，保证 a→b 与 b→a 落在同一会话。
 */
@Service
public class PrivateMessageService {

    private final PrivateMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatBroadcaster broadcaster;

    public PrivateMessageService(PrivateMessageRepository messageRepository,
                                 UserRepository userRepository,
                                 ChatBroadcaster broadcaster) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.broadcaster = broadcaster;
    }

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    /**
     * 会话列表：每个对话取最新一条消息 + 我未读的数量。
     * 两条 SQL 完成（每会话最新一条 / 每会话未读数），不再把用户全部消息读进内存分组。
     */
    public List<ConversationVO> conversations(Long userId) {
        List<PrivateMessage> latestList = messageRepository.findLatestPerConversation(userId);

        Map<String, Long> unreadByConv = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadGroupByConversation(userId)) {
            unreadByConv.put((String) row[0], ((Number) row[1]).longValue());
        }

        Map<Long, User> peerCache = new HashMap<>();
        List<ConversationVO> result = new ArrayList<>(latestList.size());
        for (PrivateMessage m : latestList) {
            Long peerId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            ConversationVO vo = new ConversationVO();
            vo.setPeer(UserPrivacyUtil.toPublicVO(
                    peerCache.computeIfAbsent(peerId, id -> userRepository.findById(id).orElse(null))));
            vo.setLastMessage(previewOf(m));
            vo.setLastMsgType(m.getMsgType());
            vo.setLastTime(m.getCreateTime());
            vo.setFromMe(m.getSenderId().equals(userId));
            vo.setUnread(unreadByConv.getOrDefault(m.getConversationId(), 0L));
            result.add(vo);
        }
        return result;
    }

    /**
     * 历史消息（按时间正序返回，默认只取最新一页）。
     * <p>向上翻页传 {@code beforeId}（上一页最小消息 id）。返回的是「最新一页」而非全部，
     * 打开会话即把该会话未读整体标记为已读（用一条 UPDATE，不再逐条 save）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<PrivateMessage> history(Long userId, Long peerId, Long beforeId, Integer size) {
        String convId = PrivateMessage.buildConversationId(userId, peerId);
        if (convId == null) return List.of();

        int limit = (size == null || size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(0, limit);
        List<PrivateMessage> page = (beforeId == null)
                ? messageRepository.findLatestPage(convId, pageable)
                : messageRepository.findPageBefore(convId, beforeId, pageable);

        // 查询是按 id 倒序取最新的，展示需要正序
        List<PrivateMessage> ordered = new ArrayList<>(page);
        java.util.Collections.reverse(ordered);

        messageRepository.markConversationRead(convId, userId);
        return ordered;
    }

    /**
     * 发送私聊消息：先落库，再尽力推送。
     * 消息体在数据中已存在，推送失败不影响消息可靠性。
     */
    @Transactional(rollbackFor = Exception.class)
    public PrivateMessage send(Long senderId, Long receiverId, String content,
                               String msgType, String refType, Long refId) {
        if (receiverId == null) {
            throw new BizException("请选择聊天对象");
        }
        if (senderId.equals(receiverId)) {
            throw new BizException("不能给自己发消息");
        }
        String type = (msgType == null || msgType.isBlank()) ? "TEXT" : msgType;
        boolean isShare = "ITEM".equals(type) || "POST".equals(type);
        if (!isShare && (content == null || content.trim().isEmpty())) {
            throw new BizException("消息内容不能为空");
        }
        if (userRepository.findById(receiverId).isEmpty()) {
            throw new BizException("对方用户不存在");
        }

        PrivateMessage msg = new PrivateMessage();
        msg.setConversationId(PrivateMessage.buildConversationId(senderId, receiverId));
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content == null ? "" : content.trim());
        msg.setMsgType(type);
        msg.setRefType(isShare ? refType : null);
        msg.setRefId(isShare ? refId : null);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        PrivateMessage saved = messageRepository.save(msg);

        pushToReceiver(saved, senderId);
        return saved;
    }

    /** 标记与某人的会话全部已读 */
    @Transactional(rollbackFor = Exception.class)
    public int markRead(Long userId, Long peerId) {
        String convId = PrivateMessage.buildConversationId(userId, peerId);
        if (convId == null) return 0;
        return messageRepository.markConversationRead(convId, userId);
    }

    public long unreadCount(Long userId) {
        return messageRepository.countByReceiverIdAndIsRead(userId, 0);
    }

    /**
     * 投递消息给接收方（在线才收得到）。
     * <p>这里必须用 {@link ChatBroadcaster}：接收方可能连在另一个实例上，本实例的
     * {@code ChatSessionRegistry} 里根本没有它的连接。
     */
    private void pushToReceiver(PrivateMessage msg, Long senderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MESSAGE");
        payload.put("message", toDTO(msg));
        payload.put("from", UserPrivacyUtil.toPublicVO(userRepository.findById(senderId).orElse(null)));
        broadcaster.pushToUser(msg.getReceiverId(), payload);
    }

    /** 对外消息结构（不含任何隐私字段） */
    public Map<String, Object> toDTO(PrivateMessage msg) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", msg.getId());
        dto.put("conversationId", msg.getConversationId());
        dto.put("senderId", msg.getSenderId());
        dto.put("receiverId", msg.getReceiverId());
        dto.put("content", msg.getContent());
        dto.put("msgType", msg.getMsgType());
        dto.put("refType", msg.getRefType());
        dto.put("refId", msg.getRefId());
        dto.put("isRead", msg.getIsRead());
        dto.put("createTime", msg.getCreateTime() == null ? null : msg.getCreateTime().toString());
        return dto;
    }

    private String previewOf(PrivateMessage m) {
        if ("ITEM".equals(m.getMsgType())) return "[商品分享] " + m.getContent();
        if ("POST".equals(m.getMsgType())) return "[帖子分享] " + m.getContent();
        if ("IMAGE".equals(m.getMsgType())) return "[图片]";
        return m.getContent();
    }
}
