<template>
	<view class="page zy-page-enter">
		<!-- 温暖头部 -->
		<view class="hero" style="--stagger-i: 0">
			<view class="hero-circle">
				<text class="hero-icon-text">织</text>
			</view>
			<text class="hero-title">你并不孤单</text>
			<text class="hero-sub">需要时，我们都在</text>
		</view>

		<!-- 卡片 1：学校心理中心 -->
		<view class="card" style="--stagger-i: 1">
			<view class="card-header">
				<view class="card-icon-wrap card-icon-warm">
					<wd-icon name="phone" size="40rpx" color="#FFB547" />
				</view>
				<view class="card-header-info">
					<text class="card-title">{{ psychCenterName }}</text>
					<text class="card-desc">校内专业心理咨询资源</text>
				</view>
			</view>
			<text class="card-phone">{{ psychCenterPhone }}</text>
			<view class="card-btn" @click="callPhone(psychCenterPhone)">
				<wd-icon name="phone" size="32rpx" color="#FFFFFF" />
				<text class="card-btn-text">拨打电话</text>
			</view>
		</view>

		<!-- 卡片 2：全国心理援助热线 -->
		<view class="card" style="--stagger-i: 2">
			<view class="card-header">
				<view class="card-icon-wrap card-icon-mint">
					<wd-icon name="phone" size="40rpx" color="#2D9B8F" />
				</view>
				<view class="card-header-info">
					<text class="card-title">{{ nationalHotlineName }}</text>
					<text class="card-desc">24 小时全国心理援助</text>
				</view>
			</view>
			<text class="card-phone">{{ nationalHotline }}</text>
			<view class="card-btn card-btn-mint" @click="callPhone(nationalHotline)">
				<wd-icon name="phone" size="32rpx" color="#FFFFFF" />
				<text class="card-btn-text">拨打电话</text>
			</view>
		</view>

		<!-- 卡片 3：立刻找人聊聊 -->
		<view class="chat-card" style="--stagger-i: 3" @click="goChat">
			<view class="chat-card-icon">
				<wd-icon name="chat" size="44rpx" color="#FFFFFF" />
			</view>
			<view class="chat-card-info">
				<text class="chat-card-title">我需要立刻找人聊聊</text>
				<text class="chat-card-sub">和织愈说说话，我都在</text>
			</view>
			<wd-icon name="arrow-right" size="36rpx" color="#FFFFFF" />
		</view>

		<!-- 温暖文案 -->
		<view class="warm-text" style="--stagger-i: 4">
			<text class="warm-line">如果你正在经历困难，请记得寻求帮助是勇敢的表现。</text>
			<text class="warm-line">你的感受很重要，有人愿意倾听。</text>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

// 兜底值：请求失败时保证页面仍可用（与 application.yml 默认值一致）
const FALLBACK = {
	psychCenterName: '学校心理中心',
	psychCenterPhone: '0791-0000000',
	nationalHotlineName: '全国心理援助热线',
	nationalHotline: '12356',
}

const psychCenterName = ref(FALLBACK.psychCenterName)
const psychCenterPhone = ref(FALLBACK.psychCenterPhone)
const nationalHotlineName = ref(FALLBACK.nationalHotlineName)
const nationalHotline = ref(FALLBACK.nationalHotline)

onShow(() => {
	fetchHotlines()
})

async function fetchHotlines() {
	try {
		const data = await http.get('/api/crisis/hotlines')
		if (data) {
			if (data.psychCenterName) psychCenterName.value = data.psychCenterName
			if (data.psychCenterPhone) psychCenterPhone.value = data.psychCenterPhone
			if (data.nationalHotlineName) nationalHotlineName.value = data.nationalHotlineName
			if (data.nationalHotline) nationalHotline.value = data.nationalHotline
		}
	} catch (e) {
		// 静默失败，保留兜底值
	}
}

function callPhone(phone) {
	if (!phone) return
	// #ifdef MP-WEIXIN
	uni.makePhoneCall({ phoneNumber: phone, fail: () => {} })
	// #endif
	// #ifndef MP-WEIXIN
	// H5 不支持 makePhoneCall，降级为 tel 链接
	window.location.href = 'tel:' + phone
	// #endif
}

function goChat() {
	uni.navigateTo({ url: '/pages/emotion/chat' })
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 0 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 错峰入场 ── */
.hero,
.card,
.chat-card,
.warm-text {
	animation: staggerIn 0.5s cubic-bezier(0.22, 0.61, 0.36, 1) backwards;
	animation-delay: calc(var(--stagger-i, 0) * 100ms);
}

@keyframes staggerIn {
	from { opacity: 0; transform: translateY(24rpx); }
	to { opacity: 1; transform: translateY(0); }
}

/* ── 温暖头部 ── */
.hero {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 64rpx 0 48rpx;
}

.hero-circle {
	width: 112rpx;
	height: 112rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, #2D9B8F, #5BCFBF);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 28rpx;
	box-shadow: 0 8rpx 24rpx rgba(45, 155, 143, 0.2);
}

.hero-icon-text {
	font-size: 48rpx;
	font-weight: 600;
	color: #FFFFFF;
	font-family: var(--zy-serif);
}

.hero-title {
	font-size: 44rpx;
	font-weight: 600;
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

.hero-sub {
	margin-top: 12rpx;
	font-size: 28rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 热线卡片 ── */
.card {
	margin-bottom: 24rpx;
	padding: 36rpx 32rpx;
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
}

.card-header {
	display: flex;
	align-items: center;
	gap: 20rpx;
	margin-bottom: 24rpx;
}

.card-icon-wrap {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.card-icon-warm {
	background-color: var(--zy-bg-tint-warm, #FFF8EE);
}

.card-icon-mint {
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.card-header-info {
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.card-title {
	font-size: 32rpx;
	font-weight: 600;
	color: var(--zy-title, #1F2937);
}

.card-desc {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.card-phone {
	display: block;
	font-size: 48rpx;
	font-weight: 700;
	color: var(--wot-color-theme, #2D9B8F);
	font-family: var(--zy-serif);
	letter-spacing: 4rpx;
	margin-bottom: 28rpx;
}

.card-btn {
	height: 80rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #FFB547, #FFCB6E);
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
	transition: transform 0.2s ease;
}

.card-btn:active {
	transform: scale(0.97);
}

.card-btn-mint {
	background: linear-gradient(135deg, #2D9B8F, #5BC4A8);
}

.card-btn-text {
	font-size: 30rpx;
	font-weight: 500;
	color: #FFFFFF;
}

/* ── 立刻找人聊聊 ── */
.chat-card {
	display: flex;
	align-items: center;
	gap: 24rpx;
	padding: 36rpx 32rpx;
	border-radius: 32rpx;
	background: linear-gradient(135deg, #2D9B8F 0%, #5BC4A8 100%);
	box-shadow: 0 8rpx 28rpx rgba(45, 155, 143, 0.25);
	margin-bottom: 32rpx;
	transition: transform 0.2s ease;
}

.chat-card:active {
	transform: scale(0.98);
}

.chat-card-icon {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	background-color: rgba(255, 255, 255, 0.2);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.chat-card-info {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.chat-card-title {
	font-size: 32rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.chat-card-sub {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.8);
}

/* ── 温暖文案 ── */
.warm-text {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	padding: 16rpx 0 48rpx;
}

.warm-line {
	font-size: 26rpx;
	line-height: 1.8;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
