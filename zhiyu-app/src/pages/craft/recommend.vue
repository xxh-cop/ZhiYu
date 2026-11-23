<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 顶部标题区 -->
		<view class="header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-eyebrow">RECOMMENDATIONS · 疗愈推荐</text>
			<view class="header-row">
				<text class="page-title">为你推荐</text>
				<view v-if="moodTag" class="mood-badge">
					<text class="mood-text">{{ moodTag }}</text>
				</view>
			</view>
		</view>

		<!-- 推荐理由卡片 -->
		<view v-if="reason" class="reason-card zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="reason-quote">"</view>
			<view class="reason-header">
				<view class="reason-icon-wrap">
					<ZyIcon name="comment" :size="28" tone="brand" />
				</view>
				<text class="reason-label">小织推荐</text>
			</view>
			<text class="reason-text">{{ reason }}</text>
		</view>

		<!-- 刷新按钮 -->
		<view class="refresh-btn zy-slide-up zy-stagger" style="--stagger-i: 2" role="button" tabindex="0" @click="loadData" @keydown.enter.prevent="loadData" @keydown.space.prevent="loadData">
			<text class="refresh-icon">↻</text>
			<text class="refresh-text">换一批</text>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading" class="card-list">
			<view v-for="i in 3" :key="'sk-'+i" class="skeleton-card">
				<view class="skeleton-cover zy-skeleton"></view>
				<view class="skeleton-body">
					<view class="skeleton-title zy-skeleton"></view>
					<view class="skeleton-text zy-skeleton"></view>
				</view>
			</view>
		</view>

		<!-- 空状态 -->
		<view v-else-if="items.length === 0 && !errorMsg" class="status-box">
			<text class="status-text">暂时没有匹配的手作，换个时间再试试</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="error" size="small" plain @click="loadData">重试</wd-button>
		</view>

		<!-- 卡片列表 -->
		<view v-else class="card-list">
			<HeritageCard
				v-for="(item, idx) in items"
				:key="item.projectId"
				class="craft-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx + 3 }"
				:family="familyFor(item.category)"
				:title="item.title"
				:tag="item.category || ''"
				:meta="'约 ' + item.estMinutes + ' 分钟'"
				:note="item.reason || ''"
				@open="goSession(item)"
			>
				<!-- 封面图（cover slot 保留现有渐变/占位） -->
				<template #cover>
					<view class="card-cover" :style="coverStyle(item.coverUrl, familyFor(item.category))">
						<view class="level-badge">
							<text class="level-text">{{ levelLabel(item.level) }}</text>
						</view>
					</view>
				</template>

				<view v-if="splitTags(item.therapyTags).length" class="card-tags">
					<view
						v-for="tag in splitTags(item.therapyTags)"
						:key="tag"
						class="tag-badge"
					>
						<text class="tag-text">{{ tag }}</text>
					</view>
				</view>
				<!-- AM2：低干扰负反馈入口，点击即移出该卡片并静默上报 DISMISS -->
				<template #footer>
					<view class="card-dismiss">
						<AccessibleButton class="dismiss-btn" :aria-label="'对「' + item.title + '」不感兴趣，换一条推荐'" @click.stop="dismiss(item)">换一个</AccessibleButton>
					</view>
				</template>
			</HeritageCard>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { trackRecommendationClick } from '../../utils/recommend-click'
import AccessibleButton from '../../components/AccessibleButton.vue'
import HeritageCard from '../../components/HeritageCard.vue'
import ZyIcon from '../../components/ZyIcon.vue'

const loading = ref(false)
const errorMsg = ref('')
const items = ref([])
const recommendationId = ref('')
const reason = ref('')
const moodTag = ref('')
const sessionId = ref(null)

const MOOD_LABELS = {
	anxious: '焦虑',
	stressed: '压力',
	depressed: '低落',
	normal: '平静',
}

onLoad((query) => {
	sessionId.value = query.sessionId || null
	loadData()
})

