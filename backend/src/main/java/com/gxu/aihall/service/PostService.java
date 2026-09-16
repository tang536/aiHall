package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.common.PageQuery;
import com.gxu.aihall.common.PageResult;
import com.gxu.aihall.dto.PostReplyVO;
import com.gxu.aihall.dto.PostVO;
import com.gxu.aihall.dto.PublicUserVO;
import com.gxu.aihall.entity.Post;
import com.gxu.aihall.entity.PostFavorite;
import com.gxu.aihall.entity.PostReply;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.repository.PostReplyRepository;
import com.gxu.aihall.cache.CacheConfig;
import com.gxu.aihall.repository.PostFavoriteRepository;
import com.gxu.aihall.repository.PostRepository;
import com.gxu.aihall.repository.UserRepository;
import com.gxu.aihall.search.KeywordSearchSupport;
import com.gxu.aihall.util.UserPrivacyUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校园论坛帖子服务：发布 / 删除 / 列表 / 详情 / 回复 / 点赞。
 * 所有对外输出均通过 PublicUserVO 脱敏，不暴露手机号、邮箱与完整学号。
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostReplyRepository postReplyRepository;
    private final PostFavoriteRepository postFavoriteRepository;
    private final UserRepository userRepository;
    private final KeywordSearchSupport keywordSearch;

    public PostService(PostRepository postRepository,
                       PostReplyRepository postReplyRepository,
                       PostFavoriteRepository postFavoriteRepository,
                       UserRepository userRepository,
                       KeywordSearchSupport keywordSearch) {
        this.postRepository = postRepository;
        this.postReplyRepository = postReplyRepository;
        this.postFavoriteRepository = postFavoriteRepository;
        this.userRepository = userRepository;
        this.keywordSearch = keywordSearch;
    }

    // ==================== 列表 / 详情 ====================

    @org.springframework.cache.annotation.Cacheable(cacheNames = CacheConfig.POST_LIST,
            key = "#keyword + '|' + #category + '|' + #page + '|' + #size")
    public PageResult<PostVO> list(String keyword, String category, int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        Page<Post> result = keywordSearch.canUseFullText(keyword)
                // 原生查询必须用无排序的 Pageable：Spring Data 会把 Sort 的属性名拼进 ORDER BY，
                // 而原生 SQL 里是列名 create_time。排序已写在 @Query 的 SQL 内。
                ? postRepository.searchByKeyword("PUBLISHED", filterValue(category), keyword.trim(), q.pageable())
                : postRepository.findAll(spec("PUBLISHED", category, null, keyword), q.pageable(NEWEST_FIRST));
        return PageResult.fromPage(result, toVOList(result.getContent()));
    }

    /** 详情：含全部回复（两级平铺，回复的回复带 replyToName） */
    public PostVO getDetail(Long id) {
        Post post = postRepository.findById(id).orElse(null);
        if (post == null || "DELETED".equals(post.getStatus())) return null;

        PostVO vo = PostVO.from(post, authorVO(post.getUserId(), new HashMap<>()));
        Map<Long, User> cache = new HashMap<>();
        List<PostReply> replies = postReplyRepository.findByPostIdAndStatusOrderByCreateTimeAsc(id, "PUBLISHED");

        // 先建立 回复id -> 展示名，便于「回复 @某某」
        Map<Long, String> replyAuthorName = new HashMap<>();
        for (PostReply r : replies) {
            replyAuthorName.put(r.getId(), authorVO(r.getUserId(), cache).getDisplayName());
        }

        List<PostReplyVO> replyVOs = new ArrayList<>();
        for (PostReply r : replies) {
            String replyToName = null;
            if (r.getParentId() != null) {
                replyToName = replyAuthorName.get(r.getParentId());
            }
            if (replyToName == null && r.getReplyToUserId() != null) {
                replyToName = authorVO(r.getReplyToUserId(), cache).getDisplayName();
            }
            replyVOs.add(PostReplyVO.from(r, authorVO(r.getUserId(), cache), replyToName));
        }
        vo.setReplies(replyVOs);
        return vo;
    }

    public List<PostVO> listMine(Long userId) {
        Map<Long, User> cache = new HashMap<>();
        List<PostVO> vos = new ArrayList<>();
        for (Post p : postRepository.findByUserIdAndStatusOrderByCreateTimeDesc(userId, "PUBLISHED")) {
            vos.add(PostVO.from(p, authorVO(userId, cache)));
        }
        return vos;
    }

    /** 管理员视角：全部帖子（可按状态/关键词过滤，含已删除） */
    /** 管理员视角：全部帖子（含已删除标记），支持状态/关键词过滤与分页 */
    public PageResult<PostVO> adminList(String status, String keyword, int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        Page<Post> result = keywordSearch.canUseFullText(keyword)
                ? postRepository.searchByKeyword(filterValue(status), "", keyword.trim(), q.pageable())
                : postRepository.findAll(spec(null, null, status, keyword), q.pageable(NEWEST_FIRST));
        return PageResult.fromPage(result, toVOList(result.getContent()));
    }

    /** 排序统一在这里定义：原生查询与 Specification 查询要保持一致 */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createTime");

    /** 空值 / ALL 统一成空串：原生 SQL 里用 '' 表示「该维度不过滤」 */
    private static String filterValue(String value) {
        return (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) ? "" : value.trim();
    }

    /**
     * 组装帖子查询条件。
     * @param fixedStatus  强制状态（学生端只看已发布）
     * @param fixedCategory 强制分类
     * @param statusFilter 管理端显式选择的状态（ALL/空视为不过滤）
     * @param keyword      标题/正文模糊匹配
     */
    private Specification<Post> spec(String fixedStatus, String fixedCategory,
                                     String statusFilter, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fixedStatus != null) {
                predicates.add(cb.equal(root.get("status"), fixedStatus));
            } else if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equals(statusFilter)) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (fixedCategory != null && !fixedCategory.isBlank() && !"ALL".equals(fixedCategory)) {
                predicates.add(cb.equal(root.get("category"), fixedCategory));
            }
            String kw = keyword == null ? null : keyword.trim().toLowerCase();
            if (kw != null && !kw.isEmpty()) {
                String pattern = "%" + kw + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("content")), pattern)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 批量转换：一次性查出这一页的作者，避免逐行查询（N+1） */
    private List<PostVO> toVOList(List<Post> posts) {
        List<PostVO> vos = new ArrayList<>();
        if (posts == null || posts.isEmpty()) return vos;
        Map<Long, User> authors = loadAuthors(posts);
        for (Post p : posts) {
            vos.add(PostVO.from(p, UserPrivacyUtil.toPublicVO(authors.get(p.getUserId()))));
        }
        return vos;
    }

    private Map<Long, User> loadAuthors(List<Post> posts) {
        Set<Long> ids = new HashSet<>();
        for (Post p : posts) {
            if (p.getUserId() != null) ids.add(p.getUserId());
        }
        Map<Long, User> map = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    // ==================== 发布 / 删除 ====================

    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.POST_LIST},
            allEntries = true)
    public Post publish(Post post, Long userId) {
        if (post.getContent() == null || post.getContent().trim().isEmpty()) {
            throw new BizException("请填写帖子内容");
        }
        post.setId(null);
        post.setUserId(userId);
        post.setContent(post.getContent().trim());
        if (post.getTitle() != null) post.setTitle(post.getTitle().trim());
        if (post.getCategory() == null || post.getCategory().isBlank()) post.setCategory("CAMPUS");
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setStatus("PUBLISHED");
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());
        return postRepository.save(post);
    }

    /** 作者删除自己的帖子（软删，回复一并软删） */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.POST_LIST},
            allEntries = true)
    public void delete(Long postId, Long operatorId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        if (!post.getUserId().equals(operatorId)) {
            throw new BizException("只能删除自己发布的帖子");
        }
        softDeletePost(post);
    }

    /** 管理员删除 */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.POST_LIST},
            allEntries = true)
    public void adminDelete(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        softDeletePost(post);
    }

    private void softDeletePost(Post post) {
        post.setStatus("DELETED");
        post.setUpdateTime(LocalDateTime.now());
        postRepository.save(post);
        for (PostReply r : postReplyRepository.findByPostIdOrderByCreateTimeAsc(post.getId())) {
            r.setStatus("DELETED");
            postReplyRepository.save(r);
        }
    }

    // ==================== 回复 / 点赞 ====================

    /**
     * 回复帖子：可回复楼主（parentId 为空），也可回复某条回复（parentId 指向回复 id），
     * 因此既能回复自己的帖子，也能回复其他用户的帖子与回复。
     */
    public PostReplyVO reply(Long postId, Long userId, String content, Long parentId) {
        if (content == null || content.trim().isEmpty()) {
            throw new BizException("请填写回复内容");
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        if (!"PUBLISHED".equals(post.getStatus())) {
            throw new BizException("该帖子已不存在");
        }

        PostReply reply = new PostReply();
        reply.setPostId(postId);
        reply.setUserId(userId);
        reply.setContent(content.trim());
        reply.setStatus("PUBLISHED");
        reply.setCreateTime(LocalDateTime.now());

        if (parentId != null) {
            PostReply parent = postReplyRepository.findById(parentId)
                    .orElseThrow(() -> new BizException("被回复的评论不存在"));
            if (!parent.getPostId().equals(postId)) {
                throw new BizException("被回复的评论不属于该帖子");
            }
            reply.setParentId(parent.getId());
            reply.setReplyToUserId(parent.getUserId());
        } else {
            reply.setReplyToUserId(post.getUserId());
        }

        postReplyRepository.save(reply);

        post.setReplyCount((int) postReplyRepository.countByPostIdAndStatus(postId, "PUBLISHED"));
        post.setUpdateTime(LocalDateTime.now());
        postRepository.save(post);

        String replyToName = reply.getReplyToUserId() != null
                ? authorVO(reply.getReplyToUserId(), new HashMap<>()).getDisplayName() : null;
        return PostReplyVO.from(reply, authorVO(userId, new HashMap<>()), replyToName);
    }

    /** 删除回复：回复者本人或楼主可删 */
    public void deleteReply(Long replyId, Long operatorId) {
        PostReply reply = postReplyRepository.findById(replyId)
                .orElseThrow(() -> new BizException("回复不存在"));
        Post post = postRepository.findById(reply.getPostId()).orElse(null);
        boolean isOwner = reply.getUserId().equals(operatorId);
        boolean isPostAuthor = post != null && post.getUserId().equals(operatorId);
        if (!isOwner && !isPostAuthor) {
            throw new BizException("只能删除自己的回复或自己帖子下的回复");
        }
        reply.setStatus("DELETED");
        postReplyRepository.save(reply);
        if (post != null) {
            post.setReplyCount((int) postReplyRepository.countByPostIdAndStatus(post.getId(), "PUBLISHED"));
            postRepository.save(post);
        }
    }

    /** 管理员删除回复（不校验归属） */
    public void adminDeleteReply(Long replyId) {
        PostReply reply = postReplyRepository.findById(replyId)
                .orElseThrow(() -> new BizException("回复不存在"));
        reply.setStatus("DELETED");
        postReplyRepository.save(reply);
        postRepository.findById(reply.getPostId()).ifPresent(post -> {
            post.setReplyCount((int) postReplyRepository.countByPostIdAndStatus(post.getId(), "PUBLISHED"));
            postRepository.save(post);
        });
    }

    public Post like(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BizException("帖子不存在"));
        post.setLikeCount((post.getLikeCount() == null ? 0 : post.getLikeCount()) + 1);
        postRepository.save(post);
        return post;
    }

    // ==================== 内部工具 ====================

    private PublicUserVO authorVO(Long userId, Map<Long, User> cache) {
        User user = cache.computeIfAbsent(userId, id -> userRepository.findById(id).orElse(null));
        return UserPrivacyUtil.toPublicVO(user);
    }

    // ==================== 收藏 ====================

    @org.springframework.transaction.annotation.Transactional
    public boolean toggleFavorite(Long userId, Long postId) {
        if (postFavoriteRepository.existsByUserIdAndPostId(userId, postId)) {
            postFavoriteRepository.deleteByUserIdAndPostId(userId, postId);
            return false;
        }
        PostFavorite fav = new PostFavorite();
        fav.setUserId(userId);
        fav.setPostId(postId);
        fav.setCreateTime(LocalDateTime.now());
        postFavoriteRepository.save(fav);
        return true;
    }

    public boolean isFavorited(Long userId, Long postId) {
        return postFavoriteRepository.existsByUserIdAndPostId(userId, postId);
    }

    public List<PostVO> listFavorites(Long userId) {
        List<PostFavorite> favs = postFavoriteRepository.findByUserIdOrderByCreateTimeDesc(userId);
        if (favs.isEmpty()) return new ArrayList<>();
        List<Long> postIds = favs.stream().map(PostFavorite::getPostId).toList();
        List<Post> posts = postRepository.findAllById(postIds);
        Map<Long, Post> postMap = new HashMap<>();
        posts.forEach(p -> postMap.put(p.getId(), p));
        List<Post> sorted = new ArrayList<>();
        for (PostFavorite fav : favs) {
            Post post = postMap.get(fav.getPostId());
            if (post != null && !"DELETED".equals(post.getStatus())) sorted.add(post);
        }
        // 批量查作者，避免 N+1
        Set<Long> authorIds = new HashSet<>();
        sorted.forEach(p -> { if (p.getUserId() != null) authorIds.add(p.getUserId()); });
        Map<Long, User> authors = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> authors.put(u.getId(), u));
        List<PostVO> result = new ArrayList<>();
        for (Post p : sorted) {
            result.add(PostVO.from(p, UserPrivacyUtil.toPublicVO(authors.get(p.getUserId()))));
        }
        return result;
    }
}
