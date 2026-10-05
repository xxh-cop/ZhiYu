<template>
	<view v-if="visible" class="consent-mask" @click.stop>
		<view class="consent-panel">
			<!-- 顶部装饰条 -->
			<view class="panel-accent"></view>

			<!-- 标题区 -->
			<view class="panel-header">
				<view class="header-icon">
					<text class="header-icon-text">织</text>
				</view>
				<text class="panel-title">欢迎来到织愈</text>
				<text class="panel-sub">用一针一线，缝补青春的焦虑</text>
			</view>

			<!-- 简述区 -->
			<view class="panel-body">
				<view class="clause">
					<wd-icon name="check" size="28rpx" color="#2D9B8F" />
					<text class="clause-text">我们仅在你同意后收集昵称、对话内容等必要信息</text>
				</view>
				<view class="clause">
					<wd-icon name="check" size="28rpx" color="#2D9B8F" />
					<text class="clause-text">对话与便签加密存储，咨询师看到的是脱敏汇总</text>
				</view>
				<view class="clause">
					<wd-icon name="check" size="28rpx" color="#2D9B8F" />
					<text class="clause-text">你可随时撤回同意、关闭联系方式授权或注销账号</text>
				</view>
				<view class="clause">
					<wd-icon name="check" size="28rpx" color="#2D9B8F" />
					<text class="clause-text">本系统不能替代专业心理咨询</text>
				</view>
			</view>

			<!-- 链接 -->
			<text class="policy-link" @click="viewFullPolicy">查看完整隐私政策</text>

			<!-- 按钮区 -->
			<view class="panel-actions">
				<view class="btn-decline" @click="onDecline">
					<text class="btn-decline-text">不同意，仅浏览</text>
				</view>
				<view class="btn-agree" @click="onAgree" :class="{ 'btn-agree--loading': loading }">
					<text class="btn-agree-text">{{ loading ? '提交中...' : '同意并继续' }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { http } from '../utils/request'

const props = defineProps({
	visible: { type: Boolean, default: false },
})

const emit = defineEmits(['agree', 'decline'])

const loading = ref(false)

async function onAgree() {
	if (loading.value) return
	loading.value = true
	try {
		await http.post('/api/user/privacy/agree', {})
		emit('agree')
	} catch (e) {
		uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
	} finally {
		loading.value = false
	}
}

function onDecline() {
	emit('decline')
}

function viewFullPolicy() {
	uni.navigateTo({ url: '/pages/privacy/index' })
}
</script>

<style scoped>
.consent-mask {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	bottom: 0;
	background-color: rgba(0, 0, 0, 0.45);
	z-index: 9999;
	display: flex;
	align-items: flex-end;
	justify-content: center;
}

.consent-panel {
	width: 100%;
	max-width: 750rpx;
	background-color: #FFFFFF;
	border-radius: 32rpx 32rpx 0 0;
	overflow: hidden;
	animation: slideUp 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}

@keyframes slideUp {
	from { transform: translateY(100%); }
	to { transform: translateY(0); }
}

.panel-accent {
	height: 8rpx;
	background: linear-gradient(90deg, #2D9B8F 0%, #5BC4A8 100%);
}

.panel-header {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 48rpx 32rpx 24rpx;
}

.header-icon {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, #2D9B8F, #5BCFBF);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 20rpx;
	box-shadow: 0 8rpx 24rpx rgba(45, 155, 143, 0.2);
}

.header-icon-text {
	font-size: 40rpx;
	font-weight: 600;
	color: #FFFFFF;
}

.panel-title {
	font-size: 38rpx;
	font-weight: 600;
	color: #1F2937;
}

.panel-sub {
	margin-top: 8rpx;
	font-size: 26rpx;
	color: #9CA3AF;
}

.panel-body {
	padding: 16rpx 40rpx 16rpx;
}

.clause {
	display: flex;
	align-items: flex-start;
	gap: 16rpx;
	padding: 12rpx 0;
}

.clause-text {
	flex: 1;
	font-size: 28rpx;
	line-height: 1.6;
	color: #4B5563;
}

.policy-link {
	display: block;
	text-align: center;
	font-size: 26rpx;
	color: #2D9B8F;
	padding: 16rpx 0 8rpx;
}

.panel-actions {
	display: flex;
	gap: 20rpx;
	padding: 16rpx 40rpx 48rpx;
}

.btn-decline {
	flex: 1;
	height: 88rpx;
	border-radius: 999rpx;
	border: 1rpx solid #E5E7EB;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color 0.15s ease;
}

.btn-decline:active {
	background-color: #F8F9FB;
}

.btn-decline-text {
	font-size: 28rpx;
	color: #6B7280;
}

.btn-agree {
	flex: 1.2;
	height: 88rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #2D9B8F, #5BC4A8);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform 0.15s ease;
	box-shadow: 0 4rpx 16rpx rgba(45, 155, 143, 0.25);
}

.btn-agree:active {
	transform: scale(0.97);
}

.btn-agree--loading {
	opacity: 0.6;
}

.btn-agree-text {
	font-size: 30rpx;
	font-weight: 500;
	color: #FFFFFF;
}
</style>
