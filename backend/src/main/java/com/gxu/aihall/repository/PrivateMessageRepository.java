package com.gxu.aihall.repository;

import com.gxu.aihall.entity.PrivateMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    /** 某用户参与的全部消息（倒序） */
    List<PrivateMessage> findBySenderIdOrReceiverIdOrderByCreateTimeDesc(Long senderId, Long receiverId);

    /** 某会话完整消息（正序，用于聊天窗口） */
    List<PrivateMessage> findByConversationIdOrderByCreateTimeAsc(String conversationId);

    List<PrivateMessage> findByReceiverIdAndIsReadOrderByCreateTimeDesc(Long receiverId, Integer isRead);

    long countByReceiverIdAndIsRead(Long receiverId, Integer isRead);

    long countByConversationIdAndReceiverIdAndIsRead(String conversationId, Long receiverId, Integer isRead);

    /**
     * 每个会话只取最新一条消息。
     * 会话列表页只需要「每个对话的最后一句话 + 未读数」，如果先把用户全部历史消息
     * 捞出来再在内存里分组，消息量一大就会拖垮接口，所以直接用 SQL 求出每个会话的 MAX(id)。
     */
    @Query(value = """
            SELECT m.* FROM private_message m
            JOIN (SELECT MAX(id) AS mid FROM private_message
                  WHERE sender_id = :userId OR receiver_id = :userId
                  GROUP BY conversation_id) t ON m.id = t.mid
            ORDER BY m.create_time DESC
            """, nativeQuery = true)
    List<PrivateMessage> findLatestPerConversation(@Param("userId") Long userId);

    /** 每个会话里我未读的消息条数 */
    @Query("select m.conversationId, count(m) from PrivateMessage m "
            + "where m.receiverId = :userId and m.isRead = 0 group by m.conversationId")
    List<Object[]> countUnreadGroupByConversation(@Param("userId") Long userId);

    /** 会话最新一页消息（按 id 倒序取，调用方再反转成正序展示） */
    @Query("select m from PrivateMessage m where m.conversationId = :conversationId order by m.id desc")
    List<PrivateMessage> findLatestPage(@Param("conversationId") String conversationId, Pageable pageable);

    /** 向上翻页：取 id 小于游标的更早消息 */
    @Query("select m from PrivateMessage m where m.conversationId = :conversationId and m.id < :beforeId order by m.id desc")
    List<PrivateMessage> findPageBefore(@Param("conversationId") String conversationId,
                                        @Param("beforeId") Long beforeId,
                                        Pageable pageable);

    /** 把某会话里我未读的消息一次性标记已读（避免逐条 save） */
    @Modifying
    @Query("update PrivateMessage m set m.isRead = 1 "
            + "where m.conversationId = :conversationId and m.receiverId = :userId and m.isRead = 0")
    int markConversationRead(@Param("conversationId") String conversationId, @Param("userId") Long userId);
}
