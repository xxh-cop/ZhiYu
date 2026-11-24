<template>
	<view :class="zyThemeClass" class="page">
		<view class="zy-tabpage-top" aria-hidden="true"></view>
		<ZyPageHead seal="社" title="织友社区" subtitle="每个人都是创作者。慢一点，也值得被看见。">
			<template #action>
				<AccessibleButton variant="primary" size="sm" aria-label="发布作品" @click="goPublish">发布</AccessibleButton>
			</template>
		</ZyPageHead>

		<!-- 排序 -->
		<view class="filter-tabs" role="group" aria-label="作品排序">
			<ZyChip v-for="tab in tabs" :key="tab.value" :active="activeTab === tab.value" @click="switchTab(tab.value)">{{ tab.label }}</ZyChip>
		</view>

		<!-- 本期一件 -->
		<view v-if="featured && !loading" class="featured" role="button" tabindex="0" :aria-label="'查看本期作品 ' + featured.title" @click="goDetail(featured)" @keydown.enter.prevent="goDetail(featured)">
			<image class="featured-img" :src="coverFor(featured)" mode="aspectFill" />
			<view class="featured-shade" aria-hidden="true"></view>
			<view class="featured-seal"><ZySeal text="本期" size="md" tilt /></view>
			<view class="featured-copy">
				<text class="featured-title">{{ featured.title }}</text>
				<view class="featured-meta">
					<text class="featured-avatar">{{ (featured.authorName || '织').charAt(0) }}</text>
					<text class="featured-author">{{ featured.authorName }}</text>
					<view class="featured-like"><ZyIcon name="heart" :size="26" color="#FFFFFF" /><text>{{ featured.likeCount || 0 }}</text></view>
				</view>
			</view>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading && records.length === 0" class="masonry">
			<view v-for="c in 2" :key="'col-'+c" class="masonry-col">
				<view v-for="i in 2" :key="'sk-'+i" class="skeleton-card">
					<view class="skeleton-cover zy-skeleton" :style="{ height: (c + i) % 2 ? '300rpx' : '380rpx' }"></view>
					<view class="skeleton-title zy-skeleton"></view>
				</view>
			</view>
		</view>

		<StateBlock v-else-if="!loading && records.length === 0 && !errorMsg" type="empty" art="community" title="还没有作品" description="发布你的第一件吧" action-text="去发布" @action="goPublish" />

		<StateBlock v-else-if="errorMsg && records.length === 0" type="error" title="作品没有加载成功" :description="errorMsg" action-text="重试" :action-loading="loading" @action="loadData(1)" />

		<!-- 瀑布流 -->
		<view v-else>
			<view class="masonry">
				<view v-for="(col, ci) in columns" :key="'c' + ci" class="masonry-col">
					<view
						v-for="item in col"
						:key="item.id"
						class="work-card"
						role="button"
						tabindex="0"
						:aria-label="'查看作品 ' + item.title"
						@click="goDetail(item)"
						@keydown.enter.prevent="goDetail(item)"
					>
						<view v-if="item.mediaType === 'text'" class="text-cover">
							<text class="text-cover-quote" aria-hidden="true">“</text>
							<text class="text-cover-excerpt">{{ (item.message || item.title || '').slice(0, 48) }}</text>
							<text class="text-cover-label">创作故事</text>
						</view>
						<view v-else class="cover" :class="{ tall: item._tall }">
							<image class="cover-img" :src="coverFor(item)" mode="aspectFill" lazy-load />
							<view v-if="item.mediaType === 'video'" class="cover-play" aria-hidden="true"><ZyIcon name="play" :size="28" color="#FFFFFF" /></view>
						</view>
						<view class="card-body">
							<text class="card-title">{{ item.title }}</text>
							<text v-if="item.message && item.mediaType !== 'text'" class="card-message">{{ item.message }}</text>
							<view class="card-meta">
								<text class="card-avatar">{{ (item.authorName || '织').charAt(0) }}</text>
								<text class="card-author">{{ item.authorName }}</text>
								<view class="card-like" :class="{ liked: item.liked }">
									<ZyIcon name="heart" :size="24" :tone="item.liked ? 'care' : 'muted'" />
									<text>{{ item.likeCount || 0 }}</text>
								</view>
							</view>
						</view>
					</view>
				</view>
			</view>

			<view v-if="errorMsg && records.length > 0" class="bottom-error" role="alert">
				<text>{{ errorMsg }}</text>
				<AccessibleButton variant="secondary" size="sm" @click="loadMore">重试</AccessibleButton>
			</view>

			<view v-if="records.length > 0" class="bottom-status">
				<ZySpinner v-if="loading" :size="28" />
				<text v-if="loading" class="bottom-text">正在加载…</text>
				<template v-else-if="!hasMore">
					<view class="bottom-divider zy-band-meander"></view>
					<text class="bottom-text">已看完全部作品</text>
					<view class="bottom-divider zy-band-meander"></view>
				</template>
			</view>
		</view>


		<!-- 悬浮发布 -->
		<AccessibleButton class="fab" aria-label="发布作品" @click="goPublish">
			<ZyIcon name="plus" :size="44" color="#FFFFFF" />
		</AccessibleButton>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { API_BASE_URL } from '../../utils/config.js'
