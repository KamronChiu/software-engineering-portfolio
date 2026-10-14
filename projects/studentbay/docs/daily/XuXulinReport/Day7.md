# 📅 工作日志

---

## 一、基本信息

| 项目名称 | StudentBay 校园二手交易平台（商品中心） |
|---------|----------------|
| 学生姓名 | 徐旭琳 |
| 汇报周期 | 2026年9月8日（Day 7 · 阶段5A 智能回复 T5.1–T5.4 + 阶段5B AI 描述优化 T5.5–T5.8 全部完成） |

---

## 二、每日详细记录

### 📌 Day 7（日期：2026年9月8日）

---

**📦 今日提交记录（按实际次数填写）**
> 格式严格遵循 `[Day X] 模块名: 描述`，每行对应一次实际 `git commit`。

- 本轮改动**尚未提交**（按惯例先本地验证、等检查确认后再走 `user2` → `develop` 推送），计划提交信息如下：
- `[Day7] (user2)：阶段5A智能回复升级T5.1/T5.2-「我发布的」页内常驻智能回复面板（Toggle开关+模板+变量+实时预览）、一键应用到全部在卖商品（覆盖可选）、卡片自动回复徽标、商品列表/全局接口返回自动回复状态与已应用统计，同步Day7日志`
- `[Day7] (user2)：阶段5A智能回复升级T5.3/T5.4-单品弹窗开关升级为Toggle+继承全局提示与灰标（已开/继承全局三态徽标）、聊天自动回复浅色气泡与买家预填提示条、replyOnce一次性回复规则开关（默认true，false时买家追问可再回一条），会话接口返回自动回复可用状态，同步Day7日志`
- `[Day7] (user2)：阶段5B AI描述优化T5.5/T5.6-自研OpenAI兼容客户端（Ollama本地主方案/智谱GLM-4-Flash备选，Boot4.1.1与spring-ai暂不兼容按设计文档降级）、AiConfig配置类与app.ai.desc.*开关（默认关闭）、AiDescriptionService润色服务（System/User Prompt组装、200字截断、3秒超时、降级不阻塞、10秒限1次内存令牌桶）、/api/ai/status与/api/ai/optimize-description接口、start-ollama.bat启动脚本、README AI章节，同步Day7日志`
- `[Day7] (user2)：阶段5B AI描述优化T5.7/T5.8-发布页描述框右上角"AI优化描述"按钮（低调文字按钮贴合表单风格、未开启置灰、空标题引导、润色中禁用、成功填入可继续手动修改并支持恢复原文、失败保留原文）、AiDescriptionServiceTests/AiDescriptionServiceEnabledTests离线测试（H2+本机无效端口验证降级与限流）、README AI测试小节，同步Day7日志`

---

**📝 详细工作内容**

承接 Day 6 阶段 4 与首页搜索栏重构完成后，进入设计文档**新增「阶段 5 · 体验升级与收尾」**的 5A 智能回复体验升级：T5.1 全局智能回复一键开关（页内常驻面板 + Toggle 开关）、T5.2 一键应用到全部在卖商品。核心目标是把智能回复从"藏在弹窗里的勾选框"改成"常驻页面的开关面板"，并补上之前缺失的"一键批量设置所有商品"能力。

### 1. 后端：一键应用接口与状态统计（`AutoReplyService` / `AutoReplyController` / `ProductController`）

- **`AutoReplyService.applyAll(userId, overwrite)`**（新增，`@Transactional`）：
  - 先取全局设置，未开启或内容为空直接抛 400"请先开启所有宝贝默认回复并填写内容"；
  - 遍历该卖家全部在卖（`AVAILABLE`）商品，按"是否已单独设置过（单品开启且有内容）"分流：
    - `overwrite=false`（默认）：已单独设置过的 `skipped++` 跳过，未设置的 `applied++` 写入全局内容；
    - `overwrite=true`：已设置的 `overwritten++` 覆盖为全局内容，未设置的照常写入；
  - 写入动作即 `autoReplyEnabled=true + autoReplyText=全局内容`，全程在事务内，异常整体回滚，不会出现"写一半"。
- **`POST /api/auto-reply/apply-all`**（新增接口）：body `{overwrite}`，返回 `{applied, skipped, overwritten}`。
- **`GET /api/auto-reply/global` 与 `PUT /api/auto-reply/global`**：返回字段补上 `appliedCount`（该卖家在卖商品中已开启单品自动回复的数量），前端面板"已应用到 N 个在卖商品"由后端权威统计，保存后立即刷新，不再依赖前端自行推算。
- **`ProductController.view()`**：补输出 `autoReplyEnabled / autoReplyText`，让"我发布的"列表能渲染每个商品的自动回复状态徽标。

### 2. 前端：页内常驻面板 + Toggle 开关（`my-products.html` / `app.js` / `app.css`）

