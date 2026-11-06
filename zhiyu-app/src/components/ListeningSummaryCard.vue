<template>
	<view class="growth-card summary-card">
		<view class="summary-head">
			<text class="growth-card-title">倾听小结</text>
			<text :class="['summary-badge', { saved }]">{{ saved ? '已保存' : '未保存' }}</text>
		</view>
		<text class="growth-muted">只有你能看到。AI 只读取这段对话里你说过的话；生成和保存分开，可以改完再存。</text>

		<text v-if="loading" class="growth-muted summary-state" role="status" aria-live="polite">正在读取小结…</text>
		<view v-else-if="error" class="summary-error" role="alert">
			<text class="growth-error">{{ error }}</text>
			<AccessibleButton class="growth-button secondary" @click="load">重试</AccessibleButton>
		</view>

		<template v-else>
			<view v-if="safetyHold" class="summary-alert" role="alert">
				<text>{{ summary }}</text>
				<AccessibleButton class="growth-button secondary" @click="support">前往支持页</AccessibleButton>
			</view>

			<template v-else>
				<text v-if="sessionStatus !== 1 && !saved" class="growth-muted summary-state">结束这次倾听后，可以生成小结。</text>
				<textarea
					v-if="summary || sessionStatus === 1"
					v-model="summary"
					class="growth-input summary-input"
					maxlength="300"
					:disabled="busy || sessionStatus !== 1"
					aria-label="倾听小结，最多 300 字"
					placeholder="可以写一句此刻想留下的话（最多 300 字）"
				/>
				<text v-if="summary" class="summary-hint" role="status" aria-live="polite">{{ generated ? 'AI 生成，已通过安全过滤；可以编辑后保存。' : '这是可编辑的记录引导，不代表 AI 已读懂对话。' }}</text>

				<AccessibleButton
					v-if="sessionStatus === 1 && !safetyHold"
					class="summary-consent"
					:class="{ 'is-checked': consent }"
					role="checkbox"
					:aria-checked="consent ? 'true' : 'false'"
					aria-label="同意读取本次已结束对话中的用户发言并发送给 AI"
					@click="consent = !consent"
				>
					<view class="summary-consent-box" aria-hidden="true">{{ consent ? '✓' : '' }}</view>
					<text>同意读取这次已结束对话中我说过的话，用于生成或保存小结</text>
				</AccessibleButton>

				<view v-if="sessionStatus === 1" class="summary-actions">
					<AccessibleButton class="growth-button secondary" :disabled="!consent || busy" @click="generate">{{ busy ? '处理中…' : '请小织生成' }}</AccessibleButton>
					<AccessibleButton class="growth-button" :disabled="!canSave" @click="save">保存小结</AccessibleButton>
					<AccessibleButton v-if="hasSaved" class="growth-button secondary" :disabled="busy" @click="remove">删除小结</AccessibleButton>
				</view>
				<view v-else-if="hasSaved" class="summary-actions">
					<AccessibleButton class="growth-button secondary" :disabled="busy" @click="remove">删除小结</AccessibleButton>
				</view>
			</template>
		</template>

		<text class="summary-foot">本系统不能替代专业心理咨询</text>

		<ActionSheet
			:open="pendingDelete"
			title="删除倾听小结"
			description="只删除这条小结，不删除倾听对话。是否继续？"
			:actions="[{ key: 'confirm', label: '确认删除', tone: 'danger', busy: busy }]"
			cancel-text="再想想"
			:busy="busy"
			@select="confirmRemove"
			@close="pendingDelete = false"
		/>
		<Toast />
	</view>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../utils/request'
import ActionSheet from './ActionSheet.vue'
import Toast from './Toast.vue'
import AccessibleButton from './AccessibleButton.vue'
import { useToast } from '../composables/useToast'

const toast = useToast()

const props = defineProps({
	sessionId: { type: [String, Number], required: true },
	sessionStatus: { type: [String, Number], default: 0 },
})

const loading = ref(true)
const busy = ref(false)
const error = ref('')
const saved = ref(false)
const hasSaved = ref(false)
const savedText = ref('')
const summary = ref('')
const generated = ref(false)
const safetyHold = ref(false)
const consent = ref(false)
const pendingDelete = ref(false)
const canSave = computed(() => consent.value && !safetyHold.value && summary.value.trim().length > 0 && !busy.value)

