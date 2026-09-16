package com.gxu.aihall.repository;

import com.gxu.aihall.entity.PostReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostReplyRepository extends JpaRepository<PostReply, Long> {
    List<PostReply> findByPostIdAndStatusOrderByCreateTimeAsc(Long postId, String status);
    List<PostReply> findByPostIdOrderByCreateTimeAsc(Long postId);
    List<PostReply> findByUserIdOrderByCreateTimeDesc(Long userId);
    long countByPostIdAndStatus(Long postId, String status);
    long countByStatus(String status);
    void deleteByPostId(Long postId);
}