- **删除顶部「⚙ 智能回复设置」小按钮和全局弹窗**，改为三栏 Tabs 正上方的**页内常驻面板** `.auto-reply-panel`：
  - 面板头：左侧标题 + 状态副标题（"未开启，买家咨询不会自动回复" / "已开启，买家咨询会自动回复 · 已应用到 N 个在卖商品"），右侧 **iOS 风格 Toggle 开关**（"所有宝贝默认回复"）；
  - Toggle 打开 → 展开编辑区（面板高亮），关闭 → 收起；打开/关闭有即时副标题反馈；
  - 编辑区：textarea（≤500 字）+ 三个快捷模板 chips + 变量说明 + **实时预览**（输入时用示例值替换 `{昵称} {商品标题} {商品价格}` 即时渲染，让卖家能预判买家看到的实际文案）；
  - 操作区："同时覆盖已单独设置的商品"复选框 + "应用到全部在卖商品"按钮 + "保存"按钮。
- **一键应用交互**：点击后调 `POST /api/auto-reply/apply-all`，toast 汇总结果（如"已应用到 3 个在卖商品，跳过 1 个已单独设置"），随后刷新列表让商品卡片徽标同步。
- **卡片徽标**：在卖商品卡片操作行首新增 `自动回复·已开` 小徽标（浅绿底、深绿字），一键应用后所有在卖商品卡片即时出现徽标，卖家一眼看清哪些商品已接入自动回复。
- **CSS**：新增通用 `.switch` Toggle 组件（原生 checkbox 视觉隐藏 + 轨道圆钮滑动动画，键盘可聚焦）与面板/预览/徽标样式，配色全部走平台 `--campus` 主题变量，并补齐 `html[data-theme="dark"]` 深色主题下的面板、轨道、预览、徽标配色，与全站风格统一、不突兀。

### 3. 验证情况

- 后端 `mvnw compile` 通过；重启服务后接口实测：
  - `PUT /api/auto-reply/global` 开启 + 保存内容 → 返回 `{enabled:true, content, appliedCount}`；
  - `POST /api/auto-reply/apply-all`（不覆盖）→ `{applied:3, overwritten:0, skipped:1}`；勾选覆盖 → `{applied:0, overwritten:4}`，结果符合预期分流；
- 浏览器端到端（登录演示账号 lin@campus.edu）：
  - 面板常驻、Toggle 开合正常、展开收起动画流畅；
  - 输入内容实时预览正确渲染（"你好呀，同学！我的宝贝还在哦，¥99.00，诚心要可以聊～"）；
  - 保存后副标题统计正确（"已应用到 4 个在卖商品"），刷新页面状态保持；
  - 一键应用 toast 结果正确，所有在卖商品卡片出现"自动回复·已开"徽标；
  - 深色主题下面板、预览、徽标配色正常。

## 今日调整的主要文件

| 文件 | 说明 |
|---|---|
| `src/main/java/com/campus/trade/service/chat/AutoReplyService.java` | 新增 `applyAll()`（事务批量应用）与 `countAppliedOnSale()`（已应用统计） |
| `src/main/java/com/campus/trade/controller/chat/AutoReplyController.java` | 新增 `POST /api/auto-reply/apply-all`；全局 GET/PUT 返回 `appliedCount` |
| `src/main/java/com/campus/trade/controller/product/ProductController.java` | `view()` 输出 `autoReplyEnabled / autoReplyText` |
| `src/main/resources/static/my-products.html` | 全局弹窗 → 页内常驻面板（Toggle + 编辑区 + 一键应用） |
| `src/main/resources/static/js/app.js` | `initMyProducts` 智能回复区重写：面板/Toggle/预览/保存/一键应用/徽标 |
| `src/main/resources/static/css/app.css` | 新增 `.switch` Toggle 组件、面板/预览/徽标样式与深色主题适配 |
| `docs/user2-商品中心设计文档.md` | §9 验收清单 T5.1/T5.2 标记完成 |

## 遇到的问题与解决方案

| 问题 | 解决方案 |
|---|---|
| 保存全局回复后，"已应用 N 个在卖商品"统计显示 0 | 根因：`PUT /api/auto-reply/global` 只返回 `enabled/content`，前端刷新面板时拿不到 `appliedCount`。修复：后端 PUT 与 GET 统一返回 `appliedCount`，前端保存后直接用返回数据刷新面板 |
| 手动打开 Toggle 时副标题仍显示"未开启"，与开关状态矛盾 | 根因：`change` 事件里只处理了收起与文案，打开方向没有即时文案。修复：打开/关闭双向更新副标题（"已开启，买家咨询会自动回复 · 保存后生效" / "未开启，买家咨询不会自动回复"），保存后由后端统计覆盖 |
| T5.2 验收要求"一键后所有在卖商品卡片出现徽标"，但列表接口此前不返回自动回复字段 | 根因：`view()` 未输出 `autoReplyEnabled`。修复：`ProductController.view()` 补字段，前端按字段渲染 `自动回复·已开` 徽标 |

---

### 同日续更：T5.3 单品智能回复开关升级 + T5.4 触发流程补强

