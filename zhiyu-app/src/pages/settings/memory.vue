<template>
	<view class="growth-page memory-page" :class="[{ 'motion-reduced': reducedMotion }, zyThemeClass]">
		<view class="growth-hero">
			<text class="growth-eyebrow">A LITTLE MEMORY</text>
			<text class="growth-title">小织记忆</text>
			<text class="growth-subtitle">只记住你愿意留下的练习偏好，不记住对话。</text>
		</view>

		<view v-if="error" class="growth-error" role="alert">
			{{ error }}
			<AccessibleButton class="growth-button secondary" @click="load">重试</AccessibleButton>
		</view>
		<text v-if="loading" class="growth-muted">正在读取记忆设置…</text>

		<template v-else>
			<view class="growth-card">
				<view class="memory-head">
					<view class="memory-head-copy">
						<text class="growth-card-title">让小织记住偏好</text>
						<text class="growth-muted">默认关闭；开启后只用于练习推荐。</text>
					</view>
					<AccessibleButton
						class="memory-switch"
						:class="{ on: enabled }"
						role="switch"
						:aria-checked="enabled ? 'true' : 'false'"
						@click="toggleSwitch"
					>{{ enabled ? '已开启' : '已关闭' }}</AccessibleButton>
				</view>
				<view class="memory-facts">
					<text>只保存 3 个偏好键</text>
					<text>不保存对话原文、情绪细节或高危信息</text>
					<text>180 天后自动失效</text>
					<text>可随时查看和删除</text>
				</view>
			</view>

			<view v-if="!enabled" class="growth-card">
				<text class="growth-card-title">开启前，请确认</text>
				<AccessibleButton
					class="memory-consent"
					:class="{ 'is-checked': consent }"
					role="checkbox"
					:aria-checked="consent ? 'true' : 'false'"
					aria-label="同意小织保存练习偏好，不保存对话原文"
					@click="consent = !consent"
				>
					<view class="memory-check" aria-hidden="true">{{ consent ? '✓' : '' }}</view>
					<text>我同意小织保存我选择的练习偏好，用于推荐更合适的练习；不保存对话原文。</text>
				</AccessibleButton>
				<AccessibleButton class="growth-button" :disabled="!consent || busy" @click="enableMemory">
					{{ busy ? '正在开启…' : '开启小织记忆' }}
				</AccessibleButton>
			</view>

			<template v-else>
				<view class="growth-card">
					<text class="growth-card-title">常用练习时长</text>
					<text class="growth-muted">下次推荐会优先考虑这个时间预算。</text>
					<view class="memory-options">
						<AccessibleButton
							v-for="minutes in minutesOptions"
							:key="minutes"
							:class="{ selected: preferences.preferred_minutes === String(minutes) }"
							:aria-pressed="preferences.preferred_minutes === String(minutes)"
							@click="setPreference('preferred_minutes', String(minutes))"
						>{{ minutes }} 分钟</AccessibleButton>
					</view>
				</view>

				<view class="growth-card">
					<text class="growth-card-title">偏好材料</text>
					<text class="growth-muted">只是推荐参考，不影响你已经开始的练习。</text>
					<view class="memory-options">
						<AccessibleButton
							v-for="item in materialOptions"
							:key="item.code"
							:class="{ selected: preferences.preferred_material === item.code }"
							:aria-pressed="preferences.preferred_material === item.code"
							@click="setPreference('preferred_material', item.code)"
						>{{ item.label }}</AccessibleButton>
					</view>
				</view>

				<view class="growth-card">
					<text class="growth-card-title">偏好纹样族</text>
					<text class="growth-muted">选择你更想先看的纹样结构。</text>
					<view class="memory-options">
						<AccessibleButton
							v-for="item in familyOptions"
							:key="item.code"
							:class="{ selected: preferences.preferred_pattern_family === item.code }"
							:aria-pressed="preferences.preferred_pattern_family === item.code"
							@click="setPreference('preferred_pattern_family', item.code)"
						>{{ item.label }}</AccessibleButton>
					</view>
				</view>

				<view class="growth-card memory-actions">
					<AccessibleButton class="growth-button" :disabled="busy || !hasPreferences" @click="savePreferences">
						{{ busy ? '正在保存…' : '保存偏好' }}
					</AccessibleButton>
					<AccessibleButton class="growth-button secondary" :disabled="busy" @click="disableMemory">关闭记忆</AccessibleButton>
					<AccessibleButton class="growth-button danger" :disabled="busy" @click="deleteAll">删除全部记忆</AccessibleButton>
				</view>
			</template>

			<text v-if="status" class="memory-status" role="status" aria-live="polite">{{ status }}</text>
			<text class="growth-foot">本系统不能替代专业心理咨询</text>
		</template>
		<ActionSheet
			:open="showDeleteSheet"
			title="删除全部小织记忆"
			description="只删除偏好记忆，不删除练习会话或作品。是否继续？"
			:actions="[{ key: 'confirm', label: '删除全部记忆', tone: 'danger' }]"
			cancel-text="再想想"
			@select="confirmDeleteAll"
			@close="showDeleteSheet = false"
		/>
		<Toast />
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useInteraction } from '../../composables/useInteraction'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()
const { reducedMotion } = useInteraction()
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const status = ref('')
const enabled = ref(false)
const consent = ref(false)
const preferences = ref({
	preferred_minutes: '',
	preferred_material: '',
	preferred_pattern_family: '',
})

