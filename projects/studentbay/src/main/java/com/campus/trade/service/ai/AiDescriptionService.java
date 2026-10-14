package com.campus.trade.service.ai;

import com.campus.trade.config.AiConfig;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * T5.6 AI 商品描述优化服务。
 *
 * <p>不依赖 Spring AI starter（Boot 4.1.1 与官方 spring-ai 版本尚未稳定适配），按设计文档降级方案
 * 自研约 60 行的 OpenAI 兼容 HttpClient：POST {base}/chat/completions，messages 直接拼 Prompt，
 * Ollama 与智谱 GLM 均可对接。对外接口与 Prompt 逻辑与 Spring AI 方案完全一致。
 *
 * <p>铁律：任何异常（未启用 / 未配 key / 超时 / 解析失败）都只返回 {@code {optimized:null, reason}}，
 * 绝不把错误抛给发布流程，绝不阻塞发布。
 */
@Service
public class AiDescriptionService {

    private static final String SYSTEM_PROMPT =
            "你是校园二手交易平台的商品描述润色助手。基于用户填写的信息润色为自然、口语化、真实可信的描述；"
                    + "不得虚构参数；突出成色与卖点；不超过200字；不得添加营销夸张词。只输出润色后的描述文本，不要输出其他内容。";

    private static final int MAX_LEN = 200;
    private static final long RATE_LIMIT_MS = 10_000L; // 每个登录用户 10 秒限 1 次

    private final AiConfig config;
    private final RestClient client;
    /** 内存令牌桶：userId -> 上次成功调用的毫秒时间戳 */
    private final ConcurrentHashMap<Long, Long> lastCallAt = new ConcurrentHashMap<>();

    public AiDescriptionService(AiConfig config) {
        this.config = config;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));
        this.client = RestClient.builder().baseUrl(config.getBaseUrl()).requestFactory(factory).build();
    }

    /**
     * 优化商品描述。永不抛异常，失败一律返回降级结构。
     */
    public Map<String, Object> optimize(Long userId, OptimizeRequest request) {
        if (!config.isEnabled()) {
            return degraded("AI 服务未配置，请手动填写");
        }
        boolean needsKey = !config.isLocalBaseUrl(); // 本地服务（Ollama）免 key；在线服务必须携带 key
        if (needsKey && config.getApiKey().isBlank()) {
            return degraded("AI 服务未配置，请手动填写");
        }
        long now = System.currentTimeMillis();
        Long last = lastCallAt.get(userId);
        if (last != null && now - last < RATE_LIMIT_MS) {
            return degraded("操作太频繁，请 10 秒后再试");
        }
        // 调用前先占用令牌：无论成功失败，同一用户 10 秒内只放行一次，防连点轰炸
        lastCallAt.put(userId, now);
        try {
            String text = callChatCompletions(buildUserPrompt(request));
            if (text == null || text.isBlank()) {
                return degraded("AI 服务不可用，请手动填写");
            }
            if (text.length() > MAX_LEN) {
                text = text.substring(0, MAX_LEN);
            }
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("optimized", text);
            ok.put("reason", null);
            return ok;
        } catch (Exception ignored) {
            return degraded("AI 服务不可用，请手动填写");
        }
    }

    /** 组装 User Prompt：标题 / 分类 / 参数(键值) / 成色 / 价格 / 原描述 */
    private String buildUserPrompt(OptimizeRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(nvl(request.title())).append('\n');
        sb.append("分类：").append(nvl(request.category())).append('\n');
        if (request.params() != null && !request.params().isEmpty()) {
            String params = request.params().entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                    .map(e -> e.getKey() + "=" + e.getValue().trim())
                    .collect(Collectors.joining("，"));
            if (!params.isEmpty()) {
                sb.append("参数：").append(params).append('\n');
            }
        }
        sb.append("成色：").append(nvl(request.condition())).append('\n');
        if (request.price() != null) {
            sb.append("价格：").append(request.price().toPlainString()).append("元\n");
        }
        if (request.description() != null && !request.description().isBlank()) {
            sb.append("原描述：").append(request.description().trim()).append('\n');
        }
        return sb.toString();
    }

    /** 调用 OpenAI 兼容接口并取回文本。Ollama 无鉴权时不带 Authorization 头。 */
    private String callChatCompletions(String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("temperature", 0.6);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)));

        RestClient.RequestBodySpec spec = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        if (!config.getApiKey().isBlank()) {
            spec.header("Authorization", "Bearer " + config.getApiKey());
        }
        Map<?, ?> response = spec.retrieve().body(Map.class);
        if (response == null) {
            return null;
        }
        List<?> choices = response.get("choices") instanceof List<?> list ? list : null;
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        Object message = first instanceof Map<?, ?> m ? m.get("message") : null;
        Object content = message instanceof Map<?, ?> mm ? mm.get("content") : null;
        return content == null ? null : String.valueOf(content).replace("```", "").trim();
    }

    private Map<String, Object> degraded(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("optimized", null);
        result.put("reason", reason);
        return result;
    }

    private String nvl(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }

    /** 商品表单字段（与发布接口字段一致，服务端重新校验长度） */
    public record OptimizeRequest(
            String title,
            String category,
            Map<String, String> params,
            String condition,
            BigDecimal price,
            String description) {
    }
}
