<template>
  <div class="BigBox">
    <div class="content-Box">
      <!-- 左侧：排行榜 + 猜你喜欢 -->
      <div class="left-box" @wheel.stop>
        <!-- Douban 排行榜 -->
        <div class="top-ten-list">
          <h2 class="list-title">Movie ranking list</h2>
          <ol class="list-items">
            <li v-for="item in Leftlist" :key="item.movie_id">
              <div class="list-items-title" @click="toTopDetail(item.movie_id)">
                {{ item.title }}
                <span :class="['score', getScoreClass(item.score)]">{{ item.score }}</span>
              </div>
            </li>
          </ol>
        </div>

        <!-- 猜你喜欢 -->
        <div class="guess-box">
          <h3>猜你喜欢</h3>
          <ul>
            <li
              v-for="item in GuessList"
              :key="item.movie_id"
              @click="toGuessDetail(item.movie_id)"
            >
              <img :src="item.image" alt="thumb" class="guess-thumb" />
              <span class="guess-title">{{ item.title }}</span>
              <span :class="['score', getScoreClass(item.score)]">{{ item.score }}</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 右侧区域 -->
      <div class="right-box">
        <full-page ref="fullpage" :options="option">
          <!-- Section 2: 正在热映 -->
          <section class="section hotBox">
            <div class="hotBox-topbox">
              <div class="left">正在热映</div>
              <div class="right" @click="toMovie()">全部</div>
            </div>
            <div class="img-bigBox">
              <div
                class="img-box"
                v-for="item in Hotlist"
                :key="item.movie_id"
              >
                <div class="top" @click="toHotDetail(item.movie_id)">
                  <van-image lazy-load :src="item.image" style="width:150px" />
                  <div class="overMovie">
                    <div class="text">{{ item.title }}</div>
                    <div class="score" :class="getScoreClass(item.score)">{{ item.score }}</div>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <!-- Section 3: 最受期待 -->
          <section class="section hopeBox">
            <div class="hotBox-topbox">
              <div class="left">最受期待</div>
              <div class="right" @click="toMovie()">全部</div>
            </div>
            <div class="img-bigBox">
              <div
                class="img-box"
                v-for="item in Hopelist"
                :key="item.movie_id"
              >
                <div class="top" @click="toHopeDetail(item.movie_id)">
                  <van-image lazy-load :src="item.image" style="width:150px" />
                  <div class="overMovie">
                    <div class="text">{{ item.title }}</div>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <!-- Section 4: 经典 Top100 -->
          <section class="section classicBox">
            <div class="hotBox-topbox">
              <div class="left">经典 Top100</div>
              <div class="right" @click="toMovie()">全部</div>
            </div>
            <div class="img-bigBox">
              <div
                class="img-box"
                v-for="item in Toplist"
                :key="item.movie_id"
              >
                <div class="top" @click="toTopDetail(item.movie_id)">
                  <van-image lazy-load :src="item.image" style="width:150px" />
                  <div class="overMovie">
                    <div class="text">{{ item.title }}</div>
                    <div class="score" :class="getScoreClass(item.score)">{{ item.score }}</div>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </full-page>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, toRefs, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  getTopMovielist,
  getHotMovielist,
  getHopeMovielist
} from '../api/request.js'

const Router = useRouter()
const Data = reactive({
  Movielist: [],
  Leftlist: [],
  Toplist: [],
  Hotlist: [],
  Hopelist: [],
  GuessList: [],
  CarouselList: []
})
const option = reactive({
  scrollingSpeed: 500,
  licenseKey: 'OPEN-SOURCE-GPLV3-LICENSE',
  navigation: true
})

const toMovie      = () => Router.push({ path: '/movie' })
const toTopDetail  = id => Router.push({ path: '/topfilm',  query: { movieId: id } })
const toHotDetail  = id => Router.push({ path: '/hotfilm',  query: { movieId: id } })
const toHopeDetail = id => Router.push({ path: '/hopefilm', query: { movieId: id } })
const toGuessDetail = id => Router.push({ path: '/moviedetail', query: { movieId: id } })

function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function getScoreClass(score) {
  if (score >= 9.4) return 'high-score'
  if (score >= 9) return 'mid-score'
  return 'low-score'
}

