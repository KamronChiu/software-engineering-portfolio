package com.campus.trade.controller.product;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.entity.location.CampusPoi;
import com.campus.trade.repository.location.CampusPoiRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 校园 POI 接口：支撑发布页"交易地点名称 × 地图"联动。
 * GET /api/pois?keyword=   地点联想/列表
 * GET /api/pois/nearest?lat=&lng=   逆查最近 POI（点地图回填地点名称）
 */
@RestController
@RequestMapping("/api/pois")
public class PoiController {
    private final CampusPoiRepository pois;

    public PoiController(CampusPoiRepository pois) {
        this.pois = pois;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        List<CampusPoi> items = pois.findByEnabledTrueOrderBySortAsc();
        if (!blank(keyword)) {
            String k = keyword.trim().toLowerCase(Locale.ROOT);
            String[] tokens = k.split("\\s+");
            items = items.stream().filter(poi -> {
                String hay = (poi.getName() + " " + (poi.getAddress() == null ? "" : poi.getAddress()))
                        .toLowerCase(Locale.ROOT);
                for (String token : tokens) {
                    if (!hay.contains(token)) {
                        return false;
                    }
                }
                return true;
            }).toList();
        }
        return ApiResponse.success(items.stream().map(this::view).toList());
    }

    @GetMapping("/nearest")
    public ApiResponse<Map<String, Object>> nearest(@RequestParam BigDecimal lat, @RequestParam BigDecimal lng) {
        List<CampusPoi> items = pois.findByEnabledTrueOrderBySortAsc();
        CampusPoi best = null;
        double bestDistance = 2000D; // 2km 内才算匹配，超出视为自定义位置
        for (CampusPoi poi : items) {
            double distance = haversine(lat.doubleValue(), lng.doubleValue(),
                    poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = poi;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", best != null);
        result.put("distanceMeters", best != null ? Math.round(bestDistance) : null);
        result.put("poi", best != null ? view(best) : null);
        return ApiResponse.success(result);
    }

    private Map<String, Object> view(CampusPoi poi) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", poi.getId());
        result.put("name", poi.getName());
        result.put("address", poi.getAddress());
        result.put("category", poi.getCategory());
        result.put("latitude", poi.getLatitude());
        result.put("longitude", poi.getLongitude());
        return result;
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
