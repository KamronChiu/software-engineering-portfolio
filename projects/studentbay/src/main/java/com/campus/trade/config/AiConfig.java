package com.campus.trade.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * T5.5 AI 商品描述优化配置（Ollama 本地主方案 / 智谱 GLM-4-Flash 备选）。
 *
 * <p>全部走 OpenAI 兼容协议（{base}/chat/completions），只切换 base-url / model / api-key 即可换 Provider：
 * <ul>
 *   <li>ollama：本地模型，完全免费离线可用，无需 key。base-url=http://localhost:11434/v1，model=qwen2.5:3b</li>
 *   <li>glm：智谱 GLM-4-Flash，官方免费额度，需在 open.bigmodel.cn 申请 key。base-url=https://open.bigmodel.cn/api/paas/v4，model=glm-4-flash</li>
 * </ul>
 * 默认跟随"AI 搜索"同一套配置（AI_SEARCH_ENABLED/AI_BASE_URL/AI_API_KEY/AI_MODEL），
 * 配过一次 key 后发布页 AI 描述优化开箱即用；也可用 AI_DESC_* 单独覆盖。
 * Provider 按 base-url 自动推断（open.bigmodel.cn → glm；localhost/127.0.0.1 → ollama；其他 → custom），
 * 显式配置 AI_DESC_PROVIDER 时以显式值为准。默认关闭，不影响构建与既有功能。
 */
@Component
public class AiConfig {

    private final boolean enabled;
    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public AiConfig(
            @Value("${app.ai.desc.enabled:false}") boolean enabled,
            @Value("${app.ai.desc.provider:auto}") String provider,
            @Value("${app.ai.desc.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${app.ai.desc.api-key:}") String apiKey,
            @Value("${app.ai.desc.model:qwen2.5:3b}") String model,
            @Value("${app.ai.desc.timeout-seconds:3}") int timeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "qwen2.5:3b" : model.trim();
        this.timeoutSeconds = timeoutSeconds <= 0 ? 3 : timeoutSeconds;
        this.provider = resolveProvider(provider);
    }

    /** Provider 未显式配置（auto）时，按 base-url 自动推断，避免 status 显示与真实通道不一致。 */
    private String resolveProvider(String configured) {
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured.trim())) {
            return configured.trim().toLowerCase();
        }
        String url = baseUrl;
        if (url.contains("open.bigmodel.cn")) {
            return "glm";
        }
        if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0")) {
            return "ollama";
        }
        return "custom";
    }

    /** 是否本地（Ollama 等免 key）服务：本地地址无需 api-key，在线服务必须携带 key。 */
    public boolean isLocalBaseUrl() {
        return baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1") || baseUrl.contains("0.0.0.0");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
