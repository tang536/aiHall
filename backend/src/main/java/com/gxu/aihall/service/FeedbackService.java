package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.common.PageQuery;
import com.gxu.aihall.common.PageResult;
import com.gxu.aihall.entity.Feedback;
import com.gxu.aihall.repository.FeedbackRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 学生反馈服务：学生提交与查看自己的反馈，管理员侧统一处理与回复。
 */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /** 学生提交反馈 */
    public Feedback submit(Feedback form, Long userId) {
        if (form.getContent() == null || form.getContent().trim().isEmpty()) {
            throw new BizException("请填写反馈内容");
        }
        Feedback fb = new Feedback();
        fb.setUserId(userId);
        fb.setType(form.getType() == null || form.getType().isBlank() ? "SUGGESTION" : form.getType());
        fb.setTitle(form.getTitle() == null ? null : form.getTitle().trim());
        fb.setContent(form.getContent().trim());
        fb.setContact(form.getContact());
        fb.setImages(form.getImages());
        fb.setStatus("PENDING");
        fb.setCreateTime(LocalDateTime.now());
        fb.setUpdateTime(LocalDateTime.now());
        return feedbackRepository.save(fb);
    }

    public List<Feedback> listMine(Long userId) {
        return feedbackRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    public Feedback getById(Long id) {
        return feedbackRepository.findById(id).orElse(null);
    }

    public void delete(Long id, Long operatorId) {
        Feedback fb = feedbackRepository.findById(id)
                .orElseThrow(() -> new BizException("反馈不存在"));
        if (!fb.getUserId().equals(operatorId)) {
            throw new BizException("只能删除自己提交的反馈");
        }
        feedbackRepository.delete(fb);
    }

    // ==================== 管理员侧 ====================

    /** 管理员视角：反馈列表（状态 + 类型过滤，分页）——条件下沉到 SQL */
    public PageResult<Feedback> adminList(String status, String type, int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        Specification<Feedback> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank() && !"ALL".equals(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null && !type.isBlank() && !"ALL".equals(type)) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResult.from(feedbackRepository.findAll(
                spec, q.pageable(Sort.by(Sort.Direction.DESC, "createTime"))));
    }

    /** 管理员回复反馈：写入回复内容并更新处理状态 */
    public Feedback reply(Long id, Long adminId, String adminReply, String status) {
        Feedback fb = feedbackRepository.findById(id)
                .orElseThrow(() -> new BizException("反馈不存在"));
        if (adminReply == null || adminReply.trim().isEmpty()) {
            throw new BizException("请填写回复内容");
        }
        fb.setAdminReply(adminReply.trim());
        fb.setAdminId(adminId);
        fb.setReplyTime(LocalDateTime.now());
        fb.setStatus(status == null || status.isBlank() ? "RESOLVED" : status);
        fb.setUpdateTime(LocalDateTime.now());
        return feedbackRepository.save(fb);
    }

    public void adminDelete(Long id) {
        feedbackRepository.deleteById(id);
    }

    public long countByStatus(String status) {
        return feedbackRepository.countByStatus(status);
    }

    public long countAll() {
        return feedbackRepository.count();
    }
}
