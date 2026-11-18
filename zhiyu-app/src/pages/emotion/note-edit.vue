<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 日期区 -->
		<view class="date-section zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="page-eyebrow">MOOD NOTE · 记录心情</text>
			<text class="date-big">{{ todayStr }}</text>
			<text class="date-week">{{ weekDay }}</text>
		</view>

		<!-- 心情选择 -->
		<view class="section zy-slide-up zy-stagger" style="--stagger-i: 1">
			<text class="section-title">此刻心情</text>
			<scroll-view scroll-x class="mood-scroll" :show-scrollbar="false">
				<view class="mood-track">
					<view
						v-for="(m, i) in moods"
						:key="i"
						class="mood-item"
						:class="{ 'mood-item--active': selectedMood === m.label }"
						role="button"
						tabindex="0"
						@click="selectMood(m)"
						@keydown.enter.prevent="selectMood(m)"
						@keydown.space.prevent="selectMood(m)"
					>
					<view class="mood-seal-wrap" aria-hidden="true">
						<text class="mood-seal">{{ m.char }}</text>
					</view>
					<text class="mood-label">{{ m.label }}</text>
					</view>
				</view>
			</scroll-view>
		</view>

		<!-- 内容输入卡片 -->
		<view class="section zy-slide-up zy-stagger" style="--stagger-i: 2">
			<view class="textarea-card">
				<FormField label="写下此刻" required :error="errors.content" :maxlength="500" :count="content.length">
					<template #default="{ id, ariaDescribedby, ariaInvalid }">
						<textarea
							:id="id"
							class="note-textarea"
							v-model="content"
							:aria-describedby="ariaDescribedby"
							:aria-invalid="ariaInvalid"
							placeholder="写下此刻的心情..."
							placeholder-style="color: #8a9a93; font-size: 24rpx;"
							:maxlength="500"
							:auto-height="true"
							:show-confirm-bar="false"
							:adjust-position="true"
							@input="errors.content = ''"
						/>
					</template>
				</FormField>
			</view>
		</view>

		<!-- 保存按钮 -->
		<view class="action-area zy-slide-up zy-stagger" style="--stagger-i: 3">
			<view class="save-btn" :class="{ 'save-btn--loading': saving }" role="button" tabindex="0" @click="handleSave" @keydown.enter.prevent="handleSave" @keydown.space.prevent="handleSave">
				<text class="save-btn-text">{{ saving ? '保存中...' : '保存' }}</text>
			</view>
			<view v-if="isEdit" class="delete-btn" role="button" tabindex="0" @click="handleDelete" @keydown.enter.prevent="handleDelete" @keydown.space.prevent="handleDelete">
				<text class="delete-btn-text">移除便签</text>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

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
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import ActionSheet from '../../components/ActionSheet.vue'
import FormField from '../../components/FormField.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const moods = [
	{ kind: 0, label: '开心', char: '喜' },
	{ kind: 1, label: '平静', char: '静' },
	{ kind: 2, label: '一般', char: '平' },
	{ kind: 3, label: '有点累', char: '倦' },
	{ kind: 4, label: '不太好', char: '低' },
]

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
.page {
	min-height: 100vh;
	padding: 36rpx 32rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f1f4f1);
}

/* ── 日期区 ── */
.date-section {
	margin-bottom: 32rpx;
	padding-bottom: 24rpx;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
}

.page-eyebrow {
	display: block;
	margin-bottom: 10rpx;
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 2px;
	color: var(--zy-muted, #6e7b75);
}

.date-big {
	font-size:56rpx;
	font-weight: 500;
	color: var(--zy-title, #111816);
	font-family: var(--zy-font-display, serif);
	display: block;
	line-height: 1.2;
}

.date-week {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
	display: block;
	margin-top: 4rpx;
}

/* ── 通用 section ── */
.section {
	margin-bottom: 32rpx;
}

.section-title {
	display: block;
	font-size:var(--zy-text-body-lg, 30rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
	margin-bottom: 20rpx;
	font-family: var(--zy-serif);
}

/* ── 心情选择 ── */
.mood-scroll {
	width: 100%;
	white-space: nowrap;
}

.mood-track {
	display: flex;
	width: 100%;
	gap: 0;
	padding-right: 0;
}

.mood-item {
	position: relative;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: flex-start;
	gap: 8rpx;
	padding: 20rpx 2rpx;
	flex: 1;
	width: auto;
	min-width: 0;
	border-radius: 0;
	background-color: transparent;
	border: 1rpx solid var(--zy-border, #d3ded8);
	border-left: 0;
	flex-shrink: 1;
	transition: all var(--zy-duration-base, 250ms) var(--zy-ease-out, ease-out);
}
.mood-item:first-child { border-left: 1rpx solid var(--zy-border, #d3ded8); }

.mood-ring {
	display: none;
}

.mood-seal-wrap {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.mood-seal {
	font-family: var(--zy-font-display, "Songti SC", "STSong", "Noto Serif CJK SC", serif);
	font-size: 54rpx;
	line-height: 1;
	color: var(--zy-title, #111816);
}

.mood-item--active {
	border-color: var(--zy-brand-600, #0e5b53);
	background-color: transparent;
	box-shadow: inset 0 -3rpx 0 var(--zy-brand-600, #0e5b53);
	transform: none;
	box-shadow: inset 0 -3rpx 0 var(--zy-brand-600, #0e5b53);
}

.mood-item--active .mood-seal {
	color: var(--zy-brand-700, #0a403b);
}

.mood-label {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #4a5f54);
}

.mood-item--active .mood-label {
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	font-weight: 500;
}

/* ── 内容输入卡片 ── */
.textarea-card {
	background-color: transparent;
	border-radius: 0;
	border: 0;
	border-top: 1rpx solid var(--zy-border, #d3ded8);
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	padding: 24rpx 0;
	box-shadow: none;
}

.note-textarea {
	width: 100%;
	min-height: 200rpx;
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.8;
	color: var(--zy-title, #111816);
	background-color: transparent;
	border: none;
}

.char-count {
	display: block;
	text-align: right;
	margin-top: 12rpx;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #4a5f54);
}

/* ── 保存按钮 ── */
.action-area {
	margin-top: 16rpx;
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.save-btn {
	height: 88rpx;
	border-radius: 0;
	background: var(--zy-brand-600, #0e5b53);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
	box-shadow: none;
}

.save-btn:active {
	transform: none;
}

.save-btn:focus-visible,
.delete-btn:focus-visible,
.mood-item:focus-visible {
	outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #1a645e));
	outline-offset: 3px;
}

.save-btn--loading {
	opacity: 0.6;
}

.save-btn-text {
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 500;
	color: #FFFFFF;
}

.delete-btn {
	height: 72rpx;
	border-radius: 0;
	border: 0;
	border-top: 1rpx solid var(--zy-border, #d3ded8);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.delete-btn:active {
	background-color: rgba(166, 72, 66, 0.05);
}

.delete-btn-text {
	font-size:var(--zy-text-body-lg, 30rpx);
	color: var(--zy-care, var(--zy-care, #a64842));
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-muted, #6e7b75);
	text-align: center;
}
</style>
