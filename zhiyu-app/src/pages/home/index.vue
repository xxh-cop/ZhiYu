<template>
	<view class="home-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
		<view class="home-inner">
			<!-- 页头：只留品牌、通知、头像；不再用口号当标题 -->
			<view class="home-header">
				<view class="wordmark" aria-label="织愈，一线之间">
					<text class="brand-name">织愈</text>
					<view class="brand-rule" aria-hidden="true"></view>
					<text class="brand-caption">一线之间</text>
				</view>
				<view class="header-right">
					<AccessibleButton class="bell" aria-label="查看通知" @click="goNotifications">
						<wd-icon name="notification" size="36rpx" color="#42524C" />
						<view v-if="noticeState.unread > 0" class="bell-dot" aria-hidden="true"></view>
					</AccessibleButton>
					<AccessibleButton class="avatar" aria-label="打开我的页面" @click="goProfile">{{avatarLetter}}</AccessibleButton>
				</view>
			</view>
			<text class="date-line">{{todayLine}}</text>
			<view v-if="showConsentBanner" class="consent-banner" role="status">
				<text class="consent-banner-text">同意隐私政策后，才能使用对话与记录。</text>
				<AccessibleButton variant="ghost" size="sm" @click="goOnboarding">去开启</AccessibleButton>
			</view>

			<!-- 今日一器：一张材料大图、一句今日文案、一个动作 -->
			<view class="today-hero">
				<image class="hero-img" src="/static/blueprint/hero-cloth.jpg" mode="aspectFill" aria-hidden="true" />
				<view class="hero-scrim" aria-hidden="true"></view>
				<view class="hero-meta-top">
					<text>今日一器</text>
					<text>001 / 赣绣</text>
				</view>
				<view class="hero-copy">
					<text class="hero-kicker">江西 · 赣绣 · 小书签</text>
					<text class="hero-title zy-t-serif">把今天，<br/>绣进一寸光里</text>
					<text class="hero-note">一针一线，不赶时间。约 60 分钟，做完可以带走。</text>
					<view class="hero-line" aria-hidden="true"></view>
					<AccessibleButton class="hero-cta" aria-label="进入今日的展间，赣绣小书签" @click="goDailyProject">
						<text>进入今日的展间</text>
						<text class="cta-arrow" aria-hidden="true">→</text>
					</AccessibleButton>
				</view>
				<text class="hero-credit">材料印象 · 夏布与绣线 / 概念视觉，非作品实拍</text>
			</view>

			<view class="after-hero">
				<!-- 今日还有：三行文字入口，不做卡片 -->
				<view class="section-label">
					<text>今日还有</text>
					<view class="section-rule" aria-hidden="true"></view>
				</view>
				<view class="text-nav">
					<AccessibleButton class="nav-item" aria-label="想说说，进入情绪倾听" @click="goEmotion">
						<text class="nav-index">01</text>
						<view class="nav-copy">
							<text class="nav-title">想说说</text>
							<text class="nav-sub">把说不出口的，留在这里</text>
						</view>
						<text class="nav-arrow" aria-hidden="true">→</text>
					</AccessibleButton>
					<AccessibleButton class="nav-item" aria-label="换一件手作" @click="goCraftRecommend">
						<text class="nav-index">02</text>
						<view class="nav-copy">
							<text class="nav-title">换一件</text>
							<text class="nav-sub">看看其他非遗手作与入门练习</text>
						</view>
						<text class="nav-arrow" aria-hidden="true">→</text>
					</AccessibleButton>
					<AccessibleButton class="nav-item" aria-label="进入非遗纹样馆" @click="goHeritage">
						<text class="nav-index">03</text>
						<view class="nav-copy">
							<text class="nav-title">看纹样</text>
							<text class="nav-sub">四类工艺，来源可查</text>
						</view>
						<text class="nav-arrow" aria-hidden="true">→</text>
					</AccessibleButton>
				</view>

				<view class="daily-quote">
					<view class="quote-rule" aria-hidden="true"></view>
					<text class="quote zy-t-serif">手作不是任务，<br/>是把注意力放回手上。</text>
					<text class="quote-src">今日笺 · 织愈</text>
				</view>

				<view class="foot-row">
					<text class="home-foot">本系统不能替代专业心理咨询</text>
					<AccessibleButton variant="ghost" size="sm" class="support-link" @click="goSupport">需要支持 →</AccessibleButton>
				</view>
			</view>

			<!-- 小织按需出现：低落打卡与创作完成后的低打扰陪伴 -->
			<CompanionNudge
				:open="nudgeOpen"
				:text="nudgeText"
				:action-text="nudgeKind === 'craft' ? '' : '和小织聊聊'"
				:face="nudgeKind === 'craft' ? 'happy' : 'care'"
				bottom-offset="calc(240rpx + env(safe-area-inset-bottom))"
				@action="onNudgeAction"
				@close="nudgeOpen = false"
			/>
		</view>
	</view>
