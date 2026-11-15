<template>
	<view :class="zyThemeClass" class="page">
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

		<!-- 关系图谱 -->
		<view class="map-card">
			<AccessibleButton
				class="map-toggle"
				:aria-expanded="mapExpanded ? 'true' : 'false'"
				aria-controls="heritage-map-panel"
				@click="toggleMap"
			>
				<view>
					<text class="map-title">非遗关系图谱</text>
					<text class="map-desc">{{ graph ? '4 门工艺 · 24 个纹样 · 4 条已登记来源' : '正在读取目录…' }}</text>
				</view>
				<text class="map-action">{{ mapExpanded ? '收起' : '展开' }}</text>
			</AccessibleButton>
			<scroll-view v-if="mapExpanded" id="heritage-map-panel" class="map-panel" scroll-y>
				<view v-if="mapError" class="map-error" role="alert">
					<text>{{ mapError }}</text>
					<AccessibleButton class="map-retry" @click="loadGraph">重试</AccessibleButton>
				</view>
				<HeritageKnowledgeMap v-else-if="graph" :graph="graph" />
			</scroll-view>
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
						role="button"
						tabindex="0"
						@click="quickAsk(tag)"
						@keydown.enter.prevent="quickAsk(tag)"
						@keydown.space.prevent="quickAsk(tag)"
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
						<view class="answer-meta">
							<text class="mode-chip">{{ modeLabel(msg.retrievalMode) }}</text>
							<text v-if="msg.generated === false" class="mode-note">结构化资料 · 未调用生成模型</text>
						</view>
						<!-- 引用来源 -->
						<view v-if="msg.sources && msg.sources.length > 0" class="sources-area">
							<view class="sources-header" role="button" tabindex="0" @click="toggleSources(idx)" @keydown.enter.prevent="toggleSources(idx)" @keydown.space.prevent="toggleSources(idx)">
								<text class="sources-title">引用来源（{{ msg.sources.length }}）</text>
								<wd-icon :name="msg.showSources ? 'arrow-up' : 'arrow-down'" size="24rpx" color="#4a5f54" />
							</view>
							<view v-if="msg.showSources" class="sources-list">
								<view
									v-for="(src, sIdx) in msg.sources"
									:key="src.id || sIdx"
									class="source-item"
								>
									<view class="source-head">
										<text class="source-title">{{ src.title }}</text>
										<text class="source-badge">{{ src.citationType === 'VERIFIED_SOURCE' ? '已登记来源' : '检索参考' }}</text>
									</view>
									<text v-if="src.organization" class="source-org">{{ src.organization }}</text>
									<text class="source-excerpt">{{ src.excerpt }}</text>
									<text v-if="src.relation" class="source-relation">{{ src.relation }}</text>
									<text v-if="src.sourceVersion" class="source-version">资料版本 {{ src.sourceVersion }}</text>
									<AccessibleButton
										v-if="src.url"
										class="source-link zy-link"
										role="link"
										:aria-label="'打开来源 ' + src.title"
										@click="openSource(src)"
									><text>打开已登记来源</text><ZyIcon name="external" :size="24" color="#1A645E" /></AccessibleButton>
									<text v-if="src.mediaReuse === 'NOT_GRANTED'" class="source-media">仅作背景参考，媒体不转载</text>
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
					placeholder="问问夏布绣、竹编、剪纸..."
					:disabled="loading"
					confirm-type="send"
					@confirm="handleAsk"
				/>
			</view>
			<view
				class="send-btn"
				:class="{ 'send-btn-disabled': !inputText.trim() || loading }"
				role="button"
				:tabindex="!inputText.trim() || loading ? -1 : 0"
				aria-label="发送"
				@click="handleAsk"
				@keydown.enter.prevent="handleAsk"
				@keydown.space.prevent="handleAsk"
			>
				<wd-icon name="arrow-up" size="36rpx" color="#FFFFFF" />
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
		<Toast />
	</view>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { request } from '../../utils/request'
import HeritageKnowledgeMap from '../../components/HeritageKnowledgeMap.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import { useToast } from '../../composables/useToast'
import { buildHeritageGraph } from '../../utils/heritage-graph-layout'

const toast = useToast()

const inputText = ref('')
const loading = ref(false)
const messages = ref([])
const scrollTop = ref(0)
const graph = ref(null)
const mapExpanded = ref(false)
const mapError = ref('')

const MODE_LABELS = {
	GRAPH_VECTOR: '关系 + 向量检索',
	STRUCTURED_GRAPH: '结构化关系检索',
	VECTOR: '向量检索',
	NONE: '未找到资料',
}

function modeLabel(mode) {
	return MODE_LABELS[mode] || '检索结果'
}

async function loadGraph() {
	mapError.value = ''
	try {
		const catalog = await request({ url: '/api/heritage/catalog' })
		graph.value = buildHeritageGraph(catalog)
	} catch (e) {
		mapError.value = (e && e.message) || '关系图谱暂时不可用'
	}
}

function toggleMap() {
	mapExpanded.value = !mapExpanded.value
}

