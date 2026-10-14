<template>
	<view class="page zy-page-enter">
		<!-- 顶部品牌区 -->
		<view class="brand-header zy-slide-up zy-stagger" style="--stagger-i: 0">
			<text class="brand-title">互助小组</text>
			<text class="brand-subtitle">找到同行的伙伴</text>
		</view>

		<!-- 骨架屏 -->
		<view v-if="loading" class="card-list">
			<view v-for="i in 3" :key="'sk-'+i" class="skeleton-card">
				<view class="skeleton-body">
					<view class="skeleton-title zy-skeleton"></view>
					<view class="skeleton-text zy-skeleton"></view>
				</view>
			</view>
		</view>

		<!-- 空状态 -->
		<view v-else-if="groups.length === 0 && !errorMsg" class="empty-box zy-slide-up">
			<view class="empty-icon-wrap">
				<text class="empty-emoji">🌿</text>
			</view>
			<text class="empty-title">还没有小组</text>
			<text class="empty-sub">来创建第一个吧</text>
			<view class="empty-btn" @click="goCreate">
				<text class="empty-btn-text">创建小组</text>
			</view>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<wd-button type="primary" plain size="small" @click="loadData">重试</wd-button>
		</view>

		<!-- 小组卡片列表 -->
		<view v-else class="card-list">
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
							<wd-icon name="user" size="24rpx" color="#718478" />
							<text class="member-text">{{ group.memberCount }} / {{ group.maxCount }} 人</text>
						</view>
					</view>
					<!-- 右侧加入按钮 -->
					<view v-if="group.joined" class="joined-tag">
						<text class="joined-text">已加入</text>
					</view>
					<view v-else-if="group.memberCount >= group.maxCount" class="full-tag">
						<text class="full-text">已满</text>
					</view>
					<view
						v-else
						class="join-btn"
						:class="{ 'join-btn--loading': joiningId === group.id }"
						@click="handleJoin(group)"
					>
						<text class="join-btn-text">{{ joiningId === group.id ? '...' : '加入' }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 悬浮创建按钮 -->
		<view class="fab" @click="goCreate">
			<wd-icon name="add" size="44rpx" color="#FFFFFF" />
		</view>

		<!-- 底部免责声明 -->
		<text class="footnote">本系统不能替代专业心理咨询</text>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

const loading = ref(false)
const errorMsg = ref('')
const groups = ref([])
const joiningId = ref(null)

const GRADIENTS = [
	'linear-gradient(135deg, #2f7566, #82ad9b)',
	'linear-gradient(135deg, #bb914d, #FFD08A)',
	'linear-gradient(135deg, #FF6B6B, #FF9B9B)',
	'linear-gradient(135deg, #82ad9b, #8DD9C4)',
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
		errorMsg.value = (e && e.message) || '加载失败，请稍后重试'
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
		uni.showToast({ title: '加入成功', icon: 'success' })
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '加入失败', icon: 'none' })
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
	background-color: var(--zy-bg, #f5f7f2);
}

/* ── 品牌区 ── */
.brand-header {
	margin-bottom: 32rpx;
}

.brand-title {
	font-family: var(--zy-serif);
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
	display: block;
}

.brand-subtitle {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
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
	font-size: 60rpx;
}

.empty-title {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #233d34);
}

.empty-sub {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
}

.empty-btn {
	margin-top: 16rpx;
	padding: 16rpx 48rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
	box-shadow: var(--zy-shadow-md);
}

.empty-btn-text {
	font-size: 28rpx;
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
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
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
	font-size: 32rpx;
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
	font-size: 30rpx;
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
	font-size: 22rpx;
	color: var(--zy-warning, #bb914d);
}

.group-desc {
	display: block;
	margin-top: 8rpx;
	font-size: 26rpx;
	line-height: 1.5;
	color: var(--zy-content, #586e62);
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
	font-size: 24rpx;
	color: var(--zy-aid, #718478);
}

/* ── 右侧按钮 ── */
.join-btn {
	padding: 12rpx 32rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
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
	font-size: 26rpx;
	color: #FFFFFF;
	font-weight: 500;
}

.joined-tag {
	padding: 12rpx 24rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #f5f7f2);
	flex-shrink: 0;
}

.joined-text {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
}

.full-tag {
	padding: 12rpx 24rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background-color: var(--zy-bg, #f5f7f2);
	opacity: 0.6;
	flex-shrink: 0;
}

.full-text {
	font-size: 26rpx;
	color: var(--zy-aid, #718478);
}

/* ── 悬浮创建按钮 ── */
.fab {
	position: fixed;
	right: 40rpx;
	bottom: 160rpx;
	width: 100rpx;
	height: 100rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	background: linear-gradient(135deg, var(--zy-primary, #2f7566), var(--zy-primary-light, #82ad9b));
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
	font-size: 22rpx;
	color: var(--zy-aid, #718478);
	text-align: center;
}
</style>
