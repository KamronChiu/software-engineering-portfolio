# 📅 工作日志

---

## 一、基本信息

| 项目名称 | StudentBay 校园二手交易平台（商品中心） |
|---------|----------------|
| 学生姓名 | 徐旭琳 |
| 汇报周期 | 2026年9月10日（Day 8 · 阶段5C 全量自动化测试与测试文档 T5.9–T5.12 全部完成） |

---

## 二、每日详细记录

### 📌 Day 8（日期：2026年9月10日）

---

**📦 今日提交记录（按实际次数填写）**
> 格式严格遵循 `[Day X] 模块名: 描述`，每行对应一次实际 `git commit`。

- 本轮改动**尚未提交**（按惯例先本地验证、等检查确认后再走 `user2` → `develop` 推送），计划提交信息如下：
- `[Day8] (user2)：全量自动化测试T5.9/T5.10-重新盘点现有测试（16类102用例，覆盖队友Day5/Day6新增与Web页面22例）、新增ProductCenterFullIntegrationTests商品中心全链路11用例（发布校验/草稿流转/上下架/编辑去旧图/图片白名单/砍一刀专属价/智能回复触发一次/擦亮置顶/最近浏览/相似推荐/热搜），同步Day8日志`
- `[Day8] (user2)：全量自动化测试T5.11/T5.12-新增PlatformGapIntegrationTests平台缺口补测12用例（购物车/留言/自然语言搜索/收藏/POI逆查/积分签到游戏/后台管理员鉴权/地理编码/举报处理/后台商品管理/聊天会话未读已读/会话生命周期）、修复OrderControllerIntegrationTests无事务污染共享H2导致全量1例偶发失败的隔离性问题、重写docs/tests/TESTING.md专业测试文档（16类106用例、覆盖矩阵、无外部依赖说明），同步Day8日志`

---

**📝 详细工作内容**

承接 Day 7 阶段 5A（智能回复升级）与 5B（AI 描述优化）全部落地后，今天进入设计文档**「5C 全量自动化测试与测试文档」。**重新梳理已完成与未完成的测试，并补全未完成的所有测试**，目标： `.\mvnw.cmd test` 一条命令就能跑完全部验收。

### 1. T5.9 现有测试盘点

全局扫描 `src/test/java` 后确认：平台现有测试实际为 **14 个测试类 / 84 个用例**（此前文档写的"10 类 59 用例"严重过时——队友新增了 `DemoDataInitializer` 4 例、Day5 订单 12 例、Day6 信任/支付 5 例、积分钱包 7 例、评价 4 例、账号设置 4 例、邮箱域 5 例，且 `WebPageAutomationTests` 实际已扩到 22 个方法）。

| 测试类 | 覆盖模块 | 方法数 |
|---|---|---|
| `StudentBayApplicationTests` | 上下文加载 | 1 |
| `DemoDataInitializerIntegrationTests` | 种子数据幂等 | 4 |
| `OrderControllerIntegrationTests` | 订单生命周期 | 5 |
| `Day5OrderWorkflowIntegrationTests` | 支付/取消/售后/介入 | 12 |
| `ProductStage4ControllerIntegrationTests` | 分类树/分页/教材检索 | 3 |
| `Day6TrustAiPaymentIntegrationTests` | 自然语言搜索/信任/支付 | 5 |
| `BusinessFlowWebAutomationTests` | 端到端主流程 | 5 |
| `WebPageAutomationTests` | 前端页面渲染/交互 | 22 |
| `AiDescriptionServiceTests` / `EnabledTests` | AI 离线降级/限流 | 4 + 2 |
| `SpaceCreditIntegrationTests` | 空间积分钱包 | 7 |
| `ReviewFlowIntegrationTests` | 评价流程 | 4 |
| `AccountSettingsIntegrationTests` | 账号设置 | 4 |
| `AccountEmailDomainIntegrationTests` | 邮箱域校验 | 5 |

### 2. T5.10 商品中心全链路测试

