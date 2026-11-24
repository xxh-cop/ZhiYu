<template>
	<!--
		ZyTabBar —— 底部导航（2026-11-18 P0 图标化）。
		线性图标 + 文字；选中 = 窑青 + 朱砂短线（品牌记号保留）；
		「我的」tab 挂通知未读朱砂圆点（复用 useNotifications 节流，未登录静默）。
	-->
	<view class="zy-tabbar" role="tablist" aria-label="主导航">
		<AccessibleButton
			v-for="item in items"
			:key="item.path"
			class="zy-tabbar-item"
			:class="{ active: item.path === current }"
			role="tab"
			:aria-selected="item.path === current"
			:aria-label="item.text + (item.path === PROFILE_PATH && unread > 0 ? '，有未读消息' : '')"
			@click="go(item.path)"
		>
			<view class="zy-tabbar-iconwrap">
				<ZyIcon :name="item.icon" :size="40" :tone="item.path === current ? 'brand' : 'muted'" />
				<view v-if="item.path === PROFILE_PATH && unread > 0" class="zy-tabbar-dot" aria-hidden="true"></view>
			</view>
			<text class="zy-tabbar-text">{{ item.text }}</text>
			<view v-if="item.path === current" class="zy-tabbar-mark" aria-hidden="true"></view>
		</AccessibleButton>
	</view>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import AccessibleButton from './AccessibleButton.vue'
import ZyIcon from './ZyIcon.vue'
import { useNotifications } from '../composables/useNotifications'
import { TOKEN_KEY } from '../utils/config'
import { TAB_ITEMS, currentTabRoute, switchTabTo } from '../utils/tabbar'

const ICON_BY_PATH = {
	'/pages/home/index': 'home',
	'/pages/work/list': 'community',
	'/pages/start/index': 'craft',
	'/pages/group/list': 'group',
	'/pages/profile/index': 'user',
}
const PROFILE_PATH = '/pages/profile/index'

const items = TAB_ITEMS.map((item) => ({ ...item, icon: ICON_BY_PATH[item.path] || 'arrow' }))
// H5 端本组件挂在独立 body 级宿主（无 pinia/mixin）：
// 选中态靠 main.js 每次页面 onShow 广播的 zy:tabshow 事件驱动；登录态直接读 storage。
const current = ref(currentTabRoute())
const { state: noticeState, refreshUnread } = useNotifications()
const unread = computed(() => noticeState.unread)

function refresh() {
	try {
		if (uni.getStorageSync(TOKEN_KEY)) refreshUnread()
	} catch (e) { /* 静默 */ }
}
function onTabShow(route) {
	if (route) current.value = route
	refresh()
}
onMounted(() => {
	uni.$on('zy:tabshow', onTabShow)
	refresh()
})
onBeforeUnmount(() => uni.$off('zy:tabshow', onTabShow))

function go(path) {
	if (path !== current.value) switchTabTo(path)
}
</script>

<style scoped>
.zy-tabbar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 40;
	display: flex;
	height: calc(112rpx + env(safe-area-inset-bottom));
	padding-bottom: env(safe-area-inset-bottom);
	background: var(--zy-card);
	border-top: 1rpx solid var(--zy-border);
	box-sizing: border-box;
}
.zy-tabbar-item {
	position: relative;
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 4rpx;
	min-height: 112rpx;
	margin: 0;
	padding: 0;
	background: transparent;
	color: var(--zy-muted);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 1.3;
}
.zy-tabbar-item::after { border: 0; }
.zy-tabbar-item.active { color: var(--zy-brand-600); font-weight: 600; }
/* v8：选中项图标落在青绿浅底「胶囊」里，比一根细线醒目得多 */
.zy-tabbar-iconwrap { position: relative; display: flex; align-items: center; justify-content: center; width: 96rpx; height: 52rpx; border-radius: 999rpx; transition: background-color 200ms ease; }
.zy-tabbar-item.active .zy-tabbar-iconwrap { background: var(--zy-brand-100, #dde6f6); }
.zy-tabbar { box-shadow: 0 -4rpx 18rpx rgba(74, 56, 26, 0.06); }
.zy-tabbar-dot {
	position: absolute;
	top: -2rpx;
	right: -8rpx;
	width: 14rpx;
	height: 14rpx;
	border-radius: 50%;
	background: var(--zy-care);
	border: 2rpx solid var(--zy-card);
}
.zy-tabbar-mark { display: none; }
</style>
