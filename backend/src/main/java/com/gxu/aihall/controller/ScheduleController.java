package com.gxu.aihall.controller;

import com.gxu.aihall.common.Result;
import com.gxu.aihall.entity.Course;
import com.gxu.aihall.entity.Exam;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.ScheduleService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final AuthService authService;

    public ScheduleController(ScheduleService scheduleService, AuthService authService) {
        this.scheduleService = scheduleService;
        this.authService = authService;
    }

    @GetMapping("/courses")
    public Result<List<Course>> getCourses(@RequestHeader(value = "Authorization", required = false) String token) {
        Long studentId = getStudentId(token);
        if (studentId == null) {
            // 未登录默认空课表
            return Result.success(new ArrayList<>());
        }
        return Result.success(scheduleService.getStudentCourses(studentId));
    }

    @GetMapping("/exams")
    public Result<List<Exam>> getExams(@RequestHeader(value = "Authorization", required = false) String token) {
        Long studentId = getStudentId(token);
        if (studentId != null) {
            return Result.success(scheduleService.getStudentExams(studentId));
        }
        return Result.success(scheduleService.getPublicExams());
    }

    /**
     * 批量保存学生课表（导入用，先清空旧课表再保存）
     */
    @PostMapping("/courses/batch")
    public Result<List<Course>> batchSaveCourses(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody List<Course> courses) {
        Long studentId = getStudentId(token);
        if (studentId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(scheduleService.saveStudentCourses(studentId, courses));
    }

    /**
     * 调用本地大模型从 PDF 提取的文本中解析课表，先判断是否为课表再提取课程
     */
    @PostMapping("/parse")
    public Result<ScheduleService.ParseResult> parseSchedule(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return Result.error(400, "文本内容为空");
        }
        return Result.success(scheduleService.parseScheduleFromText(text));
    }

    /**
     * 从教务系统同步课表和考试安排
     */
    @PostMapping("/sync-jwxt")
    public Result<ScheduleService.SyncResult> syncFromJwxt(
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long studentId = getStudentId(token);
        if (studentId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            ScheduleService.SyncResult result = scheduleService.syncFromJwxt(studentId);
            return Result.success("同步成功：课表 " + result.getCourseCount() + " 门，考试 " + result.getExamCount() + " 场", result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("同步失败：" + e.getMessage());
        }
    }

    private Long getStudentId(String token) {
        if (token == null || token.isEmpty()) return null;
        var user = authService.getUserByBearerToken(token);
        return user != null ? user.getId() : null;
    }
}
