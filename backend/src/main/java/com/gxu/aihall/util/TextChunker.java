package com.gxu.aihall.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块工具（Text Chunker）
 * 标准 RAG 流程中的文档切分环节：
 * 1. 按段落优先切分（保留语义完整性）
 * 2. 段落过长时按句子切分
 * 3. 句子仍过长时按字符数硬切分
 * 4. 块之间保留重叠（Overlap），避免边界信息丢失
 */
public class TextChunker {

    /** 每块目标字符数 */
    private final int chunkSize;

    /** 块之间重叠字符数 */
    private final int overlap;

    public TextChunker(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /** 默认构造：500字符块，100字符重叠 */
    public TextChunker() {
        this(500, 100);
    }

    /**
     * 将文档内容切分为多个块
     * @param title 文档标题（会附加到每个块的开头，增强检索匹配）
     * @param content 文档内容
     * @return 切分后的块列表
     */
    public List<String> chunk(String title, String content) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) return chunks;

        // 第一步：按段落切分
        List<String> paragraphs = splitByParagraph(content);

        // 第二步：合并段落为块
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (para.trim().isEmpty()) continue;

            // 如果当前块 + 新段落超过 chunkSize，先保存当前块
            if (current.length() > 0 && current.length() + para.length() + 2 > chunkSize) {
                chunks.add(finalizeChunk(title, current.toString()));
                // 保留重叠：取当前块末尾的 overlap 字符作为下一块的开头
                String overlapText = getTailOverlap(current.toString(), overlap);
                current = new StringBuilder(overlapText);
            }

            if (current.length() > 0) current.append("\n\n");
            current.append(para);

            // 如果单个段落就超过 chunkSize，按句子进一步切分
            while (current.length() > chunkSize * 1.5) {
                String[] split = splitBySentence(current.toString(), chunkSize);
                chunks.add(finalizeChunk(title, split[0]));
                current = new StringBuilder(split[1]);
            }
        }

        // 保存最后一块
        if (current.length() > 0) {
            chunks.add(finalizeChunk(title, current.toString()));
        }

        return chunks;
    }

    /**
     * 按段落切分（空行分隔）
     */
    private List<String> splitByParagraph(String text) {
        List<String> paragraphs = new ArrayList<>();
        String[] parts = text.split("\\n\\s*\\n");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) paragraphs.add(trimmed);
        }
        // 如果没有空行分隔，按换行切分
        if (paragraphs.size() <= 1 && text.contains("\n")) {
            paragraphs.clear();
            for (String line : text.split("\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /**
     * 按句子切分超长文本
     * @return [第一块, 剩余部分]
     */
    private String[] splitBySentence(String text, int targetSize) {
        String[] sentenceEnds = {"。", "！", "？", "；", ".", "!", "?", ";"};
        int splitPos = -1;

        // 从 targetSize 位置向前找最近的句子结束符
        int searchStart = Math.min(targetSize, text.length());
        for (int i = searchStart; i > Math.max(0, searchStart - 200); i--) {
            char c = text.charAt(i);
            for (String end : sentenceEnds) {
                if (c == end.charAt(0)) {
                    splitPos = i + 1;
                    break;
                }
            }
            if (splitPos > 0) break;
        }

        // 如果找不到句子结束符，按逗号切分
        if (splitPos < 0) {
            for (int i = searchStart; i > Math.max(0, searchStart - 100); i--) {
                char c = text.charAt(i);
                if (c == '，' || c == ',') {
                    splitPos = i + 1;
                    break;
                }
            }
        }

        // 实在找不到，硬切分
        if (splitPos < 0) splitPos = Math.min(targetSize, text.length());

        String first = text.substring(0, splitPos).trim();
        String rest = splitPos < text.length() ? text.substring(splitPos).trim() : "";
        return new String[]{first, rest};
    }

    /**
     * 获取文本末尾的重叠部分
     */
    private String getTailOverlap(String text, int overlapSize) {
        if (text.length() <= overlapSize) return text;
        String tail = text.substring(text.length() - overlapSize);
        // 从重叠部分的开头找第一个句子结束符，避免半句话
        int firstEnd = -1;
        for (int i = 0; i < Math.min(50, tail.length()); i++) {
            char c = tail.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                firstEnd = i + 1;
                break;
            }
        }
        if (firstEnd > 0 && firstEnd < tail.length()) {
            return tail.substring(firstEnd).trim();
        }
        return tail.trim();
    }

    /**
     * 最终化块：在块开头附加标题信息，增强检索时的标题匹配权重
     */
    private String finalizeChunk(String title, String content) {
        if (title != null && !title.trim().isEmpty()) {
            return "【" + title.trim() + "】\n" + content.trim();
        }
        return content.trim();
    }
}
