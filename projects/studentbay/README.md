# StudentBay 校园闲置交易平台

StudentBay 是面向高校学生的校园二手交易平台，使用 Spring Boot、Spring Data JPA、MySQL 与原生 HTML/CSS/JavaScript 实现。前端采用多页面业务流程，交易、卖家履约、咨询和管理功能彼此独立。

## 主要功能

- 校园账号登录、身份认证标识与会话登录态
- 商品发布、关键词/分类/成色筛选、价格区间、分页与热搜词、价格排序、商品详情
- 商品分类表驱动首页分类 Tab 与发布页下拉；发布页按分类动态显隐参数（教材 ISBN/课程号/服饰尺码/数码型号等）
- 最近浏览足迹、擦亮与置顶（置顶期内列表最前）、同分类相似商品推荐、成色说明与可议价标记
- 商品图片本地上传 / 粘贴链接 / 拖拽 / Ctrl+V 多方式，≤5 张主图，删除时同步清理本地文件
- Leaflet + 高德瓦片地图展示交易位置，发布商品时按国家/省/市/区/街道定位并点击地图选点，地点名称与地图双向同步
- 二手单件库存购物车、批量勾选/删除、移入收藏、同款比价与分享
- 收货地址新增/修改/删除、优惠券、配送和支付方式选择
- 多件结算奖励券、每日必做/成长任务、掷骰子校园寻宝、金币账户与订单抵扣
- 确认订单、独立支付页、支付结果页、订单详情与状态化“更多服务”
- 卖家订单页、模拟预约快递、填写运单并确认发货、买家确认收货
- 独立物流详情页、快递员隐私号码、预计取件、取件码和运输轨迹
- 按商品建立咨询会话，区分“我咨询的商品”和“别人咨询我的商品”
- 商品公开留言、卖家标识及留言管理
- 同款与相似商品价格对照、校内挂牌价区间和均价参考
- 收藏、举报、消息未读提醒
- 管理后台：用户状态、商品上下架、举报处理、地图配置

## 技术栈

- Java 17
- Spring Boot 4.1.1
- Spring Web MVC / Spring Data JPA / Jakarta Validation
- MySQL（本地运行）
- H2 + JUnit 5 + TestRestTemplate（自动化测试）
- Maven Wrapper
- Leaflet 1.9.4 + 高德地图瓦片（国内网络可正常加载，发布页/详情页地图共用）

## 本地运行

