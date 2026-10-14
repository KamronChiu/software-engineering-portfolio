package com.campus.trade.controller.account;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.dto.order.OrderItemResponse;
import com.campus.trade.dto.order.OrderResponse;
import com.campus.trade.entity.activity.UserActivity;
import com.campus.trade.entity.address.DeliveryAddress;
import com.campus.trade.entity.coupon.Coupon;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.activity.UserActivityRepository;
import com.campus.trade.repository.address.AddressRepository;
import com.campus.trade.repository.coupon.CouponRepository;
import com.campus.trade.repository.product.ProductCommentRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.social.FavoriteRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.auth.PasswordHasher;
import com.campus.trade.service.order.OrderService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AddressRepository addresses;
    private final CouponRepository coupons;
    private final CurrentUserService currentUser;
    private final PasswordHasher passwordHasher;
    private final UserRepository users;
    private final ProductRepository products;
    private final ProductCommentRepository comments;
    private final FavoriteRepository favorites;
    private final OrderService orders;
    private final UserActivityRepository activities;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    public AccountController(AddressRepository addresses, CouponRepository coupons, CurrentUserService currentUser,
                             PasswordHasher passwordHasher, UserRepository users, ProductRepository products, ProductCommentRepository comments,
                             FavoriteRepository favorites, OrderService orders, UserActivityRepository activities) {
        this.addresses = addresses;
        this.coupons = coupons;
        this.currentUser = currentUser;
        this.passwordHasher = passwordHasher;
        this.users = users;
        this.products = products;
        this.comments = comments;
        this.favorites = favorites;
        this.orders = orders;
        this.activities = activities;
    }

    @GetMapping("/addresses")
    public ApiResponse<List<DeliveryAddress>> addresses(HttpSession session) {
        return ApiResponse.success(addresses.findByUserIdOrderByDefaultAddressDescIdDesc(currentUser.require(session).getId()));
    }

    @PostMapping("/addresses")
    @Transactional
    public ApiResponse<DeliveryAddress> addAddress(@Valid @RequestBody AddressRequest request, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        List<DeliveryAddress> existing = addresses.findByUserIdOrderByDefaultAddressDescIdDesc(userId);
        boolean makeDefault = request.defaultAddress() || existing.isEmpty();
        if (makeDefault) clearDefaultAddress(existing);
        DeliveryAddress address = new DeliveryAddress();
        address.setUserId(userId);
        apply(address, request, makeDefault);
        return ApiResponse.success(201, "地址已添加", addresses.save(address));
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public ApiResponse<DeliveryAddress> updateAddress(@PathVariable Long id,
                                                       @Valid @RequestBody AddressRequest request,
                                                       HttpSession session) {
        Long userId = currentUser.require(session).getId();
        DeliveryAddress address = requireOwnedAddress(id, userId);
        boolean makeDefault = request.defaultAddress() || address.isDefaultAddress();
        if (makeDefault) {
            clearDefaultAddress(addresses.findByUserIdOrderByDefaultAddressDescIdDesc(userId));
        }
        apply(address, request, makeDefault);
        return ApiResponse.success(200, "地址已更新", addresses.save(address));
    }

    @DeleteMapping("/addresses/{id}")
    @Transactional
    public ApiResponse<Void> deleteAddress(@PathVariable Long id, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        DeliveryAddress address = requireOwnedAddress(id, userId);
        boolean deletedDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        if (deletedDefault) {
            List<DeliveryAddress> remaining = addresses.findByUserIdOrderByDefaultAddressDescIdDesc(userId);
            if (!remaining.isEmpty()) {
                remaining.get(0).setDefaultAddress(true);
                addresses.save(remaining.get(0));
            }
        }
        return ApiResponse.success(200, "地址已删除", null);
    }

    @GetMapping("/coupons")
    public ApiResponse<List<Coupon>> coupons(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        List<Coupon> available = Stream.concat(
                        coupons.findByEnabledTrueAndOwnerUserIdIsNullOrderByMinimumAmountAsc().stream(),
                        coupons.findByEnabledTrueAndOwnerUserIdAndUsedFalseOrderByCreatedAtDesc(userId).stream())
                .filter(coupon -> coupon.getExpiresAt() == null || coupon.getExpiresAt().isAfter(LocalDateTime.now()))
                .toList();
        return ApiResponse.success(available);
    }

    @PutMapping("/profile")
    @Transactional
    public ApiResponse<Map<String, Object>> updateProfile(@Valid @RequestBody ProfileRequest request, HttpSession session) {
        AppUser user = currentUser.require(session);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!isEduEmail(email) && !isLocalEmail(email)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 @xxx.edu 学生邮箱或 @xxx.local 管理员邮箱");
        }
        users.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new BusinessException(HttpStatus.CONFLICT, "该邮箱已被其他账号使用");
                });
        user.setDisplayName(request.displayName().trim());
        user.setEmail(email);
        user.setUniversity(request.university() == null ? null : request.university().trim());
        return ApiResponse.success(200, "资料已更新", userView(users.save(user)));
    }

    @PostMapping("/avatar")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file, HttpSession session) {
        AppUser user = currentUser.require(session);
        if (file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "头像图片不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!Set.of("jpg", "jpeg", "png", "webp", "gif").contains(ext)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 jpg、png、webp、gif 格式的图片");
        }
        try {
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            String fileName = "avatar-" + user.getId() + "-" + System.currentTimeMillis() + "." + ext;
            file.transferTo(dir.resolve(fileName));
            deleteAvatarFile(user.getAvatarUrl());
            user.setAvatarUrl("/uploads/" + fileName);
            users.save(user);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("avatarUrl", user.getAvatarUrl());
            return ApiResponse.success(200, "头像已更新", result);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败，请重试");
        }
    }

    @GetMapping("/bought")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> bought(HttpSession session) {
        Long buyerId = currentUser.require(session).getId();
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (OrderResponse order : orders.getBuyerOrders(buyerId)) {
            for (OrderItemResponse item : order.items()) {
                Map<String, Object> entry = result.computeIfAbsent(item.productId(), productId ->
                        products.findById(productId).map(product -> {
                            Map<String, Object> view = productView(product);
                            view.put("quantity", 0);
                            return view;
                        }).orElse(null));
                if (entry == null) continue;
                entry.put("quantity", (int) entry.get("quantity") + item.quantity());
                entry.put("boughtAt", order.createdAt());
                entry.put("orderNo", order.orderNo());
                entry.put("orderStatus", order.status());
            }
        }
        return ApiResponse.success(new ArrayList<>(result.values()));
    }

    public record ProfileRequest(@NotBlank @Size(max = 60) String displayName,
                                 @NotBlank @Email @Size(max = 120) String email,
                                 @Size(max = 80) String university) {}

    @PutMapping("/password")
    @Transactional
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpSession session) {
        AppUser user = currentUser.require(session);
        if (!passwordHasher.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        users.save(user);
        return ApiResponse.success(200, "密码已修改", null);
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 6, max = 64) String newPassword) {}

    @GetMapping("/activities")
    public ApiResponse<List<Map<String, Object>>> activities(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        return ApiResponse.success(activities.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(activity -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", activity.getId());
                    result.put("type", activity.getType());
                    result.put("productId", activity.getProductId());
                    result.put("title", activity.getTitle());
                    result.put("createdAt", activity.getCreatedAt());
                    return result;
                })
                .toList());
    }

    private Map<String, Object> userView(AppUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("displayName", user.getDisplayName());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("university", user.getUniversity());
        result.put("studentVerified", user.isStudentVerified());
        result.put("role", user.getRole());
        return result;
    }

    private Map<String, Object> productView(Product product) {
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
        result.put("locationName", product.getLocationName());
        result.put("latitude", product.getLatitude());
        result.put("longitude", product.getLongitude());
        result.put("status", product.getStatus());
        result.put("viewCount", product.getViewCount());
        result.put("favoriteCount", favorites.countByProductId(product.getId()));
        result.put("commentCount", comments.countByProductId(product.getId()));
        result.put("createdAt", product.getCreatedAt());
        return result;
    }

    private void deleteAvatarFile(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("/uploads/")) return;
        try {
            Files.deleteIfExists(Path.of(uploadDir).resolve(avatarUrl.substring("/uploads/".length())));
        } catch (IOException ignored) {}
    }

    public record AddressRequest(@NotBlank @Size(max = 60) String recipientName,
                                 @NotBlank @Size(max = 30) String phone,
                                 @NotBlank @Size(max = 500) String detail,
                                 boolean defaultAddress,
                                 Double latitude,
                                 Double longitude) {}

    private DeliveryAddress requireOwnedAddress(Long id, Long userId) {
        DeliveryAddress address = addresses.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "地址不存在"));
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该地址");
        }
        return address;
    }

    private void clearDefaultAddress(List<DeliveryAddress> userAddresses) {
        userAddresses.stream().filter(DeliveryAddress::isDefaultAddress).forEach(item -> {
            item.setDefaultAddress(false);
            addresses.save(item);
        });
    }

    private void apply(DeliveryAddress address, AddressRequest request, boolean defaultAddress) {
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setDetail(request.detail().trim());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        address.setDefaultAddress(defaultAddress);
    }

    private static boolean isEduEmail(String email) {
        return email != null && email.matches("(?i)^[^@\\s]+@[^@\\s]+\\.edu$");
    }

    private static boolean isLocalEmail(String email) {
        return email != null && email.matches("(?i)^[^@\\s]+@[^@\\s]+\\.local$");
    }
}
