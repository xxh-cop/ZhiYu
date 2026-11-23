<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import AccessibleButton from '@/components/AccessibleButton.vue'
import StateBlock from '@/components/StateBlock.vue'

const id = ref('')
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const selected = ref('')
const active = computed(() => rows.value.find((r) => r.id === selected.value))
const completed = computed(() => rows.value.filter((r) => Number(r.completed) === 1).length)

async function load() {
	loading.value = true
	error.value = ''
	try {
		rows.value = await request({ url: '/api/growth/programs/' + id.value + '/lessons' })
		if (!active.value) selected.value = rows.value[0]?.id || ''
	} catch (e) {
		error.value = '课程加载失败，请重试'
	} finally {
		loading.value = false
	}
}

async function mark() {
	if (saving.value || !active.value) return
	const lesson = active.value
	saving.value = true
	try {
		await request({
			url: '/api/growth/programs/' + id.value + '/lessons/' + lesson.id + '/progress',
			method: 'PUT',
			data: { completed: Number(lesson.completed) !== 1 },
		})
		await load()
	} catch (e) {
		error.value = '课程进度保存失败，请重试'
	} finally {
		saving.value = false
	}
}

function practice() {
	if (active.value?.project_id) uni.navigateTo({ url: '/pages/craft/detail?id=' + active.value.project_id })
}

onLoad((options) => {
	id.value = options.id || ''
	if (!/^\d+$/.test(id.value)) {
		error.value = '课程编号无效'
		return
	}
	load()
})
</script>

<template>
	<view :class="zyThemeClass" class="growth-page course-page">
		<view class="growth-hero">
			<text class="growth-eyebrow">SEQUENTIAL LEARNING · 循序学习</text>
			<text class="growth-title">一课一针，慢慢学</text>
			<text class="growth-subtitle">跟随自己的节奏，每次学会一点点。</text>
		</view>

		<StateBlock
			v-if="error"
			type="error"
			title="课程暂时没打开"
			:description="error"
			action-text="重新加载"
			:action-loading="loading"
			@action="load"
		/>
		<StateBlock v-else-if="loading && !rows.length" type="loading" shape="list" title="正在读取课程…" />

		<template v-else>
			<view class="growth-card">
				<text class="growth-card-title">学习进度 · {{ completed }} / {{ rows.length }}</text>
				<text class="growth-muted">完成状态由你主动确认，不代表观看时长或能力评定。</text>

				<view v-if="rows.length" class="lesson-list" aria-label="课节列表">
					<view
						v-for="(r, index) in rows"
						:key="r.id"
						class="lesson-row"
						:class="{ selected: selected === r.id }"
						role="button"
						tabindex="0"
						:aria-pressed="selected === r.id"
						@click="selected = r.id"
						@keydown.enter.prevent="selected = r.id"
						@keydown.space.prevent="selected = r.id"
					>
						<text class="lesson-index">{{ String(index + 1).padStart(2, '0') }}</text>
						<view class="lesson-main">
							<text class="lesson-chapter">{{ r.chapter }} · {{ r.position }}</text>
							<text class="lesson-title">{{ r.title }}</text>
							<text class="lesson-state">{{ Number(r.completed) === 1 ? '已完成' : '待学习' }}</text>
						</view>
					</view>
				</view>
				<text v-else class="growth-muted">带领者尚未发布课节。</text>
			</view>

			<view v-if="active" class="growth-card lesson-detail">
				<text class="lesson-detail-kicker">正在读 · {{ active.chapter }}</text>
				<text class="lesson-detail-title">{{ active.title }}</text>
				<text class="lesson-content">{{ active.content }}</text>
				<view class="lesson-actions">
					<AccessibleButton v-if="active.project_id" class="growth-button secondary" @click="practice">
						查看手作步骤与教学视频
					</AccessibleButton>
					<AccessibleButton class="growth-button" :disabled="saving" @click="mark">
						{{ saving ? '正在保存…' : Number(active.completed) === 1 ? '改为继续学习' : '我已完成本课节' }}
					</AccessibleButton>
				</view>
			</view>
		</template>

		<text class="growth-foot">本系统不能替代专业心理咨询</text>
	</view>
</template>

<style scoped>
.course-page .lesson-list {
	margin-top: 24rpx;
	border-top: 1rpx solid var(--zy-border);
}
.lesson-row {
	display: flex;
	gap: 18rpx;
	padding: 24rpx 0 24rpx 16rpx;
	margin-left: -16rpx;
	border-bottom: 1rpx solid var(--zy-border);
	border-left: 3rpx solid transparent;
}
.lesson-row.selected {
	border-left-color: var(--zy-care, #a64842);
}
.lesson-index {
	width: 44rpx;
	flex: none;
	padding-top: 4rpx;
	font-family: var(--zy-font-display, serif);
	font-size: 28rpx;
	color: var(--zy-text-muted);
}
.lesson-main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}
.lesson-chapter {
	font-size: 24rpx;
	letter-spacing: 1rpx;
	color: var(--zy-text-muted);
}
.lesson-title {
	font-family: var(--zy-font-display, serif);
	font-size: 32rpx;
	line-height: 1.5;
	color: var(--zy-text);
}
.lesson-state {
	font-size: 24rpx;
	color: var(--zy-text-muted);
}
.lesson-row.selected .lesson-state {
	color: var(--zy-care, #a64842);
}
.lesson-detail-kicker {
	font-size: 24rpx;
	letter-spacing: 1rpx;
	color: var(--zy-text-muted);
}
.lesson-detail-title {
	display: block;
	margin-top: 8rpx;
	font-family: var(--zy-font-display, serif);
	font-size: 40rpx;
	line-height: 1.4;
	color: var(--zy-text);
}
.lesson-content {
	display: block;
	white-space: pre-wrap;
	line-height: 1.9;
	font-size: var(--zy-text-body, 28rpx);
	margin: 24rpx 0 0;
}
.lesson-actions {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	margin-top: 32rpx;
}
.lesson-row:focus-visible {
	outline: 3px solid var(--zy-primary, var(--zy-brand-600, #217e76));
	outline-offset: 3px;
}
</style>
