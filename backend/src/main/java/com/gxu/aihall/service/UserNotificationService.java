package com.gxu.aihall.service;

import com.gxu.aihall.entity.UserNotification;
import com.gxu.aihall.repository.UserNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人通知服务：好友申请、商品被购买等针对特定用户的提示
 */
@Service
public class UserNotificationService {

    private final UserNotificationRepository repository;

    public UserNotificationService(UserNotificationRepository repository) {
        this.repository = repository;
    }

    /** 发送一条个人通知 */
    @Transactional
    public UserNotification send(Long userId, String type, String title, String content, Long relatedId) {
        if (userId == null) return null;
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setType(type != null ? type : "SYSTEM");
        n.setTitle(title);
        n.setContent(content);
        n.setRelatedId(relatedId);
        n.setIsRead(false);
        n.setCreateTime(LocalDateTime.now());
        return repository.save(n);
    }

    /** 快捷：好友申请通知 */
    public void notifyFriendRequest(Long toUserId, String fromUserName, Long requestId) {
        send(toUserId, "FRIEND_REQUEST",
                "新的好友申请",
                fromUserName + " 向你发送了好友申请，点击查看并处理",
                requestId);
    }

    /** 快捷：商品被购买通知 */
    public void notifyItemPurchased(Long sellerId, String itemTitle, String buyerName, Long orderId) {
        send(sellerId, "ORDER_PURCHASE",
                "商品已售出",
                "你发布的商品「" + itemTitle + "」已被 " + buyerName + " 购买，订单号 " + orderId,
                orderId);
    }

    public List<UserNotification> listByUser(Long userId) {
        return repository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    public long countUnread(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        repository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setIsRead(true);
                repository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<UserNotification> list = repository.findByUserIdOrderByCreateTimeDesc(userId);
        for (UserNotification n : list) {
            if (!n.getIsRead()) {
                n.setIsRead(true);
                repository.save(n);
            }
        }
    }
}
