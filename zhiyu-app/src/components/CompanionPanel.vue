<template>
	<view class="companion-card growth-card" data-companion-panel>
		<view class="companion-head">
			<view class="companion-head-copy">
				<text class="companion-eyebrow">A SMALL COMPANION</text>
				<text class="growth-card-title">和小织说一句</text>
			</view>
			<text v-if="stageLabel" class="companion-stage">{{ stageLabel }}</text>
		</view>
		<text class="growth-muted companion-intro">
			{{ patternName ? '正在陪你完成「' + patternName + '」。' : '' }}小织只在这个练习里提供陪伴、练习建议和已审核来源；不做诊断，也不替你做决定。
		</text>

		<view class="companion-thread" role="log" aria-live="polite" aria-label="和小织的对话">
			<view
				v-for="(message, index) in messages"
				:key="index"
				:class="['companion-message', message.role]"
			>
				<text class="companion-message-meta">{{ messageMeta(message) }}</text>
				<text class="companion-message-text">{{ message.text }}</text>
				<text v-if="message.support" class="companion-support">
					如果此刻很难受，可以联系学校心理中心或心理援助热线。本系统不能替代专业心理咨询。
				</text>
			</view>
			<text v-if="busy" class="companion-thinking" role="status">小织正在整理回应…</text>
		</view>

		<view v-for="tool in toolCards" :key="tool.key" class="companion-tool">
			<text class="companion-tool-title">{{ tool.title }}</text>
			<text v-for="line in tool.lines" :key="line" class="companion-tool-line">{{ line }}</text>
			<text v-if="tool.note" class="companion-tool-note">{{ tool.note }}</text>
		</view>

		<view v-if="citations.length" class="companion-citations">
			<text class="companion-tool-title">可核验来源</text>
			<text v-for="source in citations" :key="source.id" class="companion-citation">
				{{ source.title }} · {{ source.organization || '已登记来源' }}
			</text>
		</view>

		<text v-if="error" class="companion-error" role="alert">{{ error }}</text>

		<view class="companion-input-row">
			<textarea
				v-model="draft"
				class="companion-input"
				maxlength="500"
				auto-height
				:disabled="busy"
				placeholder="例如：我有点静不下来，想找一件十分钟的小事"
				aria-label="想和小织说的话，最多 500 字"
			/>
			<AccessibleButton
				class="growth-button companion-send"
				:disabled="busy || !draft.trim()"
				:aria-busy="busy ? 'true' : 'false'"
				@click="send"
			>{{ busy ? '……' : '发送' }}</AccessibleButton>
		</view>
		<text class="companion-foot">本轮文字只用于当前练习；AI 审计加密保存，不向其他同学展示。生成失败时会使用本地引导。</text>
	</view>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { API_BASE_URL, TOKEN_KEY } from '../utils/config'
import { startSSE } from '../utils/sse'
import AccessibleButton from './AccessibleButton.vue'

const props = defineProps({
	session: { type: Object, default: null },
	patternName: { type: String, default: '' },
})

const messages = ref([
	{
		role: 'assistant',
		greeting: true,
		text: '我是小织。你可以告诉我此刻的感觉，或者想找一件多长的手作；我会只在这个练习里陪你。',
	},
])
const draft = ref('')
const busy = ref(false)
const error = ref('')
const stageLabel = ref('')
const toolCards = ref([])
const citations = ref([])
let stream = null
let watchdog = null
let toolKey = 0

function messageMeta(message) {
	if (message.role === 'user') return '我'
	if (message.greeting) return '小织'
	return message.generated ? '小织' : '本地引导'
}

function clearWatchdog() {
	if (watchdog) {
		clearTimeout(watchdog)
		watchdog = null
	}
}

function addTool(data) {
	if (!data || !data.name) return
	const result = data.result || {}
	if (data.name === 'recommendPractice') {
		const items = Array.isArray(result.items) ? result.items : []
		toolCards.value.push({
			key: 'tool-' + toolKey++,
			title: items.length ? '小织找到的练习' : '练习推荐',
			lines: items.length
				? items.map(item => `${item.name} · ${item.traditionName} · 约 ${item.minutes} 分钟 · ${item.level}`)
				: ['暂时没有可用的练习，可以继续手动配色。'],
			note: result.reason || result.note || '',
		})
		return
	}
	if (data.name === 'searchHeritage') {
		const hits = Array.isArray(result.hits) ? result.hits : []
		toolCards.value.push({
			key: 'tool-' + toolKey++,
			title: '非遗资料检索',
			lines: hits.slice(0, 4).map(hit => `${hit.title} · ${hit.relationPath || hit.nodeType || '已审核条目'}`),
		})
		return
	}
	if (data.name === 'suggestBreathing') {
		toolCards.value.push({
			key: 'tool-' + toolKey++,
			title: '呼吸引导',
			lines: [result.instruction || '吸气 4 秒，呼气 6 秒，做 4 轮；不舒服就停下来。'],
		})
	}
}

function handleEvent(event, data) {
	if (event === 'stage') {
		stageLabel.value = (data && data.label) || ''
		return
	}
	if (event === 'message') {
		messages.value.push({
			role: 'assistant',
			text: (data && data.delta) || '',
			generated: data && data.generated === true,
		})
		return
	}
	if (event === 'tool') {
		addTool(data)
		return
	}
	if (event === 'citation' && data && data.id) {
		citations.value.push(data)
	}
}

