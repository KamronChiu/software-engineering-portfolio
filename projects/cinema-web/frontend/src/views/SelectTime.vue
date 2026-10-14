<template>
	<!-- 选择观影时间页面 -->
	<div class="Select-BigBox">
		<div class="Select-BigBox-topBox">
			<div class="left-box">
				<van-image lazy-load :src="require('../assets/cinema.png')" />
			</div>
			<div class="right-box">
				<div class="top-cinema-name">{{currentCinemaData.cinema_name}}</div>
				<div class="cinema-address">地址：{{currentCinemaData.specified_address}}</div>
				<div class="phone">电话：{{currentCinemaData.cinema_phone}}</div>
				<div class="cinema-tags">
					<span>影院服务</span>
					<div class="cinema-tags-item">
						<span class="tag">退</span>
						<p class="desc">未取票用户放映前30分钟可退票</p>
					</div>
					<div class="cinema-tags-item">
						<span class="tag">改签</span>
						<p class="desc">未取票用户放映前30分钟可改签</p>
					</div>
					<div class="cinema-tags-item">
						<span class="tag">儿童优惠</span>
						<p class="desc">1.3米以下儿童仅限普通厅免票无座，一位成人限带一名儿童</p>
					</div>
				</div>
			</div>
		</div>


		<div class="Select-BigBox-content-Box">
			<swiper :initialSlide="sliderIndex" @swiper="setSwiperRef" @slideChange="onSlideChange"
				:effect="'coverflow'" :navigation="true" :grabCursor="true" :centeredSlides="true"
				:slidesPerView="'auto'" :coverflowEffect="{
			      rotate: 50,
			      stretch: 0,
			      depth: 100,
			      modifier: 1,
			      slideShadows: true,
			    }" :modules="modules" class="mySwiper">
				<swiper-slide v-for="(item,index) in hotFilm" :key="index" @click="setActiveSlide(index)">
					<van-image :src="item.big_image" />
				</swiper-slide>

			</swiper>

			<div class="show-list">
				<div class="movie-info">
					<div class="movie-info-top">
						<span id="top-title">{{state.currentMovieData.title}}</span>
						<span id="top-score">{{state.currentMovieData.score}}</span>
					</div>
					<div class="movie-info-bottom">
						<div id="LEFT">
							<span class="key">上映时间：</span>
							<span class="value">{{state.currentMovieData.time}}</span>
						</div>
						<div id="RIGHT">
							<span class="key">主演：</span>
							<span class="value">{{state.currentMovieData.actor}}</span>
						</div>

					</div>
				</div>
				<div class="show-date">
					<span class="show-data-left">观影时间：</span>
					<span class="data-item" v-for="(item,index) in state.uniqueDateList" :key="index"
						:class="{active: state.dataIndex == index}" @click="changeActive(index)">{{item}}</span>
					<!-- 					<span class="data-item" :class="{active: state.dataIndex == '1'}"
						@click="changeActive('1')">明天1月2</span> -->
				</div>
				<div class="plist-container">
					<table class="plist" v-if="state.currentScheduleBox.length>0">
						<thead>
							<tr>
								<th>放映时间</th>
								<th>语言版本</th>
								<th>放映厅</th>
								<th>售价（元）</th>
								<th>选座购票</th>
							</tr>
						</thead>
						<tbody>
							<tr v-for="(item,index) in state.currentScheduleBox" :key="index">
								<td>
									<span class="begin-time">{{item.beginTime}}</span>
									<br>
									<span class="end-time">{{item.endTime}}散场</span>
								</td>
								<td><span class="lang">{{item.language}}</span></td>
								<td><span class="hall">{{item.hall_name}}</span></td>
								<td><span class="sell-price">{{item.price}}</span></td>
								<td>
									<van-button color="linear-gradient(to right, rgb(186 87 60), rgb(208 33 53))"
										class="buy-btn normal"
										@click="toSeat(item.schedule_id,Route.query.cinemaId,state.currentMovieData.movie_id)"
										style="color: #eaeaea;">选座购票
									</van-button>
								</td>
							</tr>
						</tbody>
					</table>

					<div class="finished-all-shows" v-if="state.currentScheduleBox.length === 0">
						<span class="finish-img"></span>
						<br>
						<span class="finish-text">暂未放映</span>
					</div>

				</div>
			</div>

		</div>

	</div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { register } from 'swiper/element/bundle'
import { Swiper, SwiperSlide } from 'swiper/vue'
import 'swiper/css'
import 'swiper/css/effect-coverflow'
import 'swiper/css/navigation'
import { EffectCoverflow, Navigation } from 'swiper/modules'
import { getSchedulist } from '../api/request.js'

register()
const modules = [EffectCoverflow, Navigation]

const Route = useRoute()
const Router = useRouter()