onMounted(async () => {
  let topCache = JSON.parse(localStorage.getItem('TopMovielistData'))
  if (!topCache) {
    const res = await getTopMovielist()
    topCache = res.data.MovieList
    localStorage.setItem('TopMovielistData', JSON.stringify(topCache))
  }
  Data.Movielist = topCache
  Data.Leftlist  = topCache.slice(0,10)
  Data.Toplist   = topCache.slice(11,19)

  let hotCache = JSON.parse(localStorage.getItem('HotMovielistData'))
  if (!hotCache) {
    const res = await getHotMovielist()
    hotCache = res.data.MovieList
    localStorage.setItem('HotMovielistData', JSON.stringify(hotCache))
  }
  Data.Hotlist = hotCache.slice(0,8)

  let hopeCache = JSON.parse(localStorage.getItem('HopeMovielistData'))
  if (!hopeCache) {
    const res = await getHopeMovielist()
    hopeCache = res.data.MovieList
    localStorage.setItem('HopeMovielistData', JSON.stringify(hopeCache))
  }
  Data.Hopelist = hopeCache.slice(0,8)

  let guessCache = JSON.parse(localStorage.getItem('GuessListData'))
  if (!guessCache || guessCache.length === 0) {
    guessCache = shuffle(Data.Hotlist).slice(0, 6)
    localStorage.setItem('GuessListData', JSON.stringify(guessCache))
  }
  Data.GuessList = guessCache
})

const { Leftlist, Toplist, Hotlist, Hopelist, GuessList, CarouselList } = toRefs(Data)
</script>

<style scoped lang="scss">

.BigBox {
  position: relative; 
  background: url("@/assets/b3.png") no-repeat center center;
  background-size: cover;
  margin-top: 50px;
  height: calc(100vh - 50px);
  font-size: 10px;
  font-family: $font-base;
  color: $color-text-light;

  &::before {
    content: "";
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0, 0, 0, 0.37); // 半透明遮罩
    z-index: 0;
  }

  .content-Box {
    display: flex;
    height: 100%;
    position: relative; // 确保子元素在遮罩上层
    z-index: 1;
  }
}

.left-box {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  margin-top: 50px;
  margin-left: $padding-md;
  flex-direction: column;
  overflow-y: 100%;
  height: auto;
  padding-right: $padding-sm;

  .top-ten-list,
  .guess-box {
    background: $color-card-bg;
    border-radius: $border-radius;
    box-shadow: $box-shadow-card;
    padding: $padding-md;
    margin-bottom: 80px;
  }

  .top-ten-list {
    flex: 1;
    overflow-y: auto;
    .list-title {
      font-size: 18px;
      font-weight: bold;
      color: rgba(83, 161, 239, 0.57);
      margin-bottom: 5px;
    }

    .list-items {
      list-style: decimal inside;
      padding: 0;
      margin: 0;
      font-size: 14px;
      li {
        padding: 4px 0;
        border-bottom: 1px solid $color-hover;
        .list-items-title {
          display: flex;
          justify-content: space-between;
          cursor: pointer;
          color: rgb(207, 136, 22);
          transition: color 0.2s;
          &:hover {
            color: $color-accent;
          }
          .score {
            margin-left: 5px;
          }
        }
      }
    }
  }

  .guess-box {
    margin-bottom: 100px;
    h3 {
      font-size: 18px;
      color:rgba(83, 161, 239, 0.57);
      margin-bottom: $padding-sm;
    }
    ul {
      list-style: none;
      padding: 0;
      margin: 0;
      li {
        font-size: 14px;
        color: $color-text-light;
        margin-bottom: 8px;
        display: flex;
        align-items: center;
        cursor: pointer;
        transition: color 0.2s;
        &:hover {
          color: $color-primary;
        }

        .guess-thumb {
          width: 40px;
          height: 56px;
          object-fit: cover;
          margin-right: 8px;
          border-radius: 4px;
        }

        .guess-title {
          flex: 1;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .score {
          margin-left: 5px;
        }
      }
    }
  }
}

.right-box {
  flex: 1;
  overflow: hidden;

  .section {
    padding: $padding-md;
    box-sizing: border-box;
 
  }

  .hotBox-topbox {
    display: flex;
    justify-content: space-between;
    font-size: 18px;
    font-weight: bold;
    color: rgba(83, 161, 239, 0.57);
    margin-bottom: $padding-sm;

    .right {
      cursor: pointer;
      &:hover {
        color: $color-primary;
      }
    }
  }

  .img-bigBox {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: $padding-sm;

    .img-box {
      background: $color-card-bg;
      border-radius: $border-radius;
      box-shadow: $box-shadow-card;
      overflow: hidden;
      transition: transform 0.3s ease;

      .top {
        cursor: pointer;
        position: relative;
        transition: transform 0.3s ease;

        &:hover {
          transform: scale(1.03);
        }

        .van-image {
          width: 100%;
          height: auto;
        }

        .overMovie {
          position: absolute;
          bottom: 0;
          width: 100%;
          background: rgba(0, 0, 0, 0.6);
          color: white;
          padding: 4px;
          display: flex;
          justify-content: space-between;
          align-items: center;

          .text {
            font-size: 14px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .score {
            font-size: 15px;
          }
        }
      }
    }
  }
}

// 评分颜色
.high-score {
  color: #20c997;
}
.mid-score {
  color: #ffc107;
}
.low-score {
  color: #dc3545;
}
</style>