在 T5.1/T5.2 面板验收通过后，继续完成 5A 剩下的两个子任务。T5.3 解决"单品弹窗还是老式勾选框、卖家关了单品后不知道会继承什么、卡片徽标只有'已开'一种状态"三个问题；T5.4 解决"自动回复消息在聊天里和真人消息长得一样、买家进会话不知道卖家开了自动回复、同一买家反复追问会被重复轰炸"三个问题。

### 1. 后端：一次性规则落地 + 会话提示条数据（`AutoReplySetting` / `AutoReplyService` / `ChatController`）

- **`AutoReplySetting` 新增 `replyOnce` 列**（Boolean，默认 `true`）：承载"同一买家对同一商品只自动回复一次"规则，随全局设置一起读写，`ddl-auto=update` 自动加列，老数据默认为 `true`，不破坏现有行为。
- **`AutoReplyService.maybeAutoReply()` 逻辑改造**：
  - 原来"该会话已存在卖家发的自动回复消息就直接返回"写死为一次性；
  - 现在改为：`replyOnce=true`（默认）保持原逻辑（每买家每商品一条）；`replyOnce=false` 时，只要**最后一条消息是买家发的**（即买家在追问、卖家还没回复）就允许再自动回一条；如果最后一条是卖家刚回的，则不触发，避免无限互刷。
- **`AutoReplyService.isGlobalEnabled(sellerId)`**（新增）：判断卖家是否开启全局默认回复（开启且有内容），供会话接口用。
- **`ChatController.conversationView()`** 新增返回 `autoReplyAvailable`：买家视角下，卖家**单品开了或全局开了**任一自动回复即为 `true`，前端据此显示提示条；卖家视角恒为 `false`，不给卖家自己看提示。
- **`AutoReplyController`**：全局 `GET / PUT` 返回与保存 `replyOnce`；`GlobalRequest` 记录增加 `replyOnce` 字段。

### 2. 前端：单品弹窗 Toggle + 三态徽标 + 聊天侧补强（`my-products.html` / `messages.html` / `app.js` / `app.css`）

- **单品弹窗升级（T5.3）**：
  - 弹窗内开关从 `<input type="checkbox" class="checkbox">` 换成与面板一致的 iOS 风格 **Toggle**（复用 `.switch` 组件），文案"单品开启（关 = 继承全局）"；
  - 打开 Toggle → 显示回复内容编辑区；关闭 Toggle → 编辑区收起，弹窗底部出现**"当前继承全局"提示卡**，直接展示全局默认内容前 30 字（超出加 `…`），卖家关掉单品后一眼就知道会继承什么，而不是黑盒；
  - "恢复全局默认"按钮保持：点击后单品置为关闭、清空内容，即回到继承态，提示卡同时刷新。
- **卡片徽标三态（T5.3）**：
  - 原来只有 `自动回复·已开`（绿）；现在扩展为三态：单品已开 → 绿标"自动回复·已开"；单品未开但全局开 → 灰标"自动回复·继承全局"；两者都关 → 不显示；
  - 前端 `initMyProducts` 里维护 `arGlobalOn` 变量（拉取全局设置时同步），`ops()` 渲染卡片操作行时按"单品字段 + 全局开关"组合判断徽标，对在卖 / 已下架 / 已售卡片统一生效。
- **聊天侧补强（T5.4）**：
  - 自动回复消息气泡加浅色底 + 主题色描边（`.bubble-auto`），配合原有的"自动回复"小标签，与真人消息一眼区分；
  - `messages.html` 在输入框上方新增提示条 `#chatAutoReplyTip`（"卖家开启了自动回复，可直接提问～"），`openConversation()` 根据后端返回的 `conversation.autoReplyAvailable` 显示/隐藏，买家进会话即有预填感知，卖家视角不出现；
  - 面板编辑区新增 **"同一买家对同一商品只自动回复一次"** 开关（`#arReplyOnce`），随全局设置保存，刷新回显。
- **CSS**：新增 `.ar-inherit-badge`（灰徽标）、`.ar-product-switch`（弹窗内 Toggle 行）、`.ar-inherit-tip`（继承提示卡）、`.bubble-auto`（浅色气泡）、`.chat-ar-tip`（提示条）样式，全部走 `--campus` 主题变量并补深色主题配色，与全站风格统一。

### 3. 验证情况

- 后端 `mvnw compile` 通过，重启后接口实测：
  - `GET/PUT /api/auto-reply/global` 正常返回/保存 `replyOnce`（false ↔ true 往返一致）；
  - `GET /api/chat/conversations` 返回 `autoReplyAvailable`：买家视角开启自动回复的卖家为 `true`，卖家视角为 `false`。
