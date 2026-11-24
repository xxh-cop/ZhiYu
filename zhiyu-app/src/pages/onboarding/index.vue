<template>
	<view :class="zyThemeClass" class="onboarding-page">
		<view v-if="checking" class="checking">
			<image class="checking-pet" src="/static/mascot/xiaozhi-standard.png" mode="aspectFit" aria-hidden="true" />
			<text>正在准备…</text>
		</view>

		<template v-else>
			<scroll-view scroll-y class="onboarding-scroll">
				<!-- ① 远山晨光 + 小织 -->
				<view class="hero">
					<view class="hero-hills zy-wave-bg" aria-hidden="true"></view>
					<view class="hero-inner">
						<view class="brand-row">
							<ZySeal text="织愈" size="md" />
							<text class="brand-name">织愈</text>
						</view>
						<image class="hero-pet" src="/static/mascot/xiaozhi-welcome.png" mode="aspectFit" aria-hidden="true" />
						<text class="title">压力有点大？<br/>先停一停。</text>
						<text class="definition">在这里，用一件传统小手作，把注意力放回手上。做不完也没关系。</text>
						<view class="pills">
							<view class="pill"><ZyIcon name="clock" :size="26" tone="brand" /><text>每次 5–15 分钟</text></view>
							<view class="pill"><ZyIcon name="heart" :size="26" tone="care" /><text>不打分、不比较</text></view>
						</view>
					</view>
				</view>

				<view class="body">
					<!-- ② 在这里可以做的三件小事 -->
					<view class="sec-head"><view class="sec-mark" aria-hidden="true"></view><text class="sec-title">在这里，你可以</text></view>
					<view class="things">
						<view class="thing tone-blue">
							<image class="thing-img" src="/static/mascot/xiaozhi-listening.png" mode="aspectFit" aria-hidden="true" />
							<view class="thing-copy"><text class="thing-title">说说心里话</text><text class="thing-sub">小织会听，不评价，也不急着给建议</text></view>
						</view>
						<view class="thing tone-warm">
							<image class="thing-img" src="/static/mascot/xiaozhi-crafting.png" mode="aspectFit" aria-hidden="true" />
							<view class="thing-copy"><text class="thing-title">做一件非遗小手作</text><text class="thing-sub">赣绣、剪纸、扎染……材料与步骤都标好了</text></view>
						</view>
						<view class="thing tone-pine">
							<image class="thing-img" src="/static/mascot/xiaozhi-rest.png" mode="aspectFit" aria-hidden="true" />
							<view class="thing-copy"><text class="thing-title">留下今天的心情</text><text class="thing-sub">只属于你，可以随时清除</text></view>
						</view>
					</view>

					<!-- ③ 隐私 -->
					<view class="privacy-card">
						<view class="privacy-head"><ZyIcon name="lock" :size="34" tone="brand" /><text class="privacy-title">你的记录，由你做主</text></view>
						<view class="privacy-line"><view class="check"><ZyIcon name="check" :size="20" color="#FFFFFF" /></view><text>只在你同意后收集昵称、对话与记录</text></view>
						<view class="privacy-line"><view class="check"><ZyIcon name="check" :size="20" color="#FFFFFF" /></view><text>对话与便签加密存储，咨询师只看到脱敏汇总</text></view>
						<view class="privacy-line"><view class="check"><ZyIcon name="check" :size="20" color="#FFFFFF" /></view><text>可以随时撤回授权、导出记录或注销账号</text></view>
						<AccessibleButton class="policy-link" @click="goPrivacy">查看完整隐私政策</AccessibleButton>
					</view>

					<text class="disclaimer">本系统不能替代专业心理咨询。如有紧急安全风险，请先联系身边可信任的人或当地紧急服务。</text>
				</view>
			</scroll-view>

			<view class="onboarding-actions">
				<AccessibleButton variant="primary" size="lg" class="agree-btn" :loading="saving" :disabled="saving" @click="agree">
					{{ saving ? '正在保存…' : '同意，开始放松一下' }}
				</AccessibleButton>
				<AccessibleButton class="later-btn" :disabled="saving" @click="decline">先随便看看</AccessibleButton>
			</view>
		</template>

		<Toast />
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import ZySeal from '../../components/ZySeal.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { http } from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const toast = useToast()
const checking = ref(true)
const saving = ref(false)
const preview = ref(false)

function goHome() {
	uni.reLaunch({ url: '/pages/home/index' })
}

