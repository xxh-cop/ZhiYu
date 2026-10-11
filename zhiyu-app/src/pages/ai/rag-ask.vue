<template>
	<view class="page">
		<!-- 顶部说明 -->
		<view class="header-card zy-slide-up">
			<view class="header-icon">
				<wd-icon name="chat" size="40rpx" color="#FFFFFF" />
			</view>
			<view class="header-text">
				<text class="header-title">非遗问答</text>
				<text class="header-desc">了解非遗文化，发现手作之美</text>
			</view>
		</view>

		<!-- 对话区域 -->
		<scroll-view class="chat-area" scroll-y :scroll-top="scrollTop" :scroll-with-animation="true">
			<!-- 欢迎语 -->
			<view v-if="messages.length === 0" class="welcome-area zy-slide-up">
				<text class="welcome-title">你好呀，想了解哪个非遗项目呢？</text>
				<view class="suggestion-tags">
					<view
						v-for="tag in suggestions"
						:key="tag"
						class="suggestion-tag"
						@click="quickAsk(tag)"
					>
						<text class="tag-text">{{ tag }}</text>
					</view>
				</view>
			</view>

			<!-- 消息列表 -->
			<view
				v-for="(msg, idx) in messages"
				:key="idx"
				class="msg-item zy-slide-up"
			>
				<!-- 用户消息 -->
				<view v-if="msg.role === 'user'" class="msg-user">
					<view class="msg-bubble-user">
						<text class="msg-text-user">{{ msg.content }}</text>
					</view>
				</view>

				<!-- AI 消息 -->
				<view v-else class="msg-ai">
					<view class="msg-bubble-ai">
						<text class="msg-text-ai">{{ msg.content }}</text>
						<!-- 引用来源 -->
						<view v-if="msg.sources && msg.sources.length > 0" class="sources-area">
							<view class="sources-header" @click="toggleSources(idx)">
								<text class="sources-title">引用来源（{{ msg.sources.length }}）</text>
								<wd-icon :name="msg.showSources ? 'arrow-up' : 'arrow-down'" size="24rpx" color="#9CA3AF" />
							</view>
							<view v-if="msg.showSources" class="sources-list">
								<view
									v-for="(src, sIdx) in msg.sources"
									:key="sIdx"
									class="source-item"
								>
									<text class="source-title">{{ src.title }}</text>
									<text class="source-excerpt">{{ src.excerpt }}...</text>
								</view>
							</view>
						</view>
					</view>
				</view>
			</view>

			<!-- 加载中 -->
			<view v-if="loading" class="msg-ai">
				<view class="msg-bubble-ai">
					<view class="loading-dots">
						<view class="dot dot1"></view>
						<view class="dot dot2"></view>
						<view class="dot dot3"></view>
					</view>
				</view>
			</view>
		</scroll-view>

		<!-- 底部输入 -->
		<view class="input-bar">
			<view class="input-wrap">
				<input
					v-model="inputText"
					class="input-field"
					placeholder="问问赣绣、剪纸、扎染..."
					:disabled="loading"
					confirm-type="send"
					@confirm="handleAsk"
				/>
			</view>
			<view
				class="send-btn"
				:class="{ 'send-btn-disabled': !inputText.trim() || loading }"
				@click="handleAsk"
			>
				<wd-icon name="arrow-up" size="36rpx" color="#FFFFFF" />
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { request } from '../../utils/request'

const inputText = ref('')
const loading = ref(false)
const messages = ref([])
const scrollTop = ref(0)

const suggestions = [
	'赣绣有什么历史渊源？',
	'扎染的制作流程是什么？',
	'陶艺有什么疗愈价值？',
	'剪纸适合初学者吗？',
]

function quickAsk(text) {
	inputText.value = text
	handleAsk()
}

async function handleAsk() {
	const question = inputText.value.trim()
	if (!question || loading.value) return

	// 添加用户消息
	messages.value.push({ role: 'user', content: question })
	inputText.value = ''
	loading.value = true

	await scrollToBottom()

	try {
		const data = await request({
			url: '/api/ai/rag/ask',
			method: 'POST',
			data: { question },
		})

		messages.value.push({
			role: 'ai',
			content: data.answer || '抱歉，我暂时无法回答这个问题。',
			sources: data.sources || [],
			showSources: false,
		})
	} catch (e) {
		messages.value.push({
			role: 'ai',
			content: '非遗问答服务暂时不可用，请稍后再试。',
			sources: [],
			showSources: false,
		})
	} finally {
		loading.value = false
		await scrollToBottom()
	}
}

