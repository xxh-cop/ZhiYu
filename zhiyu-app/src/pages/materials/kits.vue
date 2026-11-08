<template>
	<view :class="zyThemeClass" class="kits-page zy-page-enter">
		<view class="kits-nav">
			<AccessibleButton class="back-btn" aria-label="返回材料探索" @click="leave">‹ 返回</AccessibleButton>
			<text class="nav-label">工作坊材料包</text>
		</view>

		<view class="kits-hero">
			<text class="eyebrow">WORKSHOP MATERIAL KITS</text>
			<text class="title">把材料先准备好</text>
			<text class="subtitle">对应 3 个团体工作坊模板的准备清单。数量按实际人数调整，工具与安全事项由带领者现场确认。</text>
		</view>

		<view v-if="loading" class="loading-card" aria-busy="true">
			<view class="loading-line zy-skeleton"></view>
			<view class="loading-line short zy-skeleton"></view>
			<text class="loading-text">正在整理材料包…</text>
		</view>

		<view v-else-if="errorMsg" class="status-card" role="alert">
			<text class="status-text">{{ errorMsg }}</text>
			<AccessibleButton class="primary-action" @click="load">重新加载</AccessibleButton>
			<AccessibleButton class="text-action" @click="leave">返回材料探索</AccessibleButton>
		</view>

		<template v-else>
			<text v-if="disclaimer" class="catalog-note">{{ disclaimer }}</text>

			<view v-for="(kit, idx) in kits" :key="kit.id" class="kit-card">
				<AccessibleButton class="kit-head" :aria-expanded="expanded === kit.id" @click="toggle(kit.id)">
					<view class="kit-head-copy">
						<text class="kit-index">0{{ idx + 1 }}</text>
						<text class="kit-name">{{ kit.name }}</text>
						<text class="kit-meta">{{ kit.audience }} · 建议 {{ kit.capacity }} 人</text>
					</view>
					<text class="chevron">{{ expanded === kit.id ? '−' : '+' }}</text>
				</AccessibleButton>

				<view v-if="expanded === kit.id" class="kit-body">
					<view class="block">
						<text class="block-label">材料与数量</text>
						<view v-for="item in kit.items" :key="item.name + item.quantity" class="item-row">
							<view class="item-main">
								<text class="item-name">{{ item.name }}</text>
								<text class="item-note">{{ item.note }}</text>
							</view>
							<text class="item-qty">{{ item.quantity }}</text>
						</view>
					</view>

					<view class="block">
						<text class="block-label">工具</text>
						<text v-for="tool in kit.tools" :key="tool" class="bullet">· {{ tool }}</text>
					</view>

					<view class="block">
						<text class="block-label">带领者准备</text>
						<view v-for="(step, stepIdx) in kit.preparation" :key="step" class="step-row">
							<text class="step-num">{{ stepIdx + 1 }}</text>
							<text class="step-text">{{ step }}</text>
						</view>
					</view>

					<view class="block safety-block">
						<text class="block-label safety-label">安全提示</text>
						<text v-for="tip in kit.safety" :key="tip" class="bullet safety-bullet">· {{ tip }}</text>
					</view>

					<view class="digital-card">
						<text class="digital-label">没有实物材料时</text>
						<text class="digital-text">{{ kit.digitalAlternative }}</text>
					</view>

					<text class="source-note">{{ kit.sourceNote }}</text>
				</view>
			</view>

			<text class="page-disclaimer">本系统不能替代专业心理咨询</text>
		</template>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import AccessibleButton from '../../components/AccessibleButton.vue'

const kits = ref([])
const disclaimer = ref('')
const loading = ref(true)
const errorMsg = ref('')
const expanded = ref('')

onLoad(load)

async function load() {
	loading.value = true
	errorMsg.value = ''
	try {
		const data = await request({ url: '/api/growth/material-kits' })
		kits.value = data?.kits || []
		disclaimer.value = data?.disclaimer || ''
		expanded.value = kits.value[0]?.id || ''
	} catch (e) {
		kits.value = []
		errorMsg.value = e?.message || '材料包读取失败，请重试'
	} finally {
		loading.value = false
	}
}

function toggle(id) {
	expanded.value = expanded.value === id ? '' : id
}

function leave() {
	uni.navigateBack({
		fail: () => uni.reLaunch({ url: '/pages/materials/index' }),
	})
}
</script>

