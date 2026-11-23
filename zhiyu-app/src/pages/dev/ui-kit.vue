<template>
	<!--
		组件样张页（开发用，v7「一线」· P0 组件升级 2026-11-18）。
		覆盖：ZyIcon 38 图形矩阵；AccessibleButton 6 变体 × 5 尺寸 × 状态（tonal/状态层/spinner）；
		卡片族；缝线；SectionHead；MoodFace；StatStrip；ChatBubble；ChatInputBar。
		双端可看：H5 /pages/dev/ui-kit，小程序同名路径。
	-->
	<view class="kit-page" :class="zyThemeClass">
		<text class="kit-h1">一线 · 组件样张</text>
		<text class="kit-h2">小织 · 织结小精灵</text>
		<view class="kit-pet-stage"><XiaotuanPet :size="160" :face="petFace" /><text class="kit-note">{{ petStates.find(item => item.key === petFace).label }}</text></view>
		<view class="kit-row"><AccessibleButton v-for="state in petStates" :key="state.key" size="sm" :aria-pressed="petFace === state.key" @click="petFace = state.key">{{ state.label }}</AccessibleButton></view>
		<view class="kit-row kit-pet-thumbs"><view v-for="state in petStates" :key="state.key" class="kit-pet-sample"><XiaotuanPet :size="76" :face="state.key" quiet /><text class="kit-note">{{ state.label }}</text></view></view>
		<view class="kit-row"><XiaotuanPet :size="40" /><text class="kit-note">40px</text><XiaotuanPet :size="48" /><text class="kit-note">48px</text><AccessibleButton size="sm" :aria-pressed="reducedMotion" @click="toggleMotion">{{ reducedMotion ? '减少动态效果：已开启' : '减少动态效果：未开启' }}</AccessibleButton></view>

		<!-- C0 ZyIcon 图标矩阵 -->
		<text class="kit-h2">C0 ZyIcon · 38 图形（24 网格 / 1.5 描边 / 圆端点）</text>
		<view class="kit-icons">
			<view v-for="name in iconNames" :key="name" class="kit-icon-cell">
				<ZyIcon :name="name" :size="40" tone="text" />
				<text class="kit-note">{{ name }}</text>
			</view>
		</view>
		<text class="kit-h2">C0 色板 tone</text>
		<view class="kit-row">
			<view v-for="t in iconTones" :key="t" class="kit-icon-cell" :class="{ 'kit-tone-dark': t.startsWith('dark') || t === 'brand' || t === 'care' }">
				<ZyIcon name="heart" :size="40" :tone="t" />
				<text class="kit-note" :class="{ 'kit-note-dark': t.startsWith('dark') || t === 'brand' || t === 'care' }">{{ t }}</text>
			</view>
		</view>

		<!-- C1 AccessibleButton -->
		<text class="kit-h2">C1 AccessibleButton · 变体</text>
		<view class="kit-col">
			<AccessibleButton variant="primary" size="md">primary 主按钮</AccessibleButton>
			<AccessibleButton variant="tonal" size="md">tonal 次强调</AccessibleButton>
			<AccessibleButton variant="secondary" size="md">secondary 次按钮</AccessibleButton>
			<AccessibleButton variant="ghost" size="md">ghost 文字按钮</AccessibleButton>
			<AccessibleButton variant="danger" size="md">danger 危险操作</AccessibleButton>
			<AccessibleButton variant="care" size="md">care 危机关怀</AccessibleButton>
		</view>

		<text class="kit-h2">C1 尺寸档（primary；sm/mini 热区经 ::after 扩到 88rpx）</text>
		<view class="kit-row">
			<AccessibleButton variant="primary" size="lg">lg 96</AccessibleButton>
			<AccessibleButton variant="primary" size="md">md 88</AccessibleButton>
			<AccessibleButton variant="primary" size="sm">sm 64</AccessibleButton>
			<AccessibleButton variant="primary" size="mini">mini 56</AccessibleButton>
			<AccessibleButton variant="primary" size="icon" aria-label="图标按钮示例"><ZyIcon name="send" :size="30" color="#FFFFFF" /></AccessibleButton>
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
		<view class="zy-card"><text class="kit-card-t">zy-card 默认（瓷白 + 发丝线）</text></view>
		<view class="zy-card zy-card--mint"><text class="kit-card-t">zy-card--mint（平静语义）</text></view>
		<view class="zy-card zy-card--warm"><text class="kit-card-t">zy-card--warm（非遗/关怀语义）</text></view>
		<view class="zy-card zy-card--feature kit-feature"><text class="kit-card-t kit-feature-t">zy-card--feature（展品大卡，直角）</text></view>

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
			<AccessibleButton variant="ghost" size="sm"><text>换一批</text><ZyIcon name="refresh" :size="26" tone="brand" /></AccessibleButton>
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

		<!-- C9 选择控件 -->
		<text class="kit-h2">C9 ZyChip 筛选片 / ZySegmented 互斥单选</text>
		<view class="kit-row">
			<ZyChip v-for="c in chipDemo" :key="c" :active="chipActive === c" @click="chipActive = c">{{ c }}</ZyChip>
		</view>
		<ZySegmented v-model="segDemo" :options="segOptions" aria-label="样张时长选择" />

		<!-- C10 表单控件 -->
		<text class="kit-h2">C10 ZyInput / ZyTextarea（底线语言 · 聚焦窑青双线 · 密码 eye）</text>
		<FormField label="昵称" required :maxlength="50" :count="kitName.length">
			<template #default="{ id, ariaDescribedby, ariaInvalid }">
				<ZyInput :id="id" v-model="kitName" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" placeholder="请输入昵称" :maxlength="50" />
			</template>
		</FormField>
		<FormField label="密码" required hint="点右侧眼睛可显隐">
			<template #default="{ id }">
				<ZyInput :id="id" v-model="kitPwd" password placeholder="请输入密码" :maxlength="32" />
			</template>
		</FormField>
		<FormField label="错误态示例" :error="'这里是一条错误提示'">
			<template #default="{ id, ariaDescribedby, ariaInvalid }">
				<ZyInput :id="id" model-value="invalid" error :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" />
			</template>
		</FormField>
		<FormField label="多行输入" optional-text="选填" :maxlength="500" :count="kitBio.length">
			<template #default="{ id }">
				<ZyTextarea :id="id" v-model="kitBio" placeholder="写点什么…" :maxlength="500" />
			</template>
		</FormField>

		<!-- C11 线轴 spinner -->
		<text class="kit-h2">C11 ZySpinner · 线轴收针（朱砂线 12 帧离散绕轴；减少动效时静态）</text>
		<view class="kit-row">
			<ZySpinner :size="48" />
			<ZySpinner :size="28" />
			<view style="background:#0B1513;padding:16rpx;display:flex;align-items:center"><ZySpinner :size="48" tone="light" /></view>
			<text class="kit-note">浅底 brand / 深底 light · 上方开关可切静态帧</text>
		</view>

		<!-- C12 空态插画 -->
		<text class="kit-h2">C12 EmptyArt · 六幅线描空态（v7 色板 · H5；MP 降级小织表情）</text>
		<view class="kit-row kit-empty-row">
			<view v-for="v in emptyVariants" :key="v" class="kit-empty-sample"><EmptyArt :variant="v" /><text class="kit-note">{{ v }}</text></view>
		</view>

		<view class="kit-foot"><text>本页为开发样张，不接入业务流程。</text></view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import StitchDivider from '../../components/StitchDivider.vue'