- 浏览器端到端（演示账号双账号切换验证）：
  - 单品弹窗：打开 → Toggle 开/关切换流畅，关闭后编辑区收起、继承提示卡显示全局内容前 30 字；点"恢复全局默认"回到继承态并刷新提示；
  - 徽标三态：将第一个在卖商品单品关闭并保存后，卡片徽标由"自动回复·已开"变为"自动回复·继承全局"，刷新页面状态保持，其余 5 个仍为"已开"；
  - 聊天页（chen 买家视角）：进入咨询 lin 商品的会话，顶部出现"卖家开启了自动回复"提示条；打开含自动回复的会话，消息以浅色气泡 + "自动回复"标签展示，与买家消息明显区分；卖家视角提示条不出现；
  - 一次性规则实测：`replyOnce=true` 时买家连续追问不再触发自动回复；把全局 `replyOnce` 改为 `false` 后，买家再追问一次即再收到一条自动回复（内容正确渲染变量："你好呀，陈同学！英语六级资料，自用！还在哦，¥29.99…"），随后恢复 `true`；
  - 深色主题下灰徽标、提示条、浅色气泡配色正常。

## 补充调整的文件（T5.3/T5.4）

| 文件 | 说明 |
|---|---|
| `src/main/java/com/campus/trade/entity/chat/AutoReplySetting.java` | 新增 `replyOnce` 列（默认 true） |
| `src/main/java/com/campus/trade/service/chat/AutoReplyService.java` | `maybeAutoReply()` 支持 replyOnce=false 追问再回；新增 `isGlobalEnabled()` |
| `src/main/java/com/campus/trade/controller/chat/AutoReplyController.java` | 全局 GET/PUT 返回并保存 `replyOnce` |
| `src/main/java/com/campus/trade/controller/chat/ChatController.java` | `conversationView()` 返回 `autoReplyAvailable` |
| `src/main/resources/static/my-products.html` | 单品弹窗 Toggle + 继承提示卡；面板新增 replyOnce 开关 |
| `src/main/resources/static/messages.html` | 输入框上方新增买家预填提示条 |
| `src/main/resources/static/js/app.js` | 徽标三态、单品弹窗交互、提示条显示、回复气泡类、面板 replyOnce 保存 |
| `src/main/resources/static/css/app.css` | 灰徽标/继承提示卡/浅色气泡/提示条样式与深色适配 |
| `docs/user2-商品中心设计文档.md` | §9 验收清单 T5.3/T5.4 标记完成 |

## 遇到的问题与解决方案（T5.3/T5.4）

| 问题 | 解决方案 |
|---|---|
| 单品弹窗还是老式勾选框，且关闭单品后卖家不知道会继承什么 | 开关换为与面板一致的 Toggle；关闭时收起编辑区并显示"当前继承全局"提示卡（全局内容前 30 字 + `…`），"恢复全局默认"同步刷新提示 |
| 徽标只有"已开"一种状态，表达不了"单品未开但全局开"的情况 | 徽标升级为三态：单品已开 → 绿"自动回复·已开"；单品未开 + 全局开 → 灰"自动回复·继承全局"；都关不显示。前端维护 `arGlobalOn` 变量参与判断 |
| 前端不知道卖家是否开了自动回复，提示条没有数据来源 | 后端 `conversationView()` 新增 `autoReplyAvailable`（买家视角 && 卖家单品/全局任一开启），前端按此显隐提示条，卖家视角恒不显示 |
| 买家反复追问会收到重复自动回复，且原逻辑写死一次性无法配置 | `AutoReplySetting` 新增 `replyOnce` 列（默认 true 保持原行为）；`false` 时仅当会话最后一条是买家消息（追问中）才再回一条，卖家回过不触发，防互刷 |
| `AutoReplySetting` 新增列后老数据行为不确定 | `ddl-auto=update` 自动加列，字段默认 `true`，老库无需迁移即保持"每买家每商品一条"的既有行为 |

---

### 同日续更：T5.5 依赖与 Provider 免费配置 + T5.6 AI 优化服务与接口（降级不阻塞）

智能回复（5A）验收通过后，进入 5B：给发布页加"AI 一键优化商品描述"。T5.5 解决"用什么模型、怎么免费、怎么切换"；T5.6 解决"怎么调、怎么保证失败也不影响发布"。

### 1. 技术选型：按设计文档降级方案，自研 OpenAI 兼容客户端

设计文档 T5.5 主方案是引入 Spring AI starter（Ollama / GLM 两个 provider），但**明确写了**："若官方 spring-ai starter 版本与 Boot 4 暂不兼容，降级方案为不依赖 spring-ai、自研约 40 行的 OpenAI 兼容 HttpClient 客户端，对外接口与 Prompt 逻辑不变"。实测情况：本地 Maven 仓库只有 `spring-ai-bom 2.0.0-M2` 里程碑版，`spring-ai-starter-model-ollama` 未缓存且需联网拉取，Boot 4.1.1 无官方适配确认——**直接引入 starter 风险高、可能拖垮构建**（违反验收标准"默认关闭不影响构建与既有测试全绿"）。因此采用**降级方案作为主实现**：

