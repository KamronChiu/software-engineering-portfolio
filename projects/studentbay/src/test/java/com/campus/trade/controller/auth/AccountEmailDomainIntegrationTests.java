package com.campus.trade.controller.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖 [Day7] 提交（9a99ae9）的邮箱域策略：
 * 登录 / 注册 / 改资料仅允许 @xxx.edu 学生邮箱或 @xxx.local 管理员邮箱，
 * 注册按所选角色联动校验；同时覆盖 [Day5] 设置中心的修改密码流程。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountEmailDomainIntegrationTests {

    @Autowired
    private MockMvc web;

    private static String unique() {
        return "auth-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MockHttpSession registerSession(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        web.perform(post("/api/auth/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456","displayName":"域测试","role":"USER"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase(Locale.ROOT)));
        return session;
    }

    @Test
    void registerShouldBindUserRoleToEduDomainAndAdminRoleToLocalDomain() throws Exception {
        String tag = unique();

        // 学生角色：@gmail.com 与 @xxx.local 均拒绝
        web.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@gmail.com","password":"123456","displayName":"域测试","role":"USER"}
                                """.formatted(tag)))
                .andExpect(status().isBadRequest());
        web.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@campus.local","password":"123456","displayName":"域测试","role":"USER"}
                                """.formatted(tag)))
                .andExpect(status().isBadRequest());

        // 管理员角色：@xxx.edu 拒绝，@xxx.local 放行
        web.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@campus.edu","password":"123456","displayName":"管理员测试","role":"ADMIN"}
                                """.formatted(tag)))
                .andExpect(status().isBadRequest());
        web.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@studentbay.local","password":"123456","displayName":"域管理员","role":"ADMIN"}
                                """.formatted(tag)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // 域校验先于查重：占用邮箱配非法域应报 400 而非 409
        web.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"lin@163.com","password":"123456","displayName":"重复","role":"USER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matchingDomainRegisterShouldSucceedStoreEmailLowercasedAndLoginIgnoreCase() throws Exception {
        String mixed = "MiXeD" + unique() + "@CaMpUs.EDU";
        MockHttpSession session = new MockHttpSession();
        web.perform(post("/api/auth/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456","displayName":"大小写","role":"USER"}
                                """.formatted(mixed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.email").value(mixed.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.data.role").value("USER"));

        // 全大写登录同样命中（域名规则与邮箱匹配均忽略大小写）
        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456"}
                                """.formatted(mixed.toUpperCase(Locale.ROOT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(mixed.toLowerCase(Locale.ROOT)));
    }

    @Test
    void loginShouldRejectForeignDomainBeforeCheckingCredentials() throws Exception {
        // 非法域优先拦截 → 400；合法域但账号不存在 → 401，两条路径互不混淆
        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@gmail.com","password":"whatever"}
                                """))
                .andExpect(status().isBadRequest());

        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ghost-%s@campus.edu","password":"123456"}
                                """.formatted(unique())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdateShouldEnforceSameDomainPolicyAndRejectEmailTakenByOthers() throws Exception {
        MockHttpSession session = registerSession(unique() + "@campus.edu");

        web.perform(put("/api/account/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"域测试","email":"outsider@qq.com","university":"测试大学"}
                                """))
                .andExpect(status().isBadRequest());

        // 换成他人已占用的合法校园邮箱 → 409
        web.perform(put("/api/account/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"域测试","email":"lin@campus.edu","university":"测试大学"}
                                """))
                .andExpect(status().isConflict());

        // 换成另一个合法的校园邮箱 → 200
        String newEmail = unique() + "@campus.edu";
        web.perform(put("/api/account/profile").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"域测试","email":"%s","university":"广东理工学院"}
                                """.formatted(newEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail));
    }

    @Test
    void changePasswordShouldRejectWrongOldPasswordAndInvalidateOldCredentials() throws Exception {
        String email = unique() + "@campus.edu";
        MockHttpSession session = registerSession(email);

        web.perform(put("/api/account/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"wrong-old","newPassword":"newpass123"}
                                """))
                .andExpect(status().isBadRequest());

        web.perform(put("/api/account/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"123456","newPassword":"newpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"123456"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
        web.perform(post("/api/auth/login").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"newpass123"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }
}
