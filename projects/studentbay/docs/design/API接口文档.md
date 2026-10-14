# StudentBay API 接口文档

> 文档版本：Day9 / v1.0  
> Base URL：`http://localhost:8080`  
> 数据格式：除文件上传、支付宝通知和页面跳转外均为 JSON  
> 认证方式：服务端 HTTP Session Cookie

## 1. 通用约定

### 1.1 请求头

```http
Content-Type: application/json
Accept: application/json
```

登录成功后浏览器自动保存 Session Cookie。使用 Postman/curl 联调时应复用 Cookie：

```bash
curl -c cookie.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"lin@campus.edu","password":"123456"}'

curl -b cookie.txt http://localhost:8080/api/auth/me
```

### 1.2 统一响应

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

创建成功接口通常返回 HTTP `201`，响应体 `code` 也为 `201`。

失败：

```json
{
  "code": 409,
  "message": "当前订单状态无法申请退款",
  "data": null
}
```

| HTTP 状态 | 含义 |
|---|---|
| `200` | 查询或操作成功 |
| `201` | 资源创建成功 |
| `400` | 参数、文件或业务输入不合法 |
| `401` | 未登录或登录失效 |
| `403` | 已登录但无资源/角色权限 |
| `404` | 资源不存在 |
| `409` | 状态冲突、重复操作或资源已变化 |
| `500` | 服务器内部错误 |

### 1.3 时间、金额和枚举

- 日期：`yyyy-MM-dd`，如 `2026-09-08`；
- 时间：ISO-8601，如 `2026-09-08T17:30:00`；
- 金额：JSON number，数据库精度为两位小数；
- 页码从 `0` 开始；
- 枚举值使用大写英文字符串。

### 1.4 权限标记

| 标记 | 含义 |
|---|---|
| 公开 | 无需登录 |
| 登录 | 任意有效普通用户或管理员 |
| 参与方 | 必须是该订单买家或卖家 |
| 卖家 | 必须是商品/订单所属卖家 |
| 买家 | 必须是订单买家 |
| 管理员 | 必须为 `ADMIN` |

## 2. 认证与账户

### 2.1 认证接口

| 方法 | 路径 | 权限 | 请求/说明 |
|---|---|---|---|
| POST | `/api/auth/register` | 公开 | 注册并建立登录 Session |
| POST | `/api/auth/login` | 公开 | 邮箱密码登录 |
| GET | `/api/auth/me` | 登录 | 当前用户信息 |
| POST | `/api/auth/logout` | 登录 | 注销并失效 Session |

注册请求：

```json
{
  "email": "student@campus.edu",
  "password": "123456",
  "displayName": "校园同学",
  "role": "USER"
}
```

字段限制：邮箱合法；密码 6～64 位；显示名 2～30 位；`role` 为 `USER` 或 `ADMIN`。正式环境不应允许公开注册管理员，当前字段用于课程演示。

登录请求：

```json
{"email":"lin@campus.edu","password":"123456"}
```

用户响应主要字段：`id`、`email`、`displayName`、`avatarUrl`、`university`、`studentVerified`、`role`、`status`。

### 2.2 账户接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/account/addresses` | 登录 | 地址列表，默认地址优先 |
| POST | `/api/account/addresses` | 登录 | 新增地址 |
| PUT | `/api/account/addresses/{id}` | 登录/本人 | 修改地址 |
| DELETE | `/api/account/addresses/{id}` | 登录/本人 | 删除地址 |
| GET | `/api/account/coupons` | 登录 | 当前可用公共券和专属券 |
| PUT | `/api/account/profile` | 登录 | 修改昵称、邮箱、学校 |
| POST | `/api/account/avatar` | 登录 | 上传头像，`multipart/form-data` |
| PUT | `/api/account/password` | 登录 | 修改密码 |
| GET | `/api/account/bought` | 登录 | 聚合“我买入的”商品 |
| GET | `/api/account/activities` | 登录 | 发布/售出动态时间线 |

地址请求：

```json
{
  "recipientName": "林同学",
  "phone": "13800000000",
  "detail": "北校区 3 栋菜鸟驿站",
  "defaultAddress": true,
  "latitude": 23.164987,
  "longitude": 113.446112
}
```

