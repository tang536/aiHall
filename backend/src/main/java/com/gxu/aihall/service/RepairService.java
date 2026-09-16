package com.gxu.aihall.service;

import com.gxu.aihall.dto.RepairRequest;
import com.gxu.aihall.entity.RepairOrder;
import com.gxu.aihall.repository.RepairOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 报修服务
 */
@Service
public class RepairService {

    private final RepairOrderRepository repairOrderRepository;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public RepairService(RepairOrderRepository repairOrderRepository) {
        this.repairOrderRepository = repairOrderRepository;
    }

    /**
     * 提交报修
     */
    public RepairOrder submit(RepairRequest request, Long studentId) {
        RepairOrder order = new RepairOrder();
        order.setOrderNo(generateOrderNo());
        order.setFaultType(request.getFaultType());
        order.setLocation(request.getLocation());
        order.setDescription(request.getDescription());
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            order.setImageUrls(String.join(",", request.getImageUrls()));
        }
        order.setStudentId(studentId);
        order.setContactName(request.getContactName());
        order.setContactPhone(request.getContactPhone());
        order.setStatus("SUBMITTED");
        order.setExpectCompleteTime(LocalDateTime.now().plusDays(3));
        return repairOrderRepository.save(order);
    }

    /**
     * 根据单号查询
     */
    public RepairOrder getByOrderNo(String orderNo) {
        return repairOrderRepository.findByOrderNo(orderNo).orElse(null);
    }

    /**
     * 获取学生报修列表
     */
    public List<RepairOrder> getByStudentId(Long studentId) {
        return repairOrderRepository.findByStudentIdOrderByCreateTimeDesc(studentId);
    }

    /**
     * 获取所有报修（管理员）
     */
    public List<RepairOrder> getAll() {
        return repairOrderRepository.findAllByOrderByCreateTimeDesc();
    }

    /**
     * 按状态筛选
     */
    public List<RepairOrder> getByStatus(String status) {
        return repairOrderRepository.findByStatusOrderByCreateTimeDesc(status);
    }

    /**
     * 更新状态
     */
    public RepairOrder updateStatus(Long id, String status, String remark) {
        RepairOrder order = repairOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("报修单不存在"));
        order.setStatus(status);
        if (remark != null) order.setHandlerRemark(remark);
        order.setUpdateTime(LocalDateTime.now());
        if ("COMPLETED".equals(status)) {
            order.setCompleteTime(LocalDateTime.now());
        }
        return repairOrderRepository.save(order);
    }

    /**
     * 评价
     */
    public RepairOrder evaluate(Long id, Integer rating, String evaluation) {
        RepairOrder order = repairOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("报修单不存在"));
        order.setRating(rating);
        order.setEvaluation(evaluation);
        order.setStatus("EVALUATED");
        order.setUpdateTime(LocalDateTime.now());
        return repairOrderRepository.save(order);
    }

    /**
     * 统计
     */
    public long countByStatus(String status) {
        return repairOrderRepository.countByStatus(status);
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = sequence.getAndIncrement();
        return "BX" + date + String.format("%04d", seq);
    }
}
