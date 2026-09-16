package com.gxu.aihall.config;

import com.gxu.aihall.entity.*;
import com.gxu.aihall.repository.*;
import com.gxu.aihall.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器 - 首次启动时注入系统基础数据（管理员账号 + 公共数据）
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final NotificationRepository notificationRepository;
    private final KnowledgeDocRepository knowledgeDocRepository;
    private final PostCategoryRepository postCategoryRepository;

    public DataInitializer(UserRepository userRepository,
                           LocationRepository locationRepository,
                           NotificationRepository notificationRepository,
                           KnowledgeDocRepository knowledgeDocRepository,
                           PostCategoryRepository postCategoryRepository) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.notificationRepository = notificationRepository;
        this.knowledgeDocRepository = knowledgeDocRepository;
        this.postCategoryRepository = postCategoryRepository;
    }

    @Override
    public void run(String... args) {
        // 每次启动先兜底：把历史明文密码升级为 BCrypt 加密存储
        upgradeLegacyPasswords();
        // 兜底：二手交易上线前的老用户 balance 列为 NULL，统一补 0
        backfillBalance();
        initPostCategories();

        if (userRepository.count() > 0) {
            log.info("数据已存在，跳过初始化");
            return;
        }
        log.info("开始初始化系统基础数据...");
        initAdmin();
        initLocations();
        initNotifications();
        initKnowledgeBase();
        log.info("系统基础数据初始化完成！");
    }

    /**
     * 历史数据兼容：将仍为明文存储的密码统一升级为 BCrypt 哈希
     */
    private void upgradeLegacyPasswords() {
        int upgraded = 0;
        for (User user : userRepository.findAll()) {
            if (!PasswordUtil.isEncoded(user.getPassword())) {
                user.setPassword(PasswordUtil.encode(user.getPassword()));
                user.setUpdateTime(java.time.LocalDateTime.now());
                userRepository.save(user);
                upgraded++;
            }
        }
        if (upgraded > 0) {
            log.info("已将 {} 个用户的明文密码升级为 BCrypt 加密存储", upgraded);
        }
    }

    /**
     * 历史数据兼容：余额字段上线前的老用户该列为 NULL，统一补 0，
     * 避免后续余额计算与展示出现空指针。
     */
    private void backfillBalance() {
        int fixed = 0;
        for (User user : userRepository.findAll()) {
            if (user.getBalance() == null) {
                user.setBalance(java.math.BigDecimal.ZERO);
                userRepository.save(user);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("已将 {} 个用户的余额初始化为 0", fixed);
        }
    }

    private void initAdmin() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(PasswordUtil.encode("123456"));
        admin.setRealName("系统管理员");
        admin.setRole("ADMIN");
        admin.setCollege("计算机与信息技术学院");
        admin.setPhone("13800000000");
        admin.setEmail("admin@gxu.edu.cn");
        userRepository.save(admin);
        // 不把初始口令写进日志：日志会被采集/转发，明文口令一旦落进日志系统就等于泄露
        log.info("管理员账号初始化完成：admin（初始口令见部署文档，请首次登录后立即修改）");
    }

    private void initLocations() {
        Object[][] locations = {
                {"第一教学楼", "教学", "校园东区", 108.2960, 22.8410, "主要教学楼，含计算机学院教室", "周一至周日 7:00-22:30", "0771-3234567", null, 1, "第一教学楼"},
                {"第二教学楼", "教学", "校园东区", 108.2965, 22.8415, "公共课教学楼", "周一至周日 7:00-22:30", "0771-3234568", null, 1, "第二教学楼"},
                {"第三教学楼", "教学", "校园中区", 108.2950, 22.8405, "研究生教学楼", "周一至周日 7:00-22:00", "0771-3234569", null, 1, "第三教学楼"},
                {"图书馆", "服务", "校园中心", 108.2940, 22.8400, "校图书馆，藏书300万册", "周一至周日 8:00-22:00", "0771-3234560", null, 1, "图书馆"},
                {"学生宿舍1栋", "生活", "校园西区", 108.2920, 22.8390, "本科生宿舍", "全天", "0771-3234570", null, 1, "宿舍区"},
                {"学生宿舍2栋", "生活", "校园西区", 108.2922, 22.8392, "本科生宿舍", "全天", "0771-3234571", null, 1, "宿舍区"},
                {"学生宿舍3栋", "生活", "校园西区", 108.2924, 22.8394, "研究生宿舍", "全天", "0771-3234572", null, 1, "宿舍区"},
                {"第一食堂", "餐饮", "校园东区", 108.2955, 22.8408, "大众食堂，提供早中晚餐", "6:30-20:00", "0771-3234580", null, 1, "食堂区"},
                {"第二食堂", "餐饮", "校园西区", 108.2925, 22.8395, "特色食堂，含清真窗口", "6:30-21:00", "0771-3234581", null, 1, "食堂区"},
                {"校园服务大厅", "服务", "校园中区", 108.2945, 22.8402, "一站式学生服务大厅", "周一至周五 9:00-17:00", "0771-3234590", null, 1, "服务大厅"},
                {"体育馆", "运动", "校园北区", 108.2950, 22.8420, "室内体育馆，含篮球场、羽毛球场", "8:00-22:00", "0771-3234600", null, 1, "体育馆"},
                {"田径场", "运动", "校园北区", 108.2955, 22.8425, "标准400米田径场", "6:00-22:00", null, null, 1, "田径场"},
                {"校医院", "服务", "校园南区", 108.2935, 22.8385, "校医院，提供基础医疗服务", "8:00-17:30（急诊24小时）", "0771-3234610", null, 1, "校医院"},
                {"ATM（中国银行）", "服务", "第一食堂旁", 108.2956, 22.8407, "中国银行ATM取款机", "24小时", "95566", null, 1, "食堂区"},
                {"超市", "生活", "宿舍1栋一楼", 108.2921, 22.8391, "校园超市，日用品和零食", "7:00-23:00", null, null, 1, "宿舍区"},
                {"计算机与信息技术学院", "教学", "第一教学楼5楼", 108.2961, 22.8411, "学院办公区和实验室", "周一至周五 8:00-18:00", "0771-3234620", null, 5, "第一教学楼"},
        };
        for (int i = 0; i < locations.length; i++) {
            Object[] l = locations[i];
            Location loc = new Location();
            loc.setName((String) l[0]);
            loc.setCategory((String) l[1]);
            loc.setAddress((String) l[2]);
            loc.setLongitude((Double) l[3]);
            loc.setLatitude((Double) l[4]);
            loc.setDescription((String) l[5]);
            loc.setOpenHours((String) l[6]);
            loc.setPhone((String) l[7]);
            loc.setImageUrl((String) l[8]);
            loc.setFloor((Integer) l[9]);
            loc.setBuilding((String) l[10]);
            loc.setSortOrder(i);
            locationRepository.save(loc);
        }
        log.info("校园地点数据初始化完成：{}个地点", locations.length);
    }

    private void initNotifications() {
        Object[][] notifications = {
                {"关于2025-2026学年第二学期期末考试安排的通知", "教务", "教务处", true, false,
                        "各学院、各班级：现将2025-2026学年第二学期期末考试安排通知如下：考试时间为第18-20周，请同学们提前做好复习准备。具体考试安排请登录教务系统查询。",
                        "期末考试安排通知，请同学们及时查看。"},
                {"关于开展2026年家庭经济困难学生认定工作的通知", "学工", "学生工作处", false, false,
                        "各学院：为做好2026年家庭经济困难学生认定工作，现将有关事项通知如下：一、认定对象为全日制在校本科生；二、申请时间为9月15日-9月30日；三、需提交《家庭经济困难学生认定申请表》及相关证明材料。",
                        "家庭经济困难学生认定工作开始，请符合条件的同学及时申请。"},
                {"关于2026年国庆节放假安排的通知", "学工", "学校办公室", true, false,
                        "根据国务院办公厅通知，结合学校实际，2026年国庆节放假安排如下：10月1日至7日放假调休，共7天。10月10日（星期六）上班上课。请各单位做好值班和安全工作。",
                        "国庆节放假7天，请合理安排假期。"},
                {"关于图书馆电子资源使用培训的通知", "教务", "图书馆", false, false,
                        "为帮助同学们更好地利用图书馆电子资源，图书馆将举办电子资源使用培训。时间：每周三下午15:00；地点：图书馆三楼电子阅览室；内容：数据库检索、文献管理软件使用等。欢迎同学们参加。",
                        "图书馆电子资源培训，欢迎参加。"},
        };
        for (Object[] n : notifications) {
            Notification notif = new Notification();
            notif.setTitle((String) n[0]);
            notif.setCategory((String) n[1]);
            notif.setDepartment((String) n[2]);
            notif.setIsTop((Boolean) n[3]);
            notif.setIsEmergency((Boolean) n[4]);
            notif.setContent((String) n[5]);
            notif.setSummary((String) n[6]);
            notif.setPublisherId(1L);
            notif.setStatus(1);
            notif.setViewCount(0);
            notificationRepository.save(notif);
        }
        log.info("通知公告数据初始化完成：{}条", notifications.length);
    }

    private void initKnowledgeBase() {
        Object[][] docs = {
                {"家庭经济困难学生认定办法", "规章制度", "学校官网",
                        "家庭经济困难学生认定工作坚持实事求是、客观公平的原则。认定等级分为特别困难、比较困难、一般困难三档。申请条件：1. 家庭经济困难，无力支付学习和生活基本费用；2. 遵守学校规章制度，品行良好；3. 学习努力，积极向上。认定程序：个人申请→班级评议→学院审核→学校审定。所需材料：《家庭经济困难学生认定申请表》、家庭经济困难证明、低保证或建档立卡证明等。",
                        "贫困认定,家庭经济困难,助学金,申请条件,认定办法", 10},
                {"国家奖学金评审办法", "奖助政策", "学生工作处",
                        "国家奖学金用于奖励特别优秀的全日制本专科学生。奖励标准为每人每年8000元。申请条件：1. 热爱社会主义祖国，拥护中国共产党的领导；2. 遵守宪法和法律，遵守学校规章制度；3. 诚实守信，道德品质优良；4. 在校期间学习成绩优异，社会实践、创新能力、综合素质等方面特别突出。评审程序：个人申请→学院评审→学校审核→报教育部审批。",
                        "国家奖学金,8000元,奖学金,评审,优秀学生", 9},
                {"国家励志奖学金评审办法", "奖助政策", "学生工作处",
                        "国家励志奖学金用于奖励资助品学兼优的家庭经济困难全日制本专科学生。奖励标准为每人每年5000元。申请条件：1. 热爱社会主义祖国，拥护中国共产党的领导；2. 遵守宪法和法律，遵守学校规章制度；3. 诚实守信，道德品质优良；4. 在校期间学习成绩优秀；5. 家庭经济困难，生活俭朴。同一学年内，获得国家励志奖学金的学生可以同时申请并获得国家助学金，但不能同时获得国家奖学金。",
                        "励志奖学金,5000元,品学兼优,家庭困难,助学金", 8},
                {"学生请假管理规定", "办事流程", "教务处",
                        "学生请假分为病假、事假、公假三种。请假审批权限：1. 请假1天以内，由辅导员审批；2. 请假2-3天，由学院分管学生工作的领导审批；3. 请假4-7天，由学院分管教学工作的领导审批；4. 请假超过7天，由教务处审批。请假流程：填写《学生请假申请表》→附相关证明材料→按审批权限逐级审批→审批通过后交辅导员备案。病假需附校医院或县级以上医院诊断证明。请假期满应及时销假。",
                        "请假,病假,事假,审批,流程,辅导员", 10},
                {"在读证明办理指南", "办事流程", "教务处",
                        "在读证明用于证明学生在校就读身份。办理方式：1. 线上办理：登录教务系统→学生服务→证明开具→选择在读证明→提交申请→审核通过后可下载电子版；2. 线下办理：携带学生证到教务处学籍管理科（第一教学楼2楼）办理。办理时间：工作日9:00-11:30，15:00-17:00。中英文对照证明需额外注明用途。一般1-2个工作日办结。",
                        "在读证明,学籍,证明开具,教务处,办理", 9},
                {"成绩单办理指南", "办事流程", "教务处",
                        "成绩单分为中文成绩单和中英文对照成绩单。办理方式：1. 自助打印：使用校园卡在自助打印机（图书馆一楼、第一教学楼大厅）打印，免费打印5次/学期；2. 线上申请：登录教务系统申请，审核后到教务处领取；3. 线下办理：携带学生证到教务处办理。盖章成绩单用于出国、就业等用途，需到教务处盖章。中英文对照成绩单办理需3个工作日。",
                        "成绩单,打印,盖章,中英文,自助打印", 9},
                {"宿舍报修服务指南", "办事流程", "后勤管理处",
                        "宿舍设施损坏可通过以下方式报修：1. 线上报修：通过校园服务平台提交报修申请，说明故障类型、位置和描述；2. 电话报修：拨打后勤维修热线0771-3234567；3. 现场报修：到宿舍管理处登记。报修范围：水电故障、门窗损坏、家具维修、空调故障、网络问题等。维修响应时间：一般问题24小时内响应，紧急问题（如水管爆裂、电路故障）2小时内响应。维修完成后请进行满意度评价。",
                        "报修,宿舍,维修,水电,后勤,故障", 10},
                {"校园卡办理与使用指南", "办事流程", "信息网络中心",
                        "校园卡是学生在校期间的重要证件，可用于食堂消费、图书馆借阅、宿舍门禁、考试身份验证等。办理：新生入学时统一办理，遗失补办需到校园卡服务中心（第一食堂旁）办理，工本费20元。充值：可通过校园APP线上充值，或在食堂充值点现金充值。挂失：发现校园卡遗失应立即通过校园APP或到服务中心挂失，避免被盗刷。校园卡服务中心工作时间：工作日9:00-17:00。",
                        "校园卡,补办,充值,挂失,消费,门禁", 8},
                {"转专业管理办法", "规章制度", "教务处",
                        "学生可在第一学年第二学期申请转专业。申请条件：1. 在读一年级全日制本科生；2. 思想品德良好，遵纪守法；3. 第一学期所学课程成绩全部及格；4. 身体条件符合转入专业要求。申请流程：1. 学生向所在学院提出申请，填写《转专业申请表》；2. 所在学院审核同意；3. 转入学院组织考核（笔试+面试）；4. 教务处审批并公示。每个学生只能转专业一次。艺术类、体育类专业不得转入普通类专业。",
                        "转专业,申请,考核,教务处,一年级", 7},
                {"学生工作处联系方式", "部门联系方式", "学校官网",
                        "学生工作处：办公地点-校园服务大厅2楼；电话-0771-3234500；邮箱-xsc@gxu.edu.cn；负责奖助学金、学生管理、心理健康教育等。\n教务处：办公地点-第一教学楼2楼；电话-0771-3234501；邮箱-jwc@gxu.edu.cn；负责学籍管理、课程安排、考试安排、证明开具等。\n后勤管理处：办公地点-后勤楼；电话-0771-3234567；邮箱-hqch@gxu.edu.cn；负责宿舍管理、报修服务、食堂管理等。\n图书馆：电话-0771-3234560；负责图书借阅、电子资源等。\n校医院：电话-0771-3234610（急诊24小时）。",
                        "联系方式,学工处,教务处,后勤,图书馆,校医院,电话", 10},
        };
        for (Object[] d : docs) {
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setTitle((String) d[0]);
            doc.setCategory((String) d[1]);
            doc.setSource((String) d[2]);
            doc.setContent((String) d[3]);
            doc.setKeywords((String) d[4]);
            doc.setPriority((Integer) d[5]);
            doc.setUploaderId(1L);
            doc.setStatus(1);
            knowledgeDocRepository.save(doc);
        }
        log.info("知识库数据初始化完成：{}条文档", docs.length);
    }

    /**
     * 初始化论坛帖子默认分类（每次启动兜底，已存在则跳过）
     */
    private void initPostCategories() {
        String[][] defaults = {
            {"CAMPUS", "校园动态", "1"},
            {"STUDY", "学习交流", "2"},
            {"LOST", "失物互助", "3"},
            {"HELP", "求助问答", "4"},
            {"OTHER", "其他", "99"}
        };
        for (String[] d : defaults) {
            if (!postCategoryRepository.existsByCode(d[0])) {
                PostCategory cat = new PostCategory();
                cat.setCode(d[0]);
                cat.setName(d[1]);
                cat.setSort(Integer.parseInt(d[2]));
                cat.setStatus(1);
                cat.setCreateTime(java.time.LocalDateTime.now());
                cat.setUpdateTime(java.time.LocalDateTime.now());
                postCategoryRepository.save(cat);
                log.info("初始化帖子分类: {} - {}", d[0], d[1]);
            }
        }
    }
}
