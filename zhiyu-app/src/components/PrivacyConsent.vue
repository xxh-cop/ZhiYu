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
					<ZyIcon name="check" :size="28" tone="brand" />
					<text class="clause-text">我们仅在你同意后收集昵称、对话内容等必要信息</text>
				</view>
				<view class="clause">
					<ZyIcon name="check" :size="28" tone="brand" />
					<text class="clause-text">对话与便签加密存储，咨询师看到的是脱敏汇总</text>
				</view>
				<view class="clause">
					<ZyIcon name="check" :size="28" tone="brand" />
					<text class="clause-text">你可随时撤回同意、关闭联系方式授权或注销账号</text>
				</view>
				<view class="clause">
					<ZyIcon name="check" :size="28" tone="brand" />
					<text class="clause-text">本系统不能替代专业心理咨询</text>
				</view>
			</view>

			<!-- 链接 -->
			<text class="policy-link" role="button" tabindex="0" @click="viewFullPolicy" @keydown.enter.prevent="viewFullPolicy" @keydown.space.prevent="viewFullPolicy">查看完整隐私政策</text>

			<!-- 按钮区 -->
			<view class="panel-actions">
				<view class="btn-decline" role="button" tabindex="0" @click="onDecline" @keydown.enter.prevent="onDecline" @keydown.space.prevent="onDecline">
					<text class="btn-decline-text">不同意，仅浏览</text>
				</view>
				<view class="btn-agree" role="button" tabindex="0" @click="onAgree" @keydown.enter.prevent="onAgree" @keydown.space.prevent="onAgree" :class="{ 'btn-agree--loading': loading }">
					<text class="btn-agree-text">{{ loading ? '提交中...' : '同意并继续' }}</text>
				</view>
			</view>
		</view>
		<Toast />
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { http } from '../utils/request'
import { useToast } from '../composables/useToast'
import Toast from './Toast.vue'
import ZyIcon from './ZyIcon.vue'

const props = defineProps({
	visible: { type: Boolean, default: false },
})

const emit = defineEmits(['agree', 'decline'])

const loading = ref(false)
const toast = useToast()

async function onAgree() {
	if (loading.value) return
	loading.value = true
	try {
		await http.post('/api/user/privacy/agree', {})
		emit('agree')
	} catch (e) {
		toast.show('网络异常，请稍后重试')
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
	background: linear-gradient(90deg, var(--zy-brand-600, #217e76) 0%, var(--zy-brand-300, var(--zy-brand-300, #5bc4a8)) 100%);
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
	background: linear-gradient(135deg, var(--zy-brand-600, #217e76), #5BCFBF);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 20rpx;
	box-shadow: 0 8rpx 24rpx rgba(33, 126, 118, 0.2);
}

.header-icon-text {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 600;
	color: #FFFFFF;
}

.panel-title {
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 600;
	color: #233d34;
}

.panel-sub {
	margin-top: 8rpx;
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
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
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.6;
	color: #4a5f54;
}

.policy-link {
	display: block;
	text-align: center;
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-brand-700, #1a645e);
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
	border: 1rpx solid #dfe7dc;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color 0.15s ease;
}

.btn-decline:active {
	background-color: var(--zy-bg-sunken, #f4f4f2);
}

.btn-decline-text {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-text, #4a5f54);
}

.btn-agree {
	flex: 1.2;
	height: 88rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, var(--zy-brand-600, #217e76), var(--zy-brand-300, var(--zy-brand-300, #5bc4a8)));
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform 0.15s ease;
	box-shadow: 0 4rpx 16rpx rgba(33, 126, 118, 0.25);
}

.btn-agree:active {
	transform: scale(0.97);
}

.btn-agree--loading {
	opacity: 0.6;
}

.btn-agree-text {
	font-size:var(--zy-text-body-lg, 30rpx);
	font-weight: 500;
	color: #FFFFFF;
}
</style>