</template>

<script setup>
import AccessibleButton from '../../components/AccessibleButton.vue'
import CompanionNudge from '../../components/CompanionNudge.vue'
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { cached, TTL_KEYS } from '../../utils/ttlCache'
import { useUserStore } from '../../stores/user'
import { useInteraction } from '../../composables/useInteraction'
import { useCompanionNudge } from '../../composables/useCompanionNudge'
import { useNotifications } from '../../composables/useNotifications'

const { reducedMotion } = useInteraction()
const userStore = useUserStore()
const { state: noticeState, refreshUnread } = useNotifications()
const showConsentBanner = ref(false)

const nickname = computed(() => (userStore.userInfo && userStore.userInfo.nickname) || '同学')
const avatarLetter = computed(() => (nickname.value && nickname.value.charAt(0)) || '同')
const todayLine = computed(() => {
	const d = new Date()
	const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
	return `${d.getMonth() + 1}月${d.getDate()}日 · ${week[d.getDay()]} · 宜起针`
})

/* 小织按需出现（F2 + R3）：频次闸门与展示状态 */
const nudgeQuota = useCompanionNudge()
const nudgeOpen = ref(false)
const nudgeText = ref('')
const nudgeKind = ref('mood')
function onNudgeAction() {
	if (nudgeKind.value === 'craft') { nudgeOpen.value = false; return }
	goEmotion()
}

onShow(() => {
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	loadCurrentUser()
	ensurePrivacyOnboarding()
	refreshUnread()
	const pending = nudgeQuota.takePendingNudge()
	if (pending && nudgeQuota.canShow(pending.quotaKey)) {
		nudgeQuota.markShown(pending.quotaKey)
		nudgeKind.value = 'craft'
		nudgeText.value = pending.text
		nudgeOpen.value = true
	}
})

async function ensurePrivacyOnboarding() {
	if (userStore.privacyAgreed === null) {
		await userStore.fetchPrivacyStatus()
	}
	if (userStore.privacyAgreed === 0 && !userStore.consentDismissed) {
		uni.reLaunch({ url: '/pages/onboarding/index' })
		return
	}
	showConsentBanner.value = userStore.privacyAgreed === 0 && userStore.consentDismissed
}

function goOnboarding() {
	uni.reLaunch({ url: '/pages/onboarding/index' })
}

async function loadCurrentUser() {
	try {
		const data = await cached(TTL_KEYS.authMe, 30000, () => request({ url: '/api/auth/me', method: 'GET' }))
		userStore.setUserInfo(data)
	} catch (e) {
		/* 静默：沿用已缓存的用户信息 */
	}
}

function goDailyProject() { uni.navigateTo({ url: '/pages/craft/detail?id=102' }) }
function goEmotion() { uni.navigateTo({ url: '/pages/emotion/chat' }) }
function goNotifications() { uni.navigateTo({ url: '/pages/notification/index' }) }
function goCraftRecommend() { uni.navigateTo({ url: '/pages/craft/recommend' }) }
function goHeritage() { uni.navigateTo({ url: '/sub-heritage/pages/heritage/gallery' }) }
function goWorks() { uni.switchTab({ url: '/pages/work/list' }) }
function goProfile() { uni.switchTab({ url: '/pages/profile/index' }) }
function goSupport() { uni.navigateTo({ url: '/pages/support/index' }) }
</script>

