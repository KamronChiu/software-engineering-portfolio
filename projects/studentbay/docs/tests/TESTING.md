# StudentBay 自动化测试文档

---

## 1. 文档说明

本文档描述 StudentBay 校园二手交易平台的自动化测试体系，包括测试环境、运行方式、测试范围、用例清单、覆盖矩阵与设计约定。平台所有后端接口与前端页面均通过集成测试与页面自动化测试进行验证，测试可在本机一键运行，不依赖外部服务。

---

## 2. 测试环境

| 项 | 说明 |
|---|---|
| JDK | 17 及以上（本项目基于 Java 17 开发，已在 JDK 21 验证） |
| Maven | 项目内置 Maven Wrapper（`mvnw.cmd` / `mvnw`），无需单独安装 |
| 数据库 | 测试使用 H2 内存数据库（`src/test/resources/application.properties`），不依赖 MySQL |
| 网络 | 测试全程无需外网；AI 相关用例为离线降级验证，地理编码用例在无密钥时自动降级 |

测试配置（`src/test/resources/application.properties`）：

```properties
spring.datasource.url=jdbc:h2:mem:studentbay_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
spring.jpa.hibernate.ddl-auto=create-drop
app.alipay.enabled=false
app.ai.enabled=false
app.upload-dir=./target/test-uploads
```

---

## 3. 测试运行

### 3.1 全量运行

```bash
# Windows
.\mvnw.cmd test

# macOS / Linux
./mvnw test
```

全量运行覆盖 16 个测试类、106 个用例。执行结束输出 `BUILD SUCCESS` 且 `Tests run: 106, Failures: 0, Errors: 0` 即为全部通过。

### 3.2 指定测试类

```bash
.\mvnw.cmd test -Dtest=ProductCenterFullIntegrationTests
.\mvnw.cmd test -Dtest=ProductCenterFullIntegrationTests,PlatformGapIntegrationTests
```

多个测试类使用逗号分隔；必要时附加 `-DfailIfNoTests=false` 避免无匹配类时报错。

### 3.3 测试报告

运行结果输出至 `target/surefire-reports/`，每个测试类生成一份 `.txt` 报告，包含用例数、耗时与失败堆栈。

---

## 4. 测试范围与用例清单

测试覆盖平台全部业务模块，共 16 个测试类、106 个用例：

| # | 测试类 | 用例数 | 验证内容 |
|---|---|---|---|
| 1 | `StudentBayApplicationTests` | 1 | Spring 应用上下文可正常加载 |
| 2 | `DemoDataInitializerIntegrationTests` | 4 | 种子数据：商品分类、儿童玩具、主图唯一性、初始化幂等 |
| 3 | `OrderControllerIntegrationTests` | 5 | 订单接口：创建、查询、发货、收货、状态流转 |
| 4 | `Day5OrderWorkflowIntegrationTests` | 12 | 订单全流程：支付幂等、超时取消、改价、退款、售后、介入、双端同步、管理后台接口 |
| 5 | `ProductStage4ControllerIntegrationTests` | 3 | 分类树结构、商品列表分页、ISBN / 课程号检索 |
| 6 | `Day6TrustAiPaymentIntegrationTests` | 5 | 信任评分、风险提示、支付宝配置、自然语言搜索同义词展开 |
| 7 | `BusinessFlowWebAutomationTests` | 5 | 业务端到端：注册登录、发布、搜索、详情、下单、收货 |
| 8 | `WebPageAutomationTests` | 22 | 前端页面自动化：页面渲染、交互、跳转、表单提交 |
| 9 | `AiDescriptionServiceTests` | 4 | AI 描述服务：默认关闭态、降级返回、Prompt 组装（全字段 / 空字段） |
| 10 | `AiDescriptionServiceEnabledTests` | 2 | AI 开启态：连接失败降级不抛错、10 秒限流 |
| 11 | `SpaceCreditIntegrationTests` | 7 | 空间积分：钱包、支付抵扣、流水、门槛 |
| 12 | `ReviewFlowIntegrationTests` | 4 | 评价流程：买家评卖家、卖家回评、评价分页 |
| 13 | `AccountSettingsIntegrationTests` | 4 | 账号设置：资料修改、头像上传、密码修改 |
| 14 | `AccountEmailDomainIntegrationTests` | 5 | 邮箱域校验：校园域注册限制、管理员账号例外 |
| 15 | `ProductCenterFullIntegrationTests` | 11 | 商品中心全链路（见 4.1） |
| 16 | `PlatformGapIntegrationTests` | 12 | 平台周边模块补测（见 4.2） |

