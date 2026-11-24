<template>
	<view :class="zyThemeClass" class="page">
		<ZyPageHead seal="记" title="此刻心情" :subtitle="todayStr + ' · ' + weekDay" />

		<!-- 心情卡：大表情 + 一句回应 + 五个小表情 -->
		<view class="mood-card">
			<view class="mood-hero">
				<view class="mood-hero-face" :class="{ bounce: bump }">
					<MoodEmoji :kind="current ? current.kind : 1" :size="150" />
				</view>
				<view class="mood-hero-copy">
					<text class="mood-question">{{ current ? current.label : '今天的你，感觉怎么样？' }}</text>
					<text class="mood-reply">{{ current ? current.reply : '选一个最接近的就好，不必很准确。' }}</text>
				</view>
			</view>
			<view class="mood-row" role="radiogroup" aria-label="选择此刻心情">
				<view
					v-for="m in moods"
					:key="m.kind"
					class="mood-item"
					:class="[{ 'mood-item--active': selectedMood === m.label }, 'tone-' + m.kind]"
					role="radio"
					:aria-checked="selectedMood === m.label"
					:aria-label="m.label"
					tabindex="0"
					@click="selectMood(m)"
					@keydown.enter.prevent="selectMood(m)"
					@keydown.space.prevent="selectMood(m)"
				>
					<view class="mood-face-wrap"><MoodEmoji :kind="m.kind" :size="76" /></view>
					<text class="mood-label">{{ m.label }}</text>
				</view>
			</view>
		</view>

		<!-- 书写卡：提示语 + 输入 -->
		<view class="write-card">
			<view class="write-head">
				<view class="write-mark" aria-hidden="true"></view>
				<text class="write-title">写下此刻</text>
			</view>
			<scroll-view scroll-x class="prompt-scroll" :show-scrollbar="false">
				<view class="prompt-track">
					<view v-for="p in prompts" :key="p" class="prompt-chip" role="button" tabindex="0" :aria-label="'用这句开头：' + p" @click="usePrompt(p)" @keydown.enter.prevent="usePrompt(p)">{{ p }}</view>
				</view>
			</scroll-view>
			<FormField label="" :error="errors.content" :maxlength="500" :count="content.length">
				<template #default="{ id, ariaDescribedby, ariaInvalid }">
					<ZyTextarea :id="id" v-model="content" :aria-describedby="ariaDescribedby" :aria-invalid="ariaInvalid" :error="!!errors.content" placeholder="不用写好，一句话也可以……" :maxlength="500" auto-height :show-confirm-bar="false" @update:model-value="errors.content = ''" />
				</template>
			</FormField>
			<text class="write-note">只有你自己能看到这张便签。</text>
		</view>

		<view class="action-area">
			<view class="note-save" :class="{ 'note-save--loading': saving }" role="button" tabindex="0" @click="handleSave" @keydown.enter.prevent="handleSave" @keydown.space.prevent="handleSave">
				<text class="note-save-text">{{ saving ? '正在收好…' : (isEdit ? '保存修改' : '收进心情便签') }}</text>
			</view>
			<view v-if="isEdit" class="note-delete" role="button" tabindex="0" @click="handleDelete" @keydown.enter.prevent="handleDelete" @keydown.space.prevent="handleDelete">
				<text class="note-delete-text">移除这张便签</text>
			</view>
		</view>

		<ActionSheet
			:open="pendingDelete"
			title="移除便签"
			description="确定要移除这条心情便签吗？"
			:actions="[{ key: 'confirm', label: '确认移除', tone: 'danger', busy: deleting }]"
			cancel-text="再想想"
			:busy="deleting"
			@select="confirmDelete"
			@close="pendingDelete = false"
		/>
		<Toast />
	</view>
</template>

<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import ActionSheet from '../../components/ActionSheet.vue'
import FormField from '../../components/FormField.vue'
import ZyTextarea from '../../components/ZyTextarea.vue'
import MoodEmoji from '../../components/MoodEmoji.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const moods = [
	{ kind: 0, label: '开心', reply: '真好呀，把这份开心好好收起来。', prompts: ['今天让我笑出来的是…', '想谢谢的一个人是…', '这份开心来自…'] },
	{ kind: 1, label: '平静', reply: '平静也很珍贵，慢慢感受它。', prompts: ['此刻我身边有…', '让我安定下来的是…', '我想保持这种感觉，因为…'] },
	{ kind: 2, label: '一般', reply: '普普通通的一天，也值得被记住。', prompts: ['今天还算顺利的一件事是…', '今天有点在意的是…', '明天想试试…'] },
	{ kind: 3, label: '有点累', reply: '辛苦了，先让自己歇一会儿。', prompts: ['让我累的是…', '现在最想做的休息是…', '我已经尽力的地方是…'] },
	{ kind: 4, label: '不太好', reply: '谢谢你愿意说出来，我在这儿陪着你。', prompts: ['此刻我感到…', '如果能对自己说一句话…', '可以让我好一点点的是…'] },
]
const DEFAULT_PROMPTS = ['今天让我印象深的是…', '此刻我感到…', '想对自己说…']

