package com.campus.trade.service.auth;

import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.entity.user.UserRole;
import com.campus.trade.entity.user.UserStatus;
import com.campus.trade.repository.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    public static final String SESSION_USER_ID = "CURRENT_USER_ID";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser require(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER_ID);
        if (!(value instanceof Long userId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态已失效"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被停用");
        }
        return user;
    }

    public AppUser requireAdmin(HttpSession session) {
        AppUser user = require(session);
        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return user;
    }
}