### 4.1 商品中心全链路（`ProductCenterFullIntegrationTests`）

覆盖商品中心的完整业务闭环：

| 用例 | 验证内容 |
|---|---|
| 发布校验 | 未登录返回 401；缺标题、售价、经纬度均返回 400；正常发布后状态为 `AVAILABLE` 且浏览量初始为 0 |
| 草稿流转 | 草稿保存后可在"草稿"列表查询；编辑补全信息后转发布；草稿可删除；已发布商品不可删除 |
| 上下架 | 下架后状态为 `OFF_SHELF` 且不出现在公开列表；重新上架后恢复可见；非法状态变更返回 400 |
| 商品编辑 | 修改标题、价格、图片列表；旧图从图片列表移除；编辑他人商品返回 403 |
| 图片上传 | 未登录返回 401；png 上传成功且返回 `/uploads/` 路径；非白名单格式返回 400；超过 5MB 返回 400 |
| 砍一刀 | 开启砍价区间后买家可砍价，专属价降 1~3 元；同一买家重复砍价返回 409；挂牌原价不变；买家视角 `cutByMe=true`；未开启砍价商品不可砍；卖家不能自砍 |
| 智能回复 | 全局回复开启（空内容返回 400）；一键应用；单品覆盖；买家发起会话触发一次自动回复（含变量替换）；重复触发不产生第二条；关闭后不触发 |
| 擦亮 / 置顶 | 非本人操作返回 403；同日重复擦亮返回 409；置顶时长非法返回 400；置顶商品在搜索结果中优先 |
| 最近浏览 | 浏览记录去重、按时间倒序、可清空；未登录返回 401 |
| 相似推荐 | 推荐结果排除商品自身与已下架商品；结果不超过 8 条 |
| 热搜 | 搜索关键词计数累加；返回 TOP10 并按次数倒序 |

### 4.2 平台周边模块补测（`PlatformGapIntegrationTests`）

覆盖商品中心之外各业务模块的接口与流程：

| 用例 | 验证内容 |
|---|---|
| 购物车 | 未登录返回 401；加购去重；购物车数量统计；添加自己发布的商品返回 400；删除购物车条目 |
| 商品留言 | 未登录返回 401；发表留言；列表返回作者信息；他人删除返回 403；本人可删除 |
| 自然语言搜索 | 提交自然语言查询，返回解析结果（query / mode / interpretation）与商品列表 |
| 收藏 | 未登录返回 401；收藏 / 取消收藏切换；收藏列表 |
| 校园 POI 逆查 | 传入坐标返回最近校园地点（命中时含地点名称） |
| 积分任务 | 积分概览（余额 / 任务列表）；签到领取奖励；同日重复领取返回 409；每日骰子游戏 |
| 管理后台 | 普通用户访问返回 403；管理员可查看看板统计（订单数 / 用户数）与用户列表 |
| 地理编码 | 正向（地址 → 坐标）与逆向（坐标 → 地址）均返回 200，无密钥时自动降级 |
| 举报处理 | 举报自己商品返回 400；举报他人商品成功；管理员可见举报列表；处理举报可标记解决并下架商品 |
| 后台商品管理 | 普通用户返回 403；管理员可查看商品列表并修改商品状态 |
| 聊天会话 | 买家发起会话并发消息；卖家会话列表可见且未读数 ≥1；读取消息后已读回执、未读数归零 |
| 会话生命周期 | 注册新用户 → 会话内可访问个人信息 → 登出后会话失效返回 401 |

---

## 5. 测试覆盖矩阵

