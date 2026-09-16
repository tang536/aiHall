package com.gxu.aihall.controller;

import com.gxu.aihall.common.PageResult;
import com.gxu.aihall.common.Result;
import com.gxu.aihall.doc.ApiDoc;
import com.gxu.aihall.dto.MarketItemVO;
import com.gxu.aihall.entity.MarketItem;
import com.gxu.aihall.entity.MarketOrder;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.service.AuthService;
import com.gxu.aihall.service.MarketService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 二手交易平台接口。
 * 购买（涉及金额）在后端由事务 + 悲观锁保证一致性，前端无需做任何补偿逻辑。
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final AuthService authService;
    private final MarketService marketService;

    public MarketController(AuthService authService, MarketService marketService) {
        this.authService = authService;
        this.marketService = marketService;
    }

    // ==================== 公开浏览 ====================

    @GetMapping
    public Result<PageResult<MarketItemVO>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        return Result.success(marketService.list(keyword, category, page, size));
    }

    @GetMapping("/{id}")
    public Result<MarketItemVO> detail(@PathVariable Long id) {
        MarketItemVO vo = marketService.getDetail(id, true);
        if (vo == null) return Result.error("商品不存在或已被删除");
        return Result.success(vo);
    }

    /** 分享商品：累加分享次数并返回分享链接（无需登录） */
    @PostMapping("/{id}/share")
    public Result<Map<String, Object>> share(@PathVariable Long id) {
        return Result.success("已生成分享链接", marketService.share(id));
    }

    // ==================== 需登录 ====================

    @PostMapping
    public Result<MarketItem> publish(@RequestHeader(value = "Authorization", required = false) String token,
                                      @RequestBody MarketItem item) {
        User user = authService.requireLogin(token, "请先登录后再发布商品");
        return Result.success("发布成功", marketService.publish(item, user.getId()));
    }

    @PutMapping("/{id}")
    public Result<MarketItem> update(@RequestHeader(value = "Authorization", required = false) String token,
                                     @PathVariable Long id, @RequestBody MarketItem item) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success("修改成功", marketService.update(id, item, user.getId()));
    }

    /** 下架 */
    @PostMapping("/{id}/offshelf")
    public Result<MarketItem> offShelf(@RequestHeader(value = "Authorization", required = false) String token,
                                       @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success("已下架", marketService.offShelf(id, user.getId()));
    }

    /** 重新上架 */
    @PostMapping("/{id}/onshelf")
    public Result<MarketItem> onShelf(@RequestHeader(value = "Authorization", required = false) String token,
                                      @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success("已重新上架", marketService.onShelf(id, user.getId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader(value = "Authorization", required = false) String token,
                               @PathVariable Long id) {
        User user = authService.requireLogin(token, "未登录");
        marketService.delete(id, user.getId());
        return Result.success("已删除", null);
    }

    /**
     * 购买：事务 + 悲观锁，保证不会一物多卖或超额扣款。
     * <p>可选带上 {@code Idempotency-Key} 头开启幂等：带上后网络重试、用户连点都会命中同一笔订单，
     * 服务端直接返回原订单而不是再扣一次钱。不带该头时不做幂等，重复购买会明确报「商品已售出」。
     */
    @ApiDoc("购买二手商品（事务 + 悲观锁扣款；可选 Idempotency-Key 防重复下单）")
    @PostMapping("/{id}/buy")
    public Result<MarketOrder> buy(@RequestHeader(value = "Authorization", required = false) String token,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @PathVariable Long id) {
        User user = authService.requireLogin(token, "请先登录后再购买");
        MarketOrder order = marketService.buy(id, user.getId(), idempotencyKey);
        return Result.success("购买成功，交易金额已从余额中扣除", order);
    }

    @GetMapping("/mine")
    public Result<List<MarketItemVO>> mine(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(marketService.listMine(user.getId()));
    }

    @GetMapping("/orders/mine")
    public Result<List<MarketOrder>> myOrders(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(marketService.listBought(user.getId()));
    }

    @GetMapping("/orders/sold")
    public Result<List<MarketOrder>> soldOrders(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "未登录");
        return Result.success(marketService.listSold(user.getId()));
    }

    // ==================== 收藏 ====================

    @PostMapping("/favorites/{itemId}")
    public Result<java.util.Map<String, Object>> toggleFavorite(@RequestHeader(value = "Authorization", required = false) String token,
                                                                   @PathVariable Long itemId) {
        User user = authService.requireLogin(token, "请先登录");
        boolean favorited = marketService.toggleFavorite(user.getId(), itemId);
        return Result.success(favorited ? "已收藏" : "已取消收藏", java.util.Map.of("favorited", favorited));
    }

    @GetMapping("/favorites")
    public Result<java.util.List<MarketItemVO>> myFavorites(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = authService.requireLogin(token, "请先登录");
        return Result.success(marketService.listFavorites(user.getId()));
    }

    @GetMapping("/favorites/check/{itemId}")
    public Result<java.util.Map<String, Object>> checkFavorite(@RequestHeader(value = "Authorization", required = false) String token,
                                                                  @PathVariable Long itemId) {
        User user = authService.requireLogin(token, "请先登录");
        boolean favorited = marketService.isFavorited(user.getId(), itemId);
        return Result.success(java.util.Map.of("favorited", favorited));
    }
}