// 轮播起始索引
const sliderIndex = Route.query.movieId ? Number(Route.query.movieId) - 1 : 0

// 影院列表与当前影院信息
const cinemaList = JSON.parse(localStorage.getItem('cinemaListData')) || []
const currentCinemaData =
  cinemaList.find(item => item.cinema_id == Route.query.cinemaId) || {}

// 电影列表 (swiper用)
const hotFilm = JSON.parse(localStorage.getItem('HotMovielistData')) || []

// 拉取或读取排期列表，保底成数组
let BigscheduleList = JSON.parse(localStorage.getItem('schedulelistData')) || []
// 按当前影院过滤
let scheduleList = BigscheduleList.filter(item => item.cinema_id == Route.query.cinemaId)

const state = reactive({
  movieId: Route.query.movieId,
  currentMovieData: hotFilm[sliderIndex] || {},
  dataIndex: 0,
  currentMovie_schedule: scheduleList.filter(
    item => item.movie_id == hotFilm[sliderIndex]?.movie_id
  ),
  uniqueDateList: [
    ...new Set(
      scheduleList
        .filter(item => item.movie_id == hotFilm[sliderIndex]?.movie_id)
        .map(d => d.show_date)
    )
  ],
  currentScheduleBox: scheduleList
    .filter(item => item.movie_id == hotFilm[sliderIndex]?.movie_id)
    .filter(
      item =>
        item.show_date ===
        [...new Set(
          scheduleList
            .filter(item => item.movie_id == hotFilm[sliderIndex]?.movie_id)
            .map(d => d.show_date)
        )][0]
    )
})

// 页面挂载后，实时拉取最新排期并缓存，避免 null.filter
onMounted(async () => {
  try {
    const res = await getSchedulist()
    const list = res.data.schedulelist || []
    // 更新 localStorage
    localStorage.setItem('schedulelistData', JSON.stringify(list))
    BigscheduleList = list
    // 重新过滤
    scheduleList = BigscheduleList.filter(
      item => item.cinema_id == Route.query.cinemaId
    )
    // 更新 reactive state
    const filmId = hotFilm[sliderIndex]?.movie_id
    state.currentMovie_schedule = scheduleList.filter(item => item.movie_id == filmId)
    state.uniqueDateList = [
      ...new Set(state.currentMovie_schedule.map(d => d.show_date))
    ]
    // 放到当天
    state.dataIndex = 0
    state.currentScheduleBox = state.currentMovie_schedule.filter(
      item => item.show_date === state.uniqueDateList[0]
    )
  } catch (e) {
    console.error('拉取排期失败', e)
  }
})

// Swiper 实例引用
let swiperRef = null
const setSwiperRef = swiper => {
  swiperRef = swiper
}
const setActiveSlide = idx => swiperRef.slideTo(idx, 0)

// 幻灯片切换回调
const onSlideChange = swiper => {
  const idx = swiper.activeIndex
  state.currentMovieData = hotFilm[idx]
  const filmId = state.currentMovieData.movie_id
  state.currentMovie_schedule = scheduleList.filter(item => item.movie_id == filmId)
  state.uniqueDateList = [...new Set(state.currentMovie_schedule.map(d => d.show_date))]
  // 切回第一天
  state.dataIndex = 0
  state.currentScheduleBox = state.currentMovie_schedule.filter(
    item => item.show_date === state.uniqueDateList[0]
  )
}

// 切换排期日期
const changeActive = idx => {
  state.dataIndex = idx
  const date = state.uniqueDateList[idx]
  state.currentScheduleBox = state.currentMovie_schedule.filter(
    item => item.show_date == date
  )
}

// 选座跳转
const toSeat = (scheduleId, cinemaId, movieId) => {
  Router.push({ path: '/seat', query: { scheduleId, cinemaId, movieId } })
}
</script>

