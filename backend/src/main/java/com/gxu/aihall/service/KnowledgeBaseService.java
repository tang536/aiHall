package com.gxu.aihall.service;

import com.gxu.aihall.entity.KnowledgeDoc;
import com.gxu.aihall.repository.KnowledgeDocRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索服务（基于关键词的 RAG 检索）
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeDocRepository docRepository;

    public KnowledgeBaseService(KnowledgeDocRepository docRepository) {
        this.docRepository = docRepository;
    }

    /**
     * 根据用户问题检索相关知识库文档
     */
    public List<KnowledgeDoc> retrieve(String query, int topK) {
        List<KnowledgeDoc> allDocs = docRepository.findByStatusOrderByPriorityDescCreateTimeDesc(1);
        if (allDocs.isEmpty()) return new ArrayList<>();

        // 简单关键词匹配 + 评分
        String[] queryWords = segmentQuery(query);

        List<ScoredDoc> scored = new ArrayList<>();
        for (KnowledgeDoc doc : allDocs) {
            double score = calculateScore(doc, queryWords);
            if (score > 0) {
                scored.add(new ScoredDoc(doc, score));
            }
        }

        // 按分数排序，取 topK
        List<ScoredDoc> topDocs = scored.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());

        // AI 命中日志：记录本次检索命中了哪些文档及得分
        if (topDocs.isEmpty()) {
            log.info("[RAG命中] query=\"{}\" 未命中任何知识库文档", abbreviate(query, 80));
        } else {
            StringBuilder sb = new StringBuilder();
            for (ScoredDoc sd : topDocs) {
                sb.append(String.format("\n    - 《%s》(分类:%s, 来源:%s, 得分:%.2f)",
                        sd.doc.getTitle(), sd.doc.getCategory(), sd.doc.getSource(), sd.score));
            }
            log.info("[RAG命中] query=\"{}\" 共命中 {} 篇文档:{}", abbreviate(query, 80), topDocs.size(), sb);
        }

        return topDocs.stream().map(s -> s.doc).collect(Collectors.toList());
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * 构建 RAG 上下文
     */
    public String buildContext(List<KnowledgeDoc> docs) {
        if (docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("以下是校园知识库中的相关资料，请基于这些资料回答学生问题，如果资料中没有相关信息，请如实告知并建议咨询相关部门：\n\n");
        for (int i = 0; i < docs.size(); i++) {
            KnowledgeDoc doc = docs.get(i);
            sb.append(String.format("【资料%d】%s（分类：%s，来源：%s）\n%s\n\n",
                    i + 1, doc.getTitle(), doc.getCategory(), doc.getSource(), doc.getContent()));
        }
        return sb.toString();
    }

    /**
     * 获取引用来源列表
     */
    public List<String> getSources(List<KnowledgeDoc> docs) {
        return docs.stream()
                .map(d -> d.getTitle() + "（" + d.getSource() + "）")
                .collect(Collectors.toList());
    }

    /**
     * 简单中文分词（按常见词切分）
     */
    private String[] segmentQuery(String query) {
        if (query == null || query.isEmpty()) return new String[0];
        // 移除标点，按空格和常见分隔符切分
        String cleaned = query.replaceAll("[，。？！、；：（）【】\\s\"']+", " ");
        List<String> words = new ArrayList<>();
        for (String w : cleaned.split(" ")) {
            if (!w.isEmpty()) {
                words.add(w);
                // 对长词做二元切分，增加匹配概率
                if (w.length() > 2) {
                    for (int i = 0; i < w.length() - 1; i++) {
                        words.add(w.substring(i, i + 2));
                    }
                }
            }
        }
        return words.toArray(new String[0]);
    }

    /**
     * 计算文档与查询的匹配分数
     */
    private double calculateScore(KnowledgeDoc doc, String[] queryWords) {
        double score = 0;
        String title = doc.getTitle() != null ? doc.getTitle() : "";
        String content = doc.getContent() != null ? doc.getContent() : "";
        String keywords = doc.getKeywords() != null ? doc.getKeywords() : "";
        String category = doc.getCategory() != null ? doc.getCategory() : "";

        for (String word : queryWords) {
            // 标题匹配权重最高
            if (title.contains(word)) score += 5.0;
            // 关键词匹配
            if (keywords.contains(word)) score += 3.0;
            // 分类匹配
            if (category.contains(word)) score += 2.0;
            // 内容匹配
            if (content.contains(word)) score += 1.0;
        }

        // 优先级加成
        score += doc.getPriority() * 0.5;

        return score;
    }

    private static class ScoredDoc {
        KnowledgeDoc doc;
        double score;

        ScoredDoc(KnowledgeDoc doc, double score) {
            this.doc = doc;
            this.score = score;
        }
    }
}
