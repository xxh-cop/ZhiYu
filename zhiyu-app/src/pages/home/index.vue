<template>
	<view class="home-page" :class="{'motion-reduced':reducedMotion}">
		<view class="home-inner">
			<!-- 页头 -->
			<view class="home-header">
				<view class="wordmark">
					<image src="/static/logo.png" aria-hidden="true"/>
					<view>
						<text class="brand-name">织愈</text>
						<text class="brand-caption">给自己，一点慢时光</text>
					</view>
				</view>
				<AccessibleButton class="avatar" aria-label="打开我的页面" @click="goProfile">{{avatarLetter}}</AccessibleButton>
			</view>
			<view class="hello-row">
				<text>{{welcome}}</text>
				<text>你好，{{nickname}} ↗</text>
			</view>

			<!-- 主视觉（保留）：开始一件小手作 -->
			<view class="home-hero">
				<view class="hero-copy">
					<text class="eyebrow">A MOMENT FOR YOURSELF</text>
					<text class="hero-title">慢一点，<br/>给心情一个家。</text>
					<text class="hero-note">从手边的一张纸、一根线开始，<br/>留一小段时间，给此刻的自己。</text>
					<AccessibleButton class="hero-button" @click="goRecommend">开始一件小手作 <text>↗</text></AccessibleButton>
				</view>
				<image class="hero-art" src="/static/brand/weave-scene.png" mode="aspectFit" aria-hidden="true"/>
				<text class="hero-bottom">用一针一线，连接生活里的小确幸</text>
			</view>

			<!-- 一、现在可以做什么：倾诉 / 推荐 / 创作 -->
			<view class="section-head">
				<view><text class="eyebrow">WHAT NOW</text><text class="section-title">现在，想怎么度过这几分钟？</text></view>
			</view>
			<view class="action-grid">
				<AccessibleButton class="action-card" @click="goEmotion">
					<view class="action-pet"><XiaotuanPet :size="72" @open="goEmotion"/></view>
					<text class="action-kicker">01 / 倾诉</text>
					<text class="action-title">和小织聊聊</text>
					<text class="action-sub">把想说的慢慢说</text>
				</AccessibleButton>
				<AccessibleButton class="action-card" @click="goCraftRecommend">
					<view class="action-icon icon-recommend"><view class="icon-yarn"></view></view>
					<text class="action-kicker">02 / 推荐</text>
					<text class="action-title">看看今天的手作</text>
					<text class="action-sub">按此刻心情为你挑选</text>
				</AccessibleButton>
				<AccessibleButton class="action-card" @click="goCreate">
					<view class="action-icon icon-create"><view class="icon-needle"></view></view>
					<text class="action-kicker">03 / 创作</text>
					<text class="action-title">{{activeSession ? '继续上次的创作' : '开始一次创作'}}</text>
					<text class="action-sub">{{activeSession ? activeSessionLabel : '跟着步骤，慢慢来'}}</text>
				</AccessibleButton>
			</view>

			<!-- 二、今天状态如何：心情打卡 + 连续记录 -->
			<view class="section-head">
				<view><text class="eyebrow">CHECK IN</text><text class="section-title">此刻，你感觉怎么样？</text></view>
				<text class="small-label">只留在当前页面</text>
			</view>
			<view class="mood-card">
				<AccessibleButton v-for="(m,i) in moods" :key="m.label" class="mood" :class="{chosen:moodIndex===i}" :aria-pressed="moodIndex===i" @click="selectMood(i)">
					<view class="face" :class="'face-'+i"><view class="eyes"></view><view class="mouth"></view></view>
					<text>{{m.label}}</text>
				</AccessibleButton>
			</view>
			<view v-if="moodIndex!==null" class="mood-response">
				<text>{{moodIndex>=3?'今天也辛苦了。想说说的话，小织在这里。':'谢谢你留意此刻的感受。跟着自己的节奏就好。'}}</text>
				<AccessibleButton @click="goEmotion">和小织聊聊 →</AccessibleButton>
			</view>
			<view class="stats-card" aria-live="polite">
				<view class="stat">
					<text class="stat-num">{{statsLoaded ? (stats.streakDays ?? 0) : '—'}}</text>
					<text class="stat-label">连续创作（天）</text>
				</view>
				<view class="stat">
					<text class="stat-num">{{statsLoaded ? (stats.totalWorks ?? 0) : '—'}}</text>
					<text class="stat-label">我的作品</text>
				</view>
				<view class="stat">
					<text class="stat-num">{{statsLoaded ? (stats.totalMinutes ?? 0) : '—'}}</text>
					<text class="stat-label">累计专注（分钟）</text>
				</view>
			</view>

			<!-- AM1 情绪基线提醒：温和提示卡（非弹窗），仅首页一处，不重复打扰 -->
			<view v-if="baselineNudge" class="nudge-card" role="status">
				<text class="nudge-text">这几天低落的时刻似乎多了一些。不用着急——也许一件小手作、几分钟慢时光，会让今天好过一点点。</text>
				<AccessibleButton v-if="baselineNudge.actionType === 'craft'" class="nudge-action" @click="goCraftRecommend">去看看今天的手作 →</AccessibleButton>
				<text class="nudge-disclaimer">这只是温柔的提醒，不是任何判断。本系统不能替代专业心理咨询。</text>
			</view>

			<!-- 小织陪伴卡（保留入口，形象升级为线团小兽） -->
			<AccessibleButton class="pet-card" aria-label="和小织聊聊" @click="goEmotion">
				<XiaotuanPet :size="96"/>
				<view class="pet-copy">
					<text class="pet-label">小织 · 你的创作伙伴</text>
					<text class="pet-title">我在这里，陪你慢慢来。</text>
					<text class="pet-hint">聊聊此刻，或从一件小手作开始</text>
				</view>
				<text class="pet-arrow">↗</text>
			</AccessibleButton>

			<!-- 呼吸与音乐（保留） -->
			<view class="calm-grid">
				<AccessibleButton class="practice breath" @click="goMindfulness">
					<view class="breath-orbit"><view></view></view>
					<text class="practice-kicker">呼吸空间</text>
					<text class="practice-title">先安静三分钟</text>
					<text class="practice-sub">跟随呼吸，不用急着做什么</text>
				</AccessibleButton>
				<AccessibleButton class="practice music" @click="goMusic">
					<text class="music-symbol">♫</text>
					<text class="practice-kicker">SOUND ROOM</text>
					<text class="practice-title">给耳朵一段慢时光</text>
					<text class="practice-sub">音乐放松 · 定时停止</text>
				</AccessibleButton>
			</view>

			<!-- 三、有什么新东西：通知 / 推荐 / 寄语 -->
			<view class="section-head">
				<view><text class="eyebrow">WHAT'S NEW</text><text class="section-title">有什么新东西</text></view>
				<AccessibleButton class="plain" :disabled="recommendLoading" @click="refreshRecommend">换一批 ↻</AccessibleButton>
			</view>

			<view class="notice-card" v-if="noticeState.unread > 0">
				<text class="notice-text notice-text-unread">你有 {{ noticeState.unread }} 条新消息</text>
				<AccessibleButton class="notice-link" @click="goNotifications">去看看 →</AccessibleButton>
			</view>
			<view class="notice-card" v-else>
				<text class="notice-text">现在还没有新消息。</text>
				<AccessibleButton class="notice-link" @click="goWorks">去社区看看大家的创作 →</AccessibleButton>
			</view>

			<view v-if="recommendLoading&&!recommends.length" class="empty-card">正在准备手作灵感…</view>
			<view v-else-if="!recommends.length" class="empty-card">
				<text>{{recommendError?'手作灵感暂时没有送达':'还没有推荐，先去看看入门练习吧'}}</text>
				<AccessibleButton @click="goRecommend">浏览入门手作 →</AccessibleButton>
				<AccessibleButton v-if="recommendError" @click="refreshRecommend">重试</AccessibleButton>
			</view>
			<AccessibleButton v-for="(item,i) in recommends.slice(0,3)" :key="item.projectId" class="craft-row" @click="goCraftDetail(item)">
				<view class="craft-thumb" :class="'thumb-'+i">
					<CraftCover :src="item.coverUrl" :category="item.category||item.title" compact/>
				</view>
				<view class="craft-copy">
					<text class="craft-category">{{item.category||'手作练习'}}</text>
					<text class="craft-title">{{item.title}}</text>
					<text class="craft-meta">约 {{item.estMinutes||'—'}} 分钟 · 跟随自己的节奏</text>
				</view>
				<text class="craft-arrow">↗</text>
			</AccessibleButton>

			<view class="daily-note" @click="nextQuote">
				<text class="eyebrow">A LITTLE REMINDER</text>
				<text class="quote">“{{currentQuote}}”</text>
				<text class="note-hint">轻点换一句 · 不必事事都做到完美</text>
			</view>

			<AccessibleButton class="heritage-entry" @click="goHeritage">
				<image class="heritage-entry-art" src="/static/heritage/971201.png" mode="aspectFill" lazy-load aria-hidden="true"/>
				<view class="heritage-entry-copy">
					<text class="eyebrow">HERITAGE STUDIES</text>
					<text class="heritage-entry-title">非遗纹样馆</text>
					<text class="heritage-entry-sub">看纹样 · 读工艺 · 试着配色</text>
				</view>
				<text class="heritage-entry-arrow">↗</text>
			</AccessibleButton>

			<view class="explore-links">
				<AccessibleButton @click="goWorks">作品社区 <text>看看大家的创作 ↗</text></AccessibleButton>
				<AccessibleButton @click="goInheritors">认识手作老师 <text>了解工艺与故事 ↗</text></AccessibleButton>
				<AccessibleButton @click="goSupport">校内支持 <text>江西师大官方联系指南 ↗</text></AccessibleButton>
			</view>
			<text class="home-foot">本系统不能替代专业心理咨询</text>

			<ChatPanel v-if="showChatPanel" @close="onPanelClose"/>
			<PrivacyConsent :visible="showConsent" @agree="onConsentAgree" @decline="onConsentDecline"/>
			<!-- 小织按需出现：低落打卡（F2）与创作完成后回家（R3 时机③）的一句话陪伴 -->
			<CompanionNudge
				:open="nudgeOpen"
				:text="nudgeText"
				:action-text="nudgeKind === 'craft' ? '' : '和小织聊聊'"
				:face="nudgeKind === 'craft' ? 'happy' : 'care'"
				bottom-offset="calc(320rpx + env(safe-area-inset-bottom))"
				@action="onNudgeAction"
				@close="nudgeOpen = false"
			/>
		</view>
	</view>