新建 `ProductCenterFullIntegrationTests.java`，用 H2 内存库 + MockMvc + `@Transactional` 隔离，演示账号登录（`lin@campus.edu` 卖家 / `chen@campus.edu` 买家 / `wang@campus.edu`，密码 123456），把 user2 的商品中心从**发布 → 流转 → 编辑 → 砍价 → 智能回复 → 运营 → 浏览**逐条闭环打满：

1. **发布校验**：未登录 401；缺标题/售价/经纬度均 400；正常发布 `AVAILABLE` 且 `viewCount=0`；
2. **草稿流转**：草稿保存 → "我发布的·草稿"栏可见 → 编辑补全转发布 → 删除草稿 → 非草稿不可删；
3. **上下架**：下架后公开列表不可见，重新上架可见，非法状态变更 400；
4. **编辑**：换标题/价格/图片列表，**旧图被移除**；编辑他人商品 403；
5. **图片上传**：未登录 401、png 200、txt 400、超 5MB 400（白名单 + 体积上限）；
6. **砍一刀**：专属价降 1~3 元、重复砍 409、**挂牌原价不变**、买家视角 `cutByMe=true`、未开砍价 400、卖家自砍 403；
7. **智能回复**：全局开启（空内容 400）→ apply-all → 单品覆盖 → 买家发起会话自动回复一次（变量替换、`senderName=卖家`）→ 重复不触发 → 关闭后新商品不触发；
8. **擦亮/置顶**：非本人 403、同日重复 409、时长非法 400、**置顶排序优先**；
9. **最近浏览**：去重、倒序、清空、未登录 401；
10. **相似推荐**：排除自身与下架、≤8 条；
11. **热搜**：搜索计数、TOP10 倒序。

**调试过程中修掉的一批"测试写法"问题**（都是测试自身问题，不是业务 bug，但对未来写测试很关键）：
- 发布 JSON 模板尾部多逗号 → 400（`"addressStreet":"大学路",}` 非法 JSON），改为无 extra 时不拼逗号；
- 草稿只传 title 时 `category`/`description`/`condition` 列为 DB 非空 → 500，需按实体非空约束补齐；
- 发布成功 HTTP 200 但 body 里 `code=201`，断言需对 `$.data.id` 而不是 `$.code=200`；
- 智能回复变量名是 `{商品标题}`（不是 `{title}`），按服务端 `render()` 实际变量修正；
- 卖家不能对自己商品发起会话（接口 400），"关闭后不触发"用例改用第三方买家验证。

### 3. T5.11 平台缺口补测（12 用例）+ 一个隔离性 bug 修复

对照 23 个生产 Controller 与测试类逐项打勾，发现**购物车、商品留言、自然语言搜索、收藏、POI、积分、后台、地理编码、举报、聊天会话**等模块此前完全没有测试。新建 `PlatformGapIntegrationTests` 分两批补满，最终 12 个用例：

1. 购物车：未登录 401、加购去重、count 统计、加自己商品 400、删除；
2. 商品留言：未登录 401、发表、列表作者名、他人删除 403、本人删除；
3. 自然语言搜索：返回 `query/mode/interpretation/products`；
4. 收藏：toggle 收藏/取消、列表；
5. 校园 POI 逆查：种子坐标 2km 内命中（`found=true` + `poi.name`）；
6. 积分：概览 `coinBalance/tasks`、签到领取、重复领取 409、每日骰子游戏；
7. 后台看板：普通用户 403、管理员 200（`totalOrders/totalUsers`）、用户列表；
8. 地理编码：正向/逆向均 200，无外网 Key 自动兜底校园 POI 不报错；
9. 举报处理：举报自己商品 400、举报他人成功（code 201）、管理员举报列表可见、处理（RESOLVED + 下架商品）；
10. 后台商品管理：普通用户 403、管理员商品列表、强制修改商品状态；
11. 聊天会话：买家发起会话并发消息、卖家箱可见、未读数 ≥1、读取后已读回执且未读归零；
12. 会话生命周期：注册 → 会话内访问个人信息 → 登出 → 再访问 401。

