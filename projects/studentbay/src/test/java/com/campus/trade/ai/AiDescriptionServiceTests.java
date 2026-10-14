package com.campus.trade.ai;

import com.campus.trade.service.ai.AiDescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T5.8 AI 商品描述优化离线测试（默认配置：app.ai.desc.enabled=false）。
 *
 * <p>不依赖真实模型与外网：用 H2 内存库加载上下文，只验证默认关闭状态、
 * 未启用降级返回与 Prompt 组装逻辑。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiDescriptionServiceTests {

    @Autowired
    private MockMvc web;

    @Autowired
    private AiDescriptionService service;

    @Test
    void statusDefaultsToDisabled() throws Exception {
        web.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.provider").value("ollama"))
                .andExpect(jsonPath("$.data.model").value("qwen2.5:3b"));
    }

    @Test
    void optimizeDegradesWhenDisabled() {
        AiDescriptionService.OptimizeRequest request = new AiDescriptionService.OptimizeRequest(
                "捷安特山地车", "骑行装备", Map.of("size", "26寸"), "九成新",
                new BigDecimal("450"), "毕业出，骑了两年");
        Map<String, Object> result = service.optimize(1L, request);
        assertThat(result.get("optimized")).isNull();
        assertThat(result.get("reason")).isEqualTo("AI 服务未配置，请手动填写");
    }

    @Test
    void userPromptContainsAllFormFields() throws Exception {
        AiDescriptionService.OptimizeRequest request = new AiDescriptionService.OptimizeRequest(
                "捷安特山地车", "骑行装备", Map.of("size", "26寸", "brand", "捷安特"), "九成新",
                new BigDecimal("450"), "毕业出，骑了两年");
        Method method = AiDescriptionService.class.getDeclaredMethod(
                "buildUserPrompt", AiDescriptionService.OptimizeRequest.class);
        method.setAccessible(true);
        String prompt = (String) method.invoke(service, request);
        assertThat(prompt)
                .contains("捷安特山地车")
                .contains("骑行装备")
                .contains("size=26寸")
                .contains("brand=捷安特")
                .contains("九成新")
                .contains("450")
                .contains("毕业出，骑了两年");
    }

    @Test
    void userPromptSkipsBlankFields() throws Exception {
        AiDescriptionService.OptimizeRequest request = new AiDescriptionService.OptimizeRequest(
                "教材", null, Map.of(), null, null, null);
        Method method = AiDescriptionService.class.getDeclaredMethod(
                "buildUserPrompt", AiDescriptionService.OptimizeRequest.class);
        method.setAccessible(true);
        String prompt = (String) method.invoke(service, request);
        assertThat(prompt).contains("标题：教材").contains("分类：未填写").contains("成色：未填写");
        assertThat(prompt).doesNotContain("参数：").doesNotContain("价格：").doesNotContain("原描述：");
    }
}
