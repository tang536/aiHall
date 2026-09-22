package com.gxu.aihall.service;

import com.gxu.aihall.entity.KnowledgeChunk;
import com.gxu.aihall.entity.KnowledgeDoc;
import com.gxu.aihall.repository.KnowledgeChunkRepository;
import com.gxu.aihall.repository.KnowledgeDocRepository;
import com.gxu.aihall.util.TextChunker;
import com.gxu.aihall.util.TfidfVectorizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索服务（标准 RAG 检索）
 *
 * 标准 RAG 流程：
 * 1. 文档分块（Chunking）：长文档按段落切分为 500 字符的块，块间 100 字符重叠
 * 2. 向量化（Embedding）：基于 TF-IDF 算法将文本块转换为向量表示
 * 3. 相似度检索（Similarity Search）：计算查询向量与文档块向量的余弦相似度
 * 4. 上下文构建（Context Building）：将检索到的高相关度块组合为上下文
 *
 * 与旧版关键词匹配的区别：
 * - 旧版：整篇文档作为整体，简单关键词包含匹配 + 人工加权评分
 * - 新版：文档级分块，TF-IDF 语义向量化，余弦相似度排序，检索精度更高
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeDocRepository docRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final TextChunker chunker = new TextChunker(500, 100);

    /** 缓存：向量化器（检索时复用，避免每次重新构建词汇表） */
    private volatile TfidfVectorizer cachedVectorizer;
    /** 缓存：所有分块的向量 */
    private volatile List<Map<Integer, Double>> cachedChunkVectors;
    /** 缓存：所有分块 */
    private volatile List<KnowledgeChunk> cachedChunks;
    /** 缓存版本号：索引更新时递增，使缓存失效 */
    private volatile long cacheVersion = 0;

    public KnowledgeBaseService(KnowledgeDocRepository docRepository,
                                KnowledgeChunkRepository chunkRepository) {
        this.docRepository = docRepository;
        this.chunkRepository = chunkRepository;
    }

    // ==================== 索引管理 ====================

    /**
     * 对单篇文档建立分块索引
     * 先删除该文档的旧分块，再重新切分并保存
     */
    @Transactional
    public void indexDocument(Long docId) {
        KnowledgeDoc doc = docRepository.findById(docId).orElse(null);
        if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
            // 文档不存在或已禁用，删除其分块
            chunkRepository.deleteByDocId(docId);
            invalidateCache();
            return;
        }

        // 删除旧分块
        chunkRepository.deleteByDocId(docId);

        // 切分文档
        List<String> chunks = chunker.chunk(doc.getTitle(), doc.getContent());
        int idx = 0;
        for (String chunkContent : chunks) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setTitle(doc.getTitle());
            chunk.setChunkIndex(idx++);
            chunk.setContent(chunkContent);
            chunk.setCategory(doc.getCategory());
            chunk.setSource(doc.getSource());
            chunk.setPriority(doc.getPriority() != null ? doc.getPriority() : 0);
            chunk.setTokenCount(TfidfVectorizer.tokenize(chunkContent).size());
            chunkRepository.save(chunk);
        }

        log.info("[RAG索引] 文档《{}》已建立 {} 个分块索引", doc.getTitle(), chunks.size());
        invalidateCache();
    }

    /**
     * 重建所有文档的索引（全量重建）
     */
    @Transactional
    public void reindexAll() {
        List<KnowledgeDoc> allDocs = docRepository.findByStatusOrderByPriorityDescCreateTimeDesc(1);
        log.info("[RAG索引] 开始全量重建索引，共 {} 篇文档", allDocs.size());

        // 清空所有分块
        chunkRepository.deleteAll();

        int totalChunks = 0;
        for (KnowledgeDoc doc : allDocs) {
            List<String> chunks = chunker.chunk(doc.getTitle(), doc.getContent());
            int idx = 0;
            for (String chunkContent : chunks) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(doc.getId());
                chunk.setTitle(doc.getTitle());
                chunk.setChunkIndex(idx++);
                chunk.setContent(chunkContent);
                chunk.setCategory(doc.getCategory());
                chunk.setSource(doc.getSource());
                chunk.setPriority(doc.getPriority() != null ? doc.getPriority() : 0);
                chunk.setTokenCount(TfidfVectorizer.tokenize(chunkContent).size());
                chunkRepository.save(chunk);
            }
            totalChunks += chunks.size();
        }

        log.info("[RAG索引] 全量重建完成，共 {} 篇文档，{} 个分块", allDocs.size(), totalChunks);
        invalidateCache();
    }

    /**
     * 删除文档时同步删除其分块索引
     */
    @Transactional
    public void deleteDocument(Long docId) {
        chunkRepository.deleteByDocId(docId);
        invalidateCache();
    }

    /** 使缓存失效 */
    private void invalidateCache() {
        cacheVersion++;
        cachedVectorizer = null;
        cachedChunkVectors = null;
        cachedChunks = null;
    }

    // ==================== 标准 RAG 检索 ====================

    /**
     * 标准 RAG 检索：基于 TF-IDF 向量 + 余弦相似度
     * @param query 用户问题
     * @param topK 返回最相关的 K 个文档块
     * @return 按相似度降序排列的分块列表
     */
    public List<KnowledgeChunk> retrieveChunks(String query, int topK) {
        // 确保索引已建立（如果没有分块，自动建立）
        ensureIndexBuilt();

        // 获取缓存的向量化器和向量
        TfidfVectorizer vectorizer = cachedVectorizer;
        List<KnowledgeChunk> chunks = cachedChunks;
        List<Map<Integer, Double>> chunkVectors = cachedChunkVectors;

        if (vectorizer == null || chunks == null || chunkVectors == null) {
            // 缓存未命中，重新构建
            synchronized (this) {
                if (cachedVectorizer == null) {
                    buildVectorCache();
                }
                vectorizer = cachedVectorizer;
                chunks = cachedChunks;
                chunkVectors = cachedChunkVectors;
            }
        }

        if (chunks.isEmpty()) {
            log.info("[RAG检索] query=\"{}\" 知识库无可用分块", abbreviate(query, 80));
            return new ArrayList<>();
        }

        // 查询向量化
        Map<Integer, Double> queryVector = vectorizer.transform(query);

        // 计算余弦相似度
        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            double similarity = TfidfVectorizer.cosineSimilarity(queryVector, chunkVectors.get(i));
            // 优先级加成（0-100的优先级，映射为0-0.1的相似度加成）
            int priority = chunks.get(i).getPriority() != null ? chunks.get(i).getPriority() : 0;
            similarity += priority * 0.001;
            if (similarity > 0.001) {
                scored.add(new ScoredChunk(chunks.get(i), similarity));
            }
        }

        // 按相似度降序排序，取 topK
        List<ScoredChunk> topChunks = scored.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());

        // 检索日志
        if (topChunks.isEmpty()) {
            log.info("[RAG检索] query=\"{}\" 未命中任何相关分块（词汇表大小:{}, 总分块数:{}, 查询词元数:{}）",
                    abbreviate(query, 80), vectorizer.vocabularySize(), chunks.size(),
                    TfidfVectorizer.tokenize(query).size());
        } else {
            StringBuilder sb = new StringBuilder();
            for (ScoredChunk sc : topChunks) {
                sb.append(String.format("\n    - 《%s》#%d (分类:%s, 相似度:%.4f, 词元:%d)",
                        sc.chunk.getTitle(), sc.chunk.getChunkIndex(),
                        sc.chunk.getCategory(), sc.score, sc.chunk.getTokenCount()));
            }
            log.info("[RAG检索] query=\"{}\" 命中 {} 个分块（词汇表大小:{}, 总分块数:{}）:{}",
                    abbreviate(query, 80), topChunks.size(),
                    vectorizer.vocabularySize(), chunks.size(), sb);
        }

        return topChunks.stream().map(s -> s.chunk).collect(Collectors.toList());
    }

    /**
     * 检索相关文档（兼容旧接口，基于分块检索结果聚合到文档级别）
     * 同一文档的多个高分块会被合并为一个文档返回
     */
    public List<KnowledgeDoc> retrieve(String query, int topK) {
        List<KnowledgeChunk> chunks = retrieveChunks(query, topK * 2);
        if (chunks.isEmpty()) return new ArrayList<>();

        // 按 docId 聚合，取每个文档的最高相似度块
        Map<Long, KnowledgeChunk> bestChunkByDoc = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            bestChunkByDoc.putIfAbsent(chunk.getDocId(), chunk);
        }

        // 加载对应的文档
        List<KnowledgeDoc> docs = new ArrayList<>();
        for (Long docId : bestChunkByDoc.keySet()) {
            docRepository.findById(docId).ifPresent(docs::add);
            if (docs.size() >= topK) break;
        }
        return docs;
    }

    // ==================== 上下文构建 ====================

    /**
     * 基于检索到的分块构建 RAG 上下文（标准方式）
     * 每个块标注来源文档和块索引，便于 AI 引用
     */
    public String buildContextFromChunks(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从校园知识库中检索到的相关资料片段，请基于这些资料回答学生问题。")
          .append("如果资料中没有相关信息，请如实告知并建议咨询相关部门。\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            sb.append(String.format("【资料%d】%s（分类：%s，来源：%s）\n%s\n\n",
                    i + 1, chunk.getTitle(), chunk.getCategory(), chunk.getSource(), chunk.getContent()));
        }
        return sb.toString();
    }

    /**
     * 兼容旧接口：基于文档列表构建上下文
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
     * 获取引用来源列表（基于分块）
     */
    public List<String> getSourcesFromChunks(List<KnowledgeChunk> chunks) {
        // 去重：同一文档只列一次
        Set<String> seen = new LinkedHashSet<>();
        for (KnowledgeChunk chunk : chunks) {
            seen.add(chunk.getTitle() + "（" + chunk.getSource() + "）");
        }
        return new ArrayList<>(seen);
    }

    /**
     * 兼容旧接口
     */
    public List<String> getSources(List<KnowledgeDoc> docs) {
        return docs.stream()
                .map(d -> d.getTitle() + "（" + d.getSource() + "）")
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    /** 确保索引已建立：如果没有分块，自动对所有文档建立索引 */
    private void ensureIndexBuilt() {
        if (chunkRepository.count() == 0) {
            List<KnowledgeDoc> docs = docRepository.findByStatusOrderByPriorityDescCreateTimeDesc(1);
            if (!docs.isEmpty()) {
                log.info("[RAG索引] 检测到无分块索引，自动建立索引（{} 篇文档）", docs.size());
                for (KnowledgeDoc doc : docs) {
                    indexDocument(doc.getId());
                }
            }
        }
    }

    /** 构建向量缓存 */
    private synchronized void buildVectorCache() {
        if (cachedVectorizer != null) return; // 双重检查

        List<KnowledgeChunk> allChunks = chunkRepository.findAll();
        List<String> texts = allChunks.stream()
                .map(KnowledgeChunk::getContent)
                .collect(Collectors.toList());

        TfidfVectorizer vectorizer = new TfidfVectorizer();
        List<Map<Integer, Double>> vectors = vectorizer.fitTransform(texts);

        cachedChunks = allChunks;
        cachedChunkVectors = vectors;
        cachedVectorizer = vectorizer;

        log.info("[RAG索引] 向量缓存已构建：{} 个分块，词汇表大小 {}", allChunks.size(), vectorizer.vocabularySize());
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 带分数的分块 */
    private static class ScoredChunk {
        KnowledgeChunk chunk;
        double score;
        ScoredChunk(KnowledgeChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