const WEEK_DAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

const noteId = ref(null)
const selectedMood = ref('')
const content = ref('')
const errors = ref({ content: '' })
const saving = ref(false)
const loadingDetail = ref(false)
const pendingDelete = ref(false)
const deleting = ref(false)

const isEdit = computed(() => !!noteId.value)
const current = computed(() => moods.find((m) => m.label === selectedMood.value) || null)
const prompts = computed(() => (current.value ? current.value.prompts : DEFAULT_PROMPTS))
const bump = ref(false)
function usePrompt(p) {
	const base = content.value.trim()
	content.value = (base ? base + '\n' : '') + p
	errors.value.content = ''
}

const todayStr = computed(() => {
	const d = new Date()
	const y = d.getFullYear()
	const m = String(d.getMonth() + 1).padStart(2, '0')
	const day = String(d.getDate()).padStart(2, '0')
	return y + '-' + m + '-' + day
})

const weekDay = computed(() => {
	const d = new Date()
	return WEEK_DAYS[d.getDay()] || ''
})

onLoad((query) => {
	if (query && query.id) {
		noteId.value = query.id
		loadDetail()
	}
})

async function loadDetail() {
	loadingDetail.value = true
	try {
		const data = await http.get('/api/emotion/notes/' + noteId.value)
		if (data) {
			selectedMood.value = data.mood || ''
			content.value = data.content || ''
		}
	} catch (e) {
		toast.care((e && e.message) || '加载失败')
	} finally {
		loadingDetail.value = false
	}
}

function selectMood(m) {
	selectedMood.value = selectedMood.value === m.label ? '' : m.label
	bump.value = false
	setTimeout(() => { bump.value = true }, 0)
}

async function handleSave() {
	const trimmed = content.value.trim()
	if (!trimmed) {
		errors.value.content = '内容不能为空'
		return
	}

	saving.value = true
	try {
		if (isEdit.value) {
			await http.put('/api/emotion/notes/' + noteId.value, {
				mood: selectedMood.value || null,
				content: trimmed,
			})
		} else {
			await http.post('/api/emotion/notes', {
				mood: selectedMood.value || null,
				content: trimmed,
				noteDate: todayStr.value,
			})
		}
		toast.success('保存成功')
		setTimeout(() => {
			uni.navigateBack()
		}, 800)
	} catch (e) {
		toast.care((e && e.message) || '保存失败')
	} finally {
		saving.value = false
	}
}

function handleDelete() {
	pendingDelete.value = true
}

async function confirmDelete() {
	if (deleting.value) return
	deleting.value = true
	try {
		await http.del('/api/emotion/notes/' + noteId.value)
		toast.success('已移除')
		pendingDelete.value = false
		setTimeout(() => {
			uni.navigateBack()
		}, 800)
	} catch (e) {
		toast.care((e && e.message) || '操作失败')
	} finally {
		deleting.value = false
	}
}
</script>

<style scoped>
/* v9 记录心情：暖色、手绘表情、像在写一张小便签 */
.page {
	--note-warm: #fff4e8;
	--note-warm-2: #ffe7cf;
	--note-ink: #8a4b2a;
	min-height: 100vh;
	padding: 28rpx 28rpx 140rpx;
	box-sizing: border-box;
	background: var(--zy-bg);
}
.page.zy-theme-dark { --note-warm: #2e2520; --note-warm-2: #3a2d24; --note-ink: #f0c49b; }

/* 心情卡 */
.mood-card { position: relative; padding: 32rpx 26rpx 26rpx; border-radius: 40rpx; background: linear-gradient(160deg, var(--note-warm-2) 0%, var(--note-warm) 45%, var(--zy-card-bg) 100%); box-shadow: 0 12rpx 36rpx rgba(201, 120, 60, .14); overflow: hidden; }
.mood-hero { display: flex; align-items: center; gap: 24rpx; margin-bottom: 28rpx; }
.mood-hero-face { flex-shrink: 0; width: 170rpx; height: 170rpx; border-radius: 50%; background: rgba(255, 255, 255, .7); display: flex; align-items: center; justify-content: center; box-shadow: inset 0 0 0 6rpx rgba(255, 255, 255, .9); }
.mood-hero-face.bounce { animation: moodPop 420ms cubic-bezier(.34, 1.56, .64, 1); }
@keyframes moodPop { 0% { transform: scale(.82); } 100% { transform: scale(1); } }
.mood-hero-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10rpx; }
.mood-question { font-family: var(--zy-font-display); font-size: 40rpx; font-weight: 700; line-height: 1.35; color: var(--zy-title); }
.mood-reply { font-size: 26rpx; line-height: 1.7; color: var(--note-ink); }

