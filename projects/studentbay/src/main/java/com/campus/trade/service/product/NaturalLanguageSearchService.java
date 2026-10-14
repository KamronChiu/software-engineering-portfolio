package com.campus.trade.service.product;

import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.repository.product.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NaturalLanguageSearchService {
    private static final Pattern BUDGET_WITH_PREFIX = Pattern.compile("(?:预算|不超过|最多|低于|小于|控制在|价格在)[^0-9]{0,6}(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_WITH_UNIT = Pattern.compile("[¥￥]\\s*(\\d+(?:\\.\\d+)?)|(\\d+(?:\\.\\d+)?)\\s*(?:元|块)(?:以内|以下)?", Pattern.CASE_INSENSITIVE);
    private static final Map<String, List<String>> CATEGORY_WORDS = categoryWords();
    private static final Map<String, List<String>> PRODUCT_TYPE_SYNONYMS = productTypeSynonyms();
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "教材资料", "数码产品", "骑行装备", "宿舍生活", "服饰美妆", "其他");
    private static final Set<String> ALLOWED_CONDITIONS = Set.of(
            "全新", "九成新", "八成新", "七成新及以下");
    private static final List<String> PRODUCT_WORDS = List.of(
            "键盘", "鼠标", "耳机", "电脑", "平板", "充电宝", "扩展坞", "教材", "课本", "真题", "笔记",
            "自行车", "头盔", "车锁", "打气筒", "台灯", "桌", "收纳箱", "水壶", "坐垫", "晾衣架",
            "卫衣", "外套", "跑步鞋", "托特包", "化妆镜", "尤克里里", "球拍",
            "儿童玩具", "玩具", "积木", "拼图", "玩偶", "毛绒", "遥控车", "磁力片",
            "乐器", "吉他", "羽毛球", "计算器", "行李箱", "拉杆箱", "登机箱",
            "雨伞", "晴雨伞", "折叠伞", "雨具"
    );

    private final ProductRepository products;
    private final ObjectMapper json;
    private final boolean aiEnabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public NaturalLanguageSearchService(ProductRepository products, ObjectMapper json,
                                        @Value("${app.ai.enabled:false}") boolean aiEnabled,
                                        @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
                                        @Value("${app.ai.api-key:}") String apiKey,
                                        @Value("${app.ai.model:gpt-4.1-mini}") String model) {
        this.products = products;
        this.json = json;
        this.aiEnabled = aiEnabled;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
    }

    public Map<String, Object> search(String query) {
        String normalized = query == null ? "" : query.trim();
        SearchIntent intent = parseLocally(normalized);
        String mode = "LOCAL_SEMANTIC";
        if (aiEnabled && apiKey != null && !apiKey.isBlank()) {
            SearchIntent aiIntent = parseWithAi(normalized);
            if (aiIntent != null) {
                intent = merge(intent, aiIntent);
                mode = "AI_ASSISTED";
            } else {
                mode = "LOCAL_FALLBACK";
            }
        }
        intent = expandProductTypeKeywords(normalized, intent);

        SearchIntent resolved = intent;
        boolean requireProductMatch = hasSpecificProductIntent(normalized);
        List<Map<String, Object>> matches = products.findByStatusOrderByCreatedAtDesc(ProductStatus.AVAILABLE).stream()
                .filter(product -> resolved.maxPrice() == null || product.getPrice().compareTo(resolved.maxPrice()) <= 0)
                .filter(product -> resolved.category() == null || resolved.category().equals(product.getCategory()))
                .filter(product -> resolved.condition() == null || resolved.condition().equals(product.getCondition()))
                .filter(product -> !requireProductMatch || matchesAnyKeyword(product, resolved.keywords()))
                .map(product -> Map.entry(product, relevance(product, normalized, resolved)))
                .filter(entry -> resolved.keywords().isEmpty() || entry.getValue() > 0)
                .sorted(Map.Entry.<Product, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> entry.getKey().getCreatedAt(), Comparator.reverseOrder()))
                .limit(24)
                .map(entry -> productView(entry.getKey(), entry.getValue()))
                .toList();

        Map<String, Object> interpretation = new LinkedHashMap<>();
        interpretation.put("maxPrice", intent.maxPrice());
        interpretation.put("category", intent.category());
        interpretation.put("condition", intent.condition());
        interpretation.put("preferPickup", intent.preferPickup());
        interpretation.put("keywords", intent.keywords());
        interpretation.put("summary", intentSummary(intent));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", normalized);
        result.put("mode", mode);
        result.put("interpretation", interpretation);
        result.put("products", matches);
        return result;
    }

    private SearchIntent parseLocally(String query) {
        BigDecimal maxPrice = extractBudget(query);
        String category = CATEGORY_WORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(query::contains))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        String condition = List.of("全新", "九成新", "八成新", "七成新及以下").stream()
                .filter(query::contains).findFirst().orElse(null);
        boolean pickup = query.contains("面交") || query.contains("自提") || query.contains("校内取");
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        PRODUCT_WORDS.stream().filter(query::contains).forEach(keywords::add);
        if (query.contains("安静") || query.contains("静音") || query.contains("低噪")) {
            keywords.add("静音");
            keywords.add("茶轴");
            keywords.add("红轴");
        }
        if (query.contains("宿舍用") || query.contains("宿舍适合")) keywords.add("宿舍");
        return new SearchIntent(maxPrice, category, condition, pickup, List.copyOf(keywords));
    }

    @SuppressWarnings("unchecked")
    private SearchIntent parseWithAi(String query) {
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "temperature", 0,
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是校园二手商品搜索意图解析器。仅输出JSON对象，字段为maxPrice(number|null), category(string|null), condition(string|null), preferPickup(boolean), keywords(string数组)。分类只能是教材资料、数码产品、骑行装备、宿舍生活、服饰美妆、其他；成色只能是全新、九成新、八成新、七成新及以下或null。商品名比使用场景更能决定分类，例如宿舍用键盘属于数码产品。不要输出Markdown。"),
                            Map.of("role", "user", "content", query)));
            Map<String, Object> response = RestClient.create(baseUrl).post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = String.valueOf(message.get("content")).replace("```json", "").replace("```", "").trim();
            Map<String, Object> parsed = json.readValue(content, new TypeReference<>() {});
            BigDecimal maxPrice = parsed.get("maxPrice") == null ? null : new BigDecimal(String.valueOf(parsed.get("maxPrice")));
            String category = normalizeCategory(text(parsed.get("category")));
            String condition = normalizeCondition(text(parsed.get("condition")));
            boolean pickup = Boolean.TRUE.equals(parsed.get("preferPickup"));
            List<String> keywords = parsed.get("keywords") instanceof List<?> values
                    ? values.stream().map(String::valueOf).filter(value -> !value.isBlank()).limit(8).toList() : List.of();
            return new SearchIntent(maxPrice, category, condition, pickup, keywords);
        } catch (Exception ignored) {
            return null;
        }
    }

    private SearchIntent merge(SearchIntent local, SearchIntent ai) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>(local.keywords());
        keywords.addAll(ai.keywords());
        // Deterministic matches from the user's exact words win over model inferences.
        return new SearchIntent(local.maxPrice() == null ? ai.maxPrice() : local.maxPrice(),
                local.category() == null ? ai.category() : local.category(),
                local.condition() == null ? ai.condition() : local.condition(),
                local.preferPickup() || ai.preferPickup(), List.copyOf(keywords));
    }

    /**
     * Expands a concrete product request into catalogue words that can be matched deterministically.
     * AI still understands the sentence, while the database retrieval layer remains explainable and
     * never treats every item in the broad “其他” category as an equally valid result.
     */
    private SearchIntent expandProductTypeKeywords(String query, SearchIntent intent) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>(intent.keywords());
        PRODUCT_TYPE_SYNONYMS.values().forEach(terms -> {
            boolean mentionedInQuery = terms.stream().anyMatch(query::contains);
            boolean mentionedByAi = intent.keywords().stream().anyMatch(keyword ->
                    terms.stream().anyMatch(term -> keyword.contains(term) || term.contains(keyword)));
            if (mentionedInQuery || mentionedByAi) expanded.addAll(terms);
        });
        return new SearchIntent(intent.maxPrice(), intent.category(), intent.condition(),
                intent.preferPickup(), List.copyOf(expanded));
    }

    private boolean hasSpecificProductIntent(String query) {
        return PRODUCT_WORDS.stream().anyMatch(query::contains)
                || PRODUCT_TYPE_SYNONYMS.values().stream()
                .flatMap(Collection::stream).anyMatch(query::contains);
    }

    private boolean matchesAnyKeyword(Product product, List<String> keywords) {
        String searchable = (Optional.ofNullable(product.getTitle()).orElse("") + " "
                + Optional.ofNullable(product.getDescription()).orElse("")).toLowerCase(Locale.ROOT);
        return keywords.stream().map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(searchable::contains);
    }

    private String normalizeCategory(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (ALLOWED_CATEGORIES.contains(normalized)) return normalized;
        if (normalized.contains("数码") || normalized.contains("电子")) return "数码产品";
        if (normalized.contains("教材") || normalized.contains("图书")) return "教材资料";
        if (normalized.contains("骑行")) return "骑行装备";
        if (normalized.contains("宿舍") || normalized.contains("生活")) return "宿舍生活";
        if (normalized.contains("服饰") || normalized.contains("美妆")) return "服饰美妆";
        return null;
    }

    private String normalizeCondition(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (ALLOWED_CONDITIONS.contains(normalized)) return normalized;
        if (normalized.contains("未拆") || normalized.contains("全新")) return "全新";
        if (normalized.contains("九成") || normalized.contains("95新") || normalized.contains("近新")) return "九成新";
        if (normalized.contains("八成")) return "八成新";
        if (normalized.contains("七成") || normalized.contains("较旧")) return "七成新及以下";
        return null;
    }

    private int relevance(Product product, String query, SearchIntent intent) {
        String title = Optional.ofNullable(product.getTitle()).orElse("").toLowerCase(Locale.ROOT);
        String description = Optional.ofNullable(product.getDescription()).orElse("").toLowerCase(Locale.ROOT);
        int score = intent.category() != null && intent.category().equals(product.getCategory()) ? 3 : 0;
        for (String keyword : intent.keywords()) {
            String value = keyword.toLowerCase(Locale.ROOT);
            if (title.contains(value)) score += 6;
            if (description.contains(value)) score += 2;
        }
        if (intent.condition() != null && intent.condition().equals(product.getCondition())) score += 2;
        if (query.contains(product.getTitle())) score += 10;
        return score;
    }

    private Map<String, Object> productView(Product product, int relevance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", product.getId());
        result.put("sellerId", product.getSellerId());
        result.put("title", product.getTitle());
        result.put("description", product.getDescription());
        result.put("category", product.getCategory());
        result.put("condition", product.getCondition());
        result.put("price", product.getPrice());
        result.put("originalPrice", product.getOriginalPrice());
        result.put("imageUrl", product.getImageUrl());
        result.put("locationName", product.getLocationName());
        result.put("status", product.getStatus());
        result.put("relevance", relevance);
        return result;
    }

    private BigDecimal extractBudget(String query) {
        for (Pattern pattern : List.of(BUDGET_WITH_PREFIX, BUDGET_WITH_UNIT)) {
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                return new BigDecimal(value);
            }
        }
        return null;
    }

    private String intentSummary(SearchIntent intent) {
        List<String> parts = new ArrayList<>();
        if (intent.maxPrice() != null) parts.add("预算不超过 ¥" + intent.maxPrice());
        if (intent.category() != null) parts.add(intent.category());
        if (intent.condition() != null) parts.add(intent.condition());
        if (intent.preferPickup()) parts.add("优先校园面交");
        if (!intent.keywords().isEmpty()) parts.add("关注 " + String.join("、", intent.keywords()));
        return parts.isEmpty() ? "按描述理解商品需求" : String.join(" · ", parts);
    }

    private String text(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) return null;
        return String.valueOf(value).trim();
    }

    private static Map<String, List<String>> categoryWords() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        // Specific product nouns precede contextual words such as “宿舍”, so “宿舍用键盘” remains digital.
        values.put("数码产品", List.of("键盘", "鼠标", "耳机", "电脑", "平板", "充电", "数码", "扩展坞"));
        values.put("教材资料", List.of("教材", "课本", "笔记", "真题", "考研", "四六级", "书"));
        values.put("骑行装备", List.of("自行车", "骑行", "头盔", "车锁", "打气筒", "车灯"));
        values.put("服饰美妆", List.of("衣服", "卫衣", "外套", "鞋", "包", "化妆", "正装"));
        values.put("宿舍生活", List.of("宿舍", "台灯", "收纳", "水壶", "桌", "坐垫", "晾衣架"));
        values.put("其他", List.of("儿童玩具", "玩具", "积木", "拼图", "玩偶", "毛绒", "遥控车", "磁力片",
                "乐器", "尤克里里", "吉他", "羽毛球", "球拍", "计算器", "行李箱", "拉杆箱", "登机箱",
                "雨伞", "晴雨伞", "折叠伞", "雨具"));
        return Collections.unmodifiableMap(values);
    }

    private static Map<String, List<String>> productTypeSynonyms() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("儿童玩具", List.of("儿童玩具", "玩具", "积木", "拼图", "玩偶", "毛绒玩具", "毛绒", "遥控车", "磁力片"));
        values.put("乐器", List.of("乐器", "尤克里里", "吉他", "琴"));
        values.put("球类运动", List.of("羽毛球", "球拍", "运动器材"));
        values.put("学习工具", List.of("科学计算器", "计算器", "学习工具"));
        values.put("旅行箱包", List.of("行李箱", "拉杆箱", "登机箱", "旅行箱"));
        values.put("雨具", List.of("雨具", "雨伞", "晴雨伞", "折叠伞", "遮阳伞"));
        return Collections.unmodifiableMap(values);
    }

    private record SearchIntent(BigDecimal maxPrice, String category, String condition,
                                boolean preferPickup, List<String> keywords) {}
}
