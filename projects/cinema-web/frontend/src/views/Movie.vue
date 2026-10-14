<template>
  <div class="movie-page">
    <!-- 搜索模式：显示合并搜索结果 -->
    <template v-if="q">
      <h2 class="section-title">搜索“{{ q }}”结果（{{ filteredAll.length }}）</h2>
      <div class="card-container">
        <div
          v-for="(item, idx) in filteredAll"
          :key="idx"
          class="card"
          @click="goToDetail(item.source, item.movie_id)"
        >
          <van-image lazy-load :src="item.image" class="card-img" />
          <div class="card-footer">
            <div class="card-title">{{ item.title }}</div>
            <div
              v-if="item.score !== undefined"
              class="card-score"
              :class="getScoreClass(item.score)"
            >
              {{ item.score }}
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Fullpage 三分区模式 -->
    <template v-else>
      <full-page ref="fullpage" :options="option" class="fp-wrapper">
        <!-- 正在热映 -->
        <div class="section">
          <h2 class="section-title">正在热映</h2>
          <div class="card-container">
            <div
              v-for="item in HotData"
              :key="item.movie_id"
              class="card"
              @click="toHotDetail(item.movie_id)"
            >
              <van-image lazy-load :src="item.image" class="card-img" />
              <div class="card-footer">
                <div class="card-title">{{ item.title }}</div>
                <div
                  class="card-score"
                  :class="getScoreClass(item.score)"
                >
                  {{ item.score }}
                </div>
              </div>
            </div>
          </div>
          <div class="pagination">
            <button @click="HotprevPage" :disabled="HotcurrentPage===1">
              上一页
            </button>
            <button @click="HotnextPage" :disabled="HotcurrentPage===HotpageCount">
              下一页
            </button>
          </div>
        </div>

        <!-- 最受期待 -->
        <div class="section">
          <h2 class="section-title">最受期待</h2>
          <div class="card-container">
            <div
              v-for="item in HopeData"
              :key="item.movie_id"
              class="card"
              @click="toHopeDetail(item.movie_id)"
            >
              <van-image lazy-load :src="item.image" class="card-img" />
              <div class="card-footer">
                <div class="card-title">{{ item.title }}</div>
                <div
                  v-if="item.score !== undefined"
                  class="card-score"
                  :class="getScoreClass(item.score)"
                >
                  {{ item.score }}
                </div>
                <div v-else class="card-time">{{ item.time }}</div>
              </div>
            </div>
          </div>
          <div class="pagination">
            <button @click="HopeprevPage" :disabled="HopecurrentPage===1">
              上一页
            </button>
            <button @click="HopenextPage" :disabled="HopecurrentPage===HopepageCount">
              下一页
            </button>
          </div>
        </div>

        <!-- 经典影片 -->
        <div class="section">
          <h2 class="section-title">经典影片</h2>
          <div class="card-container">
            <div
              v-for="item in TopData"
              :key="item.movie_id"
              class="card"
              @click="toTopDetail(item.movie_id)"
            >
              <van-image lazy-load :src="item.image" class="card-img" />
              <div class="card-footer">
                <div class="card-title">{{ item.title }}</div>
                <div
                  class="card-score"
                  :class="getScoreClass(item.score)"
                >
                  {{ item.score }}
                </div>
              </div>
            </div>
          </div>
          <div class="pagination">
            <button @click="TopprevPage" :disabled="TopcurrentPage===1">
              上一页
            </button>
            <button @click="TopnextPage" :disabled="TopcurrentPage===ToppageCount">
              下一页
            </button>
          </div>
        </div>
      </full-page>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePagination } from '../page.js'

// 路由与搜索关键字
const route  = useRoute()
const router = useRouter()
const q = computed(() => route.query.q || '')

// 原始缓存数据
const hotRaw     = JSON.parse(localStorage.getItem('HotMovielistData'))   || []
const hopeRaw    = JSON.parse(localStorage.getItem('HopeMovielistData'))  || []
const classicRaw = JSON.parse(localStorage.getItem('TopMovielistData'))   || []

// 给搜索数据添加来源标记
const allRaw = [
  ...hotRaw.map(m => ({ ...m, source: 'hot' })),
  ...hopeRaw.map(m => ({ ...m, source: 'hope' })),
  ...classicRaw.map(m => ({ ...m, source: 'top' }))
]

