-- ============================================================================
-- aiHall 索引补齐脚本
--
-- 背景：项目使用 Hibernate ddl-auto=update 建表，它只会为 @Column(unique=true)
--       建唯一约束，不会为高频查询条件建索引，也不会建复合唯一索引。
--
-- 用法：可在 MySQL 客户端直接执行；也可用 .workbuddy/tools/apply_indexes.jsh
--       自动执行（会先查 information_schema，已存在的索引自动跳过，可重复运行）。
--
-- 说明：本文件里的语句不带 IF NOT EXISTS（MySQL 不支持），重复执行会报
--       "Duplicate key name"，属正常现象，不影响数据。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 下单幂等：同一幂等键只能落一笔订单
--    配合 POST /api/market/{id}/buy 的 Idempotency-Key 头使用。
-- ---------------------------------------------------------------------------
-- 若 request_id 列尚不存在（Hibernate 首次启动会自动建），可先手工补上：
-- ALTER TABLE market_order ADD COLUMN request_id VARCHAR(64) NULL AFTER order_no;
ALTER TABLE market_order ADD UNIQUE KEY uk_market_order_request (request_id);

-- ---------------------------------------------------------------------------
-- 2. 好友关系：同一对用户之间不重复发起申请
--    （应用层已有判重，这里是并发下的兜底）
-- ---------------------------------------------------------------------------
ALTER TABLE friendship ADD UNIQUE KEY uk_friendship_pair (requester_id, addressee_id);

-- ---------------------------------------------------------------------------
-- 3. 学校账号登录：按「平台 + 绑定账号」反查绑定记录
-- ---------------------------------------------------------------------------
ALTER TABLE user_binding ADD KEY idx_user_binding_account (platform, bind_account);

-- ---------------------------------------------------------------------------
-- 4. 列表分页：过滤条件 + 时间倒序
--    MarketService.list / PostService.list / FeedbackService.adminList 直接受益
-- ---------------------------------------------------------------------------
ALTER TABLE market_item ADD KEY idx_market_item_status_time (status, create_time);
ALTER TABLE post        ADD KEY idx_post_status_time (status, create_time);
ALTER TABLE feedback    ADD KEY idx_feedback_create_time (create_time);

-- ---------------------------------------------------------------------------
-- 5. 私聊：会话内游标翻页 + 未读数统计
-- ---------------------------------------------------------------------------
ALTER TABLE private_message ADD KEY idx_pm_conversation_id (conversation_id, id);
ALTER TABLE private_message ADD KEY idx_pm_receiver_read (receiver_id, is_read);

-- ---------------------------------------------------------------------------
-- 6. 关键词检索：ngram 全文索引
--
--    原来帖子/商品的关键词检索是 LIKE '%词%'，前后都带通配符，索引用不上，
--    数据量一大就是全表扫（EXPLAIN 显示 type=ALL）。
--
--    用 ngram 解析器做中文分词（ngram_token_size 默认 2，即按二元组切）。
--    注意两个前提，应用侧已做处理（见 search/KeywordSearchSupport）：
--      * 应用启动时会检查这两个索引，缺了自动补建；ngram 不可用则退回 LIKE 并告警；
--      * 检索词短于 ngram_token_size（单个汉字）时全文索引匹配不到任何 token，
--        这种短词由 LIKE 兜底，否则用户搜一个字会得到 0 结果。
--
--    实测（.workbuddy/tools/apply_fulltext.jsh）：10 组关键词下 LIKE 与 FULLTEXT
--    结果集完全一致，且 FULLTEXT 的执行计划里确实出现了 ft_ 索引。
-- ---------------------------------------------------------------------------
ALTER TABLE market_item ADD FULLTEXT INDEX ft_market_item_search (title, description) WITH PARSER ngram;
ALTER TABLE post        ADD FULLTEXT INDEX ft_post_search (title, content) WITH PARSER ngram;


-- ============================================================================
-- 教务账号「活跃绑定唯一」约束（已执行，2026-09-15）
--
-- 背景问题：同一个教务账号（如 JWXT / 2407110216）可能被多个平台账号同时绑定。
--   AuthService.schoolLogin 用 findFirstByPlatformAndBindAccountAndStatus 反查，
--   命中哪一条是不确定的 —— 意味着 B 用 A 的教务密码可能登进 A 的账号。
--
-- 已做的两步处理：
--   1) 清理历史重复数据：每个 (platform, bind_account) 只保留归属最明确的一条生效绑定
--      （优先「平台账号用户名 == 教务号」，其次绑定最早），其余置 status = 0（停用而非删除）。
--      处理明细与备份见 backups/user_binding_backup_<时间戳>.sql，回滚只需把 status 改回 1。
--      执行脚本：.workbuddy/tools/dedupe_bindings.jsh（不带 APPLY=1 时只出计划不改数据）。
--   2) 加数据库级约束（下面两条 DDL）：
--      MySQL 不支持部分索引，借助生成列在 status != 1 时取 NULL，而唯一索引允许多个 NULL，
--      以此表达「每个教务账号最多一条生效绑定」，同时保留历史停用记录。
--
-- 验证：.workbuddy/tools/verify_binding_constraint.jsh（重复占用被拒 / 停用记录可并存）
--       .workbuddy/tools/verify_binding_lookup.jsh（登录反查命中正确账号）
-- ============================================================================
ALTER TABLE user_binding
  ADD COLUMN active_binding_key VARCHAR(80)
    GENERATED ALWAYS AS (CASE WHEN status = 1 THEN CONCAT(platform, ':', bind_account) ELSE NULL END) STORED;
ALTER TABLE user_binding ADD UNIQUE KEY uk_user_binding_active (active_binding_key);

-- 注意：应用层 PlatformBindService.saveBinding 也会先做一次可读性更好的校验，
-- 两条防线都保留 —— 应用层负责给出友好提示，数据库层负责兜住并发与漏改。
