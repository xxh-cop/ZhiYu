<template>
	<view v-if="session" class="practice-bar" role="status" aria-live="polite">
		<view class="practice-bar-main">
			<view class="practice-bar-copy">
				<text class="practice-bar-kicker">本次练习 · {{ kindLabel }}</text>
				<text class="practice-bar-title">{{ session.statusLabel }} · {{ session.minutesBudget }} 分钟</text>
				<text class="practice-bar-sub">{{ hint }}</text>
			</view>
			<view class="practice-bar-actions">
				<button
					v-if="status === 3"
					class="practice-bar-button"
					:disabled="busy"
					@click="$emit('pause-and-leave')"
				>{{ busy ? '正在保存…' : '稍后继续' }}</button>
				<button
					v-if="status === 4 || status === 5 || status === 6"
					class="practice-bar-button"
					:disabled="busy"
					@click="$emit('open-journey')"
				>回看旅程</button>
			</view>
		</view>
		<text v-if="error" class="practice-bar-error" role="alert">{{ error }}</text>
	</view>
</template>

<script setup>
import { computed } from 'vue'
import { practiceKindLabel } from '../utils/practice-session'

const props = defineProps({
	session: { type: Object, default: null },
	busy: { type: Boolean, default: false },
	error: { type: String, default: '' },
})
defineEmits(['pause-and-leave', 'open-journey'])

const status = computed(() => Number(props.session && props.session.status))
const kindLabel = computed(() => practiceKindLabel(props.session && props.session.kind))
const hint = computed(() => {
	if (status.value === 3) return '进度会保存；离开前可以先把这次练习暂停。'
	if (status.value === 4) return '已暂停，随时可以回来继续。'
	if (status.value === 5) return '已完成，可在成长旅程里回看这次练习。'
	if (status.value === 6) return '已保存到成长旅程。'
	if (status.value === 7) return '这次练习已标记为放弃，可以重新开始。'
	return '准备好后，在下方开始这次练习。'
})
</script>

<style scoped>
.practice-bar {
	margin: 0 0 18rpx;
	padding: 18rpx 22rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 18rpx;
	background: var(--zy-card-bg, #fff);
}
.practice-bar-main {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx 20rpx;
}
.practice-bar-copy {
	flex: 1 1 320rpx;
	min-width: 0;
}
.practice-bar-kicker {
	display: block;
	font-size: 20rpx;
	letter-spacing: 2rpx;
	color: var(--zy-primary, #2f7566);
}
.practice-bar-title {
	display: block;
	margin-top: 4rpx;
	font-size: 27rpx;
	font-weight: 600;
	color: var(--zy-title, #233d34);
}
.practice-bar-sub {
	display: block;
	margin-top: 4rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: var(--zy-text, #586e62);
}
.practice-bar-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
}
.practice-bar-button {
	min-height: 44px;
	margin: 0;
	padding: 0 22rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 12rpx;
	background: var(--zy-bg, #f5f7f2);
	color: var(--zy-primary, #2f7566);
	font-size: 23rpx;
	line-height: 44px;
}
.practice-bar-button[disabled] {
	opacity: 0.6;
}
.practice-bar-error {
	display: block;
	margin-top: 10rpx;
	font-size: 22rpx;
	line-height: 1.6;
	color: #a4453f;
}
</style>
