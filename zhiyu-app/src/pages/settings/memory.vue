<template>
	<view :class="zyThemeClass" class="growth-page memory-page zy-page-enter">
		<view class="growth-hero">
			<ZyPageHead seal="忆" title="我记得什么，你说了算。" subtitle="你告诉我的，和我注意到的。都能看、能改、能删。" />
		</view>

		<view v-if="loading" class="growth-card">
			<view class="growth-skeleton" /><view class="growth-skeleton short" />
		</view>
		<view v-if="error" class="growth-error" role="alert">{{ error }}</view>

		<view class="growth-card">
			<view class="card-head">
				<text class="growth-card-title">记忆开关</text>
				<text class="memory-status">{{ enabled ? '已开启' : '已关闭' }}</text>
			</view>
			<text class="growth-muted">{{ disclaimer }}</text>
			<view class="growth-row">
				<AccessibleButton class="growth-button memory-switch" :class="{ secondary: !enabled }" :disabled="saving" @click="toggle">
					{{ enabled ? '关闭小织记忆' : '开启小织记忆' }}
				</AccessibleButton>
			</view>
			<view v-if="enabled" class="consent consent-on">
				<view class="consent-box is-on"><ZyIcon name="check" :size="20" tone="brand" /></view>
				<text>已开启。小织只会使用你明确选择的偏好与话题；关掉后，下一句起就不再引用。</text>
			</view>
			<view v-else class="consent" role="checkbox" :aria-checked="consent" tabindex="0" @click="consent = !consent" @keydown.enter.prevent="consent = !consent" @keydown.space.prevent="consent = !consent">
				<view class="consent-box"><ZyIcon v-if="consent" name="check" :size="20" tone="brand" /></view>
				<text>我已读过上面的说明：小织会记住我填写的偏好与话题，也会从手作和便签里看出「我注意到的」。</text>
			</view>
		</view>

		<view class="growth-card">
			<text class="growth-card-title">你告诉我的</text>
			<text class="growth-muted">只在你开启记忆后生效，{{ ttlDays }} 天后自动忘记。</text>
			<template v-if="enabled">
				<text class="field-label">愿意做多久</text>
				<picker mode="selector" :range="minuteOptions" range-key="label" @change="minutes = minuteOptions[$event.detail.value].value">
					<view class="growth-choice selected">{{ valueLabel('preferred_minutes') }}</view>
				</picker>
				<text class="field-label">顺手的材料</text>
				<picker mode="selector" :range="materialOptions" range-key="label" @change="material = materialOptions[$event.detail.value].value">
					<view class="growth-choice selected">{{ valueLabel('preferred_material') }}</view>
				</picker>
				<text class="field-label">喜欢的纹样</text>
				<picker mode="selector" :range="familyOptions" range-key="label" @change="family = familyOptions[$event.detail.value].value">
					<view class="growth-choice selected">{{ valueLabel('preferred_pattern_family') }}</view>
				</picker>
			</template>
			<view v-else class="growth-muted memory-off">开启后，这里可以选练习时长、材料和纹样。</view>

			<text class="field-label">不想聊的话题（最多 3 个）</text>
			<text class="growth-muted">写下来，小织就不主动提。例如：考试、家庭、体重。</text>
			<view class="topic-row">
				<input class="growth-input topic-input" v-model="newTopic" maxlength="12" placeholder="写一个话题，12 字以内" :disabled="!enabled" @confirm="addTopic" />
				<AccessibleButton class="growth-button topic-add" :disabled="!enabled || !newTopic.trim()" @click="addTopic">加上</AccessibleButton>
			</view>
			<view v-if="avoidTopics.length" class="topic-list">
				<view v-for="(topic, i) in avoidTopics" :key="topic" class="topic-tag">
					<text>{{ topic }}</text>
					<view class="topic-remove" role="button" tabindex="0" :aria-label="'删除话题 ' + topic" @click="avoidTopics.splice(i, 1)" @keydown.enter.prevent="avoidTopics.splice(i, 1)" @keydown.space.prevent="avoidTopics.splice(i, 1)"><ZyIcon name="close" :size="24" tone="muted" /></view>
				</view>
			</view>
			<text v-else class="growth-muted">还没有设置。没有这个需要也没关系。</text>
			<AccessibleButton v-if="enabled" class="growth-button" :disabled="saving" @click="save">保存这些</AccessibleButton>
		</view>

		<view class="growth-card">
			<text class="growth-card-title">我注意到的</text>
			<text class="growth-muted">{{ profileNote }}</text>
			<view v-if="!enabled" class="growth-muted memory-off">记忆关着，小织不看这些记录。</view>
			<view v-else-if="!derived.length" class="growth-muted memory-off">还没有可说的。做几件手作、写几条便签，小织才会有发现。</view>
			<view v-for="item in derived" :key="item.key" class="derived-item">
				<view class="derived-main">
					<text class="derived-label">{{ item.label }}</text>
					<text class="derived-value">{{ item.value }}</text>
					<text class="derived-source">{{ item.source }}</text>
				</view>
			</view>
		</view>

		<view class="growth-card">
			<text class="growth-card-title">聊天里怎么用</text>
			<text class="growth-muted">最多带 {{ maxPromptLines }} 条进对话，不说原话；你不想聊的话题不会提；低落天数只在这里给你看，不拿去定义你。关闭记忆后，下一句就不再使用。</text>
			<AccessibleButton class="growth-button secondary" @click="goChat">去和小织聊聊</AccessibleButton>
		</view>

		<view v-if="status" class="growth-muted" role="status" aria-live="polite">{{ status }}</view>
		<AccessibleButton class="growth-button danger" :disabled="saving" @click="clear">删除全部记忆</AccessibleButton>
		<text class="growth-foot">小织是陪伴工具，不能替代专业心理咨询；感到撑不住时请立即联系学校心理中心或心理援助热线。</text>
	</view>
