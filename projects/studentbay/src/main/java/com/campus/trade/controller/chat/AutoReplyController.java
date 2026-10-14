package com.campus.trade.controller.chat;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.chat.AutoReplySetting;
import com.campus.trade.entity.product.Product;
import com.campus.trade.repository.chat.AutoReplySettingRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.chat.AutoReplyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AutoReplyController {
    private final AutoReplySettingRepository settings;
    private final ProductRepository products;
    private final CurrentUserService currentUser;
    private final AutoReplyService autoReplyService;

    public AutoReplyController(AutoReplySettingRepository settings, ProductRepository products,
                               CurrentUserService currentUser, AutoReplyService autoReplyService) {
        this.settings = settings;
        this.products = products;
        this.currentUser = currentUser;
        this.autoReplyService = autoReplyService;
    }

    @GetMapping("/auto-reply/global")
    public ApiResponse<Map<String, Object>> global(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        AutoReplySetting setting = settings.findByUserId(userId).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", setting != null && Boolean.TRUE.equals(setting.getEnabled()));
        data.put("content", setting == null || setting.getContent() == null ? "" : setting.getContent());
        // T5.4 一次性规则（默认 true）
        data.put("replyOnce", setting == null || setting.getReplyOnce() == null || Boolean.TRUE.equals(setting.getReplyOnce()));
        // T5.1 面板"已应用到 N 个在卖商品"：后端权威统计在卖且单品已开的商品数
        data.put("appliedCount", autoReplyService.countAppliedOnSale(userId));
        return ApiResponse.success(data);
    }

    @PutMapping("/auto-reply/global")
    public ApiResponse<Map<String, Object>> saveGlobal(@RequestBody GlobalRequest request, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        String content = request.content() == null ? "" : request.content().trim();
        if (content.length() > 500) throw new BusinessException(HttpStatus.BAD_REQUEST, "回复内容最多 500 字");
        if (Boolean.TRUE.equals(request.enabled()) && content.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "开启自动回复需要填写回复内容");
        }
        AutoReplySetting setting = settings.findByUserId(userId).orElse(new AutoReplySetting());
        setting.setUserId(userId);
        setting.setEnabled(Boolean.TRUE.equals(request.enabled()));
        setting.setContent(content);
        setting.setReplyOnce(request.replyOnce() == null || Boolean.TRUE.equals(request.replyOnce()));
        settings.save(setting);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", setting.getEnabled());
        data.put("content", setting.getContent());
        data.put("replyOnce", setting.getReplyOnce());
        // T5.1 保存后同样返回"已应用 N 个在卖商品"，保证面板统计一致
        data.put("appliedCount", autoReplyService.countAppliedOnSale(userId));
        return ApiResponse.success(data);
    }

    @GetMapping("/products/{id}/auto-reply")
    public ApiResponse<Map<String, Object>> productReply(@PathVariable Long id, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        Product product = requireOwnedProduct(id, userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", Boolean.TRUE.equals(product.getAutoReplyEnabled()));
        data.put("text", product.getAutoReplyText() == null ? "" : product.getAutoReplyText());
        return ApiResponse.success(data);
    }

    @PutMapping("/products/{id}/auto-reply")
    public ApiResponse<Map<String, Object>> saveProductReply(@PathVariable Long id,
                                                             @RequestBody ProductReplyRequest request,
                                                             HttpSession session) {
        Long userId = currentUser.require(session).getId();
        Product product = requireOwnedProduct(id, userId);
        String text = request.text() == null ? "" : request.text().trim();
        if (text.length() > 500) throw new BusinessException(HttpStatus.BAD_REQUEST, "回复内容最多 500 字");
        if (Boolean.TRUE.equals(request.enabled()) && text.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "开启单品回复需要填写回复内容，或先关闭单品回复以继承全局");
        }
        product.setAutoReplyEnabled(Boolean.TRUE.equals(request.enabled()));
        product.setAutoReplyText(text.isEmpty() ? null : text);
        products.save(product);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", Boolean.TRUE.equals(product.getAutoReplyEnabled()));
        data.put("text", product.getAutoReplyText() == null ? "" : product.getAutoReplyText());
        return ApiResponse.success(data);
    }

    private Product requireOwnedProduct(Long id, Long userId) {
        Product product = products.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (!product.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只能设置自己发布的商品");
        }
        return product;
    }

    /**
     * T5.2 一键应用到全部在卖商品：把全局默认回复批量写入每个 AVAILABLE 商品。
     * body {overwrite}：false=跳过已单独设置的商品；true=全部覆盖。
     * 返回 {applied, skipped, overwritten}，事务内整体成功/回滚。
     */
    @PostMapping("/auto-reply/apply-all")
    public ApiResponse<Map<String, Object>> applyAll(@RequestBody ApplyAllRequest request, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        Map<String, Object> data = new LinkedHashMap<>(
                autoReplyService.applyAll(userId, Boolean.TRUE.equals(request.overwrite())));
        return ApiResponse.success(data);
    }

    public record GlobalRequest(Boolean enabled, String content, Boolean replyOnce) {}

    public record ProductReplyRequest(Boolean enabled, String text) {}

    public record ApplyAllRequest(Boolean overwrite) {}
}
