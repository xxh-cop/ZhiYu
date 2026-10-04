<template>
	<view class="home-page">
		<!-- ═══ 模块 1：顶部问候区 ═══ -->
		<view class="section section--greeting" style="--stagger-i: 0">
			<view class="greeting-left">
				<text class="greeting-hello">你好，{{ nickname }}</text>
				<text class="greeting-welcome">{{ welcome }}</text>
			</view>
			<view class="greeting-avatar" @click="goProfile">
				<text class="greeting-avatar-text">{{ avatarLetter }}</text>
				<view v-if="hasNotice" class="greeting-avatar-dot"></view>
			</view>
		</view>

		<!-- ═══ 模块 2：今日心情打卡 ═══ -->
		<view class="section section--mood" style="--stagger-i: 1">
			<text class="section-title">今天心情怎么样？</text>
			<text class="section-subtitle">轻点记录一下</text>
			<view class="mood-row">
				<view
					v-for="(m, i) in moods"
					:key="i"
					class="mood-item"
					:class="{ 'mood-item--active': moodIndex === i }"
					@click="selectMood(i)"
				>
					<text class="mood-emoji">{{ m.emoji }}</text>
					<text class="mood-label">{{ m.label }}</text>
				</view>
			</view>
			<view v-if="moodIndex !== null" class="mood-recorded">
				<wd-icon name="check" size="28rpx" color="#2D9B8F" />
				<text class="mood-recorded-text">已记录今日心情</text>
			</view>
		</view>

		<!-- ═══ 模块 3：每日寄语卡片 ═══ -->
		<view class="section section--quote" style="--stagger-i: 2" @click="nextQuote">
			<view class="quote-card">
				<view class="quote-bar"></view>
				<text class="quote-text">{{ currentQuote }}</text>
				<text class="quote-hint">轻点换一句</text>
			</view>
		</view>

		<!-- ═══ 模块 4：功能入口 2×2 网格 ═══ -->
		<view class="section section--entries" style="--stagger-i: 3">
			<view class="entries-grid">
				<view
					v-for="(entry, i) in entries"
					:key="i"
					class="entry-card"
					@click="entry.action"
				>
					<view class="entry-icon-wrap" :style="{ '--entry-i': i }">
						<wd-icon :name="entry.icon" size="36rpx" color="#2D9B8F" />
					</view>
					<text class="entry-title">{{ entry.title }}</text>
					<text class="entry-sub">{{ entry.sub }}</text>
				</view>
			</view>
		</view>

		<!-- ═══ 模块 5：今日推荐手作 ═══ -->
		<view class="section section--recommend" style="--stagger-i: 4">
			<view class="recommend-header">
				<text class="section-title">今日推荐手作</text>
				<text class="recommend-refresh" @click="refreshRecommend">换一批</text>
			</view>
			<scroll-view scroll-x class="recommend-scroll" :show-scrollbar="false">
				<view class="recommend-track">
					<view
						v-for="(item, i) in recommends"
						:key="i"
						class="recommend-card"
						@click="goRecommend"
					>
						<view class="recommend-img" :style="{ background: item.gradient }">
							<wd-icon name="image" size="40rpx" color="#FFFFFF" />
						</view>
						<text class="recommend-name">{{ item.name }}</text>
						<view class="recommend-tag">
							<text class="recommend-tag-text">{{ item.duration }}</text>
						</view>
					</view>
				</view>
			</scroll-view>
		</view>

		<!-- ═══ 模块 6：底部免责声明 ═══ -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

		<!-- 全局 AI 伴侣 -->
		<AiCompanion />
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'
import AiCompanion from '../../components/AiCompanion.vue'

const userStore = useUserStore()
const loadingMe = ref(false)
const quoteIndex = ref(0)
const moodIndex = ref(null)

const QUOTES = [
	'每一针都是与自己和解的过程',
	'手作的意义不在完美，在于专注',
	'允许自己慢下来，就是一种疗愈',
	'今天也辛苦了，给自己一个小小的拥抱',
	'情绪没有对错，只有看见与看不见',
]

const moods = [
	{ emoji: '😊', label: '开心' },
	{ emoji: '😌', label: '平静' },
	{ emoji: '😐', label: '一般' },
	{ emoji: '😮‍💨', label: '有点累' },
	{ emoji: '😕', label: '不太好' },
]

