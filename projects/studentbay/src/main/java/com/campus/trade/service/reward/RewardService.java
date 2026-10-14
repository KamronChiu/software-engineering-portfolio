package com.campus.trade.service.reward;

import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.reward.CoinLedger;
import com.campus.trade.entity.reward.DailyGameProgress;
import com.campus.trade.entity.reward.TaskClaim;
import com.campus.trade.entity.reward.UserWallet;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductCommentRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.reward.CoinLedgerRepository;
import com.campus.trade.repository.reward.DailyGameProgressRepository;
import com.campus.trade.repository.reward.TaskClaimRepository;
import com.campus.trade.repository.reward.UserWalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RewardService {
    public static final int COINS_PER_YUAN = 10;
    private static final int DAILY_DICE_ROLLS = 3;
    private static final int BOARD_SIZE = 20;
    private static final LocalDate ONE_TIME_DATE = LocalDate.of(1970, 1, 1);
    private static final List<TaskDefinition> TASKS = List.of(
            new TaskDefinition("DAILY_CHECK_IN", "校园签到", "进入任务中心并完成今日签到", 1, 3, true, true, "签到"),
            new TaskDefinition("DAILY_CART", "发现三件好物", "今天将 3 件不同的在售闲置加入购物车", 3, 8, true, true, "购物"),
            new TaskDefinition("DAILY_COMMENT", "认真做功课", "今天在商品详情发布 2 条有效公开留言", 2, 10, true, true, "留言"),
            new TaskDefinition("DAILY_CHAT", "主动咨询", "今天按商品发送 3 条咨询消息", 3, 12, true, true, "咨询"),
            new TaskDefinition("DAILY_PUBLISH", "让闲置流动", "今天发布 1 件真实校园闲置", 1, 15, true, false, "发布"),
            new TaskDefinition("BUY_THREE", "循环采购达人", "累计提交 3 笔校园闲置订单", 3, 50, false, false, "订单"),
            new TaskDefinition("COMPLETE_THREE", "可靠交易伙伴", "累计完成 3 笔交易并确认收货", 3, 80, false, false, "循环"),
            new TaskDefinition("SELL_FIVE", "校园绿色卖家", "累计发布 5 件真实闲置商品", 5, 100, false, false, "卖家"));

    private final UserWalletRepository wallets;
    private final CoinLedgerRepository ledger;
    private final TaskClaimRepository claims;
    private final DailyGameProgressRepository games;
    private final CartRepository cart;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final ProductCommentRepository comments;
    private final ChatMessageRepository messages;

    public RewardService(UserWalletRepository wallets, CoinLedgerRepository ledger,
                         TaskClaimRepository claims, DailyGameProgressRepository games,
                         CartRepository cart, OrderRepository orders, ProductRepository products,
                         ProductCommentRepository comments, ChatMessageRepository messages) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.claims = claims;
        this.games = games;
        this.cart = cart;
        this.orders = orders;
        this.products = products;
        this.comments = comments;
        this.messages = messages;
    }

    @Transactional
    public Map<String, Object> summary(Long userId) {
        UserWallet wallet = getOrCreateWallet(userId);
        List<Map<String, Object>> taskViews = TASKS.stream().map(task -> taskView(userId, task)).toList();
        int requiredTotal = (int) TASKS.stream().filter(task -> task.daily() && task.required()).count();
        int requiredCompleted = countRequiredCompleted(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coinBalance", wallet.getCoinBalance());
        result.put("coinsPerYuan", COINS_PER_YUAN);
        result.put("tasks", taskViews);
        result.put("dailyRequiredTotal", requiredTotal);
        result.put("dailyRequiredCompleted", requiredCompleted);
        result.put("dailyResetAt", LocalDate.now().plusDays(1).atStartOfDay());
        result.put("game", gameView(getOrCreateGame(userId)));
        result.put("ledger", ledger.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream().map(this::ledgerView).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> claimTask(Long userId, String code) {
        TaskDefinition task = TASKS.stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "任务不存在"));
        LocalDate claimDate = task.daily() ? LocalDate.now() : ONE_TIME_DATE;
        if (claims.existsByUserIdAndTaskCodeAndClaimDate(userId, task.code(), claimDate)) {
            throw new BusinessException(HttpStatus.CONFLICT, "该任务奖励已经领取");
        }
        if (progress(userId, task) < task.target()) {
            throw new BusinessException(HttpStatus.CONFLICT, "任务尚未完成");
        }
        recordClaim(userId, task.code(), claimDate, task.rewardCoins());
        int balance = credit(userId, task.rewardCoins(), "TASK_REWARD", task.name());
        return Map.of("rewardCoins", task.rewardCoins(), "coinBalance", balance, "message", "任务奖励已到账");
    }

    @Transactional
    public Map<String, Object> playDailyGame(Long userId) {
        LocalDate today = LocalDate.now();
        DailyGameProgress game = games.findForUpdate(userId, today).orElseGet(() -> createGame(userId, today));
        if (game.getRollsUsed() >= DAILY_DICE_ROLLS) {
            throw new BusinessException(HttpStatus.CONFLICT, "今天的骰子已经用完，明天再来");
        }

        int dice = ThreadLocalRandom.current().nextInt(1, 7);
        int from = game.getPosition();
        int destination = (from + dice) % BOARD_SIZE;
        CellOutcome outcome = cellOutcome(destination, countRequiredCompleted(userId) == requiredTaskCount());

        game.setPosition(destination);
        game.setRollsUsed(game.getRollsUsed() + 1);
        game.setCoinsEarned(game.getCoinsEarned() + outcome.rewardCoins());
        games.save(game);
        int balance = outcome.rewardCoins() > 0
                ? credit(userId, outcome.rewardCoins(), "DICE_GAME", "骰子寻宝 · " + outcome.cellName())
                : balance(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dice", dice);
        result.put("fromPosition", from);
        result.put("position", destination);
        result.put("cellType", outcome.cellType());
        result.put("cellName", outcome.cellName());
        result.put("rewardCoins", outcome.rewardCoins());
        result.put("message", outcome.message());
        result.put("remainingRolls", DAILY_DICE_ROLLS - game.getRollsUsed());
        result.put("coinBalance", balance);
        return result;
    }

    @Transactional
    public CoinUse spendUpTo(Long userId, int maximumCoins, String description) {
        if (maximumCoins <= 0) return new CoinUse(0, balance(userId));
        UserWallet wallet = wallets.findForUpdate(userId).orElseGet(() -> getOrCreateWallet(userId));
        int used = Math.min(wallet.getCoinBalance(), maximumCoins);
        if (used <= 0) return new CoinUse(0, wallet.getCoinBalance());
        wallet.setCoinBalance(wallet.getCoinBalance() - used);
        wallets.save(wallet);
        addLedger(userId, -used, "ORDER_DEDUCTION", description);
        return new CoinUse(used, wallet.getCoinBalance());
    }

    @Transactional
    public int refundOrderCoins(Long userId, int coins, String orderNo) {
        if (coins <= 0) return balance(userId);
        return credit(userId, coins, "ORDER_CANCELLATION_REFUND", "取消订单返还金币 " + orderNo);
    }

    @Transactional
    public int balance(Long userId) {
        return getOrCreateWallet(userId).getCoinBalance();
    }

    private int progress(Long userId, TaskDefinition task) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long value = switch (task.code()) {
            case "DAILY_CHECK_IN" -> 1;
            case "DAILY_CART" -> cart.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, start, end);
            case "DAILY_COMMENT" -> comments.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, start, end);
            case "DAILY_CHAT" -> messages.countBySenderIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, start, end);
            case "DAILY_PUBLISH" -> products.countBySellerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, start, end);
            case "BUY_THREE" -> orders.countByBuyerId(userId);
            case "COMPLETE_THREE" -> orders.countByBuyerIdAndStatus(userId, OrderStatus.COMPLETED);
            case "SELL_FIVE" -> products.countBySellerId(userId);
            default -> 0;
        };
        return (int) Math.min(value, task.target());
    }

    private int countRequiredCompleted(Long userId) {
        return (int) TASKS.stream()
                .filter(task -> task.daily() && task.required())
                .filter(task -> progress(userId, task) >= task.target())
                .count();
    }

    private int requiredTaskCount() {
        return (int) TASKS.stream().filter(task -> task.daily() && task.required()).count();
    }

    private Map<String, Object> taskView(Long userId, TaskDefinition task) {
        int progress = progress(userId, task);
        LocalDate claimDate = task.daily() ? LocalDate.now() : ONE_TIME_DATE;
        boolean claimed = claims.existsByUserIdAndTaskCodeAndClaimDate(userId, task.code(), claimDate);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", task.code());
        result.put("name", task.name());
        result.put("description", task.description());
        result.put("icon", task.icon());
        result.put("target", task.target());
        result.put("progress", progress);
        result.put("rewardCoins", task.rewardCoins());
        result.put("daily", task.daily());
        result.put("required", task.required());
        result.put("claimed", claimed);
        result.put("claimable", !claimed && progress >= task.target());
        return result;
    }

    private DailyGameProgress getOrCreateGame(Long userId) {
        LocalDate today = LocalDate.now();
        return games.findByUserIdAndGameDate(userId, today).orElseGet(() -> createGame(userId, today));
    }

    private DailyGameProgress createGame(Long userId, LocalDate date) {
        DailyGameProgress game = new DailyGameProgress();
        game.setUserId(userId);
        game.setGameDate(date);
        game.setPosition(0);
        return games.save(game);
    }

    private Map<String, Object> gameView(DailyGameProgress game) {
        return Map.of(
                "position", game.getPosition(),
                "rollsUsed", game.getRollsUsed(),
                "remainingRolls", DAILY_DICE_ROLLS - game.getRollsUsed(),
                "coinsEarned", game.getCoinsEarned(),
                "boardSize", BOARD_SIZE
        );
    }

    private CellOutcome cellOutcome(int position, boolean requiredTasksCompleted) {
        if (Set.of(2, 6, 10, 14, 18).contains(position)) {
            int reward = 4 + position / 2;
            return new CellOutcome("COIN", "金币格", reward, "落到金币格，奖励已进入账户");
        }
        if (Set.of(4, 12).contains(position)) {
            int[] rewards = {3, 5, 8, 12, 15};
            int reward = rewards[ThreadLocalRandom.current().nextInt(rewards.length)];
            return new CellOutcome("MYSTERY", "神秘格", reward, "打开校园神秘箱，获得随机金币");
        }
        if (Set.of(8, 16).contains(position)) {
            if (requiredTasksCompleted) {
                return new CellOutcome("TASK", "任务大奖格", 20, "今日必做任务已完成，获得大奖励");
            }
            return new CellOutcome("TASK_LOCKED", "任务格", 0, "完成全部今日必做任务后，这个格子才会发放 20 金币");
        }
        return new CellOutcome("CAMPUS", position == 0 ? "校园起点" : "校园漫步格", 2, "绿色出行奖励 2 金币");
    }

    private UserWallet getOrCreateWallet(Long userId) {
        return wallets.findById(userId).orElseGet(() -> {
            UserWallet wallet = new UserWallet();
            wallet.setUserId(userId);
            wallet.setCoinBalance(0);
            return wallets.save(wallet);
        });
    }

    private int credit(Long userId, int amount, String type, String description) {
        UserWallet wallet = wallets.findForUpdate(userId).orElseGet(() -> getOrCreateWallet(userId));
        wallet.setCoinBalance(wallet.getCoinBalance() + amount);
        wallets.save(wallet);
        addLedger(userId, amount, type, description);
        return wallet.getCoinBalance();
    }

    private void addLedger(Long userId, int amount, String type, String description) {
        CoinLedger entry = new CoinLedger();
        entry.setUserId(userId);
        entry.setAmount(amount);
        entry.setType(type);
        entry.setDescription(description);
        ledger.save(entry);
    }

    private void recordClaim(Long userId, String taskCode, LocalDate date, int reward) {
        TaskClaim claim = new TaskClaim();
        claim.setUserId(userId);
        claim.setTaskCode(taskCode);
        claim.setClaimDate(date);
        claim.setRewardCoins(reward);
        claims.save(claim);
    }

    private Map<String, Object> ledgerView(CoinLedger entry) {
        return Map.of("id", entry.getId(), "amount", entry.getAmount(), "type", entry.getType(),
                "description", entry.getDescription(), "createdAt", entry.getCreatedAt());
    }

    private record TaskDefinition(String code, String name, String description, int target,
                                  int rewardCoins, boolean daily, boolean required, String icon) {}
    private record CellOutcome(String cellType, String cellName, int rewardCoins, String message) {}
    public record CoinUse(int usedCoins, int remainingBalance) {}
}
