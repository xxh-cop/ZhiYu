<template><view class="home-page" :class="{'motion-reduced':reducedMotion}"><view class="home-inner">
<view class="home-header"><view class="wordmark"><image src="/static/logo.png"/><view><text class="brand-name">织愈</text><text class="brand-caption">给自己，一点慢时光</text></view></view><button class="avatar" @click="goProfile">{{avatarLetter}}</button></view>
<view class="hello-row"><text>{{welcome}}</text><text>你好，{{nickname}} ↗</text></view>
<view class="home-hero"><view class="hero-copy"><text class="eyebrow">A MOMENT FOR YOURSELF</text><text class="hero-title">慢一点，<br/>给心情一个家。</text><text class="hero-note">从手边的一张纸、一根线开始，<br/>留一小段时间，给此刻的自己。</text><button class="hero-button" @click="goRecommend">开始一件小手作 <text>↗</text></button></view><image class="hero-art" src="/static/brand/weave-scene.png" mode="aspectFit"/><text class="hero-bottom">用一针一线，连接生活里的小确幸</text></view>
<XiaozhiCard @open="goEmotion"/><button class="music-entry" @click="goMusic"><view><text class="eyebrow">SOUND ROOM</text><text>给耳朵一段慢时光</text><text class="music-sub">音乐放松 · 定时停止 · 按你的节奏</text></view><text class="music-symbol">♫ ↗</text></button><view class="home-columns"><view class="main-column"><view class="section-head"><view><text class="eyebrow">CHECK IN</text><text class="section-title">此刻，你感觉怎么样？</text></view><text class="small-label">只留在当前页面</text></view><view class="mood-card"><button v-for="(m,i) in moods" :key="m.label" class="mood" :class="{chosen:moodIndex===i}" :aria-pressed="moodIndex===i" @click="selectMood(i)"><view class="face" :class="'face-'+i"><view class="eyes"></view><view class="mouth"></view></view><text>{{m.label}}</text></button></view><view v-if="moodIndex!==null" class="mood-response"><text>{{moodIndex>=3?'今天也辛苦了。想说说的话，小织在这里。':'谢谢你留意此刻的感受。跟着自己的节奏就好。'}}</text><button @click="goEmotion">和小织聊聊 →</button></view>
<view class="section-head"><view><text class="eyebrow">SMALL PRACTICES</text><text class="section-title">给自己一点空间</text></view></view><view class="practice-grid"><button class="practice breath" @click="goMindfulness"><view class="breath-orbit"><view></view></view><text class="practice-kicker">01 / 呼吸空间</text><text class="practice-title">先安静三分钟</text><text class="practice-sub">跟随呼吸，不用急着做什么</text><text class="practice-arrow">↗</text></button><button class="practice chat" @click="goEmotion"><view class="chat-shape">织</view><text class="practice-kicker">02 / 小织陪伴</text><text class="practice-title">把想说的慢慢说</text><text class="practice-sub">一个可以放下思绪的角落</text><text class="practice-arrow">↗</text></button></view></view>
<view class="side-column"><view class="section-head"><view><text class="eyebrow">DISCOVER</text><text class="section-title">从手作里，遇见自己</text></view><button class="plain" :disabled="recommendLoading" @click="refreshRecommend">换一批 ↻</button></view><view v-if="recommendLoading&&!recommends.length" class="empty-card">正在准备手作灵感…</view><view v-else-if="!recommends.length" class="empty-card"><text>{{recommendError?'手作灵感暂时没有送达':'还没有推荐，先去看看入门练习吧'}}</text><button @click="goRecommend">浏览入门手作 →</button><button v-if="recommendError" @click="refreshRecommend">重试</button></view><button v-for="(item,i) in recommends.slice(0,3)" :key="item.projectId" class="craft-row" @click="goCraftDetail(item)"><view class="craft-thumb" :class="'thumb-'+i"><CraftCover :src="item.coverUrl" :category="item.category||item.title" compact/></view><view class="craft-copy"><text class="craft-category">{{item.category||'手作练习'}}</text><text class="craft-title">{{item.title}}</text><text class="craft-meta">约 {{item.estMinutes||'—'}} 分钟 · 跟随自己的节奏</text></view><text class="craft-arrow">↗</text></button><view class="daily-note" @click="nextQuote"><text class="eyebrow">A LITTLE REMINDER</text><text class="quote">“{{currentQuote}}”</text><text class="note-hint">轻点换一句 · 不必事事都做到完美</text></view></view></view>
<view class="explore-links"><button @click="goWorks">作品社区 <text>看看大家的创作 ↗</text></button><button @click="goInheritors">认识手作老师 <text>了解工艺与故事 ↗</text></button><button @click="goSupport">校内支持 <text>江西师大官方联系指南 ↗</text></button></view><text class="home-foot">本系统不能替代专业心理咨询</text>
<ChatPanel v-if="showChatPanel" @close="onPanelClose"/><PrivacyConsent :visible="showConsent" @agree="onConsentAgree" @decline="onConsentDecline"/>
</view></view></template>
<script setup>
import XiaozhiCard from "../../components/XiaozhiCard.vue"
import CraftCover from "../../components/CraftCover.vue"
import { computed, ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { request } from '../../utils/request'
import { useUserStore } from '../../stores/user'
import { useAiCompanionStore } from '../../stores/aiCompanion'
import { useInteraction } from '../../composables/useInteraction'
const { reducedMotion } = useInteraction()
import ChatPanel from '../../components/ChatPanel.vue'
import PrivacyConsent from '../../components/PrivacyConsent.vue'

const userStore = useUserStore()
const aiStore = useAiCompanionStore()
const loadingMe = ref(false)
const quoteIndex = ref(0)
const moodIndex = ref(null)
const showChatPanel = ref(false)
const showConsent = ref(false)

function onPanelClose() {
	showChatPanel.value = false
	aiStore.visible = true
}

const QUOTES = [
	'每一针都是与自己和解的过程',
	'手作的意义不在完美，在于专注',
	'允许自己慢下来，就是一种疗愈',
	'今天也辛苦了，给自己一个小小的拥抱',
	'情绪没有对错，只有看见与看不见',
]

const moods = [
	{ emoji: '😊', label: '开心' },
	{ emoji: '😌', label: '平静' },
	{ emoji: '😐', label: '一般' },
	{ emoji: '😮‍💨', label: '有点累' },
	{ emoji: '😕', label: '不太好' },
]

const recommends = ref([])
const recommendLoading = ref(false)
const recommendError = ref(false)

const entries = [
	{ icon: 'time', title: '正念片刻', sub: '呼吸练习，慢下来', action: goMindfulness },
	{ icon: 'star', title: '疗愈推荐', sub: '找到适合的手作', action: goRecommend },
	{ icon: 'image', title: '作品社区', sub: '看看大家的创作', action: goWorks },
	{ icon: 'user', title: '非遗传承人', sub: '认识手作老师', action: goInheritors },
]

const nickname = computed(
	() => (userStore.userInfo && userStore.userInfo.nickname) || '同学'
)

const avatarLetter = computed(() => {
	const n = nickname.value
	return n ? n.charAt(0) : '同'
})

const hasNotice = ref(false) // TODO: 后端有未读消息接口后改为动态获取

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
}

