<template>
	<view class="page">
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
						<text class="welcome-avatar-text">织</text>
					</view>
					<text class="welcome-title">我在这里陪你</text>
					<text class="welcome-sub">我是织愈，一个耐心的倾听者</text>
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
							<text class="ai-avatar-text">织</text>
							<view class="ai-avatar-dot"></view>
						</view>
						<view class="msg-main">
							<view class="bubble bubble-ai">
								<text class="bubble-text text-ai">{{ msg.text }}</text>
								<text v-if="!msg.done" class="typing">▋</text>
							</view>
							<text v-if="msg.done" class="msg-time">{{ formatTime(msg) }}</text>
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
						@click="handleRecommend"
					>
						<text class="recommend-text">试试一件手作，让手帮心忙起来 →</text>
					</view>
				</view>

				<!-- 错误重试 -->
				<view v-if="errorMsg" class="error-row">
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
				:disabled="streaming"
				confirm-type="send"
				@confirm="handleSend"
				no-border
				custom-style="flex: 1;"
			/>
			<view class="voice-btn" @click="handleVoice">
				<text class="voice-icon">🎤</text>
			</view>
			<wd-button
				type="primary"
				size="small"
				:disabled="streaming || !inputText.trim()"
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

		<!-- 全局 AI 伴侣 -->
		<AiCompanion />
	</view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useEmotionStore } from '../../stores/emotion'
import { useUserStore } from '../../stores/user'
import { startSSE } from '../../utils/sse'
import { API_BASE_URL } from '../../utils/config.js'
import AiCompanion from '../../components/AiCompanion.vue'

const emotionStore = useEmotionStore()
const userStore = useUserStore()
const { messages } = storeToRefs(emotionStore)

const inputText = ref('')
const streaming = ref(false)
const errorMsg = ref('')
const showRecommendBtn = ref(false)
const scrollTop = ref(0)

let sseController = null
let lastSentContent = ''

const BASE_URL = API_BASE_URL

onLoad(() => {
	// 每次进入 chat 页，重置会话
	emotionStore.reset()
})

onUnload(() => {
	// 页面卸载时 abort SSE
	if (sseController) {
		sseController.abort()
		sseController = null
	}
})

function scrollToBottom() {
	nextTick(() => {
		scrollTop.value = 99999
	})
}

function handleSend() {
	if (streaming.value) return
	const content = inputText.value.trim()
	if (!content) return

	errorMsg.value = ''
	showRecommendBtn.value = false
	lastSentContent = content
	inputText.value = ''

	// 1. 追加用户消息
	emotionStore.appendUser(content)
	// 2. 开始 assistant 消息
	emotionStore.startAssistant()
	// 3. 禁用发送
	streaming.value = true
	scrollToBottom()

	// 4. 发起 SSE
	sendMessage(content)
}

function sendMessage(content) {
	if (sseController) {
		sseController.abort()
		sseController = null
	}

	sseController = startSSE({
		url: BASE_URL + '/api/emotion/chat',
		body: {
			sessionId: emotionStore.sessionId,
			content: content,
		},
		token: userStore.token,
		onMessage(text) {
			emotionStore.appendAssistantChunk(text)
			scrollToBottom()
		},
		onDone(data) {
			emotionStore.finishAssistant()
			if (data && data.sessionId) {
				emotionStore.setSessionId(data.sessionId)
			}
			streaming.value = false
			showRecommendBtn.value = true
			scrollToBottom()
		},
		onError(err) {
			emotionStore.finishAssistant()
			streaming.value = false
			errorMsg.value = (err && err.message) || '服务暂时不可用，请稍后重试'
			scrollToBottom()
		},
	})
}

function handleRetry() {
	errorMsg.value = ''
	// 移除上一条空的 assistant 消息
	if (emotionStore.messages.length > 0) {
		const last = emotionStore.messages[emotionStore.messages.length - 1]
		if (last.role === 'assistant' && !last.text) {
			emotionStore.messages.pop()
		}
	}
	// 重新发送
	emotionStore.startAssistant()
	streaming.value = true
	sendMessage(lastSentContent)
}

function handleRecommend() {
	const sid = emotionStore.sessionId || ''
	uni.navigateTo({
		url: `/pages/craft/recommend${sid ? '?sessionId=' + sid : ''}`,
	})
}

function handleVoice() {
	uni.showToast({ title: '语音功能即将上线', icon: 'none' })
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
</script>

<style lang="scss" scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	overflow: hidden;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 对话区（flex: 1 占满剩余空间） ── */
.chat-area {
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
	background: linear-gradient(135deg, #2D9B8F, #5BCFBF);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 32rpx;
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
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

.welcome-sub {
	margin-top: 16rpx;
	font-size: 28rpx;
	color: var(--wot-color-theme, #2D9B8F);
}

.welcome-hint {
	margin-top: 32rpx;
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
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
	background-color: #F3F4F6;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.user-avatar-text {
	font-size: 28rpx;
	font-weight: 500;
	color: #6B7280;
}

/* ── AI 头像 ── */
.ai-avatar {
	position: relative;
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.ai-avatar-text {
	font-size: 28rpx;
	font-weight: 600;
	color: var(--wot-color-theme, #2D9B8F);
	font-family: var(--zy-serif);
}

.ai-avatar-dot {
	position: absolute;
	bottom: 0;
	right: 0;
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	background-color: #2D9B8F;
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
	background-color: var(--wot-color-theme, #2D9B8F);
}

.bubble-ai {
	background-color: #FFFFFF;
	border: 1px solid var(--zy-border, #E5E7EB);
	box-shadow: 0 2rpx 8rpx rgba(45, 155, 143, 0.06);
}

.bubble-text {
	font-size: 15px;
	line-height: 1.6;
}

.text-user {
	color: #FFFFFF;
}

.text-ai {
	color: var(--zy-content, #4B5563);
}

/* ── 打字机光标 ── */
.typing {
	font-size: 15px;
	color: var(--wot-color-theme, #2D9B8F);
	margin-left: 2px;
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
	color: var(--zy-aid, #9CA3AF);
}

.msg-time--user {
	text-align: right;
}

/* ── 推荐入口 ── */
.recommend-entry {
	margin-top: 8px;
	padding: 10px 16px;
	border-radius: 12px;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.recommend-text {
	font-size: 13px;
	color: var(--wot-color-theme, #2D9B8F);
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
	color: var(--zy-danger, #FF6B6B);
}

/* ── 输入区（固定底部，不被压缩） ── */
.input-area {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	padding: 16rpx 24rpx;
	gap: 8px;
	background-color: #FFFFFF;
	border-top: 1rpx solid var(--zy-border, #E5E7EB);
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
	background-color: var(--zy-bg, #F8F9FB);
	border-radius: 12px;
	padding: 0 12px;
	height: 40px;
	font-size: 15px;
	color: var(--zy-title, #1F2937);
}

/* ── 底部免责声明（固定在输入区下方） ── */
.footer-disclaimer {
	flex-shrink: 0;
	padding: 8rpx 0;
	text-align: center;
	background-color: var(--zy-bg, #F8F9FB);
	padding-bottom: calc(8rpx + constant(safe-area-inset-bottom));
	padding-bottom: calc(8rpx + env(safe-area-inset-bottom));
}

.footer-disclaimer-text {
	font-size: 20rpx;
	color: var(--zy-aid, #9CA3AF);
}
</style>
