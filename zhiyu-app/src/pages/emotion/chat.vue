<template>
	<view class="page" @touchstart="idleArm" @click="idleArm">
		<view class="chat-header"><XiaotuanPet :size="42" aria-label="小织"/><view><text class="header-name">小织 · 倾听陪伴</text><text class="header-state">{{ streaming ? '正在回应你…' : '按自己的节奏，慢慢说' }}</text></view><AccessibleButton v-if="emotionStore.sessionId" class="header-help" :disabled="streaming" @click="endConversation">结束倾听</AccessibleButton><AccessibleButton class="header-help" @click="goCrisisPage">寻求支持</AccessibleButton></view>
		<!-- 对话区 -->
		<scroll-view
			class="chat-area"
			scroll-y
			:scroll-top="scrollTop"
			:scroll-with-animation="true"
		>
			<view class="chat-list">
				<!-- 空状态欢迎区 -->
				<view v-if="messages.length === 0" class="welcome">
					<view class="welcome-avatar">
						<XiaotuanPet :size="110" aria-label="小织陪伴你"/>
					</view>
					<text class="welcome-title">我在这里陪你</text>
					<text class="welcome-sub">我是小织，一个耐心的倾听者</text>
					<text class="welcome-hint">说说今天发生了什么？</text>
				</view>

				<view
					v-for="(msg, idx) in messages"
					:key="idx"
					class="msg-row"
					:class="msg.role === 'user' ? 'msg-right' : 'msg-left'"
				>
					<!-- AI 消息：头像 + 气泡 -->
					<view v-if="msg.role === 'assistant'" class="msg-content">
						<view class="ai-avatar">
							<XiaotuanPet :size="30"/>
							<view class="ai-avatar-dot"></view>
						</view>
						<view class="msg-main">
							<view class="bubble bubble-ai">
								<text class="bubble-text text-ai">{{ msg.text }}</text>
								<text v-if="!msg.done" class="typing"></text>
							</view>
							<text v-if="msg.done" class="msg-time">{{ formatTime(msg) }}</text>
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
						</view>
					</view>

						<!-- 用户消息：气泡 + 头像 -->
						<view v-else class="msg-content msg-content--user">
							<view class="msg-main msg-main--user">
								<view class="bubble bubble-user">
									<text class="bubble-text text-user">{{ msg.text }}</text>
								</view>
								<text class="msg-time msg-time--user">{{ formatTime(msg) }}</text>
							</view>
							<view class="user-avatar">
								<text class="user-avatar-text">{{ userInitial }}</text>
							</view>
						</view>

					<!-- 最后一条 assistant 消息完成后，显示推荐入口 -->
					<view
						v-if="msg.role === 'assistant' && msg.done && idx === messages.length - 1 && showRecommendBtn"
						class="recommend-entry"
						role="button"
						tabindex="0"
						aria-label="查看为你推荐的手作"
						@click="handleRecommend"
						@keydown.enter.prevent="handleRecommend"
						@keydown.space.prevent="handleRecommend"
					>
						<text class="recommend-text">试试一件手作，让手帮心忙起来 →</text>
					</view>
				</view>

			<!-- 危机关怀卡片 -->
			<view v-if="showCrisisCard" class="crisis-card">
				<view class="crisis-card-header">
					<view class="crisis-avatar">
						<text class="crisis-avatar-text">织</text>
					</view>
					<text class="crisis-card-title">小织想对你说</text>
				</view>
				<text class="crisis-card-text">我在这里陪着你。如果需要，可以联系专业资源。</text>
				<view class="crisis-card-btns">
					<view class="crisis-btn crisis-btn-primary" role="button" tabindex="0" aria-label="查看心理援助资源" @click="goCrisisPage" @keydown.enter.prevent="goCrisisPage" @keydown.space.prevent="goCrisisPage">
						<wd-icon name="phone" size="28rpx" color="#FFFFFF" />
						<text class="crisis-btn-text">查看心理援助资源</text>
					</view>
					<view class="crisis-btn crisis-btn-secondary" role="button" tabindex="0" aria-label="继续聊聊" @click="dismissCrisis" @keydown.enter.prevent="dismissCrisis" @keydown.space.prevent="dismissCrisis">
						<text class="crisis-btn-text-secondary">继续聊聊</text>
					</view>
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

		<!-- 输入区（固定底部） -->
		<view class="input-area">
			<wd-input
				custom-class="chat-input"
				v-model="inputText"
				placeholder="说说你现在的感受..."
				:disabled="streaming || historyLoading || historyError || isConsentError"
				confirm-type="send"
				@confirm="handleSend"
				no-border
				custom-style="flex: 1;"
			/>
			<view class="voice-btn" @click="handleVoice">
				<text class="voice-icon">{{voiceState==='recording'?'停止':voiceState==='idle'?'🎤':'处理中'}}</text>
			</view>
			<wd-button
				type="primary"
				size="small"
				:disabled="streaming || historyLoading || historyError || isConsentError || !inputText.trim()"
				@click="handleSend"
				custom-style="border-radius: 999rpx; min-width: 120rpx;"
			>
				发送
			</wd-button>
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
		<Toast />
	</view>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {request} from '../../utils/request'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useEmotionStore } from '../../stores/emotion'