</template>

<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
import { ref } from 'vue'
import ZyIcon from '../../components/ZyIcon.vue'
import { onShow } from '@dcloudio/uni-app'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { request } from '../../utils/request'

const loading = ref(true), saving = ref(false), error = ref(''), status = ref('')
const enabled = ref(false), consent = ref(false), ttlDays = ref(180), disclaimer = ref(''), profileNote = ref('')
const maxPromptLines = ref(3)
const minutes = ref('10'), material = ref('bamboo'), family = ref('weave')
const items = ref([]), derived = ref([]), avoidTopics = ref([]), newTopic = ref('')
const minuteOptions = ['5', '10', '15', '20', '30'].map(value => ({ value, label: `${value} 分钟` }))
const materialOptions = [
	{ value: 'bamboo', label: '竹' }, { value: 'thread', label: '线' },
	{ value: 'paper', label: '纸' }, { value: 'porcelain', label: '瓷' },
]
const familyOptions = [
	{ value: 'stitch', label: '针线' }, { value: 'weave', label: '编织' },
	{ value: 'cutout', label: '剪刻' }, { value: 'porcelain', label: '器面' },
]

function valueLabel(key) {
	const found = items.value.find(item => item.key === key)
	return found ? found.valueLabel : '未设置'
}

function apply(data) {
	enabled.value = !!data.enabled
	ttlDays.value = data.ttlDays || 180
	disclaimer.value = data.disclaimer || ''
	profileNote.value = data.profileNote || ''
	maxPromptLines.value = data.maxPromptLines || 3
	items.value = data.items || []
	derived.value = data.derived || []
	avoidTopics.value = data.avoidTopics || []
	for (const item of items.value) {
		if (item.key === 'preferred_minutes') minutes.value = item.value
		if (item.key === 'preferred_material') material.value = item.value
		if (item.key === 'preferred_pattern_family') family.value = item.value
	}
}

async function run(action) {
	if (saving.value) return
	saving.value = true; error.value = ''; status.value = ''
	try { apply(await action()) } catch (e) { error.value = e.message || '操作未完成，请稍后重试。' }
	finally { saving.value = false }
}

async function load() {
	loading.value = true; error.value = ''
	try { apply(await request({ url: '/api/companion/memory' })) }
	catch (e) { error.value = e.message || '记忆设置暂时无法加载。' }
	finally { loading.value = false }
}

