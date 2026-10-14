package com.campus.trade.controller.account;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.trust.SpaceCreditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 我的空间信用接口：GET /api/space/credit
 * 返回买家信用 / 卖家信用百分比及构成指标（卖出数、买进数、好评数、商品被举报数）。
 */
@RestController
@RequestMapping("/api/space")
public class SpaceCreditController {

    private final SpaceCreditService spaceCreditService;
    private final CurrentUserService currentUserService;

    public SpaceCreditController(SpaceCreditService spaceCreditService, CurrentUserService currentUserService) {
        this.spaceCreditService = spaceCreditService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/credit")
    public ApiResponse<Map<String, Object>> credit(HttpSession session) {
        Long userId = currentUserService.require(session).getId();
        return ApiResponse.success(spaceCreditService.credit(userId));
    }
}