资料请求：

```json
{"displayName":"林同学","email":"lin@campus.edu","university":"Nuit University"}
```

密码请求：

```json
{"oldPassword":"123456","newPassword":"new-password"}
```

头像字段名为 `file`，最大 5 MB，支持 `jpg`、`jpeg`、`png`、`webp`、`gif`。

## 3. 商品、分类与搜索

### 3.1 商品接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/products` | 公开 | 筛选、排序和分页商品 |
| GET | `/api/products/{id}` | 公开/草稿本人 | 商品详情；登录浏览者记录足迹 |
| GET | `/api/products/{id}/comparisons` | 公开 | 同类价格与相似度分析 |
| POST | `/api/products` | 登录 | 发布商品或保存草稿 |
| PUT | `/api/products/{id}` | 卖家 | 修改商品 |
| PATCH | `/api/products/{id}/status` | 卖家 | 上下架/状态更新 |
| DELETE | `/api/products/{id}` | 卖家 | 删除商品并清理本地图片 |
| POST | `/api/products/{id}/bargain` | 登录/非卖家 | 执行一次随机砍价 |
| GET | `/api/products/hot-keywords` | 公开 | Top 10 热搜词 |
| GET | `/api/products/browse/recent` | 登录 | 最近浏览 |
| DELETE | `/api/products/browse/clear` | 登录 | 清空浏览足迹 |
| POST | `/api/products/{id}/sharpen` | 卖家 | 擦亮商品 |
| POST | `/api/products/{id}/top` | 卖家 | 设置临时置顶 |
| GET | `/api/products/recommend?productId={id}` | 公开 | 同分类相似推荐 |
| GET | `/api/products/mine/list` | 登录 | 当前用户发布的商品 |
| POST | `/api/products/natural-search` | 公开 | 自然语言智能找商品 |

### 3.2 商品列表

`GET /api/products`

| Query 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `keyword` | string | 空 | 匹配标题、描述、ISBN、课程号、模块名 |
| `category` | string | 空 | 精确分类 |
| `condition` | string | 空 | 精确成色 |
| `minPrice` | decimal | 空 | 最低价 |
| `maxPrice` | decimal | 空 | 最高价 |
| `sort` | string | `newest` | `newest`、`priceAsc`、`priceDesc`、`sharpen` |
| `page` | int | `0` | 页码 |
| `size` | int | `20` | 每页 1～100 条 |

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [{"id":7,"title":"机械键盘","price":120.00,"status":"AVAILABLE"}],
    "total": 26,
    "page": 0,
    "size": 20
  }
}
```

### 3.3 发布/修改商品

核心请求示例：

```json
{
  "title": "87 键静音机械键盘",
  "description": "红轴，宿舍使用，支持当面验货",
  "category": "数码产品",
  "condition": "九成新",
  "price": 120.00,
  "originalPrice": 259.00,
  "imageUrl": "/uploads/example.webp",
  "images": ["/uploads/example.webp"],
  "locationName": "图书馆北门",
  "latitude": 23.164987,
  "longitude": 113.446112,
  "status": "AVAILABLE",
  "conditionDetail": "右侧键帽有轻微使用痕迹",
  "brand": "Keychron",
  "model": "K2",
  "bargainEnabled": true,
  "cutEnabled": true,
  "cutMin": 1.00,
  "cutMax": 5.00,
  "cutLimit": 3
}
```

可选扩展字段：`poiId`、`addressDetail`、五级结构化地址、`size`、`material`、`color`、`gender`、`sportType`、`isbn`、`courseCode`、`moduleName`、`autoReplyEnabled`（通过独立接口维护）。

状态修改请求：

```json
{"status":"OFF_SHELF","clearCut":false}
```

置顶请求：

```json
{"hours":24}
```

### 3.4 自然语言搜索

`POST /api/products/natural-search`

```json
{"query":"预算150元，想买适合宿舍用的安静键盘，最好能面交"}
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "query": "预算150元，想买适合宿舍用的安静键盘，最好能面交",
    "mode": "AI_ASSISTED",
    "interpretation": {
      "maxPrice": 150,
      "category": "数码产品",
      "condition": null,
      "preferPickup": true,
      "keywords": ["键盘","静音","宿舍"]
    },
    "products": []
  }
}
```

`mode=LOCAL_FALLBACK` 表示未调用外部模型或模型不可用，但本地规则仍可完成搜索。

### 3.5 分类、地点、地图与上传

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/categories` | 公开 | 启用分类树 |
| GET | `/api/pois?keyword=` | 公开 | 校园 POI 搜索 |
| GET | `/api/pois/nearest?lat=&lng=` | 公开 | 最近校园地点 |
| GET | `/api/regions` | 公开 | 行政区域 JSON |
| GET | `/api/geocode?address=` | 公开 | 地址转坐标 |
| GET | `/api/geocode/reverse?lat=&lng=` | 公开 | 坐标反查地址 |
| GET | `/api/map/config` | 公开 | 前端地图参数 |
| POST | `/api/uploads` | 登录 | 本地图片上传，字段 `file` |
| POST | `/api/uploads/by-url` | 登录 | 校验并登记图片 URL |