- **不新增任何 Maven 依赖**（`pom.xml` 零改动），构建与既有测试零风险；
- `AiDescriptionService` 自研 OpenAI 兼容客户端：`POST {base}/chat/completions`，`messages` 直接拼 Prompt，与 Spring AI 方案完全同协议；
- Ollama 与智谱 GLM 都提供 OpenAI 兼容接口，只需切换 `base-url / model / api-key`，一套客户端两种 Provider 通吃。

### 2. 后端：配置 + 服务 + 接口（`AiConfig` / `AiDescriptionService` / `AiDescriptionController` / `application.properties`）

- **`AiConfig`（新配置类）**：读取独立前缀 `app.ai.desc.*`（与自然语言搜索共用的 `app.ai.*` 互不干扰），默认 **`enabled=false` 关闭**、`provider=ollama`、`base-url=http://localhost:11434/v1`、`model=qwen2.5:3b`、超时 3 秒。换 GLM 只需配 `provider=glm + base-url=https://open.bigmodel.cn/api/paas/v4 + api-key + model=glm-4-flash`。
- **`AiDescriptionService`（核心）**：
  - **Prompt 组装**：System 严格按设计文档原文（"校园二手交易平台的商品描述润色助手…不得虚构参数…≤200 字…不得添加营销夸张词"）；User 拼「标题 / 分类 / 参数(键值) / 成色 / 价格 / 原描述」；
  - **调用与解析**：RestClient + `SimpleClientHttpRequestFactory` 设 3 秒连接/读取超时；Ollama 无鉴权时不带 `Authorization` 头，GLM 有 key 才带；取 `choices[0].message.content`，空或超 200 字截断；
  - **降级铁律**：未启用 / GLM 缺 key / 超时 / 连接失败 / 解析失败，一律返回 `{optimized:null, reason:"AI 服务不可用，请手动填写"}`，**永不抛错、绝不阻塞发布**；
  - **防滥用**：内存令牌桶 `ConcurrentHashMap<Long,Long>`，每登录用户 **10 秒限 1 次**；令牌在调用**前**占用（成功失败都消耗），防止连点轰炸（初版只在成功路径记录、失败可无限重试，已修复）。
- **`AiDescriptionController`（新接口）**：
  - `GET /api/ai/status`（公开）：`{enabled, provider, model}`，前端按钮置灰依据；
  - `POST /api/ai/optimize-description`（需登录）：body 为商品表单字段，服务端重新校验（标题必填且 ≤100 字、描述 ≤5000、参数 ≤30 项），随后调 service。
- **`application.properties`**：新增 `app.ai.desc.*` 配置块，注释写明两种 Provider 的配套写法。

### 3. 配套文件：启动脚本 + README（T5.5 涉及文件）

- **`scripts/start-ollama.bat`（新增）**：检测 Ollama 是否安装（未装提示官网下载）→ 检查 `qwen2.5:3b` 模型（没有则 `ollama pull`）→ `ollama serve` 启动 → curl 轮询 `/api/tags` 等就绪 → 提示设置 `AI_DESC_ENABLED=true` 启用。
- **README**：新增「AI 商品描述优化（可选，默认关闭）」章节——功能说明、Ollama 主方案三步启用、GLM 备选环境变量清单、配置项表格、接口表格；「主要 API」表加 `/api/ai/*` 行。

### 4. 验证情况

- **既有测试全绿**：`mvnw test` 共 53 个测试（StudentBayApplication 1、DemoData 2、Day5 订单 11、Order 5、ProductStage4 3、Day6 信任 4、业务流 5、页面 22），BUILD SUCCESS——验收标准"默认关闭不影响构建与测试"达成。
- **接口实测**（默认关闭状态）：
  - `GET /api/ai/status` → `{enabled:false, provider:"ollama", model:"qwen2.5:3b"}`；
  - `POST /api/ai/optimize-description` 未登录 → 401"请先登录"；空标题 / 120 字标题 → 400；
  - 登录后调用 → 200 且 `{optimized:null, reason:"AI 服务未配置，请手动填写"}`（默认关闭降级正确，发布流程不受影响）。
- **开启 AI 后实测**（`AI_DESC_ENABLED=true` 重启，本机未装 Ollama）：
  - `status` → `{enabled:true,...}`；调用 → 连接 `localhost:11434` 失败 → 200 且 `{optimized:null, reason:"AI 服务不可用，请手动填写"}`（降级不抛错）；
  - 10 秒内第二次调用 → `{optimized:null, reason:"操作太频繁，请 10 秒后再试"}`（限流生效）；
  - 验证后服务已恢复默认关闭重启。

## 补充调整的文件（T5.5/T5.6）

