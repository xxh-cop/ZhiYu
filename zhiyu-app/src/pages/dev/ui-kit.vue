<template>
	<!--
		组件样张页（开发用，暖纸青玉 v6 · 规格 §2）。
		覆盖：AccessibleButton 5 变体 × 3 尺寸 × 状态；卡片族；缝线；SectionHead；
		MoodFace；StatStrip；ChatBubble；ChatInputBar。
		双端可看：H5 /pages/dev/ui-kit，小程序同名路径。
	-->
	<view class="kit-page" :class="zyThemeClass">
		<text class="kit-h1">暖纸青玉 · 组件样张</text>

		<!-- C1 AccessibleButton -->
		<text class="kit-h2">C1 AccessibleButton · 变体</text>
		<view class="kit-col">
			<AccessibleButton variant="primary" size="md">primary 主按钮</AccessibleButton>
			<AccessibleButton variant="secondary" size="md">secondary 次按钮</AccessibleButton>
			<AccessibleButton variant="ghost" size="md">ghost 文字按钮</AccessibleButton>
			<AccessibleButton variant="danger" size="md">danger 危险操作</AccessibleButton>
			<AccessibleButton variant="care" size="md">care 危机关怀</AccessibleButton>
		</view>

		<text class="kit-h2">C1 尺寸档（primary）</text>
		<view class="kit-row">
			<AccessibleButton variant="primary" size="lg">lg 96</AccessibleButton>
			<AccessibleButton variant="primary" size="md">md 88</AccessibleButton>
			<AccessibleButton variant="primary" size="sm">sm 64</AccessibleButton>
			<AccessibleButton variant="primary" size="icon" aria-label="图标按钮示例">↑</AccessibleButton>
		</view>

		<text class="kit-h2">C1 状态（primary）</text>
		<view class="kit-col">
			<AccessibleButton variant="primary" size="md">默认</AccessibleButton>
			<AccessibleButton variant="primary" size="md" class="zy-focused">聚焦（zy-focused 静态演示）</AccessibleButton>
			<AccessibleButton variant="primary" size="md" disabled>禁用</AccessibleButton>
			<AccessibleButton variant="primary" size="md" loading>加载中</AccessibleButton>
		</view>

		<!-- C2 卡片族 -->
		<text class="kit-h2">C2 卡片族</text>
		<view class="zy-card"><text class="kit-card-t">zy-card 默认（宣纸 + 暖墨影）</text></view>
		<view class="zy-card zy-card--mint"><text class="kit-card-t">zy-card--mint（平静语义）</text></view>
		<view class="zy-card zy-card--warm"><text class="kit-card-t">zy-card--warm（非遗/关怀语义）</text></view>
		<view class="zy-card zy-card--feature kit-feature"><text class="kit-card-t kit-feature-t">zy-card--feature（展品大卡，圆角 40rpx）</text></view>

		<!-- C4 缝线 -->
		<text class="kit-h2">C4 缝线分隔</text>
		<StitchDivider />
		<view class="kit-row kit-stitch-row">
			<text class="kit-note">竖缝线 →</text>
			<StitchDivider vertical />
			<text class="kit-note">between</text>
			<StitchDivider vertical />
			<text class="kit-note">items</text>
		</view>

		<!-- C5 SectionHead -->
		<text class="kit-h2">C5 SectionHead</text>
		<SectionHead eyebrow="WHAT NOW" title="区块标题 · plain" />
		<SectionHead eyebrow="HERITAGE" title="区块标题 · heritage" tone="heritage">
			<AccessibleButton variant="ghost" size="sm">换一批 ↻</AccessibleButton>
		</SectionHead>

		<!-- C6 MoodFace -->
		<text class="kit-h2">C6 MoodFace（平静为选中态）</text>
		<view class="kit-row kit-moods">
			<MoodFace v-for="(m, i) in moods" :key="m" :kind="i" :label="m" :selected="moodIndex === i" @select="moodIndex = moodIndex === i ? -1 : i" />
		</view>

		<!-- C7 StatStrip -->
		<text class="kit-h2">C7 StatStrip</text>
		<StitchDivider />
		<StatStrip :items="stats" aria-label="我的创作旅程" />
		<StitchDivider />

		<!-- C3 ChatBubble -->
		<text class="kit-h2">C3 ChatBubble</text>
		<ChatBubble role="ai" time="14:02">下午好呀。今天过得怎么样？想从哪里开始聊呢。</ChatBubble>
		<ChatBubble role="user" time="14:03" user-initial="同">最近作业有点多，感觉静不下心来。</ChatBubble>
		<ChatBubble role="ai" typing />

		<!-- C8 ChatInputBar -->
		<text class="kit-h2">C8 ChatInputBar</text>
		<ChatInputBar v-model="draft" @send="onSend" @voice="onVoice" />
		<text class="kit-note">输入：{{ draft || '（空）' }}；发送禁用随内容联动；语音态：{{ voiceState || 'idle' }}</text>

		<view class="kit-foot"><text>本页为开发样张，不接入业务流程。</text></view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import StitchDivider from '../../components/StitchDivider.vue'
import SectionHead from '../../components/SectionHead.vue'
import MoodFace from '../../components/MoodFace.vue'
import StatStrip from '../../components/StatStrip.vue'
import ChatBubble from '../../components/ChatBubble.vue'
import ChatInputBar from '../../components/ChatInputBar.vue'

const moods = ['开心', '平静', '一般', '有点累', '不太好']
const moodIndex = ref(1)
const stats = [
	{ value: 12, label: '连续创作 · 天' },
	{ value: 8, label: '我的作品' },
	{ value: 186, label: '专注 · 分钟' },
]
const draft = ref('')
const voiceState = ref('')
function onSend() { draft.value = '' }
function onVoice() { voiceState.value = voiceState.value === 'recording' ? '' : 'recording' }
</script>

<style scoped>
.kit-page { min-height: 100vh; padding: 40rpx 32rpx 96rpx; background: var(--zy-bg, #ffffff); }
.kit-h1 { display: block; font-size: var(--zy-text-title-lg, 40rpx); font-weight: 600; color: var(--zy-title, #33302a); margin-bottom: 16rpx; }
.kit-h2 { display: block; font-size: var(--zy-text-caption, 24rpx); letter-spacing: 2rpx; color: var(--zy-text, #5c5648); margin: 48rpx 0 20rpx; }
.kit-col { display: flex; flex-direction: column; gap: 20rpx; align-items: flex-start; }
.kit-row { display: flex; align-items: center; gap: 20rpx; flex-wrap: wrap; }
.kit-card-t { font-size: var(--zy-text-body, 28rpx); color: var(--zy-title, #33302a); }
.zy-card { margin-bottom: 20rpx; }
.kit-feature { padding: 32rpx; background: var(--zy-card-bg, #ffffff); box-shadow: var(--zy-shadow-card); }
.kit-stitch-row { height: 60rpx; }
.kit-note { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); }
.kit-moods { justify-content: space-between; }
.kit-foot { margin-top: 64rpx; text-align: center; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); }
</style>
