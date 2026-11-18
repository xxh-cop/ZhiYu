<template>
	<view :class="zyThemeClass" class="page" @touchstart="idleArm" @click="idleArm">
		<view class="chat-header">
			<AccessibleButton class="nav-back" aria-label="返回上一页" @click="requestLeave">←</AccessibleButton>
			<view class="header-avatar" aria-hidden="true"><text class="header-avatar-text">织</text></view>
			<view class="chat-header-copy">
				<text class="header-name zy-t-serif">小织</text>
				<text class="header-state">{{ headerState }}</text>
			</view>
			<AccessibleButton variant="danger" size="sm" class="header-action support-btn" aria-label="查看心理援助资源" @click="moreOpen = false; goCrisisPage()">求助</AccessibleButton>
			<AccessibleButton class="more-btn" aria-label="更多会话操作" :aria-expanded="moreOpen" @click.stop="toggleMore">···</AccessibleButton>
		</view>
		<!-- 更多操作：主流聊天式浮层菜单，保留结束倾听与返回入口 -->
			<view v-if="moreOpen" class="more-mask" @click="moreOpen = false"></view>
		<view v-if="moreOpen" class="more-menu" role="menu" aria-label="会话操作">
			<AccessibleButton v-if="emotionStore.sessionId && !historyId" class="more-item" @click="requestEnd">结束本次倾听<text class="more-note">存入倾听记录，之后可回看</text></AccessibleButton>
			<AccessibleButton class="more-item" @click="goCrisisPage(); moreOpen = false">心理援助资源</AccessibleButton>
			<AccessibleButton class="more-item" @click="moreOpen = false; leaveToHome()">返回首页</AccessibleButton>
		</view>
		<!-- 对话区 -->
		<view class="chat-body">
			<scroll-view
				class="chat-area"
				scroll-y
				:scroll-top="scrollTop"
				:scroll-with-animation="true"
				@scroll="onChatScroll"
			>
				<view class="chat-list">
					<view v-if="messages.length" class="chat-date">
						<text>{{ chatDateLabel }}</text>
					</view>
					<!-- 空状态：玄墨倾听室，只留一句提问和三行开头 -->
					<view v-if="messages.length === 0" class="welcome">
					<text class="welcome-eyebrow">玄墨倾听室 · 只在此刻</text>
					<view class="welcome-rule" aria-hidden="true"></view>
					<text class="welcome-title zy-t-serif">今天想从哪儿说起？</text>
					<text class="welcome-sub">不用想好句子，说半句也行。我不评价，也不替你总结。</text>
					<view v-if="memoryPreview" class="memory-line" role="button" tabindex="0" @click="goMemory" @keydown.enter.prevent="goMemory" @keydown.space.prevent="goMemory">
						<text class="memory-label">小织记得的</text>
						<text class="memory-text">{{ memoryPreview }}</text>
						<text class="memory-link">改</text>
					</view>
					<text v-else class="memory-note">小织只会记住你明确选择的偏好；对话原文不保存。</text>
					<view class="welcome-openers">
						<AccessibleButton v-for="(c, i) in OPENERS" :key="c" class="opener-row" @click="fillInput(c)">
							<text class="opener-index">{{ String(i + 1).padStart(2, '0') }}</text>
							<text class="opener-text">{{ c }}</text>
							<text class="opener-arrow" aria-hidden="true">→</text>
						</AccessibleButton>
					</view>
					<text class="welcome-foot">如果此刻有危险，请点右上角「求助」。</text>
				</view>

				<view
					v-for="(msg, idx) in messages"
					:key="idx"
					class="msg-row"
				>
					<ChatBubble
						v-if="msg.role === 'assistant'"
						role="ai"
						dark
						:time="msg.done ? formatTime(msg) : ''"
						:typing="!msg.done && !msg.text"
					>{{ msg.text }}</ChatBubble>
					<ChatBubble
						v-else
						role="user"
						dark
						:time="formatTime(msg)"
						:user-initial="userInitial"
					>{{ msg.text }}</ChatBubble>

					<!-- AI 消息附加：provider 标注 / 朗读 / 推荐入口（逻辑判定原样保留） -->
					<template v-if="msg.role === 'assistant'">
						<!-- 2026-11-08 装配层批：一次性归属标注——仅本条确为本地模型产出时出现，后续不重复 -->
						<text v-if="msg.done && msg.provider === 'ollama'" class="msg-provider">本条由校内本地模型生成</text>
						<view v-if="msg.done && speechAvailable && msg.text" class="msg-actions">
							<button
								class="speak-btn"
								:class="{ speaking: speakingIndex === idx, 'is-pressed': speakingIndex === idx, 'is-disabled': speechBusy === idx }"
								:aria-pressed="speakingIndex === idx ? 'true' : 'false'"
								:disabled="speechBusy === idx"
								:aria-label="speakingIndex === idx ? '停止朗读这条回复' : '朗读这条回复'"
								@click="toggleSpeak(msg, idx)"
							>{{ speechBusy === idx ? '准备语音…' : speakingIndex === idx ? '停止' : '听' }}</button>
						</view>
						<!-- 最后一条 assistant 消息完成后，显示推荐入口 -->
						<AccessibleButton
							v-if="msg.done && idx === messages.length - 1 && showRecommendBtn"
							class="recommend-entry"
							aria-label="查看为你推荐的手作"
							@click="handleRecommend"
						>
							<text class="recommend-text">试试一件手作，让手帮心忙起来 →</text>
						</AccessibleButton>
					</template>
				</view>

			<!-- 危机关怀卡片 -->
			<view v-if="showCrisisCard" class="crisis-card">
				<view class="crisis-card-header">
					<view class="crisis-avatar"><XiaotuanPet :size="30" face="care" /></view>
					<text class="crisis-card-title zy-t-serif">小织想对你说</text>
				</view>
				<text class="crisis-card-text">我先陪你一会儿。如果撑不住，这些电话现在就能打。</text>
				<view class="crisis-card-btns">
					<AccessibleButton variant="care" size="md" aria-label="查看心理援助资源" @click="goCrisisPage">
						<wd-icon name="phone" size="28rpx" color="#FFFFFF" />
						<text>查看心理援助资源</text>
					</AccessibleButton>
					<AccessibleButton variant="secondary" size="md" aria-label="继续聊聊" @click="dismissCrisis">继续聊聊</AccessibleButton>
				</view>
			</view>

			<!-- 隐私同意引导（Q2）：撤回同意后对话不可用，引导主动查看并重新同意（不自动弹） -->
			<StateBlock
				v-if="isConsentError"
				type="error"
				face="care"
				title="需要你重新同意隐私政策"
				description="你之前撤回了隐私政策同意。对话内容属于敏感信息，只有在你重新同意后，小织才能继续陪你聊。是否同意，始终由你决定。"
				action-text="去了解并重新同意"
				@action="goPrivacySettings"
			/>
			<!-- 错误重试 -->
			<view v-else-if="errorMsg" class="error-row">
				<text class="error-text">{{ errorMsg }}</text>
				<wd-button type="error" size="small" plain @click="handleRetry">重试</wd-button>
			</view>
		</view>
	</scroll-view>
		<!-- 主流聊天式「回到底部」：滚离底部时出现，有新回复时带未读数 -->
		<AccessibleButton v-if="messages.length && !atBottom" class="jump-latest" :aria-label="unreadCount ? `回到底部，${unreadCount} 条新消息` : '回到底部'" @click="jumpToLatest">
			<text class="jump-arrow" aria-hidden="true">↓</text>
			<text class="jump-text">{{ unreadCount ? `${unreadCount} 条新消息` : '回到最新' }}</text>
		</AccessibleButton>
	</view>

		<!-- 2026-11-08 装配层批：本地兜底持续轻注（云端正常时不出现；危机出口常驻） -->
		<view v-if="localMode" class="local-note">
			<text class="local-note__text">当前为本地陪伴模式，回复会朴素一些。紧急情况请随时点</text>
			<text class="local-note__link" @click="goCrisisPage">「求助」</text>
		</view>

		<!-- 输入区（固定底部）；历史对话只读，改给「开始新的倾听」出口 -->
		<ChatInputBar
			v-if="!historyId || historyLoading"
			v-model="inputText"
			dark
			placeholder="说说你现在的感受…"
			:disabled="streaming || historyLoading || historyError || isConsentError"
			:voice-state="voiceStateView"
			@send="handleSend"
			@voice="handleVoice"
		/>
		<view v-else class="history-dock">
			<text class="history-dock-text">这段对话已结束，内容只读</text>
			<AccessibleButton class="history-new" @click="startNew">开始新的倾听</AccessibleButton>
		</view>

		<!-- 底部免责声明（固定在输入区下方） -->
		<view class="footer-disclaimer">
			<text class="footer-disclaimer-text">本系统不能替代专业心理咨询</text>
		</view>
		<!-- R3 时机②：长停无操作的一次性陪伴（9s 自动退场，可点 ✕ / 右滑关闭；点按即收起） -->
		<CompanionNudge
			:open="idleNudgeOpen"
			text="不用急着说完"
			face="care"
			bottom-offset="calc(220rpx + env(safe-area-inset-bottom))"
			@action="idleNudgeOpen = false"
			@close="idleNudgeOpen = false"
		/>
		<!-- 会话退场确认：返回/结束都先给一次明确选择，避免误触丢上下文 -->
		<AccessibleDialog :open="!!closeMode" :title="closeTitle" dark @close="closeMode = ''">
			<text class="close-copy">{{ closeCopy }}</text>
			<view class="close-actions">
				<AccessibleButton class="close-cancel" @click="closeMode = ''">继续聊</AccessibleButton>
				<AccessibleButton class="close-confirm" :loading="closing" :disabled="closing" @click="confirmClose">{{ closing ? '正在结束…' : closeActionText }}</AccessibleButton>
			</view>
		</AccessibleDialog>
		<Toast />
	</view>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {request} from '../../utils/request'
