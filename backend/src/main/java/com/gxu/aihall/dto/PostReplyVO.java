package com.gxu.aihall.dto;

import com.gxu.aihall.entity.PostReply;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子回复对外视图：回复内容 + 脱敏后的回复者信息
 */
@Data
public class PostReplyVO {
    private Long id;
    private Long postId;
    private Long userId;
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private LocalDateTime createTime;

    /** 回复者公开信息（已脱敏） */
    private PublicUserVO author;

    /** 被回复者的展示名，用于「回复 @某某」 */
    private String replyToName;

    public static PostReplyVO from(PostReply reply, PublicUserVO author, String replyToName) {
        PostReplyVO vo = new PostReplyVO();
        vo.setId(reply.getId());
        vo.setPostId(reply.getPostId());
        vo.setUserId(reply.getUserId());
        vo.setParentId(reply.getParentId());
        vo.setReplyToUserId(reply.getReplyToUserId());
        vo.setContent(reply.getContent());
        vo.setCreateTime(reply.getCreateTime());
        vo.setAuthor(author);
        vo.setReplyToName(replyToName);
        return vo;
    }
}
