<template>
	<view class="page zy-page-enter">
		<view class="header">
			<text class="page-title">发布作品</text>
		</view>

		<view class="form">
			<!-- 作品标题 -->
			<view class="form-item">
				<text class="form-label">作品标题 <text class="required">*</text></text>
				<wd-input
					v-model="form.title"
					placeholder="给作品起个名字"
					:maxlength="100"
					no-border
					custom-class="form-input-el"
				/>
			</view>

			<!-- 可见性 -->
			<view class="form-item">
				<text class="form-label">谁可以看到</text>
				<view class="radio-group">
					<view
						class="radio-item"
						:class="{ 'radio-active': form.visible === 1 }"
						@click="form.visible = 1"
					>
						<view class="radio-dot" :class="{ 'dot-active': form.visible === 1 }"></view>
						<text class="radio-label">公开</text>
					</view>
					<view
						class="radio-item"
						:class="{ 'radio-active': form.visible === 0 }"
						@click="form.visible = 0"
					>
						<view class="radio-dot" :class="{ 'dot-active': form.visible === 0 }"></view>
						<text class="radio-label">私密</text>
					</view>
				</view>
			</view>

			<!-- 匿名开关 -->
			<view class="form-item form-row">
				<view class="form-row-info">
					<text class="form-label">匿名发布</text>
					<text class="form-hint">隐藏你的昵称，显示为「匿名同学」</text>
				</view>
				<view
					class="switch"
					:class="{ 'switch-on': form.anonymous }"
					@click="form.anonymous = !form.anonymous"
				>
					<view class="switch-knob"></view>
				</view>
			</view>

			<!-- AI 寄语提示 -->
			<view class="form-item">
				<view class="ai-hint">
					<view class="ai-hint-icon-wrap">
						<wd-icon name="sparkles" size="36rpx" color="#2D9B8F" />
					</view>
					<view class="ai-hint-info">
						<text class="ai-hint-title">AI 寄语</text>
						<text class="ai-hint-text">作品发布后，AI 会根据你的创作过程自动生成一句温暖寄语</text>
					</view>
				</view>
			</view>

			<!-- 提交按钮 -->
			<wd-button
				type="primary"
				block
				size="large"
				:disabled="!canSubmit || submitting"
				@click="handleSubmit"
			>{{ submitting ? '发布中…' : '发布作品' }}</wd-button>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const form = ref({
	title: '',
	visible: 1,
	anonymous: true,
	sessionId: null,
	projectId: null,
})

const submitting = ref(false)

const canSubmit = computed(() => form.value.title.trim().length > 0)

onLoad((query) => {
	form.value.sessionId = query.sessionId || null
	form.value.projectId = query.projectId || null
	if (query.projectTitle) {
		form.value.title = decodeURIComponent(query.projectTitle)
	}
})

async function handleSubmit() {
	if (!canSubmit.value || submitting.value) return

	submitting.value = true
	try {
		const body = {
			sessionId: form.value.sessionId,
			title: form.value.title.trim(),
			visible: form.value.visible,
			anonymous: form.value.anonymous,
		}
		if (form.value.projectId) {
			body.projectId = form.value.projectId
		}

		await http.post('/api/works', body)
		uni.showToast({ title: '发布成功', icon: 'success' })
		setTimeout(() => {
			uni.switchTab({ url: '/pages/work/list' })
		}, 1500)
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '发布失败', icon: 'none' })
	} finally {
		submitting.value = false
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

.header {
	margin-bottom: 40rpx;
}

.page-title {
	font-family: var(--zy-serif);
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.form {
	display: flex;
	flex-direction: column;
	gap: 36rpx;
}

.form-item {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
}

.form-label {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.required {
	color: #FF6B6B;
}

/* ── radio ── */
.radio-group {
	display: flex;
	gap: 24rpx;
}

.radio-item {
	display: flex;
	align-items: center;
	gap: 12rpx;
	padding: 20rpx 36rpx;
	border: 2rpx solid var(--zy-border, #E5E7EB);
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	transition: all 0.2s ease;
}

.radio-active {
	border-color: var(--wot-color-theme, #2D9B8F);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.radio-dot {
	width: 28rpx;
	height: 28rpx;
	border-radius: 50%;
	border: 4rpx solid var(--zy-border, #E5E7EB);
	background-color: var(--zy-bg-warm, #FFFFFF);
	transition: all 0.2s ease;
}

.dot-active {
	border-color: var(--wot-color-theme, #2D9B8F);
	background-color: var(--wot-color-theme, #2D9B8F);
	box-shadow: inset 0 0 0 4rpx var(--zy-bg-warm, #FFFFFF);
}

.radio-label {
	font-size: 28rpx;
	color: var(--zy-content, #4B5563);
}

/* ── switch ── */
.form-row {
	flex-direction: row;
	align-items: center;
	justify-content: space-between;
}

.form-row-info {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
}

.form-hint {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.switch {
	width: 88rpx;
	height: 48rpx;
	border-radius: 24rpx;
	background-color: var(--zy-border, #E5E7EB);
	position: relative;
	transition: background-color 0.2s ease;
}

.switch-on {
	background-color: var(--wot-color-theme, #2D9B8F);
}

.switch-knob {
	position: absolute;
	top: 4rpx;
	left: 4rpx;
	width: 40rpx;
	height: 40rpx;
	border-radius: 50%;
	background-color: #FFFFFF;
	transition: transform 0.2s ease;
}

.switch-on .switch-knob {
	transform: translateX(40rpx);
}

/* ── AI 寄语提示 ── */
.ai-hint {
	display: flex;
	align-items: flex-start;
	gap: 20rpx;
	padding: 28rpx;
	border-radius: 24rpx;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	border: 1rpx solid rgba(45, 155, 143, 0.12);
}

.ai-hint-icon-wrap {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background-color: rgba(45, 155, 143, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.ai-hint-info {
	flex: 1;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.ai-hint-title {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

.ai-hint-text {
	font-size: 24rpx;
	color: var(--zy-content, #4B5563);
	line-height: 1.5;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