<style scoped lang="scss">
.Select-BigBox {
  position: relative;
  margin-top: 60px;
  padding: 0 20px;
  background: url('../assets/ST3.png') no-repeat center center / cover;
  overflow: hidden;

  /* 背景模糊 */
  &::before {
    content: "";
    position: absolute;
    inset: 0;
    backdrop-filter: blur(2px) brightness(0.5);
	pointer-events: none;
    z-index: 0;
  }

  /* 顶部影院信息区 */
  .Select-BigBox-topBox {
    position: relative;
    z-index: 1;
    display: flex;
    justify-content: center;
    padding: 24px 0;

    .left-box .van-image {
      width: 220px;
      height: 220px;
      border: 3px solid black;
      border-radius: 12px;
      box-shadow: 0 6px 20px rgba(0,0,0,0.4);
      transition: transform .3s;
    }
    .left-box .van-image:hover {
      transform: scale(1.05);
    }

    .right-box {
      margin-left: 40px;
      background: rgba(0,0,0,0.6);
      padding: 20px 28px;
      border-radius: 10px;
      box-shadow: 0 6px 20px rgba(0,0,0,0.5);
      min-width: 320px;

      .top-cinema-name {
        font-size: 24px;
        font-weight: 700;
        color:rgb(253, 252, 251);
        margin-bottom: 8px;
      }
      .cinema-address,
      .phone {
        font-size: 14px;
        color: #eee;
        margin-bottom: 6px;
      }
      .cinema-tags {
        margin-top: 20px;
        .cinema-tags-item {
          display: flex;
          align-items: flex-start;
          margin-bottom: 10px;
		  margin-top: 10px;
          .tag {
            flex-shrink: 0;
            font-size: 12px;
            background: #ffcb8e;
            color: #333;
            padding: 3px 8px;
            border-radius: 4px;
            margin-right: 10px;
			margin-left: 4px;
          }
          .desc {
            font-size: 12px;
            color: #ddd;
            line-height: 1.4;
          }
        }
      }
    }
  }

  /* 内容区 */
  .Select-BigBox-content-Box {
    position: relative;
    z-index: 1;

    /* Swiper 轮播区保持原始样式 */
    .mySwiper {
      width: 100%;
      padding: 10px 0;
      position: relative;
      z-index: 1;
      background: transparent !important;
      overflow: visible !important;
      backdrop-filter: none !important;
    }
    .mySwiper .swiper-slide {
      width: auto !important;
      padding: 0 !important;
      margin: 0 !important;
      background: transparent !important;
    }
    .swiper-slide-active .van-image {
      border: none !important;
      box-shadow: none !important;
    }
    .swiper-3d .swiper-slide-shadow-coverflow {
      background-image: none !important;
    }
    .mySwiper .van-image {
      display: block;
      width: 200px;
      height: 280px;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
	  margin-bottom: 20px; 
	  margin-top: 20px;
    }


    /* 排期列表区 */
    .show-list {
      max-width: 1000px;
      margin: 32px auto;
      background: rgba(0,0,0,0.7);
      padding: 28px;
      border-radius: 12px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.6);

      /* 电影信息 */
      .movie-info {
        border-bottom: 1px solid rgba(255,255,255,0.2);
        padding-bottom: 18px;
        margin-bottom: 18px;

        .movie-info-top {
          display: flex;
          align-items: baseline;
          #top-title {
            font-size: 28px;
            font-weight: 700;
            color: #fff;
          }
          #top-score {
            font-size: 26px;
            color: #ffbf00;
            margin-left: 18px;
          }
          #top-score:after {
            content: "分";
            font-size: 14px;
          }
        }
        .movie-info-bottom {
          display: flex;
          gap: 60px;
          margin-top: 12px;
          .key {
            color: #aaa;
            font-size: 14px;
          }
          .value {
            color: #eee;
            font-size: 14px;
          }
        }
      }

      /* 日期选择 */
      .show-date {
        display: flex;
        align-items: center;
        margin-bottom: 24px;
        .show-data-left {
          color: #aaa;
          font-size: 14px;
          margin-right: 12px;
        }
        .data-item {
          color: #eee;
          font-size: 14px;
          padding: 6px 14px;
          margin-right: 10px;
          border-radius: 20px;
          cursor: pointer;
          transition: background .3s;
        }
        .data-item:hover {
          background: rgba(255,255,255,0.15);
        }
        .data-item.active {
          background: #d02c27;
          color: #fff;
        }
      }

      /* 排期表 */
      .plist-container {
        .plist {
          width: 100%;
          border-collapse: collapse;

          thead th {
            padding: 10px 0;
            font-size: 15px;
            color: #ddd;
            background: rgba(255,255,255,0.1);
          }
          tbody tr {
            &:nth-child(even) {
              background: rgba(255,255,255,0.05);
            }
            td {
              text-align: center;
              padding: 14px 0;
              .begin-time {
                font-size: 18px;
                font-weight: 600;
                color: #fff;
              }
              .end-time {
                font-size: 13px;
                color: #ccc;
              }
              .lang,
              .hall {
                font-size: 14px;
                color: #bbb;
              }
              .sell-price {
                font-size: 17px;
                color: #ff6b6b;
                font-weight: 600;
                &:before {
                  content: "\FFE5";
                  font-size: 12px;
                  margin-right: 2px;
                }
              }
              .buy-btn {
                font-size: 14px !important;
                height: 36px !important;
                border-radius: 20px !important;
                transition: transform .2s;
              }
              .buy-btn:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 12px rgba(208,33,53,0.5);
              }
            }
          }
        }
        .finished-all-shows {
          text-align: center;
          padding: 40px 0;
          .finish-text {
            font-size: 18px;
            color: #bbb;
          }
        }
      }
    }
  }
}
</style>