# StudentBay 商品中心（user2）设计文档 & 迭代对照表（V2）

> 分工角色：**user2 · 商品中心**（商品发布、搜索、上下架、图片上传 + 商品管理 + 智能回复 + 其他功能/界面扩展）
> 核心数据表：`category`、`product`（在现有基础上扩展）
> 迭代策略：**阶段0 修 Bug → 阶段1 发布页改造 → 阶段2 商品管理（我发布的）→ 阶段3 智能回复 → 阶段4 既有扩展/总结 → 阶段5 体验升级与收尾（智能回复升级 / AI 商品描述优化 / 全量自动化测试）**，每步可验收
> 文档用途：个人开发设计文档 + 迭代对照表（验收清单）。每完成一项就勾选并记录。
> 版本说明：V2 依据本人补充需求（我发布的、智能回复、砍一刀、多方式图片上传、交易地点×地图联动）扩展而来；**与 V1 不冲突的设计与扩展内容全部保留**，仅调整迭代顺序。

---

## 0. 如何用这份文档

1. 每个「子任务」都是一次可独立提交、可验收的开发单元，包含：需求、涉及文件、数据/接口改动、界面布局改动、验收标准。
2. 严格按照顺序做：**阶段 0 必须先完成**（发布页 Bug 已定位并修复，见 §3），阶段 1（发布页改造）→ 阶段 2（商品管理）→ 阶段 3（智能回复）按依赖推进，阶段 4 为不阻塞主线的增强项。
3. 每完成一个子任务：在「状态」列打勾，并在 git 提交信息中标注（如 `[商品中心] 完成发布页多方式图片上传`）。
4. 涉及数据库改动的地方，本项目使用 `spring.jpa.hibernate.ddl-auto=update`，**新增实体/字段后重启会自动建表/加列**，无需手写迁移脚本；但 `category`、`campus_poi` 这类需要初始数据的表要在 `DemoDataInitializer` 里补种子数据。
5. 本设计面向 **vibe coding / 系统开发**：每个子任务给出可落地的接口、字段、页面结构，可直接照着敲代码。

---

## 1. 我的职责与目标

| 项 | 内容 |
|---|---|
| 负责模块 | 商品中心：商品发布、搜索、上下架、图片上传 |
| 界面扩展 | 发布页、首页搜索/筛选、商品详情、**我发布的（在卖/草稿/已下架）**、**智能回复设置**、最近浏览等 |
| 新增方向（V2） | **① 我发布的**（三栏管理 + 编辑 + 重新上架）；**② 智能回复**（全局默认 + 单品设置）；**③ 砍一刀**（随机降价区间 + 价格动态）；**④ 图片上传多方式**（本地文件 + 链接 + 拖拽 + 剪贴板）；**⑤ 交易地点 × 地图联动**；**⑥ 智能回复体验升级**（Toggle 开关 + 一键应用到全部商品）；**⑦ Spring AI 商品描述优化**（免费 Provider，发布页一键润色）；**⑧ 全量自动化测试与测试文档** |
| 对照课程功能需求 | **FR2 Item Listings**（≤5 张图、详细描述、成色等级、价格、浏览量、分类专属字段）、**FR3 Search and Discovery**（关键词/分类/价格区间/成色；教材支持 ISBN、课程号、课程名检索）、**FR5 Item Status Management**（AVAILABLE / RESERVED / SOLD 三态 + 卖家管理上下架） |
| 仿闲鱼目标 | 发布支持多图九宫格、分类动态参数表单、砍价议价；首页分类 Tab + 多条件搜索；卖家可自助上下架/编辑/置顶/擦亮；商品带浏览与收藏计数；买家咨询有智能回复 |

---

## 2. 现状盘点（已完成的部分，代码级）

> 基于对当前仓库代码的通读，以下功能**已存在**，是本轮开发的起点（完善而非从零）：

| # | 已实现能力 | 位置（文件 / 接口） | 说明 |
|---|---|---|---|
| 1 | 商品发布（单图、表单、地图选点） | `publish.html` + `POST /api/products`（`ProductController.publish`） | 标题/分类/成色/售价/原价/图片链接/描述/地点名/经纬度，含校验 |
| 2 | 地图选点 | `app.js initPublish()` / `createMap()` + `MapConfigController /api/map/config` | Leaflet 点击放标记，存经纬度；**当前存在 Bug（见 §3）** |
| 3 | 商品搜索/筛选 | `GET /api/products`（`keyword/category/condition/minPrice/maxPrice/sort`）+ `index.html` 搜索面板 | 服务端已支持价格区间参数，但**前端筛选区尚未暴露价格输入框**（待扩展） |
| 4 | 商品列表/卡片 | `index.html #productGrid` + `app.js productCard()` | 图、标题、价格、原价、位置、收藏数 |
| 5 | 商品详情 + 浏览量 | `GET /api/products/{id}`（viewCount 自增）+ `product-detail.html` | 详情/卖家/价格/留言/举报/收藏/咨询/加购/同款比价 |
| 6 | 商品状态枚举 | `ProductStatus`：`AVAILABLE / RESERVED / SOLD / OFF_SHELF` | 上下架依赖此状态；**草稿态 DRAFT 待新增** |
| 7 | 后台上下架 | `admin.html adminProducts()` → `PATCH /api/admin/products/{id}/status` | 管理端可下架/恢复 |
| 8 | 我的商品列表 | `GET /api/products/mine/list`（`ProductController.mine`） | 后端已有，**前端缺"我发布的"页面**（待扩展） |
| 9 | 图片上传基础设施 | `AccountController.uploadAvatar` + `WebConfig`（`/uploads/**` → `./uploads` 目录） | 头像上传先例：MultipartFile、5MB 限制、jpg/png/webp/gif 白名单、返回 `/uploads/{文件名}`；**商品图片上传可完全复用此模式** |
| 10 | 商品咨询（聊天） | `ChatController`（`POST /api/chat/conversations/{id}/messages` 等） | 买家/卖家会话；**智能回复在此处接入自动回复** |
| 11 | 同款比价 / 价格统计 | `GET /api/products/{id}/comparisons` + `price-compare.html` | 标题相似度 + 分类内价格统计 |
| 12 | 商品留言 | `ProductCommentController` + `initProductComments()` | 公开留言、卖家标识、删除 |
| 13 | 收藏 / 举报 | `FavoriteRepository` + `/api/favorites/*`；`/api/reports` | 收藏计数并入商品 view；举报联动下架 |
| 14 | 个人中心"我的服务"宫格 | `profile.html`（`.profile-grid` + `.profile-item`） | 收藏/咨询/购物车/订单/我买入的/我卖出的/发布闲置/任务中心；**"我发布的"入口在此新增** |
| 15 | 商品表 `products` | `Product` 实体（见 §4） | 字段较全，但缺多图、分类外键、砍价、智能回复、草稿等（待扩展） |

**结论**：发布闭环（发布→列表→详情）已能跑通，**发布页地图 Bug 已修复（阶段0）**；接下来按新需求改造发布页，再补商品管理与智能回复，最后完成课程要求的搜索增强等扩展。

---

## 3. 阶段 0：修复发布界面 Bug（★ 第一个任务，已完成 ✅）

### 3.1 现象
- 发布页地图区域无法显示地图（灰块），地图相关控件/布局错乱（乱码感）；
- 商品无法正常发布。

### 3.2 根因诊断（已实测验证）

**根因一：`publish.html` / `product-detail.html` 中 Leaflet 样式表的 SRI `integrity` 哈希写错，浏览器整块拦截。**

- 页面声明：`integrity="sha256-p4NxAoJBhIINfQ3ynhtIeWqKAMr1uJlZgF0PN7eV2S4="`
- 真实字节（unpkg 与 jsdelivr 返回一致，均实测）：`sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=`
- 两者**不一致** → 浏览器 SRI 校验失败，`leaflet.css` 被整块拒绝加载。
- 浏览器控制台实测报错：`Failed to find a valid digest in the 'integrity' attribute for resource '.../leaflet.css' ... The resource has been blocked.`
- 后果链：Leaflet CSS 未生效 → 地图容器内所有 `.leaflet-*` 元素无样式（控件散乱、瓦片堆叠、布局乱码）→ 地图不可用 → 用户在发布页无法/不愿完成"地图选点"，而提交逻辑强制要求 `if (!selected) { toast('请在地图上选择交易位置'); return; }` → **商品无法发布**。
- 注意：`leaflet.js` 的 integrity 是正确的（浏览器实测 `window.L` 已定义），**只有 CSS 的哈希错了**。