const minutesOptions = [10, 15, 20, 30]
const materialOptions = [
	{ code: 'bamboo', label: '竹' },
	{ code: 'thread', label: '线' },
	{ code: 'paper', label: '纸' },
	{ code: 'porcelain', label: '瓷' },
]
const familyOptions = [
	{ code: 'stitch', label: '针线' },
	{ code: 'weave', label: '编织' },
	{ code: 'cutout', label: '剪刻' },
	{ code: 'porcelain', label: '器面' },
]
const hasPreferences = computed(() => Object.values(preferences.value).some(Boolean))

onShow(load)

async function load() {
	loading.value = true
	error.value = ''
	try {
		const data = await request({ url: '/api/companion/memory', method: 'GET' })
		apply(data)
	} catch (e) {
		error.value = e.message || '记忆设置暂时无法读取'
	} finally {
		loading.value = false
	}
}

function apply(data) {
	enabled.value = !!(data && data.enabled)
	const next = { preferred_minutes: '', preferred_material: '', preferred_pattern_family: '' }
	for (const item of (data && data.items) || []) {
		if (item && item.key in next) next[item.key] = item.value || ''
	}
	preferences.value = next
	if (!enabled.value) consent.value = false
}

async function toggleSwitch() {
	if (busy.value) return
	if (enabled.value) {
		await disableMemory()
	} else {
		status.value = '勾选下方授权后即可开启。'
	}
}

async function enableMemory() {
	if (busy.value) return
	if (!consent.value) {
		status.value = '请先勾选授权。'
		return
	}
	busy.value = true
	status.value = ''
	try {
		const data = await request({
			url: '/api/companion/memory/enabled',
			method: 'PUT',
			data: { enabled: true, consent: true },
		})
		apply(data)
		status.value = '小织记忆已开启，只保存你选择的偏好。'
	} catch (e) {
		status.value = e.message || '开启失败，请重试'
	} finally {
		busy.value = false
	}
}

async function disableMemory() {
	if (busy.value) return
	busy.value = true
	status.value = ''
	try {
		const data = await request({
			url: '/api/companion/memory/enabled',
			method: 'PUT',
			data: { enabled: false },
		})
		apply(data)
		status.value = '小织记忆已关闭；已有偏好不会被用于推荐。'
	} catch (e) {
		status.value = e.message || '关闭失败，请重试'
	} finally {
		busy.value = false
	}
}

function setPreference(key, value) {
	preferences.value = {
		...preferences.value,
		[key]: preferences.value[key] === value ? '' : value,
	}
}

