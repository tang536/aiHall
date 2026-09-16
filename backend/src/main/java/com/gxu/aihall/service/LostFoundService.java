package com.gxu.aihall.service;

import com.gxu.aihall.entity.LostItem;
import com.gxu.aihall.repository.LostItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 失物招领服务（含智能匹配）
 */
@Service
public class LostFoundService {

    private final LostItemRepository lostItemRepository;

    public LostFoundService(LostItemRepository lostItemRepository) {
        this.lostItemRepository = lostItemRepository;
    }

    public List<LostItem> getAll() {
        return lostItemRepository.findAllByOrderByCreateTimeDesc();
    }

    public List<LostItem> getByType(String type) {
        return lostItemRepository.findByTypeOrderByCreateTimeDesc(type);
    }

    public LostItem publish(LostItem item) {
        item.setStatus("PENDING");
        if (item.getKeywords() == null || item.getKeywords().isEmpty()) {
            item.setKeywords(extractKeywords(item.getItemName() + " " + item.getDescription()));
        }
        return lostItemRepository.save(item);
    }

    public LostItem getById(Long id) {
        return lostItemRepository.findById(id).orElse(null);
    }

    public void claim(Long id) {
        lostItemRepository.findById(id).ifPresent(item -> {
            item.setStatus("CLAIMED");
            lostItemRepository.save(item);
        });
    }

    /**
     * 智能匹配：发布失物时匹配招领信息，发布招领时匹配失物信息
     */
    public List<LostItem> smartMatch(LostItem newItem) {
        String oppositeType = "LOST".equals(newItem.getType()) ? "FOUND" : "LOST";
        List<LostItem> candidates = lostItemRepository.findByTypeAndStatusOrderByCreateTimeDesc(oppositeType, "PENDING");

        List<MatchedItem> matched = new ArrayList<>();
        String[] newKeywords = (newItem.getKeywords() != null ? newItem.getKeywords() :
                extractKeywords(newItem.getItemName() + " " + newItem.getDescription())).split("[,，\\s]+");

        for (LostItem candidate : candidates) {
            String[] candKeywords = (candidate.getKeywords() != null ? candidate.getKeywords() :
                    extractKeywords(candidate.getItemName() + " " + candidate.getDescription())).split("[,，\\s]+");

            double similarity = calculateSimilarity(newKeywords, candKeywords);

            // 类别匹配加分
            if (newItem.getItemCategory() != null && newItem.getItemCategory().equals(candidate.getItemCategory())) {
                similarity += 20;
            }

            // 地点匹配加分
            if (newItem.getLostLocation() != null && candidate.getLostLocation() != null
                    && newItem.getLostLocation().contains(candidate.getLostLocation().substring(0, Math.min(3, candidate.getLostLocation().length())))) {
                similarity += 10;
            }

            if (similarity >= 50) {
                matched.add(new MatchedItem(candidate, similarity));
            }
        }

        matched.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        List<LostItem> result = new ArrayList<>();
        for (MatchedItem m : matched) {
            result.add(m.item);
        }
        return result;
    }

    private double calculateSimilarity(String[] a, String[] b) {
        if (a.length == 0 || b.length == 0) return 0;
        int match = 0;
        for (String wa : a) {
            for (String wb : b) {
                if (wa.equals(wb) || (wa.length() > 1 && wb.length() > 1 && (wa.contains(wb) || wb.contains(wa)))) {
                    match++;
                    break;
                }
            }
        }
        return (match * 100.0) / Math.max(a.length, b.length);
    }

    private String extractKeywords(String text) {
        if (text == null) return "";
        // 简单提取：移除常见停用词，保留有意义的词
        String cleaned = text.replaceAll("[，。？！、；：（）【】\\d\\s\"']+", " ");
        List<String> words = new ArrayList<>();
        for (String w : cleaned.split(" ")) {
            if (w.length() >= 2 && !isStopWord(w)) {
                words.add(w);
            }
        }
        return String.join(",", words);
    }

    private boolean isStopWord(String word) {
        String[] stopWords = {"的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"};
        for (String sw : stopWords) {
            if (word.equals(sw)) return true;
        }
        return false;
    }

    private static class MatchedItem {
        LostItem item;
        double similarity;

        MatchedItem(LostItem item, double similarity) {
            this.item = item;
            this.similarity = similarity;
        }
    }
}