async function loadData() {
	loading.value = true
	errorMsg.value = ''
	items.value = []
	recommendationId.value = ''
	reason.value = ''
	moodTag.value = ''

	try {
		const params = {}
		if (sessionId.value) {
			params.sessionId = sessionId.value
		}
		const data = await http.get('/api/craft/recommend', { data: params })
		if (data) {
			recommendationId.value = data.recommendationId ? String(data.recommendationId) : ''
			reason.value = data.reason || ''
			moodTag.value = MOOD_LABELS[data.mood] || data.mood || ''
			items.value = data.items || []
		}
	} catch (e) {
		errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

function splitTags(tags) {
	if (!tags) return []
	return tags.split(',').map((t) => t.trim()).filter(Boolean)
}

function levelLabel(level) {
	if (level === 1) return '入门'
	if (level === 2) return '进阶'
	if (level === 3) return '高阶'
	return ''
}

/* 推荐接口未下发 family，按项目 category 映射四族；未命中回落编织 */
function familyFor(category) {
	const text = String(category || '')
	if (/纸|剪/.test(text)) return 'cutout'
	if (/竹|编|藤/.test(text)) return 'weave'
	if (/布|绣|织|线/.test(text)) return 'stitch'
	if (/陶|瓷|釉/.test(text)) return 'porcelain'
	return 'weave'
}

const FAMILY_COVER = {
	cutout: '/static/covers/jianzhi.jpg',
	stitch: '/static/blueprint/hero-cloth.jpg',
	weave: '/static/blueprint/hero-bamboo.jpg',
	porcelain: '/static/covers/taoyi.jpg',
}

function coverStyle(coverUrl, family) {
	// #ifdef H5
	if (coverUrl) {
		return `background-image: url(${coverUrl}); background-size: cover; background-position: center;`
	}
	// #endif
	const fallback = FAMILY_COVER[family] || '/static/blueprint/hero-cloth.jpg'
	return `background-image: url(${fallback}); background-size: cover; background-position: center;`
}

function goSession(item) {
	trackRecommendationClick({
		recommendationId: recommendationId.value,
		projectId: item.projectId,
		source: 'RECOMMEND',
	})
	uni.navigateTo({
		url: `/pages/craft/session?projectId=${item.projectId}&projectTitle=${encodeURIComponent(item.title)}`,
	})
}

/**
 * 「换一个」（AM2 DISMISS）：本地立即移出该卡片（用户要看到生效），
 * 上报静默——失败不阻塞、不打扰（稀疏保护与回灌加权都在后端）。
 */
function dismiss(item) {
	items.value = items.value.filter((i) => i.projectId !== item.projectId)
	trackRecommendationClick({
		recommendationId: recommendationId.value,
		projectId: item.projectId,
		source: 'RECOMMEND',
		eventType: 'DISMISS',
	})
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 80rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
}

/* ── 顶部标题区 ── */
.header {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 24rpx;
}

.page-title {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
}

.mood-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.mood-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
}

/* ── 推荐理由卡片 ── */
.reason-card {
	position: relative;
	padding: 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	margin-bottom: 24rpx;
	overflow: hidden;
}

.reason-quote {
	position: absolute;
	top: 4rpx;
	left: 16rpx;
	font-size:var(--zy-text-display, 56rpx);
	font-family: var(--zy-serif);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	opacity: 0.15;
	line-height: 1;
}

.reason-header {
	display: flex;
	align-items: center;
	gap: 10rpx;
	margin-bottom: 12rpx;
}

.reason-icon-wrap {
	width: 48rpx;
	height: 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	display: flex;
	align-items: center;
	justify-content: center;
}

.reason-label {
	font-size:var(--zy-text-caption, 24rpx);
	font-weight: 500;
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
}

.reason-text {
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.8;
	color: var(--zy-content, #4a5f54);
}

/* ── 刷新按钮 ── */
.refresh-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 12rpx 24rpx;
	margin-bottom: 24rpx;
}

.refresh-icon {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	transition: transform 0.5s ease;
}

.refresh-btn:active .refresh-icon {
	transform: rotate(360deg);
}

.refresh-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
}

/* ── 骨架屏 ── */
.skeleton-card {
	overflow: hidden;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
}

.skeleton-cover {
	height: 240rpx;
}

.skeleton-body {
	padding: 28rpx;
}