.mood-row { display: flex; justify-content: space-between; gap: 8rpx; padding: 18rpx 8rpx 8rpx; border-radius: 32rpx; background: rgba(255, 255, 255, .62); }
.page.zy-theme-dark .mood-row { background: rgba(0, 0, 0, .18); }
.mood-item { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 8rpx; padding: 8rpx 0 12rpx; border-radius: 28rpx; transition: transform 220ms cubic-bezier(.34, 1.56, .64, 1), background-color 200ms ease; }
.mood-face-wrap { width: 96rpx; height: 96rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 4rpx solid transparent; transition: border-color 200ms ease, box-shadow 200ms ease; }
.mood-label { font-size: 24rpx; color: var(--zy-text); }
.mood-item:active { transform: scale(.94); }
.mood-item--active { transform: translateY(-6rpx); }
.mood-item--active .mood-label { font-weight: 700; color: var(--zy-title); }
.mood-item--active.tone-0 .mood-face-wrap { border-color: #f2b53a; box-shadow: 0 8rpx 20rpx rgba(242, 181, 58, .4); background: #fff8e1; }
.mood-item--active.tone-1 .mood-face-wrap { border-color: #6fbf98; box-shadow: 0 8rpx 20rpx rgba(111, 191, 152, .38); background: #effaf4; }
.mood-item--active.tone-2 .mood-face-wrap { border-color: #e0b866; box-shadow: 0 8rpx 20rpx rgba(224, 184, 102, .38); background: #fff7e6; }
.mood-item--active.tone-3 .mood-face-wrap { border-color: #a497d8; box-shadow: 0 8rpx 20rpx rgba(164, 151, 216, .38); background: #f5f2fd; }
.mood-item--active.tone-4 .mood-face-wrap { border-color: #8aa6cf; box-shadow: 0 8rpx 20rpx rgba(138, 166, 207, .4); background: #eef3fa; }
.mood-item:focus-visible { outline: 3px solid var(--zy-brand-600); outline-offset: 2px; }

/* 书写卡 */
.write-card { margin-top: 24rpx; padding: 28rpx 26rpx 22rpx; border-radius: 36rpx; background: var(--zy-card-bg); box-shadow: var(--zy-shadow-card); }
.write-head { display: flex; align-items: center; gap: 14rpx; margin-bottom: 18rpx; }
.write-mark { width: 8rpx; height: 32rpx; border-radius: 4rpx; background: #f0a35e; }
.write-title { font-family: var(--zy-font-display); font-size: 34rpx; font-weight: 700; color: var(--zy-title); }
.write-count { margin-left: auto; font-size: 22rpx; color: var(--zy-muted); }
.prompt-scroll { width: 100%; white-space: nowrap; margin-bottom: 18rpx; }
.prompt-track { display: inline-flex; gap: 12rpx; padding: 2rpx; }
.prompt-chip { display: inline-flex; align-items: center; min-height: 60rpx; padding: 0 24rpx; border-radius: 999rpx; background: var(--note-warm); color: var(--note-ink); font-size: 24rpx; border: 2rpx dashed rgba(201, 120, 60, .35); white-space: nowrap; }
.prompt-chip:active { background: var(--note-warm-2); }
.write-note { display: block; margin-top: 14rpx; font-size: 22rpx; color: var(--zy-muted); }

/* 按钮 */
.action-area { margin-top: 28rpx; display: flex; flex-direction: column; gap: 16rpx; }
.note-save { height: 96rpx; border-radius: 999rpx; background: linear-gradient(90deg, #f0a35e 0%, #e27d5b 100%); display: flex; align-items: center; justify-content: center; box-shadow: 0 12rpx 28rpx rgba(226, 125, 91, .35); }
.note-save:active { transform: scale(.98); }
.note-save--loading { opacity: .7; }
.note-save-text { font-size: 32rpx; font-weight: 700; color: #fff; letter-spacing: 2rpx; }
.note-delete { height: 80rpx; border-radius: 999rpx; display: flex; align-items: center; justify-content: center; background: var(--zy-bg-tint-care); }
.note-delete-text { font-size: 28rpx; color: var(--zy-care); }
.note-save:focus-visible, .note-delete:focus-visible { outline: 3px solid var(--zy-brand-600); outline-offset: 3px; }
</style>
