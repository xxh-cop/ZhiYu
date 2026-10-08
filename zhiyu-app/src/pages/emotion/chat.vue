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
								<text v-if="!msg.done" class="typing"></text>
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
					<view class="crisis-btn crisis-btn-primary" @click="goCrisisPage">
						<wd-icon name="phone" size="28rpx" color="#FFFFFF" />
						<text class="crisis-btn-text">查看心理援助资源</text>
					</view>
					<view class="crisis-btn crisis-btn-secondary" @click="dismissCrisis">
						<text class="crisis-btn-text-secondary">继续聊聊</text>
					</view>
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
	</view>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useEmotionStore } from '../../stores/emotion'
import { useUserStore } from '../../stores/user'

const emotionStore = useEmotionStore()
const userStore = useUserStore()
const { messages, streaming, errorMsg, scrollSignal, crisisTriggered } = storeToRefs(emotionStore)

const inputText = ref('')
const showRecommendBtn = ref(false)
const showCrisisCard = ref(false)
const scrollTop = ref(0)

onLoad(() => {
	// 面板关闭后跳来时 preserveMessages=true，保留消息上下文
	if (emotionStore.preserveMessages) {
		emotionStore.preserveMessages = false // 消费掉
	} else {
		// 正常进入（如首页点「情绪倾听」），重置会话
		emotionStore.reset()
	}
})

onUnload(() => {
	// 页面卸载时 abort SSE
	emotionStore.abortSSE()
})

// watch scrollSignal 触发滚动到底部
watch(scrollSignal, () => {
	scrollToBottom()
})
watch(() => messages.length, () => {
	scrollToBottom()
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

	showRecommendBtn.value = false
	inputText.value = ''

	// 调用 store 的 sendMessage（SSE 逻辑已抽取到 store）
	emotionStore.sendMessage(content)
}

function handleRetry() {
	emotionStore.retryLast()
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
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BCFBF));
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
	color: var(--zy-title, #1F2937);
	font-family: var(--zy-serif);
}

.welcome-sub {
	margin-top: 16rpx;
	font-size: 28rpx;
	color: var(--zy-primary, #2D9B8F);
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
	background-color: var(--zy-border, #F3F4F6);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.user-avatar-text {
	font-size: 28rpx;
	font-weight: 500;
	color: var(--zy-sub, #6B7280);
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
	color: var(--zy-primary, #2D9B8F);
	font-family: var(--zy-serif);
}

.ai-avatar-dot {
	position: absolute;
	bottom: 0;
	right: 0;
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	background-color: var(--zy-primary, #2D9B8F);
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
	background-color: var(--zy-primary, #2D9B8F);
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
	display: inline-block;
	width: 6px;
	height: 18px;
	margin-left: 3px;
	vertical-align: text-bottom;
	border-radius: 2px;
	background: linear-gradient(180deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
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

/* ── 危机关怀卡片 ── */
.crisis-card {
	margin: 16rpx 0 24rpx;
	padding: 32rpx 28rpx;
	border-radius: 32rpx;
	background: linear-gradient(135deg, #F0F9F7 0%, #FFFFFF 100%);
	border: 2rpx solid rgba(45, 155, 143, 0.2);
	box-shadow: 0 4rpx 20rpx rgba(45, 155, 143, 0.08);
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
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BCFBF));
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
	color: var(--zy-primary, #2D9B8F);
	font-family: var(--zy-serif);
}

.crisis-card-text {
	display: block;
	font-size: 28rpx;
	line-height: 1.7;
	color: var(--zy-content, #4B5563);
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
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
}

.crisis-btn-secondary {
	background-color: var(--zy-bg, #F8F9FB);
	border: 1rpx solid var(--zy-border, #E5E7EB);
}

.crisis-btn-text {
	font-size: 28rpx;
	font-weight: 500;
	color: #FFFFFF;
}

.crisis-btn-text-secondary {
	font-size: 28rpx;
	color: var(--zy-aid, #6B7280);
}
</style>
