<template>
	<view class="page zy-page-enter">
		<view class="header">
			<text class="page-title">创建小组</text>
		</view>

		<view class="form">
			<!-- 小组名称 -->
			<view class="form-item">
				<text class="form-label">小组名称 <text class="required">*</text></text>
				<wd-input
					v-model="form.name"
					placeholder="给小组起个名字"
					:maxlength="100"
					no-border
					custom-class="form-input-el"
				/>
			</view>

			<!-- 小组描述 -->
			<view class="form-item">
				<text class="form-label">小组描述</text>
				<wd-textarea
					v-model="form.description"
					placeholder="简单介绍一下这个小组"
					:maxlength="255"
					no-border
					custom-class="form-textarea-el"
				/>
			</view>

			<!-- 人数上限 -->
			<view class="form-item">
				<text class="form-label">人数上限</text>
				<wd-input
					v-model="form.maxCount"
					placeholder="默认 50"
					type="number"
					no-border
					custom-class="form-input-el"
				/>
				<text class="form-hint">1 ~ 500，默认 50</text>
			</view>

			<!-- 提交按钮 -->
			<wd-button
				type="primary"
				block
				size="large"
				:disabled="!canSubmit || submitting"
				@click="handleSubmit"
			>{{ submitting ? '提交中…' : '创建小组' }}</wd-button>
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

.form-hint {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
