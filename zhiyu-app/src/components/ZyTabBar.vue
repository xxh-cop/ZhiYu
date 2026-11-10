<template>
	<view class="zy-tabbar" :class="{ 'zy-tabbar--warm': warm, 'zy-tabbar--static': reducedMotion }">
		<view class="zy-tabbar-bar" :style="{ backgroundImage: barBackground }"></view>
		<view class="zy-tabbar-row" role="tablist" aria-label="主导航">
			<block v-for="item in items" :key="item.path">
				<view
					v-if="!item.center"
					class="zy-tab-item"
					:class="{ 'is-active': active === item.path }"
					role="tab"
					:aria-selected="active === item.path"
					:aria-label="item.text"
					@click="go(item.path)"
				>
					<image class="zy-tab-icon" :src="active === item.path ? item.activeIcon : item.icon" mode="aspectFit" />
					<text class="zy-tab-text">{{ item.text }}</text>
					<view class="zy-tab-dot" aria-hidden="true"></view>
				</view>
				<view v-else class="zy-tab-center">
					<button
						class="zy-center-btn"
						:class="{ 'is-active': active === item.path }"
						aria-label="开始"
						@click="goCenter(item.path)"
					>
						<text class="zy-center-plus" aria-hidden="true">＋</text>
					</button>
				</view>
			</block>
		</view>
	</view>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useInteraction } from '@/composables/useInteraction'
import { CENTER_PATH, TAB_ITEMS, currentTabRoute, switchTabTo } from '@/utils/tabbar'

const props = defineProps({
	active: { type: String, default: '' },
})

const { reducedMotion } = useInteraction()
const items = TAB_ITEMS
const route = ref(props.active || currentTabRoute())
const warm = ref(false)

const active = computed(() => (props.active ? props.active : route.value))

/* 缺口式导航条：顶边在中间下凹 44rpx，中央按钮嵌在缺口里；SVG 背景随主题切换纸色 */
const LIGHT_BAR = 'data:image/svg+xml,' + encodeURIComponent(
	'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 750 112" preserveAspectRatio="none"><path d="M0 0h309c14 0 18 6 22 16 8 22 24 32 44 32s36-10 44-32c4-10 8-16 22-16h309v112H0z" fill="#FFFFFF"/></svg>'
)
const WARM_BAR = 'data:image/svg+xml,' + encodeURIComponent(
	'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 750 112" preserveAspectRatio="none"><path d="M0 0h309c14 0 18 6 22 16 8 22 24 32 44 32s36-10 44-32c4-10 8-16 22-16h309v112H0z" fill="#FFFDF7"/></svg>'
)
const barBackground = computed(() => `url("${warm.value ? WARM_BAR : LIGHT_BAR}")`)

function refresh() {
	try {
		warm.value = uni.getStorageSync('zy-theme') === 'warm'
	} catch (e) {
		warm.value = false
	}
	route.value = currentTabRoute()
}

function onTabShow(value) {
	route.value = value || currentTabRoute()
}

function go(path) {
	if (path === active.value) return
	switchTabTo(path)
}

function goCenter(path) {
	// #ifdef MP
	try {
		uni.vibrateShort({ type: 'light' })
	} catch (e) {
		/* 部分基础库不支持轻震动 */
	}
	// #endif
	go(path)
}

onMounted(() => {
	refresh()
	uni.$on('zy:tabshow', onTabShow)
	uni.$on('zy:themechange', refresh)
})
onBeforeUnmount(() => {
	uni.$off('zy:tabshow', onTabShow)
	uni.$off('zy:themechange', refresh)
})
defineExpose({ refresh })
</script>

<style scoped>
.zy-tabbar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 990;
	height: calc(112rpx + constant(safe-area-inset-bottom));
	height: calc(112rpx + env(safe-area-inset-bottom));
	padding-bottom: constant(safe-area-inset-bottom);
	padding-bottom: env(safe-area-inset-bottom);
}
.zy-tabbar-bar {
	position: absolute;
	top: 0;
	right: 0;
	bottom: 0;
	left: 0;
	background-repeat: no-repeat;
	background-size: 100% 100%;
	box-shadow: 0 -6rpx 24rpx rgba(51, 48, 42, 0.06);
}
.zy-tabbar-row {
	position: relative;
	display: flex;
	align-items: flex-end;
	height: 112rpx;
}
.zy-tab-item {
	position: relative;
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: flex-end;
	gap: 4rpx;
	height: 100%;
	padding-bottom: 12rpx;
}
.zy-tab-icon {
	width: 44rpx;
	height: 44rpx;
}
.zy-tab-text {
	font-size: var(--zy-text-micro);
	line-height: 1.4;
	color: var(--zy-text);
}
.zy-tab-dot {
	width: 40rpx;
	height: 4rpx;
	border-radius: 999rpx;
	background: transparent;
	transition: background var(--zy-duration-base) var(--zy-ease-smooth);
}
.zy-tab-item.is-active .zy-tab-text {
	font-weight: 600;
	color: var(--zy-brand-700);
}
.zy-tab-item.is-active .zy-tab-dot {
	background: var(--zy-brand-600);
}
.zy-tab-center {
	flex: 0 0 160rpx;
	display: flex;
	justify-content: center;
	height: 100%;
}
.zy-center-btn {
	position: absolute;
	top: -30rpx;
	width: 112rpx;
	height: 112rpx;
	min-width: 112rpx;
	min-height: 112rpx;
	padding: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 0;
	border-radius: 50%;
	background: linear-gradient(135deg, var(--zy-brand-600), var(--zy-brand-500));
	box-shadow: 0 10rpx 28rpx rgba(33, 126, 118, 0.35), inset 0 2rpx 0 rgba(255, 255, 255, 0.28);
	transition: transform var(--zy-duration-fast) var(--zy-ease-out);
}
.zy-center-btn::after {
	border: 0;
}
.zy-center-btn:active {
	transform: scale(0.92);
}
.zy-center-plus {
	font-size: 52rpx;
	line-height: 1;
	color: #ffffff;
	transition: transform var(--zy-duration-base) var(--zy-ease-smooth);
}
.zy-center-btn.is-active .zy-center-plus {
	transform: rotate(45deg);
}
.zy-tabbar--static .zy-tab-dot,
.zy-tabbar--static .zy-center-btn,
.zy-tabbar--static .zy-center-plus {
	transition: none;
}
</style>
