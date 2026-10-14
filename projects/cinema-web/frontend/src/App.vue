<template>
  <div id="app">
    <div class="navbar">
      <div class="logo"
           @mouseover="logoHover = true"
           @mouseleave="logoHover = false">
        <span :class="{ 'logo-hover': logoHover }">KamronZone</span>
      </div>
      <div class="nav-right">
        <el-input
          v-model="searchQuery"
          placeholder="🔍 搜索电影..."
          clearable
          class="nav-search"
          @clear="onSearch"
          @keyup.enter.native="onSearch"
        />
        <el-menu
          :default-active="activeIndex"
          mode="horizontal"
          @select="handleSelect"
          class="nav-menu"
        >
          <el-menu-item index="1"><router-link to="/">首页</router-link></el-menu-item>
          <el-menu-item index="2"><router-link to="/movie">电影</router-link></el-menu-item>
          <el-menu-item index="3"><router-link to="/cinema">影院</router-link></el-menu-item>
          <el-menu-item index="4"><router-link to="/my">我的</router-link></el-menu-item>
        </el-menu>
      </div>
    </div>
    <router-view/>
    <footer class="app-footer">
      <p>© 2025 KamronZhao. All rights reserved.</p >
    </footer>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeIndex = ref('1')         
const searchQuery = ref(route.query.q || '')
const logoHover = ref(false)

// 根据路由更新菜单高亮
watch(() => route.path, p => {
  activeIndex.value =
    p === '/'        ? '1' :
    p === '/movie'   ? '2' :
    p === '/cinema'  ? '3' :
    p === '/my'      ? '4' : ''
  // 保持搜索框与 URL 同步
  searchQuery.value = route.query.q || ''
})

// 菜单选中
const handleSelect = idx => activeIndex.value = idx

// 搜索：一律跳转到 /movie?q=...
function onSearch() {
  const q = searchQuery.value.trim()
  router.push({ path: '/movie', query: q ? { q } : {} })
}
</script>

<style scoped lang="scss">
.navbar {
  position: fixed; top: 0; left: 0; right: 0;
  height: 64px; display: flex; align-items: center;
  justify-content: space-between; padding: 0 24px;
  background: linear-gradient(90deg, #1e1f23, #2a2b30);
  box-shadow: 0 3px 10px rgba(0,0,0,0.5); z-index: 1000;
}
.logo {
  span {
    font-family: 'Pacifico', cursive;
    font-size: 28px; 
	color:rgb(187, 27, 27); 
	cursor: pointer;
    transition: transform .3s, text-shadow .3s;
  }
  .logo-hover span {
    animation: pulse 1.5s ease-in-out infinite;
  }
}
.nav-right { display: flex; align-items: center; }
.nav-search {
  width: 180px; margin-right: 24px;
  .el-input__inner { border-radius: 20px; background: rgba(255,255,255,0.1); color: #e8eaed; padding-left:36px; }
  .el-input__icon  { color: #ffb400; }
}
.nav-menu {
  background: transparent !important; 
  border-bottom: none !important; 
  line-height: 64px;
  .el-menu-item {
    margin: 0 12px; 
	position: relative;
    a { 
		color: #e8eaed; 
		font-family: 'Montserrat', sans-serif; 
		font-weight: 500; 
		text-decoration: none !important; 
		transition:color .2s; 
	}
    &:hover a       { color: #ffb400; }
    &.is-active a   { color: #ffb400; }
    &.is-active::after {
		display: none !important;
      content: '';
	  position:absolute; bottom:12px; 
	  left:50%; 
	  transform:translateX(-50%);
      width:24px; 
	  height:2px; 
	  background:#ffb400; 
	  border-radius:1px;
    }
  }
}

.app-footer {
  background-color: #1e1f23;
  color: #aaa;
  text-align: center;
  padding: 16px;
  font-size: 10px;
  border-top: 1px solid #333;
  margin-top: 25px;

  p {
    margin: 0;
  }
}
@keyframes pulse {
  0%,100% { transform: scale(1); text-shadow: 0 0 5px rgba(255,180,0,0.7); }
  50%     { transform: scale(1.1); text-shadow: 0 0 12px rgba(255,180,0,1); }
}
</style>