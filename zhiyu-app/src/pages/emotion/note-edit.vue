<template>
	<view class="page zy-page-enter">
		<!-- 心情选择 -->
		<view class="section zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="section-title">此刻心情</text>
			<scroll-view scroll-x class="mood-scroll" :show-scrollbar="false">
				<view class="mood-track">
					<view
						v-for="(m, i) in moods"
						:key="i"
						class="mood-item"
						:class="{ 'mood-item--active': selectedMood === m.label }"
						@click="selectMood(m)"
					>
						<text class="mood-emoji">{{ m.emoji }}</text>
						<text class="mood-label">{{ m.label }}</text>
					</view>
				</view>
			</scroll-view>
		</view>

		<!-- 内容输入 -->
		<view class="section zy-slide-up zy-stagger" style="--stagger-i: 1">
			<text class="section-title">写下此刻的心情</text>
			<view class="textarea-wrap">
				<textarea
					class="note-textarea"
					v-model="content"
					placeholder="写下此刻的心情..."
					:maxlength="500"
					:auto-height="true"
					:show-confirm-bar="false"
					:adjust-position="true"
				/>
				<text class="char-count">{{ content.length }} / 500</text>
			</view>
		</view>

		<!-- 日期（只读） -->
		<view class="section zy-slide-up zy-stagger" style="--stagger-i: 2">
			<view class="date-row">
				<wd-icon name="calendar" size="32rpx" color="#9CA3AF" />
				<text class="date-text">{{ todayStr }}</text>
			</view>
		</view>

		<!-- 底部按钮 -->
		<view class="action-area zy-slide-up zy-stagger" style="--stagger-i: 3">
			<wd-button
				class="save-btn"
				type="primary"
				block
				:loading="saving"
				@click="handleSave"
			>保存</wd-button>
			<wd-button
				v-if="isEdit"
				class="delete-btn"
				type="error"
				plain
				block
				@click="handleDelete"
			>移除便签</wd-button>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const moods = [
	{ emoji: '😊', label: '开心' },
	{ emoji: '😌', label: '平静' },
	{ emoji: '😐', label: '一般' },
	{ emoji: '😮💨', label: '有点累' },
	{ emoji: '😕', label: '不太好' },
]

const noteId = ref(null)
const selectedMood = ref('')
const content = ref('')
const saving = ref(false)
const loadingDetail = ref(false)

const isEdit = computed(() => !!noteId.value)

const todayStr = computed(() => {
	const d = new Date()
	const y = d.getFullYear()
	const m = String(d.getMonth() + 1).padStart(2, '0')
	const day = String(d.getDate()).padStart(2, '0')
	return y + '-' + m + '-' + day
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
		uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
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
		uni.showToast({ title: '内容不能为空', icon: 'none' })
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
		uni.showToast({ title: '保存成功', icon: 'success' })
		setTimeout(() => {
			uni.navigateBack()
		}, 800)
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '保存失败', icon: 'none' })
	} finally {
		saving.value = false
	}
}

function handleDelete() {
	uni.showModal({
		title: '移除便签',
		content: '确定要移除这条心情便签吗？',
		confirmText: '移除',
		confirmColor: '#b85450',
		success: (res) => {
			if (res.confirm) {
				doDelete()
			}
		},
	})
}

async function doDelete() {
	try {
		await http.del('/api/emotion/notes/' + noteId.value)
		uni.showToast({ title: '已移除', icon: 'success' })
		setTimeout(() => {
			uni.navigateBack()
		}, 800)
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
	}
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

.section {
	margin-bottom: 36rpx;
}

.section-title {
	display: block;
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	margin-bottom: 20rpx;
	font-family: var(--zy-serif);
}

/* ── 心情选择 ── */
.mood-scroll {
	width: 100%;
	white-space: nowrap;
}

.mood-track {
	display: inline-flex;
	gap: 20rpx;
	padding-right: 32rpx;
}

.mood-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 8rpx;
	padding: 20rpx 28rpx;
	border-radius: 20rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 2rpx solid var(--zy-border, #E5E7EB);
	flex-shrink: 0;
	transition: all 0.25s ease;
}

.mood-item--active {
	border-color: #2D9B8F;
	background-color: var(--zy-bg-tint-mint, #F0F9F7);
	transform: scale(1.05);
}

.mood-emoji {
	font-size: 44rpx;
	line-height: 1;
}

.mood-label {
	font-size: 24rpx;
	color: var(--zy-aid, #9CA3AF);
}

.mood-item--active .mood-label {
	color: #2D9B8F;
	font-weight: 500;
}

/* ── 内容输入 ── */
.textarea-wrap {
	background-color: var(--zy-bg-warm, #FFFFFF);
	border-radius: 24rpx;
	border: 1rpx solid var(--zy-border, #E5E7EB);
	padding: 28rpx;
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
}

.note-textarea {
	width: 100%;
	min-height: 200rpx;
	font-size: 28rpx;
	line-height: 1.7;
	color: var(--zy-title, #1F2937);
	background-color: transparent;
}

.char-count {
	display: block;
	text-align: right;
	margin-top: 12rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
}

/* ── 日期 ── */
.date-row {
	display: flex;
	align-items: center;
	gap: 12rpx;
	padding: 20rpx 28rpx;
	border-radius: 20rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
}

.date-text {
	font-size: 28rpx;
	color: var(--zy-content, #4B5563);
}

/* ── 底部按钮 ── */
.action-area {
	margin-top: 16rpx;
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

/* ── 底部免责 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
