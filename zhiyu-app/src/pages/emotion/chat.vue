<template>
	<view class="page">
		<!-- 顶部免责提示 -->
		<view class="topbar">
			<text class="topbar-text">本系统不能替代专业心理咨询</text>
		</view>

		<!-- 对话区 -->
		<scroll-view
			class="chat-area"
			scroll-y
			:scroll-top="scrollTop"
			:scroll-with-animation="true"
		>
			<view class="chat-list">
				<view
					v-for="(msg, idx) in messages"
					:key="idx"
					class="msg-row"
					:class="msg.role === 'user' ? 'msg-right' : 'msg-left'"
				>
					<view
						class="bubble"
						:class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'"
					>
						<text class="bubble-text" :class="msg.role === 'user' ? 'text-user' : 'text-ai'">{{ msg.text }}</text>
						<text v-if="msg.role === 'assistant' && !msg.done" class="typing">...</text>
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
					<button class="retry-btn" size="mini" @click="handleRetry">重试</button>
				</view>
			</view>
		</scroll-view>

		<!-- 输入区 -->
		<view class="input-area">
			<input
				class="text-input"
				type="text"
				v-model="inputText"
				placeholder="说说你现在的感受..."
				placeholder-class="input-placeholder"
				:disabled="streaming"
				confirm-type="send"
				@confirm="handleSend"
			/>
			<view class="voice-btn" @click="handleVoice">
				<text class="voice-icon">🎤</text>
			</view>
			<button
				class="send-btn"
				:disabled="streaming || !inputText.trim()"
				@click="handleSend"
			>
				发送
			</button>
		</view>
	</view>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useEmotionStore } from '../../stores/emotion'
import { useUserStore } from '../../stores/user'
import { startSSE } from '../../utils/sse'

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

// 小程序端用局域网 IP，H5 端用 localhost
let BASE_URL = 'http://localhost:8080'
// #ifdef MP-WEIXIN
BASE_URL = 'http://192.168.0.102:8080'
// #endif

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
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: #f7f4ef;
}

/* 顶部免责提示 */
.topbar {
	padding: 8px 16px;
	background-color: #ece3d5;
	text-align: center;
}

.topbar-text {
	font-size: 12px;
	color: #8a8378;
}

/* 对话区 */
.chat-area {
	flex: 1;
	overflow: hidden;
}

.chat-list {
	padding: 16px 12px;
}

.msg-row {
	margin-bottom: 12px;
	display: flex;
	flex-direction: column;
}

.msg-right {
	align-items: flex-end;
}

.msg-left {
	align-items: flex-start;
}

.bubble {
	max-width: 75%;
	padding: 10px 14px;
	border-radius: 8px;
}

.bubble-user {
	background-color: #8b6f47;
}

.bubble-ai {
	background-color: #ffffff;
	border: 1px solid #e5ddd0;
}

.bubble-text {
	font-size: 15px;
	line-height: 1.6;
}

.text-user {
	color: #fffdfa;
}

.text-ai {
	color: #2b2b2b;
}

.typing {
	font-size: 13px;
	color: #a49c90;
	margin-left: 4px;
}

/* 推荐入口 */
.recommend-entry {
	margin-top: 8px;
	padding: 8px 16px;
	border: 1px solid #c89b6e;
	border-radius: 8px;
	background-color: #ffffff;
}

.recommend-text {
	font-size: 13px;
	color: #c89b6e;
}

/* 错误重试 */
.error-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12px;
	margin-top: 12px;
}

.error-text {
	font-size: 13px;
	color: #b85450;
}

.retry-btn {
	font-size: 13px;
	background-color: #8b6f47;
	color: #fffdfa;
	border-radius: 8px;
}

/* 输入区 */
.input-area {
	display: flex;
	align-items: center;
	padding: 8px 12px 12px;
	gap: 8px;
	background-color: #fffdfa;
	border-top: 1px solid #e5ddd0;
}

.text-input {
	flex: 1;
	height: 40px;
	padding: 0 12px;
	border-radius: 8px;
	border: 1px solid #e5ddd0;
	background-color: #f7f4ef;
	font-size: 15px;
	color: #2b2b2b;
}

.input-placeholder {
	color: #a49c90;
}

.voice-btn {
	width: 36px;
	height: 36px;
	display: flex;
	align-items: center;
	justify-content: center;
}

.voice-icon {
	font-size: 20px;
}

.send-btn {
	min-width: 60px;
	height: 40px;
	line-height: 40px;
	padding: 0 16px;
	border-radius: 8px;
	background-color: #8b6f47;
	color: #fffdfa;
	font-size: 15px;
}

.send-btn[disabled] {
	opacity: 0.5;
}
</style>
