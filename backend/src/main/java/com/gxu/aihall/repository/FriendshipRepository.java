package com.gxu.aihall.repository;

import com.gxu.aihall.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findFirstByRequesterIdAndAddresseeIdOrderByIdDesc(Long requesterId, Long addresseeId);

    /** 查询两人之间的好友关系（不分方向） */
    @Query("select f from Friendship f where (f.requesterId = :a and f.addresseeId = :b) " +
            "or (f.requesterId = :b and f.addresseeId = :a)")
    List<Friendship> findBetween(@Param("a") Long a, @Param("b") Long b);

    /** 某用户的全部已接受好友关系 */
    @Query("select f from Friendship f where f.status = 'ACCEPTED' " +
            "and (f.requesterId = :uid or f.addresseeId = :uid)")
    List<Friendship> findAcceptedByUser(@Param("uid") Long uid);

    List<Friendship> findByAddresseeIdAndStatusOrderByCreateTimeDesc(Long addresseeId, String status);
    List<Friendship> findByRequesterIdAndStatusOrderByCreateTimeDesc(Long requesterId, String status);
}