**顺手修了一个队友测试的隔离性 bug**：全量跑时 `Day6TrustAiPaymentIntegrationTests` 的自然语言搜索"儿童玩具"用例偶发失败（期望 ≥5 条实际 3 条）。定位后发现 `OrderControllerIntegrationTests` **没有 `@Transactional`**，它下单会把种子商品改成 `RESERVED` 且不回滚，污染了共享的 H2 内存库——自然语言搜索只查 `AVAILABLE`，结果就少了。单独跑每个类都过、一起跑就挂，正是这类问题。修复：给 `OrderControllerIntegrationTests` 补上 `@Transactional`（业界标准做法，语义不变，每个用例独立回滚）。修复后全量 106 例稳定全绿。

### 4. T5.12 测试文档 `docs/tests/TESTING.md` 成稿

把旧文档（10 类 / 59 用例）整体重写为正式测试文档，按专业标准去掉"5C""与老师配合""新增"等表述与任何修改痕迹，以客观、规范的口吻完整说明测试体系，结构：

1. **文档说明**：测试体系定位与适用范围；
2. **测试环境**：JDK/Maven Wrapper/H2 内存库（无需 MySQL）、无需外网与 AI Key，附测试配置；
3. **测试运行**：全量 `.\mvnw.cmd test`、指定测试类、测试报告位置与通过标准；
4. **测试范围与用例清单**：16 类 / 106 用例完整表格，商品中心全链路 11 用例与平台周边模块 12 用例逐条列出验证点；
5. **测试覆盖矩阵**：模块 × 功能 × 测试类 × 用例数，覆盖认证、商品、订单、聊天、智能回复、收藏举报、积分、信任、AI、后台、地图、前端等全部业务域；
6. **测试设计说明**：事务隔离机制、演示账号与登录方式、无外部依赖（MockMvc 不占端口、AI 离线、地理编码降级）；
7. **常见问题**：参数写法、端口、AI 连接错误、上传目录隔离、中文乱码。

---

**⚠️ 遇到的问题与解决方案**

| 问题 | 原因 | 解决方案 |
|---|---|---|
| 全量测试 1 例偶发失败（Day6 玩具搜索） | `OrderControllerIntegrationTests` 无事务，下单改商品状态为 RESERVED 未回滚，污染共享 H2 | 补 `@Transactional`，用例独立回滚；单独跑/合跑均全绿 |
| 聊天未读断言 ClassCastException（Long/Integer） | jsonPath + Hamcrest 比较器对数字类型敏感，`unreadCount` 为 long | 改为解析 JsonNode 后 `asLong()` 断言，绕开类型强转 |
| 新测试发布接口断言失败（`code` 期望 200 实际 201） | 发布成功 HTTP 200 但业务码 `code=201` | 断言改为 `$.data.id` 存在性 |
| 草稿保存 500（category 非空） | `products.category` 等列 DB 非空，草稿校验虽只要求 title，但入库必须提供 | 草稿 body 补齐 category/description/condition |
| 智能回复变量未替换 | 服务端 `render()` 变量为 `{商品标题}` 而非 `{title}` | 按真实变量名调整测试文本与断言 |
| JSON 模板尾逗号导致 400 | `{... "addressStreet":"大学路",}` 非法 JSON | 无 extra 时不拼接尾逗号 |
| 卖家不能对自己商品发起会话 | `ChatController.start` 明确拒绝自购商品 | "关闭后不触发"用例改用第三方买家（wang）验证 |

---

**✅ 今日完成度与明日计划**

- **今日完成**：T5.9（盘点）、T5.10（商品中心全链路 11 例）、T5.11（平台缺口 12 例 + 隔离性修复）、T5.12（TESTING.md 专业文档成稿）；全量 `.\mvnw.cmd test` = **106 用例全部通过（Failures: 0, Errors: 0）**。
- **待用户检查后**：提交 `user2` 分支（提交信息格式 `[Day8] (user2): ...`）→ 合并 `develop` → 推送远程。
- **设计文档 §9 验收清单**：T5.9/T5.10/T5.11/T5.12 已全部勾选 ✅，阶段 5C 收口。