function addTopic() {
	const topic = newTopic.value.trim()
	if (!topic) return
	if (avoidTopics.value.includes(topic)) { status.value = '这个话题已经在了。'; return }
	if (avoidTopics.value.length >= 3) { error.value = '最多设置 3 个不想聊的话题。'; return }
	error.value = ''; status.value = ''
	avoidTopics.value.push(topic)
	newTopic.value = ''
}

function toggle() {
	if (enabled.value) return run(() => request({ url: '/api/companion/memory/enabled', method: 'PUT', data: { enabled: false, consent: true } }))
	if (!consent.value) { error.value = '请先勾选授权说明。'; return }
	return run(() => request({ url: '/api/companion/memory/enabled', method: 'PUT', data: { enabled: true, consent: true } }))
}

function save() {
	if (!consent.value) { error.value = '请先勾选授权说明。'; return }
	run(async () => {
		const data = await request({ url: '/api/companion/memory', method: 'PUT', data: { consent: true, preferences: { preferred_minutes: minutes.value, preferred_material: material.value, preferred_pattern_family: family.value, avoid_topics: avoidTopics.value.join(',') } } })
		status.value = '存好了，半年后自动忘记。'
		return data
	})
}

function clear() {
	uni.showModal({
		title: '删除全部记忆', content: '偏好、话题和授权都会删掉，「我注意到的」也不再用。这个操作不能撤回。',
		confirmText: '删除', confirmColor: '#a64842',
		success: ({ confirm }) => {
			if (confirm) run(async () => { const data = await request({ url: '/api/companion/memory', method: 'DELETE' }); consent.value = false; status.value = '记忆已全部删除。'; return data })
		},
	})
}

function goChat() { uni.navigateTo({ url: '/pages/emotion/chat' }) }
onShow(load)
</script>

