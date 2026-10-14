package com.campus.trade.controller.reward;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.reward.RewardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rewards")
public class RewardController {
    private final RewardService rewards;
    private final CurrentUserService currentUser;

    public RewardController(RewardService rewards, CurrentUserService currentUser) {
        this.rewards = rewards;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(HttpSession session) {
        return ApiResponse.success(rewards.summary(currentUser.require(session).getId()));
    }

    @PostMapping("/tasks/{code}/claim")
    public ApiResponse<Map<String, Object>> claim(@PathVariable String code, HttpSession session) {
        return ApiResponse.success(rewards.claimTask(currentUser.require(session).getId(), code));
    }

    @PostMapping("/game/play")
    public ApiResponse<Map<String, Object>> play(HttpSession session) {
        return ApiResponse.success(rewards.playDailyGame(currentUser.require(session).getId()));
    }
}