<style scoped>
.home-page { min-height: 100vh; color: var(--zy-title); padding: 0 0 calc(140rpx + env(safe-area-inset-bottom, 0px)); box-sizing: border-box; background: var(--zy-bg); padding-top: env(safe-area-inset-top, 0px); }
/* #ifdef MP */
.home-page { padding-bottom: calc(112rpx + env(safe-area-inset-bottom)); }
/* #endif */
.home-inner { max-width: 1080px; margin: auto; }

/* ── 页头 ── */
.home-header { display: flex; justify-content: space-between; align-items: center; padding: 32rpx 36rpx 0; }
.wordmark { display: flex; align-items: center; gap: 12rpx; }
.brand-name { font-family: var(--zy-serif); font-size: 48rpx; font-weight: 600; letter-spacing: 8rpx; line-height: 1.1; color: var(--zy-title); }
.brand-rule { width: 28rpx; height: 2rpx; background: var(--zy-care); }
.brand-caption { font-size: 20rpx; color: var(--zy-muted); letter-spacing: 4rpx; }
.header-right { display: flex; align-items: center; gap: 18rpx; }
.bell { position: relative; width: 64rpx; height: 64rpx; min-width: 64rpx; border: 1rpx solid var(--zy-border); border-radius: 0; background: transparent; padding: 0; margin: 0; }
.bell-dot { position: absolute; top: 12rpx; right: 14rpx; width: 12rpx; height: 12rpx; border-radius: 50%; background: var(--zy-care); border: 2rpx solid var(--zy-bg); }
.avatar { width: 64rpx; height: 64rpx; line-height: 64rpx; padding: 0; border-radius: 0; background: transparent; border: 1rpx solid var(--zy-border); color: var(--zy-brand-700); font-size: var(--zy-text-caption); margin: 0; min-width: 64rpx; }
.date-line { display: block; padding: 18rpx 36rpx 0; color: var(--zy-muted); font-size: 20rpx; letter-spacing: 2rpx; }
.consent-banner { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin: 20rpx 36rpx 0; padding: 14rpx 18rpx; border-top: 1rpx solid var(--zy-border); border-bottom: 1rpx solid var(--zy-border); background: transparent; }
.consent-banner-text { flex: 1; min-width: 0; font-size: var(--zy-text-micro); line-height: 1.6; color: var(--zy-text); }
.consent-banner button { flex-shrink: 0; margin: 0; padding: 0 8rpx; }

