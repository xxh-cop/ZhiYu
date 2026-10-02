<template>
	<view class="page">
		<view class="header">
			<text class="page-title">创建小组</text>
		</view>

		<view class="form">
			<!-- 小组名称 -->
			<view class="form-item">
				<text class="form-label">小组名称 <text class="required">*</text></text>
				<input
					class="form-input"
					type="text"
					v-model="form.name"
					placeholder="给小组起个名字"
					placeholder-class="input-placeholder"
					:maxlength="100"
				/>
			</view>

			<!-- 小组描述 -->
			<view class="form-item">
				<text class="form-label">小组描述</text>
				<textarea
					class="form-textarea"
					v-model="form.description"
					placeholder="简单介绍一下这个小组"
					placeholder-class="input-placeholder"
					:maxlength="255"
				/>
			</view>

			<!-- 人数上限 -->
			<view class="form-item">
				<text class="form-label">人数上限</text>
				<input
					class="form-input"
					type="number"
					v-model="form.maxCount"
					placeholder="默认 50"
					placeholder-class="input-placeholder"
				/>
				<text class="form-hint">1 ~ 500，默认 50</text>
			</view>

			<!-- 提交按钮 -->
			<view
				class="submit-btn"
				:class="{ 'btn-disabled': !canSubmit || submitting }"
				@click="handleSubmit"
			>
				<text class="submit-text">{{ submitting ? '提交中...' : '创建小组' }}</text>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { http } from '../../utils/request'

const form = ref({
	name: '',
	description: '',
	maxCount: '50',
})

const submitting = ref(false)

const canSubmit = computed(() => form.value.name.trim().length > 0)

async function handleSubmit() {
	if (!canSubmit.value || submitting.value) return

	submitting.value = true
	try {
		const body = {
			name: form.value.name.trim(),
			description: form.value.description.trim() || null,
		}
		const maxCount = parseInt(form.value.maxCount)
		if (!isNaN(maxCount) && maxCount >= 1 && maxCount <= 500) {
			body.maxCount = maxCount
		}

		await http.post('/api/groups', body)
		uni.showToast({ title: '创建成功', icon: 'success' })
		setTimeout(() => {
			uni.navigateBack()
		}, 1500)
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '创建失败', icon: 'none' })
	} finally {
		submitting.value = false
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 80rpx;
	box-sizing: border-box;
	background-color: #f7f4ef;
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
	font-weight: 600;
	color: #3d3a34;
}

.required {
	color: #b85450;
}

.form-input {
	height: 80rpx;
	padding: 0 20rpx;
	border: 2rpx solid #e5ddd0;
	border-radius: 8px;
	background-color: #fffdfa;
	font-size: 28rpx;
	color: #2b2b2b;
}

.form-textarea {
	min-height: 160rpx;
	padding: 20rpx;
	border: 2rpx solid #e5ddd0;
	border-radius: 8px;
	background-color: #fffdfa;
	font-size: 28rpx;
	color: #2b2b2b;
	box-sizing: border-box;
}

.input-placeholder {
	color: #a49c90;
}

.form-hint {
	font-size: 22rpx;
	color: #bdb2a1;
}

.submit-btn {
	height: 88rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 8px;
	background-color: #8b6f47;
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