const recommends = ref([
	{ name: '解压扎染方巾', duration: '约 45 分钟', gradient: 'linear-gradient(135deg, #2D9B8F, #5BCFBF)' },
	{ name: '刺绣书签', duration: '约 30 分钟', gradient: 'linear-gradient(135deg, #FFB547, #FFD08A)' },
	{ name: '编织杯垫', duration: '约 25 分钟', gradient: 'linear-gradient(135deg, #FF6B6B, #FF9B9B)' },
])

const entries = [
	{ icon: 'chat', title: '情绪倾听', sub: '和 AI 说说话', action: goEmotion },
	{ icon: 'star', title: '疗愈推荐', sub: '找到适合的手作', action: goRecommend },
	{ icon: 'edit', title: '创作引导', sub: '一步步跟着做', action: toastComing },
	{ icon: 'image', title: '我的作品', sub: '看看创作成果', action: goWorks },
]

const nickname = computed(
	() => (userStore.userInfo && userStore.userInfo.nickname) || '同学'
)

const avatarLetter = computed(() => {
	const n = nickname.value
	return n ? n.charAt(0) : '同'
})

const hasNotice = ref(true) // 临时：有未读消息

const welcome = computed(() => {
	const hour = new Date().getHours()
	if (hour < 6) return '夜深了，先把自己安顿好'
	if (hour < 12) return '早上好，今天也慢慢来'
	if (hour < 18) return '下午好，给自己留一点安静的时间'
	return '晚上好，把今天的疲惫放一放'
})

const currentQuote = computed(() => QUOTES[quoteIndex.value % QUOTES.length])

function selectMood(i) {
	moodIndex.value = moodIndex.value === i ? null : i
}

function nextQuote() {
	quoteIndex.value++
}

function refreshRecommend() {
	// 随机打乱顺序
	recommends.value.sort(() => Math.random() - 0.5)
}

onShow(() => {
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	loadCurrentUser()
})

async function loadCurrentUser() {
	if (loadingMe.value) return
	loadingMe.value = true
	try {
		const data = await request({ url: '/api/auth/me', method: 'GET' })
		userStore.setUserInfo(data)
	} catch (e) {
	} finally {
		loadingMe.value = false
	}
}

function goEmotion() {
	uni.navigateTo({ url: '/pages/emotion/chat' })
}

function goRecommend() {
	uni.navigateTo({ url: '/pages/craft/recommend' })
}

function goWorks() {
	uni.switchTab({ url: '/pages/work/list' })
}

function goProfile() {
	uni.switchTab({ url: '/pages/profile/index' })
}

