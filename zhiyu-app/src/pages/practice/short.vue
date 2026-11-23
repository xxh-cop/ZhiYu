<template>
	<view :class="zyThemeClass" class="short-page zy-page-enter">
		<view v-if="loading" class="short-loading">
			<view class="orb-skeleton zy-skeleton" aria-hidden="true"></view>
			<text class="loading-text">正在准备短练习…</text>
		</view>

		<view v-else-if="loadError" class="short-status">
			<StateBlock type="error" title="这条短练习没有找到" :description="loadError" action-text="重新读取" @action="load" />
			<AccessibleButton class="text-action" @click="leave">先回去</AccessibleButton>
		</view>

		<template v-else-if="practice">
			<view class="short-hero">
				<text class="eyebrow">{{ practice.minutes }} 分钟 · {{ practiceKindLabel(practice.kind) }}</text>
				<text class="title">{{ practice.title }}</text>
				<text class="subtitle">{{ practice.subtitle }}</text>
			</view>

			<view v-if="!started" class="intro-card">
				<view class="material-band zy-stitch-h" :class="'band-' + practiceFamily" aria-hidden="true">
					<view class="band-motif">
						<view class="band-a"></view>
						<view class="band-b"></view>
					</view>
				</view>
				<text class="intro-lead">练习会在你准备好后开始，共 {{ steps.length }} 步。可以随时暂停，也可以先到这里。</text>
				<view class="step-preview">
					<view v-for="(step, i) in steps" :key="step.order" class="preview-row">
						<text class="preview-index">{{ i + 1 }}</text>
						<view class="preview-copy">
							<text class="preview-title">{{ step.title }}</text>
							<text class="preview-cue">{{ step.cue }}</text>
						</view>
					</view>
				</view>
				<view v-if="practice.materials.length" class="materials-line">
					<text class="materials-label">可选材料</text>
					<text class="materials-text">{{ practice.materials.join(' · ') }}</text>
				</view>
				<text class="safety-note">{{ practice.safety }}</text>
				<AccessibleButton class="primary-action" :disabled="starting" @click="begin">{{ starting ? '正在准备…' : '开始练习' }}</AccessibleButton>
				<text v-if="startError" class="short-error" role="alert">{{ startError }}</text>
			</view>

			<view v-else-if="!done" class="player">
				<view class="progress-track" role="progressbar" :aria-valuenow="Math.round(progress)" aria-valuemin="0" aria-valuemax="100">
					<view class="progress-fill" :style="{ width: progress + '%' }"></view>
				</view>
				<view class="breath-area">
					<view class="breath-orb" :class="{ breathing: running }">
						<text class="orb-text">{{ running ? '跟着自己的节奏' : '已暂停' }}</text>
					</view>
				</view>
				<text class="step-index">第 {{ index + 1 }} / {{ steps.length }} 步</text>
				<text class="step-title" aria-live="polite">{{ currentStep.title }}</text>
				<text class="step-cue">{{ currentStep.cue }}</text>
				<text class="timer" aria-live="off">{{ remainingText }}</text>
				<text v-if="remaining === 0 && !isLast" class="timer-hint">这一步的时间到了，可以进入下一步。</text>
				<view class="player-actions">
					<AccessibleButton class="secondary-action" @click="toggleRun">{{ running ? '暂停' : '继续' }}</AccessibleButton>
					<AccessibleButton v-if="!isLast" class="primary-action" @click="nextStep">下一步</AccessibleButton>
					<AccessibleButton v-else class="primary-action" :disabled="completing" @click="complete">{{ completing ? '记录中…' : '完成练习' }}</AccessibleButton>
				</view>
				<AccessibleButton class="text-action" @click="abandon">先到这里</AccessibleButton>
				<text v-if="sessionError" class="short-error" role="alert">{{ sessionError }}</text>
			</view>

			<view v-else class="done-card">
				<view class="material-band zy-stitch-h" :class="'band-' + practiceFamily" aria-hidden="true">
					<view class="band-motif">
						<view class="band-a"></view>
						<view class="band-b"></view>
					</view>
				</view>
				<view class="done-mark"><ZyIcon name="check" :size="44" tone="brand" /></view>
				<text class="done-title">这一小段，已经完成。</text>
				<text class="done-copy">{{ practice.closing }}</text>
				<text class="done-meta">{{ practice.minutes }} 分钟 · 已记录为一次练习</text>
				<AccessibleButton class="primary-action" @click="chooseAnother">再选一个短练习</AccessibleButton>
				<AccessibleButton class="text-action" @click="leave">回到此刻</AccessibleButton>
			</view>

			<text class="short-disclaimer">本系统不能替代专业心理咨询</text>
		</template>
		<ActionSheet
			:open="showAbandonSheet"
			title="先到这里"
			description="这次练习会记录为未完成，之后可以重新选择一条短练习。"
			:actions="[{ key: 'confirm', label: '先到这里', tone: 'danger' }]"
			cancel-text="继续练习"
			@select="confirmAbandon"
			@close="showAbandonSheet = false"
		/>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import ZyIcon from '../../components/ZyIcon.vue'
