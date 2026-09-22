package com.gxu.aihall.dto;

import com.gxu.aihall.entity.Post;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 帖子对外视图：帖子内容 + 脱敏后的作者信息 + 回复列表
 */
@Data
public class PostVO {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String category;
    private String images;
    /** 首图缩略图 URL（由 images 按命名约定推导）；列表页只加载它，没有时等于原图 */
    private String thumbUrl;
    private Integer replyCount;
    private Integer likeCount;
    /** 当前登录用户是否已点赞（未登录时为 false） */
    private Boolean liked = false;
    private LocalDateTime createTime;

    /** 作者公开信息（已脱敏） */
    private PublicUserVO author;

    /** 回复列表（列表查询时为空，详情接口返回） */
    private List<PostReplyVO> replies = new ArrayList<>();

    public static PostVO from(Post post, PublicUserVO author) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setCategory(post.getCategory());
        vo.setImages(post.getImages());
        vo.setThumbUrl(thumbOf(post.getImages()));
        vo.setReplyCount(post.getReplyCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setCreateTime(post.getCreateTime());
        vo.setAuthor(author);
        return vo;
    }

    /** 从逗号分隔的 images 里取首图，再推导缩略图 URL */
    private static String thumbOf(String images) {
        if (images == null || images.isBlank()) return null;
        int comma = images.indexOf(',');
        String first = (comma < 0 ? images : images.substring(0, comma)).trim();
        if (first.isEmpty()) return null;
        return com.gxu.aihall.service.FileStorageService.thumbnailOf(first);
    }
}