function toggleSources(idx) {
	messages.value[idx].showSources = !messages.value[idx].showSources
}

async function scrollToBottom() {
	await nextTick()
	scrollTop.value = 99999
}
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: var(--zy-bg, #F8F9FB);
}

/* ── 顶部说明 ── */
.header-card {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 24rpx 32rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
	border-bottom: 1rpx solid var(--zy-border, #E5E7EB);
}

.header-icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.header-text {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
}

.header-title {
	font-size: 32rpx;
	font-weight: 600;
	color: var(--zy-title, #1F2937);
}

.header-desc {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 对话区域 ── */
.chat-area {
	flex: 1;
	padding: 24rpx;
	overflow-y: auto;
}

/* ── 欢迎语 ── */
.welcome-area {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 32rpx;
	padding: 80rpx 32rpx;
}

.welcome-title {
	font-size: 30rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}

.suggestion-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 16rpx;
	justify-content: center;
}

.suggestion-tag {
	padding: 16rpx 28rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	transition: all 200ms ease;
}

.suggestion-tag:active {
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	border-color: var(--zy-primary, #2D9B8F);
}

.tag-text {
	font-size: 26rpx;
	color: var(--zy-primary, #2D9B8F);
}

/* ── 消息项 ── */
.msg-item {
	margin-bottom: 24rpx;
}

.msg-user {
	display: flex;
	justify-content: flex-end;
}

.msg-bubble-user {
	max-width: 75%;
	padding: 20rpx 28rpx;
	border-radius: 32rpx 32rpx 8rpx 32rpx;
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
}

.msg-text-user {
	font-size: 28rpx;
	color: #FFFFFF;
	line-height: 1.6;
}

.msg-ai {
	display: flex;
	justify-content: flex-start;
}

.msg-bubble-ai {
	max-width: 85%;
	padding: 24rpx 28rpx;
	border-radius: 32rpx 32rpx 32rpx 8rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
}

.msg-text-ai {
	font-size: 28rpx;
	color: var(--zy-title, #1F2937);
	line-height: 1.7;
}

/* ── 引用来源 ── */
.sources-area {
	margin-top: 20rpx;
	padding-top: 16rpx;
	border-top: 1rpx solid var(--zy-border, #E5E7EB);
}

.sources-header {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.sources-title {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.sources-list {
	margin-top: 12rpx;
	display: flex;
	flex-direction: column;
	gap: 12rpx;
}

.source-item {
	padding: 16rpx 20rpx;
	border-radius: var(--zy-radius-md, 16rpx);
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
}

.source-title {
	font-size: 24rpx;
	font-weight: 600;
	color: var(--zy-primary, #2D9B8F);
}

.source-excerpt {
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	margin-top: 4rpx;
}

/* ── 加载动画 ── */
.loading-dots {
	display: flex;
	gap: 8rpx;
	align-items: center;
	padding: 8rpx 0;
}

.dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background-color: var(--zy-primary-light, #5BC4A8);
	animation: dotBounce 1.4s infinite ease-in-out both;
}

.dot1 { animation-delay: -0.32s; }
.dot2 { animation-delay: -0.16s; }

@keyframes dotBounce {
	0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
	40% { transform: scale(1); opacity: 1; }
}

/* ── 底部输入 ── */
.input-bar {
	display: flex;
	align-items: center;
	gap: 16rpx;
	padding: 16rpx 24rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
	border-top: 1rpx solid var(--zy-border, #E5E7EB);
}

.input-wrap {
	flex: 1;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #F8F9FB);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	display: flex;
	align-items: center;
	padding: 0 28rpx;
}

.input-field {
	flex: 1;
	font-size: 28rpx;
	color: var(--zy-title, #1F2937);
}

.send-btn {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, var(--zy-primary, #2D9B8F), var(--zy-primary-light, #5BC4A8));
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	transition: opacity 200ms ease;
}

.send-btn-disabled {
	opacity: 0.4;
}

.footnote {
	display: block;
	text-align: center;
	font-size: 20rpx;
	color: var(--zy-aid, #9CA3AF);
	padding: 8rpx 0 12rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
}
</style>
