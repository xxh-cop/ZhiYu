<template>
	<view class="home-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
		<view class="home-inner">
			<!-- ① 青花页眉：品牌 + 问候 + 节气黄历 -->
			<view class="home-top">
				<view class="top-wave zy-wave-bg" aria-hidden="true"></view>
				<view class="home-header">
					<view class="wordmark" aria-label="织愈">
						<ZySeal text="织愈" size="md" />
						<view class="wordmark-copy">
							<text class="brand-name">织愈</text>
							<text class="brand-caption">一针一线 · 慢慢变好</text>
						</view>
					</view>
					<view class="header-right">
						<AccessibleButton class="icon-btn" aria-label="查看通知" @click="goNotifications">
							<ZyIcon name="bell" :size="38" tone="title" />
							<view v-if="noticeState.unread > 0" class="bell-dot" aria-hidden="true"></view>
						</AccessibleButton>
						<AccessibleButton class="avatar" aria-label="打开我的页面" @click="goProfile">{{ avatarLetter }}</AccessibleButton>
					</view>
				</view>

				<text class="greet zy-t-serif">{{ greet }}，{{ nickname }}</text>

				<view class="almanac" role="group" :aria-label="`今日节气${term.name}，宜${almanacToday.yi.join('、')}，忌${almanacToday.ji.join('、')}`">
					<view class="almanac-term">
						<text class="term-name">{{ term.name }}</text>
						<text class="term-label">节 气</text>
					</view>
					<view class="almanac-body">
						<text class="almanac-date">{{ dateLine }}</text>
						<text class="almanac-line">{{ term.line }}</text>
						<view class="almanac-tags">
							<text class="tag-yi">宜</text>
							<text v-for="y in almanacToday.yi" :key="y" class="tag-word">{{ y }}</text>
							<text class="tag-ji">忌</text>
							<text v-for="j in almanacToday.ji" :key="j" class="tag-word">{{ j }}</text>
						</view>
						<text class="almanac-next">距「{{ upcoming.name }}」还有 {{ upcoming.days }} 天</text>
					</view>
				</view>
			</view>

			<view class="home-body">
				<!-- 继续上次 -->
				<AccessibleButton v-if="lastVisit" class="resume-card" :aria-label="`继续上次：${lastVisit.label}`" @click="openRecent(lastVisit.path)">
					<view class="resume-icon" aria-hidden="true"><ZyIcon name="clock" :size="34" tone="brand" /></view>
					<view class="resume-copy">
						<text class="resume-eyebrow">继续上次</text>
						<text class="resume-title">{{ lastVisit.label }}</text>
					</view>
					<text class="resume-go">继续</text>
				</AccessibleButton>
				<scroll-view v-if="recentRest.length" class="recent-scroll" scroll-x :show-scrollbar="false">
					<view class="recent-line">
						<AccessibleButton v-for="item in recentRest" :key="item.path" class="recent-chip" :aria-label="`打开${item.label}`" @click="openRecent(item.path)">{{ item.label }}</AccessibleButton>
					</view>
				</scroll-view>
				<view v-if="showConsentBanner" class="consent-banner" role="status">
					<text class="consent-banner-text">同意隐私政策后，才能使用对话与记录。</text>
					<AccessibleButton variant="secondary" size="sm" @click="goOnboarding">去开启</AccessibleButton>
				</view>

				<!-- ② 今日一器 -->
				<view class="sec-head">
					<view class="sec-mark" aria-hidden="true"></view>
					<text class="sec-title">今日一器</text>
					<text class="sec-note">001 · 赣绣</text>
				</view>
				<view class="today-card" role="button" tabindex="0" aria-label="进入今日的展间，赣绣小书签" @click="goDailyProject" @keydown.enter.prevent="goDailyProject">
					<image class="today-img" src="/static/blueprint/hero-cloth.jpg" mode="aspectFill" aria-hidden="true" />
					<view class="today-scrim" aria-hidden="true"></view>
					<view class="today-cloud zy-cloud-bg" aria-hidden="true"></view>
					<view class="today-seal"><ZySeal text="今日" size="md" tilt /></view>
					<view class="today-copy">
						<text class="today-kicker">江西 · 赣绣 · 小书签</text>
						<text class="today-title zy-t-serif">把今天，绣进一寸光里</text>
						<view class="today-meta">
							<text class="meta-pill">约 60 分钟</text>
							<text class="meta-pill">零基础</text>
							<text class="meta-pill">做完可带走</text>
						</view>
					</view>
				</view>
				<AccessibleButton variant="primary" size="lg" class="today-cta" @click="goDailyProject">开始今天这一件</AccessibleButton>
				<text class="today-credit">夏布与绣线 · AI 生成概念视觉，非作品实拍</text>

				<!-- ③ 从这里开始：四宫格 -->
				<view class="sec-head">
					<view class="sec-mark" aria-hidden="true"></view>
					<text class="sec-title">从这里开始</text>
				</view>
				<view class="entry-grid">
					<AccessibleButton class="entry entry--listen" aria-label="和小织聊聊，进入情绪倾听" @click="goEmotion">
						<view class="entry-copy">
							<text class="entry-title">和小织聊聊</text>
							<text class="entry-sub">说不出口的，留在这里</text>
						</view>
						<image class="entry-mascot" src="/static/mascot/xiaozhi-listening.png" mode="aspectFit" aria-hidden="true" />
					</AccessibleButton>
					<AccessibleButton class="entry entry--photo" aria-label="换一件手作" @click="goCraftRecommend">
						<image class="entry-img" src="/static/covers/jianzhi.jpg" mode="aspectFill" aria-hidden="true" />
						<view class="entry-shade" aria-hidden="true"></view>
						<view class="entry-copy on-photo">
							<text class="entry-title">换一件手作</text>
							<text class="entry-sub">非遗入门 · 按时长挑</text>
						</view>
					</AccessibleButton>
					<AccessibleButton class="entry entry--photo" aria-label="进入非遗纹样馆" @click="goHeritage">
						<image class="entry-img" src="/static/heritage/real/porcelain.jpg" mode="aspectFill" aria-hidden="true" />
						<view class="entry-shade" aria-hidden="true"></view>
						<view class="entry-copy on-photo">
							<text class="entry-title">纹样馆</text>
							<text class="entry-sub">青花 · 剪纸 · 刺绣 · 编织</text>
						</view>
					</AccessibleButton>
					<AccessibleButton class="entry entry--photo" aria-label="认识非遗传承人" @click="goInheritors">
						<image class="entry-img" src="/static/heritage/real/papercut.jpg" mode="aspectFill" aria-hidden="true" />
						<view class="entry-shade" aria-hidden="true"></view>
						<view class="entry-copy on-photo">
							<text class="entry-title">手艺人</text>
							<text class="entry-sub">手艺背后，是人</text>
						</view>
					</AccessibleButton>
				</view>

				<!-- ④ 节气手作 -->
				<AccessibleButton class="term-card" :aria-label="`${term.name}时节推荐：${term.craft}`" @click="goCraftRecommend">
					<view class="term-card-band zy-band-meander" aria-hidden="true"></view>
					<view class="term-card-row">
						<ZySeal :text="term.name" size="lg" tone="blue" />
						<view class="term-card-copy">
							<text class="term-card-kicker">{{ term.name }}时节 · 应景手作</text>
							<text class="term-card-title zy-t-serif">{{ term.craft }}</text>
						</view>
						<ZyIcon name="arrow" :size="32" tone="brand" />
					</view>
				</AccessibleButton>

				<!-- ⑤ 社区新作 -->
				<view v-if="works.length" class="sec-head">
					<view class="sec-mark" aria-hidden="true"></view>
					<text class="sec-title">织友新作</text>
					<AccessibleButton class="sec-more" @click="goWorks">看全部</AccessibleButton>
				</view>
				<scroll-view v-if="works.length" class="works-scroll" scroll-x :show-scrollbar="false">
					<view class="works-track">
						<view v-for="w in works" :key="w.id" class="work-mini" role="button" tabindex="0" :aria-label="'查看作品 ' + w.title" @click="goWork(w)">
							<image class="work-mini-img" :src="coverFor(w)" mode="aspectFill" lazy-load />
							<text class="work-mini-title">{{ w.title }}</text>
							<view class="work-mini-meta">
								<text class="work-mini-author">{{ w.authorName }}</text>
								<view class="work-mini-like"><ZyIcon name="heart" :size="22" :tone="w.liked ? 'care' : 'muted'" /><text>{{ w.likeCount || 0 }}</text></view>
							</view>
						</view>
					</view>
				</scroll-view>

				<!-- ⑥ 今日笺：青花盘 -->
				<view class="plate-wrap">
					<view class="plate zy-porcelain-rim">
						<text class="plate-quote zy-t-serif">手作不是任务，<br/>是把注意力<br/>放回手上。</text>
						<text class="plate-src">— 今日笺 —</text>
					</view>
				</view>

				<view class="foot-row">
					<text class="home-foot">本系统不能替代专业心理咨询</text>
					<AccessibleButton class="support-link" @click="goSupport">需要支持</AccessibleButton>
				</view>
			</view>

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
import ZyIcon from '../../components/ZyIcon.vue'
import ZySeal from '../../components/ZySeal.vue'
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request, http } from '../../utils/request'
import { cached, TTL_KEYS } from '../../utils/ttlCache'
import { useUserStore } from '../../stores/user'
import { useInteraction } from '../../composables/useInteraction'
import { useCompanionNudge } from '../../composables/useCompanionNudge'
import { useNotifications } from '../../composables/useNotifications'
import { getRecent } from '../../utils/recent'
import { isTabRoute } from '../../utils/tabbar'
import { currentTerm, nextTerm, almanac, greeting } from '../../utils/solar-terms'
import { coverFor } from '../../utils/covers'

