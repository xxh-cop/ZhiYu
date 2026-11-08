<template>
	<view :class="zyThemeClass" class="page">
		<!-- 顶部品牌区 -->
		<view class="hero zy-slide-up zy-stagger" style="--stagger-i: 0">
			<view class="hero-icon-wrap">
				<wd-icon name="group" size="48rpx" color="#FFFFFF" />
			</view>
			<text class="hero-title">创建互助小组</text>
			<text class="hero-sub">找到志同道合的伙伴，一起成长</text>
		</view>

		<!-- 表单卡片 -->
		<view class="form-card zy-slide-up zy-stagger" style="--stagger-i: 1">
			<view class="form">
				<!-- 小组名称 -->
				<FormField label="小组名称" required :error="errors.name" style="--stagger-i: 2">
					<template #default="{ id }">
						<wd-input
							:id="id"
							v-model="form.name"
							placeholder="给小组起个名字"
							:maxlength="100"
							no-border
							custom-class="form-input-el"
							@input="errors.name = ''"
						/>
					</template>
				</FormField>

				<!-- 小组描述 -->
				<FormField
					label="小组描述"
					optional-text="选填"
					:error="errors.description"
					:maxlength="200"
					:count="form.description.length"
					style="--stagger-i: 3"
				>
					<template #default="{ id }">
						<wd-textarea
							:id="id"
							v-model="form.description"
							placeholder="简单介绍一下这个小组"
							:maxlength="200"
							no-border
							custom-class="form-textarea-el"
							@input="errors.description = ''"
						/>
					</template>
				</FormField>

				<!-- 人数上限 -->
				<FormField
					label="人数上限"
					required
					:error="errors.maxCount"
					hint="1 ~ 500，默认 10"
					style="--stagger-i: 4"
				>
					<template #default="{ id }">
						<wd-input
							:id="id"
							v-model="form.maxCount"
							placeholder="默认 10"
							type="number"
							no-border
							custom-class="form-input-el"
							@input="errors.maxCount = ''"
						/>
					</template>
				</FormField>
			</view>
		</view>

		<!-- 底部按钮区 -->
		<view class="btn-area zy-slide-up zy-stagger" style="--stagger-i: 5">
			<view class="btn-primary" role="button" :tabindex="!canSubmit || submitting ? -1 : 0" :aria-disabled="!canSubmit || submitting" :class="{ 'btn-primary--disabled': !canSubmit || submitting }" @click="handleSubmit" @keydown.enter.prevent="handleSubmit" @keydown.space.prevent="handleSubmit">
				<text class="btn-primary-text">{{ submitting ? '创建中…' : '创建小组' }}</text>
			</view>
			<view class="btn-secondary" role="button" tabindex="0" @click="handleCancel" @keydown.enter.prevent="handleCancel" @keydown.space.prevent="handleCancel">
				<text class="btn-secondary-text">取消</text>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

		<Toast />
	</view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { http } from '../../utils/request'
import FormField from '../../components/FormField.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const form = ref({
	name: '',
	description: '',
	maxCount: '10',
})

const errors = reactive({
	name: '',
	description: '',
	maxCount: '',
})

const submitting = ref(false)

const canSubmit = computed(() => {
	const name = form.value.name.trim()
	if (!name) return false
	if (name.length > 100) return false
	// 描述为选填，不参与提交门槛
	const maxCount = parseInt(form.value.maxCount)
	if (isNaN(maxCount) || maxCount < 1 || maxCount > 500) return false
	return true
})

function validate() {
	errors.name = ''
	errors.description = ''
	errors.maxCount = ''
	let ok = true
	const name = form.value.name.trim()
	if (!name) {
		errors.name = '请输入小组名称'
		ok = false
	} else if (name.length > 100) {
		errors.name = '小组名称不能超过 100 字'
		ok = false
	}
	// 描述为选填（与原产品行为一致），不做必填拦截
	const maxCount = parseInt(form.value.maxCount)
	if (isNaN(maxCount) || maxCount < 1 || maxCount > 500) {
		errors.maxCount = '人数上限须为 1~500 的正整数'
		ok = false
	}
	return ok
}

async function handleSubmit() {
	if (submitting.value) return
	if (!validate()) return

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
		toast.success('创建成功')
		setTimeout(() => {
			uni.navigateBack()
		}, 1500)
	} catch (e) {
		toast.care((e && e.message) || '创建失败')
	} finally {
		submitting.value = false
	}
}

function handleCancel() {
	uni.navigateBack()
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 顶部品牌区 ── */
.hero {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 24rpx 0 40rpx;
}

.hero-icon-wrap {
	width: 96rpx;
	height: 96rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-primary-light, #5bc4a8));
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 20rpx;
	box-shadow: var(--zy-shadow-sm);
}

.hero-title {
	font-family: var(--zy-serif);
	font-size: 40rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.hero-sub {
	margin-top: 8rpx;
	font-size: 26rpx;
	color: var(--zy-aid, #4a5f54);
}

/* ── 表单卡片 ── */
.form-card {
	background-color: var(--zy-card-bg, #FFFFFF);
	border-radius: var(--zy-radius-lg, 32rpx);
	box-shadow: var(--zy-shadow-sm);
	padding: 40rpx 32rpx;
	margin-bottom: 32rpx;
}

.form {
	display: flex;
	flex-direction: column;
	gap: 36rpx;
}

.form-item {
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}

.form-label {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
}

.required {
	color: var(--zy-danger, #a64842);
}

.form-hint {
	font-size: 22rpx;
	color: var(--zy-aid, #4a5f54);
}

.char-hint {
	font-size: 22rpx;
	color: var(--zy-aid, #4a5f54);
	text-align: right;
}

.form-error {
	font-size: 24rpx;
	color: var(--zy-danger, #a64842);
}

/* ── 底部按钮区 ── */
.btn-area {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.btn-primary {
	height: 88rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, #217e76);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-primary:active {
	transform: scale(0.97);
}

.btn-primary--disabled {
	opacity: 0.4;
}

.btn-primary-text {
	font-size: 32rpx;
	font-weight: 500;
	color: #FFFFFF;
	letter-spacing: 2rpx;
}

.btn-secondary {
	height: 80rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.btn-secondary:active {
	background-color: var(--zy-bg, #f5f7f2);
}

.btn-secondary-text {
	font-size: 30rpx;
	color: var(--zy-content, #4a5f54);
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #4a5f54);
	text-align: center;
}

button:focus-visible,
.btn-primary:focus-visible,
.btn-secondary:focus-visible {
	outline: 3px solid var(--zy-brand-700, #1a645e);
	outline-offset: 3px;
}
</style>