async function load() {
	loading.value = true
	error.value = ''
	try {
		const data = await request({ url: `/api/emotion/sessions/${props.sessionId}/summary` })
		hasSaved.value = data.saved === true
		saved.value = hasSaved.value
		savedText.value = data.summary || ''
		summary.value = data.summary || ''
		generated.value = data.generated === true
		safetyHold.value = data.safetyHold === true
	} catch (e) {
		error.value = (e && e.message) || '小结暂时无法读取'
	} finally {
		loading.value = false
	}
}

async function generate() {
	if (busy.value || !consent.value || Number(props.sessionStatus) !== 1) return
	busy.value = true
	error.value = ''
	try {
		const data = await request({
			url: `/api/emotion/sessions/${props.sessionId}/summary/generate`,
			method: 'POST',
			data: { consent: true },
		})
		summary.value = data.summary || ''
		generated.value = data.generated === true
		safetyHold.value = data.safetyHold === true
		saved.value = false
	} catch (e) {
		error.value = (e && e.message) || '小结暂时没有生成，请重试。'
	} finally {
		busy.value = false
	}
}

async function save() {
	if (!canSave.value) return
	busy.value = true
	error.value = ''
	try {
		const data = await request({
			url: `/api/emotion/sessions/${props.sessionId}/summary`,
			method: 'PUT',
			data: { summary: summary.value.trim(), generated: generated.value, consent: true },
		})
		saved.value = true
		hasSaved.value = true
		savedText.value = summary.value.trim()
		summary.value = data.summary || summary.value.trim()
		toast.success('小结已保存')
	} catch (e) {
		error.value = (e && e.message) || '保存失败，请重试。'
	} finally {
		busy.value = false
	}
}

function remove() {
	if (busy.value) return
	pendingDelete.value = true
}

async function confirmRemove() {
	if (busy.value) return
	busy.value = true
	error.value = ''
	try {
		await request({ url: `/api/emotion/sessions/${props.sessionId}/summary`, method: 'DELETE' })
		saved.value = false
		hasSaved.value = false
		savedText.value = ''
		summary.value = ''
		generated.value = false
		safetyHold.value = false
		pendingDelete.value = false
		toast.show('小结已删除')
	} catch (e) {
		error.value = (e && e.message) || '删除失败，请重试。'
	} finally {
		busy.value = false
	}
}

function support() {
	uni.navigateTo({ url: '/pages/support/index' })
}

onMounted(load)
watch(() => props.sessionId, load)
watch(summary, (value) => {
	if (hasSaved.value) saved.value = value.trim() === savedText.value
})
</script>

<style scoped>
.summary-card { margin-top: 18rpx; }
.summary-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; }
.summary-badge { flex-shrink: 0; padding: 2rpx 10rpx; border-radius: 8rpx; background: var(--zy-bg, #f5f7f2); color: var(--zy-text, #4a5f54); font-size: 20rpx; }
.summary-badge.saved { background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #217e76); }
.summary-state { display: block; margin-top: 14rpx; }
.summary-error { display: flex; flex-direction: column; align-items: flex-start; gap: 12rpx; margin-top: 14rpx; }
.summary-alert { display: flex; flex-direction: column; align-items: flex-start; gap: 14rpx; margin-top: 14rpx; padding: 16rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 14rpx; background: var(--zy-bg, #f5f7f2); font-size: 24rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.summary-input { display: block; width: 100%; min-height: 160rpx; margin-top: 14rpx; box-sizing: border-box; }
.summary-hint { display: block; margin-top: 8rpx; font-size: 21rpx; line-height: 1.6; color: var(--zy-text, #4a5f54); }
.summary-consent { display: flex; align-items: flex-start; gap: 10rpx; width: 100%; margin: 14rpx 0 0; padding: 12rpx 14rpx; text-align: left; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 12rpx; background: var(--zy-bg, #f5f7f2); color: var(--zy-text, #4a5f54); font-size: 22rpx; line-height: 1.6; }
.summary-consent.is-checked { border-color: var(--zy-primary, #217e76); color: var(--zy-primary, #217e76); }
.summary-consent-box { display: inline-flex; align-items: center; justify-content: center; width: 30rpx; height: 30rpx; flex-shrink: 0; border: 1rpx solid currentColor; border-radius: 8rpx; font-size: 18rpx; }
.summary-actions { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 14rpx; }
.summary-actions .growth-button { margin: 0; }
.summary-foot { display: block; margin-top: 14rpx; font-size: 20rpx; color: var(--zy-text, #4a5f54); }
button:focus-visible { outline: 3px solid var(--zy-brand-700, #1a645e); outline-offset: 3px; }
</style>