URL 图片请求：`{"url":"https://example.com/photo.jpg"}`。

### 3.6 商品留言

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/products/{productId}/comments` | 公开 | 留言列表 |
| POST | `/api/products/{productId}/comments` | 登录 | 添加留言 |
| DELETE | `/api/products/{productId}/comments/{commentId}` | 留言本人/有权用户 | 删除留言 |

留言请求：`{"content":"请问可以在图书馆面交吗？"}`，最多 500 字。

## 4. 收藏与购物车

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/favorites/{productId}/toggle` | 登录 | 切换收藏状态 |
| POST | `/api/favorites/{productId}` | 登录 | 添加收藏 |
| GET | `/api/favorites` | 登录 | 收藏商品列表 |
| GET | `/api/cart` | 登录 | 购物车列表 |
| POST | `/api/cart/{productId}` | 登录/非卖家 | 加入购物车 |
| DELETE | `/api/cart/{id}` | 登录/本人 | 删除购物车项 |
| GET | `/api/cart/count` | 登录 | 购物车计数 |

重复收藏、重复加购由唯一约束和服务逻辑转换为稳定结果。

## 5. 订单与结算

### 5.1 订单接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/order/checkout` | 登录/买家 | 购物车结算并按卖家拆单 |
| GET | `/api/order/{id}` | 参与方 | 订单详情 |
| GET | `/api/order/mine/buyer` | 登录 | 我的买家订单 |
| GET | `/api/order/mine/seller` | 登录 | 我的卖家订单 |
| POST | `/api/order/{id}/payment` | 买家 | 幂等支付 |
| POST | `/api/order/{id}/cancel` | 买家 | 取消未付款订单 |
| PATCH | `/api/order/{id}/price-adjustment` | 卖家 | 修改待付款商品金额和运费 |
| POST | `/api/order/{id}/courier-booking` | 卖家 | 快递订单预约取件 |
| POST | `/api/order/{id}/confirm-shipment` | 卖家 | 填运单并确认发货 |
| POST | `/api/order/{id}/confirm-pickup-handover` | 卖家 | 面交时验证买家码 |
| POST | `/api/order/{id}/confirm-receipt` | 买家 | 快递确认收货或验证卖家码 |
| GET | `/api/order/{id}/logistics` | 参与方 | 物流详情 |
| POST | `/api/order/{id}/logistics/refresh` | 参与方 | 推进/同步模拟物流 |

### 5.2 结算请求

快递：

```json
{
  "cartItemIds": [11, 12, 15],
  "addressId": 3,
  "shippingMethod": "DELIVERY",
  "couponId": 2,
  "paymentMethod": "ALIPAY",
  "useCoins": true,
  "pickupDate": null,
  "pickupStartTime": null,
  "pickupEndTime": null,
  "pickupLocation": null
}
```

校园面交：

