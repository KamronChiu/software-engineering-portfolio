package com.campus.trade.controller.product;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.activity.ActivityType;
import com.campus.trade.entity.activity.UserActivity;
import com.campus.trade.entity.product.HotKeyword;
import com.campus.trade.entity.product.PriceCutRecord;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductBrowse;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.activity.UserActivityRepository;
import com.campus.trade.repository.product.HotKeywordRepository;
import com.campus.trade.repository.product.PriceCutRecordRepository;
import com.campus.trade.repository.product.ProductBrowseRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.product.ProductCommentRepository;
import com.campus.trade.repository.social.FavoriteRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository products;
    private final ProductCommentRepository comments;
    private final UserRepository users;
    private final FavoriteRepository favorites;
    private final PriceCutRecordRepository priceCutRecords;
    private final CurrentUserService currentUser;
    private final UserActivityRepository activities;
    private final ProductBrowseRepository browses;
    private final HotKeywordRepository hotKeywords;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    public ProductController(ProductRepository products, ProductCommentRepository comments,
                             UserRepository users, FavoriteRepository favorites,
                             PriceCutRecordRepository priceCutRecords,
                             CurrentUserService currentUser, UserActivityRepository activities,
                             ProductBrowseRepository browses, HotKeywordRepository hotKeywords) {
        this.products = products;
        this.comments = comments;
        this.users = users;
        this.favorites = favorites;
        this.priceCutRecords = priceCutRecords;
        this.currentUser = currentUser;
        this.activities = activities;
        this.browses = browses;
        this.hotKeywords = hotKeywords;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String condition,
                                                 @RequestParam(required = false) BigDecimal minPrice,
                                                 @RequestParam(required = false) BigDecimal maxPrice,
                                                 @RequestParam(defaultValue = "newest") String sort,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        recordHotKeyword(keyword);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        // 置顶期内优先；随后按 擦亮时间 / 发布时间 倒序（sort=sharpen 时擦亮优先）
        Comparator<Product> comparator = switch (sort) {
            case "priceAsc" -> Comparator.comparing(Product::getPrice);
            case "priceDesc" -> Comparator.comparing(Product::getPrice).reversed();
            case "sharpen" -> Comparator
                    .comparing((Product p) -> p.getTopExpireAt() != null && p.getTopExpireAt().isAfter(LocalDateTime.now()) ? 0 : 1)
                    .thenComparing(Comparator.comparing(Product::getSharpenedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .thenComparing(Comparator.comparing(Product::getCreatedAt).reversed());
            default -> Comparator
                    .comparing((Product p) -> p.getTopExpireAt() != null && p.getTopExpireAt().isAfter(LocalDateTime.now()) ? 0 : 1)
                    .thenComparing(Comparator.comparing(Product::getCreatedAt).reversed());
        };
        List<Product> all = products.findByStatusOrderByCreatedAtDesc(ProductStatus.AVAILABLE).stream()
                .filter(product -> blank(keyword) || product.getTitle().toLowerCase().contains(kw)
                        || product.getDescription().toLowerCase().contains(kw)
                        || nonBlankContains(product.getIsbn(), kw)
                        || nonBlankContains(product.getCourseCode(), kw)
                        || nonBlankContains(product.getModuleName(), kw))
                .filter(product -> blank(category) || product.getCategory().equals(category))
                .filter(product -> blank(condition) || product.getCondition().equals(condition))
                .filter(product -> minPrice == null || product.getPrice().compareTo(minPrice) >= 0)
                .filter(product -> maxPrice == null || product.getPrice().compareTo(maxPrice) <= 0)
                .sorted(comparator)
                .toList();
        int total = all.size();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<Map<String, Object>> items = all.subList(from, to).stream().map(this::view).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, HttpSession session) {
        Product product = requireProduct(id);
        Long viewerId = null;
        try {
            viewerId = currentUser.require(session).getId();
        } catch (BusinessException ignored) {
        }
        if (product.getStatus() == ProductStatus.DRAFT) {
            if (!product.getSellerId().equals(viewerId)) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
            }
        }
        boolean cutByMe = viewerId != null && !product.getSellerId().equals(viewerId)
                && priceCutRecords.existsByProductIdAndBuyerId(id, viewerId);
        // T4.4 最近浏览：登录且非本人浏览时记录（重复浏览只刷新时间）
        if (viewerId != null && !product.getSellerId().equals(viewerId)) {
            recordBrowse(viewerId, id);
        }
        product.setViewCount(product.getViewCount() + 1);
        Map<String, Object> data = view(products.save(product));
        data.put("cutByMe", cutByMe);
        if (cutByMe && viewerId != null) {
            priceCutRecords.findByProductIdAndBuyerId(id, viewerId)
                    .ifPresent(record -> data.put("cutPrice", record.getPriceAfter()));
        }
        return ApiResponse.success(data);
    }

    @GetMapping("/{id}/comparisons")
    public ApiResponse<Map<String, Object>> comparisons(@PathVariable Long id) {
        Product current = requireProduct(id);
        List<Map<String, Object>> matches = products
                .findByCategoryAndIdNotAndStatusNotOrderByCreatedAtDesc(
                        current.getCategory(), current.getId(), ProductStatus.OFF_SHELF)
                .stream()
                .map(product -> new AbstractMap.SimpleEntry<>(product, titleSimilarity(current.getTitle(), product.getTitle())))
                .sorted(Map.Entry.<Product, Double>comparingByValue().reversed()
                        .thenComparing(entry -> entry.getKey().getCreatedAt(), Comparator.reverseOrder()))
                .limit(12)
                .map(entry -> comparisonView(entry.getKey(), entry.getValue()))
                .toList();

        List<BigDecimal> marketPrices = matches.stream()
                .map(item -> (BigDecimal) item.get("price"))
                .toList();
        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("sampleCount", marketPrices.size());
        if (marketPrices.isEmpty()) {
            statistics.put("lowestPrice", null);
            statistics.put("averagePrice", null);
            statistics.put("highestPrice", null);
            statistics.put("differencePercent", null);
        } else {
            BigDecimal lowest = marketPrices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal highest = marketPrices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal average = marketPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(marketPrices.size()), 2, RoundingMode.HALF_UP);
            BigDecimal difference = current.getPrice().subtract(average)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(average, 1, RoundingMode.HALF_UP);
            statistics.put("lowestPrice", lowest);
            statistics.put("averagePrice", average);
            statistics.put("highestPrice", highest);
            statistics.put("differencePercent", difference);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentProduct", comparisonView(current, 1D));
        result.put("comparisons", matches);
        result.put("statistics", statistics);
        result.put("notice", "数据来自 StudentBay 校内商品挂牌价，不代表最终成交价");
        return ApiResponse.success(result);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> publish(@RequestBody PublishRequest request, HttpSession session) {
        AppUser user = currentUser.require(session);
        boolean draft = "DRAFT".equalsIgnoreCase(request.status());
        validatePublish(request, draft);

        Product product = new Product();
        product.setSellerId(user.getId());
        product.setTitle(request.title().trim());
        product.setDescription(request.description() == null ? "" : request.description().trim());
        product.setCategory(request.category());
        product.setCondition(request.condition());
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setImageUrl(request.imageUrl());
        product.setLocationName(request.locationName());
        product.setLatitude(request.latitude());
        product.setLongitude(request.longitude());
        product.setPoiId(request.poiId());
        product.setAddressDetail(request.addressDetail());
        product.setAddressCountry(request.addressCountry());
        product.setAddressProvince(request.addressProvince());
        product.setAddressCity(request.addressCity());
        product.setAddressDistrict(request.addressDistrict());
        product.setAddressStreet(request.addressStreet());
        applyImages(product, request.images());
        applyCutConfig(product, request);
        applyStage4Fields(product, request);
        product.setStatus(draft ? ProductStatus.DRAFT : ProductStatus.AVAILABLE);
        Product saved = products.save(product);
        if (!draft) {
            UserActivity activity = new UserActivity();
            activity.setUserId(user.getId());
            activity.setType(ActivityType.PUBLISH);
            activity.setProductId(saved.getId());
            activity.setTitle(saved.getTitle());
            activities.save(activity);
        }
        return ApiResponse.success(201, draft ? "草稿已保存" : "发布成功", view(saved));
    }

    @PostMapping("/{id}/bargain")
    @Transactional
    public ApiResponse<Map<String, Object>> bargain(@PathVariable Long id, HttpSession session) {
        AppUser user = currentUser.require(session);
        Product product = requireProduct(id);
        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new BusinessException(HttpStatus.CONFLICT, "该商品当前不可砍价");
        }
        if (product.getSellerId().equals(user.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能砍自己的商品");
        }
        if (product.getCutEnabled() == null || !product.getCutEnabled()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该商品未开启砍价");
        }
        if (priceCutRecords.existsByProductIdAndBuyerId(id, user.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "你已对该商品砍过价了");
        }
        int limit = product.getCutLimit() == null ? 3 : product.getCutLimit();
        int count = product.getCutCount() == null ? 0 : product.getCutCount();
        if (count >= limit) {
            throw new BusinessException(HttpStatus.CONFLICT, "该商品已被砍到最低价了");
        }
        BigDecimal min = product.getCutMin();
        BigDecimal max = product.getCutMax();
        if (min == null || max == null || max.compareTo(min) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "砍价区间未配置");
        }
        double random = min.doubleValue() + Math.random() * (max.doubleValue() - min.doubleValue());
        BigDecimal amount = BigDecimal.valueOf(random).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = new BigDecimal("0.01");
        }
        BigDecimal before = product.getPrice();
        BigDecimal after = before.subtract(amount);
        // 买家独享价下限为 1 元（不随原价/在售价链式变化，售价低于原价一半也能正常砍）
        BigDecimal floor = new BigDecimal("1.00");
        if (after.compareTo(floor) < 0) {
            after = floor;
            amount = before.subtract(after);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                amount = BigDecimal.ZERO;
            }
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "该商品已到可砍的最低价格");
        }
        // 砍价只对当前买家生成专属价，不改动商品挂牌原价
        product.setCutCount(count + 1);
        products.save(product);

        PriceCutRecord record = new PriceCutRecord();
        record.setProductId(id);
        record.setBuyerId(user.getId());
        record.setAmount(amount);
        record.setPriceBefore(before);
        record.setPriceAfter(after);
        priceCutRecords.save(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amount", amount);
        result.put("priceBefore", before);
        result.put("priceAfter", after);
        result.put("cutCount", count + 1);
        result.put("cutLimit", limit);
        return ApiResponse.success(200, "砍价成功", result);
    }

    // ---------- 阶段 4 新增接口（T4.2 热搜 / T4.4 浏览 / T4.5 擦亮置顶 / T4.6 推荐） ----------

    @GetMapping("/hot-keywords")
    public ApiResponse<List<Map<String, Object>>> hotKeywords() {
        return ApiResponse.success(hotKeywords.findTop10ByOrderByCountDesc().stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("keyword", item.getKeyword());
                    row.put("count", item.getCount());
                    return row;
                }).toList());
    }

    @GetMapping("/browse/recent")
    public ApiResponse<List<Map<String, Object>>> browseRecent(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        return ApiResponse.success(browses.findByUserIdOrderByViewedAtDesc(userId).stream()
                .map(browse -> products.findById(browse.getProductId()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::view)
                .toList());
    }

    @DeleteMapping("/browse/clear")
    public ApiResponse<Void> browseClear(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        browses.deleteByUserId(userId);
        return ApiResponse.success(200, "已清空浏览记录", null);
    }

    @PostMapping("/{id}/sharpen")
    @Transactional
    public ApiResponse<Map<String, Object>> sharpen(@PathVariable Long id, HttpSession session) {
        AppUser user = currentUser.require(session);
        Product product = requireProduct(id);
        if (!product.getSellerId().equals(user.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能擦亮自己发布的商品");
        }
        LocalDateTime now = LocalDateTime.now();
        if (product.getSharpenedAt() != null && product.getSharpenedAt().toLocalDate().equals(now.toLocalDate())) {
            throw new BusinessException(HttpStatus.CONFLICT, "今天已经擦亮过，明天再来吧");
        }
        product.setSharpenedAt(now);
        return ApiResponse.success(200, "擦亮成功，商品已提前", view(products.save(product)));
    }

    @PostMapping("/{id}/top")
    @Transactional
    public ApiResponse<Map<String, Object>> top(@PathVariable Long id,
                                                @RequestBody(required = false) TopRequest request,
                                                HttpSession session) {
        AppUser user = currentUser.require(session);
        Product product = requireProduct(id);
        if (!product.getSellerId().equals(user.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能置顶自己发布的商品");
        }
        int hours = request == null || request.hours() == null ? 24 : request.hours();
        if (hours <= 0 || hours > 720) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "置顶时长需在 1-720 小时之间");
        }
        product.setTopExpireAt(LocalDateTime.now().plusHours(hours));
        return ApiResponse.success(200, "置顶成功", view(products.save(product)));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<Map<String, Object>>> recommend(@RequestParam Long productId) {
        Product current = requireProduct(productId);
        List<Map<String, Object>> matches = products
                .findByCategoryAndIdNotAndStatusNotOrderByCreatedAtDesc(
                        current.getCategory(), current.getId(), ProductStatus.OFF_SHELF)
                .stream()
                .filter(p -> p.getStatus() != ProductStatus.SOLD && p.getStatus() != ProductStatus.DRAFT)
                .map(product -> new AbstractMap.SimpleEntry<>(product, titleSimilarity(current.getTitle(), product.getTitle())))
                .sorted(Map.Entry.<Product, Double>comparingByValue().reversed()
                        .thenComparing(entry -> entry.getKey().getCreatedAt(), Comparator.reverseOrder()))
                .limit(8)
                .map(entry -> comparisonView(entry.getKey(), entry.getValue()))
                .toList();
        return ApiResponse.success(matches);
    }

    public record TopRequest(Integer hours) {}

    @GetMapping("/mine/list")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestParam(required = false) String status, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        List<Product> all = products.findBySellerIdOrderByCreatedAtDesc(userId);
        if (!blank(status)) {
            Set<ProductStatus> allowed = new HashSet<>();
            for (String part : status.split(",")) {
                try {
                    allowed.add(ProductStatus.valueOf(part.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!allowed.isEmpty()) {
                all = all.stream().filter(product -> allowed.contains(product.getStatus())).toList();
            }
        }
        return ApiResponse.success(all.stream().map(this::view).toList());
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody PublishRequest request, HttpSession session) {
        AppUser user = currentUser.require(session);
        Product product = requireProduct(id);
        if (!product.getSellerId().equals(user.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能编辑自己发布的商品");
        }
        boolean draft = "DRAFT".equalsIgnoreCase(request.status());
        validatePublish(request, draft);
        product.setTitle(request.title().trim());
        product.setDescription(request.description() == null ? "" : request.description().trim());
        product.setCategory(request.category());
        product.setCondition(request.condition());
        product.setPrice(request.price());
        product.setOriginalPrice(request.originalPrice());
        product.setImageUrl(request.imageUrl());
        product.setLocationName(request.locationName());
        product.setLatitude(request.latitude());
        product.setLongitude(request.longitude());
        product.setPoiId(request.poiId());
        product.setAddressDetail(request.addressDetail());
        product.setAddressCountry(request.addressCountry());
        product.setAddressProvince(request.addressProvince());
        product.setAddressCity(request.addressCity());
        product.setAddressDistrict(request.addressDistrict());
        product.setAddressStreet(request.addressStreet());
        // T4.8 图片治理：先记录旧图，保存后删除被移除的本地文件
        List<String> imagesBefore = new ArrayList<>(product.getImages());
        applyImages(product, request.images());
        applyCutConfigUpdate(product, request);
        applyStage4Fields(product, request);
        // 非草稿保持原状态（在卖/已下架编辑仅更新内容）；草稿可按需直接转发布
        if (product.getStatus() == ProductStatus.DRAFT) {
            product.setStatus(draft ? ProductStatus.DRAFT : ProductStatus.AVAILABLE);
        }
        Product saved = products.save(product);
        List<String> removed = imagesBefore.stream().filter(url -> !saved.getImages().contains(url)).toList();
        deleteLocalImages(removed);
        return ApiResponse.success(200, "修改已保存", view(saved));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ApiResponse<Map<String, Object>> changeStatus(@PathVariable Long id, @RequestBody StatusRequest request, HttpSession session) {
        AppUser user = currentUser.require(session);
        Product product = requireProduct(id);
        if (!product.getSellerId().equals(user.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能操作自己发布的商品");
        }
        ProductStatus target;
        try {
            target = ProductStatus.valueOf(request.status());
        } catch (Exception ignored) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "无效的状态");
        }
        ProductStatus current = product.getStatus();
        boolean allowed = switch (current) {
            case AVAILABLE -> target == ProductStatus.OFF_SHELF;
            case OFF_SHELF, SOLD -> target == ProductStatus.AVAILABLE;
            case DRAFT -> target == ProductStatus.AVAILABLE;
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持该状态变更");
        }
        // 重新发布时按需清空历史砍价记录，让商品以全新状态重新上架
        if (target == ProductStatus.AVAILABLE && Boolean.TRUE.equals(request.clearCut())) {
            priceCutRecords.deleteByProductId(id);
            product.setCutCount(0);
        }
        product.setStatus(target);
        return ApiResponse.success(200, "状态已更新", view(products.save(product)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, HttpSession session) {
        AppUser user = currentUser.require(session);
        Product product = requireProduct(id);
        if (!product.getSellerId().equals(user.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能删除自己发布的商品");
        }
        if (product.getStatus() != ProductStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅草稿可删除");
        }
        // T4.8 图片治理：删除商品时同步清理本地图片文件与浏览足迹
        deleteLocalImages(imagesOf(product));
        browses.deleteByProductId(id);
        products.delete(product);
        return ApiResponse.success(200, "草稿已删除", null);
    }

    private void validatePublish(PublishRequest request, boolean draft) {
        if (draft) {
            if (blank(request.title())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "保存草稿至少需要填写商品标题");
            }
            return;
        }
        if (blank(request.title()) || blank(request.description()) || blank(request.category())
                || blank(request.condition()) || blank(request.locationName())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请完整填写商品标题、描述、分类、成色和交易地点");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写正确的售价");
        }
        if (request.latitude() == null || request.longitude() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请在地图上选择交易位置");
        }
    }

    private void applyImages(Product product, List<String> images) {
        List<String> normalized = images == null ? List.of() : images.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        product.getImages().clear();
        product.getImages().addAll(normalized);
        if (!normalized.isEmpty()) {
            product.setImageUrl(normalized.get(0));
        }
    }

    private void applyCutConfigUpdate(Product product, PublishRequest request) {
        if (request.cutEnabled() == null || !request.cutEnabled()) {
            product.setCutEnabled(false);
            return;
        }
        BigDecimal min = request.cutMin();
        BigDecimal max = request.cutMax();
        if (min == null || max == null || min.compareTo(BigDecimal.ZERO) <= 0 || max.compareTo(min) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请正确配置砍价区间");
        }
        product.setCutEnabled(true);
        product.setCutMin(min);
        product.setCutMax(max);
        product.setCutLimit(request.cutLimit() == null ? 3 : request.cutLimit());
    }

    private void applyCutConfig(Product product, PublishRequest request) {
        if (request.cutEnabled() == null || !request.cutEnabled()) {
            product.setCutEnabled(false);
            return;
        }
        BigDecimal min = request.cutMin();
        BigDecimal max = request.cutMax();
        if (min == null || max == null || min.compareTo(BigDecimal.ZERO) <= 0 || max.compareTo(min) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请正确配置砍价区间");
        }
        product.setCutEnabled(true);
        product.setCutMin(min);
        product.setCutMax(max);
        product.setCutLimit(request.cutLimit() == null ? 3 : request.cutLimit());
        product.setCutCount(0);
    }

    /** T4.3 / T4.7 阶段 4 参数字段：空字符串一律存 null */
    private void applyStage4Fields(Product product, PublishRequest request) {
        product.setBrand(trimToNull(request.brand()));
        product.setSize(trimToNull(request.size()));
        product.setModel(trimToNull(request.model()));
        product.setMaterial(trimToNull(request.material()));
        product.setColor(trimToNull(request.color()));
        product.setGender(trimToNull(request.gender()));
        product.setSportType(trimToNull(request.sportType()));
        product.setConditionDetail(trimToNull(request.conditionDetail()));
        product.setIsbn(trimToNull(request.isbn()));
        product.setCourseCode(trimToNull(request.courseCode()));
        product.setModuleName(trimToNull(request.moduleName()));
        product.setBargainEnabled(Boolean.TRUE.equals(request.bargainEnabled()));
    }

    private String trimToNull(String value) {
        return value == null ? null : (value.isBlank() ? null : value.trim());
    }

    /** T4.4 最近浏览：有则刷新时间，无则新增一行 */
    private void recordBrowse(Long userId, Long productId) {
        ProductBrowse browse = browses.findByUserIdAndProductId(userId, productId)
                .orElseGet(ProductBrowse::new);
        if (browse.getId() == null) {
            browse.setUserId(userId);
            browse.setProductId(productId);
        }
        browse.setViewedAt(LocalDateTime.now());
        browses.save(browse);
    }

    /** T4.2 热搜词计数 */
    private void recordHotKeyword(String keyword) {
        if (blank(keyword)) return;
        String key = keyword.trim();
        if (key.length() > 60) key = key.substring(0, 60);
        HotKeyword item = hotKeywords.findByKeyword(key).orElseGet(HotKeyword::new);
        if (item.getId() == null) {
            item.setKeyword(key);
            item.setCount(1L);
        } else {
            item.setCount(item.getCount() + 1);
        }
        hotKeywords.save(item);
    }

    /** T4.8 图片治理：只删 /uploads/ 本地文件，防止路径穿越（取文件名拼接） */
    private void deleteLocalImages(List<String> urls) {
        for (String url : urls) {
            if (url == null || !url.startsWith("/uploads/")) continue;
            try {
                Path file = Path.of(uploadDir, Path.of(url).getFileName().toString());
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean nonBlankContains(String value, String kw) {
        return value != null && !value.isBlank() && value.toLowerCase(Locale.ROOT).contains(kw);
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    private Map<String, Object> view(Product product) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", product.getId());
        result.put("sellerId", product.getSellerId());
        result.put("sellerName", users.findById(product.getSellerId()).map(AppUser::getDisplayName).orElse("校园用户"));
        result.put("title", product.getTitle());
        result.put("description", product.getDescription());
        result.put("category", product.getCategory());
        result.put("condition", product.getCondition());
        result.put("price", product.getPrice());
        result.put("originalPrice", product.getOriginalPrice());
        result.put("imageUrl", product.getImageUrl());
        result.put("images", imagesOf(product));
        result.put("locationName", product.getLocationName());
        result.put("addressDetail", product.getAddressDetail());
        result.put("addressCountry", product.getAddressCountry());
        result.put("addressProvince", product.getAddressProvince());
        result.put("addressCity", product.getAddressCity());
        result.put("addressDistrict", product.getAddressDistrict());
        result.put("addressStreet", product.getAddressStreet());
        result.put("latitude", product.getLatitude());
        result.put("longitude", product.getLongitude());
        result.put("status", product.getStatus());
        result.put("viewCount", product.getViewCount());
        result.put("favoriteCount", favorites.countByProductId(product.getId()));
        result.put("commentCount", comments.countByProductId(product.getId()));
        result.put("cutEnabled", product.getCutEnabled() == null ? false : product.getCutEnabled());
        result.put("cutMin", product.getCutMin());
        result.put("cutMax", product.getCutMax());
        result.put("cutLimit", product.getCutLimit());
        result.put("cutCount", product.getCutCount());
        result.put("brand", product.getBrand());
        result.put("size", product.getSize());
        result.put("model", product.getModel());
        result.put("material", product.getMaterial());
        result.put("color", product.getColor());
        result.put("gender", product.getGender());
        result.put("sportType", product.getSportType());
        result.put("conditionDetail", product.getConditionDetail());
        result.put("isbn", product.getIsbn());
        result.put("courseCode", product.getCourseCode());
        result.put("moduleName", product.getModuleName());
        result.put("bargainEnabled", Boolean.TRUE.equals(product.getBargainEnabled()));
        // T5.1/T5.2 智能回复：返回单品开关与内容，供"我发布的"卡片徽标与一键应用统计
        result.put("autoReplyEnabled", Boolean.TRUE.equals(product.getAutoReplyEnabled()));
        result.put("autoReplyText", product.getAutoReplyText() == null ? "" : product.getAutoReplyText());
        result.put("topExpireAt", product.getTopExpireAt());
        result.put("sharpenedAt", product.getSharpenedAt());
        result.put("createdAt", product.getCreatedAt());
        result.put("updatedAt", product.getUpdatedAt());
        return result;
    }

    private List<String> imagesOf(Product product) {
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            return product.getImages();
        }
        if (!blank(product.getImageUrl())) {
            return List.of(product.getImageUrl());
        }
        return List.of();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> comparisonView(Product product, double similarity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", product.getId());
        result.put("title", product.getTitle());
        result.put("price", product.getPrice());
        result.put("originalPrice", product.getOriginalPrice());
        result.put("imageUrl", product.getImageUrl());
        result.put("condition", product.getCondition());
        result.put("locationName", product.getLocationName());
        result.put("status", product.getStatus());
        result.put("createdAt", product.getCreatedAt());
        result.put("similarity", BigDecimal.valueOf(similarity * 100).setScale(0, RoundingMode.HALF_UP));
        result.put("matchType", similarity >= .45 ? "同款/高度相似" : similarity >= .25 ? "相似商品" : "同类商品");
        return result;
    }

    private double titleSimilarity(String first, String second) {
        String left = normalizeTitle(first);
        String right = normalizeTitle(second);
        if (left.equals(right)) return 1D;
        if (left.length() >= 4 && (left.contains(right) || right.contains(left))) return .9D;
        Set<String> leftParts = bigrams(left);
        Set<String> rightParts = bigrams(right);
        if (leftParts.isEmpty() || rightParts.isEmpty()) return 0D;
        long common = leftParts.stream().filter(rightParts::contains).count();
        return (2D * common) / (leftParts.size() + rightParts.size());
    }

    private String normalizeTitle(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s·，。、《》（）【】]+", "");
    }

    private Set<String> bigrams(String value) {
        Set<String> parts = new LinkedHashSet<>();
        for (int i = 0; i < value.length() - 1; i++) parts.add(value.substring(i, i + 2));
        return parts;
    }

    public record PublishRequest(
            String title,
            String description,
            String category,
            String condition,
            BigDecimal price,
            BigDecimal originalPrice,
            String imageUrl,
            List<String> images,
            String locationName,
            BigDecimal latitude,
            BigDecimal longitude,
            String status,
            Boolean cutEnabled,
            BigDecimal cutMin,
            BigDecimal cutMax,
            Integer cutLimit,
            Long poiId,
            String addressDetail,
            String addressCountry,
            String addressProvince,
            String addressCity,
            String addressDistrict,
            String addressStreet,
            String brand,
            String size,
            String model,
            String material,
            String color,
            String gender,
            String sportType,
            String conditionDetail,
            String isbn,
            String courseCode,
            String moduleName,
            Boolean bargainEnabled) {}

    public record StatusRequest(String status, Boolean clearCut) {}
}