function openSource(source) {
	if (!source || !source.url || !/^https:\/\//.test(source.url)) return
	// #ifdef H5
	window.open(source.url, '_blank', 'noopener,noreferrer')
	// #endif
	// #ifndef H5
	uni.setClipboardData({
		data: source.url,
		success: () => toast.show('来源链接已复制，请在浏览器打开'),
	})
	// #endif
}

const suggestions = [
	'夏布绣的材料有哪些？',
	'瑞昌竹编有什么特点？',
	'剪纸的方窗纹样怎么观察？',
	'景德镇手工制瓷的工序是什么？',
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
			retrievalMode: data.retrievalMode || 'NONE',
			generated: data.generated === true,
			showSources: false,
		})
	} catch (e) {
		messages.value.push({
			role: 'ai',
			content: '非遗问答服务暂时不可用，请稍后再试。',
			sources: [],
			retrievalMode: 'NONE',
			generated: false,
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

onMounted(loadGraph)
</script>

<style scoped>
.page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background-color: var(--zy-bg, #ffffff);
}

/* ── 顶部说明 ── */
.header-card {
	display: flex;
	align-items: center;
	gap: 20rpx;
	padding: 24rpx 32rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
	border-bottom: 1rpx solid var(--zy-border, #dfe7dc);
}

.header-icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, var(--zy-brand-600, #217e76)), var(--zy-brand-300, var(--zy-brand-300, #5bc4a8)));
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
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 600;
	color: var(--zy-title, #233d34);
}

.header-desc {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
}

/* ── 对话区域 ── */
.chat-area {
	flex: 1;
	box-sizing: border-box;
	width: 100%;
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
	font-size:var(--zy-text-body-lg, 30rpx);
	color: var(--zy-text, #4a5f54);
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
	border: 1rpx solid var(--zy-border, #dfe7dc);
	transition: all 200ms ease;
}

.suggestion-tag:active {
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	border-color: var(--zy-primary, var(--zy-brand-600, #217e76));
}

.tag-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
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
	background: var(--zy-brand-600, var(--zy-brand-600, #217e76));
}

.msg-text-user {
	font-size:var(--zy-text-body, 28rpx);
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
	border: 1rpx solid var(--zy-border, #dfe7dc);
}

.msg-text-ai {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-title, #233d34);
	line-height: 1.7;
}

/* ── 引用来源 ── */
.sources-area {
	margin-top: 20rpx;
	padding-top: 16rpx;
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

.sources-header {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.sources-title {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
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
	background-color: var(--zy-bg-tint-mint, #e8efe4);
}

.source-title {
	font-size:var(--zy-text-caption, 24rpx);
	font-weight: 600;
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
}

.source-excerpt {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #4a5f54);
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
	background-color: var(--zy-brand-300, var(--zy-brand-300, #5bc4a8));
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
	box-sizing: border-box;
	gap: 16rpx;
	padding: 16rpx 24rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
}

.input-wrap {
	flex: 1;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #ffffff);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	display: flex;
	align-items: center;
	padding: 0 28rpx;
}

.input-field {
	flex: 1;
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-title, #233d34);
}

.send-btn {
	width: 72rpx;
	height: 72rpx;
	border-radius: 50%;
	background: linear-gradient(135deg, var(--zy-primary, var(--zy-brand-600, #217e76)), var(--zy-brand-300, var(--zy-brand-300, #5bc4a8)));
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
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #4a5f54);
	padding: 8rpx 0 12rpx;
	background-color: var(--zy-card-bg, #FFFFFF);
}

.map-card {
	box-sizing: border-box;
	margin: 12rpx 24rpx 0;
	overflow: hidden;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 16rpx);
	background: var(--zy-card-bg, #ffffff);
}
.map-toggle {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	width: 100%;
	padding: 18rpx 20rpx;
	text-align: left;
	background: transparent;
	border: 0;
}
.map-title { display: block; font-size:var(--zy-text-caption, 24rpx); font-weight: 600; color: var(--zy-title, #233d34); }
.map-desc { display: block; margin-top: 4rpx; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-text, #4a5f54); }
.map-action { flex-shrink: 0; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); }
.map-panel { box-sizing: border-box; width: 100%; height: 52vh; padding: 0 16rpx 16rpx; }
.map-error { display: flex; flex-direction: column; align-items: flex-start; gap: 12rpx; padding: 20rpx; font-size:var(--zy-text-caption, 24rpx); color: #9b4b4b; }
.map-retry { padding: 8rpx 18rpx; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); background: var(--zy-bg-tint-mint, #e8efe4); border: 0; border-radius: 999rpx; }
.answer-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx; margin-top: 12rpx; }
.mode-chip { padding: 2rpx 10rpx; border-radius: 8rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); font-size:var(--zy-text-micro, 22rpx); }
.mode-note { font-size:var(--zy-text-micro, 22rpx); color: var(--zy-text, #4a5f54); }
.source-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12rpx; }
.source-badge { flex-shrink: 0; padding: 2rpx 8rpx; border-radius: 6rpx; background: var(--zy-bg, #ffffff); color: var(--zy-text, #4a5f54); font-size:var(--zy-text-micro, 22rpx); }
.source-org, .source-relation, .source-version, .source-media { display: block; margin-top: 4rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-text, #4a5f54); word-break: break-word; }
.source-link { display: inline-block; margin-top: 8rpx; padding: 0; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-brand-700, var(--zy-brand-600, #217e76)); background: transparent; border: 0; text-align: left; }

@media (min-width: 900px) {
	.map-card,
	.chat-area {
		width: 100%;
		max-width: 980px;
		margin-left: auto;
		margin-right: auto;
	}
	.msg-bubble-ai {
		max-width: 760px;
	}
}

button:focus-visible {
	outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e));
	outline-offset: 3px;
}
</style>