import { useUserStore } from '../../stores/user'
import {useVoiceInput} from '../../composables/useVoiceInput'
import {playSpeech, stopSpeech} from '../../utils/speech-playback'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import StateBlock from '../../components/StateBlock.vue'
import CompanionNudge from '../../components/CompanionNudge.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'
import { useIdleNudge } from '../../composables/useIdleNudge'

const toast = useToast()

const emotionStore = useEmotionStore()
const userStore = useUserStore()
const { messages, streaming, errorMsg, errorCode, scrollSignal, crisisTriggered } = storeToRefs(emotionStore)

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
const historyId = ref('')
const historyLoading = ref(false)
const historyError = ref(false)
async function loadHistory() {
 historyLoading.value = true; historyError.value = false; emotionStore.errorMsg = ''
 try {
  const detail = await request({url:'/api/emotion/sessions/'+historyId.value})
  emotionStore.sessionId = detail.session.sessionId
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

// watch scrollSignal 触发滚动到底部
watch(scrollSignal, () => {
	scrollToBottom()
})
watch(() => messages.value.length, () => {
	scrollToBottom()
})

function scrollToBottom() {
	nextTick(() => {
		scrollTop.value = 99999
	})
}

function handleSend() {
	if (streaming.value || historyLoading.value || historyError.value) return
	const content = inputText.value.trim()
	if (!content) return

	showRecommendBtn.value = false
	inputText.value = ''

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

function dismissCrisis() {
	emotionStore.dismissCrisis()
}

// ── 纯展示辅助函数（不涉及 SSE / store 逻辑） ──
const userInitial = computed(() => {
	const n = (userStore.userInfo && userStore.userInfo.nickname) || '同'
	return n ? n.charAt(0) : '同'
})

function formatTime(msg) {
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
async function endConversation(){if(streaming.value||!emotionStore.sessionId)return;try{await request({url:'/api/emotion/sessions/'+emotionStore.sessionId+'/state',method:'PUT',data:{action:'END'}});uni.redirectTo({url:'/pages/emotion/history'})}catch(e){emotionStore.errorMsg=e.message}}
</script>

<style lang="scss" scoped>
.chat-companion{width:64rpx;height:80rpx;flex-shrink:0}.chat-header .header-help{font-size:22rpx;padding:8rpx 12rpx;line-height:1.8;background:#e8efe4;border-radius:12rpx}.chat-header{display:flex;align-items:center;gap:20rpx;padding:24rpx 28rpx;border-bottom:1rpx solid var(--zy-border,#e2e9e9);background:var(--zy-card-bg,#fff);flex-shrink:0}.header-avatar{padding:12rpx 20rpx;background:#217e76;color:#fff;border-radius:18rpx}.header-name{display:block;font-size:28rpx;font-weight:600;color:var(--zy-title,#253c36)}.header-state{display:block;font-size:22rpx;color:var(--zy-sub,#708078);margin-top:6rpx}.header-help{font-size:23rpx;color:#217e76;margin-left:auto}.input-area,.footer-disclaimer{flex-shrink:0}

.page {
	display: flex;
	flex-direction: column;
	height: calc(100vh - var(--window-top, 0px));
	overflow: hidden;
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 对话区（flex: 1 占满剩余空间） ── */
.chat-area {
 min-height:0;
 height:0;
	flex: 1;
	overflow: hidden;
}

.chat-list {
	padding: 24rpx 32rpx;
}

/* ── 空状态欢迎区 ── */
.welcome {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 160rpx 0 120rpx;
}

.welcome-avatar {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-primary-light, #5BCFBF));
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 32rpx;
	box-shadow: var(--zy-shadow-sm);
}

.welcome-avatar-text {
	font-size: 40rpx;
	font-weight: 600;
	color: #FFFFFF;
	font-family: var(--zy-serif);
}

.welcome-title {
	font-size: 44rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
	font-family: var(--zy-serif);
}

.welcome-sub {
	margin-top: 16rpx;
	font-size: 28rpx;
	color: var(--zy-primary, #217e76);
}

.welcome-hint {
	margin-top: 32rpx;
	font-size: 26rpx;
	color: var(--zy-aid, #4a5f54);
}

/* ── 消息行 ── */
.msg-row {
	margin-bottom: 32rpx;
	display: flex;
	flex-direction: column;
}

.msg-right {
	align-items: flex-end;
}

.msg-left {
	align-items: flex-start;
}

/* ── 消息内容容器（头像+气泡） ── */
.msg-content {
	display: flex;
	align-items: flex-start;
	gap: 16rpx;
	max-width: 100%;
}

.msg-content--user {
	flex-direction: row-reverse;
}

.msg-main {
	display: flex;
	flex-direction: column;
	max-width: 72%;
}

.msg-main--user {
	align-items: flex-end;
}

/* ── 用户头像 ── */
.user-avatar {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background-color: var(--zy-border, #dfe7dc);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.user-avatar-text {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-text, #4a5f54);
}

/* ── AI 头像 ── */
.ai-avatar {
	position: relative;
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.ai-avatar-text {
	font-size: 28rpx;
	font-weight: 600;
	color: var(--zy-primary, #217e76);
	font-family: var(--zy-serif);
}

.ai-avatar-dot {
	position: absolute;
	bottom: 0;
	right: 0;
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	background-color: var(--zy-primary, #217e76);
	border: 3rpx solid #FFFFFF;
	box-sizing: border-box;
}

/* ── 气泡 ── */
.bubble {
	padding: 24rpx 32rpx;
	border-radius: 16px;
	max-width: 100%;
	width: fit-content;
	word-break: break-word;
}

.bubble-user {
	background-color: var(--zy-primary, #217e76);
}

.bubble-ai {
	background-color: #FFFFFF;
	border: 1px solid var(--zy-border, #dfe7dc);
	box-shadow: 0 2rpx 8rpx rgba(33, 126, 118, 0.06);
}

.bubble-text {
	font-size: 15px;
	line-height: 1.6;
}

.text-user {
	color: #FFFFFF;
}

.text-ai {
	color: var(--zy-content, #4a5f54);
}

/* ── 打字机光标 ── */
.typing {
	display: inline-block;
	width: 6px;
	height: 18px;
	margin-left: 3px;
	vertical-align: text-bottom;
	border-radius: 2px;
	background: linear-gradient(180deg, var(--zy-primary, #217e76), var(--zy-primary-light, #5bc4a8));
	animation: blink 0.8s steps(2) infinite;
}

@keyframes blink {
	0%, 100% { opacity: 1; }
	50% { opacity: 0; }
}

/* ── 时间戳 ── */
.msg-time {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--zy-aid, #4a5f54);
}

.msg-time--user {
	text-align: right;
}

.msg-actions {
	display: flex;
	gap: 8px;
	margin-top: 6px;
}

.speak-btn {
	min-height: 32px;
	margin: 0;
	padding: 0 14px;
	border: 1px solid var(--zy-border, #dfe7dc);
	border-radius: 999rpx;
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-primary, #217e76);
	font-size: 12px;
	line-height: 32px;
}

.speak-btn::after {
	border: 0;
}

.speak-btn.is-pressed {
	background: var(--zy-primary, #217e76);
	color: #fff;
}

.speak-btn.is-disabled {
	opacity: 0.6;
}

/* ── 推荐入口 ── */
.recommend-entry {
	margin-top: 8px;
	padding: 10px 16px;
	border-radius: 12px;
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.recommend-text {
	font-size: 13px;
	color: var(--wot-color-theme, #217e76);
}

/* ── 错误重试 ── */
.error-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12px;
	margin-top: 12px;
}

.error-text {
	font-size: 13px;
	color: var(--zy-danger, #a64842);
}

/* ── 输入区（固定底部，不被压缩） ── */
.input-area {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	padding: 16rpx 24rpx;
	gap: 8px;
	background-color: #FFFFFF;
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

.voice-btn {
	width: 36px;
	height: 36px;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.voice-icon {
	font-size: 20px;
}

:deep(.chat-input) {
	flex: 1;
	background-color: var(--zy-bg, #f5f7f2);
	border-radius: 12px;
	padding: 0 12px;
	height: 40px;
	font-size: 15px;
	color: var(--zy-title, #233d34);
}

/* ── 底部免责声明（固定在输入区下方） ── */
.footer-disclaimer {
	flex-shrink: 0;
	padding: 8rpx 0;
	text-align: center;
	background-color: var(--zy-bg, #f5f7f2);
	padding-bottom: calc(8rpx + constant(safe-area-inset-bottom));
	padding-bottom: calc(8rpx + env(safe-area-inset-bottom));
}

.footer-disclaimer-text {
	font-size: 20rpx;
	color: var(--zy-aid, #4a5f54);
}

/* ── 危机关怀卡片 ── */
.crisis-card {
	margin: 16rpx 0 24rpx;
	padding: 32rpx 28rpx;
	border-radius: 32rpx;
	background: linear-gradient(135deg, #e8efe4 0%, #FFFFFF 100%);
	border: 2rpx solid rgba(33, 126, 118, 0.2);
	box-shadow: 0 4rpx 20rpx rgba(33, 126, 118, 0.08);
	animation: crisisIn 0.4s cubic-bezier(0.22, 0.61, 0.36, 1);
}

@keyframes crisisIn {
	from { opacity: 0; transform: translateY(16rpx) scale(0.97); }
	to { opacity: 1; transform: translateY(0) scale(1); }
}

.crisis-card-header {
	display: flex;
	align-items: center;
	gap: 16rpx;
	margin-bottom: 20rpx;
}

.crisis-avatar {
	width: 56rpx;
	height: 56rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, var(--zy-primary, #217e76), var(--zy-primary-light, #5BCFBF));
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.crisis-avatar-text {
	font-size: 26rpx;
	font-weight: 600;
	color: #FFFFFF;
	font-family: var(--zy-serif);
}

.crisis-card-title {
	font-size: 30rpx;
	font-weight: 600;
	color: var(--zy-primary, #217e76);
	font-family: var(--zy-serif);
}

.crisis-card-text {
	display: block;
	font-size: 28rpx;
	line-height: 1.7;
	color: var(--zy-content, #4a5f54);
	margin-bottom: 28rpx;
}

.crisis-card-btns {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
}

.crisis-btn {
	height: 76rpx;
	border-radius: 999rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10rpx;
	transition: transform 0.2s ease;
}

.crisis-btn:active {
	transform: scale(0.97);
}

.crisis-btn-primary {
	background: var(--zy-brand-600, #217e76);
}

.crisis-btn-secondary {
	background-color: var(--zy-bg, #f5f7f2);
	border: 1rpx solid var(--zy-border, #dfe7dc);
}

.crisis-btn-text {
	font-size: 28rpx;
	font-weight: 500;
	color: #FFFFFF;
}

.crisis-btn-text-secondary {
	font-size: 28rpx;
	color: var(--zy-aid, #4a5f54);
}
</style>

<style scoped>
.recommend-entry:focus-visible,
.crisis-btn:focus-visible {
	outline: 3px solid var(--zy-primary, #217e76);
	outline-offset: 3px;
}
</style>
