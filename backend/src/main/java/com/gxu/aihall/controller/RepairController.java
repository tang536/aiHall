package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.dto.RepairRequest;
import com.gxu.aihall.entity.RepairOrder;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.RepairService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair")
public class RepairController {

    private final RepairService repairService;
    private final AuthService authService;

    public RepairController(RepairService repairService, AuthService authService) {
        this.repairService = repairService;
        this.authService = authService;
    }

    @PostMapping("/submit")
    public Result<RepairOrder> submit(@RequestBody RepairRequest request,
                                       @RequestHeader(value = "Authorization", required = false) String token) {
        Long studentId = null;
        if (token != null) {
            var user = authService.getUserByBearerToken(token);
            if (user != null) {
                studentId = user.getId();
                if (request.getContactName() == null) request.setContactName(user.getRealName());
                if (request.getContactPhone() == null) request.setContactPhone(user.getPhone());
            }
        }
        RepairOrder order = repairService.submit(request, studentId);
        return Result.success("报修提交成功，报修单号：" + order.getOrderNo(), order);
    }

    @GetMapping("/order/{orderNo}")
    public Result<RepairOrder> getByOrderNo(@PathVariable String orderNo) {
        RepairOrder order = repairService.getByOrderNo(orderNo);
        if (order == null) return Result.error("报修单号不存在");
        return Result.success(order);
    }

    @GetMapping("/my")
    public Result<List<RepairOrder>> getMyRepairs(@RequestHeader("Authorization") String token) {
        var user = authService.requireLogin(token, "未登录");
        return Result.success(repairService.getByStudentId(user.getId()));
    }

    @PostMapping("/{id}/evaluate")
    public Result<RepairOrder> evaluate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer rating = (Integer) body.get("rating");
        String evaluation = (String) body.get("evaluation");
        return Result.success(repairService.evaluate(id, rating, evaluation));
    }
}
