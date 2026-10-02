<template>
	<view class="page zy-page-enter">
		<view class="header">
			<text class="page-title">发布作品</text>
		</view>

		<view class="form">
			<!-- 作品标题 -->
			<view class="form-item">
				<text class="form-label">作品标题 <text class="required">*</text></text>
				<input
					class="form-input"
					type="text"
					v-model="form.title"
					placeholder="给作品起个名字"
					placeholder-class="input-placeholder"
					:maxlength="100"
				/>
			</view>

			<!-- 可见性 -->
			<view class="form-item">
				<text class="form-label">谁可以看到</text>
				<view class="radio-group">
					<view
						class="radio-item zy-clickable"
						:class="{ 'radio-active': form.visible === 1 }"
						@click="form.visible = 1"
					>
						<text class="radio-text">{{ form.visible === 1 ? '◉' : '○' }}</text>
						<text class="radio-label">公开</text>
					</view>
					<view
						class="radio-item zy-clickable"
						:class="{ 'radio-active': form.visible === 0 }"
						@click="form.visible = 0"
					>
						<text class="radio-text">{{ form.visible === 0 ? '◉' : '○' }}</text>
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
					class="switch zy-clickable"
					:class="{ 'switch-on': form.anonymous }"
					@click="form.anonymous = !form.anonymous"
				>
					<view class="switch-knob"></view>
				</view>
			</view>

			<!-- AI 寄语提示 -->
			<view class="form-item">
				<view class="ai-hint zy-card">
					<text class="ai-hint-icon">✨</text>
					<view class="ai-hint-info">
						<text class="ai-hint-title">AI 寄语</text>
						<text class="ai-hint-text">作品发布后，AI 会根据你的创作过程自动生成一句温暖寄语</text>
					</view>
				</view>
			</view>

			<!-- 提交按钮 -->
			<view
				class="submit-btn zy-btn-primary"
				:class="{ 'btn-disabled': !canSubmit || submitting }"
				@click="handleSubmit"
			>
				<text class="submit-text">{{ submitting ? '发布中...' : '发布作品' }}</text>
			</view>
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
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
}

.header {
	margin-bottom: 32rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 600;
	color: #3d3a34;
}

.form {
	display: flex;
	flex-direction: column;
	gap: 32rpx;
}

.form-item {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}

.form-label {
	font-size: 28rpx;
	font-weight: 500;
	color: #3d3a34;
}

.required {
	color: #b85450;
}

.form-input {
	height: 80rpx;
	padding: 0 20rpx;
	border: 1px solid rgba(139, 111, 71, 0.08);
	border-radius: 12px;
	background-color: rgba(255, 255, 255, 0.85);
	font-size: 28rpx;
	color: #2b2b2b;
}

.input-placeholder {
	color: #a49c90;
}

/* radio */
.radio-group {
	display: flex;
	gap: 24rpx;
}

.radio-item {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 16rpx 32rpx;
	border: 1px solid rgba(139, 111, 71, 0.08);
	border-radius: 12px;
	background-color: rgba(255, 255, 255, 0.85);
}

.radio-active {
	border-color: #8b6f47;
	background-color: rgba(139, 111, 71, 0.04);
}

.radio-text {
	font-size: 32rpx;
	color: #8b6f47;
}

.radio-label {
	font-size: 28rpx;
	color: #3d3a34;
}

/* switch row */
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
	color: #9c8f7d;
}

.switch {
	width: 88rpx;
	height: 48rpx;
	border-radius: 24rpx;
	background-color: #e0d5c5;
	position: relative;
	transition: background-color 0.2s ease;
}

.switch-on {
	background-color: #8b6f47;
}

.switch-knob {
	position: absolute;
	top: 4rpx;
	left: 4rpx;
	width: 40rpx;
	height: 40rpx;
	border-radius: 50%;
	background-color: #fffdfa;
	transition: transform 0.2s ease;
}

.switch-on .switch-knob {
	transform: translateX(40rpx);
}

/* AI 寄语提示 */
.ai-hint {
	display: flex;
	align-items: flex-start;
	gap: 16rpx;
	padding: 24rpx;
}

.ai-hint-icon {
	font-size: 36rpx;
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
	color: #3d3a34;
}

.ai-hint-text {
	font-size: 24rpx;
	color: #9c8f7d;
	line-height: 1.5;
}

.submit-btn {
	height: 88rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-top: 16rpx;
}

.btn-disabled {
	opacity: 0.4;
}

.submit-text {
	font-size: 30rpx;
	color: #fffdfa;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 24rpx;
	color: #bdb2a1;
	text-align: center;
}
</style>
