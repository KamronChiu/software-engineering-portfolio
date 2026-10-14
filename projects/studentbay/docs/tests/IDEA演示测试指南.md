# StudentBay IDEA 演示测试指南

> 目标：答辩时能够在 IDEA 中按业务场景直接运行测试，并清楚说明每一步验证什么。  
> 测试数据库：H2 内存数据库；不会改动本机 MySQL。  
> 外部依赖：不需要真实支付宝、AI Key 或校园网。

## 1. IDEA 运行方式

1. 展开 `src/test/java/com/campus/trade`。
2. 找到下表指定测试类。
3. 点击类名或测试方法左侧绿色三角形。
4. 选择 `Run`。
5. 观察底部 Test Results：全部绿色即通过。

全量验收时，右键 `src/test/java` 目录并选择运行全部测试；也可以打开 Maven 面板运行 `Lifecycle → test`。

## 2. 推荐演示顺序

### Demo 01：应用和演示数据

测试类：`DemoDataInitializerIntegrationTests`

| 操作步骤 | 对应测试 |
|---|---|
| 启动后自动准备六个商品分类 | `catalogueShouldContainFiveDistinctProductsInEveryCategory` |
| 准备至少五种儿童玩具 | `catalogueShouldContainFiveDistinctChildFriendlyToyProducts` |
| 每件演示商品使用不同主图 | `everyPresentationProductShouldHaveItsOwnNonBlankPhoto` |
| 重启不会重复插入演示数据 | `rerunningInitializerShouldBeIdempotent` |

### Demo 02：注册、登录和个人功能

测试类：`BusinessFlowWebAutomationTests`

| 操作步骤 | 对应测试及断言 |
|---|---|
| 新用户注册 | `visitorCanRegisterUseSessionLogoutAndLoginAgain`：注册返回 201/成功数据 |
| 获取当前登录用户 | 同一方法：`/api/auth/me` 返回注册用户 |
| 退出并失去登录态 | 同一方法：退出后访问 `/me` 返回 401 |
| 再次登录 | 同一方法：正确密码成功，错误密码失败 |
| 新增、修改、删除地址 | `userCanMaintainAddressesProfileCouponsAndRewards` |
| 修改个人资料 | 同一方法：昵称更新成功 |

### Demo 03：商品发布、搜索和互动

测试类：`BusinessFlowWebAutomationTests`、`ProductStage4ControllerIntegrationTests`、`Day6TrustAiPaymentIntegrationTests`

| 操作步骤 | 对应测试 |
|---|---|
| 卖家发布商品 | `sellerCanPublishAndBuyerCanFilterCompareAndComment` |
| 查看我发布的商品 | 同一方法：`/api/products/mine/list` |
| 分类和关键词筛选 | 同一方法及 `keywordSearchMatchesCourseCodeAndIsbn` |
| 商品详情浏览量 | `sellerCanPublishAndBuyerCanFilterCompareAndComment` |
| 同款价格对照 | 同一方法：comparisons 统计 |
| 发布与删除公开留言 | 同一方法 |
| 收藏、举报和聊天 | `buyerCanUseCartFavoritesReportsAndChat` |
| 自然语言搜索预算、键盘和面交 | `naturalLanguageSearchShouldUnderstandBudgetProductAndPickupIntentWithoutNetworkAi` |
| 儿童玩具同义词并排除折叠伞 | `naturalLanguageSearchShouldExpandToySynonymsAndNeverFillResultsWithUnrelatedOtherItems` |

### Demo 04：购物车、多卖家拆单与支付

测试类：`OrderControllerIntegrationTests`、`Day5OrderWorkflowIntegrationTests`

| 操作步骤 | 对应测试 |
|---|---|
| 商品加入购物车且重复加购不重复 | `buyerCanUseCartFavoritesReportsAndChat` |
| 同卖家多商品合并为一单 | `checkoutShouldGroupItemsBySellerAndRequireTwoWayPickupCodes` |
| 不同卖家拆成多张子订单 | `checkoutShouldSplitOnePaymentIntoOrdersForDifferentSellers` |
| 支付后状态变为已付款 | `orderShouldFollowPayShipReceiveLifecycle` |
| 相同幂等键重复支付 | `paymentShouldReturnSameResultForSameIdempotencyKeyAndRejectAnotherKey` |
| HTTP 支付必须提供幂等键 | `paymentEndpointShouldRequireAKeyAndRemainIdempotentOverHttp` |
| 买家取消未付款订单并释放商品 | `buyerShouldCancelUnpaidOrderAndReleaseProductWhileSellerCannotCancel` |
| 超时任务取消订单并释放商品 | `timeoutTaskShouldCancelOrderAndReleaseReservedProduct` |
| 卖家付款前改价、付款后锁价 | `sellerShouldAdjustProductAndShippingPriceOnlyBeforePayment` |

### Demo 05：校园面交和快递履约