const { reducedMotion } = useInteraction()
const userStore = useUserStore()
const { state: noticeState, refreshUnread } = useNotifications()
const showConsentBanner = ref(false)
const recent = ref([])
const lastVisit = computed(() => recent.value[0] || null)
const recentRest = computed(() => recent.value.slice(1, 5))
const works = ref([])

const now = ref(new Date())
const term = computed(() => currentTerm(now.value))
const upcoming = computed(() => nextTerm(now.value))
const almanacToday = computed(() => almanac(now.value))
const greet = computed(() => greeting(now.value))
const dateLine = computed(() => {
	const d = now.value
	const week = ['日', '一', '二', '三', '四', '五', '六']
	return `${d.getMonth() + 1}月${d.getDate()}日 · 星期${week[d.getDay()]}`
})

function openRecent(path) {
	if (!path) return
	const url = path.split('?')[0]
	if (isTabRoute(url)) uni.switchTab({ url })
	else uni.navigateTo({ url: path })
}

const nickname = computed(() => (userStore.userInfo && userStore.userInfo.nickname) || '同学')
const avatarLetter = computed(() => (nickname.value && nickname.value.charAt(0)) || '同')

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
	now.value = new Date()
	loadCurrentUser()
	ensurePrivacyOnboarding()
	refreshUnread()
	loadWorks()
	recent.value = getRecent()
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

