package com.gxu.aihall.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gxu.aihall.entity.Course;
import com.gxu.aihall.entity.Exam;
import com.gxu.aihall.repository.CourseRepository;
import com.gxu.aihall.repository.ExamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课表与考试服务
 */
@Service
public class ScheduleService {

    private final CourseRepository courseRepository;
    private final ExamRepository examRepository;
    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;
    private final PlatformBindService platformBindService;

    public ScheduleService(CourseRepository courseRepository, ExamRepository examRepository,
                           OllamaService ollamaService, ObjectMapper objectMapper,
                           PlatformBindService platformBindService) {
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.ollamaService = ollamaService;
        this.objectMapper = objectMapper;
        this.platformBindService = platformBindService;
    }

    /**
     * 获取学生个人课表（仅该学生自己导入的课程，未导入则为空）
     */
    public List<Course> getStudentCourses(Long studentId) {
        return courseRepository.findByStudentId(studentId);
    }

    /**
     * 获取公共课程（保留方法，当前课表页不使用）
     */
    public List<Course> getPublicCourses() {
        return courseRepository.findByStudentIdIsNull();
    }

    /**
     * 获取学生考试安排
     */
    public List<Exam> getStudentExams(Long studentId) {
        return examRepository.findByStudentIdOrStudentIdIsNullOrderByExamTimeDesc(studentId);
    }

    /**
     * 获取全校考试安排
     */
    public List<Exam> getPublicExams() {
        return examRepository.findByStudentIdIsNullOrderByExamTimeDesc();
    }

    /**
     * 批量保存学生课表（先清空该学生旧课表，再保存新的）
     */
    @Transactional
    public List<Course> saveStudentCourses(Long studentId, List<Course> courses) {
        courseRepository.deleteByStudentId(studentId);
        if (courses == null || courses.isEmpty()) {
            return new ArrayList<>();
        }
        for (Course c : courses) {
            c.setId(null);
            c.setStudentId(studentId);
            if (c.getDayOfWeek() == null) c.setDayOfWeek(1);
            if (c.getStartSection() == null) c.setStartSection(1);
            if (c.getEndSection() == null) c.setEndSection(c.getStartSection());
        }
        return courseRepository.saveAll(courses);
    }

    // ==================== 从教务系统同步课表和考试 ====================

    private static final String JWXT_BASE = "https://jwxt2018.gxu.edu.cn/jwglxt";
    private static final String JWXT_SCHEDULE_API = JWXT_BASE + "/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151";
    private static final String JWXT_EXAM_API = JWXT_BASE + "/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N305005";

    /**
     * 从教务系统同步课表和考试安排。
     * 用已保存的加密密码重新登录教务系统，抓取课表和考试数据，存入本地数据库。
     */
    @Transactional
    public SyncResult syncFromJwxt(Long studentId) throws Exception {
        // 1. 重新登录教务系统获取有效会话
        PlatformBindService.BindClient bc = platformBindService.jwxtRelogin(studentId);

        // 2. 计算当前学年学期
        int[] xnxq = currentXnxq();
        String xnm = String.valueOf(xnxq[0]);
        String xqm = String.valueOf(xnxq[1]);
        String semester = xnm + "-" + (xnm + 1) + "-" + (xqm.equals("3") ? "1" : "2");

        // 3. 抓取课表
        String scheduleBody = "xnm=" + xnm + "&xqm=" + xqm + "&kzlx=ck";
        String scheduleReferer = JWXT_BASE + "/xsgrkbcx/xskgrkbcx_cxXsgrkbIndex.html?gnmkdm=N2151&su=" + bc.username;
        String scheduleJson = platformBindService.postFormWithClient(bc, JWXT_SCHEDULE_API, scheduleBody, scheduleReferer);
        List<Course> courses = parseJwxtCourses(scheduleJson, studentId, semester);

        // 4. 抓取考试（jqGrid 风格接口，需要 page/rows/nd 参数）
        String examBody = "xnm=" + xnm + "&xqm=" + xqm + "&page=1&rows=100&sidx=&sord=asc&nd=" + System.currentTimeMillis();
        String examReferer = JWXT_BASE + "/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N305005&su=" + bc.username;
        String examJson = platformBindService.postFormWithClient(bc, JWXT_EXAM_API, examBody, examReferer);
        List<Exam> exams = parseJwxtExams(examJson, studentId, semester);

        // 5. 保存到数据库（先清空旧数据）
        courseRepository.deleteByStudentId(studentId);
        if (!courses.isEmpty()) {
            courseRepository.saveAll(courses);
        }
        examRepository.deleteByStudentId(studentId);
        if (!exams.isEmpty()) {
            examRepository.saveAll(exams);
        }

        return new SyncResult(courses.size(), exams.size(), semester);
    }