```json
{
  "cartItemIds": [11, 12],
  "addressId": null,
  "shippingMethod": "PICKUP",
  "couponId": null,
  "paymentMethod": "WECHAT_PAY",
  "useCoins": false,
  "pickupDate": "2026-09-08",
  "pickupStartTime": "17:30:00",
  "pickupEndTime": "18:00:00",
  "pickupLocation": "Library North Gate"
}
```

返回值为订单数组，因为一次用户结算可能按 `sellerId` 拆成多张订单。

### 5.3 `OrderResponse` 主要字段

| 字段 | 说明 |
|---|---|
| `id`、`orderNo` | 订单标识 |
| `buyerId`、`sellerId` | 参与方 |
| `status`、`paymentStatus`、`shipmentStatus` | 三类状态 |
| `productAmount`、`shippingCost`、`discountAmount`、`coinDiscountAmount`、`totalAmount` | 金额明细 |
| `shippingMethod` | `PICKUP` 或 `DELIVERY` |
| `shippingAddress`、`recipientName`、`recipientPhone` | 配送快照 |
| `paymentExpiresAt`、`paidAt`、`cancelledAt` | 关键时间 |
| `pickupLocation`、`pickupMeetingStartAt`、`pickupMeetingEndAt` | 面交安排 |
| `myPickupCode` | 仅返回当前查看者自己的取件/交付码 |
| `sellerHandoverConfirmed`、`buyerReceiptConfirmed` | 双向确认状态 |
| `courierCompany`、`trackingNumber`、`estimatedDeliveryAt` | 快递信息 |
| `items` | 商品快照数组 |
| `myReviewSubmitted`、`counterpartyReviewSubmitted` | 双方评价状态 |

### 5.4 支付、改价和履约请求

支付：

```json
{"paymentMethod":"ALIPAY","idempotencyKey":"order-123-browser-uuid"}
```

同一个幂等键可安全重试；订单已支付后换用另一幂等键会返回 `409`。

改价：

```json
{"productAmount":115.00,"shippingCost":0.00}
```

只有 `PENDING_PAYMENT + UNPAID` 时可改价。

预约快递：

```json
{"courierCompany":"顺丰速运","pickupTime":"2026-09-08T18:30"}
```

确认发货：`{"trackingNumber":"SF1234567890"}`。

面交确认：`{"code":"123456"}`。卖家调用 `confirm-pickup-handover` 输入买家码，买家调用 `confirm-receipt` 输入卖家码。

## 6. 面交协商

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/order/{id}/pickup-proposals` | 参与方 | 协商历史 |
| POST | `/api/order/{id}/pickup-proposals` | 参与方 | 发起修改提议 |
| POST | `/api/order/{id}/pickup-proposals/{proposalId}/respond` | 指定回应方 | 接受或拒绝 |

提议请求：

```json
{
  "meetingStartAt":"2026-09-08T17:30:00",
  "meetingEndAt":"2026-09-08T18:00:00",
  "location":"图书馆北门"
}
```

回应请求：`{"decision":"ACCEPT"}` 或 `{"decision":"REJECT"}`。

响应中的 `conflictWarning` 非空表示该时间与参与者已有面交安排冲突；系统允许双方在知情情况下继续决定。

## 7. 售后、退款与同步提醒

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/order/{id}/return-request` | 买家 | 提交正式退款/退货工单 |
| GET | `/api/order/{id}/after-sale` | 参与方 | 查询工单；无工单时 `data=null` |
| POST | `/api/order/{id}/after-sale/seller-review` | 卖家 | 同意或拒绝 |
| POST | `/api/order/{id}/after-sale/intervention` | 参与方 | 申请平台介入 |
| GET | `/api/admin/after-sales` | 管理员 | 全部工单 |
| POST | `/api/admin/after-sales/{ticketId}/review` | 管理员 | 退款审核 |
| POST | `/api/admin/after-sales/{ticketId}/refund` | 管理员 | 执行已批准退款 |

买家申请：

```json
{"reason":"商品与描述不符","description":"实物有未说明的损坏"}
```

卖家/管理员审核：

```json
{"decision":"APPROVE","note":"同意退款"}
```

介入：

