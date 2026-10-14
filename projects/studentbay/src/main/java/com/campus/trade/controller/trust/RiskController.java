package com.campus.trade.controller.trust;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.trust.TransactionRiskService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/risk")
public class RiskController {
    private final TransactionRiskService risks;
    private final CurrentUserService currentUser;

    public RiskController(TransactionRiskService risks, CurrentUserService currentUser) {
        this.risks = risks;
        this.currentUser = currentUser;
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<Map<String, Object>> order(@PathVariable Long orderId, HttpSession session) {
        return ApiResponse.success(risks.assess(orderId, currentUser.require(session).getId()));
    }
}