| 文件 | 说明 |
|---|---|
| `src/main/java/com/campus/trade/config/AiConfig.java` | 新增：AI 描述优化配置类（`app.ai.desc.*`，默认关闭） |
| `src/main/java/com/campus/trade/service/ai/AiDescriptionService.java` | 新增：自研 OpenAI 兼容客户端 + Prompt 组装 + 降级 + 限流 |
| `src/main/java/com/campus/trade/controller/ai/AiDescriptionController.java` | 新增：`GET /api/ai/status`、`POST /api/ai/optimize-description` |
| `src/main/resources/application.properties` | 新增 `app.ai.desc.*` 配置块（Ollama/GLM 注释说明） |
| `scripts/start-ollama.bat` | 新增：Ollama 检测/拉模型/启动/就绪等待脚本 |
| `README.md` | 新增「AI 商品描述优化（可选）」章节 + API 表格补 `/api/ai/*` |
| `docs/user2-商品中心设计文档.md` | §9 勾选 T5.5/T5.6；同步总开关关键词为 `app.ai.desc.enabled`、涉及文件更新 |

## 遇到的问题与解决方案（T5.5/T5.6）

| 问题 | 解决方案 |
|---|---|
| Boot 4.1.1 与官方 spring-ai starter 兼容性存疑（本地仅 2.0.0-M2 里程碑版、starter 未缓存需联网） | 按设计文档既定降级方案：不引入 starter、自研约 60 行 OpenAI 兼容客户端（`POST {base}/chat/completions`），Ollama/GLM 同协议切换，`pom.xml` 零改动、既有测试全绿 |
| 初版限流只在调用成功后才记录时间戳，失败（如连接拒绝）后可无限重试 | 修复：调用前先占用令牌（成功失败都消耗），同一用户 10 秒内只放行一次，防连点轰炸 |
| controller 与 service 各定义了同名 `OptimizeRequest` record，编译报类型不匹配 | 删除 controller 内的 record，统一复用 `AiDescriptionService.OptimizeRequest` |
| Ollama 无鉴权时带空 Bearer 头可能被拒 | 仅当 `api-key` 非空才加 `Authorization` 头；GLM 必须有 key，未配直接降级提示 |

---

### 同日续更：T5.7 发布页"AI 优化描述"按钮 + T5.8 AI 功能测试与 README 配置说明

后端 AI 服务与接口就绪后，把能力接到发布页，并补上离线测试。T5.7 解决"卖家在发布/编辑页怎么用上这个功能"；T5.8 解决"老师怎么验证、README 怎么照做启用"。

### 1. 前端：发布页 AI 优化按钮（`publish.html` / `app.js` / `app.css`）

- **按钮位置与样式**：放在"商品描述"label 同一行的**右上角**（label 改为 flex 两端对齐）。刻意做成**低调的描边小胶囊文字按钮**（"AI 优化描述"），复用平台 `--line/--paper/--campus` 主题变量，没有 ✨ 图标、没有渐变发光——和表单里其他控件一个质感，避免"AI 生成感"；
- **状态管理**：
  - 页面加载时调 `GET /api/ai/status`：`enabled=false`（默认）→ 按钮置灰 + title"AI 服务未配置，可查看 README 启用"；
  - 点击时先校验**标题必填**（toast"请先填写商品标题，AI 需要依据标题来润色"并聚焦），再收集「标题/分类/成色/价格/参数键值/原描述」POST 到 `/api/ai/optimize-description`；
  - 请求中按钮变"润色中…"并禁用（防连点），结束恢复；
- **成功/失败处理**：
  - 成功：把返回文本**填入 textarea 并聚焦**（可继续手动修改），textarea 下方出现浅色提示条"已根据你填写的信息生成，可继续手动修改 · **恢复原文**"；再次生成会以最新 AI 结果为准；
  - 恢复原文：点"恢复原文"把描述还原为生成前的原文、收起提示条；
  - 失败（AI 未配置/不可用/限流）：toast 展示后端 reason，**原描述一字不动**，按钮恢复可点。

### 2. 测试：AI 离线测试（`AiDescriptionServiceTests` / `AiDescriptionServiceEnabledTests`）

- **默认态测试类**（4 个用例，不依赖外网）：
  - `statusDefaultsToDisabled`：MockMvc 断言 `GET /api/ai/status` 默认 `{enabled:false, provider:"ollama", model:"qwen2.5:3b"}`；
  - `optimizeDegradesWhenDisabled`：未启用时返回 `{optimized:null, reason:"AI 服务未配置，请手动填写"}`；
  - `userPromptContainsAllFormFields`：反射调用 `buildUserPrompt`，断言 Prompt 含标题/分类/参数键值/成色/价格/原描述；
  - `userPromptSkipsBlankFields`：空字段时 Prompt 不出现"参数/价格/原描述"段，空值以"未填写"占位。
- **开启态测试类**（2 个用例，`@TestPropertySource` 覆盖 `enabled=true`、`base-url=http://localhost:1/v1`——本机未监听端口，**全程本机回环不出网**）：
  - `optimizeDegradesOnConnectFailure`：连接失败 → 降级"AI 服务不可用，请手动填写"，不抛错；
  - `rateLimitBlocksSecondCallWithin10Seconds`：同一用户 10 秒内第二次调用 → "操作太频繁，请 10 秒后再试"。
- 全量 `mvnw test`：**59 个测试全部通过**（原 53 + 新增 6），BUILD SUCCESS。

