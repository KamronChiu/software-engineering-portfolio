<template>
  <div class="HotFilm-Bigbox" v-if="film">
    <div class="leftBox">
      <van-image lazy-load :src="film.big_image || film.image" />
    </div>
    <div class="rightBox">
      <div class="middle-BOX">
        <div class="title">{{ film.title }}</div>
        <div class="actor"><span>主演：{{ film.actor || '暂无信息' }}</span></div>
        <div class="tiime"><span>上映时间：{{ film.time || '待定' }}</span></div>
        <div class="detail"><span>剧情简介：{{ film.detail || '暂无简介' }}</span></div>
      </div>
    </div>
  </div>
  <div v-else class="empty">
    <p>未找到对应的电影信息，请返回首页</p >
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { ref } from 'vue'

const route = useRoute()
const movieId = route.query.movieId
const film = ref(null)

const allSources = [
  ...(JSON.parse(localStorage.getItem('TopMovielistData')) || []),
  ...(JSON.parse(localStorage.getItem('HotMovielistData')) || []),
  ...(JSON.parse(localStorage.getItem('HopeMovielistData')) || []),
  ...(JSON.parse(localStorage.getItem('GuessListData')) || [])
]

film.value = allSources.find(item => item.movie_id == movieId)
</script>

<style scoped>
body {
  background-color: #24282f;
}
.HotFilm-Bigbox {
  height: 100vh;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
}
.leftBox {
  padding-right: 10px;
}
.leftBox .van-image {
  width: 192px;
  height: 264px;
}
.rightBox {
  width: 70%;
  margin-left: 20px;
}
.middle-BOX {
  color: #d0d0d0;
}
.middle-BOX div {
  padding: 10px 18px;
  font-size: 14px;
}
.middle-BOX .title {
  font-size: 26px;
  font-weight: 600;
}
.detail span {
  line-height: 26px;
  display: -webkit-box;
  -webkit-line-clamp: 12;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}
.empty {
  color: #fff;
  padding: 40px;
  text-align: center;
}
</style>