**根因二：地图瓦片源 `tile.openstreetmap.org` 在国内网络不可达（实测超时 HTTP 000）。**

- 即使修好 CSS，OSM 官方瓦片服务器在本机/国内网络下连不上，地图仍会空白灰屏。
- 实测高德瓦片 `webrd01.is.autonavi.com`（HTTP 200、image/png、响应 <0.3s）可稳定使用。
- 附带风险：原 CDN 为 `unpkg.com`，国内访问不稳定，已一并切换到国内可达的 `cdn.jsdelivr.net`（字节与 unpkg 完全一致，`integrity` 通用）。

### 3.3 修复内容（3 个文件，已完成）

| 文件 | 改动 |
|---|---|
| `src/main/resources/static/publish.html` | ① `leaflet.css` 的 `integrity` 改为正确哈希 `sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=`；② Leaflet CSS/JS 从 `unpkg.com` 切换到 `cdn.jsdelivr.net`（版本仍为 1.9.4，JS 哈希不变） |
| `src/main/resources/static/product-detail.html` | 与 publish.html 相同（该页"查看小地图"同样受此 Bug 影响） |
| `src/main/java/com/campus/trade/config/DemoDataInitializer.java` | ① 默认瓦片源改为高德：`https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}`，attribution 改为 `© 高德地图`；② 新增 `repairLegacyTileUrl()` 自愈逻辑：若数据库 `app_config` 中 `map.tileUrl` 仍是旧的 OSM 地址，则自动更新为高德，避免老库不生效 |

### 3.4 验证结果（已完成）
- 后端 `.\mvnw -o compile` 编译通过（exit 0）。
- 浏览器实测（复刻发布页的引入方式）：修复前 `Leaflet CSS 是否生效: false`、控制台报 SRI blocked；修复后 `Leaflet CSS 是否生效: true`、`document.styleSheets: 2`，**高德瓦片正常渲染出完整地图 + 蓝色定位标记**，缩放控件正常。
- 地图选点 → 发布闭环恢复正常（进入阶段 1 各任务后可再次端到端验证）。

### 3.5 遗留说明
- 若后续演示环境无外网，建议把 Leaflet 两个文件下载到 `src/main/resources/static/vendor/leaflet/` 本地引用（彻底摆脱 CDN），作为可选优化项，不阻塞当前开发。
- 数据库里的瓦片地址已由自愈逻辑自动迁移；如手动改过地图配置，可在管理后台「地图配置」页再核对一次。

---

## 4. 数据模型设计（扩展）

> 现有 `products` 表（`Product` 实体）字段：id, seller_id, title, description, category, item_condition, price, original_price, image_url, location_name, latitude, longitude, status, view_count, created_at, updated_at。
> `ProductStatus` 枚举新增 **`DRAFT`（草稿）**，用于"我发布的·草稿"栏（草稿也是一条 `product` 记录，status=DRAFT，不进入任何列表/搜索）。

### 4.1 `category` 分类表（新建实体 `Category`）——支撑分类 Tab、动态表单

| 字段 | 类型 | 说明 | 必填 |
|---|---|---|---|
| id | BIGINT PK 自增 | 主键 | ✔ |
| name | VARCHAR(40) | 分类名（如：教材资料 / 数码产品 / 骑行装备 / 宿舍生活 / 服饰美妆 / 其他） | ✔ |
| parent_id | BIGINT NULL | 父分类 id，一级分类为空（支持二级） | |
| icon | VARCHAR(200) | 分类图标/emoji | |
| sort | INT | 排序值（小的在前） | ✔ |
| status | VARCHAR(20) | `ENABLED / DISABLED` | ✔ |
| created_at / updated_at | DATETIME | 时间戳 | ✔ |

> 现有 `product.category` 是字符串，MVP 阶段先保留字符串（兼容现有代码），同时用 `category` 表驱动前端下拉/分类 Tab；完善期再评估是否迁移为 `category_id` 外键。

### 4.2 `product` 表扩展字段（在 `Product` 实体上直接加列，ddl-auto 自动建列）

**通用/展示类**
| 新增字段 | 类型 | 说明 |
|---|---|---|
| images | TEXT（JSON 数组） | 多图 URL 列表，如 `["/uploads/p-1.jpg", ...]`（主图=第 1 张） |
| brand | VARCHAR(80) | 品牌 |
| size | VARCHAR(80) | 尺寸/规格 |
| material | VARCHAR(80) | 材质 |
| color | VARCHAR(40) | 颜色 |
| gender | VARCHAR(20) | 性别（男 / 女 / 中性） |
| sport_type | VARCHAR(40) | 运动类型（骑行装备等分类用） |
| condition_detail | VARCHAR(500) | 成色/瑕疵说明（闲鱼"使用情况"） |
| isbn | VARCHAR(32) | 教材 ISBN（支持搜索） |
| course_code | VARCHAR(60) | 课程编号（支持搜索） |
| module_name | VARCHAR(120) | 课程/模块名称（支持搜索） |
| bargain_enabled | BOOLEAN | 是否可议价（议价标记，见阶段 4） |
| top_expire_at | DATETIME NULL | 置顶到期时间（null=未置顶） |
| sharpened_at | DATETIME NULL | 擦亮时间（用于"擦亮"排序权重） |
| stock | INT | 库存数量，默认 1（二手单件场景） |

**砍一刀类（V2 新增）**
| 新增字段 | 类型 | 说明 |
|---|---|---|
| cut_enabled | BOOLEAN | 是否开启"砍一刀"（false=不可砍，详情页按钮隐藏/禁用） |
| cut_min | DECIMAL(8,2) NULL | 单次随机降价下限（对应 1-3 / 3-5 / 自定义 min） |
| cut_max | DECIMAL(8,2) NULL | 单次随机降价上限（对应 1-3 / 3-5 / 自定义 max） |
| cut_limit | INT | 该商品最多可被砍次数（默认 3） |
| cut_count | INT | 已砍次数（每次砍价 +1，与记录表冗余，便于快速展示） |

**智能回复类（V2 新增）**
| 新增字段 | 类型 | 说明 |
|---|---|---|
| auto_reply_enabled | BOOLEAN NULL | 单品"开启智能回复"开关；NULL=未设置（继承全局） |
| auto_reply_text | VARCHAR(500) NULL | 单品回复内容；NULL=继承全局默认回复 |

**地点联动类（V2 新增）**
| 新增字段 | 类型 | 说明 |
|---|---|---|
| poi_id | BIGINT NULL | 关联 `campus_poi`（校园 POI）的 id；自定义位置为 NULL |
| address_detail | VARCHAR(300) | 详细地址/地点描述（如"广东省广州市黄埔区 · 图书馆东门"） |

### 4.3 `product_image` 表（新建实体 `ProductImage`）——多图上传的规范方案（可选，也可先用 4.2 的 JSON 字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| product_id | BIGINT | 所属商品 |
| url | VARCHAR(800) | 图片地址 |
| sort | INT | 排序（第 1 张为主图） |
| created_at | DATETIME | 时间戳 |

> 实现建议：MVP 用 `Product.images` JSON 字段最省事；进入"图片治理"子任务后再决定是否升级为独立表（更利于删除单图、生成缩略图）。

### 4.4 `product_browse` 表（新建，最近浏览/足迹）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 用户 |
| product_id | BIGINT | 商品 |
| viewed_at | DATETIME | 浏览时间 |

### 4.5 `price_cut_record` 砍价记录表（新建实体 `PriceCutRecord`）——砍一刀

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| product_id | BIGINT | 被砍商品 |
| buyer_id | BIGINT | 砍价买家 |
| amount | DECIMAL(8,2) | 本次砍价金额（随机生成，区间内） |
| price_before | DECIMAL(10,2) | 砍前现价 |
| price_after | DECIMAL(10,2) | 砍后现价 |
| created_at | DATETIME | 砍价时间 |

> 唯一约束：`(product_id, buyer_id)` 唯一 → 每个买家对同一商品只能砍一次。

### 4.6 `auto_reply_setting` 智能回复全局设置表（新建实体 `AutoReplySetting`）——所有宝贝默认回复

| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT PK | 卖家用户（一用户一行） |
| enabled | BOOLEAN | "所有宝贝默认回复"开关 |
| reply_text | VARCHAR(500) | 默认回复内容（支持模板变量，见阶段 3） |
| updated_at | DATETIME | 更新时间 |