import SectionHead from '../../components/SectionHead.vue'
import MoodFace from '../../components/MoodFace.vue'
import StatStrip from '../../components/StatStrip.vue'
import ChatBubble from '../../components/ChatBubble.vue'
import ChatInputBar from '../../components/ChatInputBar.vue'
import ZyChip from '../../components/ZyChip.vue'
import ZySegmented from '../../components/ZySegmented.vue'
import FormField from '../../components/FormField.vue'
import ZyInput from '../../components/ZyInput.vue'
import ZyTextarea from '../../components/ZyTextarea.vue'
import ZySpinner from '../../components/ZySpinner.vue'
import EmptyArt from '../../components/EmptyArt.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import { useInteraction } from '../../composables/useInteraction'

const { reducedMotion, toggleMotion } = useInteraction()
const iconNames = ['send', 'mic', 'play', 'pause', 'stop', 'sound', 'share', 'external', 'arrow', 'back', 'close', 'more', 'check', 'plus', 'search', 'heart', 'heart-fill', 'star', 'star-fill', 'comment', 'bell', 'settings', 'user', 'home', 'community', 'craft', 'group', 'eye', 'eye-off', 'calendar', 'location', 'clock', 'edit', 'trash', 'download', 'refresh', 'chevron-down', 'alert']
const iconTones = ['text', 'title', 'muted', 'faint', 'brand', 'brand-light', 'care', 'dark', 'dark-muted']
const petFace = ref('calm')
const petStates = [
	{ key: 'calm', label: '标准' }, { key: 'welcome', label: '陪伴' },
	{ key: 'listen', label: '倾听' }, { key: 'think', label: '想想' },
	{ key: 'craft', label: '手作' }, { key: 'happy', label: '完成' },
	{ key: 'sleepy', label: '休息' },
]

