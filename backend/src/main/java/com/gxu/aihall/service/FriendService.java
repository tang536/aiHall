package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.dto.FriendRequestVO;
import com.gxu.aihall.dto.PublicUserVO;
import com.gxu.aihall.entity.Friendship;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.repository.FriendshipRepository;
import com.gxu.aihall.repository.UserRepository;
import com.gxu.aihall.util.UserPrivacyUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 好友服务：按平台账号（学号）搜索用户、发起/处理好友申请、好友列表维护。
 */
@Service
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserNotificationService userNotificationService;

    public FriendService(FriendshipRepository friendshipRepository, UserRepository userRepository,
                         UserNotificationService userNotificationService) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.userNotificationService = userNotificationService;
    }

    /** 按平台账号（学号）精确匹配，或按姓名模糊搜索；排除自己 */
    public List<PublicUserVO> search(Long selfId, String keyword) {
        List<PublicUserVO> result = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return result;
        String kw = keyword.trim();
        List<User> users = userRepository.findByUsernameContainingOrRealNameContainingOrderByIdAsc(kw, kw);
        Set<Long> friendIds = friendIds(selfId);
        for (User u : users) {
            if (u.getId().equals(selfId)) continue;
            if (u.getStatus() != null && u.getStatus() != 1) continue;
            result.add(UserPrivacyUtil.toPublicVO(u, friendIds.contains(u.getId())));
            if (result.size() >= 20) break;
        }
        return result;
    }

    public PublicUserVO profile(Long selfId, Long targetId) {
        User u = userRepository.findById(targetId).orElse(null);
        if (u == null) return null;
        return UserPrivacyUtil.toPublicVO(u, isFriend(selfId, targetId));
    }

    /** 好友列表（已接受） */
    public List<PublicUserVO> listFriends(Long uid) {
        List<PublicUserVO> result = new ArrayList<>();
        for (Friendship f : friendshipRepository.findAcceptedByUser(uid)) {
            Long otherId = f.getRequesterId().equals(uid) ? f.getAddresseeId() : f.getRequesterId();
            User u = userRepository.findById(otherId).orElse(null);
            if (u == null) continue;
            PublicUserVO vo = UserPrivacyUtil.toPublicVO(u, true);
            // 我给他设置的备注仅自己可见
            if (f.getRequesterId().equals(uid)
                    && f.getRemark() != null && !f.getRemark().isBlank()) {
                vo.setDisplayName(f.getRemark());
            }
            result.add(vo);
        }
        return result;
    }

    /** 收到的好友申请（待处理） */
    public List<FriendRequestVO> incomingRequests(Long uid) {
        List<FriendRequestVO> result = new ArrayList<>();
        for (Friendship f : friendshipRepository.findByAddresseeIdAndStatusOrderByCreateTimeDesc(uid, "PENDING")) {
            result.add(toVO(f, userRepository.findById(f.getRequesterId()).orElse(null), true));
        }
        return result;
    }

    /** 我发出的好友申请 */
    public List<FriendRequestVO> sentRequests(Long uid) {
        List<FriendRequestVO> result = new ArrayList<>();
        for (Friendship f : friendshipRepository.findByRequesterIdAndStatusOrderByCreateTimeDesc(uid, "PENDING")) {
            result.add(toVO(f, userRepository.findById(f.getAddresseeId()).orElse(null), false));
        }
        return result;
    }

    /**
     * 发起好友申请。
     * 支持通过平台账号（学号）或用户 id 定位目标用户；
     * 若对方已向我发出过待处理的申请，则直接互为好友。
     */
    public FriendRequestVO sendRequest(Long selfId, String account, Long targetUserId) {
        User target = null;
        if (targetUserId != null) {
            target = userRepository.findById(targetUserId).orElse(null);
        } else if (account != null && !account.trim().isEmpty()) {
            target = userRepository.findByUsername(account.trim()).orElse(null);
        }
        if (target == null) {
            throw new BizException("未找到该平台账号对应的用户，请确认学号是否正确");
        }
        if (target.getId().equals(selfId)) {
            throw new BizException("不能添加自己为好友");
        }
        if (target.getStatus() != null && target.getStatus() != 1) {
            throw new BizException("该用户账号状态异常，暂时无法添加");
        }

        for (Friendship f : friendshipRepository.findBetween(selfId, target.getId())) {
            if ("ACCEPTED".equals(f.getStatus())) {
                throw new BizException("你们已经是好友了");
            }
            if ("PENDING".equals(f.getStatus())) {
                if (f.getRequesterId().equals(selfId)) {
                    throw new BizException("好友申请已发送，请等待对方确认");
                }
                // 对方已向我发过申请：直接互相成为好友
                f.setStatus("ACCEPTED");
                f.setUpdateTime(LocalDateTime.now());
                friendshipRepository.save(f);
                return toVO(f, target, true);
            }
        }

        Friendship f = new Friendship();
        f.setRequesterId(selfId);
        f.setAddresseeId(target.getId());
        f.setStatus("PENDING");
        f.setCreateTime(LocalDateTime.now());
        f.setUpdateTime(LocalDateTime.now());
        friendshipRepository.save(f);
        // 给对方发送好友申请通知
        try {
            User requester = userRepository.findById(selfId).orElse(null);
            String requesterName = requester != null ? UserPrivacyUtil.toPublicVO(requester).getDisplayName() : "某位同学";
            userNotificationService.notifyFriendRequest(target.getId(), requesterName, f.getId());
        } catch (Exception ignored) {}
        return toVO(f, target, false);
    }

    public void accept(Long selfId, Long requestId) {
        Friendship f = requireIncoming(selfId, requestId);
        f.setStatus("ACCEPTED");
        f.setUpdateTime(LocalDateTime.now());
        friendshipRepository.save(f);
    }

    public void reject(Long selfId, Long requestId) {
        Friendship f = requireIncoming(selfId, requestId);
        f.setStatus("REJECTED");
        f.setUpdateTime(LocalDateTime.now());
        friendshipRepository.save(f);
    }

    /** 删除好友 */
    public void removeFriend(Long selfId, Long otherId) {
        for (Friendship f : friendshipRepository.findBetween(selfId, otherId)) {
            if ("ACCEPTED".equals(f.getStatus())) {
                friendshipRepository.delete(f);
            }
        }
    }

    public boolean isFriend(Long a, Long b) {
        if (a == null || b == null) return false;
        for (Friendship f : friendshipRepository.findBetween(a, b)) {
            if ("ACCEPTED".equals(f.getStatus())) return true;
        }
        return false;
    }

    /** 某用户的全部好友 id */
    public Set<Long> friendIds(Long uid) {
        Set<Long> ids = new HashSet<>();
        if (uid == null) return ids;
        for (Friendship f : friendshipRepository.findAcceptedByUser(uid)) {
            ids.add(f.getRequesterId().equals(uid) ? f.getAddresseeId() : f.getRequesterId());
        }
        return ids;
    }

    private Friendship requireIncoming(Long selfId, Long requestId) {
        Friendship f = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new BizException("好友申请不存在或已处理"));
        if (!f.getAddresseeId().equals(selfId)) {
            throw new BizException("无权处理该好友申请");
        }
        if (!"PENDING".equals(f.getStatus())) {
            throw new BizException("该好友申请已处理");
        }
        return f;
    }

    private FriendRequestVO toVO(Friendship f, User other, boolean incoming) {
        FriendRequestVO vo = new FriendRequestVO();
        vo.setId(f.getId());
        vo.setStatus(f.getStatus());
        vo.setCreateTime(f.getCreateTime());
        vo.setIncoming(incoming);
        vo.setUser(UserPrivacyUtil.toPublicVO(other));
        return vo;
    }
}