/* ── 今日一器：材料大图压字 ── */
.today-hero { position: relative; height: 64vh; min-height: 720rpx; max-height: 980rpx; margin-top: 24rpx; overflow: hidden; background: #0b1513; }
.hero-img { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
.hero-scrim { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(11,21,19,.18) 0%, rgba(11,21,19,.34) 34%, rgba(11,21,19,.84) 100%); }
.hero-meta-top { position: absolute; top: 30rpx; left: 36rpx; right: 36rpx; display: flex; justify-content: space-between; color: rgba(241,244,241,.72); font-size: 20rpx; letter-spacing: 3rpx; }
.hero-copy { position: absolute; left: 36rpx; right: 36rpx; bottom: 84rpx; }
.hero-kicker { display: block; color: var(--zy-brand-300); font-size: 22rpx; letter-spacing: 3rpx; margin-bottom: 20rpx; }
.hero-title { display: block; color: var(--zy-d-text); font-size: 78rpx; line-height: 1.24; font-weight: 500; letter-spacing: 2rpx; }
.hero-note { display: block; color: rgba(241,244,241,.8); font-size: 24rpx; line-height: 1.8; margin-top: 22rpx; max-width: 86%; }
.hero-line { width: 48rpx; height: 2rpx; background: var(--zy-care); margin: 30rpx 0 22rpx; }
.hero-cta { display: inline-flex; align-items: center; gap: 14rpx; height: 76rpx; padding: 0 28rpx; background: transparent; border: 1rpx solid rgba(241,244,241,.62); border-radius: 0; color: var(--zy-d-text); font-size: 22rpx; letter-spacing: 2rpx; box-shadow: none; margin: 0; }
.hero-cta:active { background: rgba(241,244,241,.12); }
.cta-arrow { color: var(--zy-brand-300); font-size: 26rpx; }
.hero-credit { position: absolute; left: 36rpx; right: 36rpx; bottom: 26rpx; color: rgba(241,244,241,.5); font-size: 18rpx; letter-spacing: 1rpx; }

/* ── 今日还有：文字入口 ── */
.after-hero { padding: 44rpx 36rpx 0; }
.section-label { display: flex; align-items: center; gap: 18rpx; color: var(--zy-muted); font-size: 20rpx; letter-spacing: 3rpx; }
.section-rule { flex: 1; height: 1rpx; background: var(--zy-border); }
.text-nav { margin-top: 14rpx; }
.nav-item:first-child { border-top: 0; }
.nav-item { display: flex; align-items: center; gap: 18rpx; width: 100%; padding: 28rpx 0; background: transparent; border: 0; border-top: 1rpx solid var(--zy-border); border-radius: 0; text-align: left; line-height: 1.5; margin: 0; color: var(--zy-title); }
.nav-index { width: 52rpx; flex-shrink: 0; color: var(--zy-muted); font-family: var(--zy-serif); font-size: 20rpx; letter-spacing: 1rpx; }
.nav-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.nav-title { font-size: 34rpx; font-weight: 500; letter-spacing: 1rpx; }
.nav-sub { margin-top: 6rpx; color: var(--zy-muted); font-size: 22rpx; }
.nav-arrow { color: var(--zy-muted); font-size: 26rpx; }

/* ── 今日笺 ── */
.daily-quote { margin-top: 86rpx; padding: 0 2rpx 0 26rpx; border-left: 2rpx solid var(--zy-care); }
.quote-rule { display: none; }
.quote { display: block; color: var(--zy-title); font-size: 38rpx; line-height: 1.9; letter-spacing: 1rpx; }
.quote-src { display: block; margin-top: 16rpx; color: var(--zy-muted); font-size: 20rpx; letter-spacing: 3rpx; }

/* ── 页脚 ── */
.foot-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 72rpx; padding-top: 24rpx; border-top: 1rpx solid var(--zy-border); }
.home-foot { color: var(--zy-muted); font-size: 20rpx; }
.support-link { margin: 0; padding: 0; color: var(--zy-brand-700); font-size: 20rpx; }

button::after { border: 0; }
button:active { opacity: .9; }
button:focus-visible { outline: 3px solid var(--zy-brand-700); outline-offset: 3px; }
.motion-reduced view, .motion-reduced text, .motion-reduced image, .motion-reduced button, .motion-reduced input, .motion-reduced textarea, .motion-reduced label, .motion-reduced scroll-view, .motion-reduced video, .motion-reduced camera, .motion-reduced canvas, .motion-reduced slider, .motion-reduced checkbox, .motion-reduced checkbox-group, .motion-reduced svg, .motion-reduced g, .motion-reduced path, .motion-reduced ellipse, .motion-reduced circle, .motion-reduced defs, .motion-reduced clippath, .motion-reduced lineargradient, .motion-reduced radialgradient, .motion-reduced stop { animation: none !important; transition: none !important; }

@media (min-width: 850px) {
	.home-page { padding: 0 0 40px; }
	.home-header { padding: 28px 48px 0; }
	.date-line { padding: 14px 48px 0; }
	.today-hero { height: 620px; margin-top: 22px; }
	.after-hero { padding: 44px 48px 0; }
	.hero-title { font-size: 54px; }
	.hero-copy { left: 48px; right: 48px; bottom: 72px; }
	.hero-meta-top { left: 48px; right: 48px; }
	.hero-credit { left: 48px; right: 48px; }
}
</style>