/* 首页只取最新 6 件做横滑预览；失败静默隐藏该区块 */
async function loadWorks() {
	try {
		const data = await http.get('/api/works', { data: { page: 1, size: 6, sort: 'latest' } })
		works.value = (data && data.records) || []
	} catch (e) {
		works.value = []
	}
}

function goDailyProject() { uni.navigateTo({ url: '/pages/craft/detail?id=102' }) }
function goEmotion() { uni.navigateTo({ url: '/pages/emotion/chat' }) }
function goNotifications() { uni.navigateTo({ url: '/pages/notification/index' }) }
function goCraftRecommend() { uni.navigateTo({ url: '/pages/craft/recommend' }) }
function goHeritage() { uni.navigateTo({ url: '/sub-heritage/pages/heritage/gallery' }) }
function goInheritors() { uni.navigateTo({ url: '/pages/inheritor/list' }) }
function goWorks() { uni.switchTab({ url: '/pages/work/list' }) }
function goWork(w) { uni.navigateTo({ url: `/pages/work/detail?id=${w.id}` }) }
function goProfile() { uni.switchTab({ url: '/pages/profile/index' }) }
function goSupport() { uni.navigateTo({ url: '/pages/support/index' }) }
</script>

<style scoped>
.home-page { min-height: 100vh; color: var(--zy-title); padding: 0 0 calc(150rpx + env(safe-area-inset-bottom, 0px)); box-sizing: border-box; background: var(--zy-bg); }
/* #ifdef MP */
.home-page { padding-bottom: calc(130rpx + env(safe-area-inset-bottom)); }
/* #endif */
.home-inner { max-width: 1080px; margin: auto; }

