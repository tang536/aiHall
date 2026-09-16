package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.common.PageQuery;
import com.gxu.aihall.common.PageResult;
import com.gxu.aihall.dto.MarketItemVO;
import com.gxu.aihall.dto.PublicUserVO;
import com.gxu.aihall.entity.MarketFavorite;
import com.gxu.aihall.entity.MarketItem;
import com.gxu.aihall.entity.MarketOrder;
import com.gxu.aihall.entity.User;
import com.gxu.aihall.cache.CacheConfig;
import com.gxu.aihall.repository.MarketFavoriteRepository;
import com.gxu.aihall.repository.MarketItemRepository;
import com.gxu.aihall.repository.MarketOrderRepository;
import com.gxu.aihall.repository.UserRepository;
import com.gxu.aihall.search.KeywordSearchSupport;
import com.gxu.aihall.util.UserPrivacyUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 二手交易服务
 * 金额相关操作（购买）使用事务包裹，并通过悲观锁保证并发安全：
 * 商品行锁避免一物多卖，用户行锁避免余额超额扣减。
 */
@Service
public class MarketService {

    private final MarketItemRepository marketItemRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final MarketFavoriteRepository marketFavoriteRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final KeywordSearchSupport keywordSearch;
    private final UserNotificationService userNotificationService;

    public MarketService(MarketItemRepository marketItemRepository,
                         MarketOrderRepository marketOrderRepository,
                         MarketFavoriteRepository marketFavoriteRepository,
                         UserRepository userRepository,
                         WalletService walletService,
                         KeywordSearchSupport keywordSearch,
                         UserNotificationService userNotificationService) {
        this.marketItemRepository = marketItemRepository;
        this.marketOrderRepository = marketOrderRepository;
        this.marketFavoriteRepository = marketFavoriteRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.keywordSearch = keywordSearch;
        this.userNotificationService = userNotificationService;
    }

    // ==================== 查询 ====================

    /** 商品列表（关键词 + 分类过滤，仅返回在售商品）——过滤条件下沉到 SQL，只物化当前页 */
    @org.springframework.cache.annotation.Cacheable(cacheNames = CacheConfig.MARKET_LIST,
            key = "#keyword + '|' + #category + '|' + #page + '|' + #size")
    public PageResult<MarketItemVO> list(String keyword, String category, int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        Page<MarketItem> result = keywordSearch.canUseFullText(keyword)
                // 原生查询须用无排序 Pageable：Sort 属性名(createTime)会被拼进 ORDER BY，而 SQL 列名是 create_time
                ? marketItemRepository.searchByKeyword("ON_SALE", filterValue(category), keyword.trim(), q.pageable())
                : marketItemRepository.findAll(spec("ON_SALE", category, keyword, null), q.pageable(NEWEST_FIRST));
        return PageResult.fromPage(result, toVOList(result.getContent()));
    }

    public MarketItemVO getDetail(Long id, boolean countView) {
        MarketItem item = marketItemRepository.findById(id).orElse(null);
        if (item == null) return null;
        if (countView) {
            item.setViewCount((item.getViewCount() == null ? 0 : item.getViewCount()) + 1);
            marketItemRepository.save(item);
        }
        return toVO(item);
    }

    public List<MarketItemVO> listMine(Long sellerId) {
        return toVOList(marketItemRepository.findBySellerIdOrderByCreateTimeDesc(sellerId));
    }

    public List<MarketOrder> listBought(Long buyerId) {
        return marketOrderRepository.findByBuyerIdOrderByCreateTimeDesc(buyerId);
    }

    public List<MarketOrder> listSold(Long sellerId) {
        return marketOrderRepository.findBySellerIdOrderByCreateTimeDesc(sellerId);
    }

    /** 管理员视角：全部交易订单（分页） */
    public PageResult<MarketOrder> listAllOrders(int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        return PageResult.from(marketOrderRepository.findAll(
                q.pageable(Sort.by(Sort.Direction.DESC, "createTime"))));
    }