> 优先级：**单品设置（auto_reply_enabled=true 时用 auto_reply_text）→ 全局设置（enabled=true 时用 reply_text）→ 不自动回复**。

### 4.7 `campus_poi` 校园 POI 表（新建实体 `CampusPoi`）——交易地点 × 地图联动

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| name | VARCHAR(120) | POI 名称（如"图书馆东门""北区宿舍 7 栋"） |
| address | VARCHAR(300) | 详细地址（省/市/区/街道） |
| latitude / longitude | DECIMAL(10,7) | 坐标 |
| category | VARCHAR(40) | 分类（学习区/生活区/运动区/校门口…） |
| sort | INT | 排序 |
| enabled | BOOLEAN | 是否启用 |

> 在 `DemoDataInitializer` 预置广东东软学院周边常用 POI（图书馆东门、北区宿舍、南区宿舍、实验楼、食堂、运动场等）。MVP 无需外部 API key，即可实现"选地点→地图定位"与"点地图→反查最近地点"的双向同步。

### 4.8 其他可选表
- `hot_keyword`：热搜词（keyword, count），用于搜索联想/热门榜。
- 草稿：**改用 `ProductStatus.DRAFT`**（草稿即一条 product 记录），不再单独建 `draft` 表；如需"多版本草稿"再评估独立表。

---

## 5. 接口设计一览

### 现有接口（商品中心相关）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/products` | 列表/搜索（keyword, category, condition, minPrice, maxPrice, sort） |
| GET | `/api/products/{id}` | 详情（viewCount+1） |
| POST | `/api/products` | 发布商品 |
| GET | `/api/products/mine/list` | 我的商品 |
| GET | `/api/products/{id}/comparisons` | 同款比价 |
| GET | `/api/products/{id}/comments` | 商品留言 |
| PATCH | `/api/admin/products/{id}/status` | 管理端上下架 |
| GET | `/api/map/config` | 地图配置 |
| POST | `/api/chat/product/{productId}` | 发起商品咨询（智能回复触发点之一） |
| POST | `/api/chat/conversations/{id}/messages` | 发送咨询消息（**智能回复自动插入点**） |

### 新增接口（按子任务落地）
| 方法 | 路径 | 说明 | 对应子任务 |
|---|---|---|---|
| POST | `/api/uploads` | 通用图片上传（复用头像上传逻辑，返回 `/uploads/{文件名}`） | T1.1 |
| POST | `/api/products`（入参加 `status=DRAFT\|AVAILABLE`） | 支持"保存草稿"/"直接发布" | T1.4 |
| PUT | `/api/products/{id}` | 编辑商品（卖家本人，含价格） | T2.2 |
| PATCH | `/api/products/{id}/status` | 卖家上下架 / 重新上架 / 草稿转发布（校验 sellerId，越权 403） | T2.3 |
| POST | `/api/products/{id}/bargain` | 买家"砍一刀"（随机降价） | T1.3 |
| GET | `/api/pois?keyword=` | 校园 POI 联想/列表 | T1.2 |
| GET | `/api/pois/nearest?lat=&lng=` | 逆查最近 POI（点地图回填地点名） | T1.2 |
| GET/PUT | `/api/auto-reply/global` | 查看/保存"所有宝贝默认回复" | T3.1 |
| GET/PUT | `/api/products/{id}/auto-reply` | 查看/保存"单品智能回复" | T3.2 |
| GET | `/api/products/mine/list?status=` | 我的发布按状态过滤（AVAILABLE/OFF_SHELF/SOLD/DRAFT） | T2.1 |
| GET | `/api/categories` | 分类树/列表 | T4.1 |
| GET | `/api/products/hot-keywords` | 热搜词 TOP10 | T4.2 |
| GET | `/api/products/browse/recent` | 我的最近浏览 | T4.4 |
| POST | `/api/products/{id}/sharpen` | 擦亮（更新时间/排序权重） | T4.5 |
| POST | `/api/products/{id}/top` | 置顶（设置 top_expire_at） | T4.5 |
| GET | `/api/products/recommend?productId=` | 相似推荐 | T4.6 |
| POST | `/api/auto-reply/apply-all` | 一键把全局回复应用到全部在卖商品（body `{overwrite}`，返回 applied/skipped/overwritten） | T5.2 |
| GET | `/api/ai/status` | AI 能力状态（enabled/provider/model，前端按钮置灰用） | T5.5 |
| POST | `/api/ai/optimize-description` | AI 一键优化商品描述（登录 + 10s 限流 + 降级不阻塞） | T5.6 |

> 分页建议：列表接口增加 `page` / `size` 参数，返回 `{ items, total, page, size }`（见 T4.2）。

---

## 6. 迭代子任务清单（核心对照表）

### 阶段 0：修复发布 Bug 【已完成 ✅】 → 详见 §3

---

### 阶段 1：发布页改造（先把"发出去"这件事做完整、可体验）

#### T1.1 图片上传多方式（本地文件 + 链接 + 拖拽 + 剪贴板粘贴）
- **需求**：发布页"商品图片"从"单张链接输入框"升级为九宫格，支持**多种来源混合**，最多 **5 张**（FR2「up to five photos」），第 1 张为主图：
  1. **本地文件选择**：点击/拖拽上传（`input[type=file][multiple]`、drag & drop）；
  2. **粘贴图片链接**：保留现有 URL 输入能力（格子里可输入 https:// 链接，图片加载成功才保留）；
  3. **剪贴板粘贴**：页面内 Ctrl+V 直接粘贴截图（`document.addEventListener('paste')` 读 `clipboardData.items` 中的 image）；
  4. **拍照**（移动端增强）：`input capture="environment"` 调起相机。
- **涉及文件**：后端新增 `UploadController` 提供 `POST /api/uploads`（复用 `AccountController.uploadAvatar` 模式：MultipartFile、≤5MB、jpg/jpeg/png/webp/gif 白名单、写入 `./uploads`、返回 `/uploads/{文件名}`）；`Product` 实体加 `images`；`ProductController.publish/update` 接收 `List<String> images`（本地路径 + 远端链接混合），主图冗余写 `imageUrl`（兼容现有列表/详情）。
- **界面布局**：
  ```
  ┌─────────────────────────────────────┐
  │ 商品图片（第 1 张为主图，最多 5 张，可拖拽排序）   │
  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐   │
  │ │ 图1 │ │ 图2 │ │ 图3 │ │ ＋ │ │ 空 │   │
  │ │ 主图 │ │    │ │    │ │上传│ │    │   │
  │ └────┘ └────┘ └────┘ └────┘ └────┘   │
  │ [＋] 上传图片 / [⌁] 粘贴链接 ｜ 支持拖拽与 Ctrl+V 粘贴 │
  └─────────────────────────────────────┘
  ```
- **验收标准**：① 四种来源均可添加图片并实时预览；② ≤5 张、主图=第 1 张、可删除/拖拽排序；③ 非法格式/超 5MB 拦截；④ 链接图片加载失败给提示并可移除；⑤ 单图仍可发布（兼容）；⑥ 发布后详情页可见全部图片（配合 T1.3）。

#### T1.2 交易地点 × 地图联动（地点名称与地图位置双向呼应）
- **需求**：解决"地点名称文本框"与"地图选点"相互孤立的问题，做到：
  1. **选地点 → 地图跟随**："交易地点"输入框支持联想（下拉来自 `campus_poi`，含"名称+详细地址"），选中后自动在地图上定位（`setView` + 放标记）并回填经纬度与地点名称；
  2. **点地图 → 反查地点**：直接在地图上点击/拖动标记，自动调 `GET /api/pois/nearest?lat=&lng=` 反查最近 POI 并回填地点名称；500m 内无匹配时显示"自定义位置·(lat,lng)"并允许手动输入名称；
  3. **定位到我的位置**：提供"📍 定位"按钮（`navigator.geolocation`，需授权；定位后走第 2 步回填）；
  4. **一致性约束**：提交时地点名称与经纬度必须同源（选中 POI 则用 POI 名称与坐标；手动调整则用当前标记坐标 + 回填/手动名称）。
