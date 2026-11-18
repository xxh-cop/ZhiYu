<template>
	<view class="zy-tabbar" :class="{ 'zy-tabbar--static': reducedMotion }">
		<view class="zy-tabbar-row" role="tablist" aria-label="主导航">
			<view
				v-for="item in items"
				:key="item.path"
				class="zy-tab-item"
				:class="{ 'is-active': active === item.path }"
				role="tab"
				:aria-selected="active === item.path"
				:aria-label="item.text"
				@click="go(item.path)"
			>
				<view class="zy-tab-mark" aria-hidden="true"></view>
				<text class="zy-tab-text">{{ item.text }}</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useInteraction } from '@/composables/useInteraction'
import { TAB_ITEMS, currentTabRoute, switchTabTo } from '@/utils/tabbar'

const props = defineProps({
	active: { type: String, default: '' },
})

const { reducedMotion } = useInteraction()
const items = TAB_ITEMS
const route = ref(props.active || currentTabRoute())
const active = computed(() => (props.active ? props.active : route.value))

function onTabShow(value) {
	route.value = value || currentTabRoute()
}

function go(path) {
	if (path === active.value) return
	switchTabTo(path)
}

onMounted(() => {
	route.value = currentTabRoute()
	uni.$on('zy:tabshow', onTabShow)
})
onBeforeUnmount(() => {
	uni.$off('zy:tabshow', onTabShow)
})
defineExpose({ refresh: () => { route.value = currentTabRoute() } })
</script>

<style scoped>
.zy-tabbar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 990;
	box-sizing: border-box;
	height: calc(104rpx + constant(safe-area-inset-bottom));
	height: calc(104rpx + env(safe-area-inset-bottom));
	padding-bottom: constant(safe-area-inset-bottom);
	padding-bottom: env(safe-area-inset-bottom);
	background: #ffffff;
	border-top: 1rpx solid var(--zy-border, #D3DED8);
}
.zy-tabbar::after {
	content: '';
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	height: constant(safe-area-inset-bottom);
	height: env(safe-area-inset-bottom);
	background-color: #ffffff;
}
.zy-tabbar-row {
	position: relative;
	display: flex;
	align-items: stretch;
	height: 104rpx;
}
.zy-tab-item {
	position: relative;
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 8rpx;
	height: 100%;
}
.zy-tab-mark {
	width: 26rpx;
	height: 2rpx;
	background: transparent;
	transition: background var(--zy-duration-base) var(--zy-ease-smooth);
}
.zy-tab-text {
	font-size: 22rpx;
	line-height: 1.4;
	color: var(--zy-muted, #6E7B75);
	letter-spacing: 1rpx;
}
.zy-tab-item.is-active .zy-tab-text {
	color: var(--zy-title, #111816);
	font-weight: 500;
}
.zy-tab-item.is-active .zy-tab-mark {
	background: var(--zy-care, #C4402F);
}
.zy-tabbar--static .zy-tab-mark {
	transition: none;
}
</style>