import { onLoad, onUnload, onShow, onReady, onBackPress } from '@dcloudio/uni-app'
import { useEmotionStore } from '../../stores/emotion'
import { useUserStore } from '../../stores/user'
import {useVoiceInput} from '../../composables/useVoiceInput'
import {playSpeech, stopSpeech} from '../../utils/speech-playback'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import AccessibleDialog from '../../components/AccessibleDialog.vue'
import StateBlock from '../../components/StateBlock.vue'
import CompanionNudge from '../../components/CompanionNudge.vue'
import ChatBubble from '../../components/ChatBubble.vue'
import ChatInputBar from '../../components/ChatInputBar.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { useIdleNudge } from '../../composables/useIdleNudge'

const toast = useToast()

// 「小织记得的」（2026-11-15）：空状态时给一条可修改的记忆提示；未开启记忆时不显示。
const memoryPreview = ref('')
async function loadMemoryHint() {
	try {
		const m = await request({ url: '/api/companion/memory' })
		if (!m.enabled) { memoryPreview.value = ''; return }
		const parts = []
		const preferred = (m.items || []).map(i => i.valueLabel).filter(Boolean)
		if (preferred.length) parts.push(preferred.slice(0, 3).join(' · '))
		else {
			const calm = (m.derived || []).find(d => d.key === 'calm_strategy')
			if (calm) parts.push(calm.value)
		}
		if ((m.avoidTopics || []).length) parts.push(`不聊${m.avoidTopics.join('、')}`)
		memoryPreview.value = parts.length ? parts.slice(0, 2).join(' · ') : ''
	} catch (e) { memoryPreview.value = '' }
}
function goMemory() { uni.navigateTo({ url: '/pages/settings/memory' }) }
async function ensureUserInfo() {
	if (!userStore.isLogin || (userStore.userInfo && userStore.userInfo.nickname)) return
	try {
		const me = await request({ url: '/api/auth/me', timeout: 15000 })
		if (me) userStore.setUserInfo(me)
	} catch (e) { /* 头像回落为「同」，不影响对话 */ }
}
async function refreshHeader() {
	await ensureUserInfo()
	loadMemoryHint()
}
onShow(refreshHeader)