| 模块 | 功能 | 测试类 | 用例数 |
|---|---|---|---|
| 认证 | 注册、登录、登出、会话失效、邮箱域校验 | `AccountEmailDomainIntegrationTests`、`PlatformGapIntegrationTests`、`BusinessFlowWebAutomationTests` | 12 |
| 商品中心 | 发布、草稿、上下架、编辑、图片上传 | `ProductCenterFullIntegrationTests` | 5 |
| 商品运营 | 擦亮、置顶、最近浏览、相似推荐、热搜 | `ProductCenterFullIntegrationTests`、`ProductStage4ControllerIntegrationTests` | 8 |
| 砍一刀 | 专属价、限次、原价保护 | `ProductCenterFullIntegrationTests` | 1 |
| 搜索 | 关键词、分类、分页、自然语言 | `ProductStage4ControllerIntegrationTests`、`Day6TrustAiPaymentIntegrationTests`、`PlatformGapIntegrationTests` | 7 |
| 购物车 | 加购、去重、删除、计数 | `PlatformGapIntegrationTests` | 1 |
| 订单 | 创建、支付、发货、收货、退款、售后 | `OrderControllerIntegrationTests`、`Day5OrderWorkflowIntegrationTests` | 17 |
| 聊天 | 会话、消息、未读、已读、智能回复 | `ProductCenterFullIntegrationTests`、`PlatformGapIntegrationTests` | 3 |
| 智能回复 | 全局、单品、批量应用、触发规则 | `ProductCenterFullIntegrationTests` | 1 |
| 收藏 / 举报 | 收藏切换、举报提交、后台处理 | `PlatformGapIntegrationTests` | 2 |
| 留言 | 发表、列表、删除权限 | `PlatformGapIntegrationTests` | 1 |
| 评价 | 买卖双方互评 | `ReviewFlowIntegrationTests` | 4 |
| 积分 / 奖励 | 钱包、任务、签到、游戏 | `SpaceCreditIntegrationTests`、`PlatformGapIntegrationTests` | 9 |
| 信任 / 风控 | 信任分、风险提示 | `Day6TrustAiPaymentIntegrationTests` | 3 |
| 支付 | 支付宝开关、沙箱配置 | `Day6TrustAiPaymentIntegrationTests` | 1 |
| AI 描述 | 离线降级、限流、Prompt 组装 | `AiDescriptionServiceTests`、`AiDescriptionServiceEnabledTests` | 6 |
| 管理后台 | 看板、用户、商品、举报、报表 | `PlatformGapIntegrationTests` | 3 |
| 地图 / 地理 | POI 逆查、正逆地理编码 | `PlatformGapIntegrationTests` | 2 |
| 账号设置 | 资料、头像、密码 | `AccountSettingsIntegrationTests` | 4 |
| 前端页面 | 页面渲染、交互、端到端流程 | `WebPageAutomationTests`、`BusinessFlowWebAutomationTests` | 27 |

---

## 6. 测试设计说明

### 6.1 隔离机制

- 业务测试类统一使用 `@Transactional`：每个用例运行于独立事务，结束后回滚，测试之间互不影响；
- 共享一个 H2 内存库实例，`ddl-auto=create-drop` 在应用上下文启动时重建表结构；
- 上传类用例将文件写入 `target/test-uploads`，不污染工作区。

### 6.2 演示账号

种子数据（`DemoDataInitializer`）提供以下账号，密码均为 `123456`：

| 账号 | 姓名 | 角色 | 用途 |
|---|---|---|---|
| `lin@campus.edu` | 林同学 | 普通用户 | 买家视角用例 |
| `chen@campus.edu` | 陈同学 | 普通用户 | 卖家视角用例 |
| `wang@campus.edu` | 王同学 | 普通用户 | 第三方用户用例 |
| `admin@studentbay.local` | 平台管理员 | 管理员 | 后台接口用例 |

登录方式：`MockHttpSession` + `POST /api/auth/login`（JSON：`email`、`password`）。

### 6.3 无外部依赖

- 测试不启动真实端口，通过 `MockMvc` 模拟 HTTP 请求；
- AI 相关用例指向本机无效端口验证降级与限流，不出网；
- 地理编码在无高德密钥时自动降级到校园 POI 兜底，仍返回 200。

---

## 7. 常见问题

| 现象 | 原因与处理 |
|---|---|
| `No tests matching pattern ... were executed` | 多个测试类参数需用逗号分隔，并加 `-DfailIfNoTests=false` |
| 端口占用报错 | 测试使用 `MockMvc` 模拟请求，不占用端口；服务运行请参考项目 README 的端口配置 |
| AI 用例打印连接错误 | 属于降级用例的预期行为；如需过滤可单独运行指定测试类 |
| 测试产生 `target/test-uploads` 目录 | 上传用例的隔离目录，删除 `target/` 即可清理 |
| 控制台中文乱码 | 终端编码问题，不影响断言结果；IDE 中可将控制台编码设为 UTF-8 |
