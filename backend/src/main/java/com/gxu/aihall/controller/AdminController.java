package com.gxu.aihall.controller;

import com.gxu.aihall.audit.Audited;
import com.gxu.aihall.common.PageQuery;
import com.gxu.aihall.common.PageResult;
import com.gxu.aihall.common.Result;
import com.gxu.aihall.dto.MarketItemVO;
import com.gxu.aihall.dto.PostVO;
import com.gxu.aihall.entity.*;
import com.gxu.aihall.repository.KnowledgeDocRepository;
import com.gxu.aihall.repository.UserRepository;
import com.gxu.aihall.service.*;
import com.gxu.aihall.util.UserPrivacyUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final RepairService repairService;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;
    private final KnowledgeDocRepository knowledgeDocRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SystemSettingService systemSettingService;
    private final FeedbackService feedbackService;
    private final MarketService marketService;
    private final PostService postService;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuditService auditService;

    public AdminController(AdminService adminService,
                           RepairService repairService,
                           ApplicationService applicationService,
                           NotificationService notificationService,
                           KnowledgeDocRepository knowledgeDocRepository,
                           KnowledgeBaseService knowledgeBaseService,
                           SystemSettingService systemSettingService,
                           FeedbackService feedbackService,
                           MarketService marketService,
                           PostService postService,
                           WalletService walletService,
                           UserRepository userRepository,
                           AuthService authService,
                           AuditService auditService) {
        this.adminService = adminService;
        this.repairService = repairService;
        this.applicationService = applicationService;
        this.notificationService = notificationService;
        this.knowledgeDocRepository = knowledgeDocRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.systemSettingService = systemSettingService;
        this.feedbackService = feedbackService;
        this.marketService = marketService;
        this.postService = postService;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.authService = authService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(adminService.getDashboardStats());
    }

    // 报修管理
    @GetMapping("/repairs")
    public Result<List<RepairOrder>> getAllRepairs(@RequestParam(required = false) String status) {
        if (StringUtils.hasText(status)) {
            return Result.success(repairService.getByStatus(status));
        }
        return Result.success(repairService.getAll());
    }

    @Audited(action = "UPDATE_REPAIR_STATUS", targetType = "REPAIR_ORDER", targetIdParam = "id", includeParams = {"status"})
    @PutMapping("/repairs/{id}/status")
    public Result<RepairOrder> updateRepairStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(repairService.updateStatus(id, body.get("status"), body.get("remark")));
    }

    // 申请管理
    @GetMapping("/applications")
    public Result<List<Application>> getAllApplications(@RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String type) {
        if (StringUtils.hasText(status)) {
            return Result.success(applicationService.getByStatus(status));
        }
        if (StringUtils.hasText(type)) {
            return Result.success(applicationService.getByType(type));
        }
        return Result.success(applicationService.getAll());
    }

    @Audited(action = "REVIEW_APPLICATION", targetType = "APPLICATION", targetIdParam = "id", includeParams = {"status"})
    @PutMapping("/applications/{id}/review")
    public Result<Application> reviewApplication(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(applicationService.review(id, body.get("action"), body.get("remark"), body.get("nextNode")));
    }

    // 通知管理
    @Audited(action = "CREATE_NOTIFICATION", targetType = "NOTIFICATION")
    @PostMapping("/notifications")
    public Result<Notification> createNotification(@RequestBody Notification notification) {
        return Result.success("发布成功", notificationService.save(notification));
    }

    @Audited(action = "UPDATE_NOTIFICATION", targetType = "NOTIFICATION", targetIdParam = "id")
    @PutMapping("/notifications/{id}")
    public Result<Notification> updateNotification(@PathVariable Long id, @RequestBody Notification notification) {
        notification.setId(id);
        return Result.success(notificationService.save(notification));
    }

    @Audited(action = "DELETE_NOTIFICATION", targetType = "NOTIFICATION", targetIdParam = "id")
    @DeleteMapping("/notifications/{id}")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        notificationService.delete(id);
        return Result.success();
    }

    /**
     * 以 PDF 格式导入通知：解析 PDF 文本作为正文，标题默认取文件名（可指定），按标题去重。
     */
    @Audited(action = "IMPORT_NOTIFICATION_PDF", targetType = "NOTIFICATION")
    @PostMapping("/notifications/import-pdf")
    public Result<Notification> importPdf(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "title", required = false) String title,
                                          @RequestParam(value = "category", required = false) String category,
                                          @RequestParam(value = "department", required = false) String department) {
        try {
            Notification n = notificationService.importPdf(file, title, category, department);
            return Result.success("导入成功", n);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("PDF 导入失败：" + e.getMessage());
        }
    }

    // 知识库管理
    @GetMapping("/knowledge")
    public Result<List<KnowledgeDoc>> getAllKnowledge(@RequestParam(required = false) String category) {
        if (StringUtils.hasText(category)) {
            return Result.success(knowledgeDocRepository.findByCategoryOrderByPriorityDescCreateTimeDesc(category));
        }
        return Result.success(knowledgeDocRepository.findAllByOrderByPriorityDescCreateTimeDesc());
    }

    @Audited(action = "CREATE_KNOWLEDGE", targetType = "KNOWLEDGE_DOC")
    @PostMapping("/knowledge")
    public Result<KnowledgeDoc> createKnowledge(@RequestBody KnowledgeDoc doc) {
        if (doc.getTitle() != null && knowledgeDocRepository.existsByTitle(doc.getTitle())) {
            return Result.error("知识库中已存在标题为「" + doc.getTitle() + "」的文档，请勿重复添加");
        }
        KnowledgeDoc saved = knowledgeDocRepository.save(doc);
        knowledgeBaseService.indexDocument(saved.getId());
        return Result.success("添加成功", saved);
    }

    @Audited(action = "UPDATE_KNOWLEDGE", targetType = "KNOWLEDGE_DOC", targetIdParam = "id")
    @PutMapping("/knowledge/{id}")
    public Result<KnowledgeDoc> updateKnowledge(@PathVariable Long id, @RequestBody KnowledgeDoc doc) {
        doc.setId(id);
        KnowledgeDoc saved = knowledgeDocRepository.save(doc);
        knowledgeBaseService.indexDocument(id);
        return Result.success(saved);
    }

    @Audited(action = "DELETE_KNOWLEDGE", targetType = "KNOWLEDGE_DOC", targetIdParam = "id")
    @DeleteMapping("/knowledge/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeDocRepository.deleteById(id);
        knowledgeBaseService.deleteDocument(id);
        return Result.success();
    }

    // 系统设置：修改专注模式密码
    @Audited(action = "SET_FOCUS_PASSWORD", targetType = "SYSTEM")
    @PutMapping("/system/focus-password")
    public Result<Void> setFocusPassword(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.length() < 4) {
            return Result.error("密码至少4位");
        }
        systemSettingService.setFocusPassword(password);
        return Result.success();
    }

    // ==================== 学生反馈管理 ====================

    @GetMapping("/feedbacks")
    public Result<PageResult<Feedback>> listFeedbacks(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return Result.success(feedbackService.adminList(status, type, page, size));
    }

    /** 回复学生反馈并更新处理状态（PENDING / PROCESSING / RESOLVED） */
    @Audited(action = "REPLY_FEEDBACK", targetType = "FEEDBACK", targetIdParam = "id")
    @PutMapping("/feedbacks/{id}/reply")
    public Result<Feedback> replyFeedback(@RequestHeader(value = "Authorization", required = false) String token,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        User admin = authService.requireLogin(token, "未登录");
        return Result.success("已回复",
                feedbackService.reply(id, admin.getId(), body.get("adminReply"), body.get("status")));
    }

    @Audited(action = "DELETE_FEEDBACK", targetType = "FEEDBACK", targetIdParam = "id")
    @DeleteMapping("/feedbacks/{id}")
    public Result<Void> deleteFeedback(@PathVariable Long id) {
        feedbackService.adminDelete(id);
        return Result.success("已删除", null);
    }

    // ==================== 二手商品管理 ====================

    @GetMapping("/market/items")
    public Result<PageResult<MarketItemVO>> listMarketItems(@RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(marketService.adminList(status, keyword, page, size));
    }

    @Audited(action = "OFFSHELF_ITEM", targetType = "MARKET_ITEM", targetIdParam = "id")
    @PostMapping("/market/items/{id}/offshelf")
    public Result<MarketItem> adminOffShelfItem(@PathVariable Long id) {
        return Result.success("已强制下架", marketService.adminOffShelf(id));
    }

    @Audited(action = "ONSHELF_ITEM", targetType = "MARKET_ITEM", targetIdParam = "id")
    @PostMapping("/market/items/{id}/onshelf")
    public Result<MarketItem> adminOnShelfItem(@PathVariable Long id) {
        return Result.success("已恢复上架", marketService.adminOnShelf(id));
    }

    @Audited(action = "DELETE_ITEM", targetType = "MARKET_ITEM", targetIdParam = "id")
    @DeleteMapping("/market/items/{id}")
    public Result<Void> adminDeleteItem(@PathVariable Long id) {
        marketService.adminDelete(id);
        return Result.success("已删除", null);
    }

    @GetMapping("/market/orders")
    public Result<PageResult<MarketOrder>> listMarketOrders(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(marketService.listAllOrders(page, size));
    }

    // ==================== 帖子管理 ====================

    @GetMapping("/posts")
    public Result<PageResult<PostVO>> listPosts(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(postService.adminList(status, keyword, page, size));
    }

    @Audited(action = "DELETE_POST", targetType = "POST", targetIdParam = "id")
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        postService.adminDelete(id);
        return Result.success("已删除", null);
    }

    @Audited(action = "DELETE_POST_REPLY", targetType = "POST_REPLY", targetIdParam = "replyId")
    @DeleteMapping("/posts/replies/{replyId}")
    public Result<Void> deletePostReply(@PathVariable Long replyId) {
        postService.adminDeleteReply(replyId);
        return Result.success("已删除", null);
    }

    // ==================== 用户管理 ====================

    /** 用户列表：手机号/邮箱脱敏后返回，避免后台过度暴露隐私（数据库分页，关键词模糊匹配账号或姓名） */
    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> listUsers(@RequestParam(required = false) String keyword,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        PageQuery q = PageQuery.of(page, size);
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        Specification<User> spec = (root, query, cb) -> {
            if (kw == null) return cb.conjunction();
            String pattern = "%" + kw + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("realName")), pattern));
        };
        Page<User> result = userRepository.findAll(spec, q.pageable(Sort.by(Sort.Direction.DESC, "id")));
        List<Map<String, Object>> rows = new ArrayList<>(result.getNumberOfElements());
        for (User u : result.getContent()) {
            rows.add(toUserRow(u));
        }
        return Result.success(PageResult.fromPage(result, rows));
    }

    /** 调整用户余额（事务 + 悲观锁，并写入余额流水） */
    @Audited(action = "ADJUST_BALANCE", targetType = "USER", targetIdParam = "id", includeParams = {"amount", "remark"})
    @PostMapping("/users/{id}/balance")
    public Result<Map<String, Object>> adjustBalance(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        BigDecimal amount = parseAmount(body.get("amount"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        BigDecimal balance = walletService.adminAdjust(id, amount, remark);
        return Result.success("余额已调整", Map.of("balance", balance));
    }

    /** 启用 / 封禁账号：status = 1 启用，0 封禁 */
    @Audited(action = "UPDATE_USER_STATUS", targetType = "USER", targetIdParam = "id", includeParams = {"status"})
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = parseInt(body.get("status"));
        if (status == null || (status != 0 && status != 1)) {
            return Result.error("状态取值不合法");
        }
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return Result.error("用户不存在");
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return Result.error("不能禁用管理员账号");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        return Result.success(status == 1 ? "账号已启用" : "账号已禁用", null);
    }

    private Map<String, Object> toUserRow(User u) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", u.getId());
        row.put("username", u.getUsername());
        row.put("realName", u.getRealName());
        row.put("role", u.getRole());
        row.put("college", u.getCollege());
        row.put("major", u.getMajor());
        row.put("grade", u.getGrade());
        // 隐私脱敏：手机号与邮箱仅展示掩码
        row.put("phone", UserPrivacyUtil.maskPhone(u.getPhone()));
        row.put("email", UserPrivacyUtil.maskEmail(u.getEmail()));
        row.put("balance", UserPrivacyUtil.balanceOf(u));
        row.put("status", u.getStatus());
        row.put("createTime", u.getCreateTime());
        return row;
    }

    private BigDecimal parseAmount(Object raw) {
        if (raw == null) return null;
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 敏感操作审计日志（数据库分页）。
     * <p>记录由 {@code audit/AuditAspect} 自动写入，涵盖标了 {@link Audited} 的管理端操作。
     * 时间参数接受 {@code 2026-09-15} 或 {@code 2026-09-15T10:00} 这样的 ISO 形式。
     */
    @GetMapping("/audit-logs")
    public Result<PageResult<Map<String, Object>>> auditLogs(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(auditService.query(operatorId, action, targetType, targetId, result,
                parseDateTime(from), parseDateTime(to), page, size));
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            return s.length() <= 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s);
        } catch (Exception e) {
            // 时间格式不对就当没传，而不是让整个查询 500
            return null;
        }
    }

    private Integer parseInt(Object raw) {
        if (raw == null) return null;
        try {
            return Integer.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
