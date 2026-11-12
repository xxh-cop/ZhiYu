<template>
	<view class="onboarding-page">
		<view v-if="checking" class="checking">
			<text>正在准备…</text>
		</view>

		<template v-else>
			<scroll-view scroll-y class="onboarding-scroll">
				<view class="onboarding-inner">
					<view class="brand-row">
						<image class="brand-logo" src="/static/logo.png" aria-hidden="true" />
						<text class="brand-name">织愈</text>
					</view>

					<view class="hero">
						<XiaotuanPet :size="92" face="calm" aria-hidden="true" />
						<text class="eyebrow">AI 陪伴 · 非遗手作 · 学生支持</text>
						<text class="title zy-t-serif">把情绪，慢慢放下来。</text>
						<text class="definition">
							织愈是一个用 AI 陪伴 + 非遗手作，帮你在情绪低落时慢下来的学生工具。
						</text>
					</view>

					<view class="things">
						<view class="thing">
							<text class="thing-mark">01</text>
							<view class="thing-copy"><text class="thing-title">说说心里话</text><text class="thing-sub">小织会听，不急着给建议</text></view>
						</view>
						<view class="thing">
							<text class="thing-mark">02</text>
							<view class="thing-copy"><text class="thing-title">做一件小手作</text><text class="thing-sub">跟着步骤，材料与时间都标好</text></view>
						</view>
						<view class="thing">
							<text class="thing-mark">03</text>
							<view class="thing-copy"><text class="thing-title">留下今天的记录</text><text class="thing-sub">只属于你，可随时清除</text></view>
						</view>
					</view>

					<view class="privacy-card">
						<text class="privacy-title">关于你的隐私</text>
						<text class="privacy-line">✓ 只在你同意后收集昵称、对话与记录</text>
						<text class="privacy-line">✓ 对话与便签加密存储，咨询师看到的是脱敏汇总</text>
						<text class="privacy-line">✓ 可随时撤回授权、导出记录或注销账号</text>
						<AccessibleButton variant="ghost" size="sm" class="policy-link" @click="goPrivacy">
							查看完整隐私政策 →
						</AccessibleButton>
					</view>

					<text class="disclaimer">本系统不能替代专业心理咨询。如有紧急安全风险，请先联系身边可信任的人或当地紧急服务。</text>
				</view>
			</scroll-view>

			<view class="onboarding-actions">
				<AccessibleButton variant="primary" size="lg" :loading="saving" :disabled="saving" @click="agree">
					{{ saving ? '正在保存…' : '同意并开始' }}
				</AccessibleButton>
				<AccessibleButton variant="ghost" size="md" :disabled="saving" @click="decline">
					先随便看看
				</AccessibleButton>
			</view>
		</template>

		<Toast />
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { http } from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const toast = useToast()
const checking = ref(true)
const saving = ref(false)

function goHome() {
	uni.reLaunch({ url: '/pages/home/index' })
}

onLoad(async () => {
	if (userStore.privacyAgreed === null) {
		await userStore.fetchPrivacyStatus()
	}
	if (userStore.privacyAgreed === 1) {
		goHome()
		return
	}
	checking.value = false
})

function goPrivacy() {
	uni.navigateTo({ url: '/pages/privacy/index' })
}

async function agree() {
	if (saving.value) return
	saving.value = true
	try {
		await http.post('/api/user/privacy/agree', {})
		userStore.setPrivacyAgreed(1)
		userStore.setConsentDismissed(false)
		goHome()
	} catch (e) {
		toast.care(e && e.message ? e.message : '暂时无法保存，请检查网络后重试')
	} finally {
		saving.value = false
	}
}

function decline() {
	userStore.setConsentDismissed(true)
	goHome()
}
</script>

<style scoped>
.onboarding-page {
	min-height: 100vh;
	background: #FFFFFF;
	color: var(--zy-title, #33302A);
}
.checking {
	min-height: 60vh;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 26rpx;
	color: var(--zy-text, #5C5648);
}
.onboarding-scroll {
	height: calc(100vh - 220rpx);
	box-sizing: border-box;
}
.onboarding-inner {
	max-width: 720px;
	margin: 0 auto;
	padding: 32rpx 36rpx 300rpx;
}
.brand-row {
	display: flex;
	align-items: center;
	gap: 14rpx;
	margin-bottom: 28rpx;
}
.brand-logo {
	width: 52rpx;
	height: 52rpx;
	border-radius: 14rpx;
}
.brand-name {
	font-size: 34rpx;
	font-weight: 600;
	letter-spacing: 4rpx;
}
.hero {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	padding: 20rpx 0 36rpx;
}
.eyebrow {
	width: 100%;
	margin-top: 20rpx;
	font-size: 22rpx;
	letter-spacing: 3rpx;
	color: var(--zy-brand-700, #1A645E);
}
.title {
	display: block;
	width: 100%;
	margin-top: 18rpx;
	font-size: 52rpx;
	line-height: 1.35;
	font-weight: 600;
	letter-spacing: 1rpx;
}
.definition {
	display: block;
	width: 100%;
	margin-top: 18rpx;
	font-size: 29rpx;
	line-height: 1.85;
	color: var(--zy-text, #5C5648);
}
.things {
	border-top: 1rpx solid var(--zy-border, #E4DCCB);
}
.thing {
	display: flex;
	align-items: flex-start;
	gap: 24rpx;
	padding: 26rpx 0;
	border-bottom: 1rpx solid var(--zy-border, #E4DCCB);
}
.thing-mark {
	flex: 0 0 auto;
	font-size: 24rpx;
	font-weight: 600;
	color: var(--zy-brand-700, #1A645E);
	padding-top: 4rpx;
}
.thing-copy {
	flex: 1;
	min-width: 0;
}
.thing-title {
	display: block;
	font-size: 30rpx;
	font-weight: 600;
}
.thing-sub {
	display: block;
	margin-top: 6rpx;
	font-size: 24rpx;
	line-height: 1.7;
	color: var(--zy-text, #5C5648);
}
.privacy-card {
	margin-top: 30rpx;
	padding: 28rpx;
	border-radius: 24rpx;
	background: var(--zy-bg-tint-mint, #E4EFE7);
}
.privacy-title {
	display: block;
	font-size: 28rpx;
	font-weight: 600;
	margin-bottom: 14rpx;
}
.privacy-line {
	display: block;
	font-size: 24rpx;
	line-height: 1.8;
	color: var(--zy-text, #5C5648);
}
.policy-link {
	margin: 8rpx 0 0;
	padding-left: 0;
	font-size: 24rpx;
	color: var(--zy-brand-700, #1A645E);
	justify-content: flex-start;
}
.disclaimer {
	display: block;
	margin-top: 28rpx;
	font-size: 22rpx;
	line-height: 1.8;
	color: var(--zy-text, #5C5648);
}
.onboarding-actions {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 20;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
	padding: 18rpx 32rpx calc(18rpx + env(safe-area-inset-bottom));
	background: rgba(255, 255, 255, 0.97);
	border-top: 1rpx solid var(--zy-border, #E4DCCB);
}
</style>