</template>

<script setup>
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import CraftCover from '../../components/CraftCover.vue'
import ChatPanel from '../../components/ChatPanel.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import PrivacyConsent from '../../components/PrivacyConsent.vue'
import CompanionNudge from '../../components/CompanionNudge.vue'
import { useCompanionNudge } from '../../composables/useCompanionNudge'
import { useNotifications } from '../../composables/useNotifications'
import { computed, ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { cached, invalidate, TTL_KEYS } from '../../utils/ttlCache'
import { useUserStore } from '../../stores/user'
import { useAiCompanionStore } from '../../stores/aiCompanion'
import { useInteraction } from '../../composables/useInteraction'
import { trackRecommendationClick } from '../../utils/recommend-click'

const { reducedMotion } = useInteraction()
const userStore = useUserStore()
const aiStore = useAiCompanionStore()

const loadingMe = ref(false)
const quoteIndex = ref(0)
const moodIndex = ref(null)
const showChatPanel = ref(false)
const showConsent = ref(false)

const QUOTES = [
	'每一针都是与自己和解的过程',
	'手作的意义不在完美，在于专注',
	'允许自己慢下来，就是一种疗愈',
	'今天也辛苦了，给自己一个小小的拥抱',
	'情绪没有对错，只有看见与看不见',
]

const moods = [
	{ label: '开心' },
	{ label: '平静' },
	{ label: '一般' },
	{ label: '有点累' },
	{ label: '不太好' },
]

const recommends = ref([])
const recommendationId = ref('')
const recommendLoading = ref(false)
const recommendError = ref(false)

/* 连续记录：/api/user/stats（字段名按后端返回宽容读取） */
const stats = ref({ streakDays: 0, totalWorks: 0, totalMinutes: 0 })
const statsLoaded = ref(false)

/* 未完成创作会话：/api/craft/session/active */
const activeSession = ref(null)
const activeSessionLabel = computed(() => {
	const s = activeSession.value
	if (!s) return ''
	const name = s.projectName || s.projectTitle || s.title || '未完成的创作'
	// BD：补上步骤进度（接口字段 currentStep/totalSteps 齐全）
	const step = Number(s.currentStep)
	const total = Number(s.totalSteps)
	const progress = step > 0 && total > 0 ? `做到第 ${step}/${total} 步，` : ''
	return `《${name}》${progress}还在等你`
})

const nickname = computed(() => (userStore.userInfo && userStore.userInfo.nickname) || '同学')
const avatarLetter = computed(() => (nickname.value && nickname.value.charAt(0)) || '同')

const welcome = computed(() => {
	const hour = new Date().getHours()
	if (hour < 6) return '夜深了，先把自己安顿好'
	if (hour < 12) return '早上好，今天也慢慢来'
	if (hour < 18) return '下午好，给自己留一点安静的时间'
	return '晚上好，把今天的疲惫放一放'
})

const currentQuote = computed(() => QUOTES[quoteIndex.value % QUOTES.length])

function selectMood(i) {
	moodIndex.value = moodIndex.value === i ? null : i
	// 按需出现（F2 · 时机①）：选中「有点累 / 不太好」时给一句低打扰陪伴，
	// 同一 quotaKey 每天最多 2 次；取消选择不触发。
	if (moodIndex.value === i && i >= 3 && nudgeQuota.canShow('mood-low')) {
		nudgeQuota.markShown('mood-low')
		nudgeKind.value = 'mood'
		nudgeText.value = i === 3 ? '有点累的时候，我在这儿' : '不用一个人扛着，我在'
		nudgeOpen.value = true
	}
}

/* 小织按需出现（F2 + R3）：频次闸门与展示状态 */
const nudgeQuota = useCompanionNudge()
const nudgeOpen = ref(false)
const nudgeText = ref('')
/** mood=情绪低落打卡（F2，带跳转）；craft=创作完成后回家（R3 时机③，纯陪伴点按即关） */
const nudgeKind = ref('mood')
function onNudgeAction() {
	if (nudgeKind.value === 'craft') { nudgeOpen.value = false; return }
	goEmotion()
}

/* 通知未读数（K1）：onShow 节流刷新，页面隐藏期间不轮询 */
const { state: noticeState, refreshUnread } = useNotifications()
function nextQuote() {
	quoteIndex.value++
}
function refreshRecommend() {
	invalidate(TTL_KEYS.craftRecommend)
	loadRecommends()
}

onShow(() => {
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	loadCurrentUser()
	checkPrivacyConsent()
	if (recommends.value.length === 0) loadRecommends()
	loadStats()
	loadActiveSession()
	refreshUnread()
	loadBaselineNudge()
	// R3 时机③：创作完成后回到首页时出现一次（quotaKey craft-finish，每天 ≤2 次）
	const pending = nudgeQuota.takePendingNudge()
	if (pending && nudgeQuota.canShow(pending.quotaKey)) {
		nudgeQuota.markShown(pending.quotaKey)
		nudgeKind.value = 'craft'
		nudgeText.value = pending.text
		nudgeOpen.value = true
	}
	if (aiStore.proactiveEnabled) {
		aiStore.proactiveCount = 0
		aiStore.resetIdle()
	}
})

onHide(() => {
	if (aiStore.proactiveEnabled) aiStore.pauseIdle()
})

async function checkPrivacyConsent() {
	if (userStore.privacyAgreed === null) {
		await userStore.fetchPrivacyStatus()
	}
	if (userStore.privacyAgreed === 0 && !userStore.consentDismissed) {
		showConsent.value = true
	}
}

function onConsentAgree() {
	userStore.setPrivacyAgreed(1)
	showConsent.value = false
}
function onConsentDecline() {
	userStore.setConsentDismissed(true)
	showConsent.value = false
}
function onPanelClose() {
	showChatPanel.value = false
	aiStore.visible = true
}

async function loadCurrentUser() {
	if (loadingMe.value) return
	loadingMe.value = true
	try {
		// X2：30s 会话缓存（暖启动去重；昵称修改经 invalidate('auth-me') 失效）
		const data = await cached(TTL_KEYS.authMe, 30000, () => request({ url: '/api/auth/me', method: 'GET' }))
		userStore.setUserInfo(data)
	} catch (e) {
		// 静默：沿用已缓存的用户信息
	} finally {
		loadingMe.value = false
	}
}

async function loadStats() {
	try {
		// X2：30s 会话缓存（创作完成/发布/撤回经 invalidate('user-stats') 立刻失效）
		const data = await cached(TTL_KEYS.userStats, 30000, () => request({ url: '/api/user/stats', method: 'GET' }))
		stats.value = {
			streakDays: data.streakDays ?? data.continuousDays ?? 0,
			totalWorks: data.totalWorks ?? data.workCount ?? 0,
			totalMinutes: data.totalMinutes ?? data.focusMinutes ?? 0,
		}
	} catch (e) {
		// 接口缺失或失败：保持占位「—」，不阻塞首页
		statsLoaded.value = false
		return
	}
	statsLoaded.value = true
}

async function loadActiveSession() {
	try {
		// X2：30s 会话缓存（完成/放弃创作经 invalidate('craft-session-active') 失效）
		// BD（2026-11-05）：接口返回裸数组（按 update_time 倒序），首条即最近有实质推进的会话；
		// 此前误用 data.id 判断（数组无 id 恒 undefined），卡片被静默隐藏。
		const data = await cached(TTL_KEYS.craftSessionActive, 30000, () => request({ url: '/api/craft/session/active', method: 'GET' }))
		activeSession.value = Array.isArray(data) && data.length ? data[0] : null
	} catch (e) {
		activeSession.value = null
	}
}

async function loadRecommends() {
	if (recommendLoading.value) return
	recommendLoading.value = true
	recommendError.value = false
	try {
		// X2：30s 会话缓存（「换一批」经 invalidate('craft-recommend') 强制刷新）
		const data = await cached(TTL_KEYS.craftRecommend, 30000, () => request({ url: '/api/craft/recommend', method: 'GET' }))
		if (data && data.items) {
			recommendationId.value = data.recommendationId ? String(data.recommendationId) : ''
			recommends.value = data.items
		}
	} catch (e) {
		recommendError.value = true
		recommends.value = []
	} finally {
		recommendLoading.value = false
	}
}

function goEmotion() { uni.navigateTo({ url: '/pages/emotion/chat' }) }
function goNotifications() { uni.navigateTo({ url: '/pages/notification/index' }) }
function goRecommend() { uni.navigateTo({ url: '/pages/starter/index' }) }
function goCraftRecommend() { uni.navigateTo({ url: '/pages/craft/recommend' }) }

/* ── AM1 情绪基线提醒（AR 批接入）──
 * 每次 onShow 直查（不缓存——后端 72h 冷却在提示返回时生效，前端不加第二层）；
 * 未同意隐私（1003）/查询失败均静默不展示；enabled=false 时零展示。
 * nudge=true 时才渲染卡片，文案按 level 分级（当前后端仅 gentle 一档）。 */
const baselineNudge = ref(null)
async function loadBaselineNudge() {
	baselineNudge.value = null
	try {
		const data = await request({ url: '/api/emotion/baseline-nudge', method: 'GET' })
		if (data && data.enabled && data.nudge) {
			baselineNudge.value = { level: data.level || 'gentle', actionType: data.actionType || '' }
		}
	} catch (e) {
		/* 静默：提醒是增强体验，失败不打扰 */
	}
}
function goCreate() {
	const s = activeSession.value
	// BD：会话页需要 projectId（必需）+ sessionId（恢复指定会话），与 starter 页 resume 同款参数
	if (s && s.projectId && s.sessionId) {
		uni.navigateTo({ url: `/pages/craft/session?projectId=${s.projectId}&sessionId=${s.sessionId}` })
	} else {
		uni.navigateTo({ url: '/pages/starter/index' })
	}
}
function goCraftDetail(item) {
	if (item && item.projectId) {
		trackRecommendationClick({
			recommendationId: recommendationId.value,
			projectId: item.projectId,
			source: 'HOME',
		})
		uni.navigateTo({ url: `/pages/craft/detail?id=${item.projectId}` })
	} else {
		goRecommend()
	}
}
function goWorks() { uni.switchTab({ url: '/pages/work/list' }) }
function goProfile() { uni.switchTab({ url: '/pages/profile/index' }) }
function goMindfulness() { uni.navigateTo({ url: '/pages/mindfulness/index' }) }
function goInheritors() { uni.navigateTo({ url: '/pages/inheritor/list' }) }
function goHeritage() { uni.navigateTo({ url: '/sub-heritage/pages/heritage/gallery' }) }
function goMusic() { uni.navigateTo({ url: '/pages/music/index' }) }
function goSupport() { uni.navigateTo({ url: '/pages/support/index' }) }
</script>

<style scoped>
.home-page { background: var(--zy-bg); min-height: 100vh; color: var(--zy-title); padding: 32rpx 34rpx 60rpx; box-sizing: border-box; }
.home-inner { max-width: 1080px; margin: auto; }
.home-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 38rpx; }
.wordmark { display: flex; gap: 15rpx; align-items: center; }
.wordmark image { width: 70rpx; height: 70rpx; }
.brand-name { display: block; font-size: 35rpx; font-weight: 600; letter-spacing: 5rpx; }
.brand-caption { display: block; font-size: 18rpx; color: var(--zy-text); letter-spacing: 2rpx; margin-top: 3rpx; }
.avatar { width: 68rpx; height: 68rpx; line-height: 68rpx; padding: 0; border-radius: 50%; background: var(--zy-bg-tint-mint); color: var(--zy-brand-700, #1a645e); font-size: 25rpx; margin: 0; min-width: 88rpx; }
.hello-row { display: flex; justify-content: space-between; gap: 20rpx; color: var(--zy-text); font-size: 21rpx; margin: 0 0 20rpx; }
.hello-row > text:last-child { color: var(--zy-title); }

/* ── 主视觉 ── */
.home-hero { position: relative; overflow: hidden; min-height: 480rpx; border-radius: var(--zy-radius-card); background: var(--zy-bg-tint-mint); border: 1rpx solid var(--zy-border); padding: 40rpx 34rpx; box-sizing: border-box; }
.hero-copy { position: relative; z-index: 1; max-width: 72%; }
.eyebrow { display: block; font-size: 17rpx; letter-spacing: 3rpx; color: var(--zy-text); margin-bottom: 14rpx; }
.hero-title { display: block; font-family: var(--zy-sans); font-size: 53rpx; font-weight: 600; line-height: 1.45; letter-spacing: 3rpx; }
.hero-note { display: block; font-size: 22rpx; line-height: 1.9; color: var(--zy-text); margin-top: 20rpx; }
.hero-button { display: flex; justify-content: space-between; gap: 24rpx; width: fit-content; margin: 30rpx 0 0; padding: 0 25rpx; background: var(--zy-primary); border-radius: var(--zy-radius-control); font-size: 23rpx; color: #fff; line-height: 3; }
.hero-art { position: absolute; right: -190rpx; bottom: 20rpx; width: 610rpx; height: 380rpx; opacity: .8; pointer-events: none; }
.hero-bottom { position: absolute; bottom: 22rpx; right: 25rpx; font-size: 16rpx; color: var(--zy-text); letter-spacing: 2rpx; }

/* ── 区块标题 ── */
.section-head { display: flex; align-items: center; justify-content: space-between; margin: 40rpx 0 20rpx; gap: 16rpx; }
.section-head .eyebrow { font-size: 16rpx; margin-bottom: 8rpx; }
.section-title { display: block; font-size: 31rpx; font-weight: 600; letter-spacing: 1rpx; }
.small-label { font-size: 17rpx; color: var(--zy-text); white-space: nowrap; }
.plain { background: transparent; padding: 6rpx 12rpx; margin: 0; font-size: 19rpx; color: var(--zy-primary); min-height: 88rpx; }

/* ── 三行动卡 ── */
.action-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18rpx; }
.action-card { position: relative; text-align: left; background: var(--zy-card-bg); border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-card); padding: 24rpx 20rpx; margin: 0; line-height: 1.6; min-height: 250rpx; }
.action-pet { height: 96rpx; margin-bottom: 8rpx; }
.action-icon { width: 72rpx; height: 72rpx; border-radius: 50%; background: var(--zy-bg-tint-mint); border: 1rpx solid var(--zy-border); display: grid; place-items: center; margin-bottom: 20rpx; }
.icon-yarn { width: 36rpx; height: 36rpx; border-radius: 50%; background: var(--zy-brand-300); border: 4rpx solid var(--zy-primary); }
.icon-needle { width: 8rpx; height: 44rpx; border-radius: 4rpx; background: var(--zy-accent); transform: rotate(28deg); }
.action-kicker { display: block; font-size: 16rpx; color: var(--zy-text); letter-spacing: 2rpx; margin-top: 12rpx; }
.action-title { display: block; font-size: 27rpx; color: var(--zy-title); font-weight: 600; margin-top: 6rpx; }
.action-sub { display: block; font-size: 18rpx; color: var(--zy-text); margin-top: 6rpx; }

/* ── 心情打卡 ── */
.mood-card { display: flex; background: var(--zy-card-bg); padding: 25rpx 10rpx; border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-card); justify-content: space-around; }
.mood { flex: 1; padding: 0 2rpx; background: transparent; font-size: 20rpx; color: var(--zy-text); line-height: 1.8; border-radius: 14rpx; }
.mood.chosen { background: var(--zy-bg-tint-mint); color: var(--zy-primary); }
.face { position: relative; width: 59rpx; height: 59rpx; border-radius: 50%; background: #f7e9bf; margin: 0 auto 14rpx; }
.face-1 { background: var(--zy-bg-tint-mint); }
.face-2 { background: var(--zy-bg-tint-warm); }
.face-3 { background: #e7e4ef; }
.face-4 { background: #e3e9ee; }
.eyes:before, .eyes:after { content: ''; position: absolute; width: 4rpx; height: 5rpx; border-radius: 50%; background: var(--zy-text); top: 23rpx; }
.eyes:before { left: 18rpx; }
.eyes:after { right: 18rpx; }
.mouth { position: absolute; left: 22rpx; top: 31rpx; width: 16rpx; height: 9rpx; border: 2rpx solid var(--zy-text); border-top: 0; border-radius: 0 0 20rpx 20rpx; }
.face-2 .mouth { height: 1rpx; border: 0; background: var(--zy-text); top: 36rpx; }
.face-3 .mouth { height: 8rpx; width: 7rpx; left: 27rpx; border: 2rpx solid var(--zy-text); border-radius: 50%; }
.face-4 .mouth { transform: rotate(180deg); top: 34rpx; }
.mood-response { display: flex; gap: 16rpx; align-items: center; font-size: 21rpx; color: var(--zy-text); margin-top: 20rpx; line-height: 1.7; }
.mood-response button { font-size: 21rpx; white-space: nowrap; background: transparent; padding: 0; color: var(--zy-primary); margin: 0; }

/* ── 连续记录 ── */
.stats-card { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; margin-top: 20rpx; background: var(--zy-card-bg); border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-card); padding: 24rpx 10rpx; }
.stat { text-align: center; }
.stat-num { display: block; font-size: 36rpx; font-weight: 700; color: var(--zy-primary); font-variant-numeric: tabular-nums; }
.stat-label { display: block; font-size: 18rpx; color: var(--zy-text); margin-top: 6rpx; }

/* ── 小织陪伴卡 ── */
.pet-card { display: flex; align-items: center; gap: 20rpx; width: 100%; margin-top: 24rpx; padding: 22rpx 26rpx; background: var(--zy-card-bg); border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-card); text-align: left; line-height: 1.6; }
.pet-copy { flex: 1; min-width: 0; }
.pet-label { display: block; font-size: 18rpx; color: var(--zy-text); }
.pet-title { display: block; font-size: 29rpx; font-weight: 600; color: var(--zy-title); margin-top: 4rpx; }
.pet-hint { display: block; font-size: 20rpx; color: var(--zy-text); margin-top: 4rpx; }
.pet-arrow { font-size: 30rpx; color: var(--zy-primary); }

/* ── 呼吸与音乐 ── */
.calm-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; margin-top: 24rpx; }
.practice { position: relative; min-height: 240rpx; border-radius: var(--zy-radius-card); padding: 26rpx 24rpx; text-align: left; line-height: 1.6; margin: 0; overflow: hidden; border: 1rpx solid var(--zy-border); }
.breath { background: var(--zy-bg-tint-mint); }
.music { background: var(--zy-bg-tint-warm); }
.practice-kicker { display: block; font-size: 16rpx; color: var(--zy-text); letter-spacing: 2rpx; margin-top: 76rpx; }
.practice-title { display: block; font-size: 27rpx; color: var(--zy-title); font-weight: 600; margin-top: 7rpx; }
.practice-sub { display: block; font-size: 18rpx; color: var(--zy-text); margin-top: 8rpx; }
.breath-orbit { position: absolute; left: 26rpx; top: 26rpx; border: 1rpx solid var(--zy-primary); width: 68rpx; height: 68rpx; border-radius: 50%; display: grid; place-items: center; }
.breath-orbit view { width: 42rpx; height: 42rpx; border-radius: 50%; background: var(--zy-brand-100); }
.music-symbol { position: absolute; left: 26rpx; top: 22rpx; font-size: 52rpx; color: var(--zy-accent); }

