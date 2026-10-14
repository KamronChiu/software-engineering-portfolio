package com.campus.trade.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebPageAutomationTests {

    @Autowired
    private MockMvc web;

    @ParameterizedTest(name = "页面 {0} 应可访问并加载前端脚本")
    @ValueSource(strings = {
            "/index.html", "/login.html", "/product-detail.html", "/price-compare.html", "/publish.html",
            "/cart.html", "/checkout.html", "/payment.html", "/payment-success.html", "/orders.html",
            "/seller-orders.html", "/order-detail.html", "/logistics.html", "/messages.html",
            "/favorites.html", "/rewards.html", "/admin.html", "/profile.html", "/settings.html", "/bought.html"
    })
    void userFacingPageShouldLoad(String path) throws Exception {
        web.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js")));
    }

    @Test
    void loginPageShouldExposeCompleteLoginAndRegistrationControls() throws Exception {
        web.perform(get("/login.html?mode=register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("id=\"loginForm\""),
                        org.hamcrest.Matchers.containsString("id=\"email\""),
                        org.hamcrest.Matchers.containsString("id=\"password\""),
                        org.hamcrest.Matchers.containsString("id=\"toRegisterBtn\""),
                        org.hamcrest.Matchers.containsString("id=\"registerForm\""),
                        org.hamcrest.Matchers.containsString("id=\"regName\""),
                        org.hamcrest.Matchers.containsString("id=\"regEmail\""),
                        org.hamcrest.Matchers.containsString("id=\"regPassword\""),
                        org.hamcrest.Matchers.containsString("id=\"regConfirm\""),
                        org.hamcrest.Matchers.containsString("id=\"toLoginBtn\"")
                )));
    }

    @Test
    void transactionPagesShouldKeepCriticalInteractiveTargets() throws Exception {
        web.perform(get("/checkout.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"submitOrder\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"checkoutPriceInsights\"")));
        web.perform(get("/payment.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"payNow\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"paymentFeedback\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"paymentRiskAlerts\"")));
        web.perform(get("/order-detail.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"orderDetailContent\"")));
        web.perform(get("/rewards.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"playGame\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"dailyTaskList\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"dice-cube\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"treasureReveal\"")));
        web.perform(get("/index.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"naturalSearchForm\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"naturalQuery\"")));
        web.perform(get("/js/app.js"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("loadMoreBtn.hidden = true"),
                        org.hamcrest.Matchers.containsString("loadMoreBtn.disabled = true"),
                        org.hamcrest.Matchers.containsString("const favoriteButton = document.getElementById('favoriteButton')"),
                        org.hamcrest.Matchers.containsString("favoriteButton.textContent = result.active"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("event.currentTarget.textContent = result.active"))
                )));
        web.perform(get("/admin.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"adminContent\"")));
    }
}
