package com.campus.trade.controller.auth;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.entity.user.UserRole;
import com.campus.trade.entity.user.UserStatus;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.auth.PasswordHasher;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final CurrentUserService currentUser;

    public AuthController(UserRepository users, PasswordHasher passwordHasher, CurrentUserService currentUser) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        String loginEmail = request.email().trim();
        if (!isEduEmail(loginEmail) && !isLocalEmail(loginEmail)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 @xxx.edu 学生邮箱或 @xxx.local 管理员邮箱");
        }
        AppUser user = users.findByEmailIgnoreCase(loginEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确"));
        if (user.getStatus() != UserStatus.ACTIVE || !passwordHasher.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
        }
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        return ApiResponse.success(view(user));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        boolean adminRegister = "ADMIN".equalsIgnoreCase(request.role());
        String rawEmail = request.email().trim();
        if (adminRegister ? !isLocalEmail(rawEmail) : !isEduEmail(rawEmail)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    adminRegister ? "管理员邮箱需为 @xxx.local 后缀" : "仅支持 @xxx.edu 后缀的学生邮箱");
        }
        String email = rawEmail.toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email)
                .ifPresent(existing -> { throw new BusinessException(HttpStatus.CONFLICT, "该邮箱已被注册，请直接登录"); });
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordHasher.hash(request.password()));
        user.setRole(request.role() == null ? UserRole.USER : UserRole.valueOf(request.role()));
        user.setStudentVerified(false);
        users.save(user);
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        return ApiResponse.success(view(user));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpSession session) {
        return ApiResponse.success(view(currentUser.require(session)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.success(null);
    }

    private Map<String, Object> view(AppUser user) {
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

    private static boolean isEduEmail(String email) {
        return email != null && email.matches("(?i)^[^@\\s]+@[^@\\s]+\\.edu$");
    }

    private static boolean isLocalEmail(String email) {
        return email != null && email.matches("(?i)^[^@\\s]+@[^@\\s]+\\.local$");
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 6, max = 64) String password,
                                  @NotBlank @Size(min = 2, max = 30) String displayName,
                                  @Pattern(regexp = "USER|ADMIN") String role) {}
}