function toastComing() {
	uni.showToast({ title: '即将上线', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.home-page {
	min-height: 100vh;
	padding: 48rpx 32rpx 140rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ═══ 错峰入场动效 ═══ */
.section {
	animation: staggerIn 0.5s cubic-bezier(0.22, 0.61, 0.36, 1) backwards;
	animation-delay: calc(var(--stagger-i, 0) * 100ms);
}

@keyframes staggerIn {
	from {
		opacity: 0;
		transform: translateY(24rpx);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}

/* ═══ 模块 1：问候区 ═══ */
.section--greeting {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 40rpx;
}

.greeting-left {
	display: flex;
	flex-direction: column;
}

.greeting-hello {
	font-size: 52rpx;
	font-weight: 600;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
	line-height: 1.3;
}

.greeting-welcome {
	margin-top: 8rpx;
	font-size: 28rpx;
	color: var(--zy-content, #4B5563);
}

.greeting-avatar {
	position: relative;
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, #2D9B8F, #5BCFBF);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.greeting-avatar-text {
	font-size: 36rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.greeting-avatar-dot {
	position: absolute;
	top: 2rpx;
	right: 2rpx;
	width: 18rpx;
	height: 18rpx;
	border-radius: 50%;
	background-color: #FFB547;
	border: 3rpx solid #FFFFFF;
	box-sizing: border-box;
}

/* ═══ 模块 2：心情打卡 ═══ */
.section--mood {
	margin-bottom: 40rpx;
}

.section-title {
	font-size: 34rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

.section-subtitle {
	display: block;
	margin-top: 4rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.mood-row {
	display: flex;
	justify-content: space-between;
	margin-top: 28rpx;
}

.mood-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
	padding: 16rpx 8rpx;
	border-radius: 16rpx;
	transition: all 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.mood-emoji {
	font-size: 48rpx;
	line-height: 1;
	transition: transform 0.3s;
}

.mood-label {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

.mood-item--active {
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	transform: scale(1.08);
}

.mood-item--active .mood-emoji {
	transform: scale(1.15);
}

.mood-item--active .mood-label {
	color: #2D9B8F;
	font-weight: 500;
}

.mood-recorded {
	display: flex;
	align-items: center;
	gap: 8rpx;
	margin-top: 20rpx;
}

.mood-recorded-text {
	font-size: 24rpx;
	color: #2D9B8F;
}

/* ═══ 模块 3：寄语卡片 ═══ */
.section--quote {
	margin-bottom: 40rpx;
}

.quote-card {
	position: relative;
	padding: 32rpx 32rpx 32rpx 36rpx;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	border-radius: 16px;
	overflow: hidden;
	transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.section--quote:active .quote-card {
	transform: translateY(-4rpx);
	box-shadow: 0 8px 24px rgba(45, 155, 143, 0.12);
}

.quote-bar {
	position: absolute;
	left: 0;
	top: 50%;
	transform: translateY(-50%);
	width: 4rpx;
	height: 60%;
	background-color: #2D9B8F;
	border-radius: 2rpx;
}

.quote-text {
	font-size: 28rpx;
	line-height: 1.8;
	color: var(--zy-content, #4B5563);
}

.quote-hint {
	display: block;
	margin-top: 12rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ═══ 模块 4：功能入口 2×2 ═══ */
.section--entries {
	margin-bottom: 40rpx;
}

.entries-grid {
	display: flex;
	flex-wrap: wrap;
	gap: 24rpx;
}

.entry-card {
	width: calc(50% - 12rpx);
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-radius: 16px;
	padding: 32rpx;
	box-sizing: border-box;
	box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
	transition: transform 0.2s ease, box-shadow 0.2s ease;
	min-height: 200rpx;
	display: flex;
	flex-direction: column;
}

.entry-card:active {
	transform: translateY(-4rpx);
	box-shadow: 0 8px 24px rgba(45, 155, 143, 0.1);
}

.entry-icon-wrap {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background-color: rgba(45, 155, 143, 0.08);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 24rpx;
	animation: iconBounce 0.5s cubic-bezier(0.22, 0.61, 0.36, 1) backwards;
	animation-delay: calc(var(--entry-i, 0) * 100ms + 300ms);
}

@keyframes iconBounce {
	0% { transform: scale(0.8); }
	60% { transform: scale(1.15); }
	100% { transform: scale(1); }
}

.entry-title {
	font-size: 32rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	line-height: 1.3;
}

.entry-sub {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ═══ 模块 5：今日推荐手作 ═══ */
.section--recommend {
	margin-bottom: 40rpx;
}

.recommend-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 24rpx;
}

.recommend-refresh {
	font-size: 26rpx;
	color: #2D9B8F;
}

.recommend-scroll {
	width: 100%;
	white-space: nowrap;
}

.recommend-track {
	display: inline-flex;
	gap: 20rpx;
	padding-right: 32rpx;
}

.recommend-card {
	width: 240rpx;
	flex-shrink: 0;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-radius: 14px;
	padding: 16rpx;
	box-sizing: border-box;
	box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
	transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.recommend-card:active {
	transform: translateY(-4rpx);
	box-shadow: 0 8px 24px rgba(45, 155, 143, 0.1);
}

.recommend-img {
	width: 100%;
	height: 160rpx;
	border-radius: 10px;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 16rpx;
}

.recommend-name {
	font-size: 26rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.recommend-tag {
	display: inline-block;
	margin-top: 8rpx;
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: rgba(255, 181, 71, 0.12);
}

.recommend-tag-text {
	font-size: 22rpx;
	color: #FFB547;
}

/* ═══ 模块 6：底部免责 ═══ */
.footnote {
	display: block;
	margin-top: 20rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