onLoad(async (options) => {
	// 设计预览：仅用于截图/评审，不改变正常用户的首次引导流程
	preview.value = !!(options && options.preview === '1')
	if (userStore.privacyAgreed === null) {
		await userStore.fetchPrivacyStatus()
	}
	if (userStore.privacyAgreed === 1 && !preview.value) {
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
/* v9.1 首次引导：远山晨光、小织陪伴、三件小事、温和的隐私说明 */
.onboarding-page { min-height: 100vh; background: var(--zy-bg); color: var(--zy-title); }
.checking { min-height: 80vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16rpx; font-size: 26rpx; color: var(--zy-muted); }
.checking-pet { width: 180rpx; height: 180rpx; }
.onboarding-scroll { height: calc(100vh - 230rpx - env(safe-area-inset-bottom)); box-sizing: border-box; }

.hero { position: relative; overflow: hidden; padding: calc(var(--status-bar-height, 0px) + 44rpx) 40rpx 120rpx; background: linear-gradient(180deg, #dfe8f7 0%, #eef3fb 60%, var(--zy-bg) 100%); }
.hero-hills { position: absolute; left: 0; right: 0; bottom: 0; height: 280rpx; }
.hero-inner { position: relative; max-width: 960rpx; margin: 0 auto; }
.brand-row { display: flex; align-items: center; gap: 16rpx; }
.brand-name { font-family: var(--zy-font-display); font-size: 40rpx; font-weight: 700; letter-spacing: 8rpx; }
.hero-pet { position: absolute; right: -10rpx; top: 70rpx; width: 230rpx; height: 230rpx; }
.title { display: block; margin-top: 64rpx; font-family: var(--zy-font-display); font-size: 64rpx; font-weight: 700; line-height: 1.35; letter-spacing: 4rpx; color: var(--zy-brand-900); }
.definition { display: block; margin-top: 18rpx; max-width: 600rpx; font-size: 29rpx; line-height: 1.8; color: var(--zy-text); }
.pills { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 26rpx; }
.pill { display: flex; align-items: center; gap: 8rpx; padding: 10rpx 22rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .85); font-size: 24rpx; font-weight: 600; color: var(--zy-brand-700); box-shadow: 0 4rpx 12rpx rgba(36, 71, 143, .1); }

.body { max-width: 960rpx; margin: 0 auto; padding: 8rpx 32rpx 60rpx; }
.sec-head { display: flex; align-items: center; gap: 14rpx; margin: 12rpx 0 20rpx; }
.sec-mark { width: 8rpx; height: 34rpx; border-radius: 4rpx; background: var(--zy-cinnabar); }
.sec-title { font-family: var(--zy-font-display); font-size: 36rpx; font-weight: 700; }
.things { display: flex; flex-direction: column; gap: 18rpx; }
.thing { display: flex; align-items: center; gap: 22rpx; padding: 22rpx 26rpx; border-radius: 32rpx; box-shadow: var(--zy-shadow-card); }
.tone-blue { background: linear-gradient(120deg, #eef3fb, var(--zy-card-bg)); }
.tone-warm { background: linear-gradient(120deg, #fff4e8, var(--zy-card-bg)); }
.tone-pine { background: linear-gradient(120deg, #e6f3ee, var(--zy-card-bg)); }
.zy-theme-dark .thing { background: var(--zy-card-bg); }
.thing-img { width: 120rpx; height: 120rpx; flex-shrink: 0; }
.thing-copy { flex: 1; min-width: 0; }
.thing-title { display: block; font-size: 32rpx; font-weight: 700; color: var(--zy-title); }
.thing-sub { display: block; margin-top: 6rpx; font-size: 25rpx; line-height: 1.6; color: var(--zy-text); }

.privacy-card { margin-top: 28rpx; padding: 30rpx 28rpx 18rpx; border-radius: 32rpx; background: var(--zy-card-bg); box-shadow: var(--zy-shadow-card); }
.privacy-head { display: flex; align-items: center; gap: 12rpx; margin-bottom: 18rpx; }
.privacy-title { font-family: var(--zy-font-display); font-size: 32rpx; font-weight: 700; }
.privacy-line { display: flex; align-items: flex-start; gap: 14rpx; margin-bottom: 14rpx; font-size: 26rpx; line-height: 1.6; color: var(--zy-text); }
.check { width: 34rpx; height: 34rpx; border-radius: 50%; flex-shrink: 0; margin-top: 4rpx; background: var(--zy-pine, #2f7d68); display: flex; align-items: center; justify-content: center; }
.policy-link { margin: 4rpx 0 0; padding: 8rpx 0; min-height: 60rpx; font-size: 25rpx; justify-content: flex-start; }
.disclaimer { display: block; margin-top: 26rpx; text-align: center; font-size: 22rpx; line-height: 1.8; color: var(--zy-muted); }

.onboarding-actions { position: fixed; left: 0; right: 0; bottom: 0; z-index: 20; display: flex; flex-direction: column; align-items: stretch; gap: 6rpx; padding: 20rpx 32rpx calc(16rpx + env(safe-area-inset-bottom)); background: rgba(255, 255, 255, .9); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); box-shadow: 0 -8rpx 28rpx rgba(20, 36, 72, .08); }
.zy-theme-dark .onboarding-actions { background: rgba(28, 39, 64, .92); }
.agree-btn { margin: 0; border-radius: 999rpx; }
.later-btn { margin: 0; min-height: 72rpx; background: transparent; border: 0; color: var(--zy-muted); font-size: 26rpx; }
.later-btn::after { border: 0; }
</style>
