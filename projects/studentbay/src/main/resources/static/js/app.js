'use strict';

const StudentBay = (() => {
    const page = document.body.dataset.page;
    const state = { user: null, cart: [], addresses: [], coupons: [], rewards: null, conversations: [], activeConversation: null };
    const THEME_KEY = 'studentbay_theme';
    const ACCENT_PRESETS = [
        { name: '校园绿', color: '#176b55' },
        { name: '海洋蓝', color: '#2563eb' },
        { name: '日落橙', color: '#ea580c' },
        { name: '樱花粉', color: '#db2777' },
        { name: '葡萄紫', color: '#7c3aed' },
        { name: '墨黑', color: '#374151' },
        { name: '中国红', color: '#dc2626' },
        { name: '青碧', color: '#0891b2' }
    ];
    function hexToRgb(hex) {
        const h = hex.replace('#', '');
        return { r: parseInt(h.substring(0, 2), 16), g: parseInt(h.substring(2, 4), 16), b: parseInt(h.substring(4, 6), 16) };
    }
    function rgbToHex(r, g, b) {
        return '#' + [r, g, b].map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join('');
    }
    function darken(hex, factor) {
        const c = hexToRgb(hex);
        return rgbToHex(c.r * factor, c.g * factor, c.b * factor);
    }
    function soften(hex, whiteRatio) {
        const c = hexToRgb(hex);
        return rgbToHex(c.r * (1 - whiteRatio) + 255 * whiteRatio, c.g * (1 - whiteRatio) + 255 * whiteRatio, c.b * (1 - whiteRatio) + 255 * whiteRatio);
    }
    function getThemeConfig() {
        try { return JSON.parse(localStorage.getItem(THEME_KEY)) || {}; } catch (e) { return {}; }
    }
    function saveThemeConfig(cfg) {
        localStorage.setItem(THEME_KEY, JSON.stringify(cfg));
    }
    function applyTheme() {
        const cfg = getThemeConfig();
        const mode = cfg.mode || 'auto';
        const root = document.documentElement;
        if (mode === 'auto') { root.removeAttribute('data-theme'); }
        else { root.setAttribute('data-theme', mode); }
        const targets = [root];
        if (document.body) targets.push(document.body);
        const setVar = (name, val) => targets.forEach(t => val ? t.style.setProperty(name, val) : t.style.removeProperty(name));
        if (cfg.accent) {
            setVar('--campus', cfg.accent);
            setVar('--campus-dark', darken(cfg.accent, 0.6));
            setVar('--campus-soft', soften(cfg.accent, 0.9));
        } else {
            setVar('--campus', null);
            setVar('--campus-dark', null);
            setVar('--campus-soft', null);
        }
        if (cfg.navbar) {
            const c = hexToRgb(cfg.navbar);
            setVar('--navbar-bg', 'rgba(' + c.r + ',' + c.g + ',' + c.b + ',.96)');
        } else {
            setVar('--navbar-bg', null);
        }
    }
    applyTheme();
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', applyTheme);


    const fallbackImage = "data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 width=%27800%27 height=%27600%27%3E%3Crect fill=%27%23e8f4ef%27 width=%27800%27 height=%27600%27/%3E%3Cpath d=%27M260 390h280v30H260zM300 250h200v140H300zM270 225l130-75 130 75z%27 fill=%27%23176b55%27 opacity=%27.7%27/%3E%3Ctext x=%27400%27 y=%27490%27 text-anchor=%27middle%27 font-family=%27sans-serif%27 font-size=%2728%27 fill=%27%230c3e34%27%3EStudentBay%3C/text%3E%3C/svg%3E";

    async function api(path, options = {}, silent = false) {
        const request = { credentials: 'same-origin', ...options };
        const isForm = request.body instanceof FormData;
        request.headers = isForm ? (request.headers || {}) : { 'Content-Type': 'application/json', ...(request.headers || {}) };
        const response = await fetch(path, request);
        let payload;
        try { payload = await response.json(); } catch { payload = { message: '服务器返回了无法识别的内容' }; }
        if (!response.ok) {
            if (response.status === 401 && !silent) {
                location.href = `/login.html?return=${encodeURIComponent(location.pathname + location.search)}`;
                throw new Error('请先登录');
            }
            throw new Error(payload.message || '操作失败');
        }
        return payload.data;
    }

    const esc = value => String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
    const money = value => `¥${Number(value || 0).toFixed(2)}`;
    const dateTime = value => value ? new Date(value).toLocaleString('zh-CN', { month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit' }) : '';
    const query = name => new URLSearchParams(location.search).get(name);
    const empty = (icon, title, text) => `<div class="empty"><div class="empty-icon">${icon}</div><h3>${title}</h3><p>${text}</p></div>`;

    function toast(message) {
        const element = document.getElementById('toast');
        if (!element) return;
        element.textContent = message;
        element.classList.add('show');
        clearTimeout(toast.timer);
        toast.timer = setTimeout(() => element.classList.remove('show'), 2800);
    }

    async function loadUser() {
        try { state.user = await api('/api/auth/me', {}, true); } catch { state.user = null; }
        return state.user;
    }

    function requireLogin() {
        if (!state.user) {
            location.href = `/login.html?return=${encodeURIComponent(location.pathname + location.search)}`;
            return false;
        }
        return true;
    }

    async function renderHeader() {
        const header = document.getElementById('siteHeader');
        if (!header) return;
        await loadUser();
        const adminLink = state.user?.role === 'ADMIN' ? '<a href="/admin.html">管理后台</a>' : '';
        const account = state.user
            ? `<button id="accountAvatar" class="avatar-btn" title="个人中心" aria-label="个人中心">${state.user.avatarUrl ? `<img src="${esc(state.user.avatarUrl)}" alt="头像">` : esc(state.user.displayName.slice(0, 1))}</button>`
            : '<a class="login-btn" href="/login.html">登录</a><a class="login-btn" href="/login.html?mode=register">注册</a>';
        header.className = 'topbar';
        header.innerHTML = `<div class="topbar-inner"><a class="brand" href="/" aria-label="StudentBay 首页"><span class="brand-mark">SB</span>StudentBay</a><nav id="mainNav" class="nav" aria-label="主导航"><a href="/"${page === 'home' ? ' class="active" aria-current="page"' : ''}>逛校园</a><a href="/favorites.html">收藏</a><a href="/messages.html">消息<span id="unreadBadge" class="badge hidden">0</span></a><a href="/orders.html">订单</a><a href="/cart.html">购物车<span id="cartBadge" class="badge hidden">0</span></a><a class="reward-nav" href="/rewards.html">任务中心 <span id="coinNav">0币</span></a>${adminLink}<a class="publish-link" href="/publish.html">发布</a>${account}</nav></div>`;
        document.getElementById('accountAvatar')?.addEventListener('click', () => { location.href = '/profile.html'; });
        if (state.user) {
            Promise.all([api('/api/cart/count'), api('/api/chat/unread-count'), api('/api/rewards/summary')]).then(([cartCount, unread, rewards]) => {
                setBadge('cartBadge', cartCount);
                setBadge('unreadBadge', unread);
                state.rewards = rewards;
                const coinNav = document.getElementById('coinNav');
                if (coinNav) coinNav.textContent = `${rewards.coinBalance}币`;
            }).catch(() => {});
        }
    }

    async function initProfile() {
        if (!requireLogin()) return;
        const user = state.user;
        const profileAvatar = user.avatarUrl ? `<img src="${esc(user.avatarUrl)}" alt="头像">` : esc(user.displayName.slice(0, 1));
        document.getElementById('profileCard').innerHTML = `<div class="profile-hero"><a class="avatar profile-avatar space-entry" href="/my-space.html" title="查看我的空间 · 宝贝 / 评价 / 动态">${profileAvatar}</a><div><span class="eyebrow" style="color:var(--campus)">Campus account</span><h1 style="margin:6px 0 8px">${esc(user.displayName)}</h1><p style="color:var(--muted);margin:0 0 12px">${esc(user.email)}　·　${esc(user.university || '校园用户')}</p><div><span class="status">${user.studentVerified ? '已认证校园身份' : '待认证'}</span>${user.role === 'ADMIN' ? ' <span class="status warning">管理员</span>' : ''}<span id="profileTrust" class="trust-inline">Trust Score 计算中</span></div></div><a class="settings-btn" href="/settings.html" title="账号设置">⚙ 设置</a></div>`;
        if (user.role === 'ADMIN') document.getElementById('profileAdminLink').classList.remove('hidden');
        Promise.all([api('/api/cart/count'), api('/api/chat/unread-count'), api('/api/rewards/summary'), api('/api/trust/me')]).then(([cartCount, unread, rewards, trust]) => {
            setBadge('profileCartBadge', cartCount);
            setBadge('profileUnreadBadge', unread);
            state.rewards = rewards;
            const coin = document.getElementById('profileCoinBadge');
            if (coin) coin.textContent = `${rewards.coinBalance}币`;
            const trustElement = document.getElementById('profileTrust');
            if (trustElement) trustElement.innerHTML = `<b>${trust.score}</b> Trust Score · ${esc(trust.levelLabel)}`;
        }).catch(() => {});
        document.getElementById('logoutButton').addEventListener('click', async () => {
            if (confirm('要退出当前账号吗？')) {
                await api('/api/auth/logout', { method: 'POST' });
                location.href = '/';
            }
        });
    }

    async function initSettings() {
        if (!requireLogin()) return;
        const preview = document.getElementById('avatarPreview');
        const entryAvatar = document.getElementById('entryAvatar');
        const renderAvatar = () => {
            const url = state.user.avatarUrl;
            const html = url ? `<img src="${esc(url)}" alt="头像">` : `<span>${esc(state.user.displayName.slice(0, 1))}</span>`;
            preview.innerHTML = html;
            if (entryAvatar) entryAvatar.innerHTML = html;
        };
        renderAvatar();
        const updateEntrySummaries = () => {
            const profileDesc = document.getElementById('entryProfileDesc');
            if (profileDesc) profileDesc.textContent = state.user.displayName + ' · ' + state.user.email;
            updateThemeEntryDesc();
            const addrDesc = document.getElementById('entryAddressDesc');
            if (addrDesc) {
                const def = (state.addresses || []).find(a => a.defaultAddress) || (state.addresses || [])[0];
                addrDesc.textContent = def ? def.recipientName + ' ' + maskPhone(def.phone) + ' · ' + def.detail : '暂无收货地址，点击添加';
            }
        };
        updateEntrySummaries();
        document.getElementById('displayName').value = state.user.displayName;
        document.getElementById('email').value = state.user.email;
        document.getElementById('university').value = state.user.university || '';
        let pendingAvatarFile = null;
        let pendingPreviewUrl = null;
        document.getElementById('avatarPickBtn').addEventListener('click', () => document.getElementById('avatarInput').click());
        document.getElementById('avatarInput').addEventListener('change', () => {
            const file = document.getElementById('avatarInput').files[0];
            if (!file) return;
            if (file.size > 5 * 1024 * 1024) { toast('头像图片不能超过 5MB'); return; }
            if (pendingPreviewUrl) URL.revokeObjectURL(pendingPreviewUrl);
            pendingPreviewUrl = URL.createObjectURL(file);
            preview.innerHTML = `<img src="${esc(pendingPreviewUrl)}" alt="新头像预览">`;
            pendingAvatarFile = file;
            toast('已选择新头像，点击「保存修改」后生效');
            document.getElementById('avatarInput').value = '';
        });
        document.getElementById('profileForm').addEventListener('submit', async event => {
            event.preventDefault();
            const button = event.currentTarget.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                if (pendingAvatarFile) {
                    const form = new FormData();
                    form.append('file', pendingAvatarFile);
                    const result = await api('/api/account/avatar', { method: 'POST', body: form });
                    state.user.avatarUrl = result.avatarUrl;
                }
                Object.assign(state.user, await api('/api/account/profile', { method: 'PUT', body: JSON.stringify({
                    displayName: document.getElementById('displayName').value,
                    email: document.getElementById('email').value,
                    university: document.getElementById('university').value
                }) }));
                if (pendingPreviewUrl) URL.revokeObjectURL(pendingPreviewUrl);
                pendingAvatarFile = null;
                pendingPreviewUrl = null;
                renderAvatar();
                updateEntrySummaries();
                toast('资料已保存');
            } catch (error) { toast(error.message); }
            button.disabled = false;
        });
        try {
            state.addresses = await api('/api/account/addresses');
        } catch (error) { toast(error.message); }
        renderAddresses();
        updateEntrySummaries();
        document.getElementById('showAddressForm').addEventListener('click', () => openAddressForm());
        document.getElementById('cancelAddressEdit').addEventListener('click', () => closeAddressForm());
        document.getElementById('addressForm').addEventListener('submit', saveAddress);
        document.getElementById('passwordForm').addEventListener('submit', async event => {
            event.preventDefault();
            const form = event.currentTarget;
            const next = document.getElementById('newPassword').value;
            if (next !== document.getElementById('confirmPassword').value) { toast('两次输入的新密码不一致'); return; }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await api('/api/account/password', { method: 'PUT', body: JSON.stringify({
                    oldPassword: document.getElementById('currentPassword').value,
                    newPassword: next
                }) });
                form.reset();
                toast('密码已修改，下次登录请使用新密码');
            } catch (error) { toast(error.message); }
            button.disabled = false;
        });
        const showSettingsView = (view) => {
            document.getElementById('settingsMenu').classList.toggle('hidden', view !== 'menu');
            document.getElementById('profilePanel').classList.toggle('hidden', view !== 'profile');
            document.getElementById('addressPanel').classList.toggle('hidden', view !== 'address');
            document.getElementById('passwordPanel').classList.toggle('hidden', view !== 'password');
            const themePanel = document.getElementById('themePanel');
            if (themePanel) themePanel.classList.toggle('hidden', view !== 'theme');
            window.scrollTo({ top: 0, behavior: 'instant' });
        };
        document.getElementById('entryProfile').addEventListener('click', () => showSettingsView('profile'));
        document.getElementById('entryAddress').addEventListener('click', () => showSettingsView('address'));
        document.getElementById('entryPassword').addEventListener('click', () => showSettingsView('password'));
        const entryTheme = document.getElementById('entryTheme');
        if (entryTheme) entryTheme.addEventListener('click', () => { showSettingsView('theme'); initThemePanel(); });
        document.querySelectorAll('[data-back]').forEach(btn => btn.addEventListener('click', () => showSettingsView('menu')));
    }

    function initThemePanel() {
        const cfg = getThemeConfig();
        const mode = cfg.mode || 'auto';
        // Mode buttons
        document.querySelectorAll('#themeModeGroup .theme-mode-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.mode === mode);
            btn.onclick = () => {
                const c = getThemeConfig();
                c.mode = btn.dataset.mode;
                if (c.mode === 'auto') delete c.mode;
                saveThemeConfig(c);
                applyTheme();
                document.querySelectorAll('#themeModeGroup .theme-mode-btn').forEach(b => b.classList.toggle('active', b === btn));
                updateThemeEntryDesc();
            };
        });
        // Accent swatches
        const swatchContainer = document.getElementById('accentSwatches');
        const currentAccent = cfg.accent || '#176b55';
        swatchContainer.innerHTML = ACCENT_PRESETS.map(p =>
            `<button class="color-swatch ${p.color.toLowerCase() === currentAccent.toLowerCase() ? 'active' : ''}" data-color="${p.color}" style="background:${p.color}" title="${p.name}" type="button"></button>`
        ).join('');
        swatchContainer.querySelectorAll('.color-swatch').forEach(sw => {
            sw.onclick = () => {
                const c = getThemeConfig();
                c.accent = sw.dataset.color;
                saveThemeConfig(c);
                applyTheme();
                document.getElementById('accentPicker').value = sw.dataset.color;
                document.getElementById('accentHex').textContent = sw.dataset.color;
                swatchContainer.querySelectorAll('.color-swatch').forEach(s => s.classList.toggle('active', s === sw));
            };
        });
        // Accent custom picker
        const accentPicker = document.getElementById('accentPicker');
        accentPicker.value = currentAccent;
        document.getElementById('accentHex').textContent = currentAccent;
        accentPicker.oninput = () => {
            const c = getThemeConfig();
            c.accent = accentPicker.value;
            saveThemeConfig(c);
            applyTheme();
            document.getElementById('accentHex').textContent = accentPicker.value;
            swatchContainer.querySelectorAll('.color-swatch').forEach(s => s.classList.toggle('active', s.dataset.color.toLowerCase() === accentPicker.value.toLowerCase()));
        };
        // Navbar picker
        const navbarPicker = document.getElementById('navbarPicker');
        const currentNavbar = cfg.navbar || '#ffffff';
        navbarPicker.value = currentNavbar;
        document.getElementById('navbarHex').textContent = currentNavbar;
        navbarPicker.oninput = () => {
            const c = getThemeConfig();
            c.navbar = navbarPicker.value;
            saveThemeConfig(c);
            applyTheme();
            document.getElementById('navbarHex').textContent = navbarPicker.value;
        };
        document.getElementById('navbarTransparent').onclick = () => {
            const c = getThemeConfig();
            delete c.navbar;
            saveThemeConfig(c);
            applyTheme();
            navbarPicker.value = '#ffffff';
            document.getElementById('navbarHex').textContent = '#ffffff';
        };
        // Reset
        document.getElementById('themeReset').onclick = () => {
            localStorage.removeItem(THEME_KEY);
            applyTheme();
            initThemePanel();
            updateThemeEntryDesc();
            toast('已恢复默认主题');
        };
    }
    function updateThemeEntryDesc() {
        const el = document.getElementById('entryThemeDesc');
        if (!el) return;
        const cfg = getThemeConfig();
        const modeLabel = { auto: '跟随系统', light: '浅色模式', dark: '深色模式' };
        const mode = cfg.mode || 'auto';
        let desc = modeLabel[mode];
        if (cfg.accent) desc += ' · 自定义主题色';
        if (cfg.navbar) desc += ' · 自定义导航栏';
        el.textContent = desc;
    }

    async function initBought() {
        if (!requireLogin()) return;
        try {
            const items = await api('/api/account/bought');
            document.getElementById('boughtGrid').innerHTML = items.length
                ? items.map(item => productCard(item, item.quantity > 1 ? `已买 ${item.quantity} 件` : '已买入')).join('')
                : empty('⤓', '还没有买入的商品', '去逛逛商城，发现心仪的闲置好物吧');
        } catch (error) { toast(error.message); }
    }

async function initMySpace() {
        if (!requireLogin()) return;
        const user = state.user;
        const avatar = document.getElementById('spaceAvatar');
        avatar.innerHTML = user.avatarUrl ? `<img src="${esc(user.avatarUrl)}" alt="头像">` : `<span>${esc(user.displayName.slice(0, 1))}</span>`;
        document.getElementById('spaceName').textContent = user.displayName;
        document.getElementById('spaceMeta').textContent = `${user.email}　·　${user.university || '校园用户'}`;
        document.getElementById('spaceBadges').innerHTML = `<span class="status">${user.studentVerified ? '已认证校园身份' : '待认证'}</span>${user.role === 'ADMIN' ? ' <span class="status warning">管理员</span>' : ''}`;
        document.querySelectorAll('.space-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.space-tab').forEach(button => {
                    button.classList.toggle('active', button === tab);
                    button.setAttribute('aria-selected', String(button === tab));
                });
                document.querySelectorAll('.space-pane').forEach(pane => {
                    const show = pane.id === tab.dataset.pane;
                    pane.hidden = !show;
                    pane.classList.toggle('active', show);
                });
            });
        });
        document.querySelectorAll('.review-sub-tab').forEach(tab => tab.addEventListener('click', () => {
            document.querySelectorAll('.review-sub-tab').forEach(t => t.classList.toggle('active', t === tab));
            loadSpaceReviews(tab.dataset.reviewType);
        }));
        loadSpaceCredit();
        Promise.allSettled([loadSpaceProducts(), loadSpaceActivity(), loadSpaceReviews('BUYER_REVIEWS_SELLER')]);
    }

    async function loadSpaceReviews(type) {
        const list = document.getElementById('reviewList');
        const summary = document.getElementById('reviewSummary');
        if (!list) return;
        try {
            const data = await api(`/api/reviews/user/${state.user.id}?type=${type}`);
            const avg = data.averageRating || 0;
            const stars = '★'.repeat(Math.round(avg)) + '☆'.repeat(5 - Math.round(avg));
            summary.innerHTML = `<span class="avg">${avg ? avg.toFixed(1) : '—'}</span><span class="avg-stars">${stars}</span><span class="count">共 ${data.total} 条评价</span>`;
            list.innerHTML = data.reviews.length ? data.reviews.map(r => {
                const rs = '★'.repeat(r.rating) + '☆'.repeat(5 - r.rating);
                const initial = (r.reviewerName || '?').slice(0, 1);
                const avatar = r.reviewerAvatar ? `<img src="${esc(r.reviewerAvatar)}" alt="">` : esc(initial);
                return `<article class="review-card"><div class="review-card-head"><div class="review-card-avatar">${avatar}</div><div class="review-card-meta"><strong>${esc(r.reviewerName)}</strong><small>${dateTime(r.createdAt)}</small></div><span class="review-card-rating">${rs}</span></div><p class="review-card-content">${esc(r.content || '（未填写评价内容）')}</p></article>`;
            }).join('') : empty('⭐', '还没有收到评价', '完成交易后，对方可以在这里留下评价');
        } catch (error) { list.innerHTML = empty('⚠', '加载失败', error.message); }
    }

    const spaceStatus = {
        SOLD: { label: '已卖出', cls: 'sold' },
        RESERVED: { label: '已预订', cls: 'reserved' },
        OFF_SHELF: { label: '已下架', cls: 'off' },
        AVAILABLE: { label: '', cls: '' }
    };

    async function loadSpaceProducts() {
        const grid = document.getElementById('spaceProductGrid');
        if (!grid) return;
        try {
            const items = await api('/api/products/mine/list');
            grid.innerHTML = items.length
                ? items.map(product => productCard(product, '', (spaceStatus[product.status] || spaceStatus.AVAILABLE).cls)).join('')
                : empty('🏷', '还没有发布过宝贝', '去发布第一件闲置，让好物流转起来');
        } catch (error) { grid.innerHTML = empty('⚠', '加载失败', error.message); }
    }

    async function loadSpaceActivity() {
        const timeline = document.getElementById('spaceTimeline');
        if (!timeline) return;
        try {
            const items = await api('/api/account/activities');
            timeline.innerHTML = items.length
                ? items.map(item => {
                    const sold = item.type === 'SOLD';
                    return `<div class="timeline-item ${sold ? 'sold' : 'publish'}" data-product-id="${item.productId ?? ''}"><span class="timeline-dot">${sold ? '售' : '新'}</span><div class="timeline-content"><p><strong>${sold ? '宝贝被买走' : '上架新宝贝'}</strong><span class="timeline-note">${sold ? '买家已支付成功，等待交付' : '商品已在校园市集上线'}</span></p><h4>${esc(item.title)}</h4><small>${dateTime(item.createdAt)}</small></div></div>`;
                }).join('')
                : empty('✦', '还没有动态', '上架新宝贝或被买走后，这里会留下时间印记');
            timeline.querySelectorAll('.timeline-item[data-product-id]').forEach(node => {
                if (node.dataset.productId) node.addEventListener('click', () => { location.href = `/product-detail.html?id=${node.dataset.productId}`; });
            });
        } catch (error) { timeline.innerHTML = empty('⚠', '加载失败', error.message); }
    }


    async function loadSpaceCredit() {
        const box = document.getElementById('spaceCredit');
        if (!box) return;
        const kinds = ['buyer', 'seller'];
        const parts = {};
        kinds.forEach(kind => {
            parts[kind] = {
                score: document.getElementById(kind + 'CreditScore'),
                bar: document.getElementById(kind + 'CreditBar'),
                level: document.getElementById(kind + 'CreditLevel')
            };
        });
        try {
            const data = await api('/api/space/credit');
            box.dataset.loaded = '1';
            kinds.forEach(kind => {
                const key = kind === 'buyer' ? 'buyerCredit' : 'sellerCredit';
                parts[kind].score.textContent = String(data[key] ?? '--');
                parts[kind].bar.style.width = (data[key] ?? 0) + '%';
                parts[kind].level.textContent = data[key === 'buyer' ? 'buyerLevel' : 'sellerLevel'] || '';
            });
            const put = (id, value) => { const el = document.getElementById(id); if (el) el.textContent = String(value ?? 0); };
            put('cSold', data.soldCount);
            put('cBought', data.boughtCount);
            put('cSellerGood', data.sellerGoodReviews);
            put('cBuyerGood', data.buyerGoodReviews);
            put('cReported', data.reportedCount);
            const rule = document.getElementById('creditRule');
            if (rule) rule.textContent = data.rule || '';
        } catch (error) {
            kinds.forEach(kind => {
                parts[kind].score.textContent = '--';
                parts[kind].level.textContent = '加载失败';
            });
        }
        box.querySelectorAll('.credit-card').forEach(card => {
            card.addEventListener('click', () => {
                const detail = document.getElementById('creditDetail');
                const opening = detail.hidden;
                detail.hidden = false;
                box.querySelectorAll('.credit-card').forEach(c => {
                    c.classList.toggle('active', opening && c === card);
                    c.setAttribute('aria-expanded', String(opening && c === card));
                });
                if (opening) detail.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            });
        });
        const closeBtn = document.getElementById('creditClose');
        if (closeBtn) closeBtn.addEventListener('click', () => {
            const detail = document.getElementById('creditDetail');
            detail.hidden = true;
            box.querySelectorAll('.credit-card').forEach(c => {
                c.classList.remove('active');
                c.setAttribute('aria-expanded', 'false');
            });
        });
    }

    function setBadge(id, count) {
        const badge = document.getElementById(id);
        if (!badge) return;
        badge.textContent = count > 99 ? '99+' : count;
        badge.classList.toggle('hidden', !count);
    }

    let revealObserver;
    let motionMutationObserver;

    const motionTargetSelector = [
        '.page-heading', '.steps', '.tabs', '.reward-hero', '.compare-hero', '.logistics-hero',
        '.payment-success-card', '.search-panel', '.category-rail', '.daily-task-head', '.dice-world',
        'main > .card', '.settings-panel', '.profile-grid > .profile-item', '.product-grid > .product-card',
        '.cart-list > .cart-item', '.task-list > .task-card', '.compare-list > .compare-row',
        '#compareStats > .stat-card', '#buyerOrderList > .order-card', '#sellerOrderList > .order-card',
        '.timeline > .timeline-item', '.conversation-list > .conversation', '.checkout-layout > :not(.summary)',
        '.order-detail-layout > :not(.summary)', '.courier-grid > *', '.detail-support-grid > *',
        '.admin-layout > :not(.admin-nav)', '.reward-content-layout > :not(aside)', '.messages-layout'
    ].join(',');

    function decorateMotionTargets(root = document) {
        const candidates = [];
        if (root instanceof Element && root.matches(motionTargetSelector)) candidates.push(root);
        if (root.querySelectorAll) candidates.push(...root.querySelectorAll(motionTargetSelector));
        candidates.forEach((node, index) => {
            if (node.hasAttribute('data-reveal')) return;
            if (node.closest('.modal, .sheet-backdrop, .story-stack, .dice-board, .chat-body, .table-wrap')) return;
            const fadeOnly = node.matches('.messages-layout, .dice-world');
            const paired = node.matches('.checkout-layout > :not(.summary), .order-detail-layout > :not(.summary), .courier-grid > *, .detail-support-grid > *, .admin-layout > :not(.admin-nav)');
            node.dataset.reveal = fadeOnly ? 'fade' : paired ? (index % 2 ? 'right' : 'left') : 'up';
        });
    }

    function setupRevealMotion(root = document) {
        const nodes = [];
        if (root instanceof Element && root.matches('[data-reveal]:not([data-reveal-ready])')) nodes.push(root);
        if (root.querySelectorAll) nodes.push(...root.querySelectorAll('[data-reveal]:not([data-reveal-ready])'));
        if (!nodes.length) return;
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduceMotion || !('IntersectionObserver' in window)) {
            nodes.forEach(node => {
                node.dataset.revealReady = 'true';
                node.classList.add('is-visible');
            });
            return;
        }
        if (!revealObserver) {
            revealObserver = new IntersectionObserver(entries => {
                entries.forEach(entry => {
                    if (!entry.isIntersecting) return;
                    entry.target.classList.add('is-visible');
                    revealObserver.unobserve(entry.target);
                });
            }, { threshold: .12, rootMargin: '0px 0px -5% 0px' });
        }
        nodes.forEach((node, index) => {
            node.dataset.revealReady = 'true';
            node.style.setProperty('--reveal-delay', `${Math.min(index % 4, 3) * 70}ms`);
            revealObserver.observe(node);
        });
    }

    function setupPageMotion() {
        document.body.classList.add('motion-ready');
        decorateMotionTargets();
        setupRevealMotion();
        if (!('MutationObserver' in window) || motionMutationObserver) return;
        motionMutationObserver = new MutationObserver(mutations => {
            mutations.forEach(mutation => mutation.addedNodes.forEach(node => {
                if (!(node instanceof Element)) return;
                decorateMotionTargets(node);
                setupRevealMotion(node);
            }));
        });
        motionMutationObserver.observe(document.body, { childList: true, subtree: true });
        window.addEventListener('pagehide', () => {
            motionMutationObserver?.disconnect();
            revealObserver?.disconnect();
        }, { once: true });
    }

    function setupStickerTrail() {
        const host = document.getElementById('stickerTrail');
        if (!host || host.dataset.ready) return;
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || window.matchMedia('(pointer: coarse)').matches) return;
        host.dataset.ready = 'true';
        const assets = [
            { label:'¥', tone:'acid' },
            { label:'SOLD', tone:'ink' },
            { label:'包邮', tone:'paper' },
            { label:'验货', tone:'mint' },
            { label:'议价', tone:'acid' },
            { label:'校园', tone:'paper' },
            { label:'RECYCLE', tone:'ink' },
            { label:'已认证', tone:'mint' }
        ];
        let lastX = -200;
        let lastY = -200;
        let lastTime = 0;
        let assetIndex = 0;
        document.addEventListener('pointermove', event => {
            if (event.pointerType && event.pointerType !== 'mouse') return;
            const now = performance.now();
            const distance = Math.hypot(event.clientX - lastX, event.clientY - lastY);
            if (distance < 72 || now - lastTime < 54) return;
            lastX = event.clientX;
            lastY = event.clientY;
            lastTime = now;
            const asset = assets[assetIndex++ % assets.length];
            const sticker = document.createElement('span');
            sticker.className = 'trail-sticker';
            sticker.dataset.tone = asset.tone;
            sticker.textContent = asset.label;
            sticker.style.left = `${event.clientX}px`;
            sticker.style.top = `${event.clientY}px`;
            host.appendChild(sticker);
            const rotation = -16 + Math.random() * 32;
            const drift = -18 + Math.random() * 36;
            const animation = sticker.animate([
                { transform:`translate3d(-50%, -45%, 0) rotate(${rotation}deg) scale(.72)`, opacity:0 },
                { transform:`translate3d(-50%, -58%, 0) rotate(${rotation * .45}deg) scale(1)`, opacity:1, offset:.18 },
                { transform:`translate3d(calc(-50% + ${drift}px), -105%, 0) rotate(${rotation + drift * .35}deg) scale(.84)`, opacity:0 }
            ], { duration:780, easing:'cubic-bezier(.16,1,.3,1)', fill:'forwards' });
            animation.onfinish = () => sticker.remove();
            while (host.childElementCount > 12) host.firstElementChild.remove();
        }, { passive:true });
    }

    function hydrateHomeEditorial(products) {
        const stage = document.getElementById('heroProductStage');
        if (!stage) return;
        if (!products.length) {
            stage.innerHTML = '<div class="hero-empty">新的校园好物很快出现</div>';
            return;
        }
        const featured = Array.from({ length:Math.min(3, Math.max(products.length, 1)) }, (_, index) => products[index % products.length]);
        stage.innerHTML = featured.map((product, index) => `<a class="hero-product-card hero-product-card-${index + 1}" href="/product-detail.html?id=${product.id}"><img src="${esc(product.imageUrl || fallbackImage)}" alt="${esc(product.title)}" onerror="this.src='${fallbackImage}'"><span><strong>${esc(product.title)}</strong><small>${money(product.price)}</small></span></a>`).join('');
        ['storyImageOne','storyImageTwo','storyImageThree'].forEach((id, index) => {
            const image = document.getElementById(id);
            const product = products[index % products.length];
            if (!image || !product) return;
            image.src = product.imageUrl || fallbackImage;
            image.alt = product.title;
            image.hidden = false;
            image.onerror = () => { image.src = fallbackImage; };
        });
    }

    function productCard(product, badge = '', extraClass = '') {
        const stateLabels = { sold: '已卖出', reserved: '已预订', off: '已下架' };
        const stateTag = extraClass && stateLabels[extraClass] ? `<span class="state-tag ${extraClass}">${stateLabels[extraClass]}</span>` : '';
        return `<a class="product-card${extraClass ? ` ${extraClass}` : ''}" data-reveal href="/product-detail.html?id=${product.id}"><div class="product-image"><img src="${esc(product.imageUrl || fallbackImage)}" alt="${esc(product.title)}" loading="lazy" onerror="this.src='${fallbackImage}'">${stateTag || (badge ? `<span class="bought-tag">${esc(badge)}</span>` : '')}</div><div class="product-body"><div class="product-card-topline"><span class="condition-tag">${esc(product.condition)}</span><span>${esc(product.category)}</span></div><h3 class="product-title">${esc(product.title)}</h3><div class="product-price-row"><span class="price">${Number(product.price).toFixed(2)}</span>${product.originalPrice ? `<span class="old-price">¥${Number(product.originalPrice).toFixed(2)}</span>` : ''}</div><div class="product-meta"><span>${esc(product.locationName || '校内交易')}</span><span>${product.favoriteCount ?? 0} 人收藏</span></div></div></a>`;
    }

    // T4.1 分类 Tab 与发布页下拉共用：从 /api/categories 读取，不再写死
    async function loadCategories() {
        try {
            const categories = await api('/api/categories');
            const select = document.getElementById('category');
            if (select) {
                select.innerHTML = '<option value="">全部分类</option>' + categories
                    .map(item => `<option value="${esc(item.name)}">${esc(item.name)}</option>`).join('');
            }
            const rail = document.getElementById('categoryRail');
            if (rail) {
                rail.innerHTML = categories.slice(0, 6).map(item =>
                    `<a href="#marketplace" data-category-link="${esc(item.name)}">${item.icon ? esc(item.icon) : ''}<span>${esc(item.name)}</span></a>`).join('');
                rail.querySelectorAll('[data-category-link]').forEach(link => link.addEventListener('click', () => {
                    const target = document.getElementById('category');
                    if (target) {
                        target.value = link.dataset.categoryLink;
                        target.dispatchEvent(new Event('change'));
                    }
                }));
            }
        } catch { /* 接口失败时保留静态兜底 */ }
    }

    // T4.2 热搜词：点击直接带入搜索框并触发搜索
    async function loadHotKeywords() {
        const box = document.getElementById('hotKeywords');
        if (!box) return;
        try {
            const list = await api('/api/products/hot-keywords');
            if (!list || !list.length) { box.hidden = true; return; }
            box.innerHTML = '<span class="hot-label">热门搜索</span>' + list.map(item =>
                `<button type="button" class="hot-chip" data-kw="${esc(item.keyword)}">${esc(item.keyword)}</button>`).join('');
            box.hidden = false;
            box.querySelectorAll('.hot-chip').forEach(button => button.addEventListener('click', () => {
                const keyword = document.getElementById('keyword');
                if (keyword) keyword.value = button.dataset.kw;
                document.getElementById('filterForm').dispatchEvent(new Event('submit'));
            }));
        } catch { box.hidden = true; }
    }

    async function initHome() {
        await Promise.all([loadCategories(), loadHotKeywords()]);
        const form = document.getElementById('filterForm');
        const naturalForm = document.getElementById('naturalSearchForm');
        const grid = document.getElementById('productGrid');
        const resultText = document.getElementById('resultText');
        const loadMoreBtn = document.getElementById('loadMoreBtn');
        let page = 0;
        let busy = false;
        // T4.2 服务端分页：page/size，列表接口返回 {items,total,page,size}
        const load = async (reset = true) => {
            if (busy) return;
            busy = true;
            const params = new URLSearchParams();
            for (const id of ['keyword','category','condition','sort']) {
                const el = document.getElementById(id);
                if (el && el.value) params.set(id, el.value);
            }
            const minPrice = document.getElementById('minPrice')?.value;
            const maxPrice = document.getElementById('maxPrice')?.value;
            if (minPrice) params.set('minPrice', minPrice);
            if (maxPrice) params.set('maxPrice', maxPrice);
            params.set('page', reset ? 0 : page + 1);
            params.set('size', 20);
            try {
                const data = await api(`/api/products?${params}`);
                const items = data.items || [];
                if (reset) { grid.innerHTML = items.length ? items.map(productCard).join('') : empty('⌕', '暂时没有符合条件的商品', '试试更换关键词或筛选条件'); }
                else if (items.length) grid.insertAdjacentHTML('beforeend', items.map(productCard).join(''));
                page = data.page;
                resultText.textContent = `找到 ${data.total} 件在售商品`;
                if (loadMoreBtn) {
                    const hasMore = (data.page + 1) * data.size < data.total;
                    loadMoreBtn.hidden = !hasMore;
                    loadMoreBtn.disabled = false;
                }
                if (reset) hydrateHomeEditorial(items);
                setupRevealMotion(grid);
            } finally {
                busy = false;
            }
        };
        form.addEventListener('submit', event => { event.preventDefault(); load(true).catch(error => toast(error.message)); });
        form.querySelectorAll('select').forEach(select => select.addEventListener('change', () => load(true).catch(error => toast(error.message))));
        form.querySelectorAll('input').forEach(input => input.addEventListener('keydown', event => {
            if (event.key === 'Enter') { event.preventDefault(); load(true).catch(error => toast(error.message)); }
        }));
        naturalForm?.addEventListener('submit', async event => {
            event.preventDefault();
            const input = document.getElementById('naturalQuery');
            const button = document.getElementById('naturalSearchButton');
            const interpretation = document.getElementById('naturalInterpretation');
            if (!input.value.trim()) { toast('请先描述你想找的商品'); return; }
            button.disabled = true;
            button.textContent = '正在理解需求…';
            interpretation.innerHTML = '<span class="intent-thinking">正在拆解预算、用途与交易偏好</span>';
            try {
                const result = await api('/api/products/natural-search', {method:'POST',body:JSON.stringify({query:input.value.trim()})});
                const intent = result.interpretation;
                const chips = [intent.maxPrice != null ? `预算 ≤ ${money(intent.maxPrice)}` : '', intent.category || '', intent.condition || '', intent.preferPickup ? '优先校园面交' : '', ...(intent.keywords || []).slice(0,4)].filter(Boolean);
                interpretation.innerHTML = `<div class="intent-summary"><span>${result.mode==='AI_ASSISTED'?'AI 已理解':'语义引擎已理解'}</span><strong>${esc(intent.summary)}</strong></div><div class="intent-chips">${chips.map(chip=>`<i>${esc(chip)}</i>`).join('')}</div>`;
                document.getElementById('resultText').textContent = `按你的描述找到 ${result.products.length} 件商品`;
                document.getElementById('productGrid').innerHTML = result.products.length ? result.products.map(productCard).join('') : empty('⌕','没有找到足够匹配的商品','试试放宽预算或减少一个要求');
                if (loadMoreBtn) {
                    // Natural search already returns its complete ranked result set. Keeping the normal-list
                    // pager visible would append unrelated products and make the AI result look inaccurate.
                    loadMoreBtn.hidden = true;
                    loadMoreBtn.disabled = true;
                }
                hydrateHomeEditorial(result.products);
                setupRevealMotion(document.getElementById('productGrid'));
                document.getElementById('productGrid').scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});
            } catch (error) {
                interpretation.innerHTML = '';
                toast(error.message);
            } finally {
                button.disabled = false;
                button.textContent = '智能找商品';
            }
        });
        if (loadMoreBtn) loadMoreBtn.addEventListener('click', () => load(false).catch(error => toast(error.message)));
        await load(true);
    }

    async function initLogin() {
        if (await loadUser()) {
            location.href = query('return') || '/';
            return;
        }
        const track = document.getElementById('authTrack');
        const setMode = register => {
            track.classList.toggle('show-register', register);
            const url = new URL(location.href);
            if (register) url.searchParams.set('mode', 'register'); else url.searchParams.delete('mode');
            history.replaceState(null, '', url);
        };
        if (query('mode') === 'register') track.classList.add('show-register');
        const loginPattern = /^[^@\s]+@[^@\s]+\.(edu|local)$/i;
        const loginMessage = '仅支持 @xxx.edu 学生邮箱或 @xxx.local 管理员邮箱';
        const regEmailInput = document.getElementById('regEmail');
        const regRoleSelect = document.getElementById('regRole');
        const regPatternFor = () => regRoleSelect && regRoleSelect.value === 'ADMIN' ? /^[^@\s]+@[^@\s]+\.local$/i : /^[^@\s]+@[^@\s]+\.edu$/i;
        const regMessageFor = () => regRoleSelect && regRoleSelect.value === 'ADMIN' ? '管理员邮箱需为 @xxx.local 后缀' : '仅支持 @xxx.edu 后缀的学生邮箱';
        const refreshRegValidity = () => {
            if (!regEmailInput) return;
            if (regEmailInput.value && !regPatternFor().test(regEmailInput.value.trim())) {
                regEmailInput.setCustomValidity(regMessageFor());
                if (regEmailInput.pattern) regEmailInput.pattern = regRoleSelect && regRoleSelect.value === 'ADMIN' ? '[^@\\s]+@[^@\\s]+\\.local' : '[^@\\s]+@[^@\\s]+\\.edu';
            } else {
                regEmailInput.setCustomValidity('');
                if (regEmailInput.pattern) regEmailInput.pattern = regRoleSelect && regRoleSelect.value === 'ADMIN' ? '[^@\\s]+@[^@\\s]+\\.local' : '[^@\\s]+@[^@\\s]+\\.edu';
            }
        };
        const loginInput = document.getElementById('email');
        if (loginInput) {
            loginInput.addEventListener('input', () => {
                if (loginInput.value && !loginPattern.test(loginInput.value.trim())) loginInput.setCustomValidity(loginMessage);
                else loginInput.setCustomValidity('');
            });
            loginInput.addEventListener('blur', () => {
                if (loginInput.value && !loginPattern.test(loginInput.value.trim())) loginInput.setCustomValidity(loginMessage);
                else loginInput.setCustomValidity('');
            });
        }
        if (regEmailInput) {
            regEmailInput.addEventListener('input', refreshRegValidity);
            regEmailInput.addEventListener('blur', refreshRegValidity);
        }
        if (regRoleSelect) regRoleSelect.addEventListener('change', refreshRegValidity);
        document.getElementById('toRegisterBtn').addEventListener('click', () => setMode(true));
        document.getElementById('toLoginBtn').addEventListener('click', () => setMode(false));
        document.getElementById('loginForm').addEventListener('submit', async event => {
            event.preventDefault();
            const emailValue = document.getElementById('email').value;
            if (!/^[^@\s]+@[^@\s]+\.(edu|local)$/i.test(emailValue.trim())) {
                toast('仅支持 @xxx.edu 学生邮箱或 @xxx.local 管理员邮箱');
                return;
            }
            const button = event.currentTarget.querySelector('button');
            button.disabled = true;
            try {
                await api('/api/auth/login', { method:'POST', body: JSON.stringify({ email: emailValue, password: document.getElementById('password').value }) });
                location.href = query('return') || '/';
            } catch (error) { toast(error.message); button.disabled = false; }
        });
        document.getElementById('registerForm').addEventListener('submit', async event => {
            event.preventDefault();
            const password = document.getElementById('regPassword').value;
            if (password !== document.getElementById('regConfirm').value) {
                toast('两次输入的密码不一致');
                return;
            }
            const regEmailValue = document.getElementById('regEmail').value;
            const regAsAdmin = document.getElementById('regRole').value === 'ADMIN';
            if (regAsAdmin ? !/^[^@\s]+@[^@\s]+\.local$/i.test(regEmailValue.trim()) : !/^[^@\s]+@[^@\s]+\.edu$/i.test(regEmailValue.trim())) {
                toast(regAsAdmin ? '管理员邮箱需为 @xxx.local 后缀' : '仅支持 @xxx.edu 后缀的学生邮箱');
                return;
            }
            const button = event.currentTarget.querySelector('button');
            button.disabled = true;
            try {
                await api('/api/auth/register', { method:'POST', body: JSON.stringify({ displayName: document.getElementById('regName').value, email: regEmailValue, password, role: document.getElementById('regRole').value }) });
                toast('注册成功，欢迎加入 StudentBay');
                setTimeout(() => { location.href = query('return') || '/'; }, 600);
            } catch (error) { toast(error.message); button.disabled = false; }
        });
    }

    async function mapConfig() {
        const config = await api('/api/map/config');
        return { lat:Number(config.centerLat), lng:Number(config.centerLng), zoom:Number(config.zoom), tileUrl:config.tileUrl, attribution:config.attribution };
    }

    function createMap(elementId, config, lat, lng, clickable = false) {
        const map = L.map(elementId, {
            zoomControl: clickable,
            dragging: clickable,
            scrollWheelZoom: false,
            doubleClickZoom: clickable,
            touchZoom: clickable,
            boxZoom: clickable,
            keyboard: clickable,
            tap: clickable
        }).setView([lat ?? config.lat, lng ?? config.lng], clickable ? (config.zoom || 16) : 17);
        L.tileLayer(config.tileUrl, {
            maxZoom: 19,
            attribution: config.attribution,
            updateWhenIdle: true,
            keepBuffer: 0
        }).addTo(map);
        if (!clickable && lat != null && lng != null) L.marker([lat, lng]).addTo(map);
        return map;
    }

    async function initDetail() {
        const id = query('id');
        if (!id) { location.href = '/'; return; }
        const product = await api(`/api/products/${id}`);
        const sellerTrust = await api(`/api/trust/users/${product.sellerId}`).catch(() => null);
        document.title = `${product.title} - StudentBay`;
        const mine = state.user && state.user.id === product.sellerId;
        const images = (product.images && product.images.length) ? product.images : (product.imageUrl ? [product.imageUrl] : []);
        const thumbs = images.length > 1 ? `<div class="detail-thumbs">${images.map((img, i) => `<button type="button" class="detail-thumb ${i === 0 ? 'active' : ''}" data-index="${i}"><img src="${esc(img)}" alt="图 ${i + 1}" onerror="this.style.display='none'"></button>`).join('')}</div>` : '';
        const canCut = product.status === 'AVAILABLE' && product.cutEnabled && !mine;
        const cutInfo = product.cutEnabled ? `<span class="cut-badge">已有 ${product.cutCount || 0} 位同学砍过 · 每人限砍 1 次</span>` : '';
        const cutButton = canCut ? `<button id="cutButton" class="btn btn-outline btn-small" type="button" ${product.cutByMe ? 'disabled' : ''}>砍一刀</button>` : '';
        const cutPrice = product.cutByMe && product.cutPrice != null ? Number(product.cutPrice) : null;
        const detailPrice = cutPrice != null ? cutPrice : Number(product.price);
        const oldPriceHtml = cutPrice != null
            ? `<span class="old-price">¥${Number(product.price).toFixed(2)}</span>`
            : (product.originalPrice ? `<span class="old-price">¥${Number(product.originalPrice).toFixed(2)}</span>` : '');
        const buyerActions = product.status === 'AVAILABLE'
            ? '<div class="action-row"><button id="favoriteButton" class="btn btn-outline">♡ 收藏</button><button id="chatButton" class="btn btn-outline">咨询卖家</button><button id="cartButton" class="btn btn-outline">加入购物车</button><button id="buyNowButton" class="btn btn-primary">立即购买</button><button id="reportButton" class="btn btn-danger span-2">举报商品</button></div>'
            : '<div class="action-row"><button id="favoriteButton" class="btn btn-outline">♡ 收藏</button><button id="chatButton" class="btn btn-outline">咨询卖家</button><button class="btn btn-dark span-2" disabled>该商品暂时无法购买</button><button id="reportButton" class="btn btn-danger span-2">举报商品</button></div>';
        const productStatusText = {AVAILABLE:'商品在售', RESERVED:'交易中', SOLD:'已售出', OFF_SHELF:'已下架'};
        const trustBadge = sellerTrust ? `<span class="seller-trust-badge" title="由认证、成功交易、评分、举报与取消情况综合生成"><b>${sellerTrust.score}</b><span>Trust Score<small>${esc(sellerTrust.levelLabel)} · ${sellerTrust.completedTrades} 笔完成</small></span></span>` : '';
        document.getElementById('productDetail').innerHTML = `<div class="detail-media" data-reveal><div class="detail-photo-frame"><img class="detail-photo" id="detailMainImage" src="${esc(images[0] || fallbackImage)}" alt="${esc(product.title)}" onerror="this.src='${fallbackImage}'"></div>${thumbs}<div class="detail-description"><div class="detail-description-head"><h2>商品说明</h2><span>${product.viewCount} 次浏览</span></div>${product.conditionDetail ? `<p class="condition-detail"><b>成色 / 瑕疵：</b>${esc(product.conditionDetail)}</p>` : ''}<p class="description">${esc(product.description)}</p></div></div><aside class="detail-info" data-reveal><span class="status">${productStatusText[product.status] || esc(product.status)}</span><h1>${esc(product.title)}</h1><div class="detail-meta-row"><span>${esc(product.category)}</span><span>${esc(product.condition)}</span>${product.bargainEnabled ? '<span class="bargain-badge">可议价</span>' : ''}</div><div class="detail-price-row"><span class="price detail-price">${detailPrice.toFixed(2)}</span>${cutPrice != null ? `<em class="my-price-badge">我的专属价</em>` : ''}${oldPriceHtml}${cutButton}</div>${cutInfo ? `<div class="cut-row">${cutInfo}</div>` : ''}<a class="price-insight" href="/price-compare.html?product=${product.id}"><span class="price-insight-icon">PRICE</span><span><strong>查看同款价格对照</strong><small>比较校内同款与相似商品挂牌价</small></span><b>›</b></a><div class="seller-box"><span class="avatar">${esc(product.sellerName.slice(0,1))}</span><div><strong>${esc(product.sellerName)}</strong><div class="seller-verified">${sellerTrust?.verified?'已通过校园身份认证':'校园用户'}</div></div>${trustBadge}</div>${mine ? '<div class="action-row"><a class="btn btn-outline" href="/seller-orders.html">查看卖家订单</a><a class="btn btn-primary" href="/publish.html">发布新商品</a></div>' : buyerActions}</aside>`;
        setupRevealMotion(document.getElementById('productDetail'));
        renderDetailParams(product);
        document.querySelectorAll('.detail-thumb').forEach(thumb => thumb.addEventListener('click', () => {
            document.querySelectorAll('.detail-thumb').forEach(item => item.classList.remove('active'));
            thumb.classList.add('active');
            const main = document.getElementById('detailMainImage');
            if (main) main.src = thumb.querySelector('img').src;
        }));
        const cutButtonEl = document.getElementById('cutButton');
        if (cutButtonEl) cutButtonEl.addEventListener('click', async () => {
            cutButtonEl.disabled = true;
            try {
                const result = await api(`/api/products/${id}/bargain`, { method: 'POST' });
                const priceEl = document.querySelector('.detail-price');
                if (priceEl) {
                    priceEl.textContent = Number(result.priceAfter).toFixed(2);
                    priceEl.classList.remove('price-pop');
                    void priceEl.offsetWidth;
                    priceEl.classList.add('price-pop');
                }
                const priceRow = document.querySelector('.detail-price-row');
                if (priceRow && !priceRow.querySelector('.my-price-badge')) {
                    const em = document.createElement('em');
                    em.className = 'my-price-badge';
                    em.textContent = '我的专属价';
                    priceRow.insertBefore(em, priceEl ? priceEl.nextSibling : priceRow.firstChild);
                }
                const badge = document.querySelector('.cut-badge');
                if (badge) badge.textContent = `已有 ${result.cutCount} 位同学砍过 · 每人限砍 1 次`;
                toast(`砍价成功 -¥${Number(result.amount).toFixed(2)}`);
                if (result.cutCount >= result.cutLimit) cutButtonEl.remove();
            } catch (error) {
                toast(error.message);
                cutButtonEl.disabled = false;
            }
        });
        document.getElementById('locationText').textContent = product.locationName || '校内交易地点';
        let productMapLoaded = false;
        document.getElementById('showProductMap').addEventListener('click', async event => {
            const button = event.currentTarget;
            const mapElement = document.getElementById('productMap');
            if (productMapLoaded) {
                mapElement.classList.toggle('hidden');
                button.textContent = mapElement.classList.contains('hidden') ? '查看小地图' : '收起地图';
                return;
            }
            button.disabled = true;
            button.textContent = '地图加载中…';
            try {
                const config = await mapConfig();
                mapElement.classList.remove('hidden');
                const lat = Number(product.latitude);
                const lng = Number(product.longitude);
                const valid = Number.isFinite(lat) && Number.isFinite(lng) && (lat !== 0 || lng !== 0);
                const map = createMap('productMap', config, valid ? lat : config.lat, valid ? lng : config.lng);
                setTimeout(() => map.invalidateSize(), 60);
                productMapLoaded = true;
                button.textContent = '收起地图';
            } catch (error) {
                mapElement.classList.remove('hidden');
                productMapLoaded = true;
                button.textContent = '收起地图';
            } finally {
                button.disabled = false;
            }
        });
        const addToCart = async redirectToCheckout => {
            const cartItem = await api(`/api/cart/${id}`, {method:'POST'});
            setBadge('cartBadge', await api('/api/cart/count'));
            if (redirectToCheckout) {
                sessionStorage.setItem('checkoutItems', JSON.stringify([cartItem.id]));
                location.href = '/checkout.html';
            } else {
                toast('已加入购物车');
            }
        };
        document.getElementById('cartButton')?.addEventListener('click', async () => { try { await addToCart(false); } catch(error) { toast(error.message); } });
        document.getElementById('buyNowButton')?.addEventListener('click', async event => { event.currentTarget.disabled = true; event.currentTarget.textContent = '正在确认…'; try { await addToCart(true); } catch(error) { toast(error.message); event.currentTarget.disabled = false; event.currentTarget.textContent = '立即购买'; } });
        const favoriteButton = document.getElementById('favoriteButton');
        favoriteButton?.addEventListener('click', async () => {
            favoriteButton.disabled = true;
            try {
                const result = await api(`/api/favorites/${id}/toggle`, { method: 'POST' });
                favoriteButton.textContent = result.active ? '♥ 已收藏' : '♡ 收藏';
                toast(result.active ? '已加入收藏' : '已取消收藏');
            } catch (error) {
                toast(error.message);
            } finally {
                favoriteButton.disabled = false;
            }
        });
        document.getElementById('chatButton')?.addEventListener('click', async () => { try { const conversation = await api(`/api/chat/product/${id}`, {method:'POST'}); location.href = `/messages.html?conversation=${conversation.id}`; } catch(error) { toast(error.message); } });
        document.getElementById('reportButton')?.addEventListener('click', () => document.getElementById('reportModal').classList.remove('hidden'));
        document.querySelectorAll('[data-close-modal]').forEach(button => button.addEventListener('click', () => button.closest('.modal').classList.add('hidden')));
        document.getElementById('reportForm')?.addEventListener('submit', async event => { event.preventDefault(); try { await api('/api/reports', {method:'POST', body:JSON.stringify({productId:Number(id), reason:document.getElementById('reportReason').value, detail:document.getElementById('reportDetail').value})}); document.getElementById('reportModal').classList.add('hidden'); toast('举报已提交，平台会尽快处理'); } catch(error) { toast(error.message); } });
        await initProductComments(Number(id), product);
        loadRecommendations(Number(id));
    }

    // T4.3 详情页「商品参数」区：有参数才显示，参数表在发布页按分类动态填写
    function renderDetailParams(product) {
        const section = document.getElementById('paramsSection');
        const list = document.getElementById('paramsList');
        if (!section || !list) return;
        const fields = [
            ['品牌', product.brand], ['型号', product.model], ['尺码/尺寸', product.size],
            ['颜色', product.color], ['适用性别', product.gender], ['材质', product.material],
            ['运动类型', product.sportType], ['ISBN', product.isbn], ['课程编号', product.courseCode],
            ['课程/教材', product.moduleName]
        ].filter(row => row[1]);
        if (!fields.length) return;
        section.classList.remove('hidden');
        list.innerHTML = fields.map(([label, v]) => `<div class="param-item"><span>${esc(label)}</span><strong>${esc(String(v))}</strong></div>`).join('');
    }

    // T4.6 详情页「为你推荐」：同分类 + 标题相似度，服务端 /api/products/recommend 返回最多 8 条
    async function loadRecommendations(productId) {
        const grid = document.getElementById('recommendGrid');
        if (!grid) return;
        try {
            const items = await api(`/api/products/recommend?productId=${productId}`);
            grid.innerHTML = items.length ? items.map(productCard).join('') : empty('⌁', '暂时没有同类好物', '同分类商品发布后会出现在这里');
            setupRevealMotion(grid);
        } catch {
            grid.innerHTML = empty('⌁', '暂时没有同类好物', '同分类商品发布后会出现在这里');
        }
    }

    async function initProductComments(productId, product) {
        const formHost = document.getElementById('commentFormHost');
        formHost.innerHTML = state.user
            ? `<form id="commentForm" class="comment-form"><span class="avatar">${esc(state.user.displayName.slice(0,1))}</span><textarea id="commentContent" class="textarea" maxlength="500" placeholder="公开留言，问问成色、配件或交易时间…" required></textarea><button class="btn btn-primary" type="submit">发布留言</button></form>`
            : '<div class="comment-login"><a href="/login.html">登录</a>后可以公开留言</div>';
        const load = async () => {
            const comments = await api(`/api/products/${productId}/comments`);
            document.getElementById('commentCount').textContent = `${comments.length} 条留言`;
            const list = document.getElementById('commentList');
            list.innerHTML = comments.length ? comments.map(comment => `<article class="comment-item"><span class="avatar">${esc(comment.authorName.slice(0,1))}</span><div><div class="comment-head"><strong>${esc(comment.authorName)}</strong>${comment.seller?'<em class="seller-badge">卖家</em>':comment.studentVerified?'<em class="verified-badge">已认证</em>':''}<small>${dateTime(comment.createdAt)}</small></div><p>${esc(comment.content)}</p></div>${comment.canDelete?`<button class="text-button danger-text" data-delete-comment="${comment.id}">删除</button>`:''}</article>`).join('') : empty('✎','还没有留言','成为第一个公开提问的人');
            list.querySelectorAll('[data-delete-comment]').forEach(button => button.addEventListener('click', async () => {
                if (!confirm('确定删除这条留言吗？')) return;
                try { await api(`/api/products/${productId}/comments/${button.dataset.deleteComment}`, {method:'DELETE'}); await load(); toast('留言已删除'); }
                catch (error) { toast(error.message); }
            }));
        };
        document.getElementById('commentForm')?.addEventListener('submit', async event => {
            event.preventDefault();
            const form = event.currentTarget;
            const button = form.querySelector('button');
            button.disabled = true;
            try {
                await api(`/api/products/${productId}/comments`, {method:'POST', body:JSON.stringify({content:value('commentContent')})});
                form.reset();
                await load();
                toast('留言已发布');
            } catch (error) { toast(error.message); }
            button.disabled = false;
        });
        await load();
    }

    async function initPriceCompare() {
        const productId = query('product');
        if (!productId) { location.href = '/'; return; }
        const data = await api(`/api/products/${productId}/comparisons`);
        const current = data.currentProduct;
        const stats = data.statistics;
        document.title = `${current.title}价格对照 - StudentBay`;
        document.getElementById('compareBack').href = `/product-detail.html?id=${current.id}`;
        const difference = stats.differencePercent == null ? null : Number(stats.differencePercent);
        const verdict = difference == null ? '暂时没有足够的同类挂牌数据'
            : difference <= -5 ? `比同类均价低 ${Math.abs(difference).toFixed(1)}%`
            : difference >= 5 ? `比同类均价高 ${difference.toFixed(1)}%`
            : '价格接近同类商品平均水平';
        document.getElementById('compareSummary').innerHTML = `<div><span class="eyebrow">校园价格参考</span><h1>${esc(current.title)}</h1><p>${esc(data.notice)}</p></div><div class="current-price-card"><small>当前商品</small><strong>${money(current.price)}</strong><span>${esc(verdict)}</span></div>`;
        document.getElementById('compareStats').innerHTML = `<div class="stat-card"><small>参考样本</small><strong>${stats.sampleCount} 件</strong></div><div class="stat-card"><small>最低挂牌价</small><strong>${stats.lowestPrice==null?'-':money(stats.lowestPrice)}</strong></div><div class="stat-card"><small>平均挂牌价</small><strong>${stats.averagePrice==null?'-':money(stats.averagePrice)}</strong></div><div class="stat-card"><small>最高挂牌价</small><strong>${stats.highestPrice==null?'-':money(stats.highestPrice)}</strong></div>`;
        const allPrices = [Number(current.price), ...data.comparisons.map(item => Number(item.price))];
        const maxPrice = Math.max(...allPrices, 1);
        const rows = [{...current, matchType:'当前商品', current:true}, ...data.comparisons];
        document.getElementById('compareList').innerHTML = data.comparisons.length
            ? rows.map(item => `<a class="compare-row ${item.current?'current':''}" href="/product-detail.html?id=${item.id}"><img src="${esc(item.imageUrl || fallbackImage)}" alt="${esc(item.title)}" onerror="this.src='${fallbackImage}'"><div class="compare-info"><div><span class="compare-tag">${esc(item.matchType)}</span><strong>${esc(item.title)}</strong></div><small>${esc(item.condition)} · ${esc(item.locationName)}</small><div class="price-bar"><i style="width:${Math.max(8,Number(item.price)/maxPrice*100)}%"></i></div></div><div class="compare-price"><strong>${money(item.price)}</strong>${item.originalPrice?`<small>原价 ${money(item.originalPrice)}</small>`:''}</div></a>`).join('')
            : empty('⌁','暂无可对照商品','同款或同类商品发布后会自动出现在这里');
    }

    async function initPublish() {
        if (!requireLogin()) return;
        const editId = query('edit') ? Number(query('edit')) : null;
        let editingDraft = false;
        if (editId) {
            const heading = document.querySelector('.page-heading h1');
            if (heading) heading.textContent = '编辑商品';
            const eyebrow = document.querySelector('.page-heading .eyebrow');
            if (eyebrow) eyebrow.textContent = 'Edit';
            const sub = document.querySelector('.page-heading p');
            if (sub) sub.textContent = '修改商品信息后保存，改动会立即生效。';
        }
        const config = await mapConfig();
        const map = createMap('publishMap', config, config.lat, config.lng, true);
        const images = [];
        const state = { marker: null, selected: null, poiId: null, suggestTimer: null };
        const maxImages = 5;
        const fileInput = document.getElementById('fileInput');
        const imageGrid = document.getElementById('imageGrid');

        // ---------- T4.1/T4.3 分类接口化 + 按分类显隐参数表单 ----------
        const PARAM_SCHEMAS = {
            '教材资料': [
                { key: 'isbn', label: 'ISBN', placeholder: '例如 978-7-115-59367-0' },
                { key: 'courseCode', label: '课程编号', placeholder: '例如 SE2101' },
                { key: 'moduleName', label: '课程/教材名', placeholder: '例如 Java 全栈开发' }
            ],
            '数码产品': [
                { key: 'brand', label: '品牌', placeholder: '例如 索尼、罗技、小米' },
                { key: 'model', label: '型号', placeholder: '例如 WH-1000XM4、GPW' }
            ],
            '服饰美妆': [
                { key: 'brand', label: '品牌', placeholder: '选填' },
                { key: 'size', label: '尺码', placeholder: '例如 S / M / L / XL' },
                { key: 'color', label: '颜色', placeholder: '例如 墨绿、深灰' },
                { key: 'gender', label: '适用性别', options: ['男', '女', '中性', '通用'] }
            ],
            '骑行装备': [
                { key: 'brand', label: '品牌', placeholder: '例如 捷安特、迪卡侬' },
                { key: 'size', label: '尺寸', placeholder: '例如 26 寸 / M 码' },
                { key: 'sportType', label: '运动类型', placeholder: '例如 通勤、公路、山地' }
            ],
            '宿舍生活': [
                { key: 'material', label: '材质', placeholder: '例如 记忆棉、金属、塑料' },
                { key: 'size', label: '规格', placeholder: '例如 0.8L、三层、可折叠' }
            ],
            '其他': []
        };
        const paramValues = {};
        const paramFieldsHost = document.getElementById('paramFields');
        const categorySelect = document.getElementById('category');
        const renderParamFields = () => {
            const schema = PARAM_SCHEMAS[categorySelect.value] || [];
            paramFieldsHost.innerHTML = schema.map(field => {
                const current = paramValues[field.key] || '';
                if (field.options) {
                    return `<div class="field"><label for="pf_${field.key}">${field.label}</label><select id="pf_${field.key}" class="select"><option value="">请选择</option>${field.options.map(o => `<option value="${esc(o)}"${o === current ? ' selected' : ''}>${esc(o)}</option>`).join('')}</select></div>`;
                }
                return `<div class="field"><label for="pf_${field.key}">${field.label}</label><input id="pf_${field.key}" class="input" maxlength="120" placeholder="${field.placeholder || ''}" value="${esc(current)}"></div>`;
            }).join('');
            paramFieldsHost.querySelectorAll('input, select').forEach(el => el.addEventListener('input', () => {
                paramValues[el.id.slice(3)] = el.value.trim();
            }));
        };
        const collectParams = () => {
            const schema = PARAM_SCHEMAS[categorySelect.value] || [];
            const body = {};
            schema.forEach(field => { const v = paramValues[field.key] || ''; if (v) body[field.key] = v; });
            return body;
        };
        (async () => {
            try {
                const cats = await api('/api/categories');
                const previous = categorySelect.value;
                categorySelect.innerHTML = cats.map(item => `<option value="${esc(item.name)}">${esc(item.name)}</option>`).join('');
                if (cats.some(item => item.name === previous)) categorySelect.value = previous;
            } catch { /* 接口失败保留静态分类 */ }
            renderParamFields();
        })();
        categorySelect.addEventListener('change', renderParamFields);

        // ---------- 图片多方式上传 ----------
        const renderImages = () => {
            const addCell = document.getElementById('imageAddCell');
            imageGrid.querySelectorAll('.image-cell:not(#imageAddCell)').forEach(cell => cell.remove());
            images.forEach((url, index) => {
                const cell = document.createElement('div');
                cell.className = 'image-cell';
                cell.innerHTML = `<img src="${esc(url)}" alt="商品图 ${index + 1}" onerror="this.closest('.image-cell').classList.add('image-error')">${index === 0 ? '<em class="image-main-tag">主图</em>' : ''}<button type="button" class="image-remove" title="移除">×</button>`;
                cell.querySelector('.image-remove').addEventListener('click', () => { images.splice(index, 1); renderImages(); });
                imageGrid.insertBefore(cell, addCell);
            });
            addCell.classList.toggle('hidden', images.length >= maxImages);
        };

        const addImage = url => {
            if (images.length >= maxImages) { toast(`最多上传 ${maxImages} 张图片`); return; }
            if (!url) return;
            images.push(url);
            renderImages();
        };

        const uploadFiles = async files => {
            for (const file of Array.from(files)) {
                if (images.length >= maxImages) { toast(`最多上传 ${maxImages} 张图片`); return; }
                if (!file.type.startsWith('image/')) { toast('仅支持图片文件'); continue; }
                if (file.size > 5 * 1024 * 1024) { toast('图片不能超过 5MB'); continue; }
                const form = new FormData();
                form.append('file', file);
                try {
                    const result = await api('/api/uploads', { method: 'POST', body: form });
                    addImage(result.url);
                } catch (error) { toast(error.message); }
            }
        };

        const linkRow = document.getElementById('linkInputRow');
        const addCell = document.getElementById('imageAddCell');
        // 「添加图片」为 <label for=fileInput>：点击由浏览器原生弹出文件选择器（不依赖程序化 click）
        addCell.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                document.getElementById('fileInput').click();
            }
        });
        document.getElementById('pickLinkBtn').addEventListener('click', () => linkRow.classList.toggle('hidden'));
        fileInput.addEventListener('change', () => { uploadFiles(fileInput.files); fileInput.value = ''; });
        imageGrid.addEventListener('dragover', event => { event.preventDefault(); imageGrid.classList.add('dragging'); });
        imageGrid.addEventListener('dragleave', () => imageGrid.classList.remove('dragging'));
        imageGrid.addEventListener('drop', event => { event.preventDefault(); imageGrid.classList.remove('dragging'); uploadFiles(event.dataTransfer.files); });
        document.addEventListener('paste', event => {
            // 剪贴板为图片（截图）→ 直接上传
            const files = [...(event.clipboardData?.files || [])].filter(file => file.type.startsWith('image/'));
            if (files.length) { event.preventDefault(); uploadFiles(files); return; }
            // 剪贴板为图片链接（且焦点不在输入框）→ 服务端代理下载显示
            const active = document.activeElement;
            const inField = active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.tagName === 'SELECT' || active.isContentEditable);
            const text = (event.clipboardData?.getData('text/plain') || '').trim();
            if (!inField && /^https?:\/\/\S+$/i.test(text)) {
                event.preventDefault();
                pasteUrlAsImage(text);
            }
        });
        async function pasteUrlAsImage(url) {
            try {
                const result = await api('/api/uploads/by-url', { method: 'POST', body: JSON.stringify({ url }) });
                addImage(result.url);
            } catch (error) { toast(error.message); }
        }
        document.getElementById('addLinkBtn').addEventListener('click', async () => {
            const url = document.getElementById('imageLink').value.trim();
            if (!url) { toast('请输入图片链接'); return; }
            if (!/^https?:\/\//i.test(url)) { toast('链接需以 http(s):// 开头'); return; }
            const button = document.getElementById('addLinkBtn');
            button.disabled = true;
            button.textContent = '下载中…';
            try {
                const result = await api('/api/uploads/by-url', { method: 'POST', body: JSON.stringify({ url }) });
                addImage(result.url);
                document.getElementById('imageLink').value = '';
                linkRow.classList.add('hidden');
            } catch (error) {
                toast(error.message);
            } finally {
                button.disabled = false;
                button.textContent = '添加';
            }
        });
        document.getElementById('imageLink').addEventListener('keydown', event => {
            if (event.key === 'Enter') { event.preventDefault(); document.getElementById('addLinkBtn').click(); }
        });
        renderImages();

        // ---------- 交易地点 × 地图联动（全球/全国级联选择 + 地图跳转 + 单一定位） ----------
        const coordinateText = document.getElementById('coordinateText');
        const locationNameInput = document.getElementById('locationName');
        const locationNameDisplay = document.getElementById('locationNameDisplay');
        const detailInput = document.getElementById('addressDetailLine');
        const regionSelects = {
            country: document.getElementById('addressCountry'),
            province: document.getElementById('addressProvince'),
            city: document.getElementById('addressCity'),
            district: document.getElementById('addressDistrict'),
            street: document.getElementById('addressStreet')
        };
        const regionOrder = ['country', 'province', 'city', 'district', 'street'];
        // 校园园区（广东东软学院片区）：仅当区县为「南海区」时提供
        const CAMPUS_STREETS = ['广东东软学院', '狮山镇', '桂城街道', '大沥镇', '里水镇'];
        // 各级选择后地图缩放到的大致级别
        const ZOOM_BY_LEVEL = { country: 5, province: 7, city: 9, district: 11, street: 12 };
        let regionData = {};
        let regionCache = null;

        const setMarker = (lat, lng) => {
            if (state.marker) state.marker.setLatLng([lat, lng]);
            else state.marker = L.marker([lat, lng]).addTo(map);
            map.setView([lat, lng], Math.max(map.getZoom(), 16));
            state.selected = { lat, lng };
        };

        const composeLocationName = () => {
            const region = regionOrder.map(key => regionSelects[key].value).filter(Boolean).join('');
            const detail = value('addressDetailLine');
            const composed = detail ? `${region}${detail}` : region;
            locationNameInput.value = composed;
            locationNameDisplay.textContent = composed || '未选择';
            locationNameDisplay.classList.toggle('is-empty', !composed);
            return composed;
        };

        const setOptions = (select, values) => {
            select.innerHTML = `<option value="">请选择</option>` + values.map(item => `<option value="${esc(item)}">${esc(item)}</option>`).join('');
            if (values.length) select.value = values[0];
        };

        // 区域节点 = { c: {lat,lng}, s: {子节点名: 节点} }；顶层为国家名映射（无 s）
        const childrenOf = node => {
            if (node == null) return [];
            if (Array.isArray(node)) return node;
            return node.s ? Object.keys(node.s) : Object.keys(node);
        };
        const coordOf = node => (node && node.c) ? node.c : null;

        // 沿当前各级选择值取到第 index 层的节点
        const nodeAt = index => {
            let node = regionData;
            for (let j = 0; j <= index; j++) {
                const value = regionSelects[regionOrder[j]].value;
                if (!node || !value) return null;
                const children = (node.s != null) ? node.s : node;
                if (children[value] != null) node = children[value];
                else return null;
            }
            return node;
        };

        const cascade = fromIndex => {
            for (let i = fromIndex + 1; i < regionOrder.length; i++) {
                const key = regionOrder[i];
                const node = nodeAt(i - 1);
                let options = node ? childrenOf(node) : [];
                // 街道/园区：校园片区（南海区）提供园区选项，其余留空由「具体位置」补充
                if (key === 'street' && regionSelects.district.value === '南海区') options = CAMPUS_STREETS;
                const previous = regionSelects[key].value;
                setOptions(regionSelects[key], options);
                if (options.includes(previous)) regionSelects[key].value = previous;
            }
            composeLocationName();
        };

        // 某层级变化：刷新下级 + 地图跳到该层大致区域（不自动放标记，由用户点击/定位精确定位）
        const onRegionChange = index => {
            cascade(index);
            const node = nodeAt(index);
            const coord = coordOf(node);
            const label = regionSelects[regionOrder[index]].value;
            if (coord && label) {
                map.flyTo([coord.lat, coord.lng], ZOOM_BY_LEVEL[regionOrder[index]] || 10);
                coordinateText.textContent = `已定位到「${label}」所在区域，请点击地图选择精确位置`;
            }
        };

        const loadRegions = async () => {
            regionData = regionCache || (regionCache = await (await fetch('/data/regions.json')).json());
            const countries = childrenOf(regionData);
            setOptions(regionSelects.country, countries);
            regionSelects.country.value = countries.includes('中国') ? '中国' : (countries[0] || '');
            // 默认定位到校园片区（广东 · 佛山 · 南海 · 广东东软学院）
            const defaults = { province: '广东省', city: '佛山市', district: '南海区', street: '广东东软学院' };
            let node = regionData['中国'] || null;
            for (const key of ['province', 'city', 'district', 'street']) {
                if (!node) break;
                const children = key === 'street' ? CAMPUS_STREETS : childrenOf(node);
                setOptions(regionSelects[key], children);
                if (children.includes(defaults[key])) {
                    regionSelects[key].value = defaults[key];
                    node = node.s ? node.s[defaults[key]] : null;
                } else break;
            }
            composeLocationName();
        };

        regionSelects.country.addEventListener('change', () => onRegionChange(0));
        regionSelects.province.addEventListener('change', () => onRegionChange(1));
        regionSelects.city.addEventListener('change', () => onRegionChange(2));
        regionSelects.district.addEventListener('change', () => onRegionChange(3));
        regionSelects.street.addEventListener('change', composeLocationName);
        detailInput.addEventListener('input', composeLocationName);

        const reverseLookup = async (lat, lng) => {
            try {
                const data = await api(`/api/geocode/reverse?lat=${lat}&lng=${lng}`);
                if (data.found) {
                    if (data.poiId) state.poiId = Number(data.poiId);
                    // 点击地图即视为「交易地点在此」：地点名称随点击位置动态更新（优先回填最近校园 POI 名）
                    if (data.name) detailInput.value = data.name;
                    if (data.formatted) coordinateText.textContent = `已选择：${data.formatted}`;
                    composeLocationName();
                } else {
                    state.poiId = null;
                    coordinateText.textContent = `已选择自定义位置：${lat.toFixed(6)}, ${lng.toFixed(6)}，可填写具体位置`;
                }
            } catch {
                coordinateText.textContent = `已选择：${lat.toFixed(6)}, ${lng.toFixed(6)}`;
            }
        };

        const locateBtn = document.getElementById('addressLocateBtn');
        locateBtn.addEventListener('click', async () => {
            const address = composeLocationName();
            if (!address) { toast('请先选择省/市/区/街道并填写具体位置'); return; }
            locateBtn.disabled = true;
            locateBtn.textContent = '定位中…';
            try {
                const data = await api(`/api/geocode?address=${encodeURIComponent(address)}`);
                if (data.found) {
                    setMarker(Number(data.latitude), Number(data.longitude));
                    if (data.poiId) state.poiId = Number(data.poiId);
                    coordinateText.textContent = data.formatted ? `已定位：${data.formatted}` : '已定位到所选地址';
                    toast('已定位到所选地址');
                } else {
                    toast('未找到匹配地点，请在地图上手动选点');
                    coordinateText.textContent = '未找到匹配地点，可点击地图放置标记';
                }
            } catch (error) {
                toast(error.message);
            } finally {
                locateBtn.disabled = false;
                locateBtn.textContent = '定位到所选地址';
            }
        });

        await loadRegions();

        map.on('click', event => {
            state.selected = { lat: event.latlng.lat, lng: event.latlng.lng };
            if (state.marker) state.marker.setLatLng(event.latlng);
            else state.marker = L.marker(event.latlng).addTo(map);
            reverseLookup(event.latlng.lat, event.latlng.lng);
        });

        // ---------- T5.7 AI 优化描述 ----------
        const aiBtn = document.getElementById('aiOptimizeBtn');
        const aiHint = document.getElementById('aiOptimizeHint');
        const aiRestoreLink = document.getElementById('aiRestoreLink');
        let aiOriginal = null;
        const setAiBtn = busy => { aiBtn.disabled = busy; aiBtn.textContent = busy ? '润色中…' : 'AI 优化描述'; };
        (async () => {
            try {
                const st = await api('/api/ai/status');
                if (!st.enabled) { aiBtn.disabled = true; aiBtn.title = 'AI 服务未配置，可查看 README 启用'; }
            } catch { aiBtn.disabled = true; }
        })();
        aiBtn.addEventListener('click', async () => {
            const title = value('title');
            if (!title) { toast('请先填写商品标题，AI 需要依据标题来润色'); document.getElementById('title').focus(); return; }
            const description = value('description');
            setAiBtn(true);
            try {
                const data = await api('/api/ai/optimize-description', { method: 'POST', body: JSON.stringify({
                    title,
                    category: value('category'),
                    condition: value('condition'),
                    price: value('price') ? Number(value('price')) : null,
                    params: collectParams(),
                    description: description || null
                }) });
                if (data.optimized) {
                    aiOriginal = description;
                    const ta = document.getElementById('description');
                    ta.value = data.optimized;
                    ta.focus();
                    aiHint.classList.remove('hidden');
                    toast('描述已生成，可继续手动修改');
                } else {
                    toast(data.reason || 'AI 服务不可用，请手动填写');
                }
            } catch (error) {
                toast(error.message);
            } finally {
                setAiBtn(false);
            }
        });
        aiRestoreLink.addEventListener('click', () => {
            if (aiOriginal != null) document.getElementById('description').value = aiOriginal;
            aiHint.classList.add('hidden');
        });

        // ---------- 砍一刀配置 ----------
        const cutEnabled = document.getElementById('cutEnabled');
        const cutOptions = document.getElementById('cutOptions');
        const cutCustom = document.getElementById('cutCustom');
        cutEnabled.addEventListener('change', () => cutOptions.classList.toggle('hidden', !cutEnabled.checked));
        document.querySelectorAll('input[name=cutMode]').forEach(radio => radio.addEventListener('change', () => {
            cutCustom.classList.toggle('hidden', document.querySelector('input[name=cutMode]:checked').value !== 'custom');
        }));

        // ---------- 校验 + 提交 ----------
        const buildBody = draft => {
            const price = value('price') ? Number(value('price')) : null;
            const originalPrice = value('originalPrice') ? Number(value('originalPrice')) : null;
            let cut = null;
            if (cutEnabled.checked) {
                const mode = document.querySelector('input[name=cutMode]:checked').value;
                if (mode === 'custom') {
                    const min = Number(value('cutMin'));
                    const max = Number(value('cutMax'));
                    if (!min || !max || min <= 0 || max <= min) { toast('请正确填写自定义砍价区间'); return null; }
                    cut = { enabled: true, min, max };
                } else {
                    cut = mode === '1-3' ? { enabled: true, min: 1, max: 3 } : { enabled: true, min: 3, max: 5 };
                }
            }
            const params = collectParams();
            return {
                title: value('title'), description: value('description'), category: value('category'),
                condition: value('condition'), price, originalPrice, images,
                ...params,
                conditionDetail: value('conditionDetail') || null,
                bargainEnabled: document.getElementById('bargainEnabled').checked,
                locationName: value('locationName'),
                latitude: state.selected ? state.selected.lat : null,
                longitude: state.selected ? state.selected.lng : null,
                status: draft ? 'DRAFT' : 'AVAILABLE',
                cutEnabled: cut ? cut.enabled : false, cutMin: cut ? cut.min : null, cutMax: cut ? cut.max : null, cutLimit: 3,
                poiId: state.poiId, addressDetail: value('addressDetailLine'),
                addressCountry: value('addressCountry'), addressProvince: value('addressProvince'),
                addressCity: value('addressCity'), addressDistrict: value('addressDistrict'), addressStreet: value('addressStreet')
            };
        };

        const submit = async (draft, button) => {
            const body = buildBody(draft);
            if (!body) return;
            if (!body.title) { toast('请填写商品标题'); document.getElementById('title').focus(); return; }
            if (!draft) {
                if (!body.price || body.price <= 0) { toast('请填写正确的售价'); document.getElementById('price').focus(); return; }
                if (!body.description) { toast('请填写商品描述'); document.getElementById('description').focus(); return; }
                if (!body.locationName) { toast('请填写交易地点名称'); document.getElementById('locationName').focus(); return; }
                if (!body.latitude || !body.longitude) { toast('请在地图上选择交易位置'); return; }
            }
            button.disabled = true;
            button.textContent = draft ? '保存中…' : '发布中…';
            try {
                const product = editId
                    ? await api(`/api/products/${editId}`, { method: 'PUT', body: JSON.stringify(body) })
                    : await api('/api/products', { method: 'POST', body: JSON.stringify(body) });
                if (editId) {
                    toast(draft ? '草稿已保存' : '修改已保存');
                    if (!draft && editingDraft) { location.href = `/product-detail.html?id=${product.id}`; return; }
                    location.href = '/my-products.html';
                    return;
                }
                toast(draft ? '草稿已保存，可在「我发布的 · 草稿」中继续编辑' : '发布成功');
                if (draft) { location.reload(); return; }
                location.href = `/product-detail.html?id=${product.id}`;
            } catch (error) {
                toast(error.message);
                button.disabled = false;
                button.textContent = editId ? (draft ? '保存草稿' : (editingDraft ? '保存并发布' : '保存修改')) : (draft ? '保存草稿' : '确认发布');
            }
        };

        document.getElementById('publishForm').addEventListener('submit', event => { event.preventDefault(); submit(false, document.getElementById('publishBtn')); });
        document.getElementById('saveDraftBtn').addEventListener('click', () => submit(true, document.getElementById('saveDraftBtn')));
        if (editId) await setupEditMode();

        // ---------- 编辑模式（/publish.html?edit=<id>）：预填 + 保存为更新 ----------
        async function setupEditMode() {
            const product = await api(`/api/products/${editId}`);
            const me = state.user || (await api('/api/auth/me', {}, true).catch(() => null));
            if (!me || !product || product.sellerId !== me.id) {
                toast('只能编辑自己发布的商品');
                location.href = '/my-products.html';
                return;
            }
            editingDraft = product.status === 'DRAFT';
            const setV = (id, val) => { const el = document.getElementById(id); if (el) el.value = val ?? ''; };
            setV('title', product.title);
            setV('category', product.category);
            setV('condition', product.condition);
            setV('price', product.price);
            setV('originalPrice', product.originalPrice || '');
            setV('description', product.description);
            // T4.3 动态参数回填（先写缓存再重渲染，保证切换分类不丢已填值）
            const schema = PARAM_SCHEMAS[product.category] || [];
            schema.forEach(field => { if (product[field.key] != null) paramValues[field.key] = String(product[field.key]); });
            renderParamFields();
            setV('conditionDetail', product.conditionDetail);
            if (product.bargainEnabled) document.getElementById('bargainEnabled').checked = true;
            images.length = 0;
            (product.images || []).forEach(url => images.push(url));
            renderImages();
            if (product.cutEnabled) {
                cutEnabled.checked = true;
                cutOptions.classList.remove('hidden');
                const min = Number(product.cutMin);
                const max = Number(product.cutMax);
                const mode = min === 1 && max === 3 ? '1-3' : (min === 3 && max === 5 ? '3-5' : 'custom');
                const radio = document.querySelector(`input[name=cutMode][value="${mode}"]`);
                if (radio) { radio.checked = true; radio.dispatchEvent(new Event('change')); }
                if (mode === 'custom') { setV('cutMin', min); setV('cutMax', max); }
            }
            if (product.addressCountry) {
                regionSelects.country.value = product.addressCountry;
                cascade(0);
                if (product.addressProvince) { regionSelects.province.value = product.addressProvince; cascade(1); }
                if (product.addressCity) { regionSelects.city.value = product.addressCity; cascade(2); }
                if (product.addressDistrict) { regionSelects.district.value = product.addressDistrict; cascade(3); }
                if (product.addressStreet) { regionSelects.street.value = product.addressStreet; }
            }
            setV('addressDetailLine', product.addressDetail);
            composeLocationName();
            if (product.locationName) {
                locationNameInput.value = product.locationName;
                locationNameDisplay.textContent = product.locationName;
                locationNameDisplay.classList.remove('is-empty');
            }
            if (product.latitude && product.longitude) {
                setMarker(Number(product.latitude), Number(product.longitude));
                coordinateText.textContent = '已回填保存的交易位置，可点击地图微调';
            }
            document.getElementById('publishBtn').textContent = editingDraft ? '保存并发布' : '保存修改';
            document.getElementById('saveDraftBtn').classList.toggle('hidden', !editingDraft);
        }
    }

    const value = id => document.getElementById(id).value.trim();

    // T4.4 最近浏览：/api/products/browse/recent 倒序返回（一用户一商品去重），支持清空
    async function initBrowse() {
        if (!requireLogin()) return;
        const grid = document.getElementById('browseGrid');
        const clearBtn = document.getElementById('clearBrowseBtn');
        const load = async () => {
            const items = await api('/api/products/browse/recent');
            grid.innerHTML = items.length ? items.map(productCard).join('') : empty('⌂', '还没有浏览记录', '逛逛校园，看中的商品会自动出现在这里');
            setupRevealMotion(grid);
        };
        clearBtn?.addEventListener('click', async () => {
            if (!confirm('确定清空全部浏览记录吗？')) return;
            try {
                await api('/api/products/browse/clear', { method: 'DELETE' });
                await load();
                toast('浏览记录已清空');
            } catch (error) { toast(error.message); }
        });
        await load();
    }

    async function initMyProducts() {
        if (!requireLogin()) return;
        const list = document.getElementById('productList');
        let all = [];
        // T5.3 徽标三态依据：全局默认回复是否开启
        let arGlobalOn = false;
        const labels = { AVAILABLE: '在售', RESERVED: '交易中', DRAFT: '草稿', OFF_SHELF: '已下架', SOLD: '已售' };

        const statusBadge = product => `<span class="status-badge ${product.status === 'RESERVED' ? 'status-warn' : product.status === 'AVAILABLE' ? 'status-ok' : 'status-muted'}">${labels[product.status] || product.status}</span>`;

        // T5.3 徽标三态：单品已开→绿标；单品未开但全局开→灰标（继承全局）；都关→不显示
        const arBadge = product => product.autoReplyEnabled
            ? '<span class="ar-on-badge">自动回复·已开</span>'
            : (arGlobalOn ? '<span class="ar-inherit-badge">自动回复·继承全局</span>' : '');

        const ops = product => {
            if (product.status === 'DRAFT') {
                return `<a class="btn btn-small btn-outline" href="/publish.html?edit=${product.id}">继续编辑</a><button class="btn btn-small btn-primary" data-publish="${product.id}">发布</button><button class="btn btn-small btn-danger" data-delete="${product.id}">删除</button>`;
            }
            if (product.status === 'OFF_SHELF' || product.status === 'SOLD') {
                return `${arBadge(product)}<button class="btn btn-small btn-primary" data-relist="${product.id}">重新发布</button><a class="btn btn-small btn-outline" href="/publish.html?edit=${product.id}">编辑</a><button class="btn btn-small btn-ghost" data-auto-reply="${product.id}">智能回复</button><a class="btn btn-small btn-ghost" href="/product-detail.html?id=${product.id}">查看</a>`;
            }
            const down = product.status === 'AVAILABLE' ? `<button class="btn btn-small btn-outline" data-down="${product.id}">下架</button>` : '';
            // T4.5 擦亮 / 置顶：仅对在卖商品开放
            return `${arBadge(product)}<a class="btn btn-small btn-outline" href="/publish.html?edit=${product.id}">编辑</a>${down}<button class="btn btn-small btn-ghost" data-sharpen="${product.id}">擦亮</button><button class="btn btn-small btn-ghost" data-top="${product.id}">置顶</button><button class="btn btn-small btn-ghost" data-auto-reply="${product.id}">智能回复</button><a class="btn btn-small btn-ghost" href="/product-detail.html?id=${product.id}">查看</a>`;
        };

        const card = product => `<article class="my-card" data-reveal><a class="my-card-img-wrap" href="/product-detail.html?id=${product.id}"><img class="my-card-img" src="${esc(product.imageUrl || fallbackImage)}" alt="" loading="lazy" onerror="this.src='${fallbackImage}'"></a><div class="my-card-main"><div class="my-card-top"><a class="my-card-title" href="/product-detail.html?id=${product.id}">${esc(product.title)}</a>${statusBadge(product)}</div><div class="my-card-price"><span class="price">${Number(product.price).toFixed(2)}</span>${product.originalPrice ? `<span class="old-price">¥${Number(product.originalPrice).toFixed(2)}</span>` : ''}${product.cutEnabled ? '<span class="cut-badge">可小刀</span>' : ''}</div><div class="my-card-meta"><span>${esc(product.locationName || '校内交易')}</span><span>浏览 ${product.viewCount || 0} · 收藏 ${product.favoriteCount ?? 0}</span></div><div class="my-card-ops">${ops(product)}</div></div></article>`;

        const render = () => {
            const tab = document.querySelector('.tab.active').dataset.tab;
            const items = all.filter(product => tab === 'onSale' ? (product.status === 'AVAILABLE' || product.status === 'RESERVED') : tab === 'draft' ? product.status === 'DRAFT' : (product.status === 'OFF_SHELF' || product.status === 'SOLD'));
            list.innerHTML = items.length ? items.map(card).join('') : empty('▦', tab === 'onSale' ? '还没有在售商品' : tab === 'draft' ? '暂无草稿' : '还没有下架商品', tab === 'draft' ? '编辑中的商品会先存在这里' : '去发布一件闲置试试');
            list.querySelectorAll('[data-down]').forEach(btn => btn.addEventListener('click', async () => {
                try { await api(`/api/products/${btn.dataset.down}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'OFF_SHELF' }) }); toast('已下架'); await load(); } catch (error) { toast(error.message); }
            }));
            list.querySelectorAll('[data-publish]').forEach(btn => btn.addEventListener('click', async () => {
                try { await api(`/api/products/${btn.dataset.publish}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'AVAILABLE' }) }); toast('已发布'); await load(); } catch (error) { toast(error.message); }
            }));
            list.querySelectorAll('[data-sharpen]').forEach(btn => btn.addEventListener('click', async () => {
                try { await api(`/api/products/${btn.dataset.sharpen}/sharpen`, { method: 'POST' }); toast('擦亮成功，商品已提前'); await load(); } catch (error) { toast(error.message); }
            }));
            list.querySelectorAll('[data-top]').forEach(btn => btn.addEventListener('click', async () => {
                const hours = prompt('置顶时长（小时），默认 24 小时：', '24');
                if (hours === null) return;
                const value = Number(hours);
                if (!value || value <= 0 || value > 720) { toast('请输入 1-720 之间的时长'); return; }
                try { await api(`/api/products/${btn.dataset.top}/top`, { method: 'POST', body: JSON.stringify({ hours: value }) }); toast('置顶成功'); await load(); } catch (error) { toast(error.message); }
            }));
            list.querySelectorAll('[data-relist]').forEach(btn => btn.addEventListener('click', async () => {
                try { await api(`/api/products/${btn.dataset.relist}/status`, { method: 'PATCH', body: JSON.stringify({ status: 'AVAILABLE', clearCut: true }) }); toast('已重新上架'); await load(); } catch (error) { toast(error.message); }
            }));
            list.querySelectorAll('[data-delete]').forEach(btn => btn.addEventListener('click', async () => {
                if (btn.dataset.armed !== 'yes') {
                    btn.dataset.armed = 'yes';
                    btn.textContent = '确认删除？';
                    setTimeout(() => { if (btn.dataset.armed === 'yes') { btn.dataset.armed = ''; btn.textContent = '删除'; } }, 3000);
                    return;
                }
                try { await api(`/api/products/${btn.dataset.delete}`, { method: 'DELETE' }); toast('草稿已删除'); await load(); } catch (error) { toast(error.message); }
            }));
            setupRevealMotion(list);
        };

        const load = async () => {
            all = await api('/api/products/mine/list');
            document.getElementById('countOnSale').textContent = all.filter(p => p.status === 'AVAILABLE' || p.status === 'RESERVED').length;
            document.getElementById('countDraft').textContent = all.filter(p => p.status === 'DRAFT').length;
            document.getElementById('countOffShelf').textContent = all.filter(p => p.status === 'OFF_SHELF' || p.status === 'SOLD').length;
            render();
        };

        document.querySelectorAll('[data-tab]').forEach(tab => tab.addEventListener('click', () => {
            document.querySelectorAll('[data-tab]').forEach(item => item.classList.remove('active'));
            tab.classList.add('active');
            render();
        }));

        // ---------- 智能回复（阶段5 T5.1/T5.2：页内常驻面板 + Toggle + 一键应用） ----------
        const AR_TEMPLATES = [
            '你好呀，{昵称}！{商品标题}还在哦，{商品价格}，有需要随时问我～',
            '在的，{商品标题}还在售，{商品价格}，诚心要可以聊～',
            '欢迎咨询～{商品标题}可以小刀，{商品价格}，感兴趣可以直接拍下！'
        ];
        const arPanel = document.getElementById('autoReplyPanel');
        const arBody = document.getElementById('arGlobalBody');
        const arToggle = document.getElementById('arGlobalEnabled');
        const arContent = document.getElementById('arGlobalContent');
        const arPreview = document.getElementById('arGlobalPreview');
        const arPreviewText = document.getElementById('arPreviewText');
        const arPanelSub = document.getElementById('arPanelSub');
        const arApplyAllBtn = document.getElementById('arApplyAll');
        // T5.3 单品弹窗继承提示需要读取最近一次全局设置
        let arGlobalSetting = null;

        // 实时预览：用示例值替换变量，便于卖家预判买家看到的效果
        const renderArPreview = () => {
            const text = arContent.value.trim();
            if (!text) { arPreview.classList.add('hidden'); return; }
            arPreviewText.textContent = text
                .replace(/\{昵称\}/g, '同学')
                .replace(/\{商品标题\}/g, '我的宝贝')
                .replace(/\{商品价格\}/g, '¥99.00')
                .replace(/\{价格\}/g, '¥99.00');
            arPreview.classList.remove('hidden');
        };

        // 用后端数据刷新面板：开关状态、内容、展开收起、副标题（已应用 N 个在卖商品）
        const refreshArPanel = setting => {
            arGlobalSetting = setting;
            arGlobalOn = !!setting.enabled;
            const enabled = !!setting.enabled;
            arToggle.checked = enabled;
            arContent.value = setting.content || '';
            // T5.4 一次性规则回显
            document.getElementById('arReplyOnce').checked = setting.replyOnce !== false;
            arBody.classList.toggle('hidden', !enabled);
            arPanel.classList.toggle('on', enabled);
            arPanelSub.textContent = enabled
                ? `已开启，买家咨询会自动回复 · 已应用到 ${setting.appliedCount || 0} 个在卖商品`
                : '未开启，买家咨询不会自动回复';
            renderArPreview();
        };

        const loadArGlobal = async () => {
            try {
                refreshArPanel(await api('/api/auto-reply/global'));
            } catch (error) { toast(error.message); }
        };

        arToggle.addEventListener('change', () => {
            arBody.classList.toggle('hidden', !arToggle.checked);
            arPanel.classList.toggle('on', arToggle.checked);
            // 打开/关闭即时反馈；保存后 refreshArPanel 会用后端统计覆盖
            arPanelSub.textContent = arToggle.checked
                ? '已开启，买家咨询会自动回复 · 保存后生效'
                : '未开启，买家咨询不会自动回复';
        });
        arContent.addEventListener('input', renderArPreview);

        const saveAutoReplyGlobal = async () => {
            try {
                const setting = await api('/api/auto-reply/global', { method: 'PUT', body: JSON.stringify({ enabled: arToggle.checked, content: arContent.value, replyOnce: document.getElementById('arReplyOnce').checked }) });
                refreshArPanel(setting);
                toast('已保存全局智能回复');
            } catch (error) { toast(error.message); }
        };

        // T5.2 一键应用到全部在卖商品（overwrite 可选）
        const applyAllReply = async () => {
            arApplyAllBtn.disabled = true;
            try {
                const result = await api('/api/auto-reply/apply-all', { method: 'POST', body: JSON.stringify({ overwrite: document.getElementById('arOverwrite').checked }) });
                let message = `已应用到 ${result.applied} 个在卖商品`;
                if (result.overwritten) message += `，覆盖 ${result.overwritten} 个`;
                if (result.skipped) message += `，跳过 ${result.skipped} 个已单独设置`;
                toast(message);
                await load();          // 刷新卡片徽标
                await loadArGlobal();  // 刷新"已应用 N 个"统计
            } catch (error) { toast(error.message); }
            finally { arApplyAllBtn.disabled = false; }
        };

        let arProductId = null;
        // T5.3 单品弹窗：开关打开→编辑内容；关闭→显示"当前继承全局"摘要
        const arProductField = document.getElementById('arProductField');
        const arInheritTip = document.getElementById('arInheritTip');
        const arInheritText = document.getElementById('arInheritText');
        const updateProductArView = () => {
            const enabled = document.getElementById('arProductEnabled').checked;
            arProductField.classList.toggle('hidden', !enabled);
            arInheritTip.classList.toggle('hidden', enabled);
            if (!enabled && arGlobalSetting && arGlobalSetting.enabled && arGlobalSetting.content) {
                const c = arGlobalSetting.content;
                arInheritText.textContent = c.length > 30 ? c.slice(0, 30) + '…' : c;
            }
        };
        const openAutoReplyProduct = async (id) => {
            arProductId = id;
            const product = all.find(item => item.id === id);
            try {
                const setting = await api(`/api/products/${id}/auto-reply`);
                document.getElementById('arProductTitle').textContent = `智能回复 · ${product ? product.title : '商品'}`;
                document.getElementById('arProductEnabled').checked = !!setting.enabled;
                document.getElementById('arProductText').value = setting.text || '';
                updateProductArView();
                document.getElementById('autoReplyProductModal').classList.remove('hidden');
            } catch (error) { toast(error.message); }
        };
        const saveAutoReplyProduct = async () => {
            try {
                await api(`/api/products/${arProductId}/auto-reply`, { method: 'PUT', body: JSON.stringify({ enabled: document.getElementById('arProductEnabled').checked, text: document.getElementById('arProductText').value }) });
                toast('已保存该商品的智能回复');
                document.getElementById('autoReplyProductModal').classList.add('hidden');
                await load();  // 同步卡片徽标
            } catch (error) { toast(error.message); }
        };
        const resetAutoReplyProduct = async () => {
            try {
                await api(`/api/products/${arProductId}/auto-reply`, { method: 'PUT', body: JSON.stringify({ enabled: false, text: '' }) });
                document.getElementById('arProductEnabled').checked = false;
                document.getElementById('arProductText').value = '';
                toast('已恢复为继承全局默认');
            } catch (error) { toast(error.message); }
        };
        document.getElementById('arGlobalSave').addEventListener('click', saveAutoReplyGlobal);
        document.getElementById('arApplyAll').addEventListener('click', applyAllReply);
        document.getElementById('arProductEnabled').addEventListener('change', updateProductArView);
        document.getElementById('arProductSave').addEventListener('click', saveAutoReplyProduct);
        document.getElementById('arProductReset').addEventListener('click', resetAutoReplyProduct);
        document.querySelectorAll('[data-template]').forEach(btn => btn.addEventListener('click', () => {
            arContent.value = AR_TEMPLATES[Number(btn.dataset.template)];
            renderArPreview();
        }));
        document.querySelectorAll('[data-close-modal]').forEach(button => button.addEventListener('click', () => button.closest('.modal').classList.add('hidden')));
        list.addEventListener('click', event => {
            const btn = event.target.closest('[data-auto-reply]');
            if (btn) openAutoReplyProduct(Number(btn.dataset.autoReply));
        });
        await loadArGlobal();
        await load();
    }

    async function initCart() {
        if (!requireLogin()) return;
        state.cart = await api('/api/cart');
        renderCart();
        document.getElementById('checkoutButton').addEventListener('click', () => {
            const ids = selectedCartIds();
            if (!ids.length) { toast('请至少选择一件商品'); return; }
            sessionStorage.setItem('checkoutItems', JSON.stringify(ids));
            location.href = '/checkout.html';
        });
        document.getElementById('selectAllCart').addEventListener('change', event => {
            document.querySelectorAll('.cart-check:not(:disabled)').forEach(input => { input.checked = event.currentTarget.checked; });
            updateCartSummary();
        });
        document.getElementById('deleteSelectedCart').addEventListener('click', deleteSelectedCart);
    }

    function renderCart() {
        const list = document.getElementById('cartList');
        if (!state.cart.length) { list.innerHTML = empty('🛒','购物车还是空的','去校园好物里看看吧'); updateCartSummary(); return; }
        list.innerHTML = state.cart.map(item => {
            const available = item.status === 'AVAILABLE';
            return `<article class="cart-item ${available?'':'cart-unavailable'}"><input class="checkbox cart-check" type="checkbox" value="${item.id}" data-price="${item.price}" ${available?'checked':'disabled'}><a href="/product-detail.html?id=${item.productId}"><img src="${esc(item.imageUrl || fallbackImage)}" alt="${esc(item.title)}" onerror="this.src='${fallbackImage}'"></a><div class="cart-product"><div class="cart-product-head"><a href="/product-detail.html?id=${item.productId}"><strong>${esc(item.title)}</strong></a>${item.cutByMe?'<em class="cut-badge cart-cut-badge">我的专属价</em>':''}<b>${money(item.price)}</b>${item.cutByMe && item.originalPrice != null && Number(item.originalPrice) > Number(item.price) ? `<s class="cart-old-price">${money(item.originalPrice)}</s>` : ''}</div><p>${esc(item.condition)} · ${esc(item.locationName)}</p><span class="single-stock">${available?'仅此一件 · 可购买':'商品已被预订或下架'}</span><div class="cart-actions"><button type="button" data-remove-cart="${item.id}">删除</button><button type="button" data-save-cart="${item.id}" data-product="${item.productId}">移入收藏</button><a href="/price-compare.html?product=${item.productId}">同款比价</a><button type="button" data-share-product="${item.productId}" data-title="${esc(item.title)}">分享</button></div></div></article>`;
        }).join('');
        list.querySelectorAll('.cart-check').forEach(input => input.addEventListener('change', updateCartSummary));
        list.querySelectorAll('[data-remove-cart]').forEach(button => button.addEventListener('click', () => removeCartItem(Number(button.dataset.removeCart))));
        list.querySelectorAll('[data-save-cart]').forEach(button => button.addEventListener('click', async () => {
            button.disabled = true;
            try {
                await api(`/api/favorites/${button.dataset.product}`, {method:'POST'});
                await removeCartItem(Number(button.dataset.saveCart), false);
                toast('已移入收藏');
            } catch (error) { toast(error.message); button.disabled = false; }
        }));
        list.querySelectorAll('[data-share-product]').forEach(button => button.addEventListener('click', () => shareProduct(button.dataset.product, button.dataset.title)));
        updateCartSummary();
    }

    async function removeCartItem(id, showMessage = true) {
        try {
            await api(`/api/cart/${id}`, {method:'DELETE'});
            state.cart = state.cart.filter(item => item.id !== id);
            renderCart();
            setBadge('cartBadge', state.cart.length);
            if (showMessage) toast('商品已从购物车移除');
        } catch (error) { toast(error.message); }
    }

    async function deleteSelectedCart() {
        const ids = selectedCartIds();
        if (!ids.length) { toast('请先选择要删除的商品'); return; }
        if (!confirm(`确定删除选中的 ${ids.length} 件商品吗？`)) return;
        try {
            await Promise.all(ids.map(id => api(`/api/cart/${id}`, {method:'DELETE'})));
            state.cart = state.cart.filter(item => !ids.includes(item.id));
            renderCart();
            setBadge('cartBadge', state.cart.length);
            toast('所选商品已删除');
        } catch (error) { toast(error.message); }
    }

    async function shareProduct(productId, title) {
        const url = `${location.origin}/product-detail.html?id=${productId}`;
        try {
            if (navigator.share) await navigator.share({title, text:`在 StudentBay 发现一件校园闲置：${title}`, url});
            else if (navigator.clipboard) { await navigator.clipboard.writeText(url); toast('商品链接已复制'); }
            else window.prompt('复制商品链接', url);
        } catch (error) { if (error.name !== 'AbortError') toast('暂时无法分享'); }
    }

    function selectedCartIds() { return [...document.querySelectorAll('.cart-check:checked')].map(input => Number(input.value)); }
    function updateCartSummary() {
        const selected = [...document.querySelectorAll('.cart-check:checked')];
        const available = [...document.querySelectorAll('.cart-check:not(:disabled)')];
        document.getElementById('selectedCount').textContent = `${selected.length} 件`;
        document.getElementById('cartTotal').textContent = money(selected.reduce((sum,input) => sum + Number(input.dataset.price), 0));
        const selectAll = document.getElementById('selectAllCart');
        if (selectAll) {
            selectAll.checked = available.length > 0 && selected.length === available.length;
            selectAll.indeterminate = selected.length > 0 && selected.length < available.length;
        }
        const reward = document.getElementById('bulkReward');
        if (reward) reward.innerHTML = selected.length >= 3
            ? `<span class="reward-ticket">奖励券</span><strong>本单可得满 ¥50 减 ¥5 券</strong><p>同时结算 3 件及以上闲置商品，提交订单后自动发放，有效期 30 天。</p>`
            : `<span class="reward-ticket muted-ticket">多件奖励</span><strong>再选 ${Math.max(0,3-selected.length)} 件可得优惠券</strong><p>一次结算 3 件即可获得平台奖励券。</p>`;
        const unavailableCount = state.cart.filter(item => item.status !== 'AVAILABLE').length;
        const hint = document.getElementById('cartAvailabilityHint');
        if (hint) hint.textContent = unavailableCount ? `${unavailableCount} 件商品当前不可购买` : '在售商品已全部选中';
    }

    async function initCheckout() {
        if (!requireLogin()) return;
        let ids;
        try { ids = JSON.parse(sessionStorage.getItem('checkoutItems') || '[]'); } catch { ids = []; }
        if (!ids.length) { location.href = '/cart.html'; return; }
        const [cartItems, addresses, coupons, rewards] = await Promise.all([api('/api/cart'), api('/api/account/addresses'), api('/api/account/coupons'), api('/api/rewards/summary')]);
        state.cart = cartItems.filter(item => ids.includes(item.id)); state.addresses = addresses; state.coupons = coupons; state.rewards = rewards;
        if (!state.cart.length) { location.href='/cart.html'; return; }
        document.getElementById('checkoutItems').innerHTML = state.cart.map(item => `<div class="summary-row"><span>${esc(item.title)}</span><strong>${money(item.price)}</strong></div>`).join('');
        const sellerCount = new Set(state.cart.map(item => item.sellerId)).size;
        document.getElementById('sellerSplitHint').innerHTML = `<strong>${sellerCount} 位卖家</strong><span>支付后将生成 ${sellerCount} 张子订单</span>`;
        const today = new Date();
        document.getElementById('pickupDate').value = [today.getFullYear(), String(today.getMonth()+1).padStart(2,'0'), String(today.getDate()).padStart(2,'0')].join('-');
        document.getElementById('pickupLocation').value = state.cart[0].locationName || 'Library North Gate';
        renderAddresses();
        document.getElementById('couponList').innerHTML = `<label class="address-option"><input type="radio" name="coupon" value="" checked> 不使用优惠券</label>` + coupons.map(coupon => `<label class="address-option"><input type="radio" name="coupon" value="${coupon.id}" data-discount="${coupon.discountAmount}" data-minimum="${coupon.minimumAmount}"> <strong>${esc(coupon.name)}</strong>${coupon.ownerUserId?'<em class="reward-coupon-badge">平台奖励</em>':''}<small style="display:block;margin:6px 0 0 25px;color:var(--muted)">满 ${money(coupon.minimumAmount)} 减 ${money(coupon.discountAmount)}${coupon.expiresAt?` · ${new Date(coupon.expiresAt).toLocaleDateString('zh-CN')} 到期`:''}</small></label>`).join('');
        document.querySelectorAll('[name=shipping],[name=coupon]').forEach(input => input.addEventListener('change', updateCheckoutSummary));
        const useCoins = document.getElementById('useCoins');
        useCoins.disabled = rewards.coinBalance <= 0;
        useCoins.addEventListener('change', updateCheckoutSummary);
        document.getElementById('showAddressForm').addEventListener('click', () => openAddressForm());
        document.getElementById('cancelAddressEdit').addEventListener('click', () => closeAddressForm());
        document.getElementById('addressForm').addEventListener('submit', saveAddress);
        document.getElementById('submitOrder').addEventListener('click', submitCheckout);
        updateCheckoutSummary();
        loadCheckoutPriceInsights();
    }

    async function loadCheckoutPriceInsights() {
        const host = document.getElementById('checkoutPriceInsights');
        const results = await Promise.allSettled(state.cart.map(item => api(`/api/products/${item.productId}/comparisons`)));
        const insights = results.map((result,index) => ({item:state.cart[index], data:result.status === 'fulfilled' ? result.value : null}));
        host.innerHTML = insights.map(({item,data}) => {
            const average = data?.statistics?.averagePrice;
            const difference = data?.statistics?.differencePercent;
            if (average == null || difference == null) {
                return `<article class="price-guard-row neutral"><div><strong>${esc(item.title)}</strong><small>暂无足够的同类挂牌数据</small></div><span>参考样本不足</span></article>`;
            }
            const below = Number(difference) < 0;
            const equal = Number(difference) === 0;
            const direction = equal ? '≈ 与均价持平' : `${below ? '↓' : '↑'} ${Math.abs(Number(difference)).toFixed(1)}% ${below ? '低于均价' : '高于均价'}`;
            return `<article class="price-guard-row ${below?'favorable':'attention'}"><div><strong>${esc(item.title)}</strong><small>市场均价 ${money(average)}　当前价格 ${money(item.price)}</small></div><span>${direction}</span></article>`;
        }).join('');
        setupRevealMotion();
    }

    function maskPhone(phone) {
        const p = String(phone || '').trim();
        return p.length === 11 ? p.slice(0, 3) + '****' + p.slice(7) : p;
    }

    function renderAddresses(selectedId = null) {
        if (page === 'settings') { renderSettingsAddresses(); return; }
        const list = document.getElementById('addressList');
        const currentId = selectedId || Number(document.querySelector('[name=address]:checked')?.value) || state.addresses.find(item => item.defaultAddress)?.id || state.addresses[0]?.id;
        list.innerHTML = state.addresses.length ? state.addresses.map(address => `<div class="address-option address-card">
            <label class="address-select"><input type="radio" name="address" value="${address.id}" ${address.id===currentId?'checked':''}>
                <span><strong>${esc(address.recipientName)}　${esc(maskPhone(address.phone))}</strong>${address.defaultAddress?'<em class="default-badge">默认</em>':''}<small>${esc(address.detail)}</small></span>
            </label>
            <div class="address-actions"><button class="text-button" type="button" data-edit-address="${address.id}">修改</button><button class="text-button danger-text" type="button" data-delete-address="${address.id}">删除</button></div>
        </div>`).join('') : '<div class="empty-inline">还没有收货地址，请先新增。</div>';
        list.querySelectorAll('[data-edit-address]').forEach(button => button.addEventListener('click', () => {
            const address = state.addresses.find(item => item.id === Number(button.dataset.editAddress));
            if (address) openAddressForm(address);
        }));
        list.querySelectorAll('[data-delete-address]').forEach(button => button.addEventListener('click', () => deleteAddress(Number(button.dataset.deleteAddress))));
    }

    let addrPicker = { map: null, marker: null, selected: null, regionData: null, regionCache: null, initialized: false };
    const ADDR_CAMPUS_STREETS = ['广东东软学院', '狮山镇', '桂城街道', '大沥镇', '里水镇'];
    const ADDR_ZOOM = { country: 5, province: 7, city: 9, district: 11, street: 12 };
    const ADDR_REGION_ORDER = ['country', 'province', 'city', 'district', 'street'];

    async function setupAddressLocationPicker() {
        if (addrPicker.initialized) {
            if (addrPicker.map) setTimeout(() => addrPicker.map.invalidateSize(), 80);
            return;
        }
        addrPicker.initialized = true;
        const selects = {
            country: document.getElementById('addrCountry'),
            province: document.getElementById('addrProvince'),
            city: document.getElementById('addrCity'),
            district: document.getElementById('addrDistrict'),
            street: document.getElementById('addrStreet')
        };
        const detailInput = document.getElementById('addrDetailLine');
        const display = document.getElementById('addrLocationDisplay');
        const coordText = document.getElementById('addrCoordinateText');
        const hiddenDetail = document.getElementById('addressDetail');

        const setOptions = (select, values) => {
            select.innerHTML = '<option value="">请选择</option>' + values.map(v => `<option value="${esc(v)}">${esc(v)}</option>`).join('');
            if (values.length) select.value = values[0];
        };
        const childrenOf = node => {
            if (node == null) return [];
            if (Array.isArray(node)) return node;
            return node.s ? Object.keys(node.s) : Object.keys(node);
        };
        const coordOf = node => (node && node.c) ? node.c : null;
        const nodeAt = index => {
            let node = addrPicker.regionData;
            for (let j = 0; j <= index; j++) {
                const v = selects[ADDR_REGION_ORDER[j]].value;
                if (!node || !v) return null;
                const children = node.s != null ? node.s : node;
                if (children[v] != null) node = children[v];
                else return null;
            }
            return node;
        };
        const compose = () => {
            const region = ADDR_REGION_ORDER.map(k => selects[k].value).filter(Boolean).join('');
            const detail = detailInput.value.trim();
            const composed = detail ? region + detail : region;
            hiddenDetail.value = composed;
            display.textContent = composed || '未选择';
            display.classList.toggle('is-empty', !composed);
            return composed;
        };
        const cascade = fromIndex => {
            for (let i = fromIndex + 1; i < ADDR_REGION_ORDER.length; i++) {
                const key = ADDR_REGION_ORDER[i];
                const node = nodeAt(i - 1);
                let options = node ? childrenOf(node) : [];
                if (key === 'street' && selects.district.value === '南海区') options = ADDR_CAMPUS_STREETS;
                setOptions(selects[key], options);
            }
            compose();
        };
        const onRegionChange = index => {
            cascade(index);
            const node = nodeAt(index);
            const coord = coordOf(node);
            const label = selects[ADDR_REGION_ORDER[index]].value;
            if (coord && label && addrPicker.map) {
                addrPicker.map.flyTo([coord.lat, coord.lng], ADDR_ZOOM[ADDR_REGION_ORDER[index]] || 10);
                coordText.textContent = '已定位到「' + label + '」所在区域，请点击地图选择精确位置';
            }
        };

        // Load regions
        addrPicker.regionData = addrPicker.regionCache || (addrPicker.regionCache = await (await fetch('/data/regions.json')).json());
        const countries = childrenOf(addrPicker.regionData);
        setOptions(selects.country, countries);
        selects.country.value = countries.includes('中国') ? '中国' : (countries[0] || '');
        const defaults = { province: '广东省', city: '佛山市', district: '南海区', street: '广东东软学院' };
        let node = addrPicker.regionData['中国'] || null;
        for (const key of ['province', 'city', 'district', 'street']) {
            if (!node) break;
            const children = key === 'street' ? ADDR_CAMPUS_STREETS : childrenOf(node);
            setOptions(selects[key], children);
            if (children.includes(defaults[key])) { selects[key].value = defaults[key]; node = node.s ? node.s[defaults[key]] : null; }
            else break;
        }
        compose();

        selects.country.addEventListener('change', () => onRegionChange(0));
        selects.province.addEventListener('change', () => onRegionChange(1));
        selects.city.addEventListener('change', () => onRegionChange(2));
        selects.district.addEventListener('change', () => onRegionChange(3));
        selects.street.addEventListener('change', compose);
        detailInput.addEventListener('input', compose);

        // Map
        try {
            const config = await mapConfig();
            addrPicker.map = createMap('addrMap', config, config.lat, config.lng, true);
            setTimeout(() => addrPicker.map.invalidateSize(), 100);
            addrPicker.map.on('click', event => {
                addrPicker.selected = { lat: event.latlng.lat, lng: event.latlng.lng };
                if (addrPicker.marker) addrPicker.marker.setLatLng(event.latlng);
                else addrPicker.marker = L.marker(event.latlng).addTo(addrPicker.map);
                const lat = event.latlng.lat, lng = event.latlng.lng;
                api(`/api/geocode/reverse?lat=${lat}&lng=${lng}`).then(data => {
                    if (data.found) {
                        if (data.name) detailInput.value = data.name;
                        if (data.formatted) coordText.textContent = '已选择：' + data.formatted;
                        compose();
                    } else {
                        coordText.textContent = '已选择自定义位置：' + lat.toFixed(6) + ', ' + lng.toFixed(6) + '，可填写具体位置';
                    }
                }).catch(() => { coordText.textContent = '已选择：' + lat.toFixed(6) + ', ' + lng.toFixed(6); });
            });
        } catch (e) { coordText.textContent = '地图加载失败，请手动填写地址'; }

        // Locate button
        document.getElementById('addrLocateBtn').addEventListener('click', async () => {
            const address = compose();
            if (!address) { toast('请先选择省/市/区/街道并填写具体位置'); return; }
            const btn = document.getElementById('addrLocateBtn');
            btn.disabled = true; btn.textContent = '定位中…';
            try {
                const data = await api(`/api/geocode?address=${encodeURIComponent(address)}`);
                if (data.found) {
                    const lat = Number(data.latitude), lng = Number(data.longitude);
                    addrPicker.selected = { lat, lng };
                    if (addrPicker.marker) addrPicker.marker.setLatLng([lat, lng]);
                    else addrPicker.marker = L.marker([lat, lng]).addTo(addrPicker.map);
                    addrPicker.map.setView([lat, lng], Math.max(addrPicker.map.getZoom(), 16));
                    coordText.textContent = data.formatted ? '已定位：' + data.formatted : '已定位到所选地址';
                    toast('已定位到所选地址');
                } else {
                    toast('未找到匹配地点，请在地图上手动选点');
                    coordText.textContent = '未找到匹配地点，可点击地图放置标记';
                }
            } catch (error) { toast(error.message); }
            finally { btn.disabled = false; btn.textContent = '定位到所选地址'; }
        });
    }

    function renderSettingsAddresses() {
        const list = document.getElementById('addressList');
        list.innerHTML = state.addresses.length ? state.addresses.map(address => `<div class="address-option address-card">
            <div class="address-select"><span><strong>${esc(address.recipientName)}　${esc(maskPhone(address.phone))}</strong>${address.defaultAddress?'<em class="default-badge">默认</em>':''}<small>${esc(address.detail)}</small></span></div>
            <div class="address-actions">${address.defaultAddress?'':`<button class="text-button" type="button" data-default-address="${address.id}">设为默认</button>`}<button class="text-button" type="button" data-edit-address="${address.id}">修改</button><button class="text-button danger-text" type="button" data-delete-address="${address.id}">删除</button></div>
        </div>`).join('') : '<div class="empty-inline">还没有收货地址，请先新增。</div>';
        list.querySelectorAll('[data-default-address]').forEach(button => button.addEventListener('click', () => setDefaultAddress(Number(button.dataset.defaultAddress))));
        list.querySelectorAll('[data-edit-address]').forEach(button => button.addEventListener('click', () => {
            const address = state.addresses.find(item => item.id === Number(button.dataset.editAddress));
            if (address) openAddressForm(address);
        }));
        list.querySelectorAll('[data-delete-address]').forEach(button => button.addEventListener('click', () => deleteAddress(Number(button.dataset.deleteAddress))));
    }

    async function setDefaultAddress(id) {
        const address = state.addresses.find(item => item.id === id);
        if (!address) return;
        if (address.defaultAddress) { toast('该地址已是默认地址'); return; }
        try {
            await api(`/api/account/addresses/${id}`, { method:'PUT', body: JSON.stringify({
                recipientName: address.recipientName, phone: address.phone, detail: address.detail, defaultAddress: true,
                latitude: address.latitude, longitude: address.longitude
            }) });
            state.addresses = await api('/api/account/addresses');
            renderAddresses();
            toast('已设为默认地址');
        } catch (error) { toast(error.message); }
    }

    function openAddressForm(address = null) {
        const form = document.getElementById('addressForm');
        form.reset();
        const phoneInput = document.getElementById('recipientPhone');
        if (phoneInput && !phoneInput.dataset.phoneGuard) {
            phoneInput.dataset.phoneGuard = '1';
            phoneInput.addEventListener('input', () => { phoneInput.value = phoneInput.value.replace(/\D/g, '').slice(0, 11); });
        }
        document.getElementById('addressId').value = address?.id || '';
        document.getElementById('addressFormTitle').textContent = address ? '修改收货地址' : '新增收货地址';
        document.getElementById('addressSubmitText').textContent = address ? '保存修改' : '保存地址';
        document.getElementById('recipientName').value = address?.recipientName || '';
        document.getElementById('recipientPhone').value = address?.phone || '';
        document.getElementById('defaultAddress').checked = Boolean(address?.defaultAddress);
        form.classList.remove('hidden');
        setupAddressLocationPicker().then(() => {
            // Reset picker state for new address
            addrPicker.selected = null;
            if (addrPicker.marker) { addrPicker.marker.remove(); addrPicker.marker = null; }
            // For edit: put existing detail into the detail line, set marker if coords exist
            if (address) {
                document.getElementById('addrDetailLine').value = address.detail || '';
                if (address.latitude != null && address.longitude != null && addrPicker.map) {
                    addrPicker.selected = { lat: Number(address.latitude), lng: Number(address.longitude) };
                    addrPicker.marker = L.marker([address.latitude, address.longitude]).addTo(addrPicker.map);
                    addrPicker.map.setView([address.latitude, address.longitude], 16);
                }
            } else {
                document.getElementById('addrDetailLine').value = '';
            }
            // Trigger compose to update hidden field and display
            const evt = new Event('input');
            document.getElementById('addrDetailLine').dispatchEvent(evt);
            if (addrPicker.map) setTimeout(() => addrPicker.map.invalidateSize(), 120);
        });
        document.getElementById('recipientName').focus();
    }

    function closeAddressForm() {
        const form = document.getElementById('addressForm');
        form.reset();
        document.getElementById('addressId').value = '';
        form.classList.add('hidden');
    }

    async function saveAddress(event) {
        event.preventDefault();
        const editingId = Number(document.getElementById('addressId').value) || null;
        const phoneValue = value('recipientPhone').trim();
        if (!/^\d{11}$/.test(phoneValue)) { toast('联系电话需为 11 位数字'); return; }
        const body = {
            recipientName:value('recipientName'), phone:value('recipientPhone'), detail:value('addressDetail'),
            defaultAddress:document.getElementById('defaultAddress').checked,
            latitude: addrPicker.selected ? addrPicker.selected.lat : null,
            longitude: addrPicker.selected ? addrPicker.selected.lng : null
        };
        const button = event.currentTarget.querySelector('[type=submit]');
        button.disabled = true;
        let saved;
        try {
            saved = await api(editingId ? `/api/account/addresses/${editingId}` : '/api/account/addresses', {
                method:editingId ? 'PUT' : 'POST', body:JSON.stringify(body)
            });
        } catch (error) {
            toast(error.message);
            button.disabled = false;
            return;
        }
        closeAddressForm();
        try {
            state.addresses = await api('/api/account/addresses');
            renderAddresses(saved.id);
            toast(editingId ? '地址已修改' : '地址已添加');
        } catch {
            toast(editingId ? '地址已修改，请刷新页面查看' : '地址已添加，请刷新页面查看');
        }
        button.disabled = false;
    }

    async function deleteAddress(id) {
        const address = state.addresses.find(item => item.id === id);
        if (!address || !confirm(`确定删除“${address.recipientName} ${address.detail}”吗？`)) return;
        try {
            await api(`/api/account/addresses/${id}`, {method:'DELETE'});
            state.addresses = await api('/api/account/addresses');
            renderAddresses();
            if (Number(document.getElementById('addressId').value) === id) closeAddressForm();
            toast('地址已删除');
        } catch (error) { toast(error.message); }
    }

    function updateCheckoutSummary() {
        const productAmount = state.cart.reduce((sum,item) => sum + Number(item.price), 0);
        const delivery = document.querySelector('[name=shipping]:checked')?.value === 'DELIVERY';
        document.getElementById('addressSection').classList.toggle('hidden', !delivery);
        document.getElementById('pickupSection').classList.toggle('hidden', delivery);
        const shipping = delivery ? 5 * new Set(state.cart.map(item => item.sellerId)).size : 0;
        const coupon = document.querySelector('[name=coupon]:checked');
        const sellerTotals = state.cart.reduce((totals,item) => totals.set(item.sellerId, (totals.get(item.sellerId) || 0) + Number(item.price)), new Map());
        const eligible = coupon?.value && [...sellerTotals.values()].some(total => total >= Number(coupon.dataset.minimum));
        const discount = eligible ? Number(coupon.dataset.discount) : 0;
        const grossPayable = Math.max(0, productAmount + shipping - discount);
        const maximumCoins = Math.floor(grossPayable * .2 * Number(state.rewards?.coinsPerYuan || 10));
        const useCoins = document.getElementById('useCoins')?.checked;
        const coinsUsed = useCoins ? Math.min(Number(state.rewards?.coinBalance || 0), maximumCoins) : 0;
        const coinDiscount = coinsUsed / Number(state.rewards?.coinsPerYuan || 10);
        document.getElementById('productAmount').textContent = money(productAmount);
        document.getElementById('shippingAmount').textContent = money(shipping);
        document.getElementById('discountAmount').textContent = `-${money(discount)}`;
        document.getElementById('coinDiscountAmount').textContent = `-${money(coinDiscount)}`;
        document.getElementById('payableAmount').textContent = money(grossPayable - coinDiscount);
        document.getElementById('coinBalanceText').textContent = `可用 ${state.rewards?.coinBalance || 0} 金币`;
        document.getElementById('coinUsageText').textContent = useCoins
            ? `本单使用 ${coinsUsed} 金币，抵扣 ${money(coinDiscount)}`
            : `10 金币可抵 ¥1.00，本单最多可用 ${maximumCoins} 金币`;
    }

    async function submitCheckout() {
        const shippingMethod = document.querySelector('[name=shipping]:checked').value;
        const addressId = shippingMethod === 'DELIVERY' ? Number(document.querySelector('[name=address]:checked')?.value) || null : null;
        if (shippingMethod === 'DELIVERY' && !addressId) { toast('请选择或新增收货地址'); return; }
        if (shippingMethod === 'PICKUP' && (!value('pickupDate') || !value('pickupStartTime') || !value('pickupEndTime') || !value('pickupLocation'))) { toast('请填写完整的面交时间和地点'); return; }
        const couponValue = document.querySelector('[name=coupon]:checked')?.value;
        const body = { cartItemIds:state.cart.map(item => item.id), addressId, shippingMethod, couponId:couponValue?Number(couponValue):null, paymentMethod:document.querySelector('[name=payment]:checked').value, useCoins:document.getElementById('useCoins').checked, pickupDate:shippingMethod==='PICKUP'?value('pickupDate'):null, pickupStartTime:shippingMethod==='PICKUP'?value('pickupStartTime'):null, pickupEndTime:shippingMethod==='PICKUP'?value('pickupEndTime'):null, pickupLocation:shippingMethod==='PICKUP'?value('pickupLocation'):null };
        const submit = document.getElementById('submitOrder');
        submit.disabled = true;
        submit.textContent = '正在拆分订单...';
        try {
            const orders = await api('/api/order/checkout', {method:'POST',body:JSON.stringify(body)});
            if (state.cart.length >= 3) sessionStorage.setItem('multiBuyReward', 'true');
            sessionStorage.removeItem('checkoutItems');
            location.href = `/payment.html?orders=${orders.map(order=>order.id).join(',')}`;
        } catch(error) { toast(error.message); submit.disabled = false; submit.textContent = '提交订单'; }
    }

    async function initPayment() {
        if (!requireLogin()) return;
        const ids = (query('orders') || '').split(',').filter(Boolean);
        if (!ids.length) { location.href='/orders.html'; return; }
        const orders = await Promise.all(ids.map(id => api(`/api/order/${id}`)));
        const [riskResults, alipayStatus] = await Promise.all([
            Promise.all(orders.map(order => api(`/api/risk/orders/${order.id}`).catch(() => ({alerts:[],riskLevel:'CLEAR'})))),
            api('/api/payments/alipay/status').catch(() => ({enabled:false,mode:'SANDBOX'}))
        ]);
        document.getElementById('paymentOrders').innerHTML = orders.map((order,index) => `<article class="payment-order"><div><small>子订单 ${String(index+1).padStart(2,'0')} · 卖家 ${order.sellerId}</small><strong>${order.items.map(item=>esc(item.productTitle)).join('、')}</strong></div><b>${money(order.totalAmount)}</b></article>`).join('');
        document.getElementById('paymentTotal').textContent = money(orders.reduce((sum,order)=>sum+Number(order.totalAmount),0));
        const alerts = riskResults.flatMap(result => result.alerts || []);
        document.getElementById('paymentRiskAlerts').innerHTML = alerts.length
            ? `<div class="risk-summary"><span>${alerts.some(alert=>alert.severity==='HIGH')?'付款前请确认':'交易提示'}</span><strong>${alerts.length} 项风险检查结果</strong></div>${alerts.map(alert=>`<article class="risk-alert severity-${String(alert.severity).toLowerCase()}"><i>${alert.severity==='HIGH'?'!':'i'}</i><div><strong>${esc(alert.title)}</strong><p>${esc(alert.message)}</p></div></article>`).join('')}`
            : '<article class="risk-clear"><i>✓</i><div><strong>交易检查通过</strong><p>暂未发现大额、频繁取消或异常改价风险。</p></div></article>';
        const useAlipaySandbox = orders.length === 1 && orders[0].paymentStatus === 'UNPAID'
            && orders[0].paymentMethod === 'ALIPAY' && alipayStatus.enabled;
        if (useAlipaySandbox) {
            document.getElementById('paymentModeLabel').textContent = '支付宝沙箱';
            document.getElementById('paymentModeBadge').textContent = 'ALIPAY SANDBOX';
            document.getElementById('payNowLabel').textContent = '前往支付宝沙箱';
            document.getElementById('paymentFeedback').textContent = '将跳转到支付宝沙箱收银台，请使用沙箱买家账号';
        } else if (orders.some(order => order.paymentMethod === 'ALIPAY') && !alipayStatus.enabled) {
            document.getElementById('paymentFeedback').textContent = '支付宝沙箱未配置，本次使用本地模拟支付';
        } else if (orders.length > 1 && orders.some(order => order.paymentMethod === 'ALIPAY') && alipayStatus.enabled) {
            document.getElementById('paymentFeedback').textContent = '多卖家合并结算暂使用本地模拟；单卖家订单可跳转支付宝沙箱';
        }
        document.getElementById('payNow').addEventListener('click', async event => {
            const button = event.currentTarget;
            const label = document.getElementById('payNowLabel');
            const feedback = document.getElementById('paymentFeedback');
            button.disabled = true;
            button.classList.add('processing');
            if (useAlipaySandbox) {
                label.textContent = '正在打开沙箱收银台…';
                feedback.textContent = '正在为订单创建支付宝沙箱交易';
                location.href = `/api/payments/alipay/start/${orders[0].id}`;
                return;
            }
            label.textContent = 'Processing payment...';
            feedback.textContent = '正在验证订单与支付会话';
            try {
                const payment = Promise.all(orders.filter(order => order.paymentStatus === 'UNPAID').map(order => {
                    const storageKey = `studentbay-payment-key-${order.id}`;
                    let idempotencyKey = sessionStorage.getItem(storageKey);
                    if (!idempotencyKey) {
                        idempotencyKey = window.crypto?.randomUUID?.() || `pay-${order.id}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
                        sessionStorage.setItem(storageKey, idempotencyKey);
                    }
                    return api(`/api/order/${order.id}/payment`, {method:'POST',body:JSON.stringify({paymentMethod:order.paymentMethod || 'WECHAT_PAY',idempotencyKey})});
                }));
                await Promise.all([payment, new Promise(resolve => window.setTimeout(resolve, 800))]);
                button.classList.remove('processing');
                button.classList.add('paid');
                label.textContent = '✓ Payment successful';
                feedback.textContent = `${orders.length} 张子订单已支付成功`;
                sessionStorage.setItem('lastPaymentOrderCount', String(orders.length));
                window.setTimeout(() => { location.href='/payment-success.html'; }, 900);
            } catch(error) {
                toast(error.message);
                button.disabled = false;
                button.classList.remove('processing');
                label.textContent = '确认支付';
                feedback.textContent = '支付未完成，请检查后重试';
            }
        });
    }

    function initPaymentSuccess() {
        const host = document.getElementById('paymentReward');
        const orderCount = Number(sessionStorage.getItem('lastPaymentOrderCount') || 0);
        if (orderCount) {
            const summary = document.createElement('p');
            summary.className = 'success-order-count';
            summary.textContent = `${orderCount} 张卖家子订单已创建并完成支付`;
            host.before(summary);
            sessionStorage.removeItem('lastPaymentOrderCount');
        }
        if (sessionStorage.getItem('multiBuyReward') === 'true') {
            host.innerHTML = `<div class="success-reward"><span>券</span><div><strong>满 ¥50 减 ¥5 奖励券已到账</strong><small>一次结算 3 件商品奖励，有效期 30 天，可在下次订单使用。</small></div></div>`;
            sessionStorage.removeItem('multiBuyReward');
        }
    }

    const statusText = {PENDING_PAYMENT:'待付款',PAID:'待发货',COURIER_BOOKED:'已预约取件',SHIPPED:'待收货',COMPLETED:'已完成',RETURN_REQUESTED:'退货审核中',RETURN_APPROVED:'退货已同意',INTERVENTION:'平台介入中',REFUND_APPROVED:'退款待执行',REFUNDED:'已退款',CANCELLED:'已取消'};
    function openReviewModal(orderId, onDone) {
        let rating = 0;
        const backdrop = document.createElement('div');
        backdrop.className = 'review-modal-backdrop';
        backdrop.innerHTML = `<div class="review-modal" role="dialog" aria-modal="true">
            <h3>评价订单</h3>
            <p>你的评价将帮助其他同学做出选择</p>
            <div class="review-star-row" id="reviewStars">
                ${[1,2,3,4,5].map(i=>`<span class="review-star" data-star="${i}">★</span>`).join('')}
            </div>
            <textarea class="review-textarea" id="reviewContent" placeholder="说说你的交易体验（选填）" maxlength="500"></textarea>
            <div class="review-modal-actions">
                <button class="btn btn-outline" id="reviewCancel" type="button">取消</button>
                <button class="btn btn-primary" id="reviewSubmit" type="button" disabled>提交评价</button>
            </div>
        </div>`;
        document.body.appendChild(backdrop);
        const stars = backdrop.querySelectorAll('.review-star');
        const submitBtn = backdrop.querySelector('#reviewSubmit');
        stars.forEach(star => {
            star.addEventListener('mouseenter', () => { const v=Number(star.dataset.star); stars.forEach(s=>s.classList.toggle('hover',Number(s.dataset.star)<=v)); });
            star.addEventListener('mouseleave', () => stars.forEach(s=>s.classList.remove('hover')));
            star.addEventListener('click', () => { rating=Number(star.dataset.star); stars.forEach(s=>s.classList.toggle('active',Number(s.dataset.star)<=rating)); submitBtn.disabled=false; });
        });
        backdrop.querySelector('#reviewCancel').addEventListener('click', () => backdrop.remove());
        backdrop.addEventListener('click', e => { if (e.target === backdrop) backdrop.remove(); });
        submitBtn.addEventListener('click', async () => {
            if (!rating) return;
            submitBtn.disabled = true;
            try {
                await api('/api/reviews', { method:'POST', body: JSON.stringify({ orderId, rating, content: backdrop.querySelector('#reviewContent').value }) });
                toast('评价已发布');
                backdrop.remove();
                if (onDone) onDone();
            } catch (error) { toast(error.message); submitBtn.disabled = false; }
        });
    }

    function formatPreview(text) {
        if (!text) return '';
        const orderEvent = parseOrderEvent(text);
        if (orderEvent) return `订单提醒 · ${orderEvent.title}`;
        if (text.startsWith('[[REVIEW:')) {
            const end = text.indexOf(']]');
            const body = text.substring(end + 2);
            const firstLine = body.split('\n')[0] || '评价';
            return '⭐ ' + firstLine;
        }
        return text;
    }

    function parseOrderEvent(text) {
        const match = /^\[\[ORDER_EVENT:([A-Z_]+):(\d+)\]\]([\s\S]*)$/.exec(text || '');
        if (!match) return null;
        const lines = match[3].split('\n');
        return {type:match[1], orderId:Number(match[2]), title:lines[0] || '订单状态有更新', detail:lines.slice(1).join('\n')};
    }

    function renderChatMessage(message) {
        const raw = message.content;
        const orderEvent = parseOrderEvent(raw);
        if (orderEvent) {
            return `<div class="bubble order-event-bubble"><span class="chat-tag">订单提醒</span><strong>${esc(orderEvent.title)}</strong><p>${esc(orderEvent.detail || '请进入订单详情查看最新状态')}</p><a href="/order-detail.html?order=${orderEvent.orderId}">查看订单详情 →</a><small>${dateTime(message.createdAt)}</small></div>`;
        }
        if (raw && raw.startsWith('[[REVIEW:')) {
            const end = raw.indexOf(']]');
            const rating = parseInt(raw.substring(9, end), 10);
            const body = raw.substring(end + 2);
            const lines = body.split('\n');
            const label = lines[0] || '评价';
            const content = lines.slice(1).join('\n');
            const stars = '★'.repeat(rating) + '☆'.repeat(5 - rating);
            return `<div class="bubble review-bubble ${message.mine?'mine':''}"><div class="review-bubble-label">${esc(label)}</div><div class="review-bubble-stars">${stars}</div><p class="review-bubble-text">${esc(content)}</p><small>${esc(message.senderName)} · ${dateTime(message.createdAt)}</small></div>`;
        }
        return `<div class="bubble ${message.mine?'mine':''}${message.autoReply?' bubble-auto':''}">${message.autoReply?'<span class="chat-tag">自动回复</span>':''}${esc(raw)}<small>${esc(message.senderName)} · ${dateTime(message.createdAt)}</small></div>`;
    }

    function orderCard(order, seller = false) {
        const item = order.items[0];
        const actionItems = [];
        const deliveryOrder = order.shippingMethod === 'DELIVERY';
        const afterSaleActive = ['RETURN_REQUESTED','RETURN_APPROVED','INTERVENTION','REFUND_APPROVED'].includes(order.status);
        if (!seller && order.status === 'PENDING_PAYMENT') actionItems.push(`<a class="btn btn-primary btn-small" href="/payment.html?orders=${order.id}">立即付款</a><button class="btn btn-ghost btn-small" data-cancel-order="${order.id}">取消订单</button>`);
        if (order.courierCompany) actionItems.push(`<a class="btn btn-outline btn-small" href="/logistics.html?order=${order.id}">${order.status === 'COURIER_BOOKED' ? '查看取件信息' : '查看物流'}</a>`);
        if (!seller && order.status === 'SHIPPED') actionItems.push(`<button class="btn btn-primary btn-small" data-receive="${order.id}" data-pickup="${!deliveryOrder}">${deliveryOrder?'确认收货':'输入卖家交付码'}</button>`);
        if (seller && order.status === 'PAID' && deliveryOrder) actionItems.push(`<button class="btn btn-primary btn-small" data-book-courier="${order.id}">预约快递</button>`);
        if (seller && order.status === 'PAID' && !deliveryOrder) actionItems.push(`<button class="btn btn-primary btn-small" data-confirm-handover="${order.id}">验证买家取件码</button>`);
        if (seller && afterSaleActive) actionItems.push(`<a class="btn btn-primary btn-small" href="/order-detail.html?order=${order.id}">${order.status==='RETURN_REQUESTED'?'处理买家售后申请':'查看售后进度'}</a>`);
        actionItems.push(`<a class="btn btn-outline btn-small" href="/order-detail.html?order=${order.id}">订单详情</a>`);
        if (order.status === 'COMPLETED') {
            if (order.myReviewSubmitted) {
                actionItems.push(`<span class="btn btn-ghost btn-small" style="opacity:.55;cursor:default">已评价</span>`);
            } else {
                actionItems.push(`<button class="btn btn-primary btn-small" data-review="${order.id}">评价</button>`);
            }
        }
        actionItems.push(`<button class="btn btn-ghost btn-small" data-order-more="${order.id}">更多</button>`);
        const actions = `<div style="display:flex;gap:7px;justify-content:flex-end;flex-wrap:wrap">${actionItems.join('')}</div>`;
        const logistics = order.trackingNumber ? `<p style="color:var(--muted);font-size:13px">${esc(order.courierCompany)} · 运单 ${esc(order.trackingNumber)}</p>` : order.pickupTime ? `<p style="color:var(--muted);font-size:13px">${esc(order.courierCompany)} · ${esc(order.pickupTime)}</p>` : '';
        const afterSaleNotice = seller && afterSaleActive ? `<div class="order-alert"><span>售后待处理</span><strong>${order.status==='RETURN_REQUESTED'?'买家已提交退款/退货申请，请及时审核':'本单售后状态已更新'}</strong></div>` : '';
        return `<article class="card order-card">${afterSaleNotice}<div class="order-head"><span>订单号 ${esc(order.orderNo)}</span><span>${dateTime(order.createdAt)}</span></div><div class="order-main"><div class="order-product"><div class="order-thumb">♻</div><div><strong>${esc(item.productTitle)}</strong><p style="color:var(--muted);font-size:13px">${deliveryOrder?'快递配送':'校园面交'} · ${esc(order.shippingAddress)}</p>${logistics}</div></div><div style="text-align:right"><span class="status ${order.status==='PENDING_PAYMENT'?'warning':order.status==='COMPLETED'?'done':''}">${statusText[order.status]||order.status}</span><p style="font-weight:900;font-size:18px">${money(order.totalAmount)}</p>${actions}</div></div></article>`;
    }

    async function initBuyerOrders() {
        if (!requireLogin()) return;
        if (query('alipay') === 'success') {
            toast('支付宝沙箱支付已验证，订单状态已更新');
            history.replaceState(null, '', '/orders.html');
        } else if (query('alipay') === 'verify-failed') {
            toast('支付返回验签未通过，请稍后刷新或查看沙箱交易记录');
            history.replaceState(null, '', '/orders.html');
        }
        const orders = await api('/api/order/mine/buyer');
        const list = document.getElementById('buyerOrderList');
        list.innerHTML = orders.length ? orders.map(order => orderCard(order)).join('') : empty('▤','还没有订单','从校园好物开始第一笔交易');
        list.querySelectorAll('[data-receive]').forEach(button => button.addEventListener('click', async () => {
            const pickup = button.dataset.pickup === 'true';
            const code = pickup ? prompt('请输入卖家向你出示的 6 位交付码') : null;
            if (pickup && code === null) return;
            if (!pickup && !confirm('确认已经收到并验收商品吗？')) return;
            try { await api(`/api/order/${button.dataset.receive}/confirm-receipt`,{method:'POST',body:pickup?JSON.stringify({code}):null}); await initBuyerOrders(); toast('已确认收货'); } catch(error){toast(error.message);}
        }));
        bindUnpaidCancellation(list, initBuyerOrders);
        bindOrderMore(list, orders, false);
        list.querySelectorAll('[data-review]').forEach(button => button.addEventListener('click', () => openReviewModal(Number(button.dataset.review), initBuyerOrders)));
        enableOrderLiveSync(list, initBuyerOrders);
    }

    function bindUnpaidCancellation(container, reload) {
        container.querySelectorAll('[data-cancel-order]').forEach(button => button.addEventListener('click', async () => {
            if (!confirm('确认取消这张未付款订单吗？商品会立即恢复可购买状态。')) return;
            button.disabled = true;
            try { await api(`/api/order/${button.dataset.cancelOrder}/cancel`, {method:'POST'}); toast('订单已取消，商品已释放'); await reload(); }
            catch (error) { toast(error.message); button.disabled = false; }
        }));
    }

    async function initSellerOrders() {
        if (!requireLogin()) return;
        const orders = await api('/api/order/mine/seller');
        const list = document.getElementById('sellerOrderList');
        list.innerHTML = orders.length ? orders.map(order => orderCard(order,true)).join('') : empty('▤','还没有卖家订单','发布闲置后，买家的订单会显示在这里');
        list.querySelectorAll('[data-book-courier]').forEach(button => button.addEventListener('click', () => { document.getElementById('courierOrderId').value=button.dataset.bookCourier; document.getElementById('courierModal').classList.remove('hidden'); }));
        list.querySelectorAll('[data-confirm-handover]').forEach(button => button.addEventListener('click', async () => {
            const code = prompt('请输入买家向你出示的 6 位取件码');
            if (code === null) return;
            try { await api(`/api/order/${button.dataset.confirmHandover}/confirm-pickup-handover`, {method:'POST',body:JSON.stringify({code})}); await initSellerOrders(); toast('买家取件码已验证，等待买家完成双向确认'); } catch(error) { toast(error.message); }
        }));
        bindOrderMore(list, orders, true);
        list.querySelectorAll('[data-review]').forEach(button => button.addEventListener('click', () => openReviewModal(Number(button.dataset.review), initSellerOrders)));
        document.querySelectorAll('[data-close-modal]').forEach(button => button.onclick=()=>button.closest('.modal').classList.add('hidden'));
        enableOrderLiveSync(list, initSellerOrders);
    }

    function enableOrderLiveSync(container, reload) {
        if (container.dataset.liveSync === 'true') return;
        container.dataset.liveSync = 'true';
        let refreshing = false;
        const refresh = async () => {
            if (refreshing || document.hidden) return;
            refreshing = true;
            try { await reload(); } catch (_) { /* 下一轮继续同步，避免短暂网络错误打断页面 */ }
            finally { refreshing = false; }
        };
        window.setInterval(refresh, 15000);
        window.addEventListener('focus', refresh);
        document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
    }

    function bindRatings(container, orders, reload) {
        container.querySelectorAll('[data-rate-order]').forEach(button => button.addEventListener('click', () => {
            const order = orders.find(item => item.id === Number(button.dataset.rateOrder));
            if (order) showRatingDialog(order, reload);
        }));
    }

    function showRatingDialog(order, reload) {
        document.getElementById('ratingDialog')?.remove();
        const dialog = document.createElement('div');
        dialog.id = 'ratingDialog';
        dialog.className = 'sheet-backdrop rating-backdrop';
        dialog.innerHTML = `<section class="rating-dialog" role="dialog" aria-modal="true" aria-labelledby="ratingTitle"><div class="rating-dialog-head"><div><span class="eyebrow">Trust feedback</span><h2 id="ratingTitle">这次交易体验如何？</h2><p>${esc(order.items[0]?.productTitle || '校园交易')}</p></div><button class="icon-btn" type="button" data-close-rating>×</button></div><form id="ratingForm"><fieldset class="rating-stars"><legend>交易评分</legend>${[5,4,3,2,1].map(score=>`<label><input type="radio" name="ratingScore" value="${score}" ${score===5?'checked':''}><span>${score}</span><small>${{5:'非常满意',4:'满意',3:'一般',2:'待改善',1:'不满意'}[score]}</small></label>`).join('')}</fieldset><label class="rating-comment" for="ratingComment"><span>补充评价（可选）</span><textarea id="ratingComment" class="input" maxlength="300" placeholder="描述沟通、商品状态或交付体验"></textarea></label><button class="btn btn-primary btn-block" type="submit">提交交易评分</button></form></section>`;
        document.body.appendChild(dialog);
        const close = () => dialog.remove();
        dialog.addEventListener('click', event => { if (event.target === dialog || event.target.closest('[data-close-rating]')) close(); });
        dialog.querySelector('#ratingForm').addEventListener('submit', async event => {
            event.preventDefault();
            const button = event.currentTarget.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await api(`/api/trust/orders/${order.id}/rating`, {method:'POST',body:JSON.stringify({score:Number(new FormData(event.currentTarget).get('ratingScore')),comment:value('ratingComment')})});
                close();
                toast('评分已计入对方 Trust Score');
                await reload();
            } catch (error) { toast(error.message); button.disabled = false; }
        });
    }

    function bindOrderMore(container, orders, seller) {
        container.querySelectorAll('[data-order-more]').forEach(button => button.addEventListener('click', () => {
            const order = orders.find(item => item.id === Number(button.dataset.orderMore));
            if (order) showOrderMore(order, seller);
        }));
    }

    function showOrderMore(order, seller) {
        document.getElementById('orderActionSheet')?.remove();
        const item = order.items[0];
        const links = [
            `<a href="/order-detail.html?order=${order.id}"><span>▤</span><strong>查看订单详情</strong><small>地址、金额及订单进度</small></a>`,
            `<a href="/order-detail.html?order=${order.id}#amount"><span>¥</span><strong>查看钱款</strong><small>商品金额、运费及优惠</small></a>`,
            `<a href="/product-detail.html?id=${item.productId}"><span>▧</span><strong>商品快照</strong><small>${esc(item.productTitle)}</small></a>`
        ];
        if (order.courierCompany) {
            links.splice(1, 0, `<a href="/logistics.html?order=${order.id}"><span>⌁</span><strong>物流记录</strong><small>查看取件及运输轨迹</small></a>`);
        }
        if (seller) {
            links.push('<a href="/messages.html"><span>✉</span><strong>联系买家</strong><small>进入别人咨询我的商品</small></a>');
        } else {
            links.push(`<button type="button" data-contact-product="${item.productId}"><span>✉</span><strong>联系卖家</strong><small>继续咨询该商品</small></button>`);
        }
        const sheet = document.createElement('div');
        sheet.id = 'orderActionSheet';
        sheet.className = 'sheet-backdrop';
        sheet.innerHTML = `<section class="action-sheet" role="dialog" aria-modal="true" aria-label="订单更多操作"><div class="sheet-handle"></div><div class="sheet-title"><div><small>订单 ${esc(order.orderNo)}</small><h2>更多服务</h2></div><button class="icon-btn" data-close-sheet>×</button></div><div class="sheet-actions">${links.join('')}</div><button class="sheet-cancel" data-close-sheet>取消</button></section>`;
        document.body.appendChild(sheet);
        sheet.addEventListener('click', event => { if (event.target === sheet || event.target.closest('[data-close-sheet]')) sheet.remove(); });
        sheet.querySelector('[data-contact-product]')?.addEventListener('click', async buttonEvent => {
            const button = buttonEvent.currentTarget;
            button.disabled = true;
            try {
                const conversation = await api(`/api/chat/product/${button.dataset.contactProduct}`, {method:'POST'});
                location.href = `/messages.html?conversation=${conversation.id}`;
            } catch (error) { toast(error.message); button.disabled = false; }
        });
    }

    async function initOrderDetail() {
        if (!requireLogin()) return;
        const orderId = query('order');
        if (!orderId) { location.href = '/orders.html'; return; }
        const order = await api(`/api/order/${orderId}`);
        const [afterSale, pickupProposals, orderRisk] = await Promise.all([
            api(`/api/order/${orderId}/after-sale`).catch(() => null),
            order.shippingMethod !== 'DELIVERY' ? api(`/api/order/${orderId}/pickup-proposals`).catch(() => []) : Promise.resolve([]),
            api(`/api/risk/orders/${orderId}`).catch(() => ({alerts:[],riskLevel:'CLEAR'}))
        ]);
        const sellerView = state.user.id === order.sellerId;
        const pickupOrder = order.shippingMethod !== 'DELIVERY';
        const itemRows = order.items.map(item => `<a class="order-detail-item" href="/product-detail.html?id=${item.productId}"><div class="order-thumb">♻</div><div><strong>${esc(item.productTitle)}</strong><small>${money(item.unitPrice)} × ${item.quantity}</small></div><b>${money(item.subtotal)}</b></a>`).join('');
        const progressStatuses = pickupOrder ? ['PENDING_PAYMENT','PAID','SHIPPED','COMPLETED'] : ['PENDING_PAYMENT','PAID','COURIER_BOOKED','SHIPPED','COMPLETED'];
        // 售后与履约是两条并行状态；售后处理中仍展示申请前真实的配送进度。
        const timelineStatus = afterSale?.previousOrderStatus || order.status;
        const currentIndex = Math.max(0, progressStatuses.indexOf(timelineStatus));
        const orderMoments = progressStatuses.map((status,index) => {
            const labels = pickupOrder
                ? {PENDING_PAYMENT:['订单已创建','等待买家支付'],PAID:['面交已确认','按约定时间到达面交地点'],SHIPPED:['卖家已交付','买家取件码验证完成'],COMPLETED:['交易完成','双方取件码均已确认']}
                : {PENDING_PAYMENT:['订单已创建','等待买家支付'],PAID:['支付完成','等待卖家预约并交付快递'],COURIER_BOOKED:['快递已预约','等待快递员取件'],SHIPPED:['商品运输中','买家可查看实时物流'],COMPLETED:['交易完成','买家已确认收货']};
            const active = index <= currentIndex;
            return `<article class="order-moment ${active?'active':''} ${index===currentIndex?'current':''}"><span class="order-moment-marker">${index<currentIndex?'✓':String(index+1).padStart(2,'0')}</span><div><small>${index===currentIndex?'当前状态':active?'已完成':'待进行'}</small><h3>${labels[status][0]}</h3><p>${labels[status][1]}</p></div></article>`;
        }).join('');
        const payMethod = {WECHAT_PAY:'微信支付',ALIPAY:'支付宝',CAMPUS_CARD:'校园卡'}[order.paymentMethod] || '未选择';
        const actions = [];
        if (!sellerView && order.status === 'PENDING_PAYMENT') actions.push(`<a class="btn btn-primary" href="/payment.html?orders=${order.id}">立即付款</a><button id="detailCancel" class="btn btn-ghost">取消订单</button>`);
        if (!sellerView && order.status === 'SHIPPED' && !pickupOrder) actions.push(`<button id="detailReceive" class="btn btn-primary">确认收货</button>`);
        if (order.courierCompany) actions.push(`<a class="btn btn-outline" href="/logistics.html?order=${order.id}">查看物流</a>`);
        if (order.status === 'COMPLETED') {
            actions.push(order.myReviewSubmitted
                ? `<button class="btn btn-outline" disabled>已评价</button>`
                : `<button id="detailReview" class="btn btn-primary">评价${sellerView?'买家':'卖家'}</button>`);
        }
        actions.push(`<button id="detailMore" class="btn btn-outline">更多服务</button>`);
        const meetingStart = order.pickupMeetingStartAt ? new Date(order.pickupMeetingStartAt) : null;
        const meetingEnd = order.pickupMeetingEndAt ? new Date(order.pickupMeetingEndAt) : null;
        const today = new Date();
        const meetingDay = meetingStart && meetingStart.toDateString() === today.toDateString() ? 'Today' : meetingStart ? meetingStart.toLocaleDateString('zh-CN',{month:'long',day:'numeric',weekday:'short'}) : '时间待协商';
        const meetingTime = meetingStart && meetingEnd ? `${meetingStart.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false})}-${meetingEnd.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false})}` : '17:30-18:00';
        const pickupStateText = order.status === 'COMPLETED' ? '双方已完成当面交付' : sellerView ? (order.buyerReceiptConfirmed?'买家已确认':'等待买家确认') : (order.sellerHandoverConfirmed?'卖家已确认交付':'等待卖家验证');
        const pickupPanel = pickupOrder ? `<section class="meeting-card"><div class="meeting-copy"><span class="eyebrow">Meeting</span><strong>${meetingDay}</strong><b>${meetingTime}</b><p><span class="location-mark">⌖</span>${esc(order.pickupLocation || order.shippingAddress || 'Library North Gate')}</p></div><div class="pickup-pass"><small>${sellerView?'向买家出示你的交付码':'向卖家出示你的取件码'}</small><strong>${esc(order.myPickupCode || '------')}</strong><span>${pickupStateText}</span></div></section>` : '';
        const dateInputValue = date => date ? `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}T${String(date.getHours()).padStart(2,'0')}:${String(date.getMinutes()).padStart(2,'0')}` : '';
        const proposalStatusText = {PENDING:'等待对方回应',ACCEPTED:'双方已接受',REJECTED:'对方已拒绝',WITHDRAWN:'已被新提议替代'};
        const activeProposal = pickupProposals.find(proposal => proposal.status === 'PENDING');
        const proposalHistory = pickupProposals.slice(0,4).map(proposal => `<article class="meeting-proposal-item ${proposal.status.toLowerCase()}"><div><small>${dateTime(proposal.createdAt)} · ${proposal.proposerId===state.user.id?'由我发起':'由对方发起'}</small><strong>${dateTime(proposal.meetingStartAt)} 至 ${new Date(proposal.meetingEndAt).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false})}</strong><span>⌖ ${esc(proposal.location)}</span>${proposal.conflictWarning?`<p class="conflict-warning">${esc(proposal.conflictWarning)}</p>`:''}</div><b>${proposalStatusText[proposal.status]||proposal.status}</b>${proposal.status==='PENDING'&&proposal.responderId===state.user.id?`<div class="proposal-actions"><button class="btn btn-primary btn-small" data-proposal-response="${proposal.id}" data-decision="ACCEPT">接受修改</button><button class="btn btn-outline btn-small" data-proposal-response="${proposal.id}" data-decision="REJECT">拒绝</button></div>`:''}</article>`).join('');
        const meetingNegotiation = pickupOrder ? `<section class="card card-body workflow-card"><div class="section-title"><div><span class="eyebrow">Schedule together</span><h2>面交协商</h2></div><span>${activeProposal?'有待回应提议':'当前安排已同步'}</span></div>${['PENDING_PAYMENT','PAID'].includes(order.status)?`<form id="meetingProposalForm" class="meeting-proposal-form"><div class="field"><label for="proposalStart">新的开始时间</label><input id="proposalStart" class="input" type="datetime-local" value="${dateInputValue(meetingStart)}" required></div><div class="field"><label for="proposalEnd">新的结束时间</label><input id="proposalEnd" class="input" type="datetime-local" value="${dateInputValue(meetingEnd)}" required></div><div class="field span-2"><label for="proposalLocation">面交地点</label><input id="proposalLocation" class="input" value="${esc(order.pickupLocation||order.shippingAddress||'')}" maxlength="160" required></div><button class="btn btn-primary" type="submit">发送修改提议</button></form>`:'<p class="workflow-note">订单已进入交付或售后阶段，面交安排不可再修改。</p>'}<div class="meeting-proposal-list">${proposalHistory||'<p class="workflow-note">还没有协商记录，双方看到的是同一份当前安排。</p>'}</div></section>` : '';
        const afterSaleStatusText = {REQUESTED:'等待卖家审核',SELLER_APPROVED:'卖家已同意',SELLER_REJECTED:'卖家已拒绝',INTERVENTION_REQUESTED:'平台介入中',REFUND_APPROVED:'退款审核通过',REFUND_REJECTED:'退款审核未通过',REFUNDED:'退款已完成',CLOSED:'工单已关闭'};
        const afterSaleActions = afterSale ? `${sellerView&&afterSale.status==='REQUESTED'?`<div class="workflow-actions"><button class="btn btn-primary" data-seller-review="APPROVE">同意退货</button><button class="btn btn-outline" data-seller-review="REJECT">拒绝申请</button></div>`:''}${['REQUESTED','SELLER_APPROVED','SELLER_REJECTED'].includes(afterSale.status)?'<button id="requestIntervention" class="btn btn-outline">申请平台介入</button>':''}` : '';
        const refundDeadline = order.receivedAt ? new Date(new Date(order.receivedAt).getTime() + 7 * 86400000) : null;
        const refundExpired = refundDeadline && Date.now() > refundDeadline.getTime();
        const refundEligible = !sellerView && order.paymentStatus === 'PAID' && ['PAID','COURIER_BOOKED','SHIPPED','COMPLETED'].includes(order.status) && !refundExpired;
        const afterSalePanel = afterSale ? `<section class="card card-body workflow-card after-sale-card"><div class="section-title"><div><span class="eyebrow">After sale ticket</span><h2>售后工单</h2></div><span class="status warning">${afterSaleStatusText[afterSale.status]||afterSale.status}</span></div><div class="ticket-identity"><div><small>工单编号</small><strong>${esc(afterSale.workOrderNo)}</strong></div><div><small>退款金额</small><strong>${money(afterSale.refundAmount)}</strong></div></div><div class="ticket-reason"><strong>${esc(afterSale.reason)}</strong><p>${esc(afterSale.description||'买家未补充说明')}</p></div>${afterSale.sellerNote?`<p class="workflow-note"><b>卖家意见：</b>${esc(afterSale.sellerNote)}</p>`:''}${afterSale.interventionReason?`<p class="conflict-warning"><b>介入原因：</b>${esc(afterSale.interventionReason)}</p>`:''}${afterSale.adminNote?`<p class="workflow-note"><b>平台结论：</b>${esc(afterSale.adminNote)}</p>`:''}${afterSaleActions}</section>` : (refundEligible?`<section class="card card-body workflow-card after-sale-card"><div class="section-title"><div><span class="eyebrow">Buyer protection</span><h2>申请退款或退货</h2></div><span>${refundDeadline?`${refundDeadline.toLocaleDateString('zh-CN')} 前可申请`:'付款后即可申请'}</span></div><form id="returnRequestForm" class="return-request-form"><div class="field"><label for="returnReason">申请原因</label><select id="returnReason" class="input" required><option value="不再需要，申请退款">不再需要，申请退款</option><option value="商品与描述不符">商品与描述不符</option><option value="商品存在质量问题">商品存在质量问题</option><option value="面交验货争议">面交验货争议</option><option value="其他原因">其他原因</option></select></div><div class="field"><label for="returnDescription">情况说明</label><textarea id="returnDescription" class="input" maxlength="1000" placeholder="说明问题、沟通情况和期望处理方式"></textarea></div><button class="btn btn-primary" type="submit">提交售后申请</button></form></section>`:(!sellerView&&refundExpired?`<section class="card card-body workflow-card after-sale-card"><div class="section-title"><div><span class="eyebrow">Buyer protection</span><h2>售后申请已关闭</h2></div><span>确认收货后 7 天</span></div><p class="workflow-note">本单售后保障期已于 ${refundDeadline.toLocaleDateString('zh-CN')} 结束。如遇账号或安全问题，仍可联系平台管理员。</p></section>`:''));
        const pickupVerify = pickupOrder && ((sellerView && order.status === 'PAID') || (!sellerView && order.status === 'SHIPPED'))
            ? `<form id="pickupVerifyForm" class="pickup-verify"><label for="counterpartyCode">${sellerView?'输入买家取件码':'输入卖家交付码'}</label><div><input id="counterpartyCode" class="input" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" placeholder="6 位数字" required><button class="btn btn-primary">验证并确认</button></div><small>只有当面核对商品后再交换取件码。</small></form>` : '';
        const riskPanel = orderRisk.alerts?.length ? `<section class="card card-body workflow-card order-risk-card"><div class="section-title"><div><span class="eyebrow">Transaction check</span><h2>交易风险提示</h2></div><span class="status warning">${orderRisk.riskLevel}</span></div>${orderRisk.alerts.map(alert=>`<article class="order-risk-row severity-${String(alert.severity).toLowerCase()}"><i>${alert.severity==='HIGH'?'!':'i'}</i><div><strong>${esc(alert.title)}</strong><p>${esc(alert.message)}</p></div></article>`).join('')}</section>` : '';
        document.getElementById('orderDetailBack').href = sellerView ? '/seller-orders.html' : '/orders.html';
        document.getElementById('orderDetailContent').innerHTML = `<section class="card order-detail-hero"><div><span class="status ${order.status==='PENDING_PAYMENT'?'warning':order.status==='COMPLETED'?'done':''}">${statusText[order.status]}</span><h1>${sellerView?'卖家订单':'我的订单'}</h1><p>订单号 ${esc(order.orderNo)} · ${dateTime(order.createdAt)}</p></div><div class="order-hero-index"><small>ORDER STATUS</small><strong>${String(currentIndex+1).padStart(2,'0')}</strong><span>/ ${String(progressStatuses.length).padStart(2,'0')}</span></div></section><div class="order-detail-layout"><div><section class="card card-body order-story"><div class="section-title" style="margin-top:0"><div><span class="eyebrow">Order timeline</span><h2>交易时间线</h2></div><span>${statusText[order.status]}</span></div><div class="order-moments">${orderMoments}</div></section>${riskPanel}${pickupPanel}${meetingNegotiation}${afterSalePanel}<section class="card card-body" style="margin-top:18px"><div class="section-title" style="margin-top:0"><h2>商品信息</h2><span>商品快照</span></div>${itemRows}</section><section class="card card-body" style="margin-top:18px"><h2 style="margin-top:0">收货与配送</h2><dl class="detail-list"><div><dt>配送方式</dt><dd>${pickupOrder?'校内自提':'快递配送'}</dd></div><div><dt>收货人</dt><dd>${esc(order.recipientName || '当面交付')}</dd></div><div><dt>联系电话</dt><dd>${esc(order.recipientPhone || '与卖家协商')}</dd></div><div><dt>${pickupOrder?'面交地点':'收货地址'}</dt><dd>${esc(order.shippingAddress)}</dd></div>${order.trackingNumber?`<div><dt>快递运单</dt><dd>${esc(order.courierCompany)} · ${esc(order.trackingNumber)}</dd></div>`:''}</dl></section></div><aside id="amount" class="card card-body summary order-summary"><h2 style="margin-top:0">订单金额</h2><div class="summary-row"><span>商品金额</span><strong>${money(order.productAmount)}</strong></div><div class="summary-row"><span>配送费</span><strong>${money(order.shippingCost)}</strong></div><div class="summary-row"><span>优惠${order.couponCode?`（${esc(order.couponCode)}）`:''}</span><strong>-${money(order.discountAmount)}</strong></div>${Number(order.coinsUsed)>0?`<div class="summary-row"><span>金币抵扣（${order.coinsUsed}币）</span><strong>-${money(order.coinDiscountAmount)}</strong></div>`:''}<div class="summary-row summary-total"><span>实付金额</span><strong>${money(order.totalAmount)}</strong></div><div class="payment-fact"><small>支付方式</small><strong>${payMethod}</strong></div>${pickupVerify}<div class="detail-actions">${actions.join('')}</div></aside></div>`;
        document.getElementById('detailMore').addEventListener('click', () => showOrderMore(order, sellerView));
        document.getElementById('detailCancel')?.addEventListener('click', async event => {
            if (!confirm('确认取消这张未付款订单吗？商品会立即恢复可购买状态。')) return;
            event.currentTarget.disabled = true;
            try { await api(`/api/order/${order.id}/cancel`, {method:'POST'}); toast('订单已取消，商品已释放'); window.setTimeout(()=>location.reload(), 500); }
            catch (error) { toast(error.message); event.currentTarget.disabled = false; }
        });
        document.getElementById('detailReview')?.addEventListener('click', () => openReviewModal(order.id, () => location.reload()));
        document.getElementById('detailReceive')?.addEventListener('click', async event => {
            if (!confirm('确认已经收到并验收商品吗？')) return;
            event.currentTarget.disabled = true;
            try { await api(`/api/order/${order.id}/confirm-receipt`, {method:'POST'}); toast('已确认收货'); setTimeout(()=>location.reload(), 500); }
            catch (error) { toast(error.message); event.currentTarget.disabled = false; }
        });
        document.getElementById('pickupVerifyForm')?.addEventListener('submit', async event => {
            event.preventDefault();
            const button = event.currentTarget.querySelector('button');
            button.disabled = true;
            const path = sellerView ? 'confirm-pickup-handover' : 'confirm-receipt';
            try { await api(`/api/order/${order.id}/${path}`, {method:'POST',body:JSON.stringify({code:value('counterpartyCode')})}); toast('取件码验证成功'); window.setTimeout(()=>location.reload(), 500); }
            catch (error) { toast(error.message); button.disabled = false; }
        });
        document.getElementById('meetingProposalForm')?.addEventListener('submit', async event => {
            event.preventDefault();
            const button = event.currentTarget.querySelector('button');
            button.disabled = true;
            try {
                await api(`/api/order/${order.id}/pickup-proposals`, {method:'POST',body:JSON.stringify({meetingStartAt:value('proposalStart'),meetingEndAt:value('proposalEnd'),location:value('proposalLocation')})});
                toast('修改提议已发送给对方');
                window.setTimeout(()=>location.reload(), 500);
            } catch (error) { toast(error.message); button.disabled = false; }
        });
        document.querySelectorAll('[data-proposal-response]').forEach(button => button.addEventListener('click', async () => {
            button.disabled = true;
            try {
                await api(`/api/order/${order.id}/pickup-proposals/${button.dataset.proposalResponse}/respond`, {method:'POST',body:JSON.stringify({decision:button.dataset.decision})});
                toast(button.dataset.decision==='ACCEPT'?'新面交安排已生效':'已拒绝本次修改');
                window.setTimeout(()=>location.reload(), 500);
            } catch (error) { toast(error.message); button.disabled = false; }
        }));
        document.getElementById('returnRequestForm')?.addEventListener('submit', async event => {
            event.preventDefault();
            const button = event.currentTarget.querySelector('button');
            button.disabled = true;
            try {
                await api(`/api/order/${order.id}/return-request`, {method:'POST',body:JSON.stringify({reason:value('returnReason'),description:value('returnDescription')})});
                toast('退货申请已生成正式工单');
                window.setTimeout(()=>location.reload(), 500);
            } catch (error) { toast(error.message); button.disabled = false; }
        });
        document.querySelectorAll('[data-seller-review]').forEach(button => button.addEventListener('click', async () => {
            const note = prompt('填写审核意见');
            if (note === null) return;
            button.disabled = true;
            try {
                await api(`/api/order/${order.id}/after-sale/seller-review`, {method:'POST',body:JSON.stringify({decision:button.dataset.sellerReview,note})});
                toast(button.dataset.sellerReview==='APPROVE'?'已同意退货，等待平台审核退款':'已拒绝退货申请');
                window.setTimeout(()=>location.reload(), 500);
            } catch (error) { toast(error.message); button.disabled = false; }
        }));
        document.getElementById('requestIntervention')?.addEventListener('click', async event => {
            const reason = prompt('请说明需要平台介入的原因');
            if (!reason) return;
            event.currentTarget.disabled = true;
            try {
                await api(`/api/order/${order.id}/after-sale/intervention`, {method:'POST',body:JSON.stringify({reason})});
                toast('平台介入工单已进入管理员队列');
                window.setTimeout(()=>location.reload(), 500);
            } catch (error) { toast(error.message); event.currentTarget.disabled = false; }
        });
    }

    function initSellerForms() {
        document.getElementById('courierForm')?.addEventListener('submit', async event => { event.preventDefault(); try { const orderId=value('courierOrderId'); await api(`/api/order/${orderId}/courier-booking`,{method:'POST',body:JSON.stringify({courierCompany:value('courierCompany'),pickupTime:value('pickupTime')})}); location.href=`/logistics.html?order=${orderId}`; } catch(error){toast(error.message);} });
        document.getElementById('shipmentForm')?.addEventListener('submit', async event => { event.preventDefault(); try { await api(`/api/order/${value('shipmentOrderId')}/confirm-shipment`,{method:'POST',body:JSON.stringify({trackingNumber:value('trackingNumber')})}); document.getElementById('shipmentModal').classList.add('hidden'); await initSellerOrders(); toast('发货信息已同步给买家'); } catch(error){toast(error.message);} });
    }

    async function initLogistics() {
        if (!requireLogin()) return;
        const orderId = query('order');
        if (!orderId) { location.href = '/orders.html'; return; }
        const load = async () => {
            const logistics = await api(`/api/order/${orderId}/logistics`);
            renderLogistics(logistics, load);
        };
        await load();
    }

    function renderLogistics(logistics, reload) {
        const statusNames = {
            BOOKED:'等待上门取件', PICKED_UP:'快递员已取件', IN_TRANSIT:'包裹运输中',
            AT_DESTINATION:'已到达配送站', OUT_FOR_DELIVERY:'正在派送', DELIVERED:'已签收'
        };
        const stages = ['BOOKED','PICKED_UP','IN_TRANSIT','AT_DESTINATION','OUT_FOR_DELIVERY','DELIVERED'];
        const currentIndex = stages.indexOf(logistics.shipmentStatus);
        const progress = stages.map((status,index) => `<div class="shipment-step ${index <= currentIndex ? 'active' : ''}">${statusNames[status]}</div>`).join('');
        const events = logistics.events.map(event => `<div class="timeline-item"><span class="timeline-dot"></span><h4>${esc(event.title)}</h4><p>${esc(event.description)}</p><small>${esc(event.location || '')}　${dateTime(event.occurredAt)}</small></div>`).join('');
        const pickupCode = logistics.sellerView
            ? `<span>快递员上门时出示</span><strong>${esc(logistics.pickupCode)}</strong>`
            : '<span>卖家取件凭证</span><strong style="font-size:18px;letter-spacing:1px">隐私保护中</strong>';
        const confirmShipment = logistics.sellerView && logistics.shipmentStatus === 'BOOKED'
            ? `<form id="logisticsShipmentForm" class="card card-body" style="margin-top:18px"><h2 style="margin-top:0">确认快递员已取件</h2><p style="color:var(--muted)">快递员完成揽收并生成运单后，再填写运单号确认发货。</p><div style="display:grid;grid-template-columns:1fr auto;gap:10px"><input id="logisticsTrackingNumber" class="input" placeholder="填写快递运单号" required><button class="btn btn-primary">确认发货</button></div></form>`
            : '';
        const canRefresh = ['IN_TRANSIT','AT_DESTINATION'].includes(logistics.shipmentStatus);
        document.getElementById('logisticsBack').href = logistics.sellerView ? '/seller-orders.html' : '/orders.html';
        document.getElementById('logisticsContent').innerHTML = `<section class="card logistics-hero"><div class="card-body"><span class="eyebrow">${esc(logistics.courierCompany)}</span><h1>${statusNames[logistics.shipmentStatus] || '物流信息已建立'}</h1><p>${esc(logistics.productTitle)} · 订单 ${esc(logistics.orderNo)}</p><div class="shipment-progress">${progress}</div></div></section><section class="courier-grid"><div class="card card-body"><div class="courier-profile"><div class="courier-avatar">骑</div><div><small style="color:var(--muted)">本单快递员</small><h2 style="margin:4px 0">${esc(logistics.courierName)}</h2><div>${esc(logistics.courierPhone)}</div></div></div><div class="logistics-facts"><div class="fact"><small>预计上门取件</small><strong>${dateTime(logistics.pickupTime)}</strong></div><div class="fact"><small>预计送达</small><strong>${dateTime(logistics.estimatedDeliveryAt)}</strong></div></div></div><div class="pickup-code">${pickupCode}</div></section><section class="card card-body" style="margin-top:18px"><h2 style="margin-top:0">配送信息</h2><div class="logistics-facts"><div class="fact"><small>运单号</small><strong>${esc(logistics.trackingNumber || '取件后生成')}</strong></div><div class="fact"><small>配送地址</small><strong>${esc(logistics.shippingAddress)}</strong></div></div>${canRefresh ? '<div class="logistics-actions"><button id="refreshLogistics" class="btn btn-outline">同步最新物流</button></div>' : ''}</section>${confirmShipment}<section class="card card-body" style="margin-top:18px"><div class="section-title" style="margin-top:0"><div><h2>物流轨迹</h2><p>轨迹按最新时间排序</p></div></div><div class="timeline">${events || '<p style="color:var(--muted)">暂无物流轨迹</p>'}</div></section>`;
        document.getElementById('logisticsShipmentForm')?.addEventListener('submit', async event => {
            event.preventDefault();
            const input = document.getElementById('logisticsTrackingNumber');
            try {
                await api(`/api/order/${logistics.orderId}/confirm-shipment`, {method:'POST', body:JSON.stringify({trackingNumber:input.value.trim()})});
                toast('发货信息已同步给买家');
                await reload();
            } catch (error) { toast(error.message); }
        });
        document.getElementById('refreshLogistics')?.addEventListener('click', async event => {
            event.currentTarget.disabled = true;
            event.currentTarget.textContent = '同步中…';
            try { await api(`/api/order/${logistics.orderId}/logistics/refresh`, {method:'POST'}); await reload(); toast('已同步最新物流'); }
            catch (error) { toast(error.message); event.currentTarget.disabled = false; event.currentTarget.textContent = '同步最新物流'; }
        });
    }

    async function initMessages() {
        if (!requireLogin()) return;
        let box='BUYER';
        const load = async () => {
            state.conversations = await api(`/api/chat/conversations?box=${box}`);
            state.activeChatBox = box;
            renderConversationList();
            const requested=Number(query('conversation'));
            const initial = requested && state.conversations.some(item=>item.id===requested)
                ? requested : state.conversations[0]?.id;
            if (initial) await openConversation(initial);
        };
        document.querySelectorAll('[data-box]').forEach(tab=>tab.addEventListener('click',async()=>{document.querySelectorAll('[data-box]').forEach(item=>item.classList.remove('active'));tab.classList.add('active');box=tab.dataset.box;state.activeConversation=null;await load();}));
        document.getElementById('conversationSearch')?.addEventListener('input', renderConversationList);
        document.getElementById('chatForm').addEventListener('submit',async event=>{event.preventDefault();if(!state.activeConversation)return;const input=document.getElementById('messageInput');if(!input.value.trim())return;try{await api(`/api/chat/conversations/${state.activeConversation}/messages`,{method:'POST',body:JSON.stringify({content:input.value})});input.value='';await openConversation(state.activeConversation);}catch(error){toast(error.message);}});
        await load();
    }

    function renderConversationList() {
        const list = document.getElementById('conversationList');
        if (!list) return;
        const keyword = (document.getElementById('conversationSearch')?.value || '').trim().toLowerCase();
        const productStates = {AVAILABLE:'在售',RESERVED:'已拍下',SOLD:'已售出',OFF_SHELF:'已下架'};
        const filtered = (state.conversations || []).filter(item => !keyword
            || `${item.productTitle} ${item.otherName} ${item.lastMessage}`.toLowerCase().includes(keyword));
        list.innerHTML = filtered.length ? filtered.map(item => {
            const updated = item.updatedAt ? new Date(item.updatedAt).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}) : '';
            const active = Number(state.activeConversation) === Number(item.id) ? ' active' : '';
            return `<button class="conversation${active}" data-conversation="${item.id}"><img src="${esc(item.productImage||fallbackImage)}" onerror="this.src='${fallbackImage}'"><div class="conversation-copy"><h4>${esc(item.productTitle)}</h4><p>${esc(item.otherName)} · ${esc(formatPreview(item.lastMessage))}</p></div><span class="conversation-side"><time>${updated}</time>${item.unreadCount?`<b class="unread-dot">${item.unreadCount}</b>`:`<b class="conversation-state">${productStates[item.productStatus]||'商品'}</b>`}</span></button>`;
        }).join('') : empty('✉',keyword?'没有匹配会话':'暂无商品咨询',keyword?'换一个商品名或用户名试试':'在商品详情页可以联系卖家');
        list.querySelectorAll('[data-conversation]').forEach(button=>button.addEventListener('click',()=>openConversation(Number(button.dataset.conversation))));
    }

    async function openConversation(id) {
        state.activeConversation=id;
        const conversation=state.conversations.find(item=>item.id===id);
        if (!conversation) return;
        renderConversationList();
        const productStates = {AVAILABLE:'在售',RESERVED:'已被拍下',SOLD:'已售出',OFF_SHELF:'已下架'};
        const order = conversation.order;
        const orderState = order ? (statusText[order.status] || order.status) : productStates[conversation.productStatus] || '商品咨询';
        const initials = [...conversation.otherName].slice(0,2).join('');
        document.getElementById('chatHead').innerHTML=`<span class="chat-avatar">${esc(initials)}</span><div><strong>${esc(conversation.otherName)}</strong><small>${conversation.sellerView?'正在咨询你的商品':'商品卖家'} · ${esc(orderState)}</small></div>`;
        const compactActions = order?.canCancel
            ? `<button class="btn btn-ghost btn-small" data-chat-cancel-order="${order.id}">取消订单</button><a class="btn btn-primary btn-small" href="/payment.html?orders=${order.id}">去付款</a>`
            : order?.canAdjustPrice
                ? `<button class="btn btn-primary btn-small" data-chat-adjust-price="${order.id}">修改价格</button>`
                : order ? `<a class="btn btn-outline btn-small" href="/order-detail.html?order=${order.id}">订单详情</a>` : '';
        document.getElementById('chatProductBar').innerHTML=`<div class="chat-product-row"><a class="chat-product-link" href="/product-detail.html?id=${conversation.productId}"><img src="${esc(conversation.productImage||fallbackImage)}" onerror="this.src='${fallbackImage}'"><div><span class="chat-order-status">${esc(orderState)}</span><strong>${esc(conversation.productTitle)}</strong><small>${order?`含运费 ${money(order.shippingCost)}`:`${esc(productStates[conversation.productStatus]||'查看商品')} · ${esc(conversation.productLocation||'校内交易')}`}</small></div><b class="chat-product-price">${money(order?.productAmount ?? conversation.productPrice)}</b></a><div class="chat-product-toolbar">${compactActions}</div></div>`;
        const orderPanel = order ? `<section class="chat-context-card"><small>Current order</small><h3>${esc(orderState)}</h3><div class="chat-order-facts"><div><span>订单号</span><strong>${esc(order.orderNo)}</strong></div><div><span>商品合计</span><strong>${money(order.productAmount)}</strong></div><div><span>运费</span><strong>${money(order.shippingCost)}</strong></div><div><span>订单应付</span><strong>${money(order.totalAmount)}</strong></div></div>${order.priceAdjustedAt?`<p class="workflow-note">卖家已于 ${dateTime(order.priceAdjustedAt)} 调整价格</p>`:''}${order.canAdjustPrice?`<form id="chatPriceForm" class="chat-price-form"><div class="field"><label for="chatProductAmount">商品合计</label><input id="chatProductAmount" class="input" type="number" min="0" step="0.01" value="${Number(order.productAmount).toFixed(2)}" required></div><div class="field"><label for="chatShippingCost">运费</label><input id="chatShippingCost" class="input" type="number" min="0" step="0.01" value="${Number(order.shippingCost).toFixed(2)}" required></div><button class="btn btn-primary" type="submit">确认改价</button></form>`:''}<div class="chat-context-actions">${order.canCancel?`<button class="btn btn-danger" data-chat-cancel-order="${order.id}">取消未付款订单</button>`:''}${order.canRequestRefund?`<a class="btn btn-outline" href="/order-detail.html?order=${order.id}">申请退款</a>`:''}<a class="btn btn-ghost" href="/order-detail.html?order=${order.id}">查看订单详情</a></div></section>` : `<section class="chat-context-card"><small>Order status</small><h3>尚未下单</h3><p class="workflow-note">下单后，订单状态与交易操作会同步显示在这里。</p></section>`;
        document.getElementById('chatContext').innerHTML=`<section class="chat-context-card"><small>Linked product</small><h3>对应商品</h3><a class="chat-context-product" href="/product-detail.html?id=${conversation.productId}"><img src="${esc(conversation.productImage||fallbackImage)}" onerror="this.src='${fallbackImage}'"><div><strong>${esc(conversation.productTitle)}</strong><span>${money(conversation.productPrice)} · ${esc(productStates[conversation.productStatus]||'商品')}</span></div></a></section>${orderPanel}<p class="chat-safety">请在 StudentBay 内沟通和付款。任何要求脱离平台转账、缴纳保证金或扫码解冻的消息都存在风险。</p>`;
        const messages=await api(`/api/chat/conversations/${id}/messages`);
        const body=document.getElementById('chatBody');
body.innerHTML=messages.length?`<div class="chat-day-label">本次商品沟通</div>${messages.map(renderChatMessage).join('')}`:empty('✉','开始聊聊','先问问商品是否还在吧');
        body.scrollTop=body.scrollHeight;
        // T5.4 买家视角提示条：卖家开启自动回复时显示，不干扰输入
        const arTip = document.getElementById('chatAutoReplyTip');
        if (arTip) arTip.classList.toggle('hidden', !conversation.autoReplyAvailable);
        document.getElementById('messageInput').disabled=false;document.querySelector('#chatForm button').disabled=false;setBadge('unreadBadge',0);
        document.querySelectorAll('[data-chat-cancel-order]').forEach(button => button.addEventListener('click', async () => {
            if (!confirm('确认取消这张未付款订单吗？商品会立即恢复可购买状态。')) return;
            button.disabled = true;
            try { await api(`/api/order/${button.dataset.chatCancelOrder}/cancel`, {method:'POST'}); toast('订单已取消，商品已释放'); await refreshActiveConversation(id); }
            catch (error) { toast(error.message); button.disabled = false; }
        }));
        const adjustPrice = async (productAmount, shippingCost, button) => {
            button.disabled = true;
            try { await api(`/api/order/${order.id}/price-adjustment`, {method:'PATCH',body:JSON.stringify({productAmount,shippingCost})}); toast('价格已更新，买家可按新金额付款'); await refreshActiveConversation(id); }
            catch (error) { toast(error.message); button.disabled = false; }
        };
        document.getElementById('chatPriceForm')?.addEventListener('submit', event => {
            event.preventDefault();
            adjustPrice(value('chatProductAmount'), value('chatShippingCost'), event.currentTarget.querySelector('button'));
        });
        document.querySelector('[data-chat-adjust-price]')?.addEventListener('click', event => {
            const productAmount = prompt('输入新的商品合计', Number(order.productAmount).toFixed(2));
            if (productAmount === null) return;
            const shippingCost = prompt('输入新的运费', Number(order.shippingCost).toFixed(2));
            if (shippingCost === null) return;
            adjustPrice(productAmount, shippingCost, event.currentTarget);
        });
    }

    async function refreshActiveConversation(id) {
        state.conversations = await api(`/api/chat/conversations?box=${state.activeChatBox || 'BUYER'}`);
        renderConversationList();
        await openConversation(id);
    }

    async function initFavorites() {
        if (!requireLogin()) return;
        const products=await api('/api/favorites');
        document.getElementById('favoriteGrid').innerHTML=products.length?products.map(productCard).join(''):empty('♡','还没有收藏','遇到喜欢的商品可以先收藏起来');
    }

    async function initRewards() {
        if (!requireLogin()) return;
        const boardCoordinates = [
            [1,1],[1,2],[1,3],[1,4],[1,5],[1,6],[1,7],
            [2,7],[3,7],[4,7],[5,7],
            [5,6],[5,5],[5,4],[5,3],[5,2],[5,1],
            [4,1],[3,1],[2,1]
        ];
        let resetAt = null;

        const cellMeta = position => {
            if ([2,6,10,14,18].includes(position)) return {type:'coin', label:'币'};
            if ([4,12].includes(position)) return {type:'mystery', label:'?'};
            if ([8,16].includes(position)) return {type:'task', label:'★'};
            return {type:'campus', label:position === 0 ? '起' : position};
        };

        const renderDiceBoard = position => {
            const board = document.getElementById('diceBoard');
            board.querySelectorAll('.board-cell').forEach(cell => cell.remove());
            const cells = boardCoordinates.map(([row,column], index) => {
                const meta = cellMeta(index);
                return `<div class="board-cell ${meta.type} ${index===position?'current':''}" data-board-position="${index}" style="grid-row:${row};grid-column:${column}"><span>${meta.label}</span>${index===position?'<b class="board-player" aria-label="我的位置">SB</b>':''}</div>`;
            }).join('');
            board.insertAdjacentHTML('beforeend', cells);
        };

        const setDiceFace = face => {
            const tile = document.getElementById('gameTile');
            tile.className = tile.className.replace(/\bface-[1-6]\b/g, '').trim();
            tile.classList.add(`face-${face}`);
            tile.setAttribute('aria-label', `骰子点数 ${face}`);
        };

        const animatePlayer = async (from, to) => {
            const board = document.getElementById('diceBoard');
            let player = board.querySelector('.board-player');
            const steps = (to - from + boardCoordinates.length) % boardCoordinates.length;
            for (let offset = 1; offset <= steps; offset++) {
                const next = (from + offset) % boardCoordinates.length;
                const cell = board.querySelector(`[data-board-position="${next}"]`);
                if (!cell) continue;
                board.querySelectorAll('.board-cell.current').forEach(item => item.classList.remove('current'));
                cell.classList.add('current', 'player-arrival');
                if (!player) {
                    player = document.createElement('b');
                    player.className = 'board-player';
                    player.textContent = 'SB';
                }
                cell.appendChild(player);
                await new Promise(resolve => window.setTimeout(resolve, 115));
                cell.classList.remove('player-arrival');
            }
        };

        const revealTreasure = result => {
            const reveal = document.getElementById('treasureReveal');
            if (!reveal) return;
            document.getElementById('treasureKicker').textContent = result.rewardCoins > 0 ? 'Treasure unlocked' : 'Route discovered';
            document.getElementById('treasureResult').textContent = result.rewardCoins > 0
                ? `宝箱开启，获得 ${result.rewardCoins} 金币`
                : `${result.cellName}，继续向宝藏前进`;
            reveal.setAttribute('aria-hidden', 'false');
            reveal.classList.remove('is-visible');
            window.requestAnimationFrame(() => reveal.classList.add('is-visible'));
            window.clearTimeout(revealTreasure.timer);
            revealTreasure.timer = window.setTimeout(() => {
                reveal.classList.remove('is-visible');
                reveal.setAttribute('aria-hidden', 'true');
            }, 1850);
        };

        const taskCard = task => {
            const percent = Math.min(100, task.progress / task.target * 100);
            const button = task.claimed
                ? '<button class="btn btn-ghost btn-small" disabled>已领取</button>'
                : `<button class="btn ${task.claimable?'btn-primary':'btn-ghost'} btn-small" data-claim-task="${task.code}" ${task.claimable?'':'disabled'}>${task.claimable?'领取奖励':`${task.progress}/${task.target}`}</button>`;
            return `<article class="card task-card ${task.required?'required-task':''}"><div class="task-icon">${esc(task.icon)}</div><div class="task-content"><div><strong>${esc(task.name)}${task.required?'<em class="required-label">必做</em>':''}</strong><span>+${task.rewardCoins} 金币</span></div><p>${esc(task.description)}</p><div class="task-progress"><i style="width:${percent}%"></i></div></div>${button}</article>`;
        };

        const updateCountdown = () => {
            if (!resetAt) return;
            const distance = Math.max(0, resetAt.getTime() - Date.now());
            const hours = Math.floor(distance / 3600000);
            const minutes = Math.floor(distance % 3600000 / 60000);
            const seconds = Math.floor(distance % 60000 / 1000);
            const element = document.getElementById('dailyReset');
            if (element) element.textContent = `${String(hours).padStart(2,'0')}:${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')} 后更新`;
        };

        const load = async () => {
            const rewards = await api('/api/rewards/summary');
            state.rewards = rewards;
            resetAt = new Date(rewards.dailyResetAt);
            document.getElementById('rewardBalance').textContent = rewards.coinBalance;
            document.getElementById('rewardCash').textContent = money(rewards.coinBalance / rewards.coinsPerYuan);
            const coinNav = document.getElementById('coinNav');
            if (coinNav) coinNav.textContent = `${rewards.coinBalance}币`;
            document.getElementById('dailyRequiredText').textContent = `${rewards.dailyRequiredCompleted}/${rewards.dailyRequiredTotal}`;
            document.getElementById('dailyTaskList').innerHTML = rewards.tasks.filter(task => task.daily).map(taskCard).join('');
            document.getElementById('growthTaskList').innerHTML = rewards.tasks.filter(task => !task.daily).map(taskCard).join('');
            document.querySelectorAll('[data-claim-task]').forEach(button => button.addEventListener('click', async () => {
                button.disabled = true;
                try {
                    const result = await api(`/api/rewards/tasks/${button.dataset.claimTask}/claim`, {method:'POST'});
                    toast(`+${result.rewardCoins} 金币，奖励已到账`);
                    await load();
                } catch (error) { toast(error.message); button.disabled = false; }
            }));
            const gameButton = document.getElementById('playGame');
            gameButton.disabled = rewards.game.remainingRolls <= 0;
            gameButton.textContent = rewards.game.remainingRolls > 0 ? '掷骰子' : '今日机会已用完';
            document.getElementById('remainingRolls').textContent = rewards.game.remainingRolls;
            document.getElementById('gameCoins').textContent = rewards.game.coinsEarned;
            document.getElementById('gameStatus').textContent = rewards.game.remainingRolls > 0
                ? `当前位置第 ${rewards.game.position + 1} 格，完成必做任务可解锁大奖格`
                : '今日骰子已用完，零点后恢复 3 次机会';
            renderDiceBoard(rewards.game.position);
            document.getElementById('coinLedger').innerHTML = rewards.ledger.length ? rewards.ledger.map(entry => `<div class="ledger-row"><div><strong>${esc(entry.description)}</strong><small>${dateTime(entry.createdAt)}</small></div><b class="${entry.amount>0?'coin-plus':'coin-minus'}">${entry.amount>0?'+':''}${entry.amount}</b></div>`).join('') : '<p class="ledger-empty">完成任务后，这里会显示金币记录。</p>';
            updateCountdown();
        };
        document.getElementById('playGame').addEventListener('click', async event => {
            const button = event.currentTarget;
            const tile = document.getElementById('gameTile');
            button.disabled = true;
            tile.classList.add('rolling');
            let spinCount = 0;
            const spin = window.setInterval(() => setDiceFace(spinCount++ % 6 + 1), 105);
            try {
                const result = await api('/api/rewards/game/play', {method:'POST'});
                await new Promise(resolve => window.setTimeout(resolve, 900));
                window.clearInterval(spin);
                tile.classList.remove('rolling');
                setDiceFace(result.dice);
                tile.classList.add('landed');
                await animatePlayer(result.fromPosition, result.position);
                revealTreasure(result);
                await load();
                setDiceFace(result.dice);
                window.setTimeout(() => tile.classList.remove('landed'), 500);
                document.getElementById('gameStatus').textContent = `${result.message}${result.rewardCoins>0?`，+${result.rewardCoins} 金币`:''}`;
                toast(`骰出 ${result.dice} 点 · ${result.cellName}${result.rewardCoins>0?` +${result.rewardCoins}币`:''}`);
            } catch (error) {
                window.clearInterval(spin);
                tile.classList.remove('rolling');
                toast(error.message);
                button.disabled = false;
            }
        });
        window.setInterval(updateCountdown, 1000);
        await load();
    }

    async function initAdmin() {
        if (!requireLogin()) return;
        if (state.user.role!=='ADMIN'){location.href='/';return;}
        const show=tab=>{
            document.querySelectorAll('[data-admin-tab]').forEach(button=>button.classList.toggle('active',button.dataset.adminTab===tab));
            ({dashboard:adminDashboard,users:adminUsers,products:adminProducts,reports:adminReports,afterSales:adminAfterSales,map:adminMap}[tab])().catch(error=>toast(error.message));
        };
        document.querySelectorAll('[data-admin-tab]').forEach(button=>button.addEventListener('click',()=>show(button.dataset.adminTab)));
        show('dashboard');
    }

    async function adminDashboard(from = '', to = '') {
        const params = new URLSearchParams();
        if (from) params.set('from', from);
        if (to) params.set('to', to);
        const data = await api(`/api/admin/dashboard${params.size ? `?${params}` : ''}`);
        const trend = data.dailyTrend || [];
        const categories = data.categoryAnalysis || [];
        const maxOrders = Math.max(1, ...trend.map(point => Number(point.orders)));
        const maxCategoryAmount = Math.max(1, ...categories.map(item => Number(item.amount)));
        const statuses = data.orderStatuses || {};
        const completed = Number(statuses.COMPLETED || 0);
        const active = Number(statuses.PAID || 0) + Number(statuses.COURIER_BOOKED || 0) + Number(statuses.SHIPPED || 0);
        const waiting = Number(statuses.PENDING_PAYMENT || 0);
        const cancelled = Number(statuses.CANCELLED || 0);
        const total = Math.max(1, Number(data.totalOrders || 0));
        const completedDeg = completed / total * 360;
        const activeDeg = completedDeg + active / total * 360;
        const waitingDeg = activeDeg + waiting / total * 360;
        const attention = Number(data.pendingReports || 0) + Number(data.pendingAfterSales || 0);
        document.getElementById('adminContent').innerHTML = `
            <div class="dashboard-head"><div><span class="eyebrow">Live overview</span><h2>校园交易脉搏</h2><p>按日期观察订单、成交分类与治理队列。</p></div><div class="dashboard-date">${esc(data.range.from)} 至 ${esc(data.range.to)}</div></div>
            <form id="dashboardRangeForm" class="dashboard-range"><div class="field"><label for="dashboardFrom">开始日期</label><input id="dashboardFrom" class="input" type="date" value="${esc(data.range.from)}" required></div><div class="field"><label for="dashboardTo">结束日期</label><input id="dashboardTo" class="input" type="date" value="${esc(data.range.to)}" required></div><button class="btn btn-primary" type="submit">应用范围</button><button class="btn btn-outline" type="button" data-range-days="30">最近 30 天</button></form>
            <div class="metric-grid"><article class="metric-cell feature"><small>范围成交额</small><strong>${money(data.grossMerchandiseValue)}</strong><span>${data.totalOrders} 张订单</span></article><article class="metric-cell"><small>活跃用户</small><strong>${data.activeUsers}</strong><span>${data.totalUsers} 位注册用户</span></article><article class="metric-cell ${attention?'needs-attention':''}"><small>待处理事项</small><strong>${attention}</strong><span>${attention?'需要运营响应':'当前队列已清空'}</span></article><article class="metric-cell"><small>学生认证</small><strong>${data.verifiedUsers}</strong><span>${data.disabledUsers} 位用户已停用</span></article></div>
            <div class="dashboard-grid">
                <section class="dashboard-panel trend-panel"><div class="panel-heading"><div><small>选择范围</small><h3>订单趋势</h3></div><span>每日新订单</span></div><div class="trend-chart" style="--trend-count:${trend.length}" aria-label="订单数量柱状图">${trend.map((point,index)=>`<div class="trend-column"><div class="trend-value">${point.orders}</div><i style="--bar-scale:${Math.max(.06,Number(point.orders)/maxOrders)};--bar-delay:${index*35}ms"></i><span>${esc(point.label)}</span></div>`).join('')}</div></section>
                <section class="dashboard-panel status-panel"><div class="panel-heading"><div><small>选择范围</small><h3>订单状态</h3></div></div><div class="status-visual"><div class="status-donut" style="--completed:${completedDeg}deg;--active:${activeDeg}deg;--waiting:${waitingDeg}deg"><div><strong>${data.totalOrders}</strong><span>总订单</span></div></div><div class="status-legend"><span><i class="legend-completed"></i>已完成 <b>${completed}</b></span><span><i class="legend-active"></i>进行中 <b>${active}</b></span><span><i class="legend-waiting"></i>待付款 <b>${waiting}</b></span><span><i class="legend-cancelled"></i>已取消 <b>${cancelled}</b></span></div></div></section>
                <section class="dashboard-panel category-panel"><div class="panel-heading"><div><small>成交结构</small><h3>分类成交分析</h3></div><span>${categories.length} 个活跃分类</span></div><div class="category-analysis">${categories.length?categories.map((item,index)=>`<article><div><strong>${esc(item.category)}</strong><small>${item.orders} 单 · ${item.quantity} 件</small></div><span><i style="--category-scale:${Number(item.amount)/maxCategoryAmount};--bar-delay:${index*60}ms"></i></span><b>${money(item.amount)}</b></article>`).join(''):'<p class="workflow-note">该日期范围内还没有已支付成交。</p>'}</div></section>
                <section class="dashboard-panel operations-panel"><div class="panel-heading"><div><small>运营队列</small><h3>需要处理</h3></div><button class="text-button" data-open-after-sales>进入售后工单</button></div><div class="operation-list"><article><span>01</span><div><strong>投诉与举报</strong><small>待审核内容及商品举报</small></div><b>${data.pendingReports}</b></article><article><span>02</span><div><strong>售后待处理</strong><small>退货审核与退款执行</small></div><b>${data.pendingAfterSales}</b></article><article><span>03</span><div><strong>平台介入</strong><small>买卖双方争议处理</small></div><b>${data.interventionOrders}</b></article></div></section>
                <section class="dashboard-panel user-panel"><div class="panel-heading"><div><small>账户健康度</small><h3>用户状态</h3></div><span>${data.totalUsers?Math.round(Number(data.activeUsers)/Number(data.totalUsers)*100):0}% 活跃</span></div><div class="user-state-track"><i style="--user-scale:${data.totalUsers?Number(data.activeUsers)/Number(data.totalUsers):0}"></i></div><div class="user-state-facts"><div><strong>${data.activeUsers}</strong><span>正常使用</span></div><div><strong>${data.disabledUsers}</strong><span>已停用</span></div><div><strong>${data.verifiedUsers}</strong><span>学生认证</span></div></div></section>
            </div>`;
        document.getElementById('dashboardRangeForm').addEventListener('submit', event => {
            event.preventDefault();
            adminDashboard(value('dashboardFrom'), value('dashboardTo')).catch(error => toast(error.message));
        });
        document.querySelector('[data-range-days]').addEventListener('click', () => {
            const toDate = new Date();
            const fromDate = new Date();
            fromDate.setDate(toDate.getDate() - 29);
            const iso = date => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
            adminDashboard(iso(fromDate), iso(toDate)).catch(error => toast(error.message));
        });
        document.querySelector('[data-open-after-sales]').addEventListener('click', () => document.querySelector('[data-admin-tab="afterSales"]').click());
        setupRevealMotion();
    }

    async function adminUsers(){const users=await api('/api/admin/users');document.getElementById('adminContent').innerHTML=`<h2>用户管理</h2><div class="table-wrap"><table class="table"><thead><tr><th>用户</th><th>学校</th><th>认证</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody>${users.map(user=>`<tr><td><strong>${esc(user.displayName)}</strong><br><small>${esc(user.email)}</small></td><td>${esc(user.university)}</td><td>${user.studentVerified?'已认证':'-'}</td><td>${user.role}</td><td>${user.status}</td><td>${user.role==='ADMIN'?'-':`<button class="btn btn-small ${user.status==='ACTIVE'?'btn-danger':'btn-outline'}" data-user-status="${user.id}" data-next="${user.status==='ACTIVE'?'DISABLED':'ACTIVE'}">${user.status==='ACTIVE'?'停用':'启用'}</button>`}</td></tr>`).join('')}</tbody></table></div>`;document.querySelectorAll('[data-user-status]').forEach(button=>button.addEventListener('click',async()=>{await api(`/api/admin/users/${button.dataset.userStatus}/status`,{method:'PATCH',body:JSON.stringify({status:button.dataset.next})});adminUsers();}));}
    async function adminProducts(){const products=await api('/api/admin/products');document.getElementById('adminContent').innerHTML=`<h2>商品管理</h2><div class="table-wrap"><table class="table"><thead><tr><th>商品</th><th>分类</th><th>价格</th><th>位置</th><th>状态</th><th>操作</th></tr></thead><tbody>${products.map(product=>`<tr><td><strong>${esc(product.title)}</strong><br><small>ID ${product.id}</small></td><td>${esc(product.category)}</td><td>${money(product.price)}</td><td>${esc(product.locationName)}</td><td>${product.status}</td><td><button class="btn btn-small ${product.status==='OFF_SHELF'?'btn-outline':'btn-danger'}" data-product-status="${product.id}" data-next="${product.status==='OFF_SHELF'?'AVAILABLE':'OFF_SHELF'}">${product.status==='OFF_SHELF'?'恢复':'下架'}</button></td></tr>`).join('')}</tbody></table></div>`;document.querySelectorAll('[data-product-status]').forEach(button=>button.addEventListener('click',async()=>{await api(`/api/admin/products/${button.dataset.productStatus}/status`,{method:'PATCH',body:JSON.stringify({status:button.dataset.next})});adminProducts();}));}
    async function adminReports(){const reports=await api('/api/admin/reports');document.getElementById('adminContent').innerHTML=`<h2>举报处理</h2><div class="table-wrap"><table class="table"><thead><tr><th>商品</th><th>原因</th><th>说明</th><th>状态</th><th>处理</th></tr></thead><tbody>${reports.map(report=>`<tr><td>#${report.productId}</td><td>${esc(report.reason)}</td><td>${esc(report.detail||'-')}</td><td>${report.status}</td><td>${report.status==='PENDING'?`<button class="btn btn-small btn-danger" data-report="${report.id}" data-takedown="true">下架并通过</button> <button class="btn btn-small btn-outline" data-report="${report.id}" data-takedown="false">驳回</button>`:esc(report.adminNote||'已处理')}</td></tr>`).join('')}</tbody></table></div>`;document.querySelectorAll('[data-report]').forEach(button=>button.addEventListener('click',async()=>{const approved=button.dataset.takedown==='true';const note=prompt('填写处理备注',approved?'内容违规，商品已下架':'证据不足，举报未通过');if(note===null)return;await api(`/api/admin/reports/${button.dataset.report}/process`,{method:'POST',body:JSON.stringify({status:approved?'RESOLVED':'REJECTED',adminNote:note,takeDownProduct:approved})});adminReports();}));}
    async function adminAfterSales() {
        const tickets = await api('/api/admin/after-sales');
        const labels = {REQUESTED:'等待卖家审核',SELLER_APPROVED:'待平台审核',SELLER_REJECTED:'卖家已拒绝',INTERVENTION_REQUESTED:'平台介入',REFUND_APPROVED:'待执行退款',REFUND_REJECTED:'退款未通过',REFUNDED:'退款完成',CLOSED:'工单关闭'};
        document.getElementById('adminContent').innerHTML = `<div class="section-title"><div><span class="eyebrow">Service desk</span><h2>售后与介入工单</h2></div><span>${tickets.length} 张工单</span></div><div class="admin-ticket-list">${tickets.length?tickets.map(ticket=>`<article class="admin-ticket"><div class="admin-ticket-index">${String(ticket.id).padStart(2,'0')}</div><div><small>${esc(ticket.workOrderNo)} · 订单 ${ticket.orderId}</small><h3>${esc(ticket.reason)}</h3><p>${esc(ticket.description||'买家未补充说明')}</p>${ticket.interventionReason?`<p class="conflict-warning">介入原因：${esc(ticket.interventionReason)}</p>`:''}</div><div class="admin-ticket-state"><span class="status warning">${labels[ticket.status]||ticket.status}</span><strong>${money(ticket.refundAmount)}</strong><div class="workflow-actions">${['SELLER_APPROVED','INTERVENTION_REQUESTED'].includes(ticket.status)?`<button class="btn btn-primary btn-small" data-after-sale-review="${ticket.id}" data-decision="APPROVE">通过退款</button><button class="btn btn-outline btn-small" data-after-sale-review="${ticket.id}" data-decision="REJECT">驳回</button>`:''}${ticket.status==='REFUND_APPROVED'?`<button class="btn btn-primary btn-small" data-after-sale-refund="${ticket.id}">执行退款</button>`:''}</div></div></article>`).join(''):'<div class="empty"><div class="empty-icon">✓</div><h3>售后队列已清空</h3><p>当前没有需要平台处理的工单。</p></div>'}</div>`;
        document.querySelectorAll('[data-after-sale-review]').forEach(button => button.addEventListener('click', async () => {
            const note = prompt('填写平台审核结论');
            if (note === null) return;
            button.disabled = true;
            try {
                await api(`/api/admin/after-sales/${button.dataset.afterSaleReview}/review`, {method:'POST',body:JSON.stringify({decision:button.dataset.decision,note})});
                toast(button.dataset.decision==='APPROVE'?'退款审核已通过':'退款申请已驳回');
                await adminAfterSales();
            } catch (error) { toast(error.message); button.disabled = false; }
        }));
        document.querySelectorAll('[data-after-sale-refund]').forEach(button => button.addEventListener('click', async () => {
            if (!confirm('确认执行退款并恢复商品可售状态吗？')) return;
            button.disabled = true;
            try { await api(`/api/admin/after-sales/${button.dataset.afterSaleRefund}/refund`, {method:'POST'}); toast('退款已完成'); await adminAfterSales(); }
            catch (error) { toast(error.message); button.disabled = false; }
        }));
    }
    async function adminMap(){const config=await api('/api/admin/map-config');document.getElementById('adminContent').innerHTML=`<h2>地图配置</h2><form id="mapConfigForm" class="form-grid"><div class="field"><label>中心纬度</label><input id="mapLat" class="input" value="${esc(config.centerLat)}" required></div><div class="field"><label>中心经度</label><input id="mapLng" class="input" value="${esc(config.centerLng)}" required></div><div class="field"><label>默认缩放级别</label><input id="mapZoom" class="input" type="number" min="1" max="19" value="${esc(config.zoom)}" required></div><div class="field span-2"><label>地图瓦片地址</label><input id="mapTile" class="input" value="${esc(config.tileUrl)}" required></div><div class="field span-2"><label>版权标注</label><input id="mapAttribution" class="input" value="${esc(config.attribution)}" required></div><button class="btn btn-primary">保存地图配置</button></form>`;document.getElementById('mapConfigForm').addEventListener('submit',async event=>{event.preventDefault();await api('/api/admin/map-config',{method:'PUT',body:JSON.stringify({centerLat:value('mapLat'),centerLng:value('mapLng'),zoom:value('mapZoom'),tileUrl:value('mapTile'),attribution:value('mapAttribution')})});toast('地图配置已保存');});}

    async function boot() {
        try {
            setupPageMotion();
            setupStickerTrail();
            if (page !== 'login') await renderHeader();
            const handlers = {home:initHome,login:initLogin,detail:initDetail,'price-compare':initPriceCompare,publish:initPublish,cart:initCart,checkout:initCheckout,payment:initPayment,'payment-success':initPaymentSuccess,'buyer-orders':initBuyerOrders,'seller-orders':async()=>{initSellerForms();await initSellerOrders();},'order-detail':initOrderDetail,logistics:initLogistics,messages:initMessages,favorites:initFavorites,rewards:initRewards,admin:initAdmin,profile:initProfile,settings:initSettings,bought:initBought,'my-products':initMyProducts,'my-space':initMySpace,browse:initBrowse};
            if (handlers[page]) await handlers[page]();
            setupRevealMotion();
        } catch (error) { toast(error.message); }
    }

    return { boot };
})();

document.addEventListener('DOMContentLoaded', StudentBay.boot);
