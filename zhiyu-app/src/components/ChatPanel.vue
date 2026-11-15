<template>
	<!-- 遮罩层 -->
	<view class="cp-mask" @click="onMaskClick">
		<!-- 面板本体（阻止冒泡，避免点面板内部关闭） -->
		<view class="cp-panel" @click.stop>
			<!-- ═══ 头部 ═══ -->
			<view class="cp-header">
				<view class="cp-header-left">
				<view class="cp-avatar-dynamic">
					<XiaotuanPet :size="48" />
					<view class="cp-avatar-dot"></view>
				</view>
					<view class="cp-header-info">
						<text class="cp-header-title">小织</text>
						<text class="cp-header-sub">在线 · 陪伴你</text>
					</view>
				</view>
			<view class="cp-header-actions">
				<view class="cp-fullscreen-btn" role="button" tabindex="0" aria-label="全屏打开对话" @click="onFullscreen" @keydown.enter.prevent="onFullscreen" @keydown.space.prevent="onFullscreen">
					<wd-icon name="fullsreen" size="36rpx" color="var(--zy-text, #4a5f54)" />
				</view>
				<view class="cp-close-btn" role="button" tabindex="0" aria-label="关闭对话面板" @click="onClose" @keydown.enter.prevent="onClose" @keydown.space.prevent="onClose">
					<text class="cp-close-icon">×</text>
				</view>
			</view>
			</view>

			<!-- ═══ 消息区 ═══ -->
			<scroll-view
				class="cp-msg-area"
				scroll-y
				:scroll-top="scrollTop"
				:scroll-with-animation="true"
			>
				<view class="cp-msg-list">
					<!-- 空状态 -->
					<view v-if="messages.length === 0" class="cp-welcome">
						<text class="cp-welcome-text">{{ welcomeText }}</text>
						<text class="cp-welcome-hint">说说今天发生了什么？</text>
					</view>

					<!-- 消息列表 -->
					<view
						v-for="(msg, idx) in messages"
						:key="idx"
						class="cp-msg-row"
						:class="msg.role === 'user' ? 'cp-msg-right' : 'cp-msg-left'"
					>
						<!-- AI 消息 -->
						<view v-if="msg.role === 'assistant'" class="cp-msg-content">
							<view class="cp-ai-avatar">
								<text class="cp-ai-avatar-text">织</text>
							</view>
							<view class="cp-msg-main">
								<view class="cp-bubble cp-bubble-ai">
									<text class="cp-bubble-text cp-text-ai">{{ msg.text }}</text>
									<text v-if="!msg.done" class="cp-typing">▋</text>
								</view>
								<text v-if="msg.done" class="cp-msg-time">{{ formatTime() }}</text>
							</view>
						</view>

						<!-- 用户消息 -->
						<view v-else class="cp-msg-content cp-msg-content--user">
							<view class="cp-msg-main cp-msg-main--user">
								<view class="cp-bubble cp-bubble-user">
									<text class="cp-bubble-text cp-text-user">{{ msg.text }}</text>
								</view>
								<text class="cp-msg-time cp-msg-time--user">{{ formatTime() }}</text>
							</view>
							<view class="cp-user-avatar">
								<text class="cp-user-avatar-text">{{ userInitial }}</text>
							</view>
						</view>
					</view>

					<!-- 错误重试 -->
					<view v-if="errorMsg" class="cp-error-row">
						<text class="cp-error-text">{{ errorMsg }}</text>
						<wd-button type="error" size="small" plain @click="handleRetry">重试</wd-button>
					</view>
				</view>
			</scroll-view>

			<!-- ═══ 输入区 ═══ -->
			<view class="cp-input-area" :style="inputAreaStyle">
				<wd-input
					custom-class="cp-chat-input"
					v-model="inputText"
					placeholder="说说你现在的感受..."
					:disabled="streaming"
					confirm-type="send"
					:cursor-spacing="20"
					@confirm="handleSend"
					no-border
					custom-style="flex: 1;"
				/>
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

			<!-- 底部免责声明 -->
			<view class="cp-disclaimer" :style="disclaimerStyle">
				<text class="cp-disclaimer-text">本系统不能替代专业心理咨询</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useEmotionStore } from '../stores/emotion'
import { useUserStore } from '../stores/user'
import { useAiCompanionStore } from '../stores/aiCompanion'
import XiaotuanPet from './XiaotuanPet.vue'

const emit = defineEmits(['close'])

const emotionStore = useEmotionStore()
const userStore = useUserStore()
const aiStore = useAiCompanionStore()
const { messages, streaming, errorMsg, scrollSignal } = storeToRefs(emotionStore)