    /** 计算当前学年学期：[0]=学年(开始年份), [1]=学期(3=第一学期,12=第二学期) */
    private int[] currentXnxq() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();
        if (month >= 9) {
            // 9月及以后：第一学期，学年=当前年份
            return new int[]{year, 3};
        } else if (month >= 2) {
            // 2-8月：第二学期，学年=上一年
            return new int[]{year - 1, 12};
        } else {
            // 1月：第一学期（上一年的第一学期）
            return new int[]{year - 1, 3};
        }
    }

    /** 解析教务系统课表 JSON */
    private List<Course> parseJwxtCourses(String json, Long studentId, String semester) {
        List<Course> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode kbList = root.path("kbList");
            if (!kbList.isArray()) return result;

            for (JsonNode item : kbList) {
                Course c = new Course();
                c.setCourseName(text(item, "kcmc"));
                c.setTeacher(text(item, "xm"));
                c.setClassroom(text(item, "cdmc"));
                c.setCredits(text(item, "xf"));
                c.setWeekRange(text(item, "zcd"));
                c.setSemester(semester);
                c.setStudentId(studentId);

                // 星期几
                String xqj = text(item, "xqj");
                if (!xqj.isEmpty()) {
                    try { c.setDayOfWeek(Integer.parseInt(xqj)); } catch (Exception ignored) {}
                }

                // 节次：jcs 格式如 "1-2"，jc 格式如 "1-2节"
                String jcs = text(item, "jcs");
                String jc = text(item, "jc");
                String sectionStr = !jcs.isEmpty() ? jcs : jc;
                if (!sectionStr.isEmpty()) {
                    // 去掉"节"字，按 "-" 分割
                    sectionStr = sectionStr.replace("节", "").trim();
                    String[] parts = sectionStr.split("-");
                    if (parts.length >= 1) {
                        try { c.setStartSection(Integer.parseInt(parts[0].trim())); } catch (Exception ignored) {}
                    }
                    if (parts.length >= 2) {
                        try { c.setEndSection(Integer.parseInt(parts[1].trim())); } catch (Exception ignored) {}
                    } else {
                        c.setEndSection(c.getStartSection());
                    }
                }
                if (c.getStartSection() == null) c.setStartSection(1);
                if (c.getEndSection() == null) c.setEndSection(c.getStartSection());

                if (c.getCourseName() != null && !c.getCourseName().isEmpty()) {
                    result.add(c);
                }
            }
        } catch (Exception e) {
            // 解析失败返回空列表
        }
        return result;
    }

    /** 解析教务系统考试 JSON */
    private List<Exam> parseJwxtExams(String json, Long studentId, String semester) {
        List<Exam> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        try {
            JsonNode root = objectMapper.readTree(json);
            // 考试数据可能在 items 或 rows 中
            JsonNode items = root.has("items") ? root.path("items") :
                    (root.has("rows") ? root.path("rows") : root);
            if (!items.isArray()) return result;

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (JsonNode item : items) {
                Exam e = new Exam();
                e.setExamName(text(item, "kcmc"));
                e.setLocation(text(item, "cdmc"));
                e.setSeatNumber(text(item, "zwh"));
                e.setExamType(text(item, "kslb"));
                e.setSemester(semester);
                e.setStudentId(studentId);

                // 考试时间：kssj 格式如 "2026-01-23(09:00-11:00)"
                String kssj = text(item, "kssj");
                if (!kssj.isEmpty()) {
                    try {
                        // 提取日期和开始时间
                        Matcher m = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*\\(?\\s*(\\d{2}:\\d{2})").matcher(kssj);
                        if (m.find()) {
                            String dateStr = m.group(1);
                            String timeStr = m.group(2);
                            e.setExamTime(LocalDateTime.parse(dateStr + " " + timeStr, fmt));
                        }
                    } catch (Exception ignored) {}
                }

                if (e.getExamName() != null && !e.getExamName().isEmpty()) {
                    result.add(e);
                }
            }
        } catch (Exception e) {
            // 解析失败返回空列表
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
    }

    /** 同步结果 */
    public static class SyncResult {
        private int courseCount;
        private int examCount;
        private String semester;

        public SyncResult(int courseCount, int examCount, String semester) {
            this.courseCount = courseCount;
            this.examCount = examCount;
            this.semester = semester;
        }

        public int getCourseCount() { return courseCount; }
        public int getExamCount() { return examCount; }
        public String getSemester() { return semester; }
    }

    /**
     * 调用本地大模型从文本中解析课表，先判断是否为课表，再提取课程
     */
    public ParseResult parseScheduleFromText(String text) {
        String systemPrompt = "你是一个大学课表解析专家。输入的课表文本已按星期分列整理，格式为【星期一】【星期二】...【星期日】，每个标题下是该天的所有课程块。"
                + "每个课程块包含：课程名称（可能带○★◆●等标记，需去掉）、节次范围如\"(1-2节)\"或\"(5-8节)\"、周次范围如\"3-4周,6-17周\"或\"1-16周\"、教室如\"场地:西综合508\"或\"场地:6A-416\"、教师如\"教师:华蓓\"、学分如\"学分:3.0\"。"
                + "节次编号范围是1-12（上午1-4，下午5-8，晚上9-12）。同一天同一门课如果在不同周次或不同教室出现多次，应拆分为多条课程记录。"
                + "末尾【实践课程】中的课程格式为\"课程名●教师(共N周)/周次范围\"，也需解析为课程记录（节次可留空或设为1-2，周次从文本提取）。"
                + "请先判断输入是否为课表。如果不是课表（如通知、公告、论文等），输出：{\"isSchedule\":false,\"reason\":\"简短说明为什么不是课表\",\"courses\":[]}。"
                + "如果是课表，输出：{\"isSchedule\":true,\"reason\":\"\",\"courses\":[...课程数组...]}。"
                + "每个课程对象包含：courseName(课程名,必填,去掉○★◆●等后缀标记), teacher(教师,从\"教师:XXX\"提取), classroom(教室,从\"场地:XXX\"提取), credits(学分,字符串,从\"学分:X.X\"提取), "
                + "dayOfWeek(星期几,1-7整数,必填,根据所在【星期X】标题确定,星期一=1...星期日=7), startSection(开始节次,1-12整数,必填,从\"(X-Y节)\"提取X), endSection(结束节次,1-12整数,必填,从\"(X-Y节)\"提取Y), "
                + "weekRange(周次范围,如\"3-4周,6-17周\"), semester(学期,如\"2026-2027-1\"), description(其他备注信息)。"
                + "只输出 JSON 对象本身，不要输出任何其他文字、解释或 markdown 代码块标记。";

        String raw = ollamaService.chat(systemPrompt, text, new ArrayList<>());
        if (raw == null || raw.isBlank()) {
            return new ParseResult(false, "大模型返回为空", new ArrayList<>());
        }

        String json = extractJsonObject(raw);
        if (json == null) {
            return new ParseResult(false, "无法解析大模型返回内容", new ArrayList<>());
        }

        try {
            var node = objectMapper.readTree(json);
            boolean isSchedule = node.path("isSchedule").asBoolean(false);
            String reason = node.path("reason").asText("");
            if (!isSchedule) {
                return new ParseResult(false, reason.isEmpty() ? "未识别到课表内容" : reason, new ArrayList<>());
            }
            List<Course> courses = objectMapper.convertValue(
                    node.path("courses"), new TypeReference<List<Course>>() {});
            return new ParseResult(true, "", courses != null ? courses : new ArrayList<>());
        } catch (Exception e) {
            return new ParseResult(false, "解析失败：" + e.getMessage(), new ArrayList<>());
        }
    }

    /**
     * 解析结果
     */
    public static class ParseResult {
        private boolean isSchedule;
        private String reason;
        private List<Course> courses;

        public ParseResult(boolean isSchedule, String reason, List<Course> courses) {
            this.isSchedule = isSchedule;
            this.reason = reason;
            this.courses = courses;
        }

        public boolean isSchedule() { return isSchedule; }
        public String getReason() { return reason; }
        public List<Course> getCourses() { return courses; }
    }

    /**
     * 从模型输出中提取 JSON 对象字符串
     */
    private String extractJsonObject(String text) {
        // 优先找 ```json ... ``` 代码块
        Pattern codeBlock = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher m = codeBlock.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        // 找第一个 { 到最后一个 }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
}