### 3. README 补充

- AI 章节末尾新增「### 测试」小节：说明两个 AI 测试类的运行命令与覆盖内容（默认关闭降级、Prompt 组装、连接失败降级、限流）。

### 4. 验证情况

- 默认关闭：发布页按钮置灰、title 提示"AI 服务未配置，可查看 README 启用"，截图确认按钮视觉自然（无 AI 味）；
- 开启 AI 后（本机无 Ollama）：
  - 空标题点击 → toast 引导先填标题；
  - 填标题点击 → 降级 toast"AI 服务不可用，请手动填写"，描述原文保留、按钮恢复；
- 端到端成功路径（本地起假 OpenAI 兼容服务模拟 Ollama 响应，指向 `localhost:11435/v1`）：
  - 点击 → 描述被替换为润色文本、textarea 聚焦、提示条出现、toast"描述已生成，可继续手动修改"；
  - 点"恢复原文" → 描述还原、提示条收起；再点生成 → 再次替换，可反复操作；
- 验证完成后：停假服务、Spring 恢复默认配置重启（`/api/ai/status` 回到 `enabled:false`）。

## 补充调整的文件（T5.7/T5.8）

| 文件 | 说明 |
|---|---|
| `src/main/resources/static/publish.html` | 描述 label 行加"AI 优化描述"按钮 + 恢复原文提示条 |
| `src/main/resources/static/js/app.js` | `initPublish` 新增 AI 按钮逻辑（状态加载/校验/请求/替换/恢复原文） |
| `src/main/resources/static/css/app.css` | AI 按钮、提示条、恢复链接样式 + 深色主题适配 |
| `src/test/java/com/campus/trade/ai/AiDescriptionServiceTests.java` | 新增：默认态 4 用例（status/降级/Prompt 组装） |
| `src/test/java/com/campus/trade/ai/AiDescriptionServiceEnabledTests.java` | 新增：开启态 2 用例（连接失败降级/限流） |
| `README.md` | AI 章节补「测试」小节 |
| `docs/user2-商品中心设计文档.md` | §9 勾选 T5.7/T5.8 |

## 遇到的问题与解决方案（T5.7/T5.8）

| 问题 | 解决方案 |
|---|---|
| 按钮如果直接放"✨ AI 优化描述"会显得 AI 味重、与真实二手平台不符 | 改为低调描边小胶囊文字按钮，无图标无渐变，复用平台主题变量，与表单其他控件同质感；截图确认后用户认可 |
| 空标题时调用 AI 没有意义，且接口服务端也会校验 | 前端点击先校验标题必填并聚焦，给出明确引导文案 |
| 生成后用户可能不喜欢 AI 文案，需能反悔 | 记录生成前原文，显示"恢复原文"链接可一键还原；再次生成以最新结果为准 |
| AI 测试不能依赖真实模型与外网（老师本机可能没装 Ollama） | 默认态测 status/降级/Prompt 组装；开启态用 `@TestPropertySource` 指向本机未监听端口 `localhost:1` 验证连接失败降级与限流，全程离线 |

---

### 同日续更：AI 描述优化改为"跟随 AI 搜索配置"，开箱即用

用户反馈"这个 AI 服务能否直接开启？像我接入的 AI 搜索一样及时可用"——检查后发现：AI 搜索的"AI 部分"其实也需要 key 才启用（未配 key 时是本地语义解析 LOCAL_SEMANTIC 兜底，所以看起来一直可用）。为让发布页 AI 按钮配一次 key 就能用，做如下调整：

1. **配置跟随**（`application.properties` / `AiConfig` / `AiDescriptionService`）：
   - `app.ai.desc.enabled` 默认跟随 `AI_SEARCH_ENABLED`（无则 false）；`base-url/api-key/model` 默认跟随 `AI_BASE_URL/AI_API_KEY/AI_MODEL`，未配时回落 Ollama 默认值；`AI_DESC_*` 显式配置优先级更高；
   - `provider` 改为按 base-url **自动推断**（`open.bigmodel.cn → glm`、`localhost/127.0.0.1 → ollama`、其他 → `custom`），显式配置 `AI_DESC_PROVIDER` 时以显式值为准；
   - key 判断从"按 provider 判断"改为"按 base-url 判断"：本地服务（Ollama）免 key，在线服务必须携带 key，避免跟随搜索配置后 key 检查错位。
2. **README 同步**：AI 章节顶部加"开箱即用"说明（跟随 AI 搜索配置），方案一/方案二改为推荐直接配 `AI_SEARCH_*` 共用一套 key，配置项表更新默认值与说明。
3. **设计文档同步**：T5.5 总开关说明更新为"默认跟随 AI 搜索配置，可单独覆盖"。