const inputText = ref('')
const scrollTop = ref(0)

// ── 头部小织渲染（线团小兽，组件内部跟随 aiStore.petState） ──

// ── 键盘高度（小程序端） ──
const keyboardHeight = ref(0)

// ── 陪伴语（从 aiCompanion store 的 BUBBLE_TEXTS 复制，保持独立） ──
const BUBBLE_TEXTS = [
	'今天也慢慢来呀～',
	'记得给自己一点时间',
	'需要我陪你聊聊吗？',
	'你已经很棒了',
	'深呼吸，一切都会好的',
	'累了就歇一会儿吧',
]
const welcomeText = computed(() => {
	return BUBBLE_TEXTS[Math.floor(Math.random() * BUBBLE_TEXTS.length)]
})

// ── 用户头像首字 ──
const userInitial = computed(() => {
	const n = (userStore.userInfo && userStore.userInfo.nickname) || '同'
	return n ? n.charAt(0) : '同'
})

// ── 输入区动态样式（小程序键盘弹起时上推） ──
const inputAreaStyle = computed(() => {
	if (keyboardHeight.value > 0) {
		return `padding-bottom: ${keyboardHeight.value}px;`
	}
	return ''
})

const disclaimerStyle = computed(() => {
	if (keyboardHeight.value > 0) {
		return `padding-bottom: ${keyboardHeight.value}px;`
	}
	return ''
})

// ── 滚动到底部 ──
function scrollToBottom() {
	nextTick(() => {
		scrollTop.value = 99999
	})
}

// watch scrollSignal 和 messages.length 都触发滚动
watch(scrollSignal, () => {
	scrollToBottom()
})
watch(() => messages.length, () => {
	scrollToBottom()
})

// ── 发送消息 ──
function handleSend() {
	const content = inputText.value.trim()
	if (!content || streaming.value) return
	inputText.value = ''
	emotionStore.sendMessage(content)
}

// ── 重试 ──
function handleRetry() {
	emotionStore.retryLast()
}

// ── 关闭面板 ──
function onClose() {
	emotionStore.abortSSE()
	emit('close')
}

// ── 全屏跳转 chat.vue ──
function onFullscreen() {
	emotionStore.preserveMessages = true
	emit('close')
	uni.navigateTo({ url: '/pages/emotion/chat' })
}

// ── 遮罩点击（不关闭，防误触） ──
function onMaskClick() {
	// 刻意不关闭
}

// ── 时间格式化 ──
function formatTime() {
	const d = new Date()
	const h = String(d.getHours()).padStart(2, '0')
	const m = String(d.getMinutes()).padStart(2, '0')
	return `${h}:${m}`
}

// ── 键盘监听（仅小程序） ──
onMounted(() => {
	// #ifdef MP-WEIXIN
	uni.onKeyboardHeightChange((res) => {
		keyboardHeight.value = res.height || 0
	})
	// #endif
})

onUnmounted(() => {
	// #ifdef MP-WEIXIN
	uni.offKeyboardHeightChange()
	// #endif
})
</script>

