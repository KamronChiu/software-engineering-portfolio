package com.campus.trade.config;

import com.campus.trade.entity.address.DeliveryAddress;
import com.campus.trade.entity.activity.ActivityType;
import com.campus.trade.entity.activity.UserActivity;
import com.campus.trade.entity.config.AppConfig;
import com.campus.trade.entity.location.CampusPoi;
import com.campus.trade.entity.coupon.Coupon;
import com.campus.trade.entity.product.Category;
import com.campus.trade.entity.product.HotKeyword;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.entity.user.UserRole;
import com.campus.trade.repository.activity.UserActivityRepository;
import com.campus.trade.repository.address.AddressRepository;
import com.campus.trade.repository.config.AppConfigRepository;
import com.campus.trade.repository.coupon.CouponRepository;
import com.campus.trade.repository.location.CampusPoiRepository;
import com.campus.trade.repository.product.CategoryRepository;
import com.campus.trade.repository.product.HotKeywordRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.PasswordHasher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final ProductRepository products;
    private final AddressRepository addresses;
    private final CouponRepository coupons;
    private final AppConfigRepository configs;
    private final CampusPoiRepository pois;
    private final PasswordHasher passwordHasher;
    private final UserActivityRepository activities;
    private final CategoryRepository categories;
    private final HotKeywordRepository hotKeywords;

    public DemoDataInitializer(UserRepository users, ProductRepository products, AddressRepository addresses,
                               CouponRepository coupons, AppConfigRepository configs, CampusPoiRepository pois,
                               PasswordHasher passwordHasher, UserActivityRepository activities,
                               CategoryRepository categories, HotKeywordRepository hotKeywords) {
        this.users = users;
        this.products = products;
        this.addresses = addresses;
        this.coupons = coupons;
        this.configs = configs;
        this.pois = pois;
        this.passwordHasher = passwordHasher;
        this.activities = activities;
        this.categories = categories;
        this.hotKeywords = hotKeywords;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppUser demoBuyer = ensureUser("lin@campus.edu", "林同学", "广东东软学院", UserRole.USER);
        AppUser demoSeller = ensureUser("chen@campus.edu", "陈同学", "广东东软学院", UserRole.USER);
        AppUser secondSeller = ensureUser("wang@campus.edu", "王同学", "广东东软学院", UserRole.USER);
        ensureUser("admin@studentbay.local", "平台管理员", "StudentBay", UserRole.ADMIN);

        repairDemoProductOwnership(demoBuyer.getId(), demoSeller.getId());
        ensureProductCatalog(demoSeller.getId(), secondSeller.getId());

        if (addresses.count() == 0) {
            DeliveryAddress address = new DeliveryAddress();
            address.setUserId(demoBuyer.getId());
            address.setRecipientName("林同学");
            address.setPhone("138****2026");
            address.setDetail("广东东软学院 北区宿舍 5 栋 菜鸟驿站");
            address.setDefaultAddress(true);
            addresses.save(address);
        }

        if (coupons.count() == 0) {
            coupons.saveAll(List.of(coupon("CAMPUS5", "校园新学期券", "5.00", "30.00"),
                    coupon("GREEN10", "绿色循环券", "10.00", "100.00")));
        }

        config("map.centerLat", "23.138605");
        config("map.centerLng", "113.025887");
        config("map.zoom", "16");
        config("map.tileUrl", "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}");
        config("map.attribution", "&copy; 高德地图");
        config("map.geoKey", "");
        repairLegacyTileUrl();
        repairCampusCoordinates();
        seedCampusPois();
        seedCategories();
        seedHotKeywords();
        enrichDemoProducts(demoSeller.getId(), secondSeller.getId());
        seedDemoActivities(demoSeller.getId());
    }

    private void seedCampusPois() {
        if (pois.count() > 0) return;
        List<CampusPoi> campus = List.of(
                poi("图书馆东门", "广东省佛山市南海区狮山镇 · 广东东软学院图书馆东门", "23.1386050", "113.0258870", "学习区", 1),
                poi("图书馆西门", "广东省佛山市南海区狮山镇 · 广东东软学院图书馆西门", "23.1386100", "113.0249000", "学习区", 2),
                poi("北区宿舍 7 栋", "广东省佛山市南海区狮山镇 · 广东东软学院北区宿舍 7 栋楼下", "23.1405000", "113.0239000", "生活区", 3),
                poi("南区宿舍 3 栋", "广东省佛山市南海区狮山镇 · 广东东软学院南区宿舍 3 栋楼下", "23.1362000", "113.0268000", "生活区", 4),
                poi("实验楼 A 栋", "广东省佛山市南海区狮山镇 · 广东东软学院实验楼 A 栋门口", "23.1379000", "113.0245000", "学习区", 5),
                poi("创新创业学院", "广东省佛山市南海区狮山镇 · 广东东软学院创新创业学院", "23.1382000", "113.0272000", "学习区", 6),
                poi("运动场看台", "广东省佛山市南海区狮山镇 · 广东东软学院运动场看台", "23.1398000", "113.0278000", "运动区", 7),
                poi("校门口广场", "广东省佛山市南海区狮山镇 · 广东东软学院正门广场", "23.1373000", "113.0229000", "校门口", 8),
                poi("食堂一楼", "广东省佛山市南海区狮山镇 · 广东东软学院第一食堂一楼", "23.1391000", "113.0250000", "生活区", 9),
                poi("教学楼 B 座", "广东省佛山市南海区狮山镇 · 广东东软学院教学楼 B 座门口", "23.1389000", "113.0242000", "学习区", 10)
        );
        pois.saveAll(campus);
    }

    private CampusPoi poi(String name, String address, String lat, String lng, String category, int sort) {
        CampusPoi poi = new CampusPoi();
        poi.setName(name);
        poi.setAddress(address);
        poi.setLatitude(new BigDecimal(lat));
        poi.setLongitude(new BigDecimal(lng));
        poi.setCategory(category);
        poi.setSort(sort);
        poi.setEnabled(true);
        return poi;
    }

    /** T4.1 预置 6 个一级分类（与发布页/首页 Tab 一致），重复启动不重复插入 */
    private void seedCategories() {
        if (categories.count() > 0) return;
        List<Category> seeds = List.of(
                category("教材资料", "\uD83D\uDCDA", 1),
                category("数码产品", "\uD83D\uDCF1", 2),
                category("骑行装备", "\uD83D\uDEB2", 3),
                category("宿舍生活", "\uD83D\uDECF", 4),
                category("服饰美妆", "\uD83D\uDC54", 5),
                category("其他", "\uD83D\uDCD6", 6)
        );
        categories.saveAll(seeds);
    }

    private Category category(String name, String icon, int sort) {
        Category category = new Category();
        category.setName(name);
        category.setIcon(icon);
        category.setSort(sort);
        category.setStatus("ENABLED");
        return category;
    }

    /** T4.2 预置热搜词，保证全新数据库也有热搜榜可看 */
    private void seedHotKeywords() {
        if (hotKeywords.count() > 0) return;
        List<HotKeyword> seeds = List.of(
                hotKeyword("教材", 38L),
                hotKeyword("机械键盘", 26L),
                hotKeyword("自行车", 19L),
                hotKeyword("耳机", 15L),
                hotKeyword("考研", 12L),
                hotKeyword("宿舍", 10L),
                hotKeyword("台灯", 8L)
        );
        hotKeywords.saveAll(seeds);
    }

    private HotKeyword hotKeyword(String keyword, Long count) {
        HotKeyword item = new HotKeyword();
        item.setKeyword(keyword);
        item.setCount(count);
        return item;
    }

    /** T4.3/T4.7/T4.5 为演示商品补参数（ISBN/课程号/品牌/尺码/成色说明/可议价），只在字段为空时写入，不覆盖用户改动 */
    private void enrichDemoProducts(Long primarySellerId, Long secondarySellerId) {
        List<Long> sellerIds = List.of(primarySellerId, secondarySellerId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Product product : products.findAll()) {
            if (!sellerIds.contains(product.getSellerId())) continue;
            enrichOne(product, now);
        }
    }

    private void enrichOne(Product product, java.time.LocalDateTime now) {
        String title = product.getTitle();
        boolean changed = false;
        if (title.contains("Java 全栈开发教材")) {
            changed |= setIfNull(product.getIsbn(), "978-7-115-59367-0", v -> product.setIsbn(v));
            changed |= setIfNull(product.getCourseCode(), "SE2101", v -> product.setCourseCode(v));
            changed |= setIfNull(product.getModuleName(), "Java 全栈开发", v -> product.setModuleName(v));
        } else if (title.contains("高等数学同济第七版")) {
            changed |= setIfNull(product.getIsbn(), "978-7-04-039663-8", v -> product.setIsbn(v));
            changed |= setIfNull(product.getCourseCode(), "MATH101", v -> product.setCourseCode(v));
            changed |= setIfNull(product.getModuleName(), "高等数学", v -> product.setModuleName(v));
        } else if (title.contains("大学英语四级真题集")) {
            changed |= setIfNull(product.getIsbn(), "978-7-5600-9876-1", v -> product.setIsbn(v));
            changed |= setIfNull(product.getModuleName(), "大学英语四级", v -> product.setModuleName(v));
        } else if (title.contains("数据结构课程笔记")) {
            changed |= setIfNull(product.getCourseCode(), "CS2010", v -> product.setCourseCode(v));
            changed |= setIfNull(product.getModuleName(), "数据结构", v -> product.setModuleName(v));
        } else if (title.contains("机械键盘")) {
            changed |= setIfNull(product.getBrand(), "机械键盘", v -> product.setBrand(v));
            changed |= setIfNull(product.getSize(), "87 键", v -> product.setSize(v));
        } else if (title.contains("索尼降噪蓝牙耳机")) {
            changed |= setIfNull(product.getBrand(), "索尼", v -> product.setBrand(v));
            changed |= setIfNull(product.getColor(), "黑色", v -> product.setColor(v));
        } else if (title.contains("捷安特通勤自行车")) {
            changed |= setIfNull(product.getBrand(), "捷安特", v -> product.setBrand(v));
            changed |= setIfNull(product.getSize(), "26 寸", v -> product.setSize(v));
            changed |= setIfNull(product.getSportType(), "通勤", v -> product.setSportType(v));
        } else if (title.contains("一体成型骑行头盔")) {
            changed |= setIfNull(product.getBrand(), "骑行", v -> product.setBrand(v));
            changed |= setIfNull(product.getSize(), "M", v -> product.setSize(v));
            changed |= setIfNull(product.getSportType(), "骑行", v -> product.setSportType(v));
        } else if (title.contains("学院风连帽卫衣")) {
            changed |= setIfNull(product.getBrand(), "学院风", v -> product.setBrand(v));
            changed |= setIfNull(product.getSize(), "L", v -> product.setSize(v));
            changed |= setIfNull(product.getColor(), "墨绿", v -> product.setColor(v));
            changed |= setIfNull(product.getGender(), "中性", v -> product.setGender(v));
        } else if (title.contains("轻量缓震跑步鞋")) {
            changed |= setIfNull(product.getBrand(), "缓震", v -> product.setBrand(v));
            changed |= setIfNull(product.getSize(), "42", v -> product.setSize(v));
            changed |= setIfNull(product.getGender(), "男", v -> product.setGender(v));
        } else if (title.contains("毕业答辩正装外套")) {
            changed |= setIfNull(product.getBrand(), "正装", v -> product.setBrand(v));
            changed |= setIfNull(product.getSize(), "M", v -> product.setSize(v));
            changed |= setIfNull(product.getColor(), "深灰", v -> product.setColor(v));
            changed |= setIfNull(product.getGender(), "男", v -> product.setGender(v));
        }
        // 议价标记与成色说明：挑几条演示商品补全，模拟真实卖家习惯
        if ((title.contains("机械键盘 87 键") || title.contains("捷安特通勤自行车") || title.contains("索尼降噪蓝牙耳机"))
                && product.getBargainEnabled() == null) {
            product.setBargainEnabled(true);
            changed = true;
        }
        if (product.getConditionDetail() == null && product.getDescription() != null && product.getDescription().length() > 12) {
            product.setConditionDetail("详见商品描述，功能完好，无影响使用的瑕疵。");
            changed = true;
        }
        // T4.5 演示：一台已擦亮、一台置顶 48 小时（字段为空才写，避免重启覆盖）
        if (title.contains("捷安特通勤自行车") && product.getSharpenedAt() == null) {
            product.setSharpenedAt(now.minusHours(2));
            changed = true;
        }
        if (title.contains("机械键盘 87 键") && product.getTopExpireAt() == null) {
            product.setTopExpireAt(now.plusHours(48));
            changed = true;
        }
        if (changed) products.save(product);
    }

    private boolean setIfNull(String current, String value, java.util.function.Consumer<String> setter) {
        if (current != null && !current.isBlank()) return false;
        setter.accept(value);
        return true;
    }

    /** 首次启动时为演示卖家的现成商品补种"上架"动态（已售商品同时补"卖出"动态） */
    private void seedDemoActivities(Long sellerId) {
        if (activities.count() > 0) return;
        List<UserActivity> seeds = new java.util.ArrayList<>();
        products.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .sorted(java.util.Comparator.comparing(Product::getCreatedAt))
                .forEach(product -> {
                    UserActivity publish = new UserActivity();
                    publish.setUserId(sellerId);
                    publish.setType(ActivityType.PUBLISH);
                    publish.setProductId(product.getId());
                    publish.setTitle(product.getTitle());
                    publish.setCreatedAt(product.getCreatedAt());
                    seeds.add(publish);
                    if (product.getStatus() == ProductStatus.SOLD) {
                        UserActivity sold = new UserActivity();
                        sold.setUserId(sellerId);
                        sold.setType(ActivityType.SOLD);
                        sold.setProductId(product.getId());
                        sold.setTitle(product.getTitle());
                        sold.setCreatedAt(product.getUpdatedAt());
                        seeds.add(sold);
                    }
                });
        if (!seeds.isEmpty()) activities.saveAll(seeds);
    }

    private void repairDemoProductOwnership(Long buyerId, Long sellerId) {
        List<String> demoTitles = List.of(
                "Java 全栈开发教材",
                "捷安特通勤自行车",
                "宿舍折叠桌",
                "机械键盘 87 键"
        );
        products.findAll().stream()
                .filter(product -> demoTitles.contains(product.getTitle()))
                .filter(product -> product.getSellerId().equals(buyerId))
                .forEach(product -> {
                    product.setSellerId(sellerId);
                    products.save(product);
                });
    }

    private AppUser ensureUser(String email, String name, String university, UserRole role) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(user(email, name, university, role)));
    }

    /**
     * Keeps the demo catalogue useful after an existing database is upgraded. Titles are stable seed keys,
     * so restarting the application never creates duplicate sample products and never removes user listings.
     */
    private void ensureProductCatalog(Long primarySellerId, Long secondarySellerId) {
        java.util.Map<String, Product> existingByTitle = products.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Product::getTitle, item -> item, (first, ignored) -> first));
        List<SeedProduct> catalogue = demoCatalogue();
        for (int index = 0; index < catalogue.size(); index++) {
            SeedProduct seed = catalogue.get(index);
            Product existing = existingByTitle.get(seed.title());
            if (existing != null) {
                // Demo titles are stable seed keys. Keep upgraded databases visually consistent for presentations.
                if (!java.util.Objects.equals(existing.getImageUrl(), seed.imageUrl())) {
                    existing.setImageUrl(seed.imageUrl());
                    products.save(existing);
                }
                continue;
            }
            Long sellerId = index % 2 == 0 ? primarySellerId : secondarySellerId;
            products.save(product(sellerId, seed.title(), seed.description(), seed.category(), seed.condition(),
                    seed.price(), seed.originalPrice(), seed.imageUrl(), seed.location(), seed.lat(), seed.lng()));
        }
    }

    private List<SeedProduct> demoCatalogue() {
        return List.of(
                seed("Java 全栈开发教材", "课程配套教材，少量划线，附赠整理好的章节笔记。", "教材资料", "九成新", "58.00", "79.00", demoImage("1544947950-fa07a98d237f"), "图书馆东门"),
                seed("高等数学同济第七版", "上下册齐全，重点章节有铅笔批注，页面完整。", "教材资料", "八成新", "32.00", "89.00", demoImage("1495446815901-a7297e633e8d"), "教学楼 B 栋"),
                seed("大学英语四级真题集", "近五年真题和听力资料齐全，仅做过两套。", "教材资料", "九成新", "18.00", "45.00", demoImage("1524995997946-a1c2e315a42f"), "图书馆南门"),
                seed("数据结构课程笔记套装", "包含教材、实验指导书和手写知识点索引。", "教材资料", "八成新", "45.00", "96.00", demoImage("1512820790803-83ca734da794"), "实验楼 A 栋"),
                seed("考研政治核心考案", "最新版备考资料，知识框架清晰，无缺页。", "教材资料", "九成新", "28.00", "68.00", demoImage("1456513080510-7bf3a84b82f8"), "学生活动中心"),

                seed("机械键盘 87 键", "茶轴，白色背光，键帽已清洁，带原装数据线。", "数码产品", "九成新", "128.00", "239.00", demoImage("1587829741301-dc798b83add3"), "实验楼 A 栋"),
                seed("87 键机械键盘 茶轴", "白色背光，茶轴手感轻盈，键帽无明显磨损。", "数码产品", "八成新", "99.00", "219.00", demoImage("1618384887929-16ec33fab9ef"), "创新创业学院"),
                seed("机械键盘 87 键 红轴", "红轴静音版本，支持有线连接，附拔键器。", "数码产品", "九成新", "148.00", "259.00", demoImage("1595225476474-87563907a212"), "图书馆西门"),
                seed("索尼降噪蓝牙耳机", "续航正常，耳罩已更换，配充电线和收纳袋。", "数码产品", "八成新", "320.00", "899.00", demoImage("1505740420928-5e560c06d30e"), "北区食堂"),
                seed("平板电脑触控笔", "支持磁吸充电，书写流畅，适合课堂记笔记。", "数码产品", "九成新", "168.00", "399.00", demoImage("1544244015-0df4b3ffc6b0"), "图书馆东门"),
                seed("罗技静音无线鼠标", "蓝牙与接收器双模连接，滚轮和按键状态良好。", "数码产品", "九成新", "75.00", "169.00", demoImage("1527864550417-7fd91fc51a46"), "教学楼 A 栋"),
                seed("七合一 USB-C 扩展坞", "含 HDMI、读卡和网口，适合宿舍桌面与课堂演示。", "数码产品", "九成新", "86.00", "199.00", demoImage("1593642532400-2682810df593"), "创新创业学院"),
                seed("10000mAh 便携充电宝", "双口输出，电芯状态正常，附短款充电线。", "数码产品", "八成新", "58.00", "139.00", demoImage("1609592806596-4d8b5e3c1a28"), "图书馆南门"),

                seed("捷安特通勤自行车", "车况良好，刹车和变速均已保养，支持校内看车试骑。", "骑行装备", "八成新", "680.00", "1599.00", demoImage("1558981806-ec527fa84c39"), "北区宿舍 7 栋"),
                seed("一体成型骑行头盔", "M 码，带后置警示灯，只使用过一个学期。", "骑行装备", "九成新", "65.00", "139.00", demoImage("1576435728678-68d0fbf94e91"), "体育馆东侧"),
                seed("自行车密码 U 型锁", "五位密码锁，锁梁无锈蚀，附车架固定座。", "骑行装备", "九成新", "35.00", "79.00", demoImage("1502744688674-c619d1586c9e"), "南区宿舍 6 栋"),
                seed("便携自行车打气筒", "法美嘴通用，带气压表和球针，体积小巧。", "骑行装备", "八成新", "22.00", "49.00", demoImage("1529422643029-d4585747aaf2"), "田径场北门"),
                seed("USB 充电骑行灯套装", "前灯与尾灯一套，三档亮度，雨天可用。", "骑行装备", "九成新", "42.00", "89.00", demoImage("1532298229144-0ec0c57515c7"), "北区停车棚"),
                seed("自行车后座防水驮包", "可拆卸肩带，日常装书和雨衣足够，拉链顺滑。", "骑行装备", "九成新", "52.00", "119.00", demoImage("1520975954732-35dd22299614"), "南区停车棚"),

                seed("宿舍折叠桌", "可折叠收纳，桌面无明显划痕，毕业搬宿舍低价出。", "宿舍生活", "九成新", "35.00", "69.00", demoImage("1532372320572-cda25653a26d"), "南区宿舍 3 栋"),
                seed("护眼 LED 台灯", "三档色温和亮度调节，Type-C 供电。", "宿舍生活", "九成新", "48.00", "109.00", demoImage("1507473885765-e6ed057f782c"), "北区宿舍 5 栋"),
                seed("透明抽屉收纳箱", "三层可叠加，适合收纳文具、药品和小物件。", "宿舍生活", "八成新", "26.00", "59.00", demoImage("1586023492125-27b2c045efd7"), "南区宿舍 2 栋"),
                seed("宿舍人体工学坐垫", "记忆棉材质，可拆洗外套，久坐上课更舒适。", "宿舍生活", "九成新", "38.00", "88.00", demoImage("1493663284031-b7e3aefcae8e"), "北区宿舍 8 栋"),
                seed("迷你电热水壶", "宿舍低功率款，容量 0.8L，内胆已除垢清洁。", "宿舍生活", "八成新", "45.00", "99.00", demoImage("1555041469-a586c61ea9bc"), "生活服务中心"),
                seed("落地式宿舍晾衣架", "免打孔可折叠，横杆和底座稳定，适合阳台使用。", "宿舍生活", "九成新", "49.00", "108.00", demoImage("1524758631624-e2822e304c36"), "南区宿舍 4 栋"),

                seed("学院风连帽卫衣", "L 码宽松版，墨绿色，无起球和明显污渍。", "服饰美妆", "九成新", "55.00", "129.00", demoImage("1521572163474-6864f9cf17ab"), "北区宿舍 4 栋"),
                seed("帆布通勤托特包", "可放 14 英寸电脑，内袋和拉链完好。", "服饰美妆", "九成新", "29.00", "79.00", demoImage("1551488831-00ddcb6c6bd3"), "图书馆咖啡厅"),
                seed("轻量缓震跑步鞋", "42 码，操场慢跑使用，鞋底磨损轻微。", "服饰美妆", "八成新", "88.00", "269.00", demoImage("1542291026-7eec264c27ff"), "体育馆西门"),
                seed("宿舍桌面化妆镜", "三档补光，可折叠收纳，充电功能正常。", "服饰美妆", "九成新", "39.00", "99.00", demoImage("1591047139829-d91aecb6caea"), "南区宿舍 5 栋"),
                seed("毕业答辩正装外套", "M 码深灰色，仅答辩穿过一次，可校内试穿。", "服饰美妆", "九成新", "108.00", "329.00", demoImage("1483985988355-763728e1935b"), "行政楼北门"),

                seed("尤克里里 23 英寸", "琴弦已更换，音准稳定，附琴包和调音器。", "其他", "八成新", "120.00", "299.00", demoImage("1511379938547-c1f69419868d"), "艺术中心"),
                seed("羽毛球双拍套装", "两支球拍配六只球，适合课后休闲使用。", "其他", "九成新", "68.00", "159.00", demoImage("1626224583764-f87db24ac4ea"), "体育馆南门"),
                seed("卡西欧科学计算器", "功能正常，适合高数、统计和工程课程考试。", "其他", "九成新", "45.00", "118.00", demoImage("1574607383476-f517f260d30b"), "教学楼 C 栋"),
                seed("20 寸登机行李箱", "万向轮顺滑，密码锁正常，箱体有轻微使用痕迹。", "其他", "八成新", "95.00", "239.00", demoImage("1565026057757-3407d5e646e3"), "北区宿舍 1 栋"),
                seed("自动折叠晴雨伞", "加固伞骨，深蓝色，开合顺畅。", "其他", "九成新", "25.00", "59.00", demoImage("1533282960533-51328aa49826"), "校门便利店"),

                seed("儿童木质积木套装", "益智启蒙玩具，包含 80 块彩色木质积木，边角圆润，配收纳桶。", "其他", "九成新", "39.00", "89.00", demoImage("1596461404969-9ae70f2830c1"), "北区宿舍 2 栋"),
                seed("动物世界儿童拼图", "100 片大块拼图，图案完整无缺片，适合儿童动手启蒙。", "其他", "九成新", "22.00", "49.00", demoImage("1618842676088-c4d48a6a7c9d"), "图书馆南门"),
                seed("小熊毛绒玩偶", "柔软短绒儿童玩具，高约 35 厘米，已清洗消毒。", "其他", "八成新", "28.00", "69.00", demoImage("1594787318286-3d835c1d207f"), "南区宿舍 1 栋"),
                seed("遥控越野玩具车", "前进、后退和转向功能正常，附遥控器，可校内试玩。", "其他", "九成新", "88.00", "159.00", demoImage("1558060370-d644479cb6f7"), "学生活动中心"),
                seed("儿童磁力片积木", "60 片磁力建构玩具，磁力正常，配图形搭建说明卡。", "其他", "九成新", "65.00", "129.00", demoImage("1594736797933-d0501ba2fe65"), "北区食堂")
        );
    }

    private String demoImage(String photoId) {
        return "https://images.unsplash.com/photo-" + photoId + "?auto=format&fit=crop&w=900&q=80";
    }

    private SeedProduct seed(String title, String description, String category, String condition,
                             String price, String originalPrice, String imageUrl, String location) {
        return new SeedProduct(title, description, category, condition, price, originalPrice, imageUrl, location,
                "23.1649870", "113.4461120");
    }

    private AppUser user(String email, String name, String university, UserRole role) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordHasher.hash("123456"));
        user.setDisplayName(name);
        user.setUniversity(university);
        user.setStudentVerified(role == UserRole.USER);
        user.setRole(role);
        return user;
    }

    private Product product(Long sellerId, String title, String description, String category, String condition,
                            String price, String originalPrice, String imageUrl, String location, String lat, String lng) {
        Product product = new Product();
        product.setSellerId(sellerId);
        product.setTitle(title);
        product.setDescription(description);
        product.setCategory(category);
        product.setCondition(condition);
        product.setPrice(new BigDecimal(price));
        product.setOriginalPrice(new BigDecimal(originalPrice));
        product.setImageUrl(imageUrl);
        product.setLocationName(location);
        product.setLatitude(new BigDecimal(lat));
        product.setLongitude(new BigDecimal(lng));
        product.setStatus(ProductStatus.AVAILABLE);
        return product;
    }

    private Coupon coupon(String code, String name, String discount, String minimum) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setName(name);
        coupon.setDiscountAmount(new BigDecimal(discount));
        coupon.setMinimumAmount(new BigDecimal(minimum));
        return coupon;
    }

    private void config(String key, String value) {
        if (!configs.existsById(key)) {
            AppConfig config = new AppConfig();
            config.setKey(key);
            config.setValue(value);
            configs.save(config);
        }
    }

    private void repairLegacyTileUrl() {
        String legacyOsm = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
        String legacyOsmShort = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
        String amapTile = "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}";
        configs.findById("map.tileUrl").ifPresent(cfg -> {
            boolean repaired = false;
            if (legacyOsm.equals(cfg.getValue()) || legacyOsmShort.equals(cfg.getValue())) {
                cfg.setValue(amapTile);
                repaired = true;
            }
            if (repaired || amapTile.equals(cfg.getValue())) {
                cfg.setValue(amapTile);
                configs.save(cfg);
                // 瓦片换为高德后，同步修正版权署名，避免界面残留 OpenStreetMap 版权行
                configs.findById("map.attribution").ifPresent(attr -> {
                    if (!"&copy; 高德地图".equals(attr.getValue())) {
                        attr.setValue("&copy; 高德地图");
                        configs.save(attr);
                    }
                });
            }
        });
    }

    /**
     * 自愈旧版校园坐标：早期种子 POI 与地图中心误用了广州科学城一带坐标（约 23.16x / 113.44x），
     * 与佛山南海狮山校址（广东东软学院，113.025887 / 23.138605）明显错位，导致发布页地图显示
     * 与真实校址不符、按地址无法搜索到。启动时检测旧坐标簇并批量纠正，避免老库不生效。
     */
    private void repairCampusCoordinates() {
        BigDecimal campusLat = new BigDecimal("23.138605");
        BigDecimal campusLng = new BigDecimal("113.025887");
        configs.findById("map.centerLat").ifPresent(cfg -> {
            if (isWrongCluster(cfg.getValue())) {
                cfg.setValue("23.138605");
                configs.save(cfg);
            }
        });
        configs.findById("map.centerLng").ifPresent(cfg -> {
            if (isWrongCluster(cfg.getValue())) {
                cfg.setValue("113.025887");
                configs.save(cfg);
            }
        });
        // 纠正仍停留在旧坐标簇的校园 POI
        for (CampusPoi poi : pois.findAll()) {
            if (isWrongCluster(poi.getLatitude().toPlainString())
                    || isWrongCluster(poi.getLongitude().toPlainString())) {
                BigDecimal[] fixed = campusFix(poi.getName(), campusLat, campusLng);
                poi.setLatitude(fixed[0]);
                poi.setLongitude(fixed[1]);
                pois.save(poi);
            }
        }
        // 纠正历史商品残留在旧坐标簇的位置
        for (Product product : products.findAll()) {
            if (product.getLatitude() == null || product.getLongitude() == null) continue;
            if (!isWrongCluster(product.getLatitude().toPlainString())
                    && !isWrongCluster(product.getLongitude().toPlainString())) continue;
            if (product.getPoiId() != null) {
                pois.findById(product.getPoiId()).ifPresent(poi -> {
                    product.setLatitude(poi.getLatitude());
                    product.setLongitude(poi.getLongitude());
                    products.save(product);
                });
            } else {
                product.setLatitude(campusLat);
                product.setLongitude(campusLng);
                products.save(product);
            }
        }
    }

    private boolean isWrongCluster(String value) {
        if (value == null) return false;
        try {
            double v = new BigDecimal(value).doubleValue();
            return (v >= 23.15 && v <= 23.18) || (v >= 113.40 && v <= 113.50);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private BigDecimal[] campusFix(String name, BigDecimal campusLat, BigDecimal campusLng) {
        return switch (name) {
            case "图书馆西门" -> new BigDecimal[]{new BigDecimal("23.1386100"), new BigDecimal("113.0249000")};
            case "北区宿舍 7 栋" -> new BigDecimal[]{new BigDecimal("23.1405000"), new BigDecimal("113.0239000")};
            case "南区宿舍 3 栋" -> new BigDecimal[]{new BigDecimal("23.1362000"), new BigDecimal("113.0268000")};
            case "实验楼 A 栋" -> new BigDecimal[]{new BigDecimal("23.1379000"), new BigDecimal("113.0245000")};
            case "创新创业学院" -> new BigDecimal[]{new BigDecimal("23.1382000"), new BigDecimal("113.0272000")};
            case "运动场看台" -> new BigDecimal[]{new BigDecimal("23.1398000"), new BigDecimal("113.0278000")};
            case "校门口广场" -> new BigDecimal[]{new BigDecimal("23.1373000"), new BigDecimal("113.0229000")};
            case "食堂一楼" -> new BigDecimal[]{new BigDecimal("23.1391000"), new BigDecimal("113.0250000")};
            case "教学楼 B 座" -> new BigDecimal[]{new BigDecimal("23.1389000"), new BigDecimal("113.0242000")};
            default -> new BigDecimal[]{campusLat, campusLng};
        };
    }

    private record SeedProduct(String title, String description, String category, String condition,
                               String price, String originalPrice, String imageUrl, String location,
                               String lat, String lng) {}
}