测试类：`OrderControllerIntegrationTests`、`Day5OrderWorkflowIntegrationTests`

| 操作步骤 | 对应测试 |
|---|---|
| 创建 Meeting 信息 | `checkoutShouldGroupItemsBySellerAndRequireTwoWayPickupCodes` |
| 卖家验证买家取件码 | 同一方法 |
| 买家验证卖家交付码 | 同一方法 |
| 面交时间冲突提醒 | `pickupProposalShouldWarnAboutConflictAndRequireCounterpartyAcceptance` |
| 对方接受后更新正式时间地点 | 同一方法 |
| 快递预约、填写运单、物流刷新 | `deliveryOrderShouldCompleteCourierAndLogisticsLifecycle` |
| 买家确认收货、商品变为 SOLD | 同一方法 |

### Demo 06：退款、售后和消息同步

测试类：`Day5OrderWorkflowIntegrationTests`

| 操作步骤 | 对应测试 |
|---|---|
| 付款后立即申请退款 | `paidOrderShouldAllowImmediateRefundButCloseSevenDaysAfterReceipt` |
| 确认收货超过 7 天拒绝申请 | 同一方法 |
| 卖家拒绝、买家申请平台介入 | `rejectedReturnCanBecomeInterventionAndCompleteReviewedRefund` |
| 管理员审核并执行退款 | `adminEndpointsShouldReviewAndExecuteApprovedRefund` |
| 退款后恢复商品 | 同一方法中的商品状态断言 |
| 买卖双方配送方式和售后状态一致 | `returnRequestShouldNotifySellerAndKeepBothViewsSynchronized` |
| 卖家收到售后未读聊天事件 | 同一方法中的消息与未读数断言 |
| 售后进行中禁止继续发货 | 同一方法中的 409 冲突断言 |

### Demo 07：信用、风控、奖励和后台

测试类：`Day6TrustAiPaymentIntegrationTests`、`BusinessFlowWebAutomationTests`、`Day5OrderWorkflowIntegrationTests`

| 操作步骤 | 对应测试 |
|---|---|
| 完成订单后评价并影响 Trust Score | `completedOrderRatingShouldAffectTrustScoreAndRejectDuplicateRating` |
| 防止同一订单重复评价 | 同一方法 |
| 大额与异常改价风险提示 | `highValueAndRepeatedLargePriceChangesShouldCreateExplainableRiskAlerts` |
| 领取签到金币、投掷三次骰子 | `userCanMaintainAddressesProfileCouponsAndRewards` |
| 第四次投掷被次数限制拦截 | 同一方法 |
| 管理员查看举报并下架商品 | `adminCanInspectGovernanceQueueAndModerateAReport` |
| 日期筛选和分类成交分析 | `adminDashboardShouldFilterDatesExposeCategoriesAndProtectAdminRoutes` |
| 普通用户不能访问管理员接口 | 同一方法 |

### Demo 08：页面和关键按钮完整性

测试类：`WebPageAutomationTests`

| 操作步骤 | 对应测试 |
|---|---|
| 20 个用户页面均可加载 | `userFacingPageShouldLoad` 参数化测试 |
| 登录/注册表单 ID 完整 | `loginPageShouldExposeCompleteLoginAndRegistrationControls` |
| 结算、支付、订单、任务、搜索和后台按钮未被删除 | `transactionPagesShouldKeepCriticalInteractiveTargets` |

## 3. 答辩推荐运行组合

若时间有限，依次运行以下 6 个方法即可覆盖项目特色：

1. `naturalLanguageSearchShouldExpandToySynonymsAndNeverFillResultsWithUnrelatedOtherItems`
2. `checkoutShouldSplitOnePaymentIntoOrdersForDifferentSellers`
3. `checkoutShouldGroupItemsBySellerAndRequireTwoWayPickupCodes`
4. `paymentShouldReturnSameResultForSameIdempotencyKeyAndRejectAnotherKey`
5. `returnRequestShouldNotifySellerAndKeepBothViewsSynchronized`
6. `adminDashboardShouldFilterDatesExposeCategoriesAndProtectAdminRoutes`

## 4. 测试失败时的快速判断

- `ApplicationContext` 启动失败：先确认使用 JDK 17 或更高版本。
- IDEA 找不到测试：重新加载 Maven 项目，确认 `src/test/java` 标记为 Test Sources Root。
- MySQL 连接失败：说明误用了主配置；测试应自动读取 `src/test/resources/application.properties` 并连接 H2。
- AI 测试失败：不要填写真实 Key；自动化测试使用关闭或本机失败降级场景。
- 页面断言失败：检查静态页面是否改名、关键表单 ID 是否被组员合并覆盖。

## 5. 验收标准

- IDEA Test Results 全绿；
- `Failures: 0`；
- `Errors: 0`；
- 无真实 MySQL 数据被修改；
- 无真实支付产生；
- 无需外网 AI 服务。
