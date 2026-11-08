<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 温暖头部 -->
		<view class="hero zy-slide-up zy-stagger" style="--stagger-i: 0">
			<view class="hero-circle">
				<text class="hero-icon-text">织</text>
			</view>
			<text class="hero-title">你并不孤单</text>
			<text class="hero-sub">紧迫风险时请联系身边可信任的人或当地紧急服务</text>
		</view>

		<!-- 卡片 1：学校心理中心 -->
		<view class="card zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="card-stripe stripe-warm"></view>
			<view class="card-header">
				<view class="card-icon-wrap card-icon-warm">
					<wd-icon name="phone" size="40rpx" color="var(--zy-accent, #8a6d1f)" />
				</view>
				<view class="card-header-info">
					<text class="card-title">{{ psychCenterName }}</text>
					<text class="card-desc">校内咨询联系 · 预约时段请确认</text>
				</view>
			</view>
			<text class="card-phone">{{ psychCenterPhone }}</text>
			<view class="card-btn" role="button" tabindex="0" @click="callPhone(psychCenterPhone)" @keydown.enter.prevent="callPhone(psychCenterPhone)" @keydown.space.prevent="callPhone(psychCenterPhone)">
				<wd-icon name="phone" size="32rpx" color="#FFFFFF" />
				<text class="card-btn-text">拨打电话</text>
			</view>
		</view>

		<view class="card" role="button" tabindex="0" @click="goSupport" @keydown.enter.prevent="goSupport" @keydown.space.prevent="goSupport"><text class="card-title">校内预约与官方来源 →</text><text class="card-desc">查看地点、预约准备和信息核对日期</text></view>
		<!-- 卡片 2：全国心理援助热线 -->
		<view class="card zy-slide-up zy-stagger" style="--stagger-i: 2">
			<view class="card-stripe stripe-mint"></view>
			<view class="card-header">
				<view class="card-icon-wrap card-icon-mint">
					<wd-icon name="phone" size="40rpx" color="#217e76" />
				</view>
				<view class="card-header-info">
					<text class="card-title">{{ nationalHotlineName }}</text>
					<text class="card-desc">心理支持联系方式</text>
				</view>
			</view>
			<text class="card-phone">{{ nationalHotline }}</text>
			<view class="card-btn card-btn-mint" role="button" tabindex="0" @click="callPhone(nationalHotline)" @keydown.enter.prevent="callPhone(nationalHotline)" @keydown.space.prevent="callPhone(nationalHotline)">
				<wd-icon name="phone" size="32rpx" color="#FFFFFF" />
				<text class="card-btn-text">拨打电话</text>
			</view>
		</view>

		<!-- 卡片 3：立刻找人聊聊 -->
		<view class="chat-card zy-slide-up zy-stagger" style="--stagger-i: 3" role="button" tabindex="0" @click="goChat" @keydown.enter.prevent="goChat" @keydown.space.prevent="goChat">
			<view class="chat-card-icon">
				<wd-icon name="chat" size="44rpx" color="#FFFFFF" />
			</view>
			<view class="chat-card-info">
				<text class="chat-card-title">和小织聊聊（AI）</text>
				<text class="chat-card-sub">AI 陪伴不能提供紧急救援</text>
			</view>
			<wd-icon name="arrow-right" size="36rpx" color="#FFFFFF" />
		</view>

		<!-- 温暖文案 -->
		<view class="warm-text zy-slide-up zy-stagger" style="--stagger-i: 4">
			<text class="warm-line">如果你正在经历困难，请记得寻求帮助是勇敢的表现。</text>
			<text class="warm-line">你的感受很重要，有人愿意倾听。</text>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { campusSupport } from '../../data/campus-support'
import { onShow } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

// 兜底值：请求失败时保证页面仍可用（与 application.yml 默认值一致）
const FALLBACK = {
	psychCenterName: campusSupport.center.name,
	psychCenterPhone: campusSupport.center.phone,
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
			if (data.psychCenterPhone && data.psychCenterPhone !== '0791-0000000') psychCenterPhone.value = data.psychCenterPhone
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

function goSupport(){uni.navigateTo({url:'/pages/support/index'})}
function goChat() {
	uni.navigateTo({ url: '/pages/emotion/chat' })
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 0 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
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
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-primary-light, #5bc4a8));
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 28rpx;
	box-shadow: var(--zy-shadow-md);
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
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
}

.hero-sub {
	margin-top: 12rpx;
	font-size: 28rpx;
	color: var(--zy-text, #4a5f54);
}

/* ── 热线卡片 ── */
.card {
	position: relative;
	margin-bottom: 24rpx;
	padding: 36rpx 32rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	overflow: hidden;
}

.card-stripe {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	height: 6rpx;
}

.stripe-warm {
	background: linear-gradient(90deg, var(--zy-care, #a64842), var(--zy-d-care, #d4736a));
}

.stripe-mint {
	background: linear-gradient(90deg, var(--zy-primary, #217e76), var(--zy-brand-300, #5bc4a8));
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
	background-color: var(--zy-bg-tint-warm, #f5edde);
}

.card-icon-mint {
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.card-header-info {
	display: flex;
	flex-direction: column;
	gap: 6rpx;
}

.card-title {
	font-size: 32rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.card-desc {
	font-size: 24rpx;
	color: var(--zy-text, #4a5f54);
}

.card-phone {
	display: block;
	font-size: 48rpx;
	font-weight: 700;
	color: var(--zy-brand-700, #217e76);
	font-family: var(--zy-serif);
	letter-spacing: 4rpx;
	margin-bottom: 28rpx;
}

.card-btn {
	height: 80rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-accent, #8a6d1f);
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12rpx;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
	box-shadow: 0 4rpx 16rpx rgba(138, 109, 31, 0.25);
}

.card-btn:active {
	transform: scale(0.97);
}

.card-btn-mint {
	background: var(--zy-brand-600, #217e76);
	box-shadow: 0 4rpx 16rpx rgba(33, 126, 118, 0.25);
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
	border-radius: var(--zy-radius-lg, 32rpx);
	background: var(--zy-brand-600, #217e76);
	box-shadow: var(--zy-shadow-md);
	margin-bottom: 32rpx;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
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
	color: var(--zy-text, #4a5f54);
	text-align: center;
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	font-size: 22rpx;
	color: var(--zy-text, #4a5f54);
	text-align: center;
}
button:focus-visible{outline:3px solid var(--zy-brand-700,#1a645e);outline-offset:3px}
</style>