const emotionStore = useEmotionStore()
const userStore = useUserStore()
const { messages, streaming, errorMsg, errorCode, scrollSignal, crisisTriggered, localMode } = storeToRefs(emotionStore)

// Q2：撤回隐私同意后，情绪接口返回 1003「请先阅读并同意隐私政策」。
// 此时展示「去重新同意」引导态（不自动弹同意、不阻断用户选择），替代裸错误 + 无效重试。
const isConsentError = computed(() => errorCode.value === 1003 && /隐私政策/.test(errorMsg.value || ''))
function goPrivacySettings() {
	uni.navigateTo({ url: '/pages/privacy/settings' })
}

// R3 时机②：页内长停无操作 120s，小织出现一次（仅「正在做事」页启用；
// 任何交互重置计时；quotaKey page-idle 每天 ≤2 次）
const idleNudgeOpen = ref(false)
const { arm: idleArm } = useIdleNudge({
	quotaKey: 'page-idle',
	onTrigger: () => { idleNudgeOpen.value = true },
})

const inputText = ref('')
const speechAvailable = ref(false)
const speakingIndex = ref(-1)
const speechBusy = ref(-1)
const {state:voiceState,toggle:toggleVoice}=useVoiceInput(text=>{inputText.value=text})
const showRecommendBtn = ref(false)
const showCrisisCard = ref(false)
const scrollTop = ref(0)
let scrollTick = 0
const atBottom = ref(true)
const unreadCount = ref(0)
const viewportH = ref(0)
const moreOpen = ref(false)
const closeMode = ref('') // '' | 'leave' | 'end'
const closing = ref(false)
const leaveTarget = ref('back') // back | home
const bypassBack = ref(false)
const todayLabel = computed(() => {
	const d = new Date()
	return `${d.getMonth() + 1}月${d.getDate()}日 · 周${'日一二三四五六'[d.getDay()]}`
})
const headerState = computed(() => {
	if (historyLoading.value) return '正在读取对话…'
	if (historyId.value) return '已结束 · 只读'
	return streaming.value ? '正在回应你…' : '在听，不评价'
})
const chatDateLabel = computed(() => {
	if (!historyId.value) return `本次对话 · ${todayLabel.value}`
	const time = historyStartTime.value ? String(historyStartTime.value).replace('T', ' ').slice(0, 16) : ''
	return `历史对话${time ? ' · ' + time : ''} · 只读`
})
const closeTitle = computed(() => closeMode.value === 'end' ? '结束本次倾听？' : '离开玄墨倾听室？')
const closeCopy = computed(() => {
	if (closeMode.value === 'end') return '结束后这次对话会存入「倾听记录」，之后可以回看。'
	if (messages.value.length && !emotionStore.sessionId) {
		return '小织还在回应。现在离开会中断这次对话，已发送的内容可能不会保存。'
	}
	return '离开会结束本次对话，并存进「倾听记录」。想继续聊，就留在这里。'
})
const closeActionText = computed(() => {
	if (closeMode.value === 'end') return '结束并查看记录'
	return messages.value.length && !emotionStore.sessionId ? '仍要离开' : '结束并离开'
})
const historyId = ref('')
const historyStartTime = ref('')
const historyLoading = ref(false)
const historyError = ref(false)
async function loadHistory() {
 historyLoading.value = true; historyError.value = false; emotionStore.errorMsg = ''
 try {
  const detail = await request({url:'/api/emotion/sessions/'+historyId.value})
  emotionStore.sessionId = detail.session.sessionId
  historyStartTime.value = detail.session.startTime || ''
  emotionStore.messages = detail.messages.map(m=>({role:m.role,text:m.content,done:true}))
 } catch(e) { historyError.value = true; emotionStore.errorMsg = e.message || '历史对话读取失败，请重试'; emotionStore.errorCode = (e && typeof e.code === 'number' && e.code) || 0 }
 finally { historyLoading.value = false }
}