import StateBlock from '../../components/StateBlock.vue'
import ZySpinner from '../../components/ZySpinner.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import ZyChip from '../../components/ZyChip.vue'
import ZyPageHead from '../../components/ZyPageHead.vue'
import ZySeal from '../../components/ZySeal.vue'
import { coverFor } from '../../utils/covers'

const tabs = [
	{ label: '全部', value: 'all' },
	{ label: '最新', value: 'latest' },
	{ label: '热门', value: 'hot' },
]
const activeTab = ref('all')

const loading = ref(false)
const errorMsg = ref('')
const records = ref([])
const page = ref(1)
const total = ref(0)
const size = 10

const hasMore = computed(() => page.value * size < total.value)

// 「全部」页顶部保留一件有封面的作品作为本期展签，避免第一屏只有文字卡片。
const featured = computed(() => {
	if (activeTab.value !== 'all') return null
	return records.value.find(item => item.mediaType !== 'text' && item.mediaType !== 'video') || null
})

const visibleRecords = computed(() => {
	const item = featured.value
	return item ? records.value.filter(record => record.id !== item.id) : records.value
})

/* 瀑布流：左右两列交替分配，图片高矮交错 */
const columns = computed(() => {
	const cols = [[], []]
	visibleRecords.value.forEach((item, i) => cols[i % 2].push({ ...item, _tall: (i % 4 === 0 || i % 4 === 3) }))
	return cols
})

function switchTab(val) {
	if (activeTab.value === val) return
	activeTab.value = val
	loadData(1)
}

onPullDownRefresh(() => {
	loadData(1).finally(() => {
		uni.stopPullDownRefresh()
	})
})

onReachBottom(() => {
	if (hasMore.value && !loading.value) {
		page.value++
		loadMore()
	}
})