/* ── 通知卡 ── */
.notice-card { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; background: var(--zy-card-bg); border: 1rpx dashed var(--zy-border); border-radius: var(--zy-radius-card); padding: 22rpx 26rpx; }
.notice-text { font-size: 22rpx; color: var(--zy-text); }
.notice-text-unread { color: var(--zy-title); font-weight: 600; }
.notice-link { font-size: 22rpx; color: var(--zy-primary); background: transparent; padding: 0; margin: 0; white-space: nowrap; }

/* ── 推荐列表 ── */
.empty-card { padding: 30rpx; background: var(--zy-card-bg); border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-card); font-size: 23rpx; color: var(--zy-text); line-height: 1.8; }
.empty-card button { font-size: 22rpx; color: var(--zy-primary); background: transparent; }
.craft-row { display: flex; align-items: center; width: 100%; gap: 22rpx; padding: 20rpx 0; background: transparent; text-align: left; border-bottom: 1rpx solid var(--zy-border); line-height: 1.5; border-radius: 0; margin: 0; }
.craft-thumb { position: relative; background: var(--zy-bg-tint-mint); border-radius: 16rpx; width: 125rpx; height: 123rpx; flex-shrink: 0; overflow: hidden; display: grid; place-items: center; }
.craft-thumb image { width: 100%; height: 100%; }
.thumb-1 { background: var(--zy-bg-tint-warm); }
.thumb-2 { background: var(--zy-brand-50); }
.craft-copy { flex: 1; }
.craft-category { display: block; font-size: 16rpx; color: var(--zy-text); margin-bottom: 7rpx; letter-spacing: 2rpx; }
.craft-title { display: block; font-size: 27rpx; color: var(--zy-title); }
.craft-meta { display: block; font-size: 19rpx; color: var(--zy-text); margin-top: 12rpx; }
.craft-arrow { font-size: 25rpx; color: var(--zy-primary); }