<style scoped>
.field-label { display: block; font-size: var(--zy-text-body, 28rpx); font-weight: 600; margin-top: 26rpx; margin-bottom: 6rpx; }
.consent { display: flex; gap: 12rpx; align-items: flex-start; font-size: var(--zy-text-caption, 24rpx); line-height: 1.7; color: var(--zy-text, #5c5648); margin-top: 20rpx; }
.consent-box { width: 32rpx; height: 32rpx; border: 2rpx solid var(--zy-brand-600, #24478f); border-radius: 8rpx; text-align: center; line-height: 30rpx; color: var(--zy-brand-700, #24478f); flex-shrink: 0; }
.memory-off { display: block; margin-top: 18rpx; }
.topic-row { display: flex; align-items: center; gap: 16rpx; }
.topic-input { flex: 1; }
.topic-add { margin-top: 18rpx; padding: 0 28rpx; }
.topic-list { display: flex; flex-wrap: wrap; gap: 12rpx; margin: 16rpx 0; }
.topic-tag { display: inline-flex; align-items: center; gap: 8rpx; background: var(--zy-bg-tint-mint, #eef3fb); color: var(--zy-brand-700, #24478f); border-radius: 999rpx; padding: 8rpx 12rpx 8rpx 20rpx; font-size: var(--zy-text-caption, 24rpx); }
.topic-remove { font-size: var(--zy-text-body, 28rpx); line-height: 1; padding: 4rpx 8rpx; color: var(--zy-brand-700, #24478f); cursor: pointer; }
.topic-remove::after { border: 0; }
.derived-item { display: flex; padding: 22rpx 0; border-top: 1rpx solid var(--zy-line-soft, #e9e2d5); }
.derived-item:first-of-type { border-top: 0; }
.derived-main { flex: 1; }
.derived-label { display: block; font-size: var(--zy-text-caption, 24rpx); color: var(--zy-text, #5c5648); }
.derived-value { display: block; font-size: var(--zy-text-title, 32rpx); font-weight: 600; color: var(--zy-title, #33302a); margin-top: 6rpx; }
.derived-source { display: block; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-text, #5c5648); margin-top: 6rpx; }

/* ── v7 展签收口：冷瓷白、墨绿、发丝线、零圆角 ── */
.memory-page { padding: 22rpx 36rpx 96rpx; background: var(--zy-bg); }
.memory-page .growth-hero { padding: 20rpx 0 30rpx; }



.memory-page .growth-hero + .growth-card { border-top: 0; }
.memory-page .growth-card { background: transparent; border: 0; border-top: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); padding: 30rpx 0 32rpx; margin: 0; }
.memory-page .growth-card-title { color: var(--zy-title); font-family: var(--zy-serif); font-size: 34rpx; font-weight: 500; letter-spacing: 1rpx; margin-bottom: 12rpx; }
.memory-page .card-head { display: flex; align-items: baseline; justify-content: space-between; gap: 16rpx; }
.memory-page .card-head .growth-card-title { margin-bottom: 12rpx; }
.memory-page .memory-status { color: var(--zy-brand-700); font-size: 20rpx; letter-spacing: 2rpx; }
.memory-page .growth-muted { color: var(--zy-muted); font-size: 22rpx; line-height: 1.8; }
.memory-page .field-label { color: var(--zy-title); font-size: 22rpx; font-weight: 500; letter-spacing: 1rpx; margin-top: 24rpx; margin-bottom: 4rpx; }
.memory-page .consent { color: var(--zy-muted); font-size: 21rpx; line-height: 1.8; margin-top: 18rpx; }
.memory-page .consent-on { color: var(--zy-brand-700); }
.memory-page .consent-box { display: flex; align-items: center; justify-content: center; width: 28rpx; height: 28rpx; border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); line-height: 26rpx; color: var(--zy-care); font-size: 20rpx; }
.memory-page .consent-box.is-on { border-color: var(--zy-brand-600); color: var(--zy-brand-700); }
.memory-page .growth-input { height: 76rpx; min-height: 76rpx; padding: 0 4rpx; margin: 10rpx 0; border: 0; border-bottom: 2rpx solid var(--zy-faint, #8B9994); border-radius: var(--zy-radius-control, 18rpx); background: transparent; color: var(--zy-title); font-size: 24rpx; }
.memory-page .growth-choice { padding: 18rpx 0; margin: 0; border: 0; border-bottom: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); background: transparent; color: var(--zy-text); font-size: 24rpx; }
.memory-page .growth-choice.selected { border-bottom-color: var(--zy-brand-600); background: transparent; color: var(--zy-brand-700); }
.memory-page .growth-button { margin-top: 20rpx; padding: 0 24rpx; min-height: 72rpx; border-radius: var(--zy-radius-control, 18rpx); background: var(--zy-brand-700); color: #fff; font-size: 24rpx; line-height: 72rpx; }
.memory-page .growth-button.memory-switch { display: inline-flex; width: auto; align-self: flex-start; min-height: 64rpx; margin-top: 16rpx; background: transparent; border: 1rpx solid var(--zy-brand-700); color: var(--zy-brand-700); font-size: 22rpx; line-height: 64rpx; }
.memory-page .growth-button.secondary { background: transparent; border: 1rpx solid var(--zy-border); color: var(--zy-brand-700); }
.memory-page .growth-button.danger { background: transparent; border: 1rpx solid rgba(166,72,66,.42); color: var(--zy-care); }
.memory-page .growth-button.is-disabled { opacity: 1; background: transparent; border-color: var(--zy-border); color: var(--zy-muted); }
.memory-page .growth-error { background: transparent; border-left: 2rpx solid var(--zy-care); border-radius: var(--zy-radius-control, 18rpx); color: var(--zy-care); padding: 10rpx 0 10rpx 16rpx; }
.memory-page .topic-tag { border-radius: var(--zy-radius-control, 18rpx); background: transparent; border: 1rpx solid var(--zy-border); color: var(--zy-brand-700); font-size: 21rpx; padding: 6rpx 10rpx 6rpx 14rpx; }
.memory-page .derived-item { border-top: 1rpx solid var(--zy-border); padding: 20rpx 0; }
.memory-page .derived-label { color: var(--zy-muted); font-size: 20rpx; letter-spacing: 1rpx; }
.memory-page .derived-value { color: var(--zy-title); font-family: var(--zy-serif); font-size: 30rpx; font-weight: 500; }
.memory-page .derived-source { color: var(--zy-muted); font-size: 20rpx; }
.memory-page .growth-foot { color: var(--zy-muted); font-size: 20rpx; line-height: 1.8; margin-top: 34rpx; }
.memory-page button:active { transform: none; }
</style>