onLoad(async (query) => {
 loadSpeechStatus()
 if(query?.sessionId){emotionStore.reset();historyId.value=query.sessionId;await loadHistory();return;}
	// 面板关闭后跳来时 preserveMessages=true，保留消息上下文
	if (emotionStore.preserveMessages) {
		emotionStore.preserveMessages = false // 消费掉
	} else {
		// 正常进入（如首页点「情绪倾听」），重置会话
		emotionStore.reset()
	}
})

onUnload(() => {
	stopSpeech()
	// 页面卸载时 abort SSE
	emotionStore.abortSSE()
})

onReady(() => {
	uni.createSelectorQuery().select('.chat-area').boundingClientRect(rect => {
		if (rect && rect.height) viewportH.value = rect.height
	}).exec()
})

// 主流聊天行为：只有停留在底部时才跟随新内容；翻看历史时不抢滚动
watch(scrollSignal, () => {
	if (atBottom.value) scrollToBottom()
})
watch(() => messages.value.length, (next, prev) => {
	if (!next) return
	if (atBottom.value) scrollToBottom()
	else unreadCount.value += Math.max(1, next - (prev || 0))
})

function onChatScroll(event) {
	const d = (event && event.detail) || {}
	const gap = (d.scrollHeight || 0) - (d.scrollTop || 0) - viewportH.value
	const nearBottom = gap <= 140
	atBottom.value = nearBottom
	if (nearBottom) unreadCount.value = 0
}
function jumpToLatest() {
	atBottom.value = true
	unreadCount.value = 0
	scrollToBottom()
}

function scrollToBottom() {
	nextTick(() => {
		// 递增值确保每次都是新的 prop，uni scroll-view 才会重新滚到底
		scrollTop.value = 99999 + (++scrollTick % 1000)
	})
}