- **涉及文件**：新建 `CampusPoi` 实体 + Repository + `PoiController`（`GET /api/pois`、`GET /api/pois/nearest`）；`DemoDataInitializer` 预置校园 POI；`publish.html` 地点区重构、`app.js initPublish()` 联动逻辑。
- **界面布局**：
  ```
  ┌────────────────────────────────────────┐
  │ 交易地点名称  [ 输入联想 ▾  | 📍定位 ]   │
  │  选中后显示：省/市/区 · 街道 · 地点名       │
  ├────────────────────────────────────────┤
  │ 在地图上选择位置（390px）                │
  │  • 选地点自动居中并放标记                │
  │  • 点地图自动反查最近地点回填             │
  └────────────────────────────────────────┘
  ```
- **验收标准**：① 输入"图"能联想出"图书馆东门"等 POI；② 选中 POI 后地图定位到该点、坐标正确；③ 点地图后地点名称自动变为最近的 POI（或"自定义位置"）；④ 提交后详情页展示的地点名称与地图标记一致；⑤ 定位按钮在授权后可用（无授权环境降级隐藏）。

#### T1.3 砍一刀（随机降价区间 + 详情页价格动态）
- **需求**：
  - **设置端（发布时可预选，编辑/"我发布的"可随时改）**：选择是否开启"砍一刀"；开启后选区间，三选一：
    - `1-3元`：cut_min=1.00, cut_max=3.00
    - `3-5元`：cut_min=3.00, cut_max=5.00
    - `自定义砍价区间`：手填 cut_min、cut_max（校验 0.01 ≤ min < max，且不超过当前售价）
  - **砍价端（买家，商品详情页）**：点"砍一刀" → 在 `[cut_min, cut_max]` 内随机生成金额 → 现价 = 当前价 − 金额（下限保护：不低于 ¥1.00 且不低于原价的 50%）→ 价格变化**动态动画**（数字跳动 + 标签变化 + toast"砍价成功 -¥x.xx"）→ 新价格持久化并展示。
  - **规则补充（我完善）**：① 每件商品最多被砍 `cut_limit`（默认 3）次，`cut_count` 记录；② 每个买家对同一商品仅能砍一次（`price_cut_record` 唯一约束）；③ `cut_enabled=false` 的商品**不显示/禁用**砍一刀按钮（"设置了不砍价的商品无法进行小刀"）；④ 已下架/已售/交易中商品不可砍；⑤ 卖家本人不可砍自己的商品。
  - **数据落库**：砍后新价写入 `product.price`（现价），`original_price` 保留作划线参考；每次砍价写 `price_cut_record`；详情页展示"已砍 N 次 · 现价 xx / 原价 xx"。
- **涉及文件**：`Product` 加砍价字段；`PriceCutRecord` 实体 + Repository；`ProductController.bargain`（事务：校验规则→随机金额→更新 price/cut_count→写记录）；`product-detail.html` 价格区加"砍一刀"按钮与动画；`publish.html` / `my-products.html` 砍价配置表单。
- **界面布局（详情页价格区）**：
  ```
  现价 ¥88.00  [ 划线原价 ¥100.00 ]   [砍一刀]（可砍才显示）
  「已砍 1 次 · 上限 3 次」 ｜ 砍价成功后价格数字滚动动画
  ```
- **验收标准**：① 三档区间与自定义均可设置并保存；② 砍价金额在区间内且价格正确递减、有下限保护；③ 每个买家只能砍一次、总次数达上限后按钮禁用；④ 未开启砍价的商品无按钮；⑤ 价格变化有动画且详情页刷新后保持新价格；⑥ 已售/下架商品不可砍。

#### T1.4 发布页"保存草稿"与草稿转发布
- **需求**：发布页底部提供两个动作：**「保存草稿」**（status=DRAFT，未发布）与「确认发布」（status=AVAILABLE）；草稿可继续编辑后发布。
- **涉及文件**：`ProductController.publish` 接收 `status` 参数（DRAFT/AVAILABLE）；`publish.html` 双按钮；`app.js initPublish()`。
- **验收标准**：保存草稿后不出现在首页/搜索；草稿可被再次加载编辑并发布；空表单保存草稿给出提示。

#### T1.5 发布表单校验与交互完善（沿用 V1）
- **需求**：发布前校验（价格>0、必填项、标题长度、成色必选）；图片未传提示；提交/保存中按钮防重复；失败 toast 回显不丢内容。
- **验收标准**：非法输入在提交前拦截并定位字段；成功跳转商品详情；失败不回跳。

#### T1.6 商品详情页多图展示（沿用 V1）
- **需求**：详情页主图区支持 1–N 张切换（缩略图点击切换 / 左右箭头），缺失图回退占位图。
- **验收标准**：多图可切换；单图无多余控件；移动端可用。

---

### 阶段 2：商品管理——"我发布的"页面（在卖 / 草稿 / 已下架）

#### T2.1 "我发布的"页面与入口
- **需求**：新页面 `my-products.html`，**三栏 Tab**：
  - **在卖**：当前在售商品（status=AVAILABLE，含 RESERVED 交易中并打标）；可**编辑**（含改价格）、**下架**、设置**砍一刀**、设置**智能回复**；
  - **草稿**：未发布草稿（status=DRAFT）；可**继续编辑**并**发布**、可删除；
  - **已下架**：下架（OFF_SHELF）与已售（SOLD）商品；可**重新发布**（SOLD/OFF_SHELF → AVAILABLE）、可**编辑**、设置**智能回复**。
  - 三栏均展示：图、标题、价格、状态标签、浏览/收藏数、操作按钮。
- **入口（"我的服务"宫格）**：在 `profile.html` 的 `.profile-grid` 中，于"我卖出的"与"发布闲置"之间新增一个 `.profile-item` 卡片，样式与其它入口一致：
  ```html
  <a class="profile-item" href="/my-products.html">
    <span class="profile-icon">▦</span>
    <span>我发布的<small>管理在卖、草稿与已下架商品</small></span>
  </a>
  ```
  - 与顶部导航"发布"、个人中心其它入口风格统一，衔接自然。
- **涉及文件**：`my-products.html`（新建）、`app.js` 新增 `initMyProducts()`、`profile.html` 加入口、`ProductController.mine` 支持 `status` 过滤。
- **验收标准**：① 入口出现在"我的服务"宫格且样式一致；② 三栏数量与状态过滤正确；③ 只显示当前用户商品；④ 各操作按钮可用且与后端联动；⑤ 空态提示"去发布"。

#### T2.2 编辑并保存商品（含价格）
- **需求**：`PUT /api/products/{id}`（仅所有者），从"在卖/草稿/已下架"进入编辑页（复用发布页并预填：标题/分类/成色/价格/原价/图片/砍价配置/智能回复/地点），保存后更新 `updated_at`。
- **验收标准**：修改标题/价格/图片/参数/砍价/回复后保存生效；非所有者 403；编辑不改变浏览量/收藏数/已产生的砍价记录。

#### T2.3 上下架 / 重新上架 / 草稿转发布
- **需求**：`PATCH /api/products/{id}/status` 支持状态迁移（校验 sellerId）：
  - 在卖 → 下架（AVAILABLE → OFF_SHELF）；
  - 已下架/已售 → 重新发布（OFF_SHELF/SOLD → AVAILABLE）；
  - 草稿 → 发布（DRAFT → AVAILABLE）；
  - 已售商品重新发布时，可选**清空历史砍价记录并重置 cut_count=0**（避免带"已砍 N 次"标签重新卖）。
- **验收标准**：状态迁移后首页/搜索可见性正确；越权 403；管理员端上下架不受影响。

---

### 阶段 3：智能回复（全局默认 + 单品设置，接入咨询自动回复）

> 背景：咨询会话由 user1 的 `ChatController` 驱动。本模块负责**回复内容的配置**与**自动触发逻辑**；与 user1 协作在 `ChatController.send`（买家发消息时）调用 `AutoReplyService.maybeAutoReply(...)` 插入一条"卖家（自动）"消息。需要给 `ChatMessage` 增加 `auto_reply` 标记字段（与 user1 协商，字段加在 user1 的实体上，属于小范围协作改动）。

#### T3.1 全局默认回复（所有宝贝）
- **需求**：卖家可开启/关闭"所有宝贝默认回复"并编辑默认回复内容；保存到 `auto_reply_setting`（一用户一行）。
- **内容能力（我完善）**：
  - 预设模板库（可选即用）：如「你好呀，这个{商品标题}还在哦，{商品价格}，可以小刀～」「在的！有什么想问的都可以问我哦」等 3–5 条 + 支持自定义；
  - **模板变量**：`{昵称}`、`{商品标题}`、`{商品价格}`，保存时校验、发送时替换；
  - 触发规则：**买家发起咨询时自动回复一次**（新会话创建或买家发首条消息），每个买家对同一商品默认只自动回复一次。