1. 在 MySQL 创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS studentbay
CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;
```

2. 在 IDEA 的 `StudentBayApplication` 运行配置中设置环境变量：

```text
DB_USERNAME=studentbay
DB_PASSWORD=你的本地数据库密码
```

3. 运行 `StudentBayApplication`，浏览器打开：

```text
http://localhost:8080/
```

首次启动会自动创建演示数据。演示账号的密码均为 `123456`：

| 角色 | 邮箱 |
|---|---|
| 买家 | `lin@campus.edu` |
| 卖家 | `chen@campus.edu` |
| 管理员 | `admin@studentbay.local` |

## 校园网内访问

项目默认监听 `0.0.0.0:8080`，数据库仍只在本机访问，不会向校园网暴露 MySQL 端口。

1. 确保 MySQL 已启动，并且本机可以正常运行 StudentBay。
2. 在项目目录执行：

```bash
./scripts/start-lan.sh
```

脚本会安全地提示输入 MySQL 密码，构建应用并打印本机校园网访问地址。当前截图中的地址为：

```text
http://10.225.235.76:8080
```

同学的设备需要连接同一个校园 Wi-Fi，并在浏览器中输入该地址。Mac 必须保持开机，StudentBay 和 MySQL 也必须持续运行。

如果本机可以打开，但同一 Wi-Fi 下的其他设备访问超时，通常是校园网开启了客户端隔离。应用代码无法绕过该网络策略，可改用自己管理的路由器或热点，或向学校网络中心申请可供校内访问的服务器、固定内网 IP 和 DNS 名称。

局域网 IP 可能在重新连接 Wi-Fi 后变化，可用以下命令重新查看：

```bash
ipconfig getifaddr en0
```

若要使用类似 `studentbay.school.edu` 的固定网址，需要学校网络中心将校内 DNS 解析到一台固定内网服务器。正式长期使用时还应配置 HTTPS，避免登录信息通过明文 HTTP 传输。

## 页面流程

### 买家

```text
商品列表 → 商品详情 → 加入购物车 → 确认订单 → 支付 → 买家订单 → 确认收货
```

### 卖家

```text
发布商品 → 收到已付款订单 → 预约快递 → 填写运单并确认发货
```

### 咨询与治理

商品详情可以发起商品专属会话或提交举报；预约快递后可进入独立物流详情页；管理后台可处理用户、商品、举报与地图配置。

## 主要 API

| 模块 | 入口 |
|---|---|
| 登录态 | `/api/auth/*` |
| 商品 | `/api/products/*` |
| 购物车 | `/api/cart/*` |
| 地址与优惠券 | `/api/account/*` |
| 任务、游戏与金币 | `/api/rewards/*` |
| 订单与履约 | `/api/order/*` |
| 咨询与未读消息 | `/api/chat/*` |
| 收藏与举报 | `/api/favorites/*`、`/api/reports` |
| 管理后台 | `/api/admin/*` |
| 地图公共配置 | `/api/map/config` |
| AI 商品描述优化 | `/api/ai/*`（可选，默认关闭） |

## AI 商品描述优化（可选，默认跟随 AI 搜索配置）

发布 / 编辑商品页的描述输入框右上角提供"AI 优化描述"：把已填的标题、分类、参数、成色、价格、原描述发给本地或在线模型，一键润色成自然口语化的商品描述（≤200 字，可继续手动修改）。**默认关闭**，不配置也不影响任何发布功能；AI 调用失败时按钮会提示"AI 服务不可用"，原描述保留，绝不阻塞发布。

> **开箱即用**：AI 描述优化**默认跟随"AI 搜索"同一套配置**（`AI_SEARCH_ENABLED` / `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`）。如果给 AI 搜索配过 Key，发布页 AI 按钮无需任何额外配置即可直接使用；也可以用 `AI_DESC_*` 单独覆盖（优先级更高）。Provider 由 base-url 自动推断：`open.bigmodel.cn → glm`、`localhost/127.0.0.1 → ollama`、其他 → `custom`。

### 方案一（主方案）· Ollama 本地模型

完全免费、离线可用（答辩断网也能演示），无需申请任何 Key：

1. 安装 Ollama：https://ollama.com/download
2. 运行项目自带脚本（自动拉取模型 `qwen2.5:3b` 并启动服务）：

   ```bash
   scripts/start-ollama.bat
   ```

3. 在 IDEA 运行配置中设置环境变量 `AI_SEARCH_ENABLED=true`（搜索与描述优化同时启用；只启用描述优化可设 `AI_DESC_ENABLED=true`），重启 Spring Boot 服务。

### 方案二（备选）· 智谱 GLM-4-Flash

智谱官方免费额度，需要申请 API Key：

1. 注册并申请 Key：https://open.bigmodel.cn
2. 在 IDEA 运行配置中设置环境变量（与 AI 搜索共用，两个功能同时生效）：

   ```text
   AI_SEARCH_ENABLED=true
   AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
   AI_API_KEY=你的智谱Key
   AI_MODEL=glm-4-flash
   ```

3. 重启 Spring Boot 服务。仅描述优化单独配置时使用 `AI_DESC_*` 前缀的对应变量。

### 配置项说明

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AI_DESC_ENABLED` | 跟随 `AI_SEARCH_ENABLED`，否则 `false` | 总开关，`true` 时发布页 AI 按钮可点 |
| `AI_DESC_PROVIDER` | `auto`（按 base-url 推断） | `ollama` / `glm` / `custom`，显式配置时以显式值为准 |
| `AI_DESC_BASE_URL` | 跟随 `AI_BASE_URL`，否则 `http://localhost:11434/v1` | OpenAI 兼容接口地址，换 Provider 需配套修改 |
| `AI_DESC_API_KEY` | 跟随 `AI_API_KEY`，否则空 | 在线服务必填；本地 Ollama 无需 |
| `AI_DESC_MODEL` | 跟随 `AI_MODEL`，否则 `qwen2.5:3b` | 模型名 |
| `AI_DESC_TIMEOUT_SECONDS` | `3` | 单次请求超时（秒），超时即降级为手动填写 |

### 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ai/status` | AI 状态 `{enabled, provider, model}`，前端按钮置灰依据 |
| POST | `/api/ai/optimize-description` | 润色商品描述（需登录，每用户 10 秒限 1 次） |

### 测试

AI 相关自动化测试**不依赖真实模型与外网**（H2 内存库；开启态用例把接口指向本机未监听端口，验证降级与限流）：

```bash
./mvnw test -Dtest=AiDescriptionServiceTests,AiDescriptionServiceEnabledTests
```

覆盖内容：默认关闭状态与降级返回、Prompt 组装（标题/分类/参数/成色/价格/原描述）、连接失败降级不抛错、10 秒限流。

## 测试

测试环境使用 H2，不依赖本地 MySQL：

```bash
./mvnw test
```

答辩时可按照 [IDEA 演示测试指南](docs/tests/IDEA演示测试指南.md) 逐项运行核心流程测试。

## 项目文档

- [产品说明书](docs/design/产品说明书.md)：产品定位、角色操作、业务规则、用例图、流程图和核心时序图
- [系统设计说明书](docs/design/系统设计说明书.md)：系统架构、模块边界、安全性与一致性设计
- [数据库设计](docs/design/数据库设计.md)：数据表、实体关系、索引和事务策略
- [API 接口文档](docs/design/API接口文档.md)：接口规范、权限、参数、状态和调用示例
- [赵梓廷个人贡献说明](docs/design/个人贡献说明-赵梓廷.md)：Day 2—Day 10 的个人工作与可核验证据

## 分支协作

开发前从 `develop` 同步，个人工作提交到自己的分支，再通过 Pull Request 合并：

```bash
git switch user1
git fetch origin
git merge origin/develop
git add .
git commit -m "[交易核心] 完成购物车与订单履约流程"
git push origin user1
```