function handleSend() {
	if (streaming.value || historyLoading.value || historyError.value) return
	const content = inputText.value.trim()
	if (!content) return

	showRecommendBtn.value = false
	inputText.value = ''
	atBottom.value = true
	unreadCount.value = 0
	scrollToBottom()

	// 调用 store 的 sendMessage（SSE 逻辑已抽取到 store）
	emotionStore.sendMessage(content)
}

function handleRetry() {
	if (historyError.value) { loadHistory(); return }
	emotionStore.retryLast()
}

function handleRecommend() {
	const sid = emotionStore.sessionId || ''
	uni.navigateTo({
		url: `/pages/craft/recommend${sid ? '?sessionId=' + sid : ''}`,
	})
}

function handleVoice() {
	stopSpeech()
	speakingIndex.value=-1
	if(!streaming.value)toggleVoice()
}

async function loadSpeechStatus(){
	if(!userStore.isLogin)return
	try{
		const status=await request({url:'/api/speech/status'})
		speechAvailable.value=!!status?.available
	}catch(e){
		speechAvailable.value=false
	}
}

async function toggleSpeak(msg,idx){
	if(!speechAvailable.value||!msg?.text)return
	if(speakingIndex.value===idx){
		stopSpeech()
		speakingIndex.value=-1
		return
	}
	if(speechBusy.value!==-1)return
	stopSpeech()
	speakingIndex.value=-1
	speechBusy.value=idx
	try{
		const data=await request({url:'/api/speech/synthesize',method:'POST',data:{text:msg.text.slice(0,2000)},timeout:30000})
		if(!data?.audioBase64)throw new Error('语音暂时不可用')
		speakingIndex.value=idx
		playSpeech(data.audioBase64,'audio/mpeg',{
			onEnd:()=>{if(speakingIndex.value===idx)speakingIndex.value=-1},
			onError:()=>{
				if(speakingIndex.value===idx)speakingIndex.value=-1
				toast.care('语音播放失败，可以继续看文字')
			}
		})
	}catch(e){
		speakingIndex.value=-1
		toast.care(e.message||'语音暂时不可用')
	}finally{
		speechBusy.value=-1
	}
}

function goCrisisPage() {
	uni.navigateTo({ url: '/pages/crisis/index' })
}

function startNew() {
	emotionStore.reset()
	historyId.value = ''
	uni.redirectTo({ url: '/pages/emotion/chat' })
}

function toggleMore() {
	moreOpen.value = !moreOpen.value
}

// 返回/离开：有进行中的对话时先确认，避免误触把上下文丢掉
function requestLeave(target = 'back') {
	if (bypassBack.value) return
	leaveTarget.value = target
	moreOpen.value = false
	if (!messages.value.length || historyId.value) {
		leavePage()
		return
	}
	closeMode.value = 'leave'
}

// 结束：保留原「结束倾听」语义，结束后进入倾听记录
function requestEnd() {
	moreOpen.value = false
	if (!emotionStore.sessionId) {
		toast.care('现在还没有可以结束的对话')
		return
	}
	if (streaming.value) {
		toast.care('等小织说完这句，再结束好吗？')
		return
	}
	closeMode.value = 'end'
}

function leaveToHome() {
	requestLeave('home')
}

async function confirmClose() {
	if (closing.value) return
	const mode = closeMode.value
	if (!mode) return
	closing.value = true
	closeMode.value = ''
	try {
		if (mode === 'end') await endConversation()
		else await endAndLeave()
	} finally {
		closing.value = false
	}
}

async function endAndLeave() {
	emotionStore.abortSSE()
	try {
		if (emotionStore.sessionId && !historyId.value) {
			await request({
				url: '/api/emotion/sessions/' + emotionStore.sessionId + '/state',
				method: 'PUT',
				data: { action: 'END' },
			})
		}
		leavePage()
	} catch (e) {
		toast.care((e && e.message) || '结束失败，请重试')
	}
}

function leavePage() {
	bypassBack.value = true
	const done = () => setTimeout(() => { bypassBack.value = false }, 400)
	if (leaveTarget.value === 'home') {
		uni.switchTab({ url: '/pages/home/index', complete: done })
		return
	}
	uni.navigateBack({
		fail: () => uni.switchTab({ url: '/pages/home/index' }),
		complete: done,
	})
}

onBackPress(() => {
	if (bypassBack.value) return false
	if (moreOpen.value) {
		moreOpen.value = false
		return true
	}
	if (closeMode.value) {
		closeMode.value = ''
		return true
	}
	if (messages.value.length && !historyId.value) {
		leaveTarget.value = 'back'
		closeMode.value = 'leave'
		return true
	}
	return false
})

function dismissCrisis() {
	emotionStore.dismissCrisis()
}