- **界面布局（设置页/弹窗）**：
  ```
  ┌─ 智能回复设置（全局）───────────────┐
  │ 所有宝贝默认回复  [ 开 / 关 ]        │
  │ 默认回复内容 textarea（≤500字）      │
  │ [模板A][模板B][模板C] ｜ 变量：{昵称}{商品标题}{价格}│
  │ [保存]                             │
  └───────────────────────────────────┘
  ```
- **涉及文件**：`AutoReplySetting` 实体 + Repository + `AutoReplyController`（`GET/PUT /api/auto-reply/global`）；入口可放"我发布的"顶部或 `settings.html`。
- **验收标准**：开关与内容保存/回显正确；模板可一键填入；买家咨询时按全局内容自动回复一条。

#### T3.2 单品智能回复
- **需求**：在"我发布的·在卖/已下架"商品上可单独开启/关闭该商品的智能回复并编辑内容（覆盖全局）；不设置则继承全局。
- **界面布局（商品操作弹窗）**：
  ```
  ┌─ 智能回复 · {商品标题} ──────────────┐
  │ 单品开启  [ 开 / 关 ]（关=继承全局）    │
  │ 回复内容 textarea（留空=用全局默认）    │
  │ [保存] [恢复全局默认]                │
  └───────────────────────────────────┘
  ```
- **涉及文件**：`Product` 加 `auto_reply_enabled/auto_reply_text`；`ProductController` 或独立端点 `GET/PUT /api/products/{id}/auto-reply`；`my-products.html` 操作按钮。
- **验收标准**：单品开关/内容独立生效；"恢复全局默认"清空单品覆盖；优先级（单品 > 全局 > 不回复）正确。

#### T3.3 自动回复触发接入（与 user1 协作）
- **需求**：在咨询消息发送链路中插入自动回复：
  - 触发点：`ChatController.send`（买家发消息）与/或 `POST /api/chat/product/{id}`（发起会话）之后，调用 `AutoReplyService.maybeAutoReply(conversationId, senderId)`；
  - 逻辑：`senderId` 为买家（非卖家）时，取该商品卖家的回复配置（单品 → 全局 → 无），若命中且该买家未收到过该商品的自动回复，则插入一条 `ChatMessage`（senderId=卖家、`autoReply=true`、content=模板替换后的文本），并更新会话 `updatedAt`；
  - 前端展示：自动回复消息带"自动回复"小标签；买家端可提示"卖家设置了自动回复"。
- **涉及文件**：`AutoReplyService`（user2）、`ChatMessage` 加 `autoReply` 字段 + `ChatController.send` 一行调用（与 user1 协商合并）。
- **验收标准**：买家发起咨询后自动收到一条卖家自动回复；每个买家每商品只触发一次；关闭回复后不再触发；模板变量正确替换；自动回复计入未读、会话列表正常。

---

### 阶段 4：既有扩展与总结（保留 V1 不冲突内容，按依赖顺序）

- [x] **T4.1 分类表 + 分类树 + 首页分类 Tab（V1·T2.1）**
- **需求**：新建 `category` 表并预置 6 个一级分类；`GET /api/categories` 返回分类树；首页分类 Tab 和发布页分类下拉改为从接口读取（不再写死）。
- **涉及文件**：`Category` 实体 + `CategoryRepository` + `CategoryController`；`DemoDataInitializer` 补分类种子；`index.html` / `publish.html` 渲染逻辑。
- **验收标准**：分类 Tab 与发布下拉数据一致；新增分类后页面自动出现；点 Tab 正确筛选。

- [x] **T4.2 搜索增强（分页 + 多条件 + 教材检索 + 热搜）（V1·T2.2）**
- **需求**：① 列表接口分页（`page/size`）；② 前端增加价格区间输入（服务端已支持）；③ 教材 ISBN/课程号/课程名参与匹配（字段 `isbn/course_code/module_name`）；④ 热搜词 TOP10 展示。
- **验收标准**：分页正确不重不漏；价格区间生效；输入 ISBN/课程号能搜到对应教材；组合条件结果正确。

- [x] **T4.3 商品参数 / 分类动态表单（V1·T2.3）**
- **需求**：发布时按分类显示不同参数项（教材→ISBN/课程号/课程名；服饰→品牌/尺码/颜色/性别；数码→品牌/型号；骑行→品牌/尺寸/运动类型），参数存 `product` 扩展字段，详情页"商品参数"区回显。
- **验收标准**：切换分类字段正确显隐；参数发布后详情页回显。

- [x] **T4.4 最近浏览 / 足迹（V1·T2.5）**
- **需求**：登录用户浏览详情自动记录（`product_browse`），提供"最近浏览"列表页（倒序、去重、可清空）。
- **验收标准**：浏览记录出现在足迹；重复浏览只更新时间；清空后空态。

- [x] **T4.5 擦亮与置顶（V1·T3.1）**
- **需求**：卖家可"擦亮"（`sharpened_at=now`，列表加权排序，可限每日次数）与"置顶"（`top_expire_at` 内置顶优先）。
- **验收标准**：擦亮后排序提前；置顶期内最前、到期自动失效。

- [x] **T4.6 相似商品推荐（V1·T3.2）**
- **需求**：详情页底部"为你推荐"（同分类 + 标题相似度，复用比价算法），排除自身/下架，最多 8 条。
- **验收标准**：推荐合理；不含自身与已下架。

- [x] **T4.7 成色详情与议价标记（V1·T3.3；注意与"砍一刀"区分）**
- **需求**：发布页"成色说明/瑕疵"输入（`condition_detail`）与"可议价"开关（`bargain_enabled`）；列表/详情展示议价标识与成色等级。**议价=可协商标记；砍一刀=随机降价功能，二者独立。**
- **验收标准**：可填可议价；列表出现"可议价"角标；详情展示成色说明。

- [x] **T4.8 图片治理（V1·T3.4）**
- **需求**：编辑删除图片时同步删除本地文件（复用 `deleteAvatarFile`）；单张 ≤5MB、最多 5 张；可选压缩。
- **验收标准**：删除商品/图片后 `uploads/` 文件被清理；超限拦截。

- [x] **T4.9 演示数据 + 测试 + 文档 + 分支管理（V1·T3.5）**
- **需求**：① `DemoDataInitializer` 补充多图/ISBN/课程号/砍价/POI/参数的演示商品；② 新增接口补测试（参考 `OrderControllerIntegrationTests`，H2）；③ 更新 README；④ `docs/daily` 记录迭代、`user2` 分支提交、PR 合并。
- **验收标准**：全新数据库启动即可演示；`./mvnw test` 全绿；README 与实现一致。

---

### 阶段 5：体验升级与收尾（智能回复升级 / AI 商品描述 / 全量自动化测试）【新增 V3】

> 三块相互独立，建议执行顺序：**5A 智能回复体验升级 → 5B Spring AI 商品描述优化 → 5C 全量自动化测试与测试文档**。
> 数据模型：**本阶段不新增数据表**——智能回复复用 `auto_reply_settings` 与 `product.auto_reply_*`；AI 限流用内存令牌桶；测试用 H2 内存库。每子任务仍遵循"可独立提交、可验收"。

#### 5A 智能回复体验升级

**T5.1 全局智能回复一键开关（页内常驻面板 + Toggle 开关）**
- **需求**：把"我发布的"顶部「⚙ 智能回复设置」弹窗升级为**页内常驻面板**（三栏 Tabs 正上方）；"所有宝贝默认回复"由勾选框改为 **Toggle 开关**（iOS 风格滑动按钮）；面板开启时高亮并展示内容摘要与"已应用到 N 个在卖商品"状态。
- **交互**：Toggle 开 → 展开内容编辑区（textarea ≤500 字 + 快捷模板 chips + 变量说明 + **实时预览**，预览即时渲染 `{昵称}{商品标题}{商品价格}`）；Toggle 关 → 收起编辑区，面板显示"未开启，买家咨询不会自动回复"。
- **界面布局**：
  ```
  ┌─ 智能回复（我的发布 · 顶部常驻面板）─────────────────────┐
  │ 所有宝贝默认回复         [ Toggle 开/关 ]                  │
  │ 开启后买家咨询自动回复；已应用到 N 个在卖商品               │
  │ ┌ 展开区 ────────────────────────────────┐               │
  │ │ 默认回复内容（≤500字） textarea         │               │
  │ │ [模板A][模板B][模板C] 变量 {昵称}{商品标题}{商品价格}   │
  │ │ 预览：你好呀，{昵称}！……                │               │
  │ │ [保存] [应用到全部在卖商品]             │               │
  │ └────────────────────────────────────────┘               │
  └─────────────────────────────────────────────────────────┘
  ```