/* ── 寄语 ── */
.daily-note { border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-card); padding: 26rpx; margin-top: 26rpx; background: var(--zy-bg-tint-mint); }
.quote { display: block; font-family: var(--zy-sans); font-size: 28rpx; color: var(--zy-title); line-height: 1.8; }
.note-hint { display: block; font-size: 17rpx; color: var(--zy-text); margin-top: 14rpx; }

/* ── 非遗入口 ── */
.heritage-entry { display: flex; align-items: center; gap: 18rpx; width: 100%; margin: 22rpx 0 0; padding: 20rpx; background: var(--zy-card-bg); border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-card); text-align: left; line-height: 1.6; color: var(--zy-title); }
.heritage-entry-art { width: 110rpx; height: 110rpx; flex-shrink: 0; border-radius: 14rpx; background: var(--zy-bg-tint-mint); }
.heritage-entry-copy { flex: 1; min-width: 0; }
.heritage-entry-title { display: block; font-size: 28rpx; font-weight: 600; color: var(--zy-title); }
.heritage-entry-sub { display: block; margin-top: 6rpx; font-size: 21rpx; color: var(--zy-text); }
.heritage-entry-arrow { font-size: 28rpx; color: var(--zy-primary); }
.heritage-entry:active { opacity: .84; }