// ── 纯展示辅助函数（不涉及 SSE / store 逻辑） ──
const userInitial = computed(() => {
	const n = (userStore.userInfo && userStore.userInfo.nickname) || '同'
	return n ? n.charAt(0) : '同'
})

function formatTime(msg) {
	if (historyId.value) return ''
	const d = new Date()
	const h = String(d.getHours()).padStart(2, '0')
	const m = String(d.getMinutes()).padStart(2, '0')
	return `${h}:${m}`
}

// SSE done 后显示推荐按钮（watch 最后一条消息的 done 状态）
watch(() => {
	const last = messages.value[messages.value.length - 1]
	return last && last.role === 'assistant' && last.done
}, (val) => {
	if (val) showRecommendBtn.value = true
})

// 危机卡片：crisisTriggered 延迟 0.5s 显示
watch(crisisTriggered, (val) => {
	if (val) {
		setTimeout(() => {
			showCrisisCard.value = true
			nextTick(() => scrollToBottom())
		}, 500)
	} else {
		showCrisisCard.value = false
	}
})
async function endConversation(){
	if(!emotionStore.sessionId){toast.care('现在还没有可以结束的对话');return}
	emotionStore.abortSSE()
	try{
		await request({url:'/api/emotion/sessions/'+emotionStore.sessionId+'/state',method:'PUT',data:{action:'END'}})
		uni.redirectTo({url:'/pages/emotion/history'})
	}catch(e){
		toast.care((e&&e.message)||'结束失败，请重试')
	}
}

