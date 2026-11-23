<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 顶部品牌区 -->
		<view class="brand-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="brand-eyebrow">COMMON ROOM · 互助小组</text>
			<view class="brand-head-row">
				<view class="brand-copy">
					<text class="brand-title">找到同行的伙伴</text>
					<text class="brand-subtitle">不必独自完成，也不用急着变好。</text>
				</view>
				<AccessibleButton class="create-btn" aria-label="创建小组" @click="goCreate">发起小组</AccessibleButton>
			</view>
		</view>

		<!-- 首屏加载骨架 -->
		<StateBlock v-if="loading" type="loading" shape="list" title="正在整理小组…" />

		<!-- 空状态 -->
		<StateBlock v-else-if="groups.length === 0 && !errorMsg" type="empty" art="groups" face="calm" title="还没有小组" description="来创建第一个吧。" action-text="创建小组" @action="goCreate" />

		<!-- 错误状态 -->
		<StateBlock v-else-if="errorMsg && groups.length === 0" type="error" title="小组加载失败" :description="errorMsg" action-text="重试" @action="loadData" />

		<!-- 小组卡片列表 -->
		<view v-else class="card-list">
			<view v-if="errorMsg" class="group-error-row" role="alert">
				<text>{{ errorMsg }}</text>
				<AccessibleButton @click="loadData">重试</AccessibleButton>
			</view>
			<view
				v-for="(group, idx) in groups"
				:key="group.id"
				class="group-card zy-slide-up zy-stagger"
				:style="{ '--stagger-i': idx + 1 }"
			>
				<view class="card-main">
					<!-- 左侧方形字块 -->
					<view class="group-icon">
						<text class="group-icon-text">{{ group.name ? group.name.charAt(0) : '小' }}</text>
					</view>
					<!-- 中间信息 -->
					<view class="card-center">
						<view class="name-row">
							<text class="group-name">{{ group.name }}</text>
							<view v-if="group.myRole === '组长'" class="role-badge">
								<text class="role-text">组长</text>
							</view>
						</view>
						<text v-if="group.description" class="group-desc">{{ group.description }}</text>
						<view class="member-row">
							<wd-icon name="user" size="24rpx" color="#4a5f54" />
							<text class="member-text">{{ group.memberCount }} / {{ group.maxCount }} 人</text>
						</view>
					</view>
					<!-- 右侧加入按钮 -->
					<view v-if="group.joined" class="joined-tag" role="button" tabindex="0" :aria-label="'进入小组 ' + group.name" @click="openGroup(group)" @keydown.enter.prevent="openGroup(group)" @keydown.space.prevent="openGroup(group)">
						<text class="joined-text">进入小组</text>
					</view>
					<view v-else-if="group.memberCount >= group.maxCount" class="full-tag">
						<text class="full-text">已满</text>
					</view>
					<view
						v-else
						class="join-btn"
						role="button"
						tabindex="0"
						:aria-label="'加入小组 ' + group.name"
						:class="{ 'join-btn--loading': joiningId === group.id }"
						@click="handleJoin(group)"
						@keydown.enter.prevent="handleJoin(group)"
						@keydown.space.prevent="handleJoin(group)"
					>
						<text class="join-btn-text">{{ joiningId === group.id ? '...' : '加入' }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>

		<Toast />
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import StateBlock from '../../components/StateBlock.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { useToast } from '../../composables/useToast'

const toast = useToast()

const loading = ref(false)
const errorMsg = ref('')
const groups = ref([])
const joiningId = ref(null)
function openGroup(group){uni.navigateTo({url:'/pages/group/detail?id='+group.id})}

onShow(() => {
	loadData()
})

async function loadData() {
	loading.value = true
	errorMsg.value = ''
	try {
		const data = await http.get('/api/groups')
		groups.value = data || []
	} catch (e) {
		errorMsg.value = '小组加载失败，请稍后重试'
	} finally {
		loading.value = false
	}
}

async function handleJoin(group) {
	if (joiningId.value) return
	joiningId.value = group.id
	try {
		const updated = await http.post(`/api/groups/${group.id}/join`)
		const idx = groups.value.findIndex((g) => g.id === group.id)
		if (idx >= 0) {
			groups.value[idx] = updated
		}
		toast.success('加入成功')
	} catch (e) {
		toast.care((e && e.message) || '加入失败')
	} finally {
		joiningId.value = null
	}
}

function goCreate() {
	uni.navigateTo({ url: '/pages/group/create' })
}
</script>

<style scoped>
.page {
	min-height: 100vh;
	padding: 36rpx 32rpx 140rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #f1f4f1);
}
/* #ifdef MP */
/* 小程序 custom tabBar 不占页面视口，页面自留底部空间（含安全区） */
.page { padding-bottom: calc(112rpx + env(safe-area-inset-bottom)); }
/* #endif */

/* ── 品牌区 ── */
.brand-header {
	padding-bottom: 36rpx;
	margin-bottom: 8rpx;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
}
.brand-eyebrow {
	display: block;
	font-size: var(--zy-text-micro, 22rpx);
	letter-spacing: 2px;
	color: var(--zy-muted, #6e7b75);
	margin-bottom: 22rpx;
}
.brand-head-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 24rpx; }
.brand-copy { min-width: 0; }
.brand-title {
	font-family: var(--zy-font-display, serif);
	font-size: 64rpx;
	font-weight: 500;
	line-height: 1.2;
	color: var(--zy-title, #111816);
	display: block;
}
.brand-subtitle {
	font-size: var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
	display: block;
	margin-top: 10rpx;
	line-height: 1.7;
}
.create-btn {
	flex-shrink: 0;
	min-height: 72rpx;
	margin: 0;
	padding: 0 24rpx;
	border: 1rpx solid var(--zy-brand-700, #0a403b);
	border-radius: 0;
	background: transparent;
	color: var(--zy-brand-700, #0a403b);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 72rpx;
	letter-spacing: 1rpx;
}
.create-btn::after { border: 0; }

/* ── 骨架屏 ── */
.skeleton-card {
	padding: 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
}
.skeleton-body {
	display: flex;
	flex-direction: column;
	gap: 16rpx;
}
.skeleton-title {
	height: 36rpx;
	width: 50%;
}
.skeleton-text {
	height: 28rpx;
	width: 80%;
}

/* ── 空状态 ── */
.empty-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 100rpx 0;
	gap: 16rpx;
}

.empty-icon-wrap {
	width: 120rpx;
	height: 120rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-mint, #e8efe4);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 16rpx;
}

.empty-emoji {
	font-size:var(--zy-text-display, 56rpx);
}

.empty-title {
	font-size:var(--zy-text-body-lg, 30rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
}

.empty-sub {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

.empty-btn {
	margin-top: 16rpx;
	padding: 16rpx 40rpx;
	border-radius: 0;
	background: var(--zy-brand-600, #0e5b53);
	box-shadow: none;
}

.empty-btn-text {
	font-size:var(--zy-text-body, 28rpx);
	color: #FFFFFF;
	font-weight: 500;
}

/* ── 状态 ── */
.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 120rpx 0;
	gap: 24rpx;
}

.error-text {
	font-size:var(--zy-text-body, 28rpx);
	color: var(--zy-danger, var(--zy-care, #a64842));
}

/* ── 卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 0;
	border-top: 0;
}

.group-card {
	padding: 28rpx 0;
	border-radius: 0;
	background-color: transparent;
	border: 0;
	border-bottom: 1rpx solid var(--zy-border, #d3ded8);
	box-shadow: none;
	transition: background-color var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.group-card:active {
	background-color: var(--zy-bg-sunken, #e9efeb);
	box-shadow: none;
}

.card-main {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

/* ── 左侧方形字块 ── */
.group-icon {
	width: 84rpx;
	height: 84rpx;
	border-radius: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	border: 1rpx solid var(--zy-border, #d3ded8);
	background: var(--zy-card-bg, #fff);
}

.group-icon-text {
	font-family: var(--zy-font-display, serif);
	font-size: 38rpx;
	font-weight: 500;
	color: var(--zy-brand-700, #0a403b);
}

/* ── 中间信息 ── */
.card-center {
	flex: 1;
	min-width: 0;
}

.name-row {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.group-name {
	font-family: var(--zy-font-display, serif);
	font-size: 38rpx;
	font-weight: 500;
	color: var(--zy-title, #111816);
	line-height: 1.4;
}

.role-badge {
	padding: 2rpx 8rpx;
	border-radius: 0;
	border: 1rpx solid var(--zy-accent, #8e4a3c);
	background-color: transparent;
}

.role-text {
	font-size: var(--zy-text-micro, 22rpx);
	color: var(--zy-accent, #8e4a3c);
}

.group-desc {
	display: block;
	margin-top: 8rpx;
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 1.6;
	color: var(--zy-muted, #6e7b75);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.member-row {
	display: flex;
	align-items: center;
	gap: 8rpx;
	margin-top: 10rpx;
}

.member-text {
	font-size: var(--zy-text-micro, 22rpx);
	color: var(--zy-muted, #6e7b75);
	letter-spacing: 1rpx;
}

/* ── 右侧按钮 ── */
.join-btn {
	padding: 10rpx 22rpx;
	border-radius: 0;
	border: 1rpx solid var(--zy-brand-700, #0a403b);
	background: transparent;
	flex-shrink: 0;
}

.join-btn:active {
	opacity: .7;
}

.join-btn--loading {
	opacity: 0.6;
}

.join-btn-text {
	font-size: var(--zy-text-caption, 24rpx);
	color: var(--zy-brand-700, #0a403b);
	font-weight: 400;
}

.joined-tag {
	padding: 10rpx 22rpx;
	border-radius: 0;
	border: 1rpx solid var(--zy-border, #d3ded8);
	background-color: transparent;
	flex-shrink: 0;
}

.joined-text {
	font-size: var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
}

.full-tag {
	padding: 10rpx 22rpx;
	border-radius: 0;
	background-color: transparent;
	opacity: 0.6;
	flex-shrink: 0;
}

.full-text {
	font-size: var(--zy-text-caption, 24rpx);
	color: var(--zy-muted, #6e7b75);
}

/* ── 底部说明 ── */
.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: var(--zy-text-micro, 22rpx);
	color: var(--zy-muted, #6e7b75);
	text-align: center;
}
</style>

<style scoped>
.empty-btn:focus-visible,
.joined-tag:focus-visible,
.join-btn:focus-visible,
.create-btn:focus-visible {
	outline: 3px solid var(--zy-brand-600, #0e5b53);
	outline-offset: 3px;
}
.group-error-row {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	margin-bottom: 20rpx;
	padding: 24rpx;
	border: 1rpx solid var(--zy-border, #d3ded8);
	border-radius: 0;
	background: transparent;
	color: var(--zy-text, #42524c);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 1.7;
	text-align: center;
}
.group-error-row button {
	min-height: 72rpx;
	padding: 0 26rpx;
	border: 1rpx solid var(--zy-brand-600, #0e5b53);
	border-radius: 0;
	background: transparent;
	color: var(--zy-brand-700, #0a403b);
	font-size: var(--zy-text-caption, 24rpx);
	line-height: 2.6;
}
.group-error-row button::after { border: 0; }

/* ── v9 列表收口：标题、角色与进入动作 ── */
.name-row { flex-wrap: wrap; align-items: flex-start; }
.group-name { flex: 1 1 100%; min-width: 0; }
.role-badge { flex-shrink: 0; white-space: nowrap; padding: 2rpx 8rpx; margin-top: 6rpx; align-self: flex-start; }
.joined-tag { border: 0; padding: 6rpx 0; background: transparent; color: var(--zy-muted, #6e7b75); }
.joined-text { color: var(--zy-brand-700, #0a403b); }
.join-btn { border: 0; border-bottom: 1rpx solid var(--zy-brand-700, #0a403b); padding: 4rpx 0; }
.full-tag { border: 0; padding: 4rpx 0; color: var(--zy-muted, #6e7b75); }
</style>