- **涉及文件**：`my-products.html`（面板区）、`app.js`（initMyProducts 智能回复区重写）、`app.css`（新增通用 `.switch` Toggle 样式：原生 checkbox + label::after 圆钮，支持键盘聚焦与深色主题）。
- **验收标准**：开关为 Toggle 而非勾选；开/关/内容保存回显正确；刷新后保持；展开收起动画流畅；深色主题配色正确。

**T5.2 一键应用到全部在卖商品**
- **需求**：面板内新增"应用到全部在卖商品"按钮：把当前全局回复内容批量写入每个 `AVAILABLE` 商品的单品配置（`auto_reply_enabled=true`、`auto_reply_text=全局内容`），实现"一键设置所有商品"。
- **策略**：默认**仅应用到未单独设置过的商品**；勾选"覆盖已单独设置的商品"则全量覆盖。
- **接口**：`POST /api/auto-reply/apply-all`，body `{ "overwrite": false }`；返回 `{ "applied": n, "skipped": m, "overwritten": k }`；事务内批量写入（`AutoReplyService.applyAll`），异常整体回滚。
- **涉及文件**：`AutoReplyController` + `AutoReplyService`、`my-products.html` / `app.js`。
- **验收标准**：一键后所有在卖商品卡片出现"自动回复·已开"徽标；未单独设置过的被写入；overwrite=true 覆盖已设置项；事务异常不产生半写入（测试断言）。

**T5.3 单品智能回复开关升级（Toggle + 状态徽标 + 继承提示）**
- **需求**：商品卡片的"智能回复"按钮升级为带状态徽标的操作行；单品弹窗内开关改 Toggle；关闭单品 = 继承全局（弹窗内显示"当前继承全局：{全局内容前 30 字}…"）；提供"恢复全局默认"按钮。
- **状态徽标规则**：单品已开 → `自动回复·已开`（绿）；单品未开且全局开 → `自动回复·继承全局`（灰）；两者都关 → 不显示。
- **涉及文件**：`my-products.html` 卡片渲染、`app.js`、`app.css`（徽标样式）。
- **验收标准**：Toggle 交互正确；徽标与后端状态一致；"恢复全局默认"清空单品覆盖后徽标变为继承态。

**T5.4 触发流程补强（自动回复标签 + 买家预填提示 + 一次性规则）**
- **需求**：① 聊天窗口自动回复消息样式补强（"自动回复"小标签 + 浅色气泡，不混淆真人消息）；② 买家点"咨询"进入会话时，若卖家开启自动回复，输入框上方显示**提示条**（"卖家开启了自动回复，可直接提问"，不强行插入内容）；③ `AutoReplyService` 增加 `replyOnce` 规则（默认 true：每买家每商品仅自动回复一次；false 时买家追问可再回一条）。
- **涉及文件**：`AutoReplyService`（`replyOnce` 读全局设置或常量开关）、聊天前端（chat 相关页面标签样式）。
- **验收标准**：自动回复消息带标签且样式可辨；预填提示条出现且不干扰输入；默认每买家每商品一条；改 false 后追问可再触发。

#### 5B Spring AI 免费接入：AI 一键优化商品描述

**T5.5 依赖与 Provider 免费配置（Ollama 本地主方案 / 智谱 GLM-4-Flash 备选）**
- **需求**：引入 Spring AI，支持两种免费 Provider 二选一：
  - **主方案 · Ollama 本地模型**：完全免费、离线可用（答辩无网络也能演示）。`pom.xml` 加 `spring-ai-starter-model-ollama`；`application.properties`：`spring.ai.ollama.base-url=http://localhost:11434`、`spring.ai.ollama.chat.options.model=qwen2.5:3b`（3B 模型轻量够用）。
  - **备选 · 智谱 GLM-4-Flash**（OpenAI 兼容、官方免费额度）：加 `spring-ai-starter-model-openai`，`spring.ai.openai.base-url=https://open.bigmodel.cn/api/paas/v4`、`api-key`、`model=glm-4-flash`。
  - **版本兼容注意**：项目为 Spring Boot 4.1.1，若官方 spring-ai starter 版本与 Boot 4 暂不兼容，**降级方案**为不依赖 spring-ai、自研约 40 行的 OpenAI 兼容 HttpClient 客户端（`POST {base}/chat/completions`，messages 拼 prompt），对外接口与 Prompt 逻辑不变——保证"免费接入"始终可落地。
  - **总开关**：`app.ai.desc.enabled=false` **默认关闭**，且**默认跟随"AI 搜索"同一套配置**（`AI_SEARCH_ENABLED/AI_BASE_URL/AI_API_KEY/AI_MODEL`）——只要给 AI 搜索配过一次 key，发布页 AI 描述优化开箱即用，无需单独配置；也可用 `AI_DESC_*` 前缀单独覆盖（优先级更高）。Provider 按 base-url 自动推断（`open.bigmodel.cn → glm`、`localhost/127.0.0.1 → ollama`、其他 → `custom`），本地服务免 key、在线服务必须携带 key。`GET /api/ai/status` 返回 `{ enabled, provider, model }`，前端据此决定按钮是否可点。
- **涉及文件**：`application.properties`、`config/AiConfig`（配置类）、`scripts/start-ollama.bat`（新增：检测/提示安装 Ollama 并 `ollama pull qwen2.5:3b`）、README。
- **验收标准**：默认关闭不影响构建与既有测试全绿；打开后 `/api/ai/status` 正确返回；README 按步骤可启用任一 Provider。

**T5.6 AI 优化服务与接口（降级不阻塞）**
- **需求**：`AiDescriptionService`（Spring AI `ChatClient` 或降级 HttpClient）：
  - **Prompt 组装**：System「你是校园二手交易平台的商品描述润色助手。基于用户填写的信息润色为自然、口语化、真实可信的描述；不得虚构参数；突出成色与卖点；≤200 字；不得添加营销夸张词」；User 拼「标题/分类/参数(键值)/成色/价格/原描述」。
  - **返回解析**：取文本；空或超 200 字截断；3 秒超时。
  - **降级**：未配置/超时/异常一律返回 `{ optimized: null, reason: "AI 服务不可用，请手动填写" }`，**绝不阻塞发布**。
  - **防滥用**：登录用户 10 秒限 1 次（内存令牌桶）。
- **接口**：`POST /api/ai/optimize-description`（body：商品表单字段，服务端重新校验长度）；`GET /api/ai/status`。
- **涉及文件**：`AiDescriptionController`、`AiDescriptionService`、`AiConfig`（配置类）。
- **验收标准**：配置开启时返回润色文本且不含虚构参数；未配置返回明确降级提示；限流生效；无 AI 时发布流程完全不受影响。

**T5.7 发布页"AI 优化描述"按钮**
- **需求**：发布/编辑页描述 textarea 右上角加「✨ AI 优化描述」按钮：点击把已填表单（标题/分类/参数/成色/描述）提交接口；请求中按钮变「润色中…」并禁用；成功后描述填入 textarea 并聚焦（**可继续手动修改**）；失败 toast 提示、原描述保留。
- **界面布局**：
  ```
  商品描述 textarea（span-2）
  [ ✨ AI 优化描述 ]  ← textarea 右上角；灰=未开启AI/未登录
  例："捷安特山地车"+分类"骑行装备"+成色"九成新" → 润色后自动填入
  ```
- **涉及文件**：`publish.html`、`app.js`（initPublish 增加 AI 按钮逻辑）。
- **验收标准**：按钮位置自然、与表单风格统一；loading 态；成功替换且可编辑；失败不丢原文；替换后用户可选择恢复原文；`app.ai.enabled=false` 时按钮置灰并提示"AI 服务未配置"。

**T5.8 AI 功能测试与 README 配置说明**
- **需求**：新增 `AiDescriptionServiceTests`（**不依赖真实模型/外网**：mock ChatClient 或直接测降级分支）；README 增加"AI 商品描述（可选）"章节（Ollama 安装、GLM key 申请、开关说明）。
- **验收标准**：测试离线全绿；README 照做即可启用。

