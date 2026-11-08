<template>
	<view class="home-page" :class="{'motion-reduced':reducedMotion}">
		<view class="home-inner">
			<!-- 页头：品牌 + 通知铃铛（未读红点）+ 头像 -->
			<view class="home-header">
				<view class="wordmark">
					<image src="/static/logo.png" aria-hidden="true"/>
					<view>
						<text class="brand-name">织愈</text>
						<text class="brand-caption">给自己，一点慢时光</text>
					</view>
				</view>
				<view class="header-right">
					<AccessibleButton class="bell" aria-label="查看通知" @click="goNotifications">
						<wd-icon name="notification" size="36rpx" color="#5C5648" />
						<view v-if="noticeState.unread > 0" class="bell-dot" aria-hidden="true"></view>
					</AccessibleButton>
					<AccessibleButton class="avatar" aria-label="打开我的页面" @click="goProfile">{{avatarLetter}}</AccessibleButton>
				</view>
			</view>
			<text class="hello-row">{{welcome}}</text>

			<!-- 主视觉（去框化，落在暖纸底上） -->
			<view class="home-hero">
				<view class="hero-copy">
					<text class="eyebrow">A MOMENT FOR YOURSELF</text>
					<text class="hero-title zy-t-serif">慢一点，<br/>给心情一个家。</text>
					<text class="hero-note">从手边的一张纸、一根线开始，留一小段时间，给此刻的自己。</text>
					<AccessibleButton variant="primary" size="lg" class="hero-button" @click="goRecommend">开始一件小手作</AccessibleButton>
				</view>
				<view class="hero-pet" aria-hidden="true"><XiaotuanPet :size="112" /></view>
				<text class="hero-bottom">用一针一线，连接生活里的小确幸</text>
			</view>

			<StitchDivider />

			<!-- 一、现在可以做什么：一主（倾诉）两次（推荐/创作） -->
			<SectionHead eyebrow="WHAT NOW" title="现在，想怎么度过这几分钟？" />
			<AccessibleButton class="main-card zy-card zy-card--mint" aria-label="和小织聊聊" @click="goEmotion">
				<view class="main-card-pet"><XiaotuanPet :size="64" /></view>
				<view class="main-card-copy">
					<text class="main-card-kicker">01 / 倾诉</text>
					<text class="main-card-title">和小织聊聊</text>
					<text class="main-card-sub">把想说的慢慢说，我在这里</text>
				</view>
				<view class="main-card-arrow" aria-hidden="true">→</view>
			</AccessibleButton>
			<view class="duo-grid">
				<AccessibleButton class="duo-card zy-card" @click="goCraftRecommend">
					<text class="duo-kicker">02 / 推荐</text>
					<text class="duo-title">今天的手作</text>
					<text class="duo-sub">按此刻心情，为你挑了一件</text>
				</AccessibleButton>
				<AccessibleButton class="duo-card zy-card" @click="goCreate">
					<text class="duo-kicker">03 / 创作</text>
					<text class="duo-title">{{activeSession ? '继续上次' : '开始创作'}}</text>
					<text class="duo-sub">{{activeSession ? activeSessionLabel : '跟着步骤，慢慢来'}}</text>
				</AccessibleButton>
			</view>

			<!-- 二、此刻状态：心情打卡 + 旅程数据行 -->
			<SectionHead eyebrow="CHECK IN" title="此刻，你感觉怎么样？">
				<text class="small-label">仅当前页可见</text>
			</SectionHead>
			<view class="mood-row">
				<MoodFace v-for="(m,i) in moods" :key="m.label" :kind="i" :label="m.label" :selected="moodIndex===i" @select="selectMood(i)" />
			</view>
			<view v-if="moodIndex!==null" class="mood-response">
				<text>{{moodIndex>=3?'今天也辛苦了。想说说的话，小织在这里。':'谢谢你留意此刻的感受。跟着自己的节奏就好。'}}</text>
				<AccessibleButton variant="ghost" size="sm" @click="goEmotion">和小织聊聊 →</AccessibleButton>
			</view>

			<StitchDivider />
			<StatStrip aria-live="polite" aria-label="我的创作旅程" :items="journeyItems" />
			<StitchDivider />

			<!-- AM1 情绪基线提醒：温和提示卡（非弹窗），仅首页一处，不重复打扰 -->
			<view v-if="baselineNudge" class="nudge-card zy-card zy-card--warm" role="status">
				<text class="nudge-text">这几天低落的时刻似乎多了一些。不用着急——也许一件小手作、几分钟慢时光，会让今天好过一点点。</text>
				<AccessibleButton v-if="baselineNudge.actionType === 'craft'" variant="secondary" size="sm" class="nudge-action" @click="goCraftRecommend">去看看今天的手作 →</AccessibleButton>
				<text class="nudge-disclaimer">这只是温柔的提醒，不是任何判断。本系统不能替代专业心理咨询。</text>
			</view>

			<!-- 呼吸与音乐（保留） -->
			<view class="calm-grid">
				<AccessibleButton class="practice zy-card zy-card--mint breath" @click="goMindfulness">
					<view class="breath-orbit" aria-hidden="true"><view></view></view>
					<text class="practice-kicker">呼吸空间</text>
					<text class="practice-title">先安静三分钟</text>
					<text class="practice-sub">跟随呼吸，不用急着做什么</text>
				</AccessibleButton>
				<AccessibleButton class="practice zy-card zy-card--warm music" @click="goMusic">
					<view class="music-icon" aria-hidden="true"><wd-icon name="service" size="52rpx" color="#7A5C12" /></view>
					<text class="practice-kicker">SOUND ROOM</text>
					<text class="practice-title">给耳朵一段慢时光</text>
					<text class="practice-sub">音乐放松 · 定时停止</text>
				</AccessibleButton>
			</view>

			<!-- 三、有什么新东西 -->
			<SectionHead eyebrow="WHAT'S NEW" title="今天为你挑的">
				<AccessibleButton variant="ghost" size="sm" :disabled="recommendLoading" @click="refreshRecommend">换一批 ↻</AccessibleButton>
			</SectionHead>

			<view v-if="recommendLoading&&!recommends.length" class="empty-card zy-card">正在准备手作灵感…</view>
			<view v-else-if="!recommends.length" class="empty-card zy-card">
				<text>{{recommendError?'手作灵感暂时没有送达':'还没有推荐，先去看看入门练习吧'}}</text>
				<AccessibleButton variant="ghost" size="sm" @click="goRecommend">浏览入门手作 →</AccessibleButton>
				<AccessibleButton v-if="recommendError" variant="ghost" size="sm" @click="refreshRecommend">重试</AccessibleButton>
			</view>
			<template v-else>
				<AccessibleButton v-if="recommends[0]" class="feat-card zy-card zy-card--feature" @click="goCraftDetail(recommends[0])">
					<view class="feat-cover">
						<CraftCover :src="recommends[0].coverUrl" :category="recommends[0].category||recommends[0].title" />
					</view>
					<view class="feat-body">
						<text class="feat-category">{{recommends[0].category||'手作练习'}}</text>
						<text class="feat-title">{{recommends[0].title}}</text>
						<text class="feat-meta">约 {{recommends[0].estMinutes||'—'}} 分钟 · 跟随自己的节奏</text>
					</view>
				</AccessibleButton>
				<AccessibleButton v-for="item in recommends.slice(1,3)" :key="item.projectId" class="craft-row" @click="goCraftDetail(item)">
					<view class="craft-thumb">
						<!-- 裸 image 而非 CraftCover：自定义组件在小程序 wx:for 内内容塌陷（2026-11-08 排查，历史问题）。
						     无真实封面（coverUrl 为空）时不放品类照片冒充，改用品类纹样占位（明确是占位，不误导）。 -->
						<image v-if="item.coverUrl" :src="resolveCover(item)" mode="aspectFill" class="craft-thumb-img" />
						<view v-else class="craft-thumb-ph" :class="'ph-' + coverKind(item)" aria-hidden="true">
							<text class="ph-label">{{ item.category || '手作' }}</text>
						</view>
					</view>
					<view class="craft-copy">
						<text class="craft-category">{{item.category||'手作练习'}}</text>
						<text class="craft-title">{{item.title}}</text>
						<text class="craft-meta">约 {{item.estMinutes||'—'}} 分钟 · 跟随自己的节奏</text>
					</view>
				</AccessibleButton>
			</template>

			<!-- 每日寄语：居中衬线引言，无容器 -->
			<view class="daily-note" @click="nextQuote">
				<text class="quote zy-t-serif">“{{currentQuote}}”</text>
				<text class="note-hint">轻点换一句 · 不必事事都做到完美</text>
			</view>

			<!-- 非遗入口：暖金 tint + 回纹角饰 -->
			<AccessibleButton class="heritage-entry zy-card zy-card--warm" @click="goHeritage">
				<view class="heritage-copy">
					<text class="eyebrow eyebrow-heritage">HERITAGE STUDIES</text>
					<text class="heritage-title zy-t-serif">非遗纹样馆</text>
					<text class="heritage-sub">看纹样 · 读工艺 · 试着配色</text>
				</view>
				<text class="heritage-arrow" aria-hidden="true">→</text>
				<view class="heritage-corner" aria-hidden="true"></view>
			</AccessibleButton>

			<view class="explore-links">
				<AccessibleButton variant="ghost" size="sm" @click="goWorks">作品社区 · 看看大家的创作</AccessibleButton>
				<AccessibleButton variant="ghost" size="sm" @click="goInheritors">认识手作老师 · 了解工艺与故事</AccessibleButton>
				<AccessibleButton variant="ghost" size="sm" @click="goSupport">校内支持 · 官方联系指南</AccessibleButton>
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
import StitchDivider from '../../components/StitchDivider.vue'
import SectionHead from '../../components/SectionHead.vue'
import MoodFace from '../../components/MoodFace.vue'
import StatStrip from '../../components/StatStrip.vue'
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
import { API_BASE_URL } from '../../utils/config'

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

