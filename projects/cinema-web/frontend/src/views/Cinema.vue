<template>
  <!-- 影院页面 -->
  <div class="cinema-page">
    <!-- 如果传入 movieId，则显示电影信息卡 -->
    <div class="movie-info-card" v-if="Route.query.movieId">
      <div class="poster">
        <van-image lazy-load :src="currentFilmData.big_image" />
      </div>
      <div class="info">
        <h2 class="title">{{ currentFilmData.title }}</h2>
        <p class="meta"><strong>主演：</strong>{{ currentFilmData.actor }}</p >
        <p class="meta"><strong>上映：</strong>{{ currentFilmData.time }}</p >
        <van-button class="detail-btn" @click="toMovieDetail(currentFilmData.movie_id)">
          更多详情
        </van-button>
      </div>
    </div>

    <!-- 影院列表卡片 -->
    <section class="cinema-list-BOX">
      <h3 class="list-title">影院列表</h3>
      <div class="cinema-card" v-for="(item,index) in cinemaList" :key="index">
        <div class="card-left">
          <div class="cinema-name">{{ item.cinema_name }}</div>
          <div class="cinema-address">
            {{ item.specified_address }}
            <span class="distance">{{ item.distance || '' }}</span>
          </div>
          <div class="tag-section">
            <!-- 可根据业务动态渲染 -->
            <span class="tag red">特惠·19.9元起</span>
            <span class="tag pink">券包·3.3折</span>
            <span class="tag blue">改签</span>
            <span class="tag blue">退票</span>
            <span class="tag gray">3D/IMAX</span>
            <span class="tag gray">可停车</span>
          </div>
        </div>
        <div class="card-right">
          <div class="price">¥{{ item.price || '48' }} 起</div>
          <van-button class="buy-btn"
            @click="toSelectTime(item.cinema_id, Route.query.movieId)"
          >
            选座购票
          </van-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'

const Route = useRoute()
const Router = useRouter()

// 本地取 film 列表 & cinema 列表
const hotFilm    = JSON.parse(localStorage.getItem('HotMovielistData')) || []
const cinemaList = JSON.parse(localStorage.getItem('cinemaListData'))   || []

// 找到当前电影数据
const currentFilmData =
  hotFilm.find(f => String(f.movie_id) === String(Route.query.movieId)) || {}

// 跳转详情
const toMovieDetail = movieId => {
  Router.push({ path: '/hotfilm', query: { movieId } })
}
// 跳转选座
const toSelectTime = (cinemaId, movieId) => {
  Router.push({ path: '/cinemas', query: { cinemaId, movieId } })
}
</script>

<style scoped lang="scss">
.cinema-page {
  background: #f5f5f5;
  min-height: 100vh;
  padding: 80px 16px 24px; // 顶部留白给导航
  font-family: 'Montserrat', sans-serif;
  color: #333;

  /* 电影信息卡 */
  .movie-info-card {
    display: flex;
    background: #fff;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 2px 12px rgba(0,0,0,0.06);
    margin-bottom: 24px;

    .poster {
      flex-shrink: 0;
      .van-image {
        width: 160px;
        height: 220px;
        object-fit: cover;
      }
    }
    .info {
      padding: 16px 24px;
      .title {
        font-size: 20px;
        margin-bottom: 8px;
        color: #ffb400;
      }
      .meta {
        font-size: 14px;
        margin: 4px 0;
        color: #666;
      }
      .detail-btn {
        margin-top: 12px;
        background: linear-gradient(135deg, #ba573c, #d02135);
        color: #fff;
        border-radius: 20px;
        height: 32px;
      }
    }
  }

  /* 影院列表 */
  .cinema-list-BOX {
    .list-title {
      font-size: 18px;
      font-weight: 600;
      margin-bottom: 12px;
      color: #222;
    }

    .cinema-card {
      display: flex;
      justify-content: space-between;
      background: #fff;
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 12px;
      box-shadow: 0 1px 8px rgba(0,0,0,0.04);
      transition: box-shadow .3s, transform .3s;
      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 16px rgba(0,0,0,0.1);
      }

      .card-left {
        flex: 1;
        .cinema-name {
          font-size: 16px;
          font-weight: bold;
          margin-bottom: 6px;
        }
        .cinema-address {
          font-size: 12px;
          color: #777;
          margin-bottom: 8px;
          .distance {
            float: right;
            color: #999;
          }
        }
        .tag-section {
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
          .tag {
            font-size: 10px;
            padding: 2px 6px;
            border-radius: 4px;
            &.red   { background: #fff0f0; color: #e64646; }
            &.pink  { background: #fdf2f7; color: #e14d7a; }
            &.blue  { background: #eaf6ff; color: #3797f0; }
            &.gray  { background: #f4f4f4; color: #666; }
          }
        }
      }

      .card-right {
        text-align: right;
        .price {
          font-size: 18px;
          color: #f03d37;
          font-weight: bold;
          margin-bottom: 8px;
        }
        .buy-btn {
          background: linear-gradient(135deg, #ba573c, #d02135);
          color: #fff;
          border-radius: 20px;
          height: 32px;
        }
      }
    }
  }
}
</style>