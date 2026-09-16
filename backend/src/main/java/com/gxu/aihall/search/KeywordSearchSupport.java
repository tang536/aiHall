package com.gxu.aihall.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 关键词检索能力探测与自愈。
 * <p>帖子 / 商品的关键词检索原本是 {@code LIKE '%词%'`}，前后都带通配符，索引用不上，
 * 数据量一大就是全表扫。这里改用 MySQL 的 ngram 全文索引（对中文按 ngram 分词），
 * 让检索真正走索引。
 * <p>但全文索引有两个前提，不能想当然：
 * <ol>
 *   <li><b>索引得先存在</b>——项目用 {@code ddl-auto: update} 建表，Hibernate 不会建 FULLTEXT 索引，
 *       而手写迁移脚本容易被漏掉。所以启动时检查一遍，缺了就补建；</li>
 *   <li><b>检索词长度要够</b>——ngram 的分词粒度由 {@code ngram_token_size} 决定（默认 2），
 *       搜单个汉字匹配不到任何 token。这种短词必须退回 LIKE。</li>
 * </ol>
 * <p>任何一步不成立（比如 MySQL 版本不支持 ngram）都不会让接口报错，而是退回 LIKE，
 * 只是数据量大时慢一些——可用性优先于性能。
 */
@Slf4j
@Component
public class KeywordSearchSupport implements ApplicationRunner {

    /** {索引名, 表名, DDL} */
    private static final String[][] FULLTEXT_INDEXES = {
            {"ft_market_item_search", "market_item",
                    "ALTER TABLE market_item ADD FULLTEXT INDEX ft_market_item_search (title, description) WITH PARSER ngram"},
            {"ft_post_search", "post",
                    "ALTER TABLE post ADD FULLTEXT INDEX ft_post_search (title, content) WITH PARSER ngram"},
    };

    private static final int DEFAULT_MIN_KEYWORD_LENGTH = 2;

    private final JdbcTemplate jdbc;

    private volatile boolean fullTextReady = false;
    private volatile int minKeywordLength = DEFAULT_MIN_KEYWORD_LENGTH;
    private volatile String disabledReason = "尚未初始化";

    public KeywordSearchSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialise();
    }

    /**
     * 探测 ngram 支持并按需补建全文索引；失败不抛异常，只降级 + 告警。
     * 包级可见，便于测试直接调用。
     */
    void initialise() {
        try {
            Integer tokenSize = jdbc.queryForObject("SELECT @@ngram_token_size", Integer.class);
            if (tokenSize == null || tokenSize <= 0) {
                disable("无法读取 ngram_token_size");
                return;
            }
            minKeywordLength = tokenSize;

            int created = 0;
            for (String[] idx : FULLTEXT_INDEXES) {
                if (!indexExists(idx[1], idx[0])) {
                    jdbc.execute(idx[2]);
                    created++;
                    log.info("关键词检索：已创建全文索引 {}.{}（WITH PARSER ngram）", idx[1], idx[0]);
                }
            }
            fullTextReady = true;
            log.info("关键词检索：全文索引就绪（ngram 分词粒度 {}）{}",
                    minKeywordLength, created > 0 ? "，本次新建 " + created + " 个索引" : "");
        } catch (Exception e) {
            disable(e.getMessage());
        }
    }

    private void disable(String reason) {
        fullTextReady = false;
        disabledReason = reason;
        log.warn("关键词检索：全文索引不可用，退回 LIKE 模糊匹配（功能不受影响，数据量大时较慢）：{}", reason);
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index);
        return count != null && count > 0;
    }

    /**
     * 该检索词能否走全文索引。
     * 空词、或长度短于 ngram 分词粒度的词一律返回 false（交给 LIKE 兜底）。
     */
    public boolean canUseFullText(String keyword) {
        if (!fullTextReady || keyword == null) return false;
        String kw = keyword.trim();
        if (kw.isEmpty()) return false;
        return kw.codePointCount(0, kw.length()) >= minKeywordLength;
    }

    public boolean isFullTextReady() {
        return fullTextReady;
    }

    public int getMinKeywordLength() {
        return minKeywordLength;
    }

    public String getDisabledReason() {
        return disabledReason;
    }
}
