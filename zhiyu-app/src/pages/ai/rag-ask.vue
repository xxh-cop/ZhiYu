<template>
	<view :class="zyThemeClass" class="page">
		<!-- 顶部说明 -->
		<view class="header-card zy-slide-up">
			<view class="header-text">
				<text class="header-eyebrow">HERITAGE ATLAS · 非遗问答</text>
				<text class="header-title">问一件非遗的事</text>
				<text class="header-desc">材质、工序与纹样观察，都可以慢慢问。</text>
			</view>
			<view class="header-icon">
				<wd-icon name="chat" size="32rpx" color="#0A403B" />
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
				<text class="welcome-kicker">ASK A QUESTION · 从一条线索开始</text>
				<text class="welcome-title">你想先了解哪一件？</text>
				<text class="welcome-note">可以问材料、工序，也可以问纹样怎么看。</text>
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
						<text class="tag-mark">问</text>
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
				<wd-icon name="arrow-up" size="36rpx" :color="(!inputText.trim() || loading) ? '#6e7b75' : '#FFFFFF'" />
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
	background-color: var(--zy-bg, #f1f4f1);
}

/* ── 顶部说明 ── */
.header-card {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 20rpx;
	padding: 24rpx 32rpx 28rpx;
	background-color: transparent;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
}

