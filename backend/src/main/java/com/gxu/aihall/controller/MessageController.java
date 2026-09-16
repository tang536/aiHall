package com.gxu.aihall.controller;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.common.Result;
import com.gxu.aihall.doc.ApiDoc;
import com.gxu.aihall.dto.ConversationVO;
import com.gxu.aihall.entity.PrivateMessage;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.PrivateMessageService;
import com.gxu.aihall.ws.ChatBroadcaster;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 私聊接口。
 * 消息统一通过本控制器的 POST 落库，落库成功后由 WebSocket 推送给在线接收方；
 * 接收方离线时消息留在数据库，上线后通过会话列表 / 历史消息拉取。
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final AuthService authService;
    private final PrivateMessageService messageService;
    private final ChatBroadcaster broadcaster;

    public MessageController(AuthService authService,
                             PrivateMessageService messageService,
                             ChatBroadcaster broadcaster) {
        this.authService = authService;
        this.messageService = messageService;
        this.broadcaster = broadcaster;
    }

    /** 会话列表（含对方信息、最后一条消息、未读数） */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(messageService.conversations(user.getId()));
    }

    /**
     * 与某人的历史消息（默认只返回最新一页，正序）。
     * <p>向上翻页：把上一页最小消息 id 作为 {@code beforeId} 传回来即可。
     * 打开会话会把该会话的未读整体置为已读。
     */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestHeader(value = "Authorization", required = false) String token,
                                                     @RequestParam Long peerId,
                                                     @RequestParam(required = false) Long beforeId,
                                                     @RequestParam(required = false) Integer size) {
        User user = authService.requireLogin(token, "未登录");
        List<Map<String, Object>> list = new ArrayList<>();
        for (PrivateMessage m : messageService.history(user.getId(), peerId, beforeId, size)) {
            list.add(messageService.toDTO(m));
        }
        return Result.success(list);
    }

    /** 发送消息（落库 + 尽力推送） */
    @ApiDoc("发送私聊消息（落库后尽力推送给在线接收方，跨实例经 Redis 广播）")
    @PostMapping
    public Result<Map<String, Object>> send(@RequestHeader(value = "Authorization", required = false) String token,
                                            @RequestBody Map<String, Object> body) {
        User user = authService.requireLogin(token, "请先登录后再发送消息");
        Long peerId = parseLong(body.get("peerId"));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        String msgType = body.get("msgType") == null ? "TEXT" : String.valueOf(body.get("msgType"));
        String refType = body.get("refType") == null ? null : String.valueOf(body.get("refType"));
        Long refId = parseLong(body.get("refId"));
        PrivateMessage saved = messageService.send(user.getId(), peerId, content, msgType, refType, refId);
        return Result.success("已发送", messageService.toDTO(saved));
    }

    /** 标记与某人的会话全部已读 */
    @PostMapping("/read")
    public Result<Map<String, Object>> markRead(@RequestHeader(value = "Authorization", required = false) String token,
                                                @RequestParam Long peerId) {
        User user = authService.requireLogin(token, "未登录");
        int changed = messageService.markRead(user.getId(), peerId);
        return Result.success(Map.of("changed", changed));
    }

    /** 未读总数（用于顶部徽标与 WebSocket 断线时的轮询兜底） */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(Map.of("count", messageService.unreadCount(user.getId())));
    }

    /**
     * WebSocket 投递运行信息（仅管理员可用）。
     * <p>排查「消息落库了但对方没收到」时的第一步：看发送方的 {@code broadcastSent} 有没有涨、
     * 接收方所在实例的 {@code broadcastReceived} 有没有涨。两边都不涨说明 Redis 频道不通。
     */
    @GetMapping("/ws-stats")
    public Result<Map<String, Object>> wsStats(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BizException(403, "无权查看投递信息");
        }
        return Result.success(broadcaster.stats());
    }

    private Long parseLong(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
