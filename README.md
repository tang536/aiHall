# AI 学生事务智能办事大厅（aiHall）

> 面向广西大学学生的一站式智能办事平台，基于 **Spring Boot 3 + Vue 3 + MySQL + Ollama (deepseek-r1:7b)** 构建，采用广西大学深红 + 金色视觉风格。

---

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
  - [学生端](#学生端)
  - [管理员端](#管理员端)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [账号说明](#账号说明)
- [API 接口概览](#api-接口概览)
- [数据库实体](#数据库实体)
- [配置说明](#配置说明)
- [部署说明](#部署说明)
- [常见问题](#常见问题)
- [更新日志](#更新日志)

---

## 项目简介

aiHall（AI 学生事务智能办事大厅）是一个整合了 AI 智能问答、事项办理、校园社交、二手交易、论坛交流、地图导航等多功能的一站式校园服务平台。项目采用前后端分离架构，提供学生端和管理员端双端应用，所有数据存储在本地 MySQL 数据库，AI 能力通过本地 Ollama 大模型提供，无需依赖外部云服务。

### 设计理念

- **一站式服务**：将分散的校园事务整合到一个平台，减少学生在多个系统间切换
- **AI 驱动**：基于 RAG 知识库的智能问答，快速解答校园常见问题
- **社交化**：内置好友系统、私聊、论坛、二手交易，构建校园社区生态
- **本地化**：所有数据和 AI 模型均在本地运行，保护隐私，无需外网
- **广西大学风格**：整体 UI 采用广西大学深红（#941e23）+ 金色（#b8935a）+ 米黄（#f7f3ec）配色，衬线体标题

---

## 功能特性

### 学生端

| 模块 | 功能说明 |
|------|----------|
| **AI 智能问答** | 基于校园知识库 + deepseek-r1:7b 大模型的 RAG 问答，支持多轮对话、流式输出、引用来源标注、满意度反馈（点赞/点踩）、快捷提问建议 |
| **统一事项办理** | 奖助学金、请假申请、证明开具等在线申请与审核流转，进度实时追踪，支持查看申请详情和审核意见 |
| **课表与考试** | 支持 Excel/文本导入课表，或绑定广西大学教务系统（JWXT）后一键自动同步个人课表与考试安排；周视图展示（12 节课），同课程同名同色，按周次过滤 |
| **通知公告** | 分类筛选、关键词搜索、置顶/紧急标记、紧急通知弹窗；顶部集成「我的消息」个人通知区（好友申请、商品购买等实时通知），支持标记已读和点击跳转 |
| **校园地图导航** | 广西大学离线地图（Leaflet + 本地瓦片 + 本地路网寻路），无需地图服务 AK，支持地点搜索、分类筛选、步行路线规划、自动定位 |
| **失物招领** | 信息发布与智能匹配（基于关键词 + 类别 + 地点的相似度算法），支持图片上传 |
| **报修登记** | 在线报修、进度追踪、服务评价，支持图片上传 |
| **校园论坛** | 帖子发布（含图片）、分类浏览（校园动态/学习交流/失物互助/求助问答/其他）、帖子评论、帖子收藏、点击作者头像跳转个人主页 |
| **二手交易市场** | 商品发布（含图片）、分类浏览、商品搜索、在线购买、商品收藏、我的收藏、卖家主页跳转、购买后自动通知卖家 |
| **好友系统** | 好友搜索、发送好友申请、申请审批、好友列表、私聊（支持文字和图片）、点击头像跳转个人主页 |
| **个人主页** | 查看用户基本信息、发布的帖子、发布的二手商品，支持加好友和私聊 |
| **个人资料** | 编辑个人信息、绑定教务系统账号、修改密码、查看钱包余额和交易记录 |
| **钱包系统** | 余额充值、消费记录、购买商品自动扣款 |
| **意见反馈** | 提交反馈、查看反馈处理状态 |
| **专注模式** | 需密码进入/退出（密码由管理员在系统设置中维护） |

### 管理员端

| 模块 | 功能说明 |
|------|----------|
| **数据仪表盘** | ECharts 真实统计图表，包含用户增长、申请趋势、报修统计、论坛活跃度、二手交易等数据可视化 |
| **用户管理** | 用户列表、搜索、禁用/启用、重置密码、查看用户详情 |
| **申请审核** | 事项申请列表、审核通过/驳回、查看申请详情 |
| **报修管理** | 报修单列表、分配处理、更新进度、查看评价 |
| **通知管理** | 通知发布、编辑、删除、置顶/紧急标记、**PDF 格式导入通知**（自动提取文本作为正文，按标题去重）、一键加入知识库 |
| **知识库管理** | 知识文档增删改查、分类管理、供 AI 问答检索使用 |
| **论坛管理** | 帖子列表、删除违规帖子、**帖子分类管理**（增删改查、启用/禁用，默认 5 个分类） |
| **二手商品管理** | 商品列表、审核、下架违规商品、查看交易订单 |
| **地点管理** | 地图地点增删改查、分类管理 |
| **系统设置** | 专注模式密码维护、系统参数配置 |
| **反馈管理** | 查看用户反馈、回复处理 |

---

## 项目结构

```
aiHall/
├── backend/                          # Spring Boot 后端（端口 8080）
│   ├── src/main/java/com/gxu/aihall/
│   │   ├── entity/                   # 25 个 JPA 实体
│   │   ├── repository/               # Spring Data JPA 仓储
│   │   ├── service/                  # 业务逻辑层
│   │   ├── controller/               # 24 个 REST 控制器
│   │   ├── config/                   # 配置类（CORS、拦截器、数据初始化等）
│   │   ├── util/                     # 工具类（隐私脱敏、AES 加解密等）
│   │   └── AiHallApplication.java    # 启动类
│   ├── src/main/resources/
│   │   ├── application.yml           # 应用配置
│   │   └── static/                   # 静态资源（地图瓦片等）
│   └── pom.xml
│
├── student-frontend/                 # 学生端 Vue 项目（端口 5173）
│   ├── public/
│   │   ├── favicon.svg               # 广西大学风格 favicon
│   │   └── map-tiles/                # 离线地图瓦片
│   ├── src/
│   │   ├── views/                    # 18 个页面组件
│   │   │   ├── Login.vue             # 登录/注册
│   │   │   ├── Home.vue              # 首页
│   │   │   ├── Chat.vue              # AI 对话
│   │   │   ├── Schedule.vue          # 课表
│   │   │   ├── Applications.vue      # 事项申请
│   │   │   ├── Notifications.vue     # 通知公告 + 我的消息
│   │   │   ├── CampusMap.vue         # 校园地图
│   │   │   ├── LostFound.vue         # 失物招领
│   │   │   ├── Repair.vue            # 报修
│   │   │   ├── Posts.vue             # 论坛列表 + 我的收藏
│   │   │   ├── PostDetail.vue        # 帖子详情
│   │   │   ├── Market.vue            # 二手市场 + 我的收藏
│   │   │   ├── MarketDetail.vue      # 商品详情
│   │   │   ├── Friends.vue           # 好友列表 + 搜索
│   │   │   ├── Messages.vue          # 私聊列表
│   │   │   ├── UserProfile.vue       # 用户主页
│   │   │   ├── Profile.vue           # 个人资料
│   │   │   └── Feedback.vue          # 意见反馈
│   │   ├── components/               # 公共组件
│   │   │   ├── ChatWindow.vue        # 私聊窗口
│   │   │   ├── UserCard.vue          # 用户卡片
│   │   │   └── ErrorBoundary.vue     # 错误边界
│   │   ├── api/index.js              # API 接口定义
│   │   ├── router/index.js           # 路由配置
│   │   ├── stores/                   # Pinia 状态管理
│   │   ├── assets/theme.css          # 广西大学主题样式
│   │   ├── App.vue                   # 根组件
│   │   └── main.js                   # 入口文件
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── admin-frontend/                   # 管理员端 Vue 项目（端口 5174）
│   ├── public/
│   │   ├── favicon.svg               # 广西大学风格 favicon
│   │   └── map-tiles/                # 离线地图瓦片
│   ├── src/
│   │   ├── views/admin/              # 11 个管理页面
│   │   │   ├── Login.vue             # 管理员登录
│   │   │   ├── Dashboard.vue         # 数据仪表盘
│   │   │   ├── UserManage.vue        # 用户管理
│   │   │   ├── ApplicationManage.vue # 申请审核
│   │   │   ├── RepairManage.vue      # 报修管理
│   │   │   ├── NotificationManage.vue# 通知管理（含 PDF 导入）
│   │   │   ├── KnowledgeManage.vue   # 知识库管理
│   │   │   ├── PostManage.vue        # 论坛管理 + 分类管理
│   │   │   ├── MarketManage.vue      # 二手商品管理
│   │   │   ├── LocationManage.vue    # 地点管理
│   │   │   ├── FeedbackManage.vue    # 反馈管理
│   │   │   └── SystemSettings.vue    # 系统设置
│   │   ├── components/
│   │   │   ├── AdminLayout.vue       # 管理端布局
│   │   │   └── ErrorBoundary.vue     # 错误边界
│   │   ├── api/index.js              # API 接口定义
│   │   ├── router/index.js           # 路由配置
│   │   ├── assets/theme.css          # 广西大学主题样式
│   │   ├── App.vue
│   │   └── main.js
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── backups/                          # 数据库备份目录
└── README.md
```

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 开发语言 |
| Spring Boot | 3.3.4 | 应用框架 |
| Spring Data JPA | - | ORM 持久层 |
| MySQL | 8.0+ | 关系型数据库 |
| Spring Security | - | 安全认证（JWT） |
| Apache PDFBox | - | PDF 通知导入解析 |
| Ollama API | - | 本地大模型集成（deepseek-r1:7b） |
| WebSocket | - | 实时私聊消息推送 |
| Caffeine | - | 本地热点缓存 |
| AES | - | 教务系统凭证加密存储 |

### 前端（学生端 + 管理员端）

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4 | 前端框架 |
| Vite | 5 | 构建工具 |
| Element Plus | - | UI 组件库 |
| Vue Router | 4 | 路由管理 |
| Pinia | - | 状态管理 |
| Axios | - | HTTP 客户端 |
| ECharts | 5 | 数据可视化（管理端仪表盘） |
| Leaflet | - | 校园离线地图 |
| xlsx | - | Excel 课表导入 |

### AI 与数据

| 组件 | 说明 |
|------|------|
| Ollama | 本地大模型运行时，默认端口 11434 |
| deepseek-r1:7b | 推理模型，支持中文问答 |
| RAG 检索 | 基于知识库关键词匹配 + 优先级排序 |
| 流式输出 | SSE 逐字返回 AI 回复 |

---

## 快速开始

### 环境要求

| 依赖 | 最低版本 | 说明 |
|------|----------|------|
| JDK | 21 | 后端编译运行 |
| Maven | - | 项目已自带 Maven Wrapper（mvnw.cmd），无需全局安装 |
| Node.js | 18+ | 前端构建（推荐 20+） |
| npm | 9+ | 前端包管理 |
| MySQL | 8.0+ | 数据库（用户名 root，密码 12345） |
| Ollama | 最新 | AI 问答功能（可选，不启动不影响其他功能） |

### 一键启动（推荐 Windows）

项目根目录提供了一键启动脚本，自动完成依赖检查、后端编译、双端启动，并打开浏览器：

```bash
# 双击运行，或在命令行执行
start-all.bat
```

脚本会依次启动：
- **后端服务**：Spring Boot（端口 8080），首次启动自动下载 Maven 依赖
- **学生端**：Vite 开发服务器（端口 5173）
- **管理端**：Vite 开发服务器（端口 5174）

三个服务分别在独立窗口运行，关闭窗口即停止。如需一键停止所有服务，运行 stop-all.bat。

> 前置条件：MySQL 已启动（用户名 root，密码 12345）；AI 问答功能需额外启动 Ollama。
### 1. 配置 MySQL

确保 MySQL 服务已启动，用户名 `root`，密码 `12345`。

数据库 `ai_hall` 会在应用首次启动时**自动创建**（`createDatabaseIfNotExist=true`），所有数据表由 JPA `ddl-auto: update` 自动维护，无需手动建库建表。

如需修改数据库连接，编辑 `backend/src/main/resources/application.yml` 中的 `spring.datasource` 配置，或通过环境变量覆盖：

```bash
set DB_URL=jdbc:mysql://localhost:3306/ai_hall?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
set DB_USERNAME=root
set DB_PASSWORD=your_password
```

### 2. 启动 Ollama（AI 问答功能需要）

```bash
# 拉取模型（约 4.7GB，仅需一次）
ollama pull deepseek-r1:7b

# 启动服务（默认端口 11434）
ollama serve
```

> 不启动 Ollama 也可正常使用系统其他功能，仅 AI 问答会提示「AI 服务暂不可用」。

### 3. 启动后端

```bash
cd backend
mvnw.cmd spring-boot:run
```

后端启动后：
- API 地址：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- 数据库：MySQL（`localhost:3306/ai_hall`）

### 4. 启动学生端前端

```bash
cd student-frontend
npm install
npm run dev
```

访问：`http://localhost:5173`

### 5. 启动管理员端前端

```bash
cd admin-frontend
npm install
npm run dev
```

访问：`http://localhost:5174`

### 6. 局域网访问（可选）

后端已配置 `server.address: 0.0.0.0`，CORS 已放行 `10.2.50.*`、`192.168.*`、`10.1.37.*` 网段。同一局域网内其他机器可通过 `http://<本机IP>:5173` 和 `http://<本机IP>:5174` 访问。

---

## 账号说明

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 管理员 | `admin` | `123456` | 系统预置，登录管理员端 |
| 学生 | 学号 | 自设 | 需在学生端登录页自行注册 |

> 学生账号请通过学生端登录页面的「注册」标签自行注册，注册时需填写学号、真实姓名、密码。同一 IP 注册有 60 秒冷却时间。

---

## API 接口概览

所有接口统一前缀 `/api`，除登录/注册外均需在请求头携带 `Authorization: Bearer <token>`。

### 认证与用户

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 学生登录 |
| POST | `/api/auth/register` | 学生注册 |
| POST | `/api/auth/admin-login` | 管理员登录 |
| GET | `/api/auth/me` | 获取当前用户信息 |
| PUT | `/api/users/profile` | 更新个人资料 |
| POST | `/api/users/change-password` | 修改密码 |

### AI 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/send` | 发送消息（SSE 流式返回） |
| GET | `/api/chat/history` | 获取对话历史 |
| POST | `/api/chat/feedback` | 提交满意度反馈 |

### 事项申请

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/applications` | 获取我的申请列表 |
| POST | `/api/applications` | 提交新申请 |
| GET | `/api/applications/{id}` | 申请详情 |

### 课表与考试

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/schedule/courses` | 获取课表 |
| POST | `/api/schedule/import` | Excel/文本导入课表 |
| POST | `/api/schedule/sync` | 从教务系统同步 |
| GET | `/api/schedule/exams` | 获取考试安排 |

### 通知公告

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notifications` | 通知列表（分类/搜索/分页） |
| GET | `/api/notifications/{id}` | 通知详情 |
| GET | `/api/notifications/personal` | 个人通知列表 |
| PUT | `/api/notifications/personal/{id}/read` | 标记个人通知已读 |

### 校园论坛

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/posts` | 帖子列表（分类/搜索/分页） |
| POST | `/api/posts` | 发布帖子 |
| GET | `/api/posts/{id}` | 帖子详情 |
| DELETE | `/api/posts/{id}` | 删除帖子 |
| POST | `/api/posts/{id}/replies` | 发表评论 |
| GET | `/api/post-categories` | 获取帖子分类列表 |
| POST | `/api/posts/{id}/favorite` | 收藏/取消收藏帖子 |
| GET | `/api/posts/favorites/mine` | 我的收藏帖子 |

### 二手交易

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/market/items` | 商品列表（分类/搜索/分页） |
| POST | `/api/market/items` | 发布商品 |
| GET | `/api/market/items/{id}` | 商品详情 |
| POST | `/api/market/items/{id}/buy` | 购买商品 |
| POST | `/api/market/items/{id}/favorite` | 收藏/取消收藏商品 |
| GET | `/api/market/favorites/mine` | 我的收藏商品 |
| GET | `/api/market/orders/mine` | 我的订单 |

### 好友与私聊

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/friends` | 好友列表 |
| GET | `/api/friends/search?keyword=` | 搜索用户 |
| POST | `/api/friends/request/{userId}` | 发送好友申请 |
| PUT | `/api/friends/request/{id}/accept` | 接受好友申请 |
| PUT | `/api/friends/request/{id}/reject` | 拒绝好友申请 |
| GET | `/api/messages/{userId}` | 获取与某用户的私聊记录 |
| WS | `/ws/chat` | WebSocket 实时私聊 |

### 钱包

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/wallet/balance` | 查询余额 |
| POST | `/api/wallet/recharge` | 充值 |
| GET | `/api/wallet/transactions` | 交易记录 |

### 管理员接口（`/api/admin/**`，需 ADMIN 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/stats/overview` | 仪表盘统计数据 |
| GET | `/api/admin/users` | 用户列表 |
| PUT | `/api/admin/users/{id}/status` | 禁用/启用用户 |
| GET | `/api/admin/applications` | 申请列表 |
| PUT | `/api/admin/applications/{id}/audit` | 审核申请 |
| GET | `/api/admin/repairs` | 报修列表 |
| PUT | `/api/admin/repairs/{id}/status` | 更新报修状态 |
| POST | `/api/admin/notifications` | 发布通知 |
| POST | `/api/admin/notifications/import-pdf` | PDF 导入通知 |
| POST | `/api/admin/notifications/{id}/to-knowledge` | 通知加入知识库 |
| GET | `/api/admin/knowledge` | 知识库列表 |
| POST | `/api/admin/knowledge` | 新增知识文档 |
| GET | `/api/admin/post-categories` | 帖子分类列表 |
| POST | `/api/admin/post-categories` | 新增分类 |
| PUT | `/api/admin/post-categories/{id}` | 更新分类 |
| DELETE | `/api/admin/post-categories/{id}` | 删除分类 |
| GET | `/api/admin/market/items` | 商品列表 |
| PUT | `/api/admin/market/items/{id}/status` | 商品上下架 |
| GET | `/api/admin/locations` | 地点列表 |
| POST | `/api/admin/locations` | 新增地点 |
| GET | `/api/admin/feedbacks` | 反馈列表 |
| PUT | `/api/admin/settings/focus-password` | 设置专注模式密码 |

---

## 数据库实体

共 25 个 JPA 实体，按功能模块分类：

### 用户与认证
- `User` — 用户（学号、真实姓名、密码哈希、角色、状态、余额）
- `UserBinding` — 教务系统绑定（加密存储的账号密码）
- `AuditLog` — 审计日志

### 社交
- `Friendship` — 好友关系（申请人、接收人、状态）
- `PrivateMessage` — 私聊消息（文字/图片）
- `UserNotification` — 个人通知（好友申请、商品购买等）

### 论坛
- `Post` — 帖子（标题、内容、图片、作者、分类）
- `PostReply` — 帖子评论
- `PostCategory` — 帖子分类（名称、排序、启用状态）
- `PostFavorite` — 帖子收藏（用户-帖子关联）

### 二手交易
- `MarketItem` — 二手商品（标题、描述、价格、图片、卖家、状态）
- `MarketOrder` — 交易订单（商品、买家、卖家、金额、状态）
- `MarketFavorite` — 商品收藏（用户-商品关联）
- `BalanceTransaction` — 余额交易记录（充值、消费、退款）

### 事务办理
- `Application` — 事项申请（类型、内容、附件、状态、审核意见）
- `RepairOrder` — 报修单（地点、描述、图片、状态、评价）
- `Feedback` — 意见反馈

### 教务
- `Course` — 课程（名称、教师、时间、地点、周次）
- `Exam` — 考试安排（课程、时间、地点）

### 系统
- `Notification` — 通知公告（标题、内容、分类、置顶、紧急）
- `KnowledgeDoc` — 知识库文档（标题、内容、分类、优先级）
- `Location` — 地图地点（名称、经纬度、分类、描述）
- `LostItem` — 失物招领信息
- `SystemSetting` — 系统设置（键值对）
- `ChatMessage` — AI 对话历史记录

---

## 配置说明

核心配置文件：`backend/src/main/resources/application.yml`

### 数据库

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/ai_hall?...&createDatabaseIfNotExist=true}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:12345}
  jpa:
    hibernate:
      ddl-auto: update    # 自动更新表结构
```

### Ollama 大模型

```yaml
ollama:
  base-url: http://localhost:11434
  model: deepseek-r1:7b
  timeout: 300          # 秒
  temperature: 0.3
  num-ctx: 8192
  num-predict: 1024
```

### JWT 认证

```yaml
app:
  jwt:
    secret: ${APP_JWT_SECRET:...}           # AES 密钥（教务凭证加密）
    signing-key: ${APP_JWT_SIGNING_KEY:...} # JWT 签名密钥
    expire-hours: 24
    renew-before-minutes: 120               # 滑动续期阈值
```

### CORS 跨域

```yaml
app:
  cors:
    allowed-origins: http://localhost:5173,http://localhost:5174,http://10.2.50.*:*,http://192.168.*:*,http://10.1.37.*:*
```

### 安全策略

```yaml
app:
  security:
    login-max-failures: 10       # 连续密码错误锁定次数
    login-lock-minutes: 10       # 锁定时长
    register-cooldown-seconds: 60 # 同 IP 注册冷却
```

### 缓存

```yaml
app:
  cache:
    enabled: true
    list-ttl-seconds: 30        # 列表缓存 TTL
    reference-ttl-seconds: 300  # 引用数据缓存 TTL
    max-entries: 500
```

---

## 部署说明

### 生产环境构建

**后端打包：**

```bash
cd backend
mvnw.cmd clean package -DskipTests
# 产物：target/ai-student-hall-1.0.0.jar
```

**前端构建：**

```bash
# 学生端
cd student-frontend
npm install
npm run build
# 产物：dist/

# 管理员端
cd admin-frontend
npm install
npm run build
# 产物：dist/
```

### 运行

```bash
# 后端
java -jar target/ai-student-hall-1.0.0.jar

# 前端使用 Nginx 或其他静态服务器托管 dist 目录
# 需配置反向代理将 /api 转发到后端 8080 端口
```

### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 学生端
    location / {
        root /path/to/student-frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # 管理员端（可通过子路径或子域名）
    location /admin/ {
        alias /path/to/admin-frontend/dist/;
        try_files $uri $uri/ /admin/index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket
    location /ws/ {
        proxy_pass http://localhost:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

### 环境变量（生产环境务必覆盖）

| 变量 | 说明 |
|------|------|
| `DB_URL` | 数据库连接 URL |
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `APP_JWT_SECRET` | AES 加密密钥 |
| `APP_JWT_SIGNING_KEY` | JWT 签名密钥（≥32 字节） |
| `APP_CORS_ORIGINS` | 允许的跨域来源 |

---

## 常见问题

### Q: 启动后端时报数据库连接失败？
A: 确认 MySQL 服务已启动，用户名密码与 `application.yml` 中一致。默认 `root/12345`，数据库会自动创建。

### Q: AI 问答提示服务不可用？
A: 确认 Ollama 已启动（`ollama serve`）且已拉取 `deepseek-r1:7b` 模型（`ollama pull deepseek-r1:7b`）。不启动 Ollama 不影响其他功能。

### Q: 局域网其他机器无法登录？
A: 确认后端 `server.address: 0.0.0.0`，且 CORS 配置中包含对应网段。当前已放行 `10.2.50.*`、`192.168.*`、`10.1.37.*`。

### Q: 课表同步失败？
A: 教务系统绑定需在「个人资料」中填写正确的教务系统账号密码。广西大学教务系统无需验证码，后端自动模拟登录。密码 AES 加密存储。

### Q: PDF 导入通知后内容为空？
A: PDF 导入仅支持**文字型 PDF**，扫描件/图片型 PDF 无法提取文字，系统会给出提示。可手动复制文字发布通知。

### Q: 前端构建后 favicon 不更新？
A: 浏览器有缓存，硬刷新（Ctrl+Shift+R）或清除缓存后即可看到新图标。

### Q: 私聊消息收不到？
A: 确认 WebSocket 连接正常。多实例部署时需将 `app.session.store` 改为 `redis`，并配置 Redis 连接。

---

## 更新日志

### v1.0.0（2026-09）

**初始版本功能：**
- AI 智能问答（RAG + deepseek-r1:7b，流式输出，引用来源，满意度反馈）
- 统一事项办理（奖助学金、请假、证明开具，申请审核流转）
- 课表与考试（Excel 导入 + 教务系统一键同步，周视图）
- 通知公告（分类筛选，紧急弹窗，PDF 导入，加入知识库）
- 校园地图导航（离线瓦片，本地路网寻路，无第三方 AK）
- 失物招领（智能匹配算法）
- 报修登记（进度追踪，服务评价）
- 专注模式（密码保护）
- 管理员后台（仪表盘，用户/申请/报修/通知/知识库/地点管理）

**新增功能：**
- 校园论坛（帖子发布、评论、分类、收藏、个人主页跳转）
- 二手交易市场（商品发布、购买、收藏、钱包充值、购买通知卖家）
- 好友系统（搜索、申请、审批、私聊 WebSocket、个人主页）
- 个人通知系统（好友申请通知、商品购买通知，通知页顶部「我的消息」）
- 帖子收藏 + 商品收藏（「我的收藏」标签页）
- 管理员帖子分类管理（5 个默认分类，增删改查，启用/禁用）
- 用户主页（查看用户帖子和商品，加好友/私聊入口）

**UI 美化：**
- 整体套用广西大学深红 + 金色 + 米黄视觉风格
- 学生端/管理员端登录页美化
- AI 对话界面美化
- 私聊窗口美化
- favicon 替换为广西大学风格圆形「大」字徽章

**Bug 修复：**
- 学生端/管理端内容空白（ErrorBoundary 未导入 + fragment 双根节点）
- 后端日志 INFO 级别不变色（logging pattern 未用 %clr 包装）
- 学生端二手商品不加载（listError 未定义）
- 局域网其他机器登录失败（CORS 仅配了 localhost）
- 好友搜索显示「未知用户」（前端把数组当单个对象）
- 加好友/私聊失败（user.userId 为 undefined 被拦截）
- 管理员端二手商品管理页报错（itemsLoading/itemsError 未定义）
- PostCategoryController 编译错误（误用不存在的 requireAdmin 方法）
- MarketService/FriendService 误用不存在的 User.getNickname() 方法
- 二手交易发布商品后不自动刷新

**知识库 RAG 检索改造：**
- 新建 `KnowledgeChunk` 实体（文档分块，docId + chunkIndex + content + tokenCount + priority）
- 新建 `TextChunker` 工具类（按段落/句子切分，500字/块，100字重叠）
- 新建 `TfidfVectorizer` 工具类（TF-IDF 向量化 + 余弦相似度，中文二元分词 + 停用词过滤）
- 重构 `KnowledgeBaseService`（retrieveChunks 标准RAG检索、indexDocument 分块索引、向量缓存、自动建索引）
- 修改 `ChatService`（改用 retrieveChunks + buildContextFromChunks）
- 修改 `AdminController`（知识库增删改后同步更新分块索引）


### v1.1.0（2026-09-22）

**交互优化：**
- 顶部导航未读徽章：「消息」显示私聊未读总数，「通知」显示个人通知未读数（好友申请、商品被购买等），红色背景数字，超过 99 显示 99+
- 「我的好友」列表每个好友头像旁显示该好友的未读消息数，30 秒轮询刷新，点击聊天并阅读后返回列表自动清零
- 阅读消息/通知后离开页面时，顶部导航徽章立即刷新（路由监听），无需等待轮询
- 通知未读数 = 个人通知总数 - 已读通知数（后端 countUnread 接口），消息未读数 = 各会话未读数之和
- 修复消息徽章不显示的 bug（App.vue 误用 chat.unread，store 中实际字段为 unreadTotal）
- 帖子点赞改为 toggle 模式：同一用户对同一帖子只能点赞一次，再次点击取消点赞（PostLike 实体联合唯一约束，PostVO 新增 liked 字段）
- 好友状态联动：已是好友时不再显示「加好友」，改为「删好友」；私聊窗口顶部同步好友状态
- 私聊窗口点击对方头像可跳转其个人主页
- 学号不再作为隐私字段，用户主页公开显示学号

**功能新增：**
- 个人中心支持自定义头像上传（上传后全站头像优先显示图片，无头像回退文字首字母）
- 论坛帖子详情页支持收藏，「我的收藏」可查看收藏的帖子
- 二手交易商品详情页支持收藏，「我的收藏」可查看收藏的商品
- 购买商品后自动通知卖家，好友申请后自动通知对方

**知识库 RAG 检索改造：**
- 新建 KnowledgeChunk 实体（文档分块，500字/块，100字重叠）
- 新建 TextChunker（按段落/句子切分）、TfidfVectorizer（TF-IDF 向量化 + 余弦相似度）
- 重构 KnowledgeBaseService（标准 RAG 检索、分块索引、向量缓存）
- ChatService 改用 retrieveChunks + buildContextFromChunks

**工程化：**
- 后端添加 Maven Wrapper（mvnw.cmd），无需全局安装 Maven 即可构建运行
- 新增一键启动脚本 start-all.bat（自动检查环境、安装依赖、启动后端+学生端+管理端、打开浏览器）
- 新增停止脚本 stop-all.bat（一键停止所有服务）
- 删除微信小程序端，专注 Web 端

**UI 统一：**
- 全站默认头像背景色统一为广西大学深红渐变 linear-gradient(135deg, #941e23, #761317)
  （此前学生端 7 个页面/组件使用蓝绿渐变 #409eff→#67c23a，与整体风格不一致）
- 管理员端侧边栏改为悬浮岛式（floating island）：圆角 20px、深红投影、上下留白、菜单项 pill 样式
- 顶部导航「消息」「通知」添加红色未读数字徽章，30 秒轮询刷新，点击进入对应页面后自动清零

**Bug 修复：**
- 学生端 API getProfile 路径错误（/auth/profile → /auth/userinfo），导致 GET method not supported
- 前端多处跳转用户主页时未校验 ID，undefined 被序列化为字符串传入后端导致 Long 转换失败
- 前端所有跳转函数统一添加 isValidId 校验，无效 ID 提示并返回
---

## 许可证

本项目为广西大学计算机学院课程项目，仅供学习研究使用。

---

## 联系方式

如有问题或建议，请通过学生端「意见反馈」提交。
