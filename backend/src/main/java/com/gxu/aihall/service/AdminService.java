package com.gxu.aihall.service;

import com.gxu.aihall.entity.ChatMessage;
import com.gxu.aihall.entity.RepairOrder;
import com.gxu.aihall.entity.Application;
import com.gxu.aihall.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员仪表盘统计服务
 * 所有指标均来自数据库真实统计
 */
@Service
public class AdminService {

    private final RepairOrderRepository repairOrderRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationRepository notificationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LostItemRepository lostItemRepository;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final MarketItemRepository marketItemRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final PostRepository postRepository;
    private final PostReplyRepository postReplyRepository;

    public AdminService(RepairOrderRepository repairOrderRepository,
                        ApplicationRepository applicationRepository,
                        NotificationRepository notificationRepository,
                        ChatMessageRepository chatMessageRepository,
                        LostItemRepository lostItemRepository,
                        UserRepository userRepository,
                        FeedbackRepository feedbackRepository,
                        MarketItemRepository marketItemRepository,
                        MarketOrderRepository marketOrderRepository,
                        PostRepository postRepository,
                        PostReplyRepository postReplyRepository) {
        this.repairOrderRepository = repairOrderRepository;
        this.applicationRepository = applicationRepository;
        this.notificationRepository = notificationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.lostItemRepository = lostItemRepository;
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.marketItemRepository = marketItemRepository;
        this.marketOrderRepository = marketOrderRepository;
        this.postRepository = postRepository;
        this.postReplyRepository = postReplyRepository;
    }

    /**
     * 获取仪表盘统计数据
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // ========== 报修统计 ==========
        Map<String, Long> repairStats = new HashMap<>();
        repairStats.put("submitted", repairOrderRepository.countByStatus("SUBMITTED"));
        repairStats.put("accepted", repairOrderRepository.countByStatus("ACCEPTED"));
        repairStats.put("repairing", repairOrderRepository.countByStatus("REPAIRING"));
        repairStats.put("completed", repairOrderRepository.countByStatus("COMPLETED"));
        repairStats.put("total", repairOrderRepository.count());
        stats.put("repair", repairStats);

        // ========== 申请统计 ==========
        Map<String, Long> appStats = new HashMap<>();
        appStats.put("submitted", applicationRepository.countByStatus("SUBMITTED"));
        appStats.put("reviewing", applicationRepository.countByStatus("REVIEWING"));
        appStats.put("approved", applicationRepository.countByStatus("APPROVED"));
        appStats.put("rejected", applicationRepository.countByStatus("REJECTED"));
        appStats.put("total", applicationRepository.count());
        stats.put("application", appStats);

        // 事项类型分布
        Map<String, Long> appTypeStats = new HashMap<>();
        appTypeStats.put("奖助学金", applicationRepository.countByType("SCHOLARSHIP"));
        appTypeStats.put("请假申请", applicationRepository.countByType("LEAVE"));
        appTypeStats.put("证明开具", applicationRepository.countByType("CERTIFICATE"));
        stats.put("appTypeDistribution", appTypeStats);

        // ========== AI问答统计（真实数据） ==========
        long totalMessages = chatMessageRepository.count();
        long userMessages = chatMessageRepository.countByRole("user");
        long assistantMessages = chatMessageRepository.countByRole("assistant");
        long positiveFeedback = chatMessageRepository.countByFeedback(1);
        long negativeFeedback = chatMessageRepository.countByFeedback(-1);
        long feedbackTotal = positiveFeedback + negativeFeedback;
        double satisfactionRate = feedbackTotal > 0
                ? Math.round(positiveFeedback * 1000.0 / feedbackTotal) / 10.0 : 0.0;

        // 平均响应时间（只统计 assistant 消息中有 responseTime 的）
        List<ChatMessage> assistantMsgs = chatMessageRepository.findByRoleAndCreateTimeAfter(
                "assistant", LocalDateTime.now().minusDays(30));
        double avgResponseTime = assistantMsgs.stream()
                .filter(m -> m.getResponseTime() != null && m.getResponseTime() > 0)
                .mapToLong(ChatMessage::getResponseTime)
                .average().orElse(0);
        avgResponseTime = Math.round(avgResponseTime);

        Map<String, Object> chatStats = new HashMap<>();
        chatStats.put("totalMessages", totalMessages);
        chatStats.put("userMessages", userMessages);
        chatStats.put("assistantMessages", assistantMessages);
        chatStats.put("positiveFeedback", positiveFeedback);
        chatStats.put("negativeFeedback", negativeFeedback);
        chatStats.put("satisfactionRate", satisfactionRate);
        chatStats.put("avgResponseTime", (long) avgResponseTime);
        stats.put("chat", chatStats);

        // ========== 近7天趋势（真实数据） ==========
        stats.put("trend", buildSevenDayTrend());

        // ========== 高频问题（从真实用户提问中统计） ==========
        stats.put("hotQuestions", buildHotQuestions());

        // ========== 其他统计 ==========
        stats.put("notificationCount", notificationRepository.count());
        stats.put("lostItemCount", lostItemRepository.count());
        stats.put("userCount", userRepository.count());

        // ========== 学生反馈统计（新增模块） ==========
        Map<String, Long> feedbackStats = new HashMap<>();
        feedbackStats.put("pending", feedbackRepository.countByStatus("PENDING"));
        feedbackStats.put("processing", feedbackRepository.countByStatus("PROCESSING"));
        feedbackStats.put("resolved", feedbackRepository.countByStatus("RESOLVED"));
        feedbackStats.put("total", feedbackRepository.count());
        stats.put("feedback", feedbackStats);

        // ========== 二手交易统计 ==========
        Map<String, Long> marketStats = new HashMap<>();
        marketStats.put("onSale", marketItemRepository.countByStatus("ON_SALE"));
        marketStats.put("offShelf", marketItemRepository.countByStatus("OFF_SHELF"));
        marketStats.put("sold", marketItemRepository.countByStatus("SOLD"));
        marketStats.put("itemTotal", marketItemRepository.count());
        marketStats.put("orderTotal", marketOrderRepository.count());
        stats.put("market", marketStats);

        // ========== 论坛统计 ==========
        Map<String, Long> postStats = new HashMap<>();
        postStats.put("published", postRepository.countByStatus("PUBLISHED"));
        postStats.put("deleted", postRepository.countByStatus("DELETED"));
        postStats.put("total", postRepository.count());
        postStats.put("replyTotal", postReplyRepository.count());
        stats.put("post", postStats);

        return stats;
    }

    /**
     * 首页公开统计（4个核心指标，全部来自数据库真实数据）
     */
    public Map<String, Object> getPublicStats() {
        Map<String, Object> stats = new HashMap<>();
        // 服务学生数：系统注册用户总数
        stats.put("totalUsers", userRepository.count());
        // AI问答次数：用户真实提问条数
        stats.put("aiQuestions", chatMessageRepository.countByRole("user"));
        // 报修完成：状态为已完成的工单数量
        stats.put("repairDone", repairOrderRepository.countByStatus("COMPLETED"));
        // 满意度：基于用户对AI回答的点踩/点赞反馈计算
        long positive = chatMessageRepository.countByFeedback(1);
        long negative = chatMessageRepository.countByFeedback(-1);
        long feedbackTotal = positive + negative;
        // 无反馈数据时返回 -1，前端显示为 "--"
        double satisfaction = feedbackTotal > 0
                ? Math.round(positive * 1000.0 / feedbackTotal) / 10.0 : -1;
        stats.put("satisfaction", satisfaction);
        return stats;
    }