/* ── 探索链接 ── */
.explore-links { margin-top: 36rpx; border-top: 1rpx solid var(--zy-border); padding-top: 18rpx; }
.explore-links button { display: flex; justify-content: space-between; background: transparent; border-bottom: 1rpx solid var(--zy-border); border-radius: 0; font-size: 24rpx; color: var(--zy-title); padding: 20rpx 0; line-height: 1.8; }
.explore-links text { font-size: 18rpx; color: var(--zy-text); }
.home-foot { display: block; text-align: center; font-size: 18rpx; color: var(--zy-text); margin: 36rpx 0; }

button::after { border: 0; }
button:active { opacity: .82; }
button:focus-visible { outline: 3px solid var(--zy-primary); outline-offset: 3px; }
.motion-reduced * { animation: none !important; transition: none !important; }

@media (min-width: 850px) {
	.home-page { padding: 28px 48px; }
	.home-hero { min-height: 310px; padding: 36px 42px; }
	.hero-title { font-size: 40px; }
	.hero-copy { max-width: 60%; }
	.hero-art { width: 620px; height: 380px; right: -30px; bottom: -45px; }
	.explore-links { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 25px; }
	.explore-links button { display: block; text-align: left; }
	.explore-links text { display: block; margin-top: 10px; }
	.practice { min-height: 210px; }
}
/* AM1 基线提醒卡：暖底弱提示，与 notice-card 同一视觉量级，不抢主视觉 */
.nudge-card {
	display: flex;
	flex-direction: column;
	gap: 18rpx;
	margin-bottom: 28rpx;
	padding: 28rpx;
	background: var(--zy-bg-tint-warm, #f7efd9);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 26rpx;
}
.nudge-text { font-size: 26rpx; line-height: 1.8; color: var(--zy-text, #4a5f54); }
.nudge-action {
	align-self: flex-start;
	padding: 10rpx 24rpx;
	font-size: 24rpx;
	color: var(--zy-brand-700, #1a645e);
	background: var(--zy-bg-tint-mint, #e8efe4);
	border-radius: 999rpx;
}
.nudge-action:focus-visible { outline: 3px solid var(--zy-brand-700, #1a645e); outline-offset: 2px; }
.nudge-disclaimer { font-size: 22rpx; line-height: 1.7; color: var(--zy-text, #4a5f54); opacity: 0.85; }
</style>
