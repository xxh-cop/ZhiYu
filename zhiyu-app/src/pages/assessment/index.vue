<template>
	<view class="check-page">
		<view class="check-top">
			<AccessibleButton class="back-btn" aria-label="返回上一页" @click="goBack">←</AccessibleButton>
			<text class="top-index">自我了解 / SELF-CHECK</text>
		</view>

		<view class="check-head">
			<view class="head-rule" aria-hidden="true"></view>
			<text class="check-title zy-t-serif">不给自己打分，<br/>只看看此刻。</text>
			<text class="check-sub">五个维度，把模糊的感受放回身体里。</text>
			<text class="check-sub check-sub-second">不是量表，不诊断，也不打分。</text>
			<text class="scale-hint">每一行五格，从「很少」到「很多」；没有标准答案。</text>
		</view>

		<view class="dim-list">
			<view v-for="(d, i) in DIMENSIONS" :key="d.key" class="dim-row">
				<text class="dim-index">{{ String(i + 1).padStart(2, '0') }}</text>
				<view class="dim-main">
					<view class="dim-copy">
						<text class="dim-name">{{ d.name }}</text>
						<text class="dim-q">{{ d.question }}</text>
					</view>
					<view class="dim-scale" role="group" :aria-label="d.name + '状态'">
						<button
							v-for="n in 5"
							:key="n"
							class="dim-dot"
							:class="{ 'is-on': values[d.key] >= n, 'is-selected': values[d.key] === n }"
							:aria-label="d.name + '：' + LABELS[n - 1]"
							:aria-pressed="values[d.key] === n"
							@click="setValue(d.key, n)"
						><text class="dim-dot-num">{{ n }}</text></button>
					</view>
					<text class="dim-value">{{ values[d.key] ? LABELS[values[d.key] - 1] : '未选' }}</text>
				</view>
			</view>
		</view>

		<view v-if="complete" class="summary">
			<view class="summary-line" aria-hidden="true"></view>
			<text class="summary-kicker">这一刻的状态</text>
			<text class="summary-text zy-t-serif">{{ summary }}</text>
			<text class="summary-note">这只是一次自我整理，不是评分或诊断。若困扰持续或影响生活，请联系专业支持。</text>
		</view>

		<view class="check-actions">
			<AccessibleButton class="save-btn" variant="primary" :disabled="!complete" @click="saveLocal">记下这一刻</AccessibleButton>
			<AccessibleButton class="journey-link" variant="ghost" @click="goJourney">看看我的成长旅程 →</AccessibleButton>
		</view>

		<view class="support-card">
			<text class="support-text">如果此刻有些撑不住，可以直接找到支持渠道。</text>
			<AccessibleButton class="support-link" variant="ghost" @click="goCrisis">我需要支持 →</AccessibleButton>
		</view>

		<text class="check-foot">本系统不能替代专业心理咨询</text>
		<Toast />
	</view>
</template>

<script setup>
import AccessibleButton from '../../components/AccessibleButton.vue'
import Toast from '../../components/Toast.vue'
import { computed, reactive } from 'vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

/* 非临床五维：只做状态整理，不评分、不诊断、不生成风险等级 */
const DIMENSIONS = [
	{ key: 'sleep', name: '睡眠', question: '昨夜，睡得安稳吗？' },
	{ key: 'energy', name: '精力', question: '今天，身体有多少力气？' },
	{ key: 'focus', name: '专注', question: '能把注意力放回一件事上吗？' },
	{ key: 'connection', name: '联结', question: '今天，和人有真实的连接吗？' },
	{ key: 'calm', name: '平静', question: '此刻，心里有多少安静？' }
]
const LABELS = ['很少', '较少', '一般', '较多', '很多']
const values = reactive({ sleep: 0, energy: 0, focus: 0, connection: 0, calm: 0 })
const complete = computed(() => DIMENSIONS.every(d => values[d.key] > 0))
const summary = computed(() => {
	const parts = DIMENSIONS.map(d => `${d.name}${values[d.key] <= 2 ? '偏少' : values[d.key] >= 4 ? '偏多' : '在中间'}`)
	return `你标记了：${parts.join('，')}。没有好坏，只是今天的样子。`
})

function setValue(key, n) { values[key] = n }
function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) }) }
function goJourney() { uni.navigateTo({ url: '/pages/journey/index' }) }
function goCrisis() { uni.navigateTo({ url: '/pages/crisis/index' }) }
function saveLocal() {
	if (!complete.value) return
	try {
		uni.setStorageSync('zy-five-check', { at: new Date().toISOString(), values: { ...values } })
		toast.success('已记下，只保存在这台设备')
	} catch (e) {
		toast.care('这台设备暂时无法保存')
	}
}
</script>

