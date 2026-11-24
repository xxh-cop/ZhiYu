<template>
	<view :class="zyThemeClass" class="kits-page zy-page-enter">
		<view class="kits-hero">
			<ZyPageHead seal="材" title="把材料先准备好" subtitle="对应 3 个团体工作坊模板的准备清单；不含采购链接与价格。数量按实际人数调整，工具与安全事项由带领者现场确认。" />
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
			<view class="kit-list">
				<HeritageCard
					v-for="(kit, idx) in kits"
					:key="kit.id"
					class="kit-card"
					:family="kitFamily(kit)"
					:title="kit.name"
					:tag="'0' + (idx + 1)"
					:meta="[kit.audience, '建议 ' + kit.capacity + ' 人'].filter(Boolean).join(' · ')"
					:aria-label="'材料包 ' + kit.name + '，' + (expanded === kit.id ? '收起清单' : '展开清单')"
					:aria-expanded="expanded === kit.id"
					@open="toggle(kit.id)"
				>
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
					<template #footer>
						<text class="chevron">{{ expanded === kit.id ? '−' : '+' }}</text>
					</template>
				</HeritageCard>
			</view>

		</template>
	</view>
</template>

<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import AccessibleButton from '../../components/AccessibleButton.vue'
import HeritageCard from '../../components/HeritageCard.vue'

const kits = ref([])
const loading = ref(true)
const errorMsg = ref('')
const expanded = ref('')

/* 材料包未下发 family，按名称/受众里的工艺关键词映射四族；未命中回落编织 */
function kitFamily(kit) {
	const text = `${(kit && kit.name) || ''}${(kit && kit.audience) || ''}`
	if (/纸|剪/.test(text)) return 'cutout'
	if (/竹|编|藤/.test(text)) return 'weave'
	if (/布|绣|织|线/.test(text)) return 'stitch'
	if (/陶|瓷|釉/.test(text)) return 'porcelain'
	return 'weave'
}

onLoad(load)