/* ── 暖纸青玉 P4：纯展示新增（无逻辑改动） ── */
// 空状态开场白（静态文案，点击仅回填输入框，不接接口）
const OPENERS = ['最近静不下心来', '今天有一件开心的小事', '我也不知道从哪说起']
function fillInput(text) { inputText.value = text }
// 语音状态映射：useVoiceInput 的 idle/starting/recording/uploading → ChatInputBar 的 ''/recording/processing
const voiceStateView = computed(() => {
	const s = voiceState.value
	if (s === 'recording') return 'recording'
	if (s === 'starting' || s === 'uploading') return 'processing'
	return ''
})
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: calc(100vh - var(--window-top, 0px));
	overflow: hidden;
	background-color: var(--zy-d-bg, #0B1513);
	color: var(--zy-d-text, #F1F4F1);
}

/* ── 页头：主流聊天结构——返回 / 身份 / 求助 / 更多 ── */
.chat-header { position: relative; z-index: 70; display: flex; align-items: center; gap: 12rpx; padding: 16rpx 24rpx; padding-top: calc(16rpx + env(safe-area-inset-top, 0px)); flex-shrink: 0; border-bottom: 1rpx solid rgba(241,244,241,.12); }
.nav-back { width: 64rpx; height: 64rpx; min-width: 64rpx; min-height: 64rpx; margin: 0; padding: 0; background: transparent; border: 0; color: var(--zy-d-text); font-size: 34rpx; line-height: 1; }
.nav-back::after { border: 0; }
.chat-header-copy { flex: 1; min-width: 0; }
.header-avatar { width: 52rpx; height: 52rpx; margin-right: 12rpx; border: 1rpx solid rgba(143,199,184,.42); border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: rgba(143,199,184,.08); }
.header-avatar-text { font-family: var(--zy-serif); font-size: 22rpx; line-height: 1; color: var(--zy-brand-300); }
.header-name { display: block; font-size: 32rpx; font-weight: 500; letter-spacing: 3rpx; color: var(--zy-d-text); white-space: nowrap; }
.header-state { display: block; font-size: 20rpx; color: var(--zy-d-text-secondary); margin-top: 4rpx; letter-spacing: 1rpx; }
.header-action { flex-shrink: 0; margin: 0; }
.support-btn { min-height: 56rpx; padding: 0 18rpx; font-size: 22rpx; }
.more-btn { width: 56rpx; height: 56rpx; min-width: 56rpx; min-height: 56rpx; margin: 0; padding: 0; background: transparent; border: 1rpx solid rgba(241,244,241,.24); border-radius: 0; color: var(--zy-d-text-secondary); font-size: 24rpx; line-height: 1; }
.more-btn::after { border: 0; }
.more-mask { position: fixed; inset: 0; z-index: 65; background: transparent; }
.more-menu { position: absolute; top: calc(env(safe-area-inset-top, 0px) + 112rpx); right: 24rpx; z-index: 72; width: 360rpx; padding: 8rpx 0; background: #10201D; border: 1rpx solid rgba(241,244,241,.22); box-shadow: 0 18rpx 48rpx rgba(0,0,0,.4); }
.more-item { display: flex; flex-direction: column; align-items: flex-start; width: 100%; min-height: 80rpx; margin: 0; padding: 18rpx 24rpx; background: transparent; border: 0; border-bottom: 1rpx solid rgba(241,244,241,.12); border-radius: 0; color: var(--zy-d-text); font-size: 25rpx; line-height: 1.4; }
.more-item:last-child { border-bottom: 0; }
.more-item::after { border: 0; }
.more-item:active { background: rgba(143,199,184,.1); }
.more-note { margin-top: 4rpx; font-size: 20rpx; color: var(--zy-d-text-secondary); }

/* ── 对话区（flex: 1 占满剩余空间） ── */
.chat-body { position: relative; flex: 1; min-height: 0; display: flex; flex-direction: column; }
.chat-area {
	min-height: 0;
	height: 100%;
	flex: 1;
	overflow: hidden;
}
.chat-list { padding: 0 36rpx 32rpx; }
.chat-date { display: flex; justify-content: center; padding: 24rpx 0 8rpx; }
.chat-date text { padding: 6rpx 18rpx; border: 1rpx solid rgba(241,244,241,.14); color: rgba(169,187,178,.72); font-size: 20rpx; letter-spacing: 1rpx; }

/* ── 空状态：玄墨、偏左、像进入一间房 ── */
.welcome { display: flex; flex-direction: column; align-items: flex-start; padding: 11vh 0 56rpx; }
.welcome-eyebrow { color: var(--zy-d-text-secondary); font-size: 20rpx; letter-spacing: 4rpx; }
.welcome-rule { width: 44rpx; height: 2rpx; margin: 26rpx 0 30rpx; background: var(--zy-care); }
.welcome-title { font-size: 58rpx; line-height: 1.35; font-weight: 500; color: var(--zy-d-text); letter-spacing: 2rpx; max-width: 88%; }
.welcome-sub { margin-top: 20rpx; font-size: 26rpx; line-height: 1.9; color: var(--zy-d-text-secondary); max-width: 88%; }
.memory-line { display: grid; grid-template-columns: 1fr auto; column-gap: 14rpx; row-gap: 6rpx; align-items: end; margin-top: 34rpx; padding-left: 16rpx; border-left: 2rpx solid rgba(196,64,47,.7); }
.memory-label { grid-column: 1 / -1; color: var(--zy-brand-300); font-size: 20rpx; letter-spacing: 2rpx; }
.memory-text { grid-column: 1; color: var(--zy-d-text-secondary); font-size: 24rpx; line-height: 1.7; }
.memory-link { grid-column: 2; color: var(--zy-brand-300); font-size: 20rpx; padding: 0 4rpx; text-decoration: underline; text-underline-offset: 5rpx; }
.memory-note { margin-top: 30rpx; color: rgba(169,187,178,.66); font-size: 21rpx; line-height: 1.7; }
.welcome-openers { width: 100%; margin-top: 46rpx; }
.opener-row { display: flex; align-items: center; gap: 16rpx; width: 100%; padding: 22rpx 0; background: transparent; border: 0; border-top: 1rpx solid rgba(241,244,241,.14); border-radius: 0; text-align: left; margin: 0; color: var(--zy-d-text-secondary); line-height: 1.5; }
.opener-index { width: 44rpx; flex-shrink: 0; color: rgba(169,187,178,.55); font-family: var(--zy-serif); font-size: 20rpx; }
.opener-text { flex: 1; font-size: 27rpx; }
.opener-arrow { color: rgba(169,187,178,.55); font-size: 24rpx; }
.welcome-foot { margin-top: 40rpx; color: rgba(169,187,178,.5); font-size: 20rpx; }

/* ── 消息行（暗色排版行在 ChatBubble dark 内） ── */
.msg-row { display: flex; flex-direction: column; }
.msg-provider { display: block; margin: -30rpx 0 26rpx 62rpx; font-size: 20rpx; color: var(--zy-d-text-secondary); opacity: .6; }
.msg-actions { display: flex; gap: 16rpx; margin: -28rpx 0 26rpx 62rpx; }
.speak-btn { min-height: 52rpx; margin: 0; padding: 0 18rpx; border-radius: 0; background: transparent; border: 1rpx solid rgba(143,199,184,.4); color: var(--zy-brand-300); font-size: 20rpx; line-height: 1.6; }
.speak-btn::after { border: 0; }
.speak-btn.is-pressed { background: rgba(143,199,184,.16); color: var(--zy-d-text); }
.speak-btn.is-disabled { opacity: .5; }

/* ── 推荐入口：一行字，不做胶囊 ── */
.recommend-entry { align-self: flex-start; margin: -16rpx 0 28rpx 62rpx; padding: 10rpx 0; border-radius: 0; background: transparent; border-bottom: 1rpx solid rgba(196,64,47,.6); box-shadow: none; line-height: 1.6; }
.recommend-text { font-size: 23rpx; color: var(--zy-brand-300); }

/* ── 错误重试 ── */
.error-row { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 12px; }
.error-text { font-size: var(--zy-text-caption); color: var(--zy-danger, var(--zy-care, #a64842)); }

/* ── 本地兜底持续轻注 ── */
.local-note { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; padding: 12rpx 24rpx; background: rgba(143,199,184,.08); border-top: 1rpx solid rgba(143,199,184,.18); }
.local-note__text { font-size: var(--zy-text-micro); color: var(--zy-d-text-secondary); }
.local-note__link { font-size: var(--zy-text-micro); color: var(--zy-brand-300); }

/* ── 回到底部：不抢历史阅读，有新消息才提示 ── */
.jump-latest { position: absolute; right: 28rpx; bottom: 24rpx; z-index: 20; display: flex; align-items: center; gap: 8rpx; min-height: 64rpx; margin: 0; padding: 0 20rpx; background: #10201D; border: 1rpx solid rgba(143,199,184,.5); border-radius: 0; color: var(--zy-brand-300); font-size: 21rpx; line-height: 1.4; box-shadow: 0 10rpx 28rpx rgba(0,0,0,.28); }
.jump-latest::after { border: 0; }
.jump-arrow { font-size: 26rpx; line-height: 1; }
.jump-text { white-space: nowrap; }

/* ── 历史对话只读出口 ── */
.history-dock { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 20rpx 32rpx 8rpx; border-top: 1rpx solid rgba(241,244,241,.18); }
.history-dock-text { font-size: 22rpx; color: var(--zy-d-text-secondary); }
.history-new { min-height: 60rpx; margin: 0; padding: 0 18rpx; background: transparent; border: 1rpx solid rgba(143,199,184,.5); border-radius: 0; color: var(--zy-brand-300); font-size: 22rpx; }
.history-new::after { border: 0; }

/* ── 会话退场确认 ── */
.close-copy { display: block; font-size: 25rpx; line-height: 1.8; color: var(--zy-d-text-secondary); margin-bottom: 28rpx; }
.close-actions { display: flex; gap: 16rpx; justify-content: flex-end; }
.close-cancel { min-height: 64rpx; margin: 0; padding: 0 24rpx; background: transparent; border: 1rpx solid rgba(241,244,241,.24); border-radius: 0; color: var(--zy-d-text-secondary); font-size: 24rpx; }
.close-confirm { min-height: 64rpx; margin: 0; padding: 0 24rpx; background: var(--zy-care); border: 1rpx solid var(--zy-care); border-radius: 0; color: #FFFFFF; font-size: 24rpx; }
.close-cancel::after, .close-confirm::after { border: 0; }

/* ── 底部免责声明（发丝线上缘） ── */
.footer-disclaimer { flex-shrink: 0; margin: 0 32rpx; padding: 12rpx 0; padding-bottom: calc(12rpx + constant(safe-area-inset-bottom)); padding-bottom: calc(12rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid rgba(241,244,241,.14); text-align: center; }
.footer-disclaimer-text { font-size: var(--zy-text-micro); color: rgba(169,187,178,.58); }

/* ── 危机关怀卡片：暗底朱砂，不跳出房间 ── */
.crisis-card {
	margin: 16rpx 0 24rpx;
	padding: 28rpx 0 28rpx 20rpx;
	border-left: 2rpx solid var(--zy-d-care, #D4736A);
	background: transparent;
	animation: crisisIn 0.4s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.crisis-card-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 18rpx; }
.crisis-avatar { flex-shrink: 0; }
.crisis-card-title { font-size: 30rpx; font-weight: 500; color: var(--zy-d-care, #D4736A); }
.crisis-card-text { display: block; font-size: 26rpx; line-height: 1.8; color: var(--zy-d-text); margin-bottom: 24rpx; }
.crisis-card-btns { display: flex; flex-direction: column; gap: 16rpx; }
@keyframes crisisIn {
	from { opacity: 0; transform: translateY(16rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.motion-reduced .crisis-card { animation: none; }
</style>
