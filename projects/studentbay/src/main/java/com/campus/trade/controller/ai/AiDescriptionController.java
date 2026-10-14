package com.campus.trade.controller.ai;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.config.AiConfig;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.service.ai.AiDescriptionService;
import com.campus.trade.service.auth.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T5.5/T5.6 AI 商品描述优化接口。
 *
 * <p>GET /api/ai/status            AI 能力状态（前端按钮置灰用，公开）
 * <br>POST /api/ai/optimize-description  润色商品描述（需登录，10 秒限 1 次）
 */
@RestController
@RequestMapping("/api/ai")
public class AiDescriptionController {

    private final AiConfig config;
    private final AiDescriptionService service;
    private final CurrentUserService currentUser;

    public AiDescriptionController(AiConfig config, AiDescriptionService service, CurrentUserService currentUser) {
        this.config = config;
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", config.isEnabled());
        data.put("provider", config.getProvider());
        data.put("model", config.getModel());
        return ApiResponse.success(data);
    }

    @PostMapping("/optimize-description")
    public ApiResponse<Map<String, Object>> optimize(@RequestBody AiDescriptionService.OptimizeRequest request, HttpSession session) {
        AppUser user = currentUser.require(session);
        validate(request);
        return ApiResponse.success(service.optimize(user.getId(), request));
    }

    private void validate(AiDescriptionService.OptimizeRequest request) {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数不能为空");
        }
        if (request.title() == null || request.title().isBlank() || request.title().trim().length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请填写商品标题（不超过 100 字）");
        }
        if (request.description() != null && request.description().length() > 5000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "原描述过长");
        }
        if (request.params() != null && request.params().size() > 30) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "参数过多");
        }
        if (request.params() != null) {
            for (Map.Entry<String, String> entry : request.params().entrySet()) {
                if (entry.getKey() == null || entry.getKey().length() > 20 || (entry.getValue() != null && entry.getValue().length() > 100)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "参数格式不正确");
                }
            }
        }
    }
}
