package com.campus.trade.controller.payment;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.payment.AlipaySandboxService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@Controller
@RequestMapping("/api/payments/alipay")
public class PaymentController {
    private final AlipaySandboxService alipay;
    private final CurrentUserService currentUser;

    public PaymentController(AlipaySandboxService alipay, CurrentUserService currentUser) {
        this.alipay = alipay;
        this.currentUser = currentUser;
    }

    @GetMapping("/status")
    @ResponseBody
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(Map.of("enabled", alipay.configured(), "mode", "SANDBOX"));
    }

    @GetMapping(value = "/start/{orderId}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> start(@PathVariable Long orderId, HttpSession session) {
        Long buyerId = currentUser.require(session).getId();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(alipay.createPagePayment(orderId, buyerId));
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public String notify(@RequestParam Map<String, String> parameters) {
        return alipay.verifyNotificationAndConfirm(parameters) ? "success" : "failure";
    }

    @GetMapping("/return")
    public RedirectView returned(@RequestParam Map<String, String> parameters) {
        boolean success = alipay.verifyReturnAndConfirm(parameters);
        String orderNo = parameters.getOrDefault("out_trade_no", "");
        return new RedirectView("/orders.html?alipay=" + (success ? "success" : "verify-failed")
                + "&trade=" + orderNo);
    }
}
