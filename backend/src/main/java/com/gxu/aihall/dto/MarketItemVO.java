package com.gxu.aihall.dto;

import com.gxu.aihall.entity.MarketItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 二手商品对外视图：商品字段 + 脱敏后的卖家信息
 */
@Data
public class MarketItemVO {
    private Long id;
    private Long sellerId;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String category;
    private String itemCondition;
    private String images;
    /**
     * 首图缩略图 URL（由 images 按命名约定推导，见 FileStorageService.thumbnailOf）。
     * 列表页只加载它，详情页才用原图；没有缩略图时等于原图，前端无需分支。
     */
    private String thumbUrl;
    private String tradeLocation;
    private String status;
    private Integer viewCount;
    private Integer shareCount;
    private LocalDateTime onlineTime;
    private LocalDateTime createTime;

    /** 卖家公开信息（已脱敏） */
    private PublicUserVO seller;

    public static MarketItemVO from(MarketItem item, PublicUserVO seller) {
        MarketItemVO vo = new MarketItemVO();
        vo.setId(item.getId());
        vo.setSellerId(item.getSellerId());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setPrice(item.getPrice());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setCategory(item.getCategory());
        vo.setItemCondition(item.getItemCondition());
        vo.setImages(item.getImages());
        vo.setThumbUrl(thumbOf(item.getImages()));
        vo.setTradeLocation(item.getTradeLocation());
        vo.setStatus(item.getStatus());
        vo.setViewCount(item.getViewCount());
        vo.setShareCount(item.getShareCount());
        vo.setOnlineTime(item.getOnlineTime());
        vo.setCreateTime(item.getCreateTime());
        vo.setSeller(seller);
        return vo;
    }

    /** 从逗号分隔的 images 里取首图，再推导缩略图 URL */
    private static String thumbOf(String images) {
        if (images == null || images.isBlank()) return null;
        int comma = images.indexOf(',');
        String first = (comma < 0 ? images : images.substring(0, comma)).trim();
        if (first.isEmpty()) return null;
        return com.gxu.aihall.service.FileStorageService.thumbnailOf(first);
    }
}