```json
{"reason":"双方对验货结果存在分歧，请平台核验聊天记录"}
```

规则：

- 未付款订单直接取消，不创建退款工单；
- 付款后至确认收货后 7 天内可申请；
- 一张订单只能有一张正式售后工单；
- 售后进行中禁止卖家继续预约快递或确认发货；
- 售后动作自动写入关联商品会话，形成对方未读订单提醒；
- 执行退款后订单与支付状态均变为 `REFUNDED`，商品恢复可购买。

## 8. 聊天与自动回复

### 8.1 聊天接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/chat/product/{productId}` | 登录/非卖家 | 创建或返回商品会话 |
| GET | `/api/chat/conversations?box=BUYER` | 登录 | 我咨询的会话 |
| GET | `/api/chat/conversations?box=SELLER` | 登录 | 别人咨询我的会话 |
| GET | `/api/chat/conversations/{id}/messages` | 会话参与方 | 消息列表并将对方消息标记已读 |
| POST | `/api/chat/conversations/{id}/messages` | 会话参与方 | 发送消息 |
| GET | `/api/chat/unread-count` | 登录 | 所有会话未读总数 |

发送消息：`{"content":"今晚可以面交吗？"}`，最多 1000 字。

会话响应同时包含商品信息和最近订单摘要：`productStatus`、`productPrice`、`order.status`、`canCancel`、`canAdjustPrice`、`canRequestRefund` 等，使聊天、商品与交易状态保持同一上下文。

### 8.2 自动回复

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/auto-reply/global` | 登录 | 全局自动回复设置 |
| PUT | `/api/auto-reply/global` | 登录 | 修改全局设置 |
| GET | `/api/products/{id}/auto-reply` | 商品卖家 | 商品级设置 |
| PUT | `/api/products/{id}/auto-reply` | 商品卖家 | 修改商品级设置 |

全局请求：`{"enabled":true,"content":"已收到消息，我会尽快回复。"}`。  
商品请求：`{"enabled":true,"text":"这件商品支持图书馆北门面交。"}`。

## 9. 评价、信誉与风险

### 9.1 双向评价

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/reviews` | 已完成订单参与方 | 创建一次评价 |
| GET | `/api/reviews/order/{orderId}` | 参与方 | 订单双方评价 |
| GET | `/api/reviews/user/{userId}?type=` | 公开 | 用户收到的评价与均分 |

请求：

```json
{"orderId":123,"rating":5,"content":"沟通及时，商品与描述一致"}
```

`type` 可为 `BUYER_REVIEWS_SELLER` 或 `SELLER_REVIEWS_BUYER`。

### 9.2 Trust Score 与兼容评分

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/trust/users/{userId}` | 公开 | 指定用户信誉分与组成 |
| GET | `/api/trust/me` | 登录 | 当前用户信誉分 |
| POST | `/api/trust/orders/{orderId}/rating` | 已完成订单参与方 | 兼容评分接口 |

兼容评分请求：`{"score":5,"comment":"交易顺利"}`。

Trust Score 综合返回总分、等级、校园认证、完成交易数、平均评分、举报影响和解释项。`trade_reviews` 与 `transaction_ratings` 会按订单/评价人去重，避免同一评价重复计算。

### 9.3 交易风险

`GET /api/risk/orders/{orderId}`，权限为订单参与方。

返回 `riskLevel` 和 `alerts[]`，检查大额交易、近期频繁取消、异常改价和低信誉交易对手。风险提示用于付款前解释，不直接代替用户决策。

## 10. 收藏举报与平台治理

举报创建：

`POST /api/reports`，登录权限。

```json
{"productId":7,"reason":"疑似虚假商品","detail":"图片与描述明显不一致"}
```

管理员接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/dashboard?from=&to=` | 数据面板；默认最近 7 天，最多 366 天 |
| GET | `/api/admin/users` | 用户列表 |
| PATCH | `/api/admin/users/{id}/status` | 设置 `ACTIVE` / `DISABLED` |
| GET | `/api/admin/products` | 全部商品 |
| PATCH | `/api/admin/products/{id}/status` | 设置商品状态 |
| GET | `/api/admin/reports` | 举报列表 |
| POST | `/api/admin/reports/{id}/process` | 处理举报，可同时下架商品 |
| GET | `/api/admin/after-sales` | 售后队列 |
| POST | `/api/admin/after-sales/{id}/review` | 审核退款 |
| POST | `/api/admin/after-sales/{id}/refund` | 执行退款 |
| GET | `/api/admin/map-config` | 管理端地图配置 |
| PUT | `/api/admin/map-config` | 修改地图配置 |