    /**
     * 构建近7天每日趋势数据
     */
    private Map<String, Object> buildSevenDayTrend() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");

        List<String> days = new ArrayList<>();
        List<Long> repairData = new ArrayList<>();
        List<Long> appData = new ArrayList<>();
        List<Long> chatData = new ArrayList<>();

        // 查询近7天数据
        List<RepairOrder> recentRepairs = repairOrderRepository.findByCreateTimeAfter(sevenDaysAgo);
        List<Application> recentApps = applicationRepository.findByCreateTimeAfter(sevenDaysAgo);
        List<ChatMessage> recentChats = chatMessageRepository.findByRoleAndCreateTimeAfter("user", sevenDaysAgo);

        // 按日期分组计数
        Map<LocalDate, Long> repairByDay = recentRepairs.stream()
                .collect(Collectors.groupingBy(r -> r.getCreateTime().toLocalDate(), Collectors.counting()));
        Map<LocalDate, Long> appByDay = recentApps.stream()
                .collect(Collectors.groupingBy(a -> a.getCreateTime().toLocalDate(), Collectors.counting()));
        Map<LocalDate, Long> chatByDay = recentChats.stream()
                .collect(Collectors.groupingBy(c -> c.getCreateTime().toLocalDate(), Collectors.counting()));

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            days.add(date.format(fmt));
            repairData.add(repairByDay.getOrDefault(date, 0L));
            appData.add(appByDay.getOrDefault(date, 0L));
            chatData.add(chatByDay.getOrDefault(date, 0L));
        }

        Map<String, Object> trend = new HashMap<>();
        trend.put("days", days);
        trend.put("repair", repairData);
        trend.put("application", appData);
        trend.put("chat", chatData);
        return trend;
    }

    /**
     * 从真实用户提问中统计高频问题
     */
    private List<Map<String, Object>> buildHotQuestions() {
        // 查询近30天的用户提问
        List<ChatMessage> userMsgs = chatMessageRepository.findByRoleAndCreateTimeAfter(
                "user", LocalDateTime.now().minusDays(30));

        // 按内容分组计数，取前5
        return userMsgs.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getContent() != null ? m.getContent().trim() : "",
                        Collectors.counting()))
                .entrySet().stream()
                .filter(e -> !e.getKey().isEmpty())
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    String q = e.getKey();
                    // 截断过长的问题
                    item.put("question", q.length() > 20 ? q.substring(0, 20) + "..." : q);
                    item.put("count", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }
}
