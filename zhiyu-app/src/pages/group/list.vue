<template>
	<view :class="zyThemeClass" class="page zy-page-enter">
		<!-- 顶部品牌区 -->
		<view class="brand-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="brand-title">互助小组</text>
			<text class="brand-subtitle">找到同行的伙伴</text>
		</view>

		<!-- 首屏加载骨架 -->
		<StateBlock v-if="loading" type="loading" title="正在整理小组…" />

		<!-- 空状态 -->
		<StateBlock v-else-if="groups.length === 0 && !errorMsg" type="empty" face="calm" title="还没有小组" description="来创建第一个吧。" action-text="创建小组" @action="goCreate" />

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
					<!-- 左侧圆形图标 -->
					<view class="group-icon" :style="iconGradient(group.name)">
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

		<!-- 悬浮创建按钮 -->
		<view class="fab" role="button" tabindex="0" aria-label="创建小组" @click="goCreate" @keydown.enter.prevent="goCreate" @keydown.space.prevent="goCreate">
			<wd-icon name="add" size="44rpx" color="#FFFFFF" />
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

const GRADIENTS = [
	'linear-gradient(135deg, #217e76, #5bc4a8)',
	'linear-gradient(135deg, #8a6d1f, #FFD08A)',
	'linear-gradient(135deg, #a64842, #d4736a)',
	'linear-gradient(135deg, #5bc4a8, #8adcc6)',
	'linear-gradient(135deg, #FF9B6B, #FFC499)',
]

function iconGradient(name) {
	if (!name) return GRADIENTS[0]
	const hash = name.charCodeAt(0) % GRADIENTS.length
	return GRADIENTS[hash]
}

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
	padding: 32rpx 24rpx 140rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #ffffff);
}
/* #ifdef MP */
/* 小程序 custom tabBar 不占页面视口，页面自留底部空间（含安全区） */
.page { padding-bottom: calc(112rpx + env(safe-area-inset-bottom)); }
/* #endif */

/* ── 品牌区 ── */
.brand-header {
	margin-bottom: 32rpx;
}

.brand-title {
	font-family: var(--zy-serif);
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
	display: block;
}

.brand-subtitle {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
	display: block;
	margin-top: 8rpx;
}

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
	padding: 16rpx 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, var(--zy-brand-600, #217e76));
	box-shadow: var(--zy-shadow-md);
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
	gap: 20rpx;
}

.group-card {
	padding: 24rpx 28rpx;
	border-radius: var(--zy-radius-lg, 32rpx);
	background-color: var(--zy-card-bg, #FFFFFF);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	box-shadow: var(--zy-shadow-sm);
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out), box-shadow var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.group-card:active {
	transform: translateY(-2rpx);
	box-shadow: var(--zy-shadow-md);
}

.card-main {
	display: flex;
	align-items: center;
	gap: 20rpx;
}

/* ── 左侧圆形图标 ── */
.group-icon {
	width: 72rpx;
	height: 72rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.group-icon-text {
	font-size:var(--zy-text-title, 32rpx);
	font-weight: 600;
	color: #FFFFFF;
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
	font-size:var(--zy-text-body-lg, 30rpx);
	font-weight: 500;
	color: var(--zy-title, #233d34);
	line-height: 1.4;
}

.role-badge {
	padding: 4rpx 16rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg-tint-warm, #f5edde);
}

.role-text {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-warning, var(--zy-accent, #7a5c12));
}

.group-desc {
	display: block;
	margin-top: 8rpx;
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.5;
	color: var(--zy-content, #4a5f54);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.member-row {
	display: flex;
	align-items: center;
	gap: 8rpx;
	margin-top: 8rpx;
}

.member-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

/* ── 右侧按钮 ── */
.join-btn {
	padding: 12rpx 32rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-brand-600, var(--zy-brand-600, #217e76));
	flex-shrink: 0;
	transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}

.join-btn:active {
	transform: scale(0.96);
}

.join-btn--loading {
	opacity: 0.6;
}

.join-btn-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: #FFFFFF;
	font-weight: 500;
}

.joined-tag {
	padding: 12rpx 24rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #ffffff);
	flex-shrink: 0;
}

.joined-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

.full-tag {
	padding: 12rpx 24rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #ffffff);
	opacity: 0.6;
	flex-shrink: 0;
}

.full-text {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-aid, #4a5f54);
}

/* ── 悬浮创建按钮 ── */
.fab {
	position: fixed;
	right: 40rpx;
	bottom: 160rpx;
	width: 100rpx;
	height: 100rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, var(--zy-brand-600, #217e76)), var(--zy-primary-light, var(--zy-brand-300, #5bc4a8)));
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: var(--zy-shadow-lg);
	z-index: 100;
	transition: transform var(--zy-duration-base, 200ms) var(--zy-ease-out, ease-out);
}

.fab:active {
	transform: scale(0.92);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-aid, #4a5f54);
	text-align: center;
}
</style>

<style scoped>
.empty-btn:focus-visible,
.joined-tag:focus-visible,
.join-btn:focus-visible,
.fab:focus-visible {
	outline: 3px solid var(--zy-primary, var(--zy-brand-600, #217e76));
	outline-offset: 3px;
}
.group-error-row {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;
	margin-bottom: 20rpx;
	padding: 24rpx;
	border: 1rpx dashed var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 24rpx);
	background: var(--zy-card-bg, #fff);
	color: var(--zy-content, #4a5f54);
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.7;
	text-align: center;
}
.group-error-row button {
	min-height: 72rpx;
	padding: 0 26rpx;
	border: 1rpx solid var(--zy-primary, var(--zy-brand-600, #217e76));
	border-radius: var(--zy-radius-full, 9999rpx);
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, var(--zy-brand-600, #217e76));
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 2.6;
}
.group-error-row button::after { border: 0; }
</style>
