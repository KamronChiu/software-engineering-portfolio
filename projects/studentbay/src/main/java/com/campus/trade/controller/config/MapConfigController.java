package com.campus.trade.controller.config;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.entity.config.AppConfig;
import com.campus.trade.repository.config.AppConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/map/config")
public class MapConfigController {
    private final AppConfigRepository configs;

    public MapConfigController(AppConfigRepository configs) {
        this.configs = configs;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> get() {
        Map<String, String> result = new LinkedHashMap<>();
        configs.findAll().forEach(config -> result.put(config.getKey().replace("map.", ""), config.getValue()));
        return ApiResponse.success(result);
    }
}