async function load() {
	loading.value = true
	errorMsg.value = ''
	try {
		const data = await request({ url: '/api/growth/material-kits' })
		kits.value = data?.kits || []
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
	background: var(--zy-bg, #ffffff);
	color: var(--zy-content, #4a5f54);
}
.kits-nav { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.back-btn { margin: 0; padding: 8rpx 18rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 999rpx; background: var(--zy-card-bg, #fff); color: var(--zy-content, #4a5f54); font-size:var(--zy-text-micro, 22rpx); line-height: 2; }
.back-btn::after { border: 0; }
.nav-label { font-size:var(--zy-text-micro, 22rpx); letter-spacing: 2rpx; color: var(--zy-text, #4a5f54); }
.kits-hero { margin: 34rpx 0 24rpx; }
.eyebrow { display: block; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 2rpx; color: var(--zy-accent, var(--zy-accent, #7a5c12)); }
.title { display: block; margin-top: 10rpx; font-family: var(--zy-serif); font-size:var(--zy-text-title-lg, 40rpx); font-weight: 600; line-height: 1.4; color: var(--zy-title, #233d34); }
.subtitle { display: block; margin-top: 10rpx; font-size:var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-text, #4a5f54); }
.catalog-note { display: block; margin-bottom: 18rpx; padding: 16rpx 18rpx; border-radius: 12rpx; background: var(--zy-bg-tint-mint, #eef3fb); font-size:var(--zy-text-micro, 22rpx); line-height: 1.8; color: var(--zy-content, #4a5f54); }
.kit-list { display: flex; flex-direction: column; gap: 18rpx; }
.kit-card { overflow: hidden; }
.kit-body { padding: 0 0 4rpx; }
.chevron { display: block; text-align: right; font-size:var(--zy-text-title, 32rpx); color: var(--zy-brand-700, var(--zy-brand-600, #24478f)); }
.block { margin-top: 24rpx; }
.block-label { display: block; margin-bottom: 12rpx; font-size:var(--zy-text-micro, 22rpx); font-weight: 600; letter-spacing: 1rpx; color: var(--zy-brand-700, var(--zy-brand-600, #24478f)); }
.item-row { display: flex; align-items: flex-start; gap: 16rpx; padding: 14rpx 0; border-bottom: 1rpx dashed var(--zy-border, #dfe7dc); }
.item-row:last-child { border-bottom: 0; }
.item-main { flex: 1; min-width: 0; }
.item-name { display: block; font-size:var(--zy-text-caption, 24rpx); line-height: 1.6; color: var(--zy-title, #233d34); }
.item-note { display: block; margin-top: 4rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-text, #4a5f54); }
.item-qty { flex-shrink: 0; max-width: 190rpx; text-align: right; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-brand-700, var(--zy-brand-600, #24478f)); }
.bullet { display: block; margin-top: 6rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.8; color: var(--zy-content, #4a5f54); }
.step-row { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 10rpx; }
.step-num { flex-shrink: 0; width: 38rpx; height: 38rpx; border-radius: 50%; background: var(--zy-bg-tint-mint, #eef3fb); color: var(--zy-brand-700, var(--zy-brand-600, #24478f)); font-size:var(--zy-text-micro, 22rpx); line-height: 38rpx; text-align: center; }
.step-text { flex: 1; font-size:var(--zy-text-micro, 22rpx); line-height: 1.8; color: var(--zy-content, #4a5f54); }
.safety-block { padding: 18rpx 18rpx 14rpx; border-radius: 14rpx; background: var(--zy-bg, #ffffff); border-left: 6rpx solid var(--zy-gold, #d9a441); }
.safety-label { color: var(--zy-warning-text, var(--zy-accent, #7a5c12)); }
.safety-bullet { color: var(--zy-warning-text, var(--zy-accent, #7a5c12)); }
.digital-card { margin-top: 22rpx; padding: 18rpx; border: 1rpx dashed var(--zy-border, #dfe7dc); border-radius: 14rpx; }
.digital-label { display: block; font-size:var(--zy-text-micro, 22rpx); letter-spacing: 1rpx; color: var(--zy-accent, var(--zy-accent, #7a5c12)); }
.digital-text { display: block; margin-top: 6rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.8; color: var(--zy-content, #4a5f54); }
.source-note { display: block; margin-top: 18rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.7; color: var(--zy-text, #4a5f54); }
.loading-card, .status-card { margin-top: 30rpx; padding: 34rpx 28rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 20rpx; background: var(--zy-card-bg, #fff); }
.loading-line { height: 28rpx; border-radius: 8rpx; }
.loading-line.short { width: 62%; margin-top: 16rpx; }
.loading-text, .status-text { display: block; margin-top: 16rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.8; color: var(--zy-text, #4a5f54); }
.primary-action { min-height: 86rpx; margin-top: 18rpx; border-radius: 999rpx; background: var(--zy-brand-700, var(--zy-brand-600, #24478f)); color: #fff; font-size:var(--zy-text-caption, 24rpx); line-height: 2.8; }
.primary-action::after { border: 0; }
.text-action { margin-top: 10rpx; background: transparent; color: var(--zy-text, #4a5f54); font-size:var(--zy-text-micro, 22rpx); line-height: 2.4; }
.text-action::after { border: 0; }
.page-disclaimer { display: block; margin-top: 32rpx; text-align: center; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-text, #4a5f54); }
/* ── v9 冷瓷展签收口 ── */
.kits-page { padding: 32rpx 32rpx 120rpx; background: var(--zy-bg); }
.kits-nav { padding-bottom: 22rpx; }
.back-btn { padding: 0; border: 0; border-radius: var(--zy-radius-control, 18rpx); background: transparent; color: var(--zy-muted); font-size: 22rpx; line-height: 1.8; display: inline-flex; align-items: center; gap: 4rpx; min-height: 64rpx; }
.back-btn::after { border: 0; }
.nav-label { font-size: 22rpx; letter-spacing: 1px; color: var(--zy-muted); }
.kits-hero { margin: 10rpx 0 28rpx; padding-bottom: 28rpx; border-bottom: 1rpx solid var(--zy-border); }
.eyebrow { color: var(--zy-muted); letter-spacing: 2px; }
.title { font-family: var(--zy-font-display, serif); font-size: 56rpx; font-weight: 500; line-height: 1.3; color: var(--zy-title); }
.subtitle { font-size: 26rpx; color: var(--zy-muted); line-height: 1.8; }
.catalog-note { padding: 18rpx 0 18rpx 20rpx; border-left: 2rpx solid var(--zy-brand-700); border-radius: var(--zy-radius-control, 18rpx); background: transparent; color: var(--zy-text); font-size: 22rpx; line-height: 1.8; }
.kit-list { gap: 0; }
.kit-body { padding: 0; }
.block { margin-top: 30rpx; }
.block-label { font-family: var(--zy-font-display, serif); font-size: 28rpx; font-weight: 500; letter-spacing: 1px; color: var(--zy-brand-700); margin-bottom: 10rpx; }
.item-row { padding: 16rpx 0; border-bottom: 1rpx solid var(--zy-border); }
.item-name { font-size: 26rpx; color: var(--zy-title); }
.item-note { font-size: 22rpx; color: var(--zy-muted); line-height: 1.7; }
.item-qty { font-size: 22rpx; color: var(--zy-brand-700); }
.bullet { font-size: 22rpx; line-height: 1.9; color: var(--zy-text); }
.step-num { width: 36rpx; height: 36rpx; border-radius: var(--zy-radius-control, 18rpx); border: 1rpx solid var(--zy-border); background: transparent; color: var(--zy-brand-700); line-height: 36rpx; font-size: 20rpx; }
.step-text { font-size: 22rpx; color: var(--zy-text); }
.safety-block { padding: 16rpx 0 16rpx 18rpx; border-radius: var(--zy-radius-control, 18rpx); background: transparent; border-left: 2rpx solid var(--zy-care); }
.safety-label, .safety-bullet { color: var(--zy-care); }
.digital-card { margin-top: 24rpx; padding: 18rpx 0; border: 0; border-top: 1rpx solid var(--zy-border); border-bottom: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); background: transparent; }
.digital-label { color: var(--zy-muted); font-size: 22rpx; }
.digital-text { color: var(--zy-text); font-size: 22rpx; }
.source-note { color: var(--zy-muted); font-size: 20rpx; }
.loading-card, .status-card { margin-top: 30rpx; padding: 28rpx 0; border: 0; border-top: 1rpx solid var(--zy-border); border-bottom: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); background: transparent; }
.primary-action { border-radius: var(--zy-radius-control, 18rpx); background: var(--zy-brand-700); min-height: 80rpx; }
.text-action { color: var(--zy-muted); }
.page-disclaimer { color: var(--zy-muted); }
@media (max-width: 360px) {
	.kits-page { padding-left: 24rpx; padding-right: 24rpx; }
	.title { font-size:var(--zy-text-title-lg, 40rpx); }
	.item-row { gap: 10rpx; }
	.item-qty { max-width: 150rpx; }
}
</style>