import { onLoad, onHide, onUnload } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import ActionSheet from '../../components/ActionSheet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import StateBlock from '../../components/StateBlock.vue'
import {
	createPracticeSession,
	practiceKindLabel,
	transitionPracticeSession,
} from '../../utils/practice-session'

const practiceId = ref('')
const mood = ref('')
const practice = ref(null)
const loading = ref(true)
const loadError = ref('')

const started = ref(false)
const starting = ref(false)
const startError = ref('')
const index = ref(0)
const remaining = ref(0)
const running = ref(false)
const done = ref(false)
const completing = ref(false)
const sessionError = ref('')
const sessionId = ref(null)
const sessionStatus = ref(0)

let timer = null
let remainingEndAt = 0

const steps = computed(() => practice.value?.steps || [])
const currentStep = computed(() => steps.value[index.value] || { title: '', cue: '', seconds: 0 })

/* 短练习接口只下发 kind/patternId（不含 family）；HERITAGE 时按纹样目录编号段
   （9711 针线 / 9712 编织 / 9713 剪刻 / 9714 器面，见 catalog/heritage-catalog.json）
   推导材质带，取不到则回落针线。 */
const FAMILY_BY_PATTERN_PREFIX = { 9711: 'stitch', 9712: 'weave', 9713: 'cutout', 9714: 'porcelain' }
const practiceFamily = computed(() => {
	const data = practice.value
	if (!data || data.kind !== 'HERITAGE') return 'stitch'
	if (data.family) return data.family
	const prefix = String(data.patternId || '').slice(0, 4)
	return FAMILY_BY_PATTERN_PREFIX[prefix] || 'stitch'
})
const isLast = computed(() => steps.value.length > 0 && index.value >= steps.value.length - 1)
const progress = computed(() => {
	const total = steps.value.length
	if (!total) return 0
	const current = currentStep.value
	const within = current.seconds ? (current.seconds - remaining.value) / current.seconds : 1
	return Math.min(100, ((index.value + Math.max(0, Math.min(1, within))) / total) * 100)
})
const remainingText = computed(() => {
	const value = Math.max(0, Math.round(remaining.value))
	return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`
})

onLoad((query) => {
	practiceId.value = query?.id || ''
	mood.value = query?.mood || ''
	load()
})

onHide(() => pauseOnLeave())
onUnload(() => pauseOnLeave())

async function load() {
	if (!practiceId.value) {
		loading.value = false
		loadError.value = '没有找到这条短练习，请重新选择。'
		return
	}
	loading.value = true
	loadError.value = ''
	try {
		practice.value = await request({
			url: '/api/practice/short-practices/' + encodeURIComponent(practiceId.value),
		})
		index.value = 0
		remaining.value = steps.value[0]?.seconds || 0
	} catch (e) {
		practice.value = null
		loadError.value = e?.code === 1004 ? '这条短练习不存在，可能已经调整。' : (e?.message || '短练习读取失败，请重试')
	} finally {
		loading.value = false
	}
}

function beginTimer() {
	stopTimer()
	remainingEndAt = Date.now() + remaining.value * 1000
	timer = setInterval(() => {
		remaining.value = Math.max(0, Math.ceil((remainingEndAt - Date.now()) / 1000))
		if (remaining.value <= 0) {
			stopTimer()
			running.value = false
		}
	}, 250)
}

function stopTimer() {
	if (timer) clearInterval(timer)
	timer = null
}

async function begin() {
	if (starting.value || !practice.value) return
	starting.value = true
	startError.value = ''
	try {
		if (!sessionId.value) {
			const session = await createPracticeSession({
				kind: practice.value.kind,
				minutesBudget: practice.value.minutes,
				moodCode: mood.value || undefined,
				heritagePatternId: practice.value.patternId || undefined,
			})
			sessionId.value = session.practiceSessionId
			sessionStatus.value = Number(session.status)
			const startedSession = await transitionPracticeSession(sessionId.value, 'START')
			sessionStatus.value = Number(startedSession.status)
		}
		started.value = true
		index.value = 0
		remaining.value = steps.value[0]?.seconds || 0
		running.value = true
		beginTimer()
	} catch (e) {
		startError.value = e?.message || '练习暂时没有准备好，请重试'
	} finally {
		starting.value = false
	}
}

async function toggleRun() {
	if (!started.value) {
		await begin()
		return
	}
	if (completing.value) return
	if (running.value) {
		stopTimer()
		running.value = false
		await syncSession('PAUSE')
		return
	}
	running.value = true
	beginTimer()
	await syncSession(sessionStatus.value === 4 ? 'RESUME' : null)
}

function nextStep() {
	if (isLast.value) return
	index.value += 1
	remaining.value = steps.value[index.value]?.seconds || 0
	if (running.value) beginTimer()
}

async function complete() {
	if (!sessionId.value || completing.value || done.value) return
	completing.value = true
	sessionError.value = ''
	stopTimer()
	running.value = false
	try {
		if (sessionStatus.value === 4) {
			const resumed = await transitionPracticeSession(sessionId.value, 'RESUME')
			sessionStatus.value = Number(resumed.status)
		}
		const finished = await transitionPracticeSession(sessionId.value, 'COMPLETE')
		sessionStatus.value = Number(finished.status)
		done.value = true
	} catch (e) {
		sessionError.value = e?.message || '完成状态记录失败，可以再试一次'
	} finally {
		completing.value = false
	}
}

async function syncSession(action) {
	if (!action || !sessionId.value || done.value) return
	try {
		const session = await transitionPracticeSession(sessionId.value, action)
		sessionStatus.value = Number(session.status)
		sessionError.value = ''
	} catch (e) {
		sessionError.value = e?.message || '练习状态同步失败，可以继续跟着页面完成'
	}
}

const showAbandonSheet = ref(false)

function abandon() {
	if (!sessionId.value) {
		leave()
		return
	}
	showAbandonSheet.value = true
}

async function confirmAbandon() {
	showAbandonSheet.value = false
	if (!sessionId.value) return
	stopTimer()
	running.value = false
	try {
		await transitionPracticeSession(sessionId.value, 'ABANDON')
	} catch (e) {
		/* 已结束或已放弃时无需额外处理 */
	}
	sessionId.value = null
	leave()
}

function releaseSession() {
	if (!sessionId.value || done.value) return
	transitionPracticeSession(sessionId.value, started.value ? 'PAUSE' : 'ABANDON').catch(() => {})
}

function pauseOnLeave() {
	stopTimer()
	running.value = false
	releaseSession()
}

function chooseAnother() {
	leave()
}

function leave() {
	stopTimer()
	running.value = false
	releaseSession()
	uni.navigateBack({
		fail: () => uni.reLaunch({ url: '/pages/start/index' }),
	})
}
</script>

<style scoped>
.short-page {
	min-height: 100vh;
	padding: 32rpx 32rpx 80rpx;
	box-sizing: border-box;
	background: var(--zy-bg, #f1f4f1);
	color: var(--zy-text, #42524c);
}
.short-hero { margin: 30rpx 0 24rpx; }
.eyebrow { display: block; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 2px; color: var(--zy-muted, #6e7b75); }
.title { display: block; margin-top: 12rpx; font-family: var(--zy-font-display, serif); font-size:56rpx; font-weight: 500; line-height: 1.35; color: var(--zy-title, #111816); }
.subtitle { display: block; margin-top: 10rpx; font-size:var(--zy-text-caption, 24rpx); line-height: 1.7; color: var(--zy-muted, #6e7b75); }
.intro-card,
.player,
.done-card { padding: 26rpx 0; border: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; box-shadow: none; }

/* 材质带（气息深化·阶段二）：HERITAGE 按纹样族，其余回落针线；纯 CSS 只用令牌 */
.material-band { display: flex; align-items: center; justify-content: center; min-height: 72rpx; margin-bottom: 20rpx; border-radius: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-bottom: 1rpx solid var(--zy-border, #d3ded8); background: transparent; }
.band-motif { position: relative; width: 48rpx; height: 48rpx; }
.band-stitch .band-motif { width: 168rpx; height: 24rpx; }
.band-a,
.band-b { position: absolute; }
.band-weave .band-a { left: 0; right: 0; top: 16rpx; height: 6rpx; background: var(--zy-brand-600); }
.band-weave .band-b { top: 0; bottom: 0; left: 21rpx; width: 6rpx; background: var(--zy-care, #a64842); }
.band-stitch .band-a { left: 0; right: 0; top: 11rpx; border-top: 2rpx dashed var(--zy-care, #a64842); }
.band-stitch .band-b { display: none; }
.band-cutout .band-a { top: 2rpx; right: 2rpx; bottom: 2rpx; left: 2rpx; background: var(--zy-care, #a64842); }
.band-cutout .band-b { left: 16rpx; top: 16rpx; width: 16rpx; height: 16rpx; background: var(--zy-card-bg); border-radius: 50%; }
.band-porcelain .band-a { top: 2rpx; right: 2rpx; bottom: 2rpx; left: 2rpx; border: 4rpx solid var(--zy-brand-600); border-radius: 50%; }
.band-porcelain .band-b { left: 15rpx; top: 15rpx; width: 18rpx; height: 18rpx; border: 3rpx solid var(--zy-care, #a64842); border-radius: 50%; }
.intro-lead { display: block; font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-content, #4a5f54); }
.step-preview { margin: 22rpx 0; }
.preview-row { display: flex; gap: 18rpx; padding: 16rpx 0; border-bottom: 1rpx solid var(--zy-border, #dfe7dc); }
.preview-row:last-child { border-bottom: 0; }
.preview-index { flex-shrink: 0; width: 44rpx; height: 44rpx; border-radius: 0; background: transparent; border: 1rpx solid var(--zy-border, #d3ded8); color: var(--zy-brand-700, #0a403b); font-size:var(--zy-text-micro, 22rpx); line-height: 42rpx; text-align: center; }
.preview-copy { flex: 1; min-width: 0; }
.preview-title { display: block; font-family: var(--zy-font-display, serif); font-size:28rpx; font-weight: 500; color: var(--zy-title, #233d34); }
.preview-cue { display: block; margin-top: 6rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.7; color: var(--zy-sub, #73857b); }
.materials-line { display: flex; gap: 12rpx; flex-wrap: wrap; margin-bottom: 16rpx; font-size:var(--zy-text-micro, 22rpx); }
.materials-label { color: var(--zy-care, #a64842); }
.materials-text { color: var(--zy-content, #4a5f54); }
.safety-note { display: block; margin-bottom: 22rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.7; color: var(--zy-sub, #73857b); }
.primary-action,
.secondary-action { min-height: 88rpx; margin-top: 14rpx; border-radius: 0; font-size:var(--zy-text-body, 28rpx); line-height: 2.9; }
.primary-action { background: var(--zy-brand-600, #0e5b53); color: #fff; }
.primary-action.is-disabled { opacity: 0.55; }
.secondary-action { border: 1rpx solid var(--zy-border, #d3ded8); background: transparent; color: var(--zy-brand-700, #0a403b); }
.primary-action::after,
.secondary-action::after { border: 0; }
.text-action { margin-top: 14rpx; background: transparent; color: var(--zy-sub, #73857b); font-size:var(--zy-text-caption, 24rpx); line-height: 2.4; }
.text-action::after { border: 0; }
.short-error { display: block; margin-top: 12rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-care, var(--zy-care, #a64842)); }
.progress-track { height: 2rpx; overflow: hidden; border-radius: 0; background: var(--zy-border, #d3ded8); }
.progress-fill { height: 100%; border-radius: 0; background: var(--zy-brand-600, #0e5b53); transition: width 0.4s ease; }
.breath-area { display: flex; justify-content: center; padding: 28rpx 0 18rpx; }
.breath-orb { width: 280rpx; height: 280rpx; border-radius: 50%; border: 1rpx solid var(--zy-border, #d3ded8); background: #f7f9f7; display: flex; align-items: center; justify-content: center; box-shadow: none; transition: transform 0.8s ease; }
.breath-orb.breathing { animation: short-breathe 10s ease-in-out infinite; }
.orb-text { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); }
.step-index { display: block; text-align: center; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 2rpx; color: var(--zy-sub, #73857b); }
.step-title { display: block; margin-top: 10rpx; text-align: center; font-family: var(--zy-font-display, serif); font-size:44rpx; font-weight: 500; color: var(--zy-title, #111816); }
.step-cue { display: block; margin: 16rpx auto 0; max-width: 600rpx; text-align: center; font-size:var(--zy-text-caption, 24rpx); line-height: 1.9; color: var(--zy-content, #4a5f54); }
.timer { display: block; margin-top: 18rpx; text-align: center; font-family: var(--zy-sans); font-size:var(--zy-text-title-lg, 40rpx); font-variant-numeric: tabular-nums; color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); }
.timer-hint { display: block; margin-top: 6rpx; text-align: center; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-sub, #73857b); }
.player-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; margin-top: 14rpx; }
.player-actions .primary-action,
.player-actions .secondary-action { margin-top: 0; }
.done-card { text-align: center; padding-top: 44rpx; }
.done-mark { display: flex; align-items: center; justify-content: center; width: 96rpx; height: 96rpx; margin: 0 auto 18rpx; border-radius: 0; background: transparent; border: 1rpx solid var(--zy-border, #d3ded8); color: var(--zy-brand-700, #0a403b); font-size:var(--zy-text-title-lg, 40rpx); line-height: 94rpx; }
.done-title { display: block; font-family: var(--zy-font-display, serif); font-size:40rpx; color: var(--zy-title, #111816); }
.done-copy { display: block; margin: 14rpx auto 0; max-width: 560rpx; font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-content, #4a5f54); }
.done-meta { display: block; margin: 16rpx 0 10rpx; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-sub, #73857b); }
.short-loading { display: flex; flex-direction: column; align-items: center; padding: 120rpx 0; gap: 18rpx; }
.orb-skeleton { width: 260rpx; height: 260rpx; border-radius: 50%; }
.loading-text { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-sub, #73857b); }
.short-status { display: flex; flex-direction: column; gap: 14rpx; padding: 60rpx 28rpx; text-align: center; }
.status-text { font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-content, #4a5f54); }
.short-disclaimer { display: block; margin-top: 34rpx; text-align: center; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-sub, #73857b); }
@keyframes short-breathe {
	0%, 100% { transform: scale(0.88); }
	50% { transform: scale(1.04); }
}
@media (max-width: 360px) {
	.short-page { padding-left: 24rpx; padding-right: 24rpx; }
	.breath-orb { width: 240rpx; height: 240rpx; }
}
@media (prefers-reduced-motion: reduce) {
	.breath-orb.breathing { animation: none; }
	.progress-fill { transition: none; }
}
button:focus-visible { outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e)); outline-offset: 3px; }
</style>