<style scoped>
.check-page { min-height: 100vh; padding: 0 36rpx 64rpx; box-sizing: border-box; background: var(--zy-bg); padding-top: env(safe-area-inset-top, 0px); }
.check-top { display: flex; align-items: center; gap: 18rpx; padding-top: 24rpx; }
.back-btn { width: 64rpx; height: 64rpx; min-width: 64rpx; min-height: 64rpx; padding: 0; margin: 0; background: transparent; border: 1rpx solid var(--zy-border); border-radius: 0; color: var(--zy-title); font-size: 28rpx; }
.top-index { color: var(--zy-muted); font-size: 20rpx; letter-spacing: 3rpx; }

.check-head { padding-top: 44rpx; }
.head-rule { width: 44rpx; height: 2rpx; background: var(--zy-care); }
.check-title { display: block; margin-top: 24rpx; font-size: 64rpx; line-height: 1.34; font-weight: 500; letter-spacing: 2rpx; color: var(--zy-title); }
.check-sub { display: block; margin-top: 22rpx; max-width: 92%; color: var(--zy-muted); font-size: 24rpx; line-height: 1.9; }
.check-sub-second { margin-top: 4rpx; }
.scale-hint { display: block; margin-top: 20rpx; color: var(--zy-muted); font-size: 20rpx; line-height: 1.7; }

.dim-list { margin-top: 48rpx; border-top: 1rpx solid var(--zy-border); }
.dim-row { display: flex; gap: 16rpx; padding: 26rpx 0 22rpx; border-bottom: 1rpx solid var(--zy-border); }
.dim-index { width: 44rpx; flex-shrink: 0; padding-top: 4rpx; color: var(--zy-muted); font-family: var(--zy-serif); font-size: 20rpx; }
.dim-main { flex: 1; min-width: 0; }
.dim-copy { display: flex; align-items: baseline; flex-wrap: wrap; gap: 12rpx; }
.dim-name { color: var(--zy-title); font-family: var(--zy-font-display, "Songti SC", serif); font-size: 32rpx; font-weight: 500; letter-spacing: 1rpx; }
.dim-q { color: var(--zy-muted); font-size: 21rpx; }
.dim-scale { display: flex; gap: 0; margin-top: 16rpx; align-items: center; }
.dim-dot { width: 88rpx; height: 80rpx; min-width: 88rpx; min-height: 80rpx; padding: 0; margin: 0 -1rpx 0 0; border: 1rpx solid var(--zy-border); border-radius: 0; background: transparent; display: flex; align-items: center; justify-content: center; }
.dim-dot::after { border: 0; }
.dim-dot-num { font-size: 26rpx; color: var(--zy-muted); line-height: 1; }
.dim-dot.is-on { border-color: var(--zy-brand-600); background: transparent; }
.dim-dot.is-on .dim-dot-num { color: var(--zy-brand-700); }
.dim-dot.is-selected { background: var(--zy-brand-600); border-color: var(--zy-brand-600); }
.dim-dot.is-selected .dim-dot-num { color: #ffffff; }
.dim-value { display: block; margin-top: 8rpx; color: var(--zy-muted); font-size: 20rpx; }
.dim-dot:focus-visible { outline: 2px solid var(--zy-brand-700); outline-offset: 2px; }

.summary { margin-top: 48rpx; padding-left: 20rpx; border-left: 2rpx solid var(--zy-care); }
.summary-line { display: none; }
.summary-kicker { display: block; color: var(--zy-muted); font-size: 20rpx; letter-spacing: 3rpx; }
.summary-text { display: block; margin-top: 14rpx; color: var(--zy-title); font-size: 38rpx; line-height: 1.9; }
.summary-note { display: block; margin-top: 16rpx; color: var(--zy-muted); font-size: 21rpx; line-height: 1.8; }

.check-actions { margin-top: 44rpx; display: flex; flex-direction: column; gap: 18rpx; }
.save-btn { width: 100%; }
.journey-link { margin: 0; padding: 0; align-self: flex-start; color: var(--zy-brand-700); font-size: 22rpx; }

.support-card { margin-top: 52rpx; padding-top: 24rpx; border-top: 1rpx solid var(--zy-border); display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.support-text { flex: 1; min-width: 0; color: var(--zy-text); font-size: 22rpx; line-height: 1.8; }
.support-link { margin: 0; padding: 0; flex-shrink: 0; color: var(--zy-care); font-size: 22rpx; background: transparent; border: 0; min-height: 44rpx; }
.support-link::after { border: 0; }
.check-foot { display: block; margin-top: 42rpx; color: var(--zy-muted); font-size: 20rpx; text-align: center; }
</style>
