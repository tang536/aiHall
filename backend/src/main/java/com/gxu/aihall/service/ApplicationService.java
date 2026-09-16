package com.gxu.aihall.service;

import com.gxu.aihall.dto.ApplicationRequest;
import com.gxu.aihall.entity.Application;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.repository.ApplicationRepository;
import com.gxu.aihall.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事项申请服务（奖助/请假/证明）
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public ApplicationService(ApplicationRepository applicationRepository, UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 提交申请
     */
    public Application submit(ApplicationRequest request, Long studentId) {
        User student = userRepository.findById(studentId).orElse(null);
        Application app = new Application();
        app.setApplicationNo(generateAppNo());
        app.setType(request.getType());
        app.setTitle(request.getTitle());
        app.setFormData(request.getFormData());
        if (request.getMaterialUrls() != null && !request.getMaterialUrls().isEmpty()) {
            app.setMaterialUrls(String.join(",", request.getMaterialUrls()));
        }
        app.setStudentId(studentId);
        app.setStudentName(student != null ? student.getRealName() : "未知");
        app.setStatus("SUBMITTED");
        app.setCurrentNode(getInitialNode(request.getType()));
        return applicationRepository.save(app);
    }

    private String getInitialNode(String type) {
        return switch (type) {
            case "SCHOLARSHIP" -> "辅导员审核";
            case "LEAVE" -> "辅导员审批";
            case "CERTIFICATE" -> "学院教务审核";
            default -> "待审核";
        };
    }

    public Application getByAppNo(String appNo) {
        return applicationRepository.findByApplicationNo(appNo).orElse(null);
    }

    public List<Application> getByStudentId(Long studentId) {
        return applicationRepository.findByStudentIdOrderByCreateTimeDesc(studentId);
    }

    public List<Application> getAll() {
        return applicationRepository.findAllByOrderByCreateTimeDesc();
    }

    public List<Application> getByStatus(String status) {
        return applicationRepository.findByStatusOrderByCreateTimeDesc(status);
    }

    public List<Application> getByType(String type) {
        return applicationRepository.findByTypeOrderByCreateTimeDesc(type);
    }

    /**
     * 审核
     */
    public Application review(Long id, String action, String remark, String nextNode) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("申请不存在"));

        if ("approve".equals(action)) {
            if (nextNode != null && !nextNode.isEmpty()) {
                app.setStatus("REVIEWING");
                app.setCurrentNode(nextNode);
            } else {
                app.setStatus("APPROVED");
                app.setCurrentNode("已完成");
                app.setCompleteTime(LocalDateTime.now());
            }
        } else if ("reject".equals(action)) {
            app.setStatus("REJECTED");
            app.setRejectReason(remark);
            app.setCompleteTime(LocalDateTime.now());
        }
        app.setUpdateTime(LocalDateTime.now());
        return applicationRepository.save(app);
    }

    public long countByStatus(String status) {
        return applicationRepository.countByStatus(status);
    }

    public long countByType(String type) {
        return applicationRepository.countByType(type);
    }

    private String generateAppNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = sequence.getAndIncrement();
        return "SQ" + date + String.format("%04d", seq);
    }
}
