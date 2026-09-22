package com.gxu.aihall.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TF-IDF 向量化工具
 * 实现标准的词频-逆文档频率算法，用于将文本转换为向量表示，
 * 并通过余弦相似度计算文本之间的语义相关度。
 *
 * 标准 RAG 检索流程：
 * 1. 分词（中文按二元切分 + 停用词过滤）
 * 2. 构建词汇表（Vocabulary）
 * 3. 计算文档频率（DF）和逆文档频率（IDF）
 * 4. 计算每个文档的 TF-IDF 向量
 * 5. 查询向量化后计算余弦相似度
 */
public class TfidfVectorizer {

    /** 停用词集合（中文常见无意义词） */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "那", "他", "她", "它", "们", "这个", "那个", "什么", "怎么",
            "吗", "呢", "吧", "啊", "呀", "哦", "嗯", "哈", "啦", "么", "之", "其", "或",
            "与", "及", "等", "等等", "可以", "能", "能够", "应该", "应当", "必须", "需要",
            "如果", "假如", "因为", "所以", "但是", "然而", "而且", "并且", "或者", "还是",
            "不是", "就是", "只是", "只有", "只要", "不管", "无论", "虽然", "尽管", "即使",
            "对于", "关于", "根据", "按照", "通过", "由于", "为了", "以便", "以免", "以及",
            "之前", "之后", "以前", "以后", "现在", "目前", "已经", "正在", "将要", "即将",
            "这里", "那里", "哪里", "这边", "那边", "上面", "下面", "里面", "外面", "中间"
    ));

    /** 词汇表：词 -> 索引 */
    private final Map<String, Integer> vocabulary = new HashMap<>();

    /** 文档频率：词 -> 出现该词的文档数 */
    private final Map<String, Integer> docFrequency = new HashMap<>();

    /** 文档总数 */
    private int docCount = 0;

    /**
     * 中文分词：按标点和空格切分，对长词做二元切分，过滤停用词和单字
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        // 移除标点，按空格和常见分隔符切分
        String cleaned = text.replaceAll("[，。？！、；：（）【】《》\"'\\s\\d\\p{Punct}]+", " ");
        List<String> tokens = new ArrayList<>();
        for (String w : cleaned.split(" ")) {
            if (w.isEmpty() || STOP_WORDS.contains(w)) continue;
            // 英文单词直接保留
            if (w.matches("[a-zA-Z]+")) {
                if (w.length() > 1) tokens.add(w.toLowerCase());
                continue;
            }
            // 中文：2字词直接保留，长词做二元切分
            if (w.length() == 2) {
                if (!STOP_WORDS.contains(w)) tokens.add(w);
            } else if (w.length() > 2) {
                for (int i = 0; i < w.length() - 1; i++) {
                    String bigram = w.substring(i, i + 2);
                    if (!STOP_WORDS.contains(bigram)) tokens.add(bigram);
                }
            }
        }
        return tokens;
    }

    /**
     * 计算词频（TF）：词在文档中出现的次数 / 文档总词数
     */
    private Map<String, Double> computeTf(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (tokens.isEmpty()) return tf;
        Map<String, Integer> count = new HashMap<>();
        for (String t : tokens) count.merge(t, 1, Integer::sum);
        double total = tokens.size();
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            tf.put(e.getKey(), e.getValue() / total);
        }
        return tf;
    }

    /**
     * 拟合：根据所有文档构建词汇表和文档频率
     * @param documents 所有文档的文本列表
     */
    public void fit(List<String> documents) {
        vocabulary.clear();
        docFrequency.clear();
        docCount = documents.size();

        // 第一遍：构建词汇表和文档频率
        for (String doc : documents) {
            List<String> tokens = tokenize(doc);
            Set<String> uniqueTokens = new HashSet<>(tokens);
            for (String t : uniqueTokens) {
                if (!vocabulary.containsKey(t)) {
                    vocabulary.put(t, vocabulary.size());
                }
                docFrequency.merge(t, 1, Integer::sum);
            }
        }
    }

    /**
     * 计算逆文档频率（IDF）：log((文档总数 + 1) / (包含该词的文档数 + 1)) + 1
     * 使用平滑 IDF 避免除零
     */
    private double idf(String term) {
        int df = docFrequency.getOrDefault(term, 0);
        return Math.log((double) (docCount + 1) / (df + 1)) + 1.0;
    }

    /**
     * 将文本转换为 TF-IDF 向量（稀疏表示：索引 -> 权重）
     */
    public Map<Integer, Double> transform(String text) {
        List<String> tokens = tokenize(text);
        Map<String, Double> tf = computeTf(tokens);
        Map<Integer, Double> vector = new HashMap<>();
        for (Map.Entry<String, Double> e : tf.entrySet()) {
            Integer idx = vocabulary.get(e.getKey());
            if (idx != null) {
                vector.put(idx, e.getValue() * idf(e.getKey()));
            }
        }
        return vector;
    }

    /**
     * 计算两个稀疏向量的余弦相似度
     * cos(a, b) = (a · b) / (||a|| * ||b||)
     */
    public static double cosineSimilarity(Map<Integer, Double> a, Map<Integer, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        // 点积
        double dot = 0.0;
        Map<Integer, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Integer, Double> larger = a.size() <= b.size() ? b : a;
        for (Map.Entry<Integer, Double> e : smaller.entrySet()) {
            Double bv = larger.get(e.getKey());
            if (bv != null) dot += e.getValue() * bv;
        }
        // 范数
        double normA = 0.0, normB = 0.0;
        for (double v : a.values()) normA += v * v;
        for (double v : b.values()) normB += v * v;
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (normA * normB);
    }

    /**
     * 拟合并转换：一次性完成词汇表构建和所有文档的向量化
     * @return 每个文档的 TF-IDF 向量列表
     */
    public List<Map<Integer, Double>> fitTransform(List<String> documents) {
        fit(documents);
        List<Map<Integer, Double>> vectors = new ArrayList<>();
        for (String doc : documents) {
            vectors.add(transform(doc));
        }
        return vectors;
    }

    /** 获取词汇表大小 */
    public int vocabularySize() {
        return vocabulary.size();
    }

    /** 获取文档总数 */
    public int docCount() {
        return docCount;
    }
}