    /** 管理员视角：全部商品（可按状态/关键词过滤）——条件下沉到 SQL，保留已下架/已售出 */
    @org.springframework.cache.annotation.Cacheable(cacheNames = CacheConfig.MARKET_LIST,
            key = "#keyword + '|' + #category + '|' + #page + '|' + #size")
    public PageResult<MarketItemVO> adminList(String status, String keyword, int page, int size) {
        PageQuery q = PageQuery.of(page, size);
        Page<MarketItem> result = keywordSearch.canUseFullText(keyword)
                ? marketItemRepository.searchByKeyword(filterValue(status), "", keyword.trim(), q.pageable())
                : marketItemRepository.findAll(spec(null, null, keyword, status), q.pageable(NEWEST_FIRST));
        return PageResult.fromPage(result, toVOList(result.getContent()));
    }

    /** 排序统一在这里定义：原生查询与 Specification 查询要保持一致 */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createTime");

    /** 空值 / ALL 统一成空串：原生 SQL 里用 '' 表示「该维度不过滤」 */
    private static String filterValue(String value) {
        return (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) ? "" : value.trim();
    }

    /**
     * 组装商品查询条件。
     * @param fixedStatus  强制状态（学生端只看在售），为 null 时不限制
     * @param fixedCategory 强制分类，为 null 时不限制
     * @param keyword      标题/描述模糊匹配
     * @param statusFilter 管理端显式选择的状态过滤（ALL 或空视为不过滤）
     */
    private Specification<MarketItem> spec(String fixedStatus, String fixedCategory,
                                           String keyword, String statusFilter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fixedStatus != null) {
                predicates.add(cb.equal(root.get("status"), fixedStatus));
            } else if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equals(statusFilter)) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (fixedCategory != null && !fixedCategory.isBlank() && !"ALL".equals(fixedCategory)) {
                predicates.add(cb.equal(root.get("category"), fixedCategory));
            }
            String kw = keyword == null ? null : keyword.trim().toLowerCase();
            if (kw != null && !kw.isEmpty()) {
                String pattern = "%" + kw + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ==================== 发布 / 上下架 ====================

    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public MarketItem publish(MarketItem item, Long sellerId) {
        validate(item);
        item.setId(null);
        item.setSellerId(sellerId);
        item.setStatus("ON_SALE");
        item.setViewCount(0);
        item.setShareCount(0);
        item.setOnlineTime(LocalDateTime.now());
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        return marketItemRepository.save(item);
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public MarketItem update(Long id, MarketItem form, Long operatorId) {
        MarketItem item = requireOwned(id, operatorId);
        validate(form);
        item.setTitle(form.getTitle().trim());
        item.setDescription(form.getDescription());
        item.setPrice(form.getPrice());
        item.setOriginalPrice(form.getOriginalPrice());
        if (form.getCategory() != null) item.setCategory(form.getCategory());
        if (form.getItemCondition() != null) item.setItemCondition(form.getItemCondition());
        item.setImages(form.getImages());
        item.setTradeLocation(form.getTradeLocation());
        item.setUpdateTime(LocalDateTime.now());
        return marketItemRepository.save(item);
    }

    /** 下架 */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public MarketItem offShelf(Long id, Long operatorId) {
        MarketItem item = requireOwned(id, operatorId);
        if ("SOLD".equals(item.getStatus())) {
            throw new BizException("商品已售出，无需下架");
        }
        item.setStatus("OFF_SHELF");
        item.setUpdateTime(LocalDateTime.now());
        return marketItemRepository.save(item);
    }

    /** 重新上架 */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public MarketItem onShelf(Long id, Long operatorId) {
        MarketItem item = requireOwned(id, operatorId);
        if ("SOLD".equals(item.getStatus())) {
            throw new BizException("商品已售出，不能重新上架");
        }
        item.setStatus("ON_SALE");
        item.setOnlineTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        return marketItemRepository.save(item);
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public void delete(Long id, Long operatorId) {
        MarketItem item = requireOwned(id, operatorId);
        if (marketOrderRepository.existsByItemIdAndStatus(id, "PAID")) {
            throw new BizException("该商品已有成交订单，不能删除，请改为下架");
        }
        marketItemRepository.delete(item);
    }

    /** 管理员删除（不校验归属） */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public void adminDelete(Long id) {
        marketItemRepository.findById(id).ifPresent(marketItemRepository::delete);
    }

    /** 管理员强制下架 */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public MarketItem adminOffShelf(Long id) {
        MarketItem item = marketItemRepository.findById(id)
                .orElseThrow(() -> new BizException("商品不存在"));
        item.setStatus("OFF_SHELF");
        item.setUpdateTime(LocalDateTime.now());
        return marketItemRepository.save(item);
    }

    /** 管理员恢复上架 */
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public MarketItem adminOnShelf(Long id) {
        MarketItem item = marketItemRepository.findById(id)
                .orElseThrow(() -> new BizException("商品不存在"));
        if ("SOLD".equals(item.getStatus())) {
            throw new BizException("商品已售出，不能恢复上架");
        }
        item.setStatus("ON_SALE");
        item.setOnlineTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        return marketItemRepository.save(item);
    }

    /** 分享：仅累加分享次数，返回分享链接 */
    public Map<String, Object> share(Long id) {
        MarketItem item = marketItemRepository.findById(id)
                .orElseThrow(() -> new BizException("商品不存在"));
        item.setShareCount((item.getShareCount() == null ? 0 : item.getShareCount()) + 1);
        marketItemRepository.save(item);
        Map<String, Object> data = new HashMap<>();
        data.put("shareUrl", "http://localhost:5173/market/" + id);
        data.put("shareCount", item.getShareCount());
        return data;
    }

    // ==================== 购买（事务 + 悲观锁） ====================

    /**
     * 购买商品：单事务内完成，任一步失败整体回滚。
     * 商品行锁（FOR UPDATE）防一物多卖；余额在同一事务内悲观锁扣减，不足即回滚。
     * 幂等 opt-in：仅传合法 {@code Idempotency-Key} 时生效，命中同键返回原订单不二次扣款；
     * 不传该头则不做幂等，仍由行锁+状态校验保证只能成交一次（重复购买报「商品已售出」）。
     */
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(cacheNames = {CacheConfig.MARKET_LIST},
            allEntries = true)
    public MarketOrder buy(Long itemId, Long buyerId, String idempotencyKey) {
        String requestId = buildRequestId(idempotencyKey, buyerId);

        // 0) 幂等前置查：命中直接返回原订单（仅在显式传了 Idempotency-Key 时生效）
        if (requestId != null) {
            MarketOrder existing = marketOrderRepository.findFirstByRequestId(requestId).orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        // 1) 商品行锁：防一物多卖
        MarketItem item = marketItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BizException("商品不存在或已被删除"));

        // 1.5) 拿到行锁后再查一次：覆盖「同键请求并发」的情况
        if (requestId != null) {
            MarketOrder duplicated = marketOrderRepository.findFirstByRequestId(requestId).orElse(null);
            if (duplicated != null) {
                return duplicated;
            }
        }

        // 2) 基础校验
        if (buyerId.equals(item.getSellerId())) {
            throw new BizException("不能购买自己发布的商品");
        }
        if (!"ON_SALE".equals(item.getStatus())) {
            throw new BizException("SOLD".equals(item.getStatus()) ? "商品已售出" : "商品已下架");
        }
        BigDecimal price = item.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("商品价格异常，无法交易");
        }

        // 3) 订单号先生成，供流水关联
        String orderNo = "MO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 4) 扣款 / 入账（内部按用户 id 升序加锁，避免死锁；余额不足会抛异常触发回滚）
        walletService.transfer(buyerId, item.getSellerId(), price, orderNo, "购买二手商品：" + item.getTitle());

        // 5) 商品置为已售出
        item.setStatus("SOLD");
        item.setUpdateTime(LocalDateTime.now());
        marketItemRepository.save(item);

        // 6) 生成订单
        MarketOrder order = new MarketOrder();
        order.setOrderNo(orderNo);
        order.setRequestId(requestId);
        order.setItemId(item.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(item.getSellerId());
        order.setAmount(price);
        order.setItemTitle(item.getTitle());
        order.setStatus("PAID");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        MarketOrder savedOrder = marketOrderRepository.save(order);
        try {
            User buyer = userRepository.findById(buyerId).orElse(null);
            String buyerName = buyer != null ? UserPrivacyUtil.toPublicVO(buyer).getDisplayName() : "某位同学";
            userNotificationService.notifyItemPurchased(item.getSellerId(), item.getTitle(), buyerName, savedOrder.getId());
        } catch (Exception ignored) {}
        return savedOrder;
    }

    /**
     * 构造订单幂等键：仅当客户端显式传入合法 {@code Idempotency-Key} 时返回非空。
     * 键按买家隔离命名空间，避免不同买家复用同一个客户端键互相干扰。
     * 返回 null 表示本次下单未启用幂等。
     */
    private String buildRequestId(String idempotencyKey, Long buyerId) {
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        if (key == null || key.isEmpty() || key.length() > 48 || !key.matches("[A-Za-z0-9_.:-]+")) {
            return null;
        }
        return "key:" + buyerId + ":" + key;
    }

    // ==================== 内部工具 ====================

    private void validate(MarketItem item) {
        if (item.getTitle() == null || item.getTitle().trim().isEmpty()) {
            throw new BizException("请填写商品标题");
        }
        if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("请填写正确的商品价格");
        }
        if (item.getPrice().compareTo(new BigDecimal("999999")) > 0) {
            throw new BizException("商品价格过高，请核对后重新提交");
        }
        if (item.getDescription() == null || item.getDescription().trim().isEmpty()) {
            throw new BizException("请填写商品描述");
        }
    }

    private MarketItem requireOwned(Long id, Long operatorId) {
        MarketItem item = marketItemRepository.findById(id)
                .orElseThrow(() -> new BizException("商品不存在"));
        if (!item.getSellerId().equals(operatorId)) {
            throw new BizException("只能操作自己发布的商品");
        }
        return item;
    }

    /** 批量转换：一次性把这一页涉及到的卖家查出来，避免逐行查询（N+1） */
    private List<MarketItemVO> toVOList(List<MarketItem> items) {
        List<MarketItemVO> result = new ArrayList<>();
        if (items == null || items.isEmpty()) return result;

        Set<Long> sellerIds = new HashSet<>();
        for (MarketItem item : items) {
            if (item.getSellerId() != null) sellerIds.add(item.getSellerId());
        }
        Map<Long, User> sellers = new HashMap<>();
        userRepository.findAllById(sellerIds).forEach(u -> sellers.put(u.getId(), u));

        for (MarketItem item : items) {
            result.add(MarketItemVO.from(item, UserPrivacyUtil.toPublicVO(sellers.get(item.getSellerId()))));
        }
        return result;
    }

    private PublicUserVO toSellerVO(Long sellerId, Map<Long, User> cache) {
        User user = cache.computeIfAbsent(sellerId, id -> userRepository.findById(id).orElse(null));
        return UserPrivacyUtil.toPublicVO(user);
    }

    private MarketItemVO toVO(MarketItem item) {
        return MarketItemVO.from(item, toSellerVO(item.getSellerId(), new HashMap<>()));
    }

    // ==================== 收藏 ====================

    @Transactional
    public boolean toggleFavorite(Long userId, Long itemId) {
        if (marketFavoriteRepository.existsByUserIdAndItemId(userId, itemId)) {
            marketFavoriteRepository.deleteByUserIdAndItemId(userId, itemId);
            return false;
        }
        MarketFavorite fav = new MarketFavorite();
        fav.setUserId(userId);
        fav.setItemId(itemId);
        fav.setCreateTime(LocalDateTime.now());
        marketFavoriteRepository.save(fav);
        return true;
    }

    public boolean isFavorited(Long userId, Long itemId) {
        return marketFavoriteRepository.existsByUserIdAndItemId(userId, itemId);
    }

    public List<MarketItemVO> listFavorites(Long userId) {
        List<MarketFavorite> favs = marketFavoriteRepository.findByUserIdOrderByCreateTimeDesc(userId);
        if (favs.isEmpty()) return new ArrayList<>();
        List<Long> itemIds = favs.stream().map(MarketFavorite::getItemId).toList();
        List<MarketItem> items = marketItemRepository.findAllById(itemIds);
        Map<Long, MarketItem> itemMap = new HashMap<>();
        items.forEach(i -> itemMap.put(i.getId(), i));
        List<MarketItem> sorted = new ArrayList<>();
        for (MarketFavorite fav : favs) {
            MarketItem item = itemMap.get(fav.getItemId());
            if (item != null) sorted.add(item);
        }
        return toVOList(sorted);
    }
}