/* ── ① 青花页眉 ── */
.home-top { position: relative; overflow: hidden; padding: calc(28rpx + env(safe-area-inset-top, 0px)) 32rpx 36rpx; background: var(--zy-brand-50); border-radius: 0 0 48rpx 48rpx; }
.top-wave { position: absolute; right: -40rpx; top: 0; width: 520rpx; height: 100%; }
.home-header { position: relative; display: flex; justify-content: space-between; align-items: center; }
.wordmark { display: flex; align-items: center; gap: 16rpx; }
.wordmark-copy { display: flex; flex-direction: column; }
.brand-name { font-family: var(--zy-font-display); font-size: 40rpx; font-weight: 700; letter-spacing: 8rpx; line-height: 1.2; color: var(--zy-title); }
.brand-caption { font-size: 22rpx; color: var(--zy-muted); letter-spacing: 2rpx; }
.header-right { display: flex; align-items: center; gap: 16rpx; }
.icon-btn { position: relative; width: 76rpx; height: 76rpx; min-width: 76rpx; min-height: 76rpx; padding: 0; margin: 0; border-radius: 50%; background: var(--zy-card-bg); border: 0; box-shadow: var(--zy-shadow-card); }
.bell-dot { position: absolute; top: 14rpx; right: 16rpx; width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--zy-cinnabar); border: 3rpx solid var(--zy-card-bg); }
.avatar { width: 76rpx; height: 76rpx; min-width: 76rpx; min-height: 76rpx; padding: 0; margin: 0; border-radius: 50%; background: var(--zy-brand-600); color: #fff; border: 0; font-family: var(--zy-font-display); font-size: 32rpx; font-weight: 700; box-shadow: var(--zy-shadow-cta); }

.greet { position: relative; display: block; margin-top: 36rpx; font-size: 48rpx; font-weight: 700; letter-spacing: 2rpx; color: var(--zy-title); }

/* 黄历卡 */
.almanac { position: relative; display: flex; margin-top: 24rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); overflow: hidden; }
.almanac-term { width: 150rpx; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10rpx; background: var(--zy-brand-600); color: #fff; padding: 24rpx 0; }
.term-name { font-family: var(--zy-font-display); font-size: 52rpx; font-weight: 700; writing-mode: vertical-rl; letter-spacing: 10rpx; line-height: 1; }
.term-label { font-size: 20rpx; letter-spacing: 4rpx; opacity: .85; }
.almanac-body { flex: 1; min-width: 0; padding: 22rpx 26rpx; display: flex; flex-direction: column; gap: 8rpx; }
.almanac-date { font-size: 22rpx; color: var(--zy-muted); letter-spacing: 1rpx; }
.almanac-line { font-family: var(--zy-font-display); font-size: 30rpx; line-height: 1.55; color: var(--zy-title); }
.almanac-tags { display: flex; flex-wrap: wrap; align-items: center; gap: 8rpx; margin-top: 4rpx; }
.tag-yi, .tag-ji { width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 8rpx; font-family: var(--zy-font-display); font-size: 24rpx; font-weight: 700; color: #fff; }
.tag-yi { background: var(--zy-pine, #2f7d68); }
.tag-ji { background: var(--zy-cinnabar); margin-left: 10rpx; }
.tag-word { font-size: 24rpx; color: var(--zy-text); }
.almanac-next { font-size: 22rpx; color: var(--zy-brand-600); }

/* ── 主体 ── */
.home-body { padding: 28rpx 32rpx 0; }
.resume-card { display: flex; align-items: center; gap: 20rpx; width: 100%; margin: 0 0 16rpx; padding: 22rpx 24rpx; background: var(--zy-card-bg); border: 2rpx solid var(--zy-card-line); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); text-align: left; }
.resume-icon { width: 72rpx; height: 72rpx; border-radius: 50%; background: var(--zy-brand-50); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.resume-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.resume-eyebrow { font-size: 22rpx; color: var(--zy-muted); }
.resume-title { font-size: 30rpx; font-weight: 600; color: var(--zy-title); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.resume-go { flex-shrink: 0; padding: 10rpx 26rpx; border-radius: 999rpx; background: var(--zy-brand-600); color: #fff; font-size: 24rpx; font-weight: 600; }
.recent-scroll { width: 100%; white-space: nowrap; margin-bottom: 8rpx; }
.recent-line { display: inline-flex; gap: 12rpx; padding: 4rpx 2rpx 12rpx; }
.recent-chip { margin: 0; min-height: 60rpx; padding: 0 24rpx; border-radius: 999rpx; background: var(--zy-card-bg); border: 2rpx solid var(--zy-border); color: var(--zy-text); font-size: 24rpx; white-space: nowrap; }
.consent-banner { display: flex; align-items: center; gap: 16rpx; margin: 8rpx 0 16rpx; padding: 20rpx 24rpx; border-radius: var(--zy-radius-card); background: var(--zy-gamboge-50, #fbf1de); }
.consent-banner-text { flex: 1; min-width: 0; font-size: 24rpx; line-height: 1.6; color: var(--zy-warning-text); }

/* 分区标题：朱砂短竖 + 宋体 */
.sec-head { display: flex; align-items: center; gap: 14rpx; margin: 40rpx 0 20rpx; }
.sec-mark { width: 8rpx; height: 34rpx; border-radius: 4rpx; background: var(--zy-cinnabar); }
.sec-title { font-family: var(--zy-font-display); font-size: 36rpx; font-weight: 700; letter-spacing: 2rpx; color: var(--zy-title); }
.sec-note { margin-left: auto; font-size: 22rpx; color: var(--zy-muted); letter-spacing: 2rpx; }
.sec-more { margin: 0 0 0 auto; min-height: 56rpx; padding: 0 22rpx; border-radius: 999rpx; background: var(--zy-brand-50); color: var(--zy-brand-700); font-size: 24rpx; font-weight: 600; border: 0; }

/* ── ② 今日一器 ── */
.today-card { position: relative; height: 520rpx; border-radius: var(--zy-radius-feature); overflow: hidden; background: var(--zy-brand-900); box-shadow: var(--zy-shadow-pop); }
.today-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.today-scrim { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(13, 31, 69, 0) 30%, rgba(13, 31, 69, .88) 100%); }
.today-cloud { position: absolute; right: 24rpx; bottom: 150rpx; width: 128rpx; height: 64rpx; }
.today-seal { position: absolute; top: 28rpx; right: 28rpx; }
.today-copy { position: absolute; left: 32rpx; right: 32rpx; bottom: 30rpx; }
.today-kicker { display: block; font-size: 22rpx; letter-spacing: 3rpx; color: rgba(255, 255, 255, .8); }
.today-title { display: block; margin-top: 10rpx; font-size: 48rpx; font-weight: 700; line-height: 1.3; color: #fff; letter-spacing: 2rpx; }
.today-meta { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 18rpx; }
.meta-pill { padding: 6rpx 18rpx; border-radius: 999rpx; background: rgba(255, 255, 255, .18); color: #fff; font-size: 22rpx; }
.today-cta { width: 100%; margin: 24rpx 0 0; }
.today-credit { display: block; margin-top: 12rpx; text-align: center; font-size: 20rpx; color: var(--zy-faint); }

/* ── ③ 四宫格 ── */
.entry-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; }
.entry { position: relative; height: 260rpx; margin: 0; padding: 0; border: 0; border-radius: var(--zy-radius-card); overflow: hidden; text-align: left; box-shadow: var(--zy-shadow-card); display: block; }
.entry:active { transform: scale(.98); }
.entry--listen { background: var(--zy-brand-600); }
.entry--listen .entry-copy { position: absolute; left: 24rpx; top: 24rpx; right: 24rpx; }
.entry--listen .entry-title { color: #fff; }
.entry--listen .entry-sub { color: rgba(255, 255, 255, .82); }
.entry-mascot { position: absolute; right: -8rpx; bottom: -10rpx; width: 190rpx; height: 190rpx; }
.entry-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.entry-shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(13, 31, 69, 0) 25%, rgba(13, 31, 69, .82) 100%); }
.entry-copy { display: flex; flex-direction: column; gap: 4rpx; }
.entry-copy.on-photo { position: absolute; left: 22rpx; right: 22rpx; bottom: 20rpx; }
.entry-title { font-family: var(--zy-font-display); font-size: 34rpx; font-weight: 700; color: #fff; letter-spacing: 2rpx; }
.entry-sub { font-size: 22rpx; color: rgba(255, 255, 255, .85); line-height: 1.5; }

/* ── ④ 节气手作 ── */
.term-card { width: 100%; margin: 28rpx 0 0; padding: 0 0 26rpx; background: var(--zy-card-bg); border: 2rpx solid var(--zy-card-line); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); overflow: hidden; text-align: left; display: block; }
.term-card-band { margin-bottom: 22rpx; }
.term-card-row { display: flex; align-items: center; gap: 24rpx; padding: 0 26rpx; }
.term-card-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.term-card-kicker { font-size: 22rpx; color: var(--zy-muted); letter-spacing: 1rpx; }
.term-card-title { font-size: 36rpx; font-weight: 700; color: var(--zy-title); }

/* ── ⑤ 织友新作 ── */
.works-scroll { width: 100%; white-space: nowrap; }
.works-track { display: inline-flex; gap: 20rpx; padding: 4rpx 2rpx 16rpx; }
.work-mini { display: inline-flex; flex-direction: column; width: 260rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-card); overflow: hidden; box-shadow: var(--zy-shadow-card); white-space: normal; }
.work-mini-img { width: 260rpx; height: 300rpx; display: block; background: var(--zy-bg-sunken); }
.work-mini-title { padding: 16rpx 18rpx 0; font-size: 26rpx; font-weight: 600; color: var(--zy-title); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.work-mini-meta { display: flex; align-items: center; justify-content: space-between; padding: 6rpx 18rpx 18rpx; font-size: 22rpx; color: var(--zy-muted); }
.work-mini-like { display: flex; align-items: center; gap: 4rpx; }

/* ── ⑥ 青花盘 ── */
.plate-wrap { display: flex; justify-content: center; margin: 56rpx 0 12rpx; }
.plate { width: 460rpx; height: 460rpx; border-radius: 50%; background: var(--zy-card-bg); display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; box-shadow: var(--zy-shadow-card), inset 0 0 0 4rpx var(--zy-brand-600), inset 0 0 0 12rpx var(--zy-card-bg), inset 0 0 0 14rpx var(--zy-brand-200); }
.plate-quote { font-size: 36rpx; line-height: 1.8; color: var(--zy-brand-700); letter-spacing: 2rpx; }
.plate-src { margin-top: 14rpx; font-size: 22rpx; color: var(--zy-cinnabar); letter-spacing: 4rpx; }

.foot-row { display: flex; align-items: center; justify-content: center; gap: 16rpx; margin-top: 40rpx; padding: 24rpx 0; }
.home-foot { color: var(--zy-muted); font-size: 22rpx; }
.support-link { margin: 0; padding: 0 8rpx; min-height: 56rpx; font-size: 24rpx; }

.motion-reduced .entry:active { transform: none; }

@media (min-width: 850px) {
	.home-top { padding: 28px 48px 32px; }
	.home-body { padding: 24px 48px 0; }
	.entry-grid { grid-template-columns: repeat(4, 1fr); }
	.today-card { height: 420px; }
}
</style>