<style scoped>
.kits-page {
	min-height: 100vh;
	padding: 28rpx 32rpx 90rpx;
	box-sizing: border-box;
	background: var(--zy-bg, #f5f7f2);
	color: var(--zy-content, #4a5f54);
}
.kits-nav { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.back-btn { margin: 0; padding: 8rpx 18rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 999rpx; background: var(--zy-card-bg, #fff); color: var(--zy-content, #4a5f54); font-size: 22rpx; line-height: 2; }
.back-btn::after { border: 0; }
.nav-label { font-size: 20rpx; letter-spacing: 2rpx; color: var(--zy-text, #4a5f54); }
.kits-hero { margin: 34rpx 0 24rpx; }
.eyebrow { display: block; font-size: 19rpx; letter-spacing: 2rpx; color: var(--zy-accent, #8a6d1f); }
.title { display: block; margin-top: 10rpx; font-family: var(--zy-serif); font-size: 44rpx; font-weight: 600; line-height: 1.4; color: var(--zy-title, #233d34); }
.subtitle { display: block; margin-top: 10rpx; font-size: 23rpx; line-height: 1.8; color: var(--zy-text, #4a5f54); }
.catalog-note { display: block; margin-bottom: 18rpx; padding: 16rpx 18rpx; border-radius: 12rpx; background: var(--zy-bg-tint-mint, #eaf5ef); font-size: 20rpx; line-height: 1.8; color: var(--zy-content, #4a5f54); }
.kit-card { margin-bottom: 18rpx; overflow: hidden; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 22rpx; background: var(--zy-card-bg, #fff); box-shadow: var(--zy-shadow-sm); }
.kit-head { display: flex; align-items: center; justify-content: space-between; width: 100%; margin: 0; padding: 24rpx 24rpx; border: 0; background: transparent; text-align: left; line-height: 1.5; }
.kit-head::after { border: 0; }
.kit-head-copy { flex: 1; min-width: 0; }
.kit-index { display: block; font-family: var(--zy-sans); font-size: 18rpx; letter-spacing: 2rpx; color: var(--zy-accent, #8a6d1f); }
.kit-name { display: block; margin-top: 6rpx; font-family: var(--zy-serif); font-size: 29rpx; font-weight: 600; line-height: 1.5; color: var(--zy-title, #233d34); }
.kit-meta { display: block; margin-top: 6rpx; font-size: 20rpx; line-height: 1.6; color: var(--zy-text, #4a5f54); }
.chevron { flex-shrink: 0; width: 54rpx; text-align: center; font-size: 34rpx; color: var(--zy-brand-700, #217e76); }
.kit-body { padding: 0 24rpx 26rpx; }
.block { margin-top: 24rpx; }
.block-label { display: block; margin-bottom: 12rpx; font-size: 21rpx; font-weight: 600; letter-spacing: 1rpx; color: var(--zy-brand-700, #217e76); }
.item-row { display: flex; align-items: flex-start; gap: 16rpx; padding: 14rpx 0; border-bottom: 1rpx dashed var(--zy-border, #dfe7dc); }
.item-row:last-child { border-bottom: 0; }
.item-main { flex: 1; min-width: 0; }
.item-name { display: block; font-size: 23rpx; line-height: 1.6; color: var(--zy-title, #233d34); }
.item-note { display: block; margin-top: 4rpx; font-size: 19rpx; line-height: 1.6; color: var(--zy-text, #4a5f54); }
.item-qty { flex-shrink: 0; max-width: 190rpx; text-align: right; font-size: 20rpx; line-height: 1.6; color: var(--zy-brand-700, #217e76); }
.bullet { display: block; margin-top: 6rpx; font-size: 22rpx; line-height: 1.8; color: var(--zy-content, #4a5f54); }
.step-row { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 10rpx; }
.step-num { flex-shrink: 0; width: 38rpx; height: 38rpx; border-radius: 50%; background: var(--zy-bg-tint-mint, #eaf5ef); color: var(--zy-brand-700, #217e76); font-size: 19rpx; line-height: 38rpx; text-align: center; }
.step-text { flex: 1; font-size: 22rpx; line-height: 1.8; color: var(--zy-content, #4a5f54); }
.safety-block { padding: 18rpx 18rpx 14rpx; border-radius: 14rpx; background: var(--zy-bg, #f5f7f2); border-left: 6rpx solid var(--zy-gold, #d9a441); }
.safety-label { color: var(--zy-warning-text, #8a6d1f); }
.safety-bullet { color: var(--zy-warning-text, #8a6d1f); }
.digital-card { margin-top: 22rpx; padding: 18rpx; border: 1rpx dashed var(--zy-border, #dfe7dc); border-radius: 14rpx; }
.digital-label { display: block; font-size: 19rpx; letter-spacing: 1rpx; color: var(--zy-accent, #8a6d1f); }
.digital-text { display: block; margin-top: 6rpx; font-size: 21rpx; line-height: 1.8; color: var(--zy-content, #4a5f54); }
.source-note { display: block; margin-top: 18rpx; font-size: 19rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); }
.loading-card, .status-card { margin-top: 30rpx; padding: 34rpx 28rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 20rpx; background: var(--zy-card-bg, #fff); }
.loading-line { height: 28rpx; border-radius: 8rpx; }
.loading-line.short { width: 62%; margin-top: 16rpx; }
.loading-text, .status-text { display: block; margin-top: 16rpx; font-size: 22rpx; line-height: 1.8; color: var(--zy-text, #4a5f54); }
.primary-action { min-height: 86rpx; margin-top: 18rpx; border-radius: 999rpx; background: var(--zy-brand-700, #217e76); color: #fff; font-size: 26rpx; line-height: 2.8; }
.primary-action::after { border: 0; }
.text-action { margin-top: 10rpx; background: transparent; color: var(--zy-text, #4a5f54); font-size: 22rpx; line-height: 2.4; }
.text-action::after { border: 0; }
.page-disclaimer { display: block; margin-top: 32rpx; text-align: center; font-size: 20rpx; color: var(--zy-text, #4a5f54); }
.kit-head:focus-visible { outline: 3px solid var(--zy-primary, #217e76); outline-offset: -3px; }
@media (max-width: 360px) {
	.kits-page { padding-left: 24rpx; padding-right: 24rpx; }
	.title { font-size: 38rpx; }
	.item-row { gap: 10rpx; }
	.item-qty { max-width: 150rpx; }
}
</style>