async function savePreferences() {
	if (busy.value || !hasPreferences.value) return
	busy.value = true
	status.value = ''
	try {
		const payload = {}
		for (const [key, value] of Object.entries(preferences.value)) {
			if (value) payload[key] = value
		}
		const data = await request({
			url: '/api/companion/memory',
			method: 'PUT',
			data: { consent: true, preferences: payload },
		})
		apply(data)
		status.value = '偏好已保存，180 天后自动失效。'
	} catch (e) {
		status.value = e.message || '保存失败，请重试'
	} finally {
		busy.value = false
	}
}

const showDeleteSheet = ref(false)

function deleteAll() {
	if (busy.value) return
	showDeleteSheet.value = true
}

async function confirmDeleteAll() {
	showDeleteSheet.value = false
	if (busy.value) return
	busy.value = true
	status.value = ''
	try {
		const data = await request({ url: '/api/companion/memory', method: 'DELETE' })
		apply(data)
		toast.success('小织记忆已删除并关闭。')
	} catch (e) {
		status.value = e.message || '删除失败，请重试'
	} finally {
		busy.value = false
	}
}
</script>

<style src="../../styles/growth.css"></style>
<style scoped>
.memory-page {
	max-width: 820px;
	margin: 0 auto;
}
.memory-head {
	display: flex;
	flex-wrap: wrap;
	align-items: flex-start;
	justify-content: space-between;
	gap: 12rpx 18rpx;
}
.memory-head-copy {
	flex: 1 1 320rpx;
	min-width: 0;
}
.memory-head .growth-muted {
	display: block;
	margin-top: 6rpx;
}
.memory-switch {
	flex: 0 0 auto;
	min-height: 44px;
	margin: 0;
	padding: 0 24rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 999rpx;
	background: var(--zy-bg, #f5f7f2);
	color: var(--zy-content, #4a5f54);
	font-size: 23rpx;
	line-height: 44px;
}
.memory-switch.on {
	border-color: var(--zy-primary, #217e76);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, #217e76);
}
.memory-facts {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 8rpx 16rpx;
	margin-top: 18rpx;
	padding-top: 16rpx;
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}
.memory-facts text {
	font-size: 21rpx;
	line-height: 1.7;
	color: var(--zy-content, #4a5f54);
}
.memory-consent {
	display: flex;
	align-items: flex-start;
	gap: 12rpx;
	width: 100%;
	margin: 16rpx 0;
	padding: 14rpx 16rpx;
	text-align: left;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 14rpx;
	background: var(--zy-bg, #f5f7f2);
	color: var(--zy-content, #4a5f54);
	font-size: 23rpx;
	line-height: 1.6;
}
.memory-consent.is-checked {
	border-color: var(--zy-primary, #217e76);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, #217e76);
}
.memory-check {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 34rpx;
	height: 34rpx;
	flex-shrink: 0;
	margin-top: 2rpx;
	border: 1rpx solid currentColor;
	border-radius: 8rpx;
	font-size: 20rpx;
}
.memory-options {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 10rpx;
	margin-top: 16rpx;
}
.memory-options button {
	min-width: 0;
	min-height: 44px;
	margin: 0;
	padding: 0 6rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 12rpx;
	background: var(--zy-card-bg, #fff);
	color: var(--zy-content, #4a5f54);
	font-size: 22rpx;
	line-height: 44px;
}
.memory-options button.selected {
	border-color: var(--zy-primary, #217e76);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, #217e76);
}
.memory-actions {
	display: flex;
	flex-direction: column;
	gap: 14rpx;
}
.memory-status {
	display: block;
	margin: 16rpx 0 0;
	font-size: 22rpx;
	line-height: 1.7;
	color: var(--zy-brand-700, #217e76);
}
@media (max-width: 420px) {
	.memory-facts {
		grid-template-columns: minmax(0, 1fr);
	}
	.memory-options {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}
}
</style>