数据面板响应包含：`range`、`totalOrders`、`grossMerchandiseValue`、`orderStatuses`、`totalUsers`、`activeUsers`、`disabledUsers`、`verifiedUsers`、`pendingReports`、`refundedOrders`、`interventionOrders`、`pendingAfterSales`、`dailyTrend[]`、`categoryAnalysis[]`。

举报处理请求：

```json
{"status":"RESOLVED","adminNote":"核验后已下架","takeDownProduct":true}
```

地图配置请求为键值对象，可包含 `centerLat`、`centerLng`、`zoom`、`tileUrl`、`attribution`。

## 11. 任务与金币

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/rewards/summary` | 登录 | 钱包、任务、游戏进度和流水摘要 |
| POST | `/api/rewards/tasks/{code}/claim` | 登录 | 领取已完成任务奖励 |
| POST | `/api/rewards/game/play` | 登录 | 每日掷骰子寻宝 |

任务领取由 `(user_id, task_code, claim_date)` 唯一约束保证每日幂等；金币余额和流水在同一事务中更新。

## 12. 支付宝沙箱

| 方法 | 路径 | 权限/类型 | 说明 |
|---|---|---|---|
| GET | `/api/payments/alipay/status` | 公开 JSON | 是否已配置沙箱 |
| GET | `/api/payments/alipay/start/{orderId}` | 买家 HTML | 创建支付表单并跳转沙箱 |
| POST | `/api/payments/alipay/notify` | 支付宝表单回调 | 异步验签并确认订单 |
| GET | `/api/payments/alipay/return` | 支付宝页面回跳 | 验签后跳转订单页 |

沙箱关闭时，前端自动使用 0.8 秒本地模拟支付；沙箱开启时，单卖家支付宝订单可跳转支付宝沙箱。多卖家合并支付当前仍使用本地模拟，避免一个外部交易号对应多商户子订单的账务歧义。

## 13. 兼容接口说明

以下接口为课程早期集成测试保留，不是当前前端主流程：

| 方法 | 路径 |
|---|---|
| POST | `/api/order/create` |
| GET | `/api/order/buyer/{buyerId}` |
| GET | `/api/order/seller/{sellerId}` |
| PATCH | `/api/order/{id}/pay` |
| PATCH | `/api/order/{id}/ship` |
| PATCH | `/api/order/{id}/receive` |

正式部署前建议移除或为其补齐 Session 与资源权限校验，客户端应使用第 5 节列出的正式接口。

## 14. 关键联调场景

### 14.1 多卖家结算

1. 调用 `POST /api/cart/{productId}` 加入不同卖家商品；
2. 调用 `POST /api/order/checkout`；
3. 验证返回多个不同 `sellerId` 的订单；
4. 分别使用各订单 ID 调用幂等支付接口。

### 14.2 面交双向确认

1. 结算时选择 `PICKUP`；
2. 卖家与买家分别获取订单，验证 `shippingMethod` 和安排一致；
3. 卖家输入买家码确认交付；
4. 买家输入卖家码确认收货；
5. 验证订单为 `COMPLETED`、商品为 `SOLD`。

### 14.3 售后同步

1. 买家对已付款订单提交 `return-request`；
2. 卖家读取 `/api/order/mine/seller`，应看到 `RETURN_REQUESTED`；
3. 卖家读取 `/api/chat/unread-count`，应增加未读提醒；
4. 买卖双方读取同一订单，`shippingMethod` 与 `status` 应一致；
5. 售后期间调用快递预约/发货接口，应返回 `409`；
6. 卖家审核，必要时申请介入，由管理员审核并执行退款。