<style lang="scss" scoped>
/* ── 遮罩层 ── */
.cp-mask {
	position: fixed;
	inset: 0;
	z-index: 10000;
	background: rgba(0, 0, 0, 0.3);
	display: flex;
	align-items: flex-end;
	justify-content: center;
	animation: cpFadeIn 250ms cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes cpFadeIn {
	from {
		opacity: 0;
	}
	to {
		opacity: 1;
	}
}

/* ── 面板本体 ── */
.cp-panel {
	width: 100%;
	height: 75vh;
	background-color: var(--zy-bg, #ffffff);
	border-radius: 24rpx 24rpx 0 0;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	box-shadow: 0 -4rpx 24rpx rgba(0, 0, 0, 0.12);
	animation: cpSlideUp 250ms cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes cpSlideUp {
	from {
		transform: translateY(100%);
	}
	to {
		transform: translateY(0);
	}
}

/* ── 头部 ── */
.cp-header {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 20rpx 32rpx;
	background-color: #FFFFFF;
	border-bottom: 1rpx solid var(--zy-border, #dfe7dc);
	min-height: 140rpx;
	box-sizing: border-box;
}

.cp-header-left {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

/* ── 头部动态小织头像 ── */
.cp-avatar-dynamic {
	position: relative;
	width: 96rpx;
	height: 96rpx;
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}

.cp-avatar-dot {
	position: absolute;
	top: 8rpx;
	right: 8rpx;
	width: 16rpx;
	height: 16rpx;
	border-radius: 50%;
	background-color: var(--zy-success, #2e7d5b);
	border: 2rpx solid #FFFFFF;
	box-sizing: border-box;
}

.cp-header-info {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
}

.cp-header-title {
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.cp-header-sub {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
}

.cp-header-actions {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.cp-fullscreen-btn {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.cp-close-btn {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.cp-close-icon {
	font-size:var(--zy-text-title-lg, 40rpx);
	color: var(--zy-aid, #4a5f54);
	line-height: 1;
}

/* ── 消息区 ── */
.cp-msg-area {
	flex: 1;
	overflow: hidden;
}

.cp-msg-list {
	padding: 24rpx 32rpx;
}

/* ── 空状态 ── */
.cp-welcome {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 120rpx 0 80rpx;
}

.cp-welcome-text {
	font-size:var(--zy-text-body-lg, 30rpx);
	color: var(--wot-color-theme, var(--zy-brand-700, #1a645e));
	margin-bottom: 16rpx;
}

.cp-welcome-hint {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

/* ── 消息行 ── */
.cp-msg-row {
	margin-bottom: 32rpx;
	display: flex;
	flex-direction: column;
}

.cp-msg-right {
	align-items: flex-end;
}

.cp-msg-left {
	align-items: flex-start;
}

/* ── 消息内容容器 ── */
.cp-msg-content {
	display: flex;
	align-items: flex-start;
	gap: 16rpx;
	max-width: 100%;
}

.cp-msg-content--user {
	flex-direction: row-reverse;
}

.cp-msg-main {
	display: flex;
	flex-direction: column;
	max-width: 72%;
}

.cp-msg-main--user {
	align-items: flex-end;
}

/* ── AI 头像 ── */
.cp-ai-avatar {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.cp-ai-avatar-text {
	font-size:var(--zy-text-body, 28rpx);
	font-weight: 600;
	color: var(--wot-color-theme, var(--zy-brand-700, #1a645e));
}

/* ── 用户头像 ── */
.cp-user-avatar {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background-color: var(--zy-bg, #ffffff);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.cp-user-avatar-text {
	font-size:var(--zy-text-body, 28rpx);
	font-weight: 500;
	color: var(--zy-text, #4a5f54);
}

/* ── 气泡 ── */
.cp-bubble {
	padding: 24rpx 32rpx;
	border-radius: 16px;
	max-width: 100%;
	width: fit-content;
	word-break: break-word;
}

.cp-bubble-user {
	background-color: var(--wot-color-theme, var(--zy-brand-600, #217e76));
}

.cp-bubble-ai {
	background-color: #FFFFFF;
	border: 1px solid var(--zy-border, #dfe7dc);
	box-shadow: 0 2rpx 8rpx rgba(33, 126, 118, 0.06);
}

.cp-bubble-text {
	font-size: 15px;
	line-height: 1.6;
}

.cp-text-user {
	color: #FFFFFF;
}

.cp-text-ai {
	color: var(--zy-content, #4a5f54);
}

/* ── 打字机光标 ── */
.cp-typing {
	font-size: 15px;
	color: var(--wot-color-theme, var(--zy-brand-700, #1a645e));
	margin-left: 2px;
	animation: cpBlink 0.8s steps(2) infinite;
}

@keyframes cpBlink {
	0%, 100% { opacity: 1; }
	50% { opacity: 0; }
}

/* ── 时间戳 ── */
.cp-msg-time {
	margin-top: 8rpx;
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

.cp-msg-time--user {
	text-align: right;
}

/* ── 错误重试 ── */
.cp-error-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12px;
	margin-top: 12px;
}

.cp-error-text {
	font-size: 13px;
	color: var(--zy-danger, var(--zy-care, #a64842));
}

/* ── 输入区 ── */
.cp-input-area {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	padding: 16rpx 24rpx;
	gap: 8px;
	background-color: #FFFFFF;
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

:deep(.cp-chat-input) {
	flex: 1;
	background-color: var(--zy-bg, #ffffff);
	border-radius: 12px;
	padding: 0 12px;
	height: 40px;
	font-size: 15px;
	color: var(--zy-title, #233d34);
}

/* ── 底部免责声明 ── */
.cp-disclaimer {
	flex-shrink: 0;
	padding: 8rpx 0;
	text-align: center;
	background-color: var(--zy-bg, #ffffff);
	padding-bottom: calc(8rpx + constant(safe-area-inset-bottom));
	padding-bottom: calc(8rpx + env(safe-area-inset-bottom));
}

.cp-disclaimer-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
}
</style>