/* 旅程数据行（StatStrip；未加载完成时占位 —） */
const journeyItems = computed(() => [
	{ value: statsLoaded.value ? (stats.value.streakDays ?? 0) : '—', label: '连续创作 · 天' },
	{ value: statsLoaded.value ? (stats.value.totalWorks ?? 0) : '—', label: '我的作品' },
	{ value: statsLoaded.value ? (stats.value.totalMinutes ?? 0) : '—', label: '专注 · 分钟' },
])

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
/* 推荐行缩略图地址：与 CraftCover 同一解析（coverUrl → API 拼接）；空封面由模板走纹样占位 */
function resolveCover(item) {
	const src = item && item.coverUrl
	if (!src) return ''
	if (/^https?:|^\/static\//.test(src)) return src
	return API_BASE_URL + src
}
/* 品类占位纹样分族：与 CraftCover kind 判定同源 */
function coverKind(item) {
	const cat = (item && (item.category || item.title)) || ''
	return /线|编|绣|染/.test(cat) ? 'yarn' : /陶|泥/.test(cat) ? 'clay' : 'paper'
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
.home-page { min-height: 100vh; color: var(--zy-title); padding: 32rpx 32rpx 64rpx; box-sizing: border-box; }
.home-inner { max-width: 1080px; margin: auto; }

/* ── 页头 ── */
.home-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.wordmark { display: flex; gap: 15rpx; align-items: center; }
.wordmark image { width: 70rpx; height: 70rpx; }
.brand-name { display: block; font-size: 35rpx; font-weight: 600; letter-spacing: 5rpx; }
.brand-caption { display: block; font-size: var(--zy-text-micro); color: var(--zy-text); letter-spacing: 2rpx; margin-top: 3rpx; }
.header-right { display: flex; align-items: center; gap: 20rpx; }
.bell { position: relative; width: 68rpx; height: 68rpx; min-width: 68rpx; border-radius: 50%; background: var(--zy-card-bg); box-shadow: var(--zy-shadow-card); padding: 0; margin: 0; }
.bell-dot { position: absolute; top: 14rpx; right: 16rpx; width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--zy-care); border: 3rpx solid var(--zy-card-bg); }
.avatar { width: 68rpx; height: 68rpx; line-height: 68rpx; padding: 0; border-radius: 50%; background: var(--zy-bg-tint-mint); color: var(--zy-brand-700); font-size: 25rpx; margin: 0; min-width: 88rpx; }
.hello-row { display: block; color: var(--zy-text); font-size: var(--zy-text-caption); margin: 0 4rpx 36rpx; }

/* ── 主视觉（去框化，直接落在纸底） ── */
.home-hero { position: relative; padding: 20rpx 4rpx 52rpx; }
.hero-copy { position: relative; z-index: 1; max-width: 68%; }
.eyebrow { display: block; font-size: var(--zy-text-micro); letter-spacing: 4rpx; color: var(--zy-text); margin-bottom: 20rpx; }
.hero-title { display: block; font-size: var(--zy-text-display); font-weight: 600; line-height: 1.35; letter-spacing: 2rpx; }
.hero-note { display: block; font-size: var(--zy-text-caption); line-height: 1.9; color: var(--zy-text); margin-top: 24rpx; }
.hero-button { margin: 36rpx 0 0; }
.hero-pet { position: absolute; right: 0; top: 16rpx; }
.hero-bottom { display: block; margin-top: 24rpx; font-size: var(--zy-text-micro); color: var(--zy-text); letter-spacing: 2rpx; }

/* ── 主行动卡（一主） ── */
.main-card { display: flex; flex-direction: row; align-items: center; gap: 28rpx; width: 100%; text-align: left; line-height: 1.6; border-radius: var(--zy-radius-feature); }
.main-card-pet { flex-shrink: 0; }
.main-card-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.main-card-kicker { font-size: var(--zy-text-micro); letter-spacing: 2rpx; color: var(--zy-brand-700); }
.main-card-title { font-size: var(--zy-text-title-lg); font-weight: 600; color: var(--zy-title); margin-top: 4rpx; }
.main-card-sub { font-size: var(--zy-text-caption); color: var(--zy-text); margin-top: 6rpx; }
.main-card-arrow { width: 68rpx; height: 68rpx; border-radius: 50%; background: var(--zy-brand-600); color: #FFFDF7; font-size: 30rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }

/* ── 两次卡 ── */
.duo-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24rpx; margin-top: 24rpx; }
.duo-card { display: flex; flex-direction: column; align-items: flex-start; text-align: left; line-height: 1.6; }
.duo-kicker { font-size: var(--zy-text-micro); letter-spacing: 2rpx; color: var(--zy-accent); }
.duo-title { font-size: var(--zy-text-title); font-weight: 600; color: var(--zy-title); margin-top: 8rpx; }
.duo-sub { font-size: var(--zy-text-micro); color: var(--zy-text); margin-top: 8rpx; line-height: 1.6; }

/* ── 心情打卡 ── */
.mood-row { display: flex; justify-content: space-between; padding: 8rpx 16rpx 0; }
.mood-response { display: flex; gap: 16rpx; align-items: center; font-size: var(--zy-text-caption); color: var(--zy-text); margin: 24rpx 8rpx 0; line-height: 1.7; }
.small-label { font-size: var(--zy-text-micro); color: var(--zy-text); white-space: nowrap; }

/* ── 呼吸与音乐 ── */
.calm-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24rpx; margin-top: 48rpx; }
.practice { position: relative; display: flex; flex-direction: column; align-items: flex-start; text-align: left; line-height: 1.6; min-height: 240rpx; overflow: hidden; }
.practice-kicker { display: block; font-size: var(--zy-text-micro); color: var(--zy-text); letter-spacing: 2rpx; margin-top: 88rpx; }
.practice-title { display: block; font-size: var(--zy-text-title); color: var(--zy-title); font-weight: 600; margin-top: 8rpx; }
.practice-sub { display: block; font-size: var(--zy-text-micro); color: var(--zy-text); margin-top: 8rpx; }
.breath-orbit { position: absolute; left: 32rpx; top: 32rpx; border: 2rpx solid var(--zy-brand-600); width: 68rpx; height: 68rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.breath-orbit view { width: 42rpx; height: 42rpx; border-radius: 50%; background: var(--zy-brand-100); }
.music-icon { position: absolute; left: 32rpx; top: 28rpx; }

/* ── 推荐 ── */
.empty-card { display: flex; gap: 16rpx; align-items: center; font-size: var(--zy-text-body); color: var(--zy-text); line-height: 1.8; }
.feat-card { display: flex; flex-direction: column; width: 100%; padding: 0; overflow: hidden; text-align: left; line-height: 1.6; background: var(--zy-card-bg); box-shadow: var(--zy-shadow-card); }
.feat-cover { width: 100%; height: 340rpx; overflow: hidden; }
.feat-body { padding: 28rpx 32rpx 32rpx; display: flex; flex-direction: column; }
.feat-category { font-size: var(--zy-text-micro); letter-spacing: 2rpx; color: var(--zy-accent); }
.feat-title { font-size: var(--zy-text-title); font-weight: 600; color: var(--zy-title); margin-top: 8rpx; }
.feat-meta { font-size: var(--zy-text-micro); color: var(--zy-text); margin-top: 10rpx; }
.craft-row { display: flex; align-items: center; width: 100%; gap: 24rpx; padding: 24rpx 0; background: transparent; text-align: left; border-bottom: 2rpx dashed var(--zy-stitch); line-height: 1.5; border-radius: 0; margin: 0; }
.craft-thumb { position: relative; background: var(--zy-bg-tint-mint); border-radius: 16rpx; width: 125rpx; height: 123rpx; flex-shrink: 0; overflow: hidden; }
.craft-thumb-img { width: 100%; height: 100%; display: block; }
/* 无封面占位：品类纹样（经纬线）+ 品类名，明确是占位而非作品照片 */
.craft-thumb-ph { width: 100%; height: 100%; display: flex; align-items: flex-end; justify-content: flex-start; padding: 10rpx 12rpx; box-sizing: border-box; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 60 60'%3E%3Cg stroke='%23ffffff' stroke-width='1.6' fill='none' opacity='.55'%3E%3Cpath d='M0 15 Q15 8 30 15 T60 15'/%3E%3Cpath d='M0 30 Q15 23 30 30 T60 30'/%3E%3Cpath d='M0 45 Q15 38 30 45 T60 45'/%3E%3Cpath d='M15 0 Q22 30 15 60'/%3E%3Cpath d='M38 0 Q45 30 38 60'/%3E%3C/g%3E%3C/svg%3E"); background-size: 60rpx 60rpx; }
.ph-yarn { background-color: var(--zy-brand-100); }
.ph-clay { background-color: var(--zy-bg-tint-warm); }
.ph-paper { background-color: var(--zy-brand-50); }
.ph-label { font-size: var(--zy-text-micro); color: var(--zy-brand-700); letter-spacing: 1rpx; }
.craft-copy { flex: 1; min-width: 0; }
.craft-category { display: block; font-size: var(--zy-text-micro); color: var(--zy-accent); margin-bottom: 6rpx; letter-spacing: 2rpx; }
.craft-title { display: block; font-size: var(--zy-text-body-lg); color: var(--zy-title); }
.craft-meta { display: block; font-size: var(--zy-text-micro); color: var(--zy-text); margin-top: 10rpx; }

/* ── 每日寄语（居中衬线，无容器） ── */
.daily-note { text-align: center; padding: 72rpx 48rpx 8rpx; }
.quote { display: block; font-size: var(--zy-text-title); color: var(--zy-title); line-height: 2; }
.note-hint { display: block; font-size: var(--zy-text-micro); color: var(--zy-text); margin-top: 20rpx; }

/* ── 非遗入口（暖金 tint + 回纹角饰） ── */
.heritage-entry { position: relative; display: flex; flex-direction: row; align-items: center; gap: 18rpx; width: 100%; margin: 48rpx 0 0; text-align: left; line-height: 1.6; color: var(--zy-title); overflow: hidden; border-radius: var(--zy-radius-feature); }
.heritage-copy { flex: 1; min-width: 0; }
.eyebrow-heritage { color: var(--zy-accent); margin-bottom: 10rpx; }
.heritage-title { display: block; font-size: var(--zy-text-title-lg); font-weight: 600; letter-spacing: 2rpx; }
.heritage-sub { display: block; margin-top: 8rpx; font-size: var(--zy-text-caption); color: var(--zy-text); }
.heritage-arrow { font-size: 32rpx; color: var(--zy-accent); flex-shrink: 0; }
.heritage-corner { position: absolute; right: -12rpx; bottom: -12rpx; width: 168rpx; height: 168rpx; opacity: .14; pointer-events: none; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 84 84'%3E%3Cg fill='none' stroke='%237A5C12' stroke-width='2.5'%3E%3Cpath d='M8 76 V44 H40 V76 H24 V60'/%3E%3Cpath d='M44 76 V28 H76 V60 H60 V44'/%3E%3C/g%3E%3C/svg%3E"); background-size: contain; }

/* ── 页脚链接与免责 ── */
.explore-links { margin-top: 48rpx; border-top: 2rpx dashed var(--zy-stitch); padding-top: 24rpx; display: flex; flex-direction: column; gap: 8rpx; align-items: flex-start; }
.home-foot { display: block; text-align: center; font-size: var(--zy-text-micro); color: var(--zy-text); margin: 48rpx 0 16rpx; }

button::after { border: 0; }
button:active { opacity: .9; }
button:focus-visible { outline: 3px solid var(--zy-brand-700); outline-offset: 3px; }
.motion-reduced view, .motion-reduced text, .motion-reduced image, .motion-reduced button, .motion-reduced input, .motion-reduced textarea, .motion-reduced label, .motion-reduced scroll-view, .motion-reduced video, .motion-reduced camera, .motion-reduced canvas, .motion-reduced slider, .motion-reduced checkbox, .motion-reduced checkbox-group, .motion-reduced svg, .motion-reduced g, .motion-reduced path, .motion-reduced ellipse, .motion-reduced circle, .motion-reduced defs, .motion-reduced clippath, .motion-reduced lineargradient, .motion-reduced radialgradient, .motion-reduced stop { animation: none !important; transition: none !important; }

@media (min-width: 850px) {
	.home-page { padding: 28px 48px; }
	.hero-title { font-size: 40px; }
	.hero-copy { max-width: 60%; }
	.explore-links { flex-direction: row; gap: 25px; }
	.practice { min-height: 210px; }
}
</style>