.skeleton-title {
	height: 36rpx;
	width: 60%;
	margin-bottom: 16rpx;
}

.skeleton-text {
	height: 28rpx;
	width: 90%;
}

/* ── 状态区 ── */
.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 80rpx 0;
	gap: 24rpx;
}

.status-text {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-text, #4a5f54);
}

.error-text {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-care, var(--zy-care, #a64842));
}

/* ── 卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.craft-card {
	overflow: hidden;
	transition: transform var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out);
}

.craft-card:active {
	transform: translateY(-2rpx);
}

/* ── 封面图（cover slot；高度跟随 HeritageCard 封面） ── */
.card-cover {
	height: 100%;
	box-sizing: border-box;
	display: flex;
	align-items: flex-start;
	justify-content: flex-end;
	padding: 16rpx;
}

.level-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-gold, #d9a441);
}

.level-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: #FFFFFF;
	font-weight: 500;
}

/* ── 疗愈标签 ── */
.card-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 12rpx;
}

.tag-badge {
	padding: 6rpx 20rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.tag-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
	text-align: center;
}
/* AM2「换一个」：低干扰小字入口，不抢卡片主视觉 */
.card-dismiss {
	display: flex;
	justify-content: flex-end;
	margin-top: 12rpx;
}
.dismiss-btn {
	padding: 6rpx 16rpx;
	font-size:var(--zy-text-micro, 22rpx);
	line-height: 1.6;
	color: var(--zy-text, #4a5f54);
	background: transparent;
	border-radius: 999rpx;
}
.dismiss-btn:focus-visible {
	outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e));
	outline-offset: 2px;
}
button:focus-visible{outline:3px solid var(--zy-brand-700,var(--zy-brand-700, #1a645e));outline-offset:3px}

/* ── v9 冷瓷展签收口 ── */
.page { padding: 36rpx 32rpx 120rpx; background-color: var(--zy-bg); }
.header { display: block; padding-bottom: 28rpx; margin-bottom: 28rpx; border-bottom: 1rpx solid var(--zy-border); }
.page-eyebrow { display: block; font-size: var(--zy-text-micro, 22rpx); letter-spacing: 2px; color: var(--zy-muted); margin-bottom: 18rpx; }
.header-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 16rpx; }
.page-title { font-family: var(--zy-font-display, serif); font-size: 64rpx; font-weight: 500; line-height: 1.2; }
.mood-badge { padding: 0; border-radius: 0; background: transparent; border-bottom: 2rpx solid var(--zy-accent); }
.mood-text { color: var(--zy-accent); font-size: var(--zy-text-caption, 24rpx); }
.reason-card { padding: 24rpx 0 24rpx 24rpx; border-radius: 0; background: transparent; border: 0; border-left: 4rpx solid var(--zy-care); box-shadow: none; }
.reason-quote { display: none; }
.reason-icon-wrap { width: 40rpx; height: 40rpx; border-radius: 0; background: transparent; }
.reason-label { color: var(--zy-care); letter-spacing: 1rpx; }
.reason-text { font-family: var(--zy-font-display, serif); font-size: 32rpx; line-height: 1.9; color: var(--zy-title); }
.refresh-btn { padding: 8rpx 0; }
.refresh-text { color: var(--zy-brand-700); }
.skeleton-card { border-radius: 0; border: 1rpx solid var(--zy-border); }
.skeleton-cover { height: 220rpx; }
.card-list { gap: 28rpx; }
.craft-card { border-radius: 0; }
.craft-card:active { transform: none; }
.card-cover { padding: 0; }
.level-badge { padding: 6rpx 12rpx; border-radius: 0; background: var(--zy-brand-700); }
.level-text { letter-spacing: 1rpx; }
.card-tags { gap: 10rpx; }
.tag-badge { padding: 2rpx 8rpx; border-radius: 0; border: 1rpx solid var(--zy-border); background: transparent; }
.tag-text { color: var(--zy-muted); }
.card-dismiss { margin-top: 8rpx; }
.dismiss-btn { border-radius: 0; padding: 4rpx 0; }
.footnote { color: var(--zy-muted); }
</style>