async function loadData(pageNum) {
	loading.value = true
	errorMsg.value = ''
	if (pageNum === 1) {
		records.value = []
	}
	page.value = pageNum

	try {
		const params = { page: pageNum, size }
		if (activeTab.value === 'latest') params.sort = 'latest'
		if (activeTab.value === 'hot') params.sort = 'hot'
		const data = await http.get('/api/works', { data: params })
		if (data) {
			total.value = Number(data.total) || 0
			records.value = data.records || []
		}
	} catch (e) {
		errorMsg.value = '作品加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

async function loadMore() {
	loading.value = true
	try {
		const params = { page: page.value, size }
		if (activeTab.value === 'latest') params.sort = 'latest'
		if (activeTab.value === 'hot') params.sort = 'hot'
		const data = await http.get('/api/works', { data: params })
		if (data) {
			total.value = Number(data.total) || 0
			records.value = records.value.concat(data.records || [])
		}
	} catch (e) {
		errorMsg.value = '更多作品加载失败，请重试'
	} finally {
		loading.value = false
	}
}

function coverStyle(coverUrl) {
	// #ifdef H5
	if (coverUrl) {
		const url = fullUrl(coverUrl)
		return `background-image: url(${url}); background-size: cover; background-position: center;`
	}
	// #endif
	// #ifndef H5
	if (coverUrl) {
		const url = fullUrl(coverUrl)
		return `background-image: url(${url}); background-size: cover; background-position: center;`
	}
	// #endif
	return 'background: var(--zy-bg-sunken);'
}

function fullUrl(url) {
	if (!url) return ''
	if (url.startsWith('http') || url.startsWith('/static/')) return url
	return API_BASE_URL + url
}

function formatTime(time) {
	if (!time) return ''
	const parts = time.split('T')
	if (parts.length < 2) return time
	const dateParts = parts[0].split('-')
	if (dateParts.length < 3) return time
	return `${parseInt(dateParts[1])}月${parseInt(dateParts[2])}日`
}

function goDetail(item) {
	uni.navigateTo({
		url: `/pages/work/detail?id=${item.id}`,
	})
}

function goPublish() {
	uni.navigateTo({ url: '/pages/work/publish' })
}

// 初始加载
loadData(1)
</script>

<style scoped>
.page { min-height: 100vh; padding: 28rpx 28rpx 200rpx; box-sizing: border-box; background-color: var(--zy-bg); }
/* #ifdef MP */
.page { padding-bottom: calc(200rpx + env(safe-area-inset-bottom)); }
/* #endif */

.filter-tabs { display: flex; gap: 14rpx; margin: 4rpx 0 26rpx; }

/* 本期一件 */
.featured { position: relative; height: 440rpx; border-radius: var(--zy-radius-feature); overflow: hidden; margin-bottom: 26rpx; box-shadow: var(--zy-shadow-pop); background: var(--zy-brand-900); }
.featured-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.featured-shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(13, 31, 69, 0) 35%, rgba(13, 31, 69, .9) 100%); }
.featured-seal { position: absolute; top: 24rpx; left: 24rpx; }
.featured-copy { position: absolute; left: 30rpx; right: 30rpx; bottom: 26rpx; }
.featured-title { display: block; font-family: var(--zy-font-display); font-size: 44rpx; font-weight: 700; line-height: 1.3; color: #fff; letter-spacing: 2rpx; }
.featured-meta { display: flex; align-items: center; gap: 12rpx; margin-top: 14rpx; font-size: 24rpx; color: rgba(255, 255, 255, .88); }
.featured-avatar { width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; border-radius: 50%; background: rgba(255, 255, 255, .22); color: #fff; font-size: 22rpx; }
.featured-author { flex: 1; }
.featured-like { display: flex; align-items: center; gap: 6rpx; }

/* 瀑布流 */
.masonry { display: flex; gap: 20rpx; align-items: flex-start; }
.masonry-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 20rpx; }
.work-card { background: var(--zy-card-bg); border-radius: var(--zy-radius-card); overflow: hidden; box-shadow: var(--zy-shadow-card); }
.work-card:active { transform: scale(.98); }
.cover { position: relative; height: 300rpx; background: var(--zy-bg-sunken); }
.cover.tall { height: 400rpx; }
.cover-img { width: 100%; height: 100%; display: block; }
.cover-play { position: absolute; left: 50%; top: 50%; width: 76rpx; height: 76rpx; margin: -38rpx 0 0 -38rpx; border-radius: 50%; background: rgba(13, 31, 69, .6); display: flex; align-items: center; justify-content: center; }
.text-cover { position: relative; min-height: 240rpx; padding: 56rpx 24rpx 24rpx; box-sizing: border-box; background: var(--zy-brand-50); display: flex; flex-direction: column; justify-content: flex-end; }
.text-cover-quote { position: absolute; top: 4rpx; left: 18rpx; font-family: var(--zy-font-display); font-size: 88rpx; line-height: 1; color: var(--zy-brand-300); }
.text-cover-excerpt { font-family: var(--zy-font-display); font-size: 30rpx; line-height: 1.7; color: var(--zy-brand-700); }
.text-cover-label { position: absolute; top: 20rpx; right: 20rpx; padding: 2rpx 14rpx; border-radius: 999rpx; background: var(--zy-card-bg); font-size: 20rpx; color: var(--zy-brand-600); }
.card-body { padding: 18rpx 20rpx 20rpx; }
.card-title { display: block; font-size: 28rpx; font-weight: 700; line-height: 1.45; color: var(--zy-title); }
.card-message { display: -webkit-box; margin-top: 8rpx; font-size: 24rpx; line-height: 1.6; color: var(--zy-text); overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.card-meta { display: flex; align-items: center; gap: 10rpx; margin-top: 14rpx; }
.card-avatar { width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: var(--zy-brand-100); color: var(--zy-brand-700); font-size: 20rpx; font-weight: 700; flex-shrink: 0; }
.card-author { flex: 1; min-width: 0; font-size: 22rpx; color: var(--zy-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-like { display: flex; align-items: center; gap: 4rpx; font-size: 22rpx; color: var(--zy-muted); }
.card-like.liked { color: var(--zy-care); }

/* 骨架 */
.skeleton-card { background: var(--zy-card-bg); border-radius: var(--zy-radius-card); overflow: hidden; padding-bottom: 20rpx; }
.skeleton-title { height: 28rpx; width: 70%; margin: 18rpx 20rpx 0; }

/* 底部 */
.bottom-status { display: flex; align-items: center; justify-content: center; gap: 16rpx; padding: 40rpx 0 8rpx; }
.bottom-divider { width: 84rpx; }
.bottom-text { font-size: 24rpx; color: var(--zy-muted); }
.bottom-error { display: flex; flex-direction: column; align-items: center; gap: 14rpx; margin-top: 24rpx; padding: 24rpx; border-radius: var(--zy-radius-card); background: var(--zy-card-bg); color: var(--zy-text); font-size: 24rpx; }


/* 悬浮发布 */
.fab { position: fixed; right: 36rpx; bottom: calc(150rpx + env(safe-area-inset-bottom)); z-index: 30; width: 108rpx; height: 108rpx; min-width: 108rpx; min-height: 108rpx; padding: 0; margin: 0; border-radius: 50%; background: var(--zy-cinnabar); border: 0; box-shadow: 0 12rpx 28rpx rgba(192, 57, 43, .35); display: flex; align-items: center; justify-content: center; }
.fab:active { transform: scale(.94); }

@media (min-width: 850px) {
	.masonry-col { gap: 24px; }
}
</style>

<style scoped>
/* v9 自定义导航：H5 留状态栏；小程序让出胶囊区 */
.zy-tabpage-top { height: var(--status-bar-height, 0px); }
/* #ifdef MP */
.zy-tabpage-top { height: calc(var(--status-bar-height, 20px) + 88rpx); }
/* #endif */
</style>