.header-icon {
	width: 68rpx;
	height: 68rpx;
	border-radius: 0;
	background: transparent;
	border: 1rpx solid var(--zy-border, #d3ded8);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	margin-top: 4rpx;
}

.header-text {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
	flex: 1;
	min-width: 0;
}

.header-eyebrow {
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 2px;
	color: var(--zy-muted, #6e7b75);
}

.header-title {
	font-family: var(--zy-font-display, serif);
	font-size: 40rpx;
	font-weight: 500;
	line-height: 1.4;
	color: var(--zy-title, #111816);
}

.header-desc {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
	line-height: 1.7;
}

/* ── 对话区域 ── */
.chat-area {
	flex: 1;
	box-sizing: border-box;
	width: 100%;
	padding: 24rpx 32rpx;
	overflow-y: auto;
}

/* ── 欢迎语 ── */
.welcome-area {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 12rpx;
	padding: 32rpx 0 20rpx;
}

.welcome-kicker { font-size: var(--zy-text-micro, 22rpx); letter-spacing: 2px; color: var(--zy-muted, #6e7b75); }
.welcome-title { font-family: var(--zy-font-display, serif); font-size: 40rpx; font-weight: 500; line-height: 1.4; color: var(--zy-title, #111816); }
.welcome-note { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); line-height: 1.8; }

.suggestion-tags {
	display: flex;
	flex-direction: column;
	width: 100%;
	gap: 0;
	margin-top: 20rpx;
	border-top: 1rpx solid var(--zy-border, #d3ded8);
}

.suggestion-tag {
	display: flex;
	align-items: center;
	gap: 16rpx;
	width: 100%;
	box-sizing: border-box;
	padding: 20rpx 0;
	border-radius: 0;
	border: 0;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	background-color: transparent;
	transition: opacity 160ms ease;
	text-align: left;
}

.suggestion-tag:active {
	background-color: transparent;
	border-color: var(--zy-brand-600, #0e5b53);
	opacity: .75;
}

.tag-mark { flex-shrink: 0; width: 44rpx; height: 44rpx; line-height: 42rpx; text-align: center; border: 1rpx solid var(--zy-border, #d3ded8); font-size: 22rpx; color: var(--zy-brand-700, #0a403b); }
.tag-text {
	font-size: var(--zy-text-caption, 24rpx);
	color: var(--zy-title, #111816);
	line-height: 1.6;
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
	max-width: 78%;
	padding: 16rpx 22rpx;
	border-radius: 0;
	background: var(--zy-brand-600, #0e5b53);
}

.msg-text-user {
	font-size:var(--zy-text-body, 28rpx);
	color: #FFFFFF;
	line-height: 1.7;
}

.msg-ai {
	display: flex;
	justify-content: flex-start;
}

.msg-bubble-ai {
	max-width: 90%;
	padding: 8rpx 0 8rpx 20rpx;
	border-radius: 0;
	background-color: transparent;
	border: 0;
	border-left: 2rpx solid var(--zy-brand-600, #0e5b53);
}

.msg-text-ai {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-text, #42524c);
	line-height: 1.85;
}

/* ── 引用来源 ── */
.sources-area {
	margin-top: 18rpx;
	padding-top: 14rpx;
	border-top: 1rpx solid var(--zy-border, #d3ded8);
}

.sources-header {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.sources-title {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
}

.sources-list {
	margin-top: 12rpx;
	display: flex;
	flex-direction: column;
	gap: 0;
}

.source-item {
	padding: 14rpx 0;
	border-radius: 0;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	background-color: transparent;
}

.source-title {
	font-size:var(--zy-text-caption, 24rpx);
	font-weight: 500;
	color: var(--zy-brand-700, #0a403b);
}

.source-excerpt {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #42524c);
	margin-top: 4rpx;
	line-height: 1.8;
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
	padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
	background-color: var(--zy-bg, #f1f4f1);
	border-top: 1rpx solid var(--zy-border, #d3ded8);
}

.input-wrap {
	flex: 1;
	height: 76rpx;
	border-radius: 0;
	background-color: transparent;
	border: 0;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	display: flex;
	align-items: center;
	padding: 0 4rpx;
}

.input-field {
	flex: 1;
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-title, #111816);
}

.send-btn {
	width: 76rpx;
	height: 76rpx;
	border-radius: 0;
	background: var(--zy-brand-600, #0e5b53);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	transition: opacity 200ms ease;
}

.send-btn-disabled {
	opacity: 1;
	background: transparent;
	border: 1rpx solid var(--zy-border, #d3ded8);
}

.footnote {
	display: block;
	text-align: center;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-muted, #6e7b75);
	padding: 8rpx 0 12rpx;
	background-color: transparent;
}

.map-card {
	box-sizing: border-box;
	margin: 0 32rpx;
	overflow: hidden;
	border: 0;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	border-radius: 0;
	background: transparent;
}
.map-toggle {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	width: 100%;
	padding: 16rpx 0;
	text-align: left;
	background: transparent;
	border: 0;
}
.map-title { display: block; font-family:var(--zy-font-display,serif); font-size:28rpx; font-weight:500; color:var(--zy-title, #111816); }
.map-desc { display: block; margin-top: 4rpx; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); }
.map-action { flex-shrink: 0; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-brand-700, #0a403b); }
.map-panel { box-sizing: border-box; width: 100%; height: 52vh; padding: 0 0 16rpx; }
.map-error { display: flex; flex-direction: column; align-items: flex-start; gap: 12rpx; padding: 20rpx 0; font-size:var(--zy-text-caption, 24rpx); color: var(--zy-care,#a64842); }
.map-retry { padding: 8rpx 18rpx; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-brand-700,#0a403b); background: transparent; border: 1rpx solid var(--zy-border,#d3ded8); border-radius: 0; }
.answer-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx; margin-top: 12rpx; }
.mode-chip { padding: 2rpx 8rpx; border-radius: 0; border:1rpx solid var(--zy-border,#d3ded8); background: transparent; color: var(--zy-muted,#6e7b75); font-size:var(--zy-text-micro, 22rpx); }
.mode-note { font-size:var(--zy-text-micro, 22rpx); color: var(--zy-muted,#6e7b75); }
.source-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12rpx; }
.source-badge { flex-shrink: 0; padding: 2rpx 8rpx; border-radius: 0; background: transparent; border:1rpx solid var(--zy-border,#d3ded8); color: var(--zy-muted,#6e7b75); font-size:var(--zy-text-micro, 22rpx); }
.source-org, .source-relation, .source-version, .source-media { display: block; margin-top: 4rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-muted,#6e7b75); word-break: break-word; }
.source-link { display: inline-block; margin-top: 8rpx; padding: 0; font-size:var(--zy-text-micro, 22rpx); color: var(--zy-brand-700, #0a403b); background: transparent; border: 0; text-align: left; }

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
