package com.campus.trade.ai;

import com.campus.trade.service.ai.AiDescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5.8 AI 商品描述优化开启态离线测试。
 *
 * <p>通过 @TestPropertySource 把 AI 开启、base-url 指向本机未监听端口（localhost:1），
 * 验证连接失败降级与 10 秒限流。全程本机回环，不依赖外网与真实模型。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.ai.desc.enabled=true",
        "app.ai.desc.base-url=http://localhost:1/v1",
        "app.ai.desc.timeout-seconds=1"
})
class AiDescriptionServiceEnabledTests {

    @Autowired
    private AiDescriptionService service;

    @Test
    void optimizeDegradesOnConnectFailure() {
        // 独立 userId，避免与限流用例互相影响
        Map<String, Object> result = service.optimize(100L, request());
        assertThat(result.get("optimized")).isNull();
        assertThat(result.get("reason")).isEqualTo("AI 服务不可用，请手动填写");
    }

    @Test
    void rateLimitBlocksSecondCallWithin10Seconds() {
        Map<String, Object> first = service.optimize(200L, request());
        assertThat(first.get("reason")).isEqualTo("AI 服务不可用，请手动填写");

        // 同一用户 10 秒内第二次调用必须被限流
        Map<String, Object> second = service.optimize(200L, request());
        assertThat(second.get("optimized")).isNull();
        assertThat(second.get("reason")).isEqualTo("操作太频繁，请 10 秒后再试");
    }

    private AiDescriptionService.OptimizeRequest request() {
        return new AiDescriptionService.OptimizeRequest(
                "捷安特山地车", "骑行装备", Map.of("size", "26寸"), "九成新",
                new BigDecimal("450"), "毕业出，骑了两年");
    }
}
