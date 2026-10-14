package com.campus.trade.controller.geo;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.entity.config.AppConfig;
import com.campus.trade.entity.location.CampusPoi;
import com.campus.trade.repository.config.AppConfigRepository;
import com.campus.trade.repository.location.CampusPoiRepository;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地理编码控制器（支撑发布页「选择国家/地区/街道 + 单一定位」）：
 * GET /api/regions            级联选择所需的静态区域数据（省 → 市 → 区/县 → 街道/园区）
 * GET /api/geocode?address=   正向地理编码：地址 → 坐标（高德 Web 服务 → 校园 POI 兜底）
 * GET /api/geocode/reverse?lat=&lng=  逆地理编码：坐标 → 地址（高德 → 校园 POI 兜底）
 *
 * 高德 Web 服务需要申请 key（免费），配置到 app_config 的 map.geoKey 后，任意国内地址均可搜索；
 * 未配置 key 时自动降级为校园 POI 匹配，校外地址可在地图上手动选点。
 */
@RestController
@RequestMapping("/api")
public class GeoController {
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final AppConfigRepository configs;
    private final CampusPoiRepository pois;
    private final RestClient http;
    private final String regionsJson;

    public GeoController(AppConfigRepository configs, CampusPoiRepository pois) {
        this.configs = configs;
        this.pois = pois;
        java.net.http.HttpClient jdkClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(TIMEOUT);
        this.http = RestClient.builder().requestFactory(factory).build();
        this.regionsJson = loadRegions();
    }

    @GetMapping(value = "/regions", produces = "application/json;charset=UTF-8")
    public org.springframework.http.ResponseEntity<String> regions() {
        return org.springframework.http.ResponseEntity.ok(regionsJson);
    }

    @GetMapping("/geocode")
    public ApiResponse<Map<String, Object>> geocode(@RequestParam String address) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (blank(address)) {
            result.put("found", false);
            return ApiResponse.success(result);
        }
        String key = config("map.geoKey");
        if (!blank(key)) {
            Map<?, ?> amap = amapGet(
                    "https://restapi.amap.com/v3/geocode/geo?address={addr}&key={key}",
                    address.trim(), key);
            Object geocodes = amap == null ? null : amap.get("geocodes");
            if (geocodes instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object location = first.get("location");
                if (location != null) {
                    String[] parts = location.toString().split(",");
                    if (parts.length == 2) {
                        result.put("found", true);
                        result.put("latitude", Double.parseDouble(parts[1].trim()));
                        result.put("longitude", Double.parseDouble(parts[0].trim()));
                        result.put("formatted", first.get("formatted_address"));
                        result.put("source", "amap");
                        return ApiResponse.success(result);
                    }
                }
            }
        }
        CampusPoi poi = campusMatch(address);
        if (poi != null) {
            result.put("found", true);
            result.put("latitude", poi.getLatitude());
            result.put("longitude", poi.getLongitude());
            result.put("formatted", poi.getAddress());
            result.put("poiId", poi.getId());
            result.put("source", "campus");
            return ApiResponse.success(result);
        }
        result.put("found", false);
        return ApiResponse.success(result);
    }

    @GetMapping("/geocode/reverse")
    public ApiResponse<Map<String, Object>> reverse(@RequestParam BigDecimal lat, @RequestParam BigDecimal lng) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = config("map.geoKey");
        if (!blank(key)) {
            Map<?, ?> amap = amapGet(
                    "https://restapi.amap.com/v3/geocode/regeo?location={loc}&key={key}",
                    lng.toPlainString() + "," + lat.toPlainString(), key);
            Object regeo = amap == null ? null : amap.get("regeocode");
            if (regeo instanceof Map<?, ?> map && map.get("formatted_address") != null) {
                result.put("found", true);
                result.put("formatted", map.get("formatted_address").toString());
                result.put("source", "amap");
                return ApiResponse.success(result);
            }
        }
        CampusPoi nearest = nearest(lat, lng);
        if (nearest != null) {
            result.put("found", true);
            result.put("formatted", nearest.getAddress());
            result.put("poiId", nearest.getId());
            result.put("name", nearest.getName());
            result.put("source", "campus");
            return ApiResponse.success(result);
        }
        result.put("found", false);
        return ApiResponse.success(result);
    }

    private Map<?, ?> amapGet(String urlTemplate, String param, String key) {
        try {
            String url = urlTemplate.replace("{key}", key)
                    .replace("{addr}", param)
                    .replace("{loc}", param);
            return http.get().uri(URI.create(url)).retrieve().body(Map.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private CampusPoi campusMatch(String address) {
        if (blank(address)) return null;
        String normalized = address.replace(" ", "");
        for (CampusPoi poi : pois.findByEnabledTrueOrderBySortAsc()) {
            String poiName = poi.getName().replace(" ", "");
            if (poiName.isEmpty()) continue;
            if (normalized.contains(poiName)) {
                return poi;
            }
        }
        return null;
    }

    private CampusPoi nearest(BigDecimal lat, BigDecimal lng) {
        CampusPoi best = null;
        double bestDistance = 2000D;
        for (CampusPoi poi : pois.findByEnabledTrueOrderBySortAsc()) {
            double distance = haversine(lat.doubleValue(), lng.doubleValue(),
                    poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = poi;
            }
        }
        return best;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double radius = 6371000D;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String config(String key) {
        return configs.findById(key).map(AppConfig::getValue).orElse("");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 全国 + 全球行政区划（各级含中心坐标），加载自静态资源 static/data/regions.json，
     * 供发布页级联选择与地图「跳到大致区域」。纯 JDK 读取原始 JSON 字符串，避免引入额外 JSON 解析依赖。 */
    private String loadRegions() {
        try {
            org.springframework.core.io.Resource res = new org.springframework.core.io.ClassPathResource("static/data/regions.json");
            try (java.io.InputStream in = res.getInputStream()) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