// 搜索过滤
const filteredAll = computed(() => {
  if (!q.value) return []
  const key = q.value.toLowerCase()
  return allRaw.filter(m => m.title.toLowerCase().includes(key))
})

// 点击跳转
function goToDetail(source, id) {
  const map = { hot: '/hotfilm', hope: '/hopefilm', top: '/topfilm' }
  router.push({ path: map[source] || '/moviedetail', query: { movieId: id } })
}

// 分区专属跳转
const toHotDetail  = id => goToDetail('hot', id)
const toHopeDetail = id => goToDetail('hope', id)
const toTopDetail  = id => goToDetail('top', id)

// fullpage.js 配置，固定导航栏下移 64px
const option = {
  scrollingSpeed: 500,
  licenseKey: 'OPEN-SOURCE-GPLV3-LICENSE',
  navigation: true,
  navigationPosition: 'left',
  navigationTooltips: ['热映','期待','经典'],
  showActiveTooltip: true,
  fixedElements: '.navbar',
  paddingTop: '64px'
}

// 分页逻辑
const {
  currentPage: HotcurrentPage,
  paginatedData: HotData,
  pageCount: HotpageCount,
  nextPage: HotnextPage,
  prevPage: HotprevPage
} = usePagination(hotRaw, 10)

const {
  currentPage: HopecurrentPage,
  paginatedData: HopeData,
  pageCount: HopepageCount,
  nextPage: HopenextPage,
  prevPage: HopeprevPage
} = usePagination(hopeRaw, 10)

const {
  currentPage: TopcurrentPage,
  paginatedData: TopData,
  pageCount: ToppageCount,
  nextPage: TopnextPage,
  prevPage: TopprevPage
} = usePagination(classicRaw, 10)

// 评分等级函数
function getScoreClass(score) {
  if (score >= 9.3) return 'high-score'
  if (score >= 9) return 'mid-score'
  return 'low-score'
}
</script>

<style scoped>
/* 整体背景与留白 */
.movie-page {
  background: linear-gradient(180deg, #121212, #1e1f23);
  color: #e8eaed;
  min-height: 100vh;
}

/* 分区标题 */
.section-title {
  margin: 24px 0 12px 150px;
  font-size: 24px; font-weight: 600; color: #ffb400;
  text-transform: uppercase;
}

/* 卡片容器 */
.card-container {
  display: flex; flex-wrap: wrap;
  padding-left: 150px; border-left: 4px solid #17191b;
}

/* 单卡片样式 */
.card {
  width: 160px; margin: 8px 16px; cursor: pointer;
  transition: transform .3s, box-shadow .3s;
}
.card:hover {
  transform: translateY(-4px) scale(1.03);
  box-shadow: 0 8px 16px rgba(0,0,0,0.6);
}
.card-img {
  width: 100%; height: 187px; border-radius: 10px; object-fit: cover;
}
.card-footer { margin-top: 8px; text-align: center; }
.card-title {
  font-size: 14px; color: #d0d0d0;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* 评分颜色等级 */
.card-score {
  font-size: 16px; margin-top: 4px;
}
.high-score { color: #20c997; }
.mid-score  { color: #ffc107; }
.low-score  { color: #dc3545; }

/* 如果是显示时间 */
.card-time {
  font-size: 14px; color: #c29090; font-style: italic;
}

/* 艺术化分页按钮 */
.pagination {
  display: flex; justify-content: center; margin: 24px 0 40px;
}
.pagination button {
  background: linear-gradient(135deg, #ffb400, #ff5f6d);
  color: #fff; border: none;
  padding: 8px 18px; margin: 0 10px;
  border-radius: 20px; font-weight: 600;
  box-shadow: 0 4px 12px rgba(255,95,109,0.4);
  transition: transform .3s, box-shadow .3s, background .3s;
}
.pagination button:disabled {
  opacity: .4; cursor: not-allowed; background: #555; box-shadow: none;
}
.pagination button:not(:disabled):hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 6px 16px rgba(255,95,109,0.6);
  background: linear-gradient(135deg, #ff5f6d, #ffb400);
}

/* 隐藏 fullpage 水印 */
.fp-warning, .fp-watermark { display: none !important; }
</style>