#### 5C 全量自动化测试与测试文档

**T5.9 现有测试盘点（写入测试文档）** ✅ 已完成（2026-09-10）
- **需求**：按模块盘点现有测试类与测试方法，逐条标注运行命令，写入 `docs/tests/TESTING.md`。
- **盘点结果（全量核对后最新基线：16 类 / 102 用例）**：
  | 测试类 | 覆盖模块 | 方法数 |
  |---|---|---|
  | `StudentBayApplicationTests` | 上下文加载 | 1 |
  | `DemoDataInitializerIntegrationTests` | 种子数据幂等 | 4 |
  | `OrderControllerIntegrationTests` | 订单生命周期/分拆/自提（已补事务回滚） | 5 |
  | `Day5OrderWorkflowIntegrationTests` | 支付幂等/超时取消/退款/后台审核 | 12 |
  | `ProductStage4ControllerIntegrationTests` | 分类树/分页/教材检索 | 3 |
  | `Day6TrustAiPaymentIntegrationTests` | 自然语言搜索/信任分/风控/沙箱 | 5 |
  | `BusinessFlowWebAutomationTests` | 端到端主流程（Web） | 5 |
  | `WebPageAutomationTests` | 页面结构/交互（Web，队友扩充） | 22 |
  | `AiDescriptionServiceTests` / `AiDescriptionServiceEnabledTests` | AI 描述离线降级/限流 | 4+2 |
  | `SpaceCreditIntegrationTests` | 空间积分钱包/抵扣/流水 | 7 |
  | `ReviewFlowIntegrationTests` | 评价流程 | 4 |
  | `AccountSettingsIntegrationTests` / `AccountEmailDomainIntegrationTests` | 账号设置/邮箱域校验 | 4+5 |
  | `ProductCenterFullIntegrationTests`（新增） | 商品中心全链路 | 11 |
  | `PlatformGapIntegrationTests`（新增） | 购物车/留言/收藏/积分/后台/地理 | 8 |
- **涉及文件**：`docs/tests/TESTING.md`（已更新为 16 类 / 102 用例成稿）。
- **验收标准**：✅ 盘点与实际代码一致（102 用例全绿）。

**T5.10 商品中心全链路测试补充（核心）** ✅ 已完成（2026-09-10）
- **需求**：新增 `ProductCenterFullIntegrationTests`（H2、`@SpringBootTest`，参照 `ProductStage4ControllerIntegrationTests`），覆盖 user2 全链路：
  1. 发布校验：缺标题/价格/地点 → 400；正常发布 → AVAILABLE、viewCount=0；
  2. 草稿：保存草稿 → 草稿栏可见 → 编辑后发布 → 状态变更；删除草稿；
  3. 上下架：下架 → OFF_SHELF 不出现在公开列表；重新上架 → AVAILABLE；
  4. 编辑：改价格/标题/图片列表，旧图被移除；
  5. 图片上传：multipart 白名单（类型/5MB）校验；
  6. 砍一刀：开启区间 → 砍价成功、重复 409；砍后价格仅本人可见；
  7. 智能回复：全局保存（空内容开启被拒）、单品覆盖、触发一次、变量替换、关闭不触发、apply-all 批量结果；
  8. 擦亮/置顶：每日限次 409、置顶排序优先、到期失效；
  9. 最近浏览：去重/倒序/清空；
  10. 相似推荐：排除自身与下架、≤8 条；
  11. 热搜：搜索计数、TOP10。
- **涉及文件**：`src/test/java/com/campus/trade/controller/product/ProductCenterFullIntegrationTests.java`（新建）。
- **验收标准**：`./mvnw test` 全绿；每个场景独立测试方法、断言明确。

**T5.11 平台其他模块缺口补测** ✅ 已完成（2026-09-10）
- **需求**：按覆盖矩阵补齐非商品中心缺口：注册/登录/登出会话、购物车（加购/合并/清空）、聊天 + 自动回复端到端、收藏/取消收藏/举报、后台商品管理与统计接口。
- **实现**：`PlatformGapIntegrationTests` 共 **12 个方法**：购物车（加购/去重/删除/计数）、商品留言（发表/列表/删除权限）、自然语言搜索、收藏切换、POI 逆查、积分任务（签到/游戏/重复领取 409）、后台看板与用户列表（普通用户 403）、地理编码（正/逆，无 Key 降级）、举报提交与管理员处理（含处理时下架商品）、后台商品管理（列表/改状态）、聊天会话（卖家箱/未读/已读回执）、会话生命周期（注册→登录→登出 401）。
- **验收标准**：✅ 全绿；覆盖矩阵勾满（模块 × 功能 × 测试类 × ✓，见 TESTING.md 第 5 节）。

**T5.12 测试文档 `docs/tests/TESTING.md` 成稿** ✅ 已完成（2026-09-10）
- **需求**：完整可复现文档，结构：
  1. **环境与准备**：JDK 21 / Maven（mvnw）/ 测试用 H2 内存库（不依赖本机 MySQL 启动）/ 演示账号；
  2. **一键运行**：`./mvnw test`（Windows 用 `mvnw.cmd test`）；单类：`./mvnw test -Dtest=类名`；单方法：`./mvnw test -Dtest=类名#方法名`；
  3. **测试类清单**：每个类的路径、作用、测试方法列表、运行代码；
  4. **覆盖矩阵**：模块 × 功能 × 测试类 × 状态；
  5. **新增测试设计说明**（H2、无网络依赖、幂等）；
  6. **常见问题**（测试与 8081 服务端口冲突、MySQL 未启动不影响、Ollama 未装不影响 AI 测试等）。
- **涉及文件**：`docs/tests/TESTING.md`（最终成稿）。
- **验收标准**：✅ 文档与代码一致（16 类 / 106 用例全绿）；按文档可一键复现全部测试；覆盖矩阵覆盖全部业务模块。

---

## 7. 关键页面界面布局说明

### 7.1 发布页 `publish.html`（阶段 1 改造后）
```
页面标题：发布校园闲置（Eyebrow: Publish）
┌─ 卡片表单（.shell.narrow 两列网格）────────────────────────┐
│ 商品标题            （span-2）                              │
│ 分类 ▾    |    成色 ▾    （两列）                           │
│ 售价（元）|    原价（元）（两列）                            │
│ 动态参数区：根据分类显示（教材/服饰/数码/骑行…） span-2       │
│ 商品图片 九宫格（本地上传/粘贴链接/拖拽/Ctrl+V，≤5张，主图）span-2│
│ 商品描述 textarea（span-2）                                │
│ 可议价 ☑   |   砍一刀 [关|1-3元|3-5元|自定义]  （span-2 一行）│
│ 交易地点名称 联想选择 + 📍定位（span-2）                     │
│ 在地图上选择位置（390px；选地点↔点地图双向同步）  （span-2）    │
│ [ 保存草稿 ] [ 确认发布 ]（span-2，btn-block 并排）          │
└──────────────────────────────────────────────┘
```
- 已修复点：Leaflet CSS/JS 引入正确、瓦片用高德（见 §3）。

### 7.2 首页搜索/筛选区 `index.html`（阶段 4）
- 现状：`keyword + category + condition + sort` 四字段一行。
- 扩展后：增加"价格区间（min–max）"，关键词框聚焦浮出"历史搜索 + 热搜词"；分类 Tab（category-rail）改由 `/api/categories` 渲染。
- 排序选项：最新 / 价格升 / 价格降（保留），可选新增"擦亮优先"。

### 7.3 商品详情 `product-detail.html`（阶段 1/4）
- 媒体区：大图 + 底部缩略图条（多图切换）。
- 信息区：状态、标题、分类/成色、**现价（划线原价）+ 砍一刀按钮 + 已砍次数**、可议价标识、浏览/收藏/留言数、位置（POI 名称 + 高德小地图）、卖家卡片。
- 新增"商品参数"区：品牌/尺码/材质/颜色/ISBN/课程号等（T4.3）。
- 底部：留言列表 + 相似推荐（T4.6）。
- 砍价动态：点"砍一刀"→ 数字滚动动画 + toast"砍价成功 -¥x.xx" → 现价更新、按钮按剩余次数更新状态。

### 7.4 我的发布 `my-products.html`（新建，阶段 2）
```
我发布的（N 件）  [ 在卖 | 草稿 | 已下架 ]     ← 三栏 Tab
┌─────────────────────────────────────────────┐
│ [图] 标题…        ¥xx    [在售/交易中]  浏览12·收藏3 │
│      操作：编辑 | 下架 | 砍一刀 | 智能回复        │
├─────────────────────────────────────────────┤
│ （草稿栏）[图] 未发布草稿…   [继续编辑] [发布] [删除]│
├─────────────────────────────────────────────┤
│ （已下架栏）[图] 标题… ¥xx [已下架/已售] [重新发布][编辑][智能回复]│
└─────────────────────────────────────────────┘
顶部：全局智能回复入口「⚙ 智能回复设置（默认回复）」
```
- 空态：暂无发布商品 → 去发布。

### 7.5 智能回复设置（阶段 5 升级）
- **全局**："我发布的"顶部**页内常驻面板**（T5.1）：Toggle 开关 + 内容编辑 + 快捷模板 + 实时预览 + **一键应用到全部在卖商品**（T5.2）。
- **单品**：商品卡片"智能回复"按钮 + 状态徽标（已开 / 继承全局）→ 弹窗 Toggle 编辑（T5.3）。
- **聊天侧**：自动回复消息带"自动回复"标签；买家进入会话有预填提示条（T5.4）。

### 7.6 发布页"AI 优化描述"（阶段 5）
```
商品描述 textarea（span-2）
[ ✨ AI 优化描述 ]  ← textarea 右上角；灰=未配置AI/未登录
点击 → 润色中… → 结果填入 textarea（可继续手动修改）
```
- 按钮样式与发布页统一；失败 toast 且不丢原文（T5.7）。

### 7.7 测试文档（阶段 5）
- 交付物：`docs/tests/TESTING.md`（环境准备 / 一键与单类单方法运行命令 / 测试类清单 / 覆盖矩阵 / 常见问题）。
- 新增测试类：`ProductCenterFullIntegrationTests`（商品中心全链路）、`PlatformGapIntegrationTests`（平台缺口补测）、`AiDescriptionServiceTests`（AI 降级逻辑，离线可测）。

---

## 8. 风险与注意事项

| 风险/注意 | 说明与对策 |
|---|---|
| 国内网络 CDN/瓦片 | 已切 jsdelivr + 高德瓦片；演示前务必联网自检一次；彻底方案是把 Leaflet 下载到 `static/vendor/` 本地引用 |
| 图片上传安全 | 复用头像上传白名单（jpg/jpeg/png/webp/gif）+ 5MB；文件名 `product-{userId}-{ts}.{ext}` 防覆盖/路径穿越；校验文件名不含 `..`；远端链接只存 http(s)/`/uploads/` 开头 |
| 砍价一致性与并发 | 砍价接口加事务；`price_cut_record` 唯一约束 `(product_id, buyer_id)` 防重复；用 `cut_count < cut_limit` + 行锁/乐观校验防超砍；价格下限保护（≥¥1 且 ≥ 原价 50%） |
| 砍价与订单价格 | 砍后现价 = 商品挂牌价；下单以砍后价为准（与 user1 订单快照字段衔接时确认）；已售/交易中禁止砍 |
| 智能回复与模块边界 | 自动回复消息插入点在 user1 的 `ChatController.send`，需协商在 `ChatMessage` 加 `auto_reply` 字段并加一行 `AutoReplyService` 调用；勿破坏 user1 会话逻辑 |
| 越权 | 所有"卖家本人"操作（编辑/上下架/重新发布/砍价配置/智能回复）必须校验 `sellerId == currentUser.id`，否则 403；砍价接口校验买家非卖家本人 |
| XSS | 前端统一 `esc()` 转义渲染；智能回复/模板、地点名称等用户内容入库与渲染均转义 |
| 定位/地理 | `navigator.geolocation` 需 HTTPS（localhost 可豁免），未授权时降级隐藏定位按钮；POI 反查距离算法用 Haversine 即可 |
| 数据库迁移 | `ddl-auto=update` 自动加列；`category`/`campus_poi` 需在 `DemoDataInitializer` 补种子；老库瓦片地址由 `repairLegacyTileUrl()` 自愈 |
| 多图字段一致性 | `images[0]` 与 `imageUrl` 保持同步，避免列表（imageUrl）与详情（images）不一致 |
| 草稿可见性 | 所有列表/搜索/推荐/比价查询必须排除 `DRAFT`，仅"我发布的·草稿"可见 |
| AI 服务可用性 | 免费方案依赖 Ollama 本地（需安装+下载模型）或 GLM 免费 key；`app.ai.enabled` 默认关闭，未配置时发布页按钮置灰；AI 超时/异常一律降级提示，绝不阻塞发布（T5.6） |
| AI 描述真实性 | System Prompt 约束"不虚构参数、基于填写信息润色"；前端可再编辑；≤200 字截断；spring-ai 与 Boot 4.1 版本不兼容时采用自研 OpenAI 兼容客户端降级（T5.5） |
| Toggle 样式兼容 | 自定义 Toggle 基于原生 checkbox（`appearance:none` + label 圆钮），键盘可聚焦（tabindex+Enter 切换），深色主题有配色；不引入第三方组件库 |
| 测试可复现性 | 测试用 H2 内存库 + `@SpringBootTest`，不依赖外网/真实 AI/本机 MySQL 启动；`./mvnw test` 一键复现；覆盖矩阵与 TESTING.md 一致（T5.9–T5.12） |

---

## 9. 总验收清单（Checklist）

- [x] **阶段0** 发布页地图 Bug 修复（Leaflet CSS integrity + 瓦片源 + CDN），编译与浏览器实测通过
- [ ] T1.1 图片上传多方式（本地/链接/拖拽/Ctrl+V，≤5 张，主图）
- [ ] T1.2 交易地点 × 地图联动（POI 联想 + 反查 + 定位，双向同步）
- [ ] T1.3 砍一刀（区间配置 + 随机降价 + 详情页价格动态 + 防刷规则）
- [ ] T1.4 保存草稿与草稿转发布
- [ ] T1.5 发布表单校验与交互完善
- [ ] T1.6 商品详情多图展示
- [x] T2.1 "我发布的"页面（在卖/草稿/已下架三栏）+ 我的服务入口
- [x] T2.2 编辑并保存商品（含价格）
- [x] T2.3 上下架 / 重新上架 / 草稿转发布
- [x] T3.1 智能回复·全局默认（开关 + 模板 + 变量）
- [x] T3.2 智能回复·单品设置（覆盖全局）
- [x] T3.3 智能回复自动触发接入咨询（与 user1 协作）
- [x] T4.1 分类表 + 分类 Tab
- [x] T4.2 搜索增强（分页/价格区间/教材检索/热搜）
- [x] T4.3 商品参数动态表单
- [x] T4.4 最近浏览足迹
- [x] T4.5 擦亮/置顶
- [x] T4.6 相似推荐
- [x] T4.7 成色详情/议价标记
- [x] T4.8 图片治理
- [x] T4.9 演示数据/测试/文档/分支
- [x] T5.1 全局智能回复页内面板 + Toggle 开关
- [x] T5.2 一键应用到全部在卖商品
- [x] T5.3 单品 Toggle + 状态徽标 + 继承提示
- [x] T5.4 触发补强（自动回复标签/预填提示/一次性规则）
- [x] T5.5 Spring AI 依赖与免费 Provider 配置（Ollama/GLM + 降级方案）
- [x] T5.6 AI 优化服务与接口（降级不阻塞 + 限流）
- [x] T5.7 发布页"AI 优化描述"按钮
- [x] T5.8 AI 功能测试与 README 配置说明
- [ ] T5.9 现有测试盘点入档
- [ ] T5.10 商品中心全链路测试补充
- [ ] T5.11 平台其他模块缺口补测
- [ ] T5.12 TESTING.md 测试文档成稿

> 迭代顺序建议：阶段 0（已完成）→ 阶段 1（T1.1→T1.2→T1.3→T1.4→T1.5→T1.6）→ 阶段 2（T2.1→T2.2→T2.3）→ 阶段 3（T3.1→T3.2→T3.3）→ 阶段 4（按依赖顺序）→ 阶段 5（5A：T5.1→T5.2→T5.3→T5.4；5B：T5.5→T5.6→T5.7→T5.8；5C：T5.9→T5.10→T5.11→T5.12）。每完成一项在对应行打勾并提交 git。