function nextQuote() {
	quoteIndex.value++
}

function refreshRecommend() {
	loadRecommends()
}

onShow(() => {
	if (!userStore.isLogin) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	loadCurrentUser()
	checkPrivacyConsent()
	if (recommends.value.length === 0) {
		loadRecommends()
	}
})

async function checkPrivacyConsent() {
	// 未查询过隐私状态时拉取一次
	if (userStore.privacyAgreed === null) {
		await userStore.fetchPrivacyStatus()
	}
	// 未同意且本次会话未「仅浏览」→ 弹窗
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

// 页面级生命周期：tab 切换时暂停/重置主动气泡计时（M2-b3B）
onShow(() => {
	if (aiStore.proactiveEnabled) {
		aiStore.proactiveCount = 0
		aiStore.resetIdle()
	}
})
onHide(() => {
	if (aiStore.proactiveEnabled) {
		aiStore.pauseIdle()
	}
})

async function loadCurrentUser() {
	if (loadingMe.value) return
	loadingMe.value = true
	try {
		const data = await request({ url: '/api/auth/me', method: 'GET' })
		userStore.setUserInfo(data)
	} catch (e) {
	} finally {
		loadingMe.value = false
	}
}

function goEmotion() {
	uni.navigateTo({ url: '/pages/emotion/chat' })
}

function goRecommend() {
	uni.navigateTo({ url: '/pages/starter/index' })
}

function goCraftDetail(item) {
	if (item && item.projectId) {
		uni.navigateTo({ url: `/pages/craft/detail?id=${item.projectId}` })
	} else {
		goRecommend()
	}
}

function recommendImgStyle(item) {
	if (item && item.coverUrl) return ''
	// 渐变 fallback
	const gradients = [
		'linear-gradient(135deg, #2f7566, #5BCFBF)',
		'linear-gradient(135deg, #bb914d, #FFD08A)',
		'linear-gradient(135deg, #FF6B6B, #FF9B9B)',
	]
	const idx = (item && item.projectId ? item.projectId : 0) % gradients.length
	return `background: ${gradients[idx]};`
}

async function loadRecommends() {
	if (recommendLoading.value) return
	recommendLoading.value = true
	recommendError.value = false
	try {
		const data = await request({ url: '/api/craft/recommend', method: 'GET' })
		if (data && data.items) {
			recommends.value = data.items
		}
	} catch (e) {
		recommendError.value = true
		// 保留清晰的失败状态，允许重试
		recommends.value = []
	} finally {
		recommendLoading.value = false
	}
}

function goWorks() {
	uni.switchTab({ url: '/pages/work/list' })
}

function goProfile() {
	uni.switchTab({ url: '/pages/profile/index' })
}

function goMindfulness() {
	uni.navigateTo({ url: '/pages/mindfulness/index' })
}

function goInheritors() {
	uni.navigateTo({ url: '/pages/inheritor/list' })
}

function goMusic(){uni.navigateTo({url:'/pages/music/index'})}
function goSupport(){uni.navigateTo({url:'/pages/support/index'})}
</script>
<style scoped>
.home-page{background:#f6f7f2;min-height:100vh;color:#294333;padding:32rpx 34rpx 60rpx;box-sizing:border-box}.home-inner{max-width:1080px;margin:auto}.home-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:38rpx}.wordmark{display:flex;gap:15rpx;align-items:center}.wordmark image{width:70rpx;height:70rpx}.brand-name{display:block;font-size:35rpx;font-weight:600;letter-spacing:5rpx}.brand-caption{display:block;font-size:18rpx;color:#889580;letter-spacing:2rpx;margin-top:3rpx}.avatar{width:68rpx;height:68rpx;line-height:68rpx;padding:0;border-radius:50%;background:#e7ecdf;color:#567354;font-size:25rpx;margin:0}.hello-row{display:flex;justify-content:space-between;gap:20rpx;color:#7b8b77;font-size:21rpx;margin:0 0 20rpx}.hello-row>text:last-child{color:#405e46}.home-hero{position:relative;overflow:hidden;min-height:480rpx;border-radius:28rpx;background:#e7edde;border:1rpx solid #dce5d3;padding:40rpx 34rpx;box-sizing:border-box}.hero-copy{position:relative;z-index:1;max-width:72%}.eyebrow{display:block;font-size:17rpx;letter-spacing:3rpx;color:#899b7b;margin-bottom:14rpx}.hero-title{display:block;font-family:'Songti SC','SimSun',serif;font-size:53rpx;font-weight:500;line-height:1.45;letter-spacing:3rpx}.hero-note{display:block;font-size:22rpx;line-height:1.9;color:#7a8b70;margin-top:20rpx}.hero-button{display:flex;justify-content:space-between;gap:24rpx;width:fit-content;margin:30rpx 0 0;padding:0 25rpx;background:#35664b;border-radius:12rpx;font-size:23rpx;color:#fff;line-height:3}.hero-art{position:absolute;right:-190rpx;bottom:20rpx;width:610rpx;height:380rpx;opacity:.8;pointer-events:none}.hero-bottom{position:absolute;bottom:22rpx;right:25rpx;font-size:16rpx;color:#94a286;letter-spacing:2rpx}.section-head{display:flex;align-items:center;justify-content:space-between;margin:40rpx 0 20rpx;gap:16rpx}.section-head .eyebrow{font-size:16rpx;margin-bottom:8rpx}.section-title{display:block;font-size:31rpx;font-weight:550;letter-spacing:1rpx}.small-label{font-size:17rpx;color:#9aa48f;white-space:nowrap}.mood-card{display:flex;background:#fff;padding:25rpx 10rpx;border:1rpx solid #e8ece2;border-radius:22rpx;justify-content:space-around}.mood{flex:1;padding:0 2rpx;background:transparent;font-size:20rpx;color:#8a9884;line-height:1.8;border-radius:14rpx}.mood.chosen{background:#f0f5e9;color:#446c4e}.face{position:relative;width:59rpx;height:59rpx;border-radius:50%;background:#f7e9bf;margin:0 auto 14rpx}.face-1{background:#dce9d2}.face-2{background:#ece8db}.face-3{background:#e7e4ef}.face-4{background:#e3e9ee}.eyes:before,.eyes:after{content:'';position:absolute;width:4rpx;height:5rpx;border-radius:50%;background:#68735f;top:23rpx}.eyes:before{left:18rpx}.eyes:after{right:18rpx}.mouth{position:absolute;left:22rpx;top:31rpx;width:16rpx;height:9rpx;border:2rpx solid #68735f;border-top:0;border-radius:0 0 20rpx 20rpx}.face-2 .mouth{height:1rpx;border:0;background:#68735f;top:36rpx}.face-3 .mouth{height:8rpx;width:7rpx;left:27rpx;border:2rpx solid #68735f;border-radius:50%}.face-4 .mouth{transform:rotate(180deg);top:34rpx}.mood-response{display:flex;gap:16rpx;align-items:center;font-size:21rpx;color:#6a8065;margin-top:20rpx;line-height:1.7}.mood-response button{font-size:21rpx;white-space:nowrap;background:transparent;padding:0;color:#35664b;margin:0}.practice-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx}.practice{position:relative;min-height:282rpx;border-radius:22rpx;padding:28rpx 24rpx;text-align:left;line-height:1.6;width:100%;margin:0;overflow:hidden}.breath{background:#e7eeea}.chat{background:#f1eadc}.practice-kicker{display:block;font-size:16rpx;color:#90a091;letter-spacing:2rpx;margin-top:82rpx}.practice-title{display:block;font-size:27rpx;color:#3b5a45;font-weight:550;margin-top:7rpx}.practice-sub{display:block;font-size:17rpx;color:#90a08f;margin-top:8rpx;max-width:95%}.practice-arrow{position:absolute;right:24rpx;top:24rpx;color:#82927d}.breath-orbit{position:absolute;left:26rpx;top:28rpx;border:1rpx solid #a8c1ab;width:68rpx;height:68rpx;border-radius:50%;display:grid;place-items:center}.breath-orbit view{width:42rpx;height:42rpx;border-radius:50%;background:#c6d8c4;border:1rpx solid #b5cdb7}.chat-shape{position:absolute;left:25rpx;top:25rpx;background:#e3d6b9;border-radius:24rpx 24rpx 24rpx 3rpx;width:68rpx;height:65rpx;text-align:center;line-height:65rpx;color:#9b8b67;font-family:serif;font-size:28rpx}.plain{background:transparent;padding:0;margin:0;font-size:19rpx;color:#71856b}.craft-row{display:flex;align-items:center;width:100%;gap:22rpx;padding:20rpx 0;background:transparent;text-align:left;border-bottom:1rpx solid #e1e8d9;line-height:1.5;border-radius:0;margin:0}.craft-thumb{position:relative;background:#e5e9d9;border-radius:16rpx;width:125rpx;height:123rpx;flex-shrink:0;overflow:hidden;display:grid;place-items:center}.craft-thumb image{width:100%;height:100%}.thumb-1{background:#ede5d8}.thumb-2{background:#e3eae7}.paper-leaf{width:50rpx;height:75rpx;border-radius:80% 0 80% 0;transform:rotate(-20deg);background:#aec19e;border:1rpx solid #9caf8a}.craft-copy{flex:1}.craft-category{display:block;font-size:16rpx;color:#929e88;margin-bottom:7rpx;letter-spacing:2rpx}.craft-title{display:block;font-size:27rpx;color:#36523c}.craft-meta{display:block;font-size:19rpx;color:#99a28e;margin-top:12rpx}.craft-arrow{font-size:25rpx;color:#9bab90}.daily-note{border:1rpx solid #e2e7d9;border-radius:20rpx;padding:26rpx;margin-top:26rpx;background:#f1f3e9}.quote{display:block;font-family:'Songti SC','SimSun',serif;font-size:28rpx;color:#657954;line-height:1.8}.note-hint{display:block;font-size:17rpx;color:#a3ad95;margin-top:14rpx}.explore-links{margin-top:36rpx;border-top:1rpx solid #e0e7d8;padding-top:18rpx}.explore-links button{display:flex;justify-content:space-between;background:transparent;border-bottom:1rpx solid #e6ebdf;border-radius:0;font-size:24rpx;color:#547047;padding:20rpx 0;line-height:1.8}.explore-links text{font-size:18rpx;color:#9ba88d}.home-foot{display:block;text-align:center;font-size:18rpx;color:#a1ad96;margin:36rpx 0}.empty-card{padding:30rpx;background:#fff;border:1rpx solid #e1e8d9;border-radius:18rpx;font-size:23rpx;color:#8b9c80;line-height:1.8}.empty-card button{font-size:22rpx;color:#46744e;background:transparent}button::after{border:0}button:active{opacity:.82}button:focus-visible{outline:3px solid #739a71;outline-offset:3px}.motion-reduced *{animation:none!important;transition:none!important}@media(min-width:850px){.home-page{padding:28px 48px}.home-header{margin-bottom:28px}.home-hero{min-height:310px;padding:36px 42px}.hero-title{font-size:40px}.hero-copy{max-width:60%}.hero-art{width:620px;height:380px;right:-30px;bottom:-45px}.home-columns{display:grid;grid-template-columns:1.15fr 1fr;gap:36px}.explore-links{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:25px}.explore-links button{display:block;text-align:left}.explore-links text{display:block;margin-top:10px}.practice{min-height:210px}.practice-kicker{margin-top:90px}}
</style>
<style scoped>.hero-copy{max-width:90%}.hero-title{font-size:49rpx;letter-spacing:1rpx}.hero-art{opacity:.52}.brand-caption,.hero-note,.small-label,.mood,.practice-sub,.craft-meta,.note-hint,.explore-links text,.home-foot{color:#687b60}.practice-kicker,.craft-category,.eyebrow{color:#74836b}.practice-sub,.craft-meta,.note-hint,.home-foot{font-size:22rpx}.explore-links text{font-size:21rpx}.hero-bottom{color:#6e805f}@media(min-width:850px){.hero-copy{max-width:65%}.hero-title{font-size:40px}.hero-art{opacity:.8}}</style>

<style scoped>.music-entry{display:flex;justify-content:space-between;align-items:center;text-align:left;margin:24rpx 0;background:#e8efe4;border:1rpx solid #dfe7dc;border-radius:24rpx;padding:26rpx 30rpx;width:100%;color:#233d34;font-size:29rpx;line-height:1.6}.music-sub{display:block;font-size:22rpx;color:#586e62;margin-top:8rpx}.music-symbol{font-size:48rpx;color:#2f7566}.home-page{background:var(--zy-bg)}.home-hero{background:var(--zy-bg-tint-mint)}.hero-title,.section-title{font-family:var(--zy-sans);font-weight:600}.hero-button{background:var(--zy-primary)}</style>