**验证**：只设 `AI_SEARCH_ENABLED=true + AI_BASE_URL=http://localhost:11435/v1 + AI_API_KEY + AI_MODEL`（不设任何 `AI_DESC_*`）重启：
- `GET /api/ai/status` → `{enabled:true, provider:"ollama", model:"gpt-4.1-mini"}`（enabled/model 均跟随成功）；
- 发布页按钮由置灰变为可点；填写标题后点击 → 本地假 AI 服务返回 → 描述替换成功、提示条出现、可恢复原文；
- 首次点击出现一次瞬时"AI 服务不可用"降级（原描述保留、按钮恢复，重试成功）——降级保护按预期兜底，不阻塞发布；
- 验证完停掉假服务、恢复默认配置重启，`/api/ai/status` 回到 `enabled:false`；全量 `mvnw test` 59 个用例全绿（10 个测试类，0 失败 0 错误）。

## 补充调整的文件（同日续更 · 跟随搜索配置）

| 文件 | 说明 |
|---|---|
| `src/main/resources/application.properties` | AI 描述优化配置默认跟随 AI 搜索（嵌套占位符 `AI_DESC_ENABLED:${AI_SEARCH_ENABLED:false}` 等） |
| `src/main/java/com/campus/trade/config/AiConfig.java` | provider 按 base-url 自动推断；新增 `isLocalBaseUrl()` |
| `src/main/java/com/campus/trade/service/ai/AiDescriptionService.java` | key 判断改按 base-url（本地免 key、在线必带 key） |
| `README.md` | AI 章节"开箱即用"说明 + 配置项表更新 |
| `docs/user2-商品中心设计文档.md` | T5.5 总开关说明更新 |

## 遇到的问题与解决方案（同日续更）

| 问题 | 解决方案 |
|---|---|
| 用户以为 AI 搜索"已经接入 AI"，希望描述优化也能直接开启 | 查明搜索 AI 实际也需要 key（未配时为本地语义解析兜底）；改为描述优化默认跟随搜索配置，配一次 key 两个功能同时启用 |
| 跟随搜索配置后 base-url 可能指向在线服务，原"provider=ollama 免 key"的判断会错位（不校验 key 直接请求） | key 判断改为按 base-url：本地地址免 key、在线地址必须携带 key；provider 也按 base-url 推断，status 显示与真实通道一致 |
| 首次点击出现瞬时降级 | 属降级保护预期行为（调用前占令牌、失败保留原文、按钮恢复），重试即成功；生产环境为稳定在线服务或本机 Ollama，不受影响 |

---

### 同日续更：申请并配置智谱免费 Key，AI 真实可用

用户要求"直接申请一个免费 key 配置好重启"。协助完成并实测：

1. **申请 Key**（智谱开放平台 open.bigmodel.cn，注册/实名由用户本人完成，需手机号验证码）：
   - 用户登录后，协助在 API Key 管理页创建新 Key（名称 `studentbay-ai`，免费 GLM-4-Flash 额度），复制完整 Key 用于本地配置；
   - Key 属敏感凭据：只写入本地开发环境变量，不写入代码/日志/文档，不外传。
2. **配置持久化**（用户级环境变量 `setx`，IDEA 与终端启动均生效）：
   - `AI_SEARCH_ENABLED=true`、`AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4`、`AI_API_KEY=<智谱Key>`、`AI_MODEL=glm-4-flash`；
   - 因配置改为跟随搜索（上一段），**AI 搜索与发布页 AI 描述优化共用这一套 Key，同时启用**。
3. **重启验证**（显式注入环境变量重启）：
   - `GET /api/ai/status` → `{enabled:true, provider:"glm", model:"glm-4-flash"}`（provider 按 base-url 自动推断）；
   - 发布页 AI 按钮可点：填写"星巴克随行杯"表单 → 点击 → 按钮变"润色中…" → 真实模型返回自然润色文案并替换描述、提示条与 toast 正常，可恢复原文；
   - AI 搜索同步切为 `AI_ASSISTED` 模式（此前为本地语义解析 LOCAL_SEMANTIC）。

## 遇到的问题与解决方案（同日续更 · Key 配置）

| 问题 | 解决方案 |
|---|---|
| 申请智谱 Key 需手机号注册与实名认证，AI 无法代办 | 浏览器打开开放平台，用户本人完成注册/登录/创建 Key，AI 协助读取完整 Key 并完成本地配置 |
| `setx` 写入用户环境变量后，工具进程读不到（进程环境快照不刷新） | 通过注册表确认写入成功（`[Environment]::GetEnvironmentVariable(...,'User')` 可读）；启动服务时显式注入环境变量验证；用户 IDEA/新终端启动时自然读到持久化配置 |
| API Key 是敏感凭据，不能进代码库 | 仅存于本地用户级环境变量，日志与文档只记掩码，README 强调妥善保管 |

## 后续计划

- **5A / 5B 全部完成**：T5.1–T5.8 已全部完成并实测通过，剩余等待检查确认后统一提交（`user2` → `develop` → 推送）。
- **5C 全量自动化测试**：补商品中心全链路测试（T5.10）+ 平台缺口补测（T5.11），交付 `docs/tests/TESTING.md` 测试说明文档（T5.9/T5.12）。