function send() {
	const text = draft.value.trim()
	if (!text || busy.value || !props.session) return
	messages.value.push({ role: 'user', text })
	draft.value = ''
	busy.value = true
	error.value = ''
	stageLabel.value = ''
	toolCards.value = []
	citations.value = []
	clearWatchdog()
	watchdog = setTimeout(() => {
		if (!busy.value) return
		busy.value = false
		error.value = '小织这次回应超时了，可以再试一次，或继续手动练习。'
		if (stream) stream.abort()
	}, 45000)

	const token = uni.getStorageSync(TOKEN_KEY) || ''
	stream = startSSE({
		url: `${API_BASE_URL}/api/companion/sessions/${encodeURIComponent(props.session.practiceSessionId)}/message`,
		body: { content: text },
		token,
		onEvent: handleEvent,
		onDone(data) {
			clearWatchdog()
			busy.value = false
			if (data && data.blocked) {
				const last = [...messages.value].reverse().find(message => message.role === 'assistant')
				if (last) last.support = true
			}
		},
		onError(err) {
			clearWatchdog()
			busy.value = false
			error.value = (err && err.message) || '小织暂时没有连上，可以继续手动练习。'
		},
	})
}

onBeforeUnmount(() => {
	clearWatchdog()
	if (stream) stream.abort()
})
</script>

<style scoped>
.companion-card {
	margin-top: 20rpx;
}
.companion-head {
	display: flex;
	flex-wrap: wrap;
	align-items: baseline;
	justify-content: space-between;
	gap: 10rpx 18rpx;
}
.companion-head-copy {
	min-width: 0;
}
.companion-eyebrow {
	display: block;
	font-size: 19rpx;
	letter-spacing: 3rpx;
	color: var(--zy-brand-700, #217e76);
}
.companion-stage {
	flex-shrink: 0;
	padding: 4rpx 12rpx;
	border-radius: 999rpx;
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, #217e76);
	font-size: 21rpx;
}
.companion-intro {
	display: block;
	margin: 8rpx 0 16rpx;
	line-height: 1.7;
}
.companion-thread {
	max-height: 360px;
	overflow-y: auto;
	padding: 4rpx 0;
}
.companion-message {
	display: flex;
	flex-direction: column;
	gap: 4rpx;
	max-width: 92%;
	margin: 0 0 14rpx;
	padding: 12rpx 16rpx;
	border-radius: 16rpx;
	background: var(--zy-bg, #f5f7f2);
}
.companion-message.user {
	align-self: flex-end;
	margin-left: auto;
	background: var(--zy-bg-tint-mint, #e8efe4);
}
.companion-message-meta {
	font-size: 19rpx;
	color: var(--zy-brand-700, #217e76);
}
.companion-message-text {
	font-size: 24rpx;
	line-height: 1.7;
	color: var(--zy-title, #233d34);
	overflow-wrap: anywhere;
}
.companion-support {
	display: block;
	margin-top: 6rpx;
	padding-top: 8rpx;
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
	font-size: 21rpx;
	line-height: 1.7;
	color: var(--zy-accent, #8a6d1f);
}
.companion-thinking {
	display: block;
	margin: 6rpx 0 10rpx;
	font-size: 22rpx;
	color: var(--zy-text, #4a5f54);
}
.companion-tool {
	margin-top: 12rpx;
	padding: 12rpx 16rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 14rpx;
	background: var(--zy-bg, #f5f7f2);
}
.companion-tool-title {
	display: block;
	font-size: 22rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}
.companion-tool-line {
	display: block;
	margin-top: 6rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: var(--zy-text, #4a5f54);
	overflow-wrap: anywhere;
}
.companion-tool-note {
	display: block;
	margin-top: 10rpx;
	padding-top: 8rpx;
	border-top: 1rpx solid var(--zy-border, #dfe7dc);
	font-size: 21rpx;
	line-height: 1.7;
	color: var(--zy-content, #4a5f54);
}
.companion-citations {
	margin-top: 12rpx;
	padding: 12rpx 16rpx;
	border-radius: 14rpx;
	background: var(--zy-bg, #f5f7f2);
}
.companion-citation {
	display: block;
	margin-top: 4rpx;
	font-size: 21rpx;
	line-height: 1.6;
	color: var(--zy-text, #4a5f54);
	overflow-wrap: anywhere;
}
.companion-error {
	display: block;
	margin-top: 12rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: var(--zy-care, #a64842);
}
.companion-input-row {
	display: flex;
	flex-wrap: wrap;
	align-items: flex-end;
	gap: 12rpx;
	margin-top: 16rpx;
}
.companion-input {
	flex: 1 1 420rpx;
	box-sizing: border-box;
	width: 100%;
	min-height: 80rpx;
	max-height: 200rpx;
	padding: 16rpx 18rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 14rpx;
	background: var(--zy-bg, #f5f7f2);
	color: var(--zy-title, #233d34);
	font-size: 24rpx;
	line-height: 1.6;
}
.companion-send {
	flex: 0 0 auto;
	min-width: 140rpx;
	min-height: 44px;
	margin: 0;
}
.companion-send.is-disabled {
	opacity: 0.55;
}
.companion-foot {
	display: block;
	margin-top: 12rpx;
	font-size: 20rpx;
	line-height: 1.7;
	color: var(--zy-text, #4a5f54);
}
@media (max-width: 420px) {
	.companion-message {
		max-width: 100%;
	}
	.companion-send {
		width: 100%;
	}
}
</style>