const moods = ['开心', '平静', '一般', '有点累', '不太好']
const moodIndex = ref(1)
const stats = [
	{ value: 12, label: '连续创作 · 天' },
	{ value: 8, label: '我的作品' },
	{ value: 186, label: '专注 · 分钟' },
]
const draft = ref('')
const chipDemo = ['可报名', '全部', '工作坊', '课程']
const chipActive = ref('可报名')
const segOptions = [{ value: 1, label: '1 分钟' }, { value: 3, label: '3 分钟' }, { value: 5, label: '5 分钟' }, { value: 15, label: '15 分钟' }]
const segDemo = ref(3)
const kitName = ref('')
const kitPwd = ref('')
const kitBio = ref('')
const emptyVariants = ['works', 'community', 'notice', 'notes', 'groups', 'programs']
const voiceState = ref('')
function onSend() { draft.value = '' }
function onVoice() { voiceState.value = voiceState.value === 'recording' ? '' : 'recording' }
</script>

<style scoped>
.kit-page { min-height: 100vh; padding: 40rpx 32rpx 96rpx; background: var(--zy-bg, #F1F4F1); }
.kit-h1 { display: block; font-size: var(--zy-text-title-lg, 44rpx); font-weight: 600; color: var(--zy-title, #111816); margin-bottom: 16rpx; }
.kit-pet-stage { display: flex; flex-direction: column; align-items: center; gap: 16rpx; padding: 32rpx; margin-bottom: 24rpx; background: var(--zy-bg-sunken, #E9EFEB); border-radius: 0; }
.kit-pet-thumbs { margin: 28rpx 0; }
.kit-pet-sample { display: flex; flex-direction: column; align-items: center; }
.kit-h2 { display: block; font-size: var(--zy-text-caption, 24rpx); letter-spacing: 2rpx; color: var(--zy-text, #42524C); margin: 48rpx 0 20rpx; }
.kit-col { display: flex; flex-direction: column; gap: 20rpx; align-items: flex-start; }
.kit-row { display: flex; align-items: center; gap: 20rpx; flex-wrap: wrap; }
.kit-empty-row { align-items: flex-start; }
.kit-empty-sample { display: flex; flex-direction: column; align-items: center; gap: 8rpx; width: 30%; }
.kit-icons { display: grid; grid-template-columns: repeat(6, 1fr); gap: 24rpx 12rpx; }
.kit-icon-cell { display: flex; flex-direction: column; align-items: center; gap: 8rpx; padding: 12rpx 0; }
.kit-tone-dark { background: var(--zy-d-bg, #0B1513); }
.kit-note-dark { color: var(--zy-d-text-secondary, #A9BBB2); }
.kit-card-t { font-size: var(--zy-text-body, 28rpx); color: var(--zy-title, #111816); }
.zy-card { margin-bottom: 20rpx; }
.kit-feature { padding: 32rpx; background: var(--zy-card-bg, #ffffff); }
.kit-stitch-row { height: 60rpx; }
.kit-note { font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #42524C); }
.kit-moods { justify-content: space-between; }
.kit-foot { margin-top: 64rpx; text-align: center; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #42524C); }
</style>
