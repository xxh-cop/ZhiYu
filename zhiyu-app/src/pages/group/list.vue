<template>
	<view class="page zy-page-enter">
		<!-- 顶部标题 + 创建入口 -->
		<view class="header">
			<text class="page-title">互助小组</text>
			<wd-button size="small" plain @click="goCreate">+ 创建小组</wd-button>
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
		<view v-else-if="groups.length === 0 && !errorMsg" class="status-box">
			<text class="empty-emoji">🌿</text>
			<text class="status-text">还没有小组，来创建第一个吧</text>
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
				class="group-card"
				:style="{ '--stagger-i': idx }"
			>
				<view class="card-header">
					<text class="group-name">{{ group.name }}</text>
					<view v-if="group.myRole === '组长'" class="role-badge">
						<text class="role-text">组长</text>
					</view>
				</view>

				<text v-if="group.description" class="group-desc">{{ group.description }}</text>

				<view class="card-footer">
					<view class="member-info">
						<wd-icon name="user" size="28rpx" color="#9CA3AF" />
						<text class="member-text">{{ group.memberCount }} / {{ group.maxCount }}</text>
					</view>

					<!-- 已加入 -->
					<view v-if="group.joined" class="joined-tag">
						<text class="joined-text">已加入</text>
					</view>
					<!-- 已满 -->
					<view v-else-if="group.memberCount >= group.maxCount" class="full-tag">
						<text class="full-text">已满</text>
					</view>
					<!-- 加入 -->
					<wd-button
						v-else
						type="primary"
						size="small"
						:loading="joiningId === group.id"
						@click="handleJoin(group)"
					>加入</wd-button>
				</view>
			</view>
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
		// 用返回的 GroupVO 替换列表中对应项
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
	padding: 32rpx 24rpx 120rpx;
	box-sizing: border-box;
	background-color: var(--zy-bg, #F8F9FB);
}

.header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 32rpx;
}

.page-title {
	font-family: var(--zy-serif);
	font-size: 44rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
}

/* ── 骨架屏 ── */
.skeleton-card {
	padding: 28rpx;
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
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

/* ── 状态 ── */
.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 120rpx 0;
	gap: 24rpx;
}

.empty-emoji {
	font-size: 80rpx;
}

.status-text {
	font-size: 28rpx;
	color: var(--zy-aid, #9CA3AF);
}

.error-text {
	font-size: 28rpx;
	color: var(--zy-danger, #FF6B6B);
}

/* ── 卡片列表 ── */
.card-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.group-card {
	padding: 28rpx;
	border-radius: 32rpx;
	background-color: var(--zy-bg-warm, #FFFFFF);
	border: 1rpx solid var(--zy-border, #E5E7EB);
	box-shadow: 0 4rpx 20rpx rgba(31, 41, 55, 0.05);
	transition: transform 0.15s ease;
	animation: cardEnter 0.35s ease both;
	animation-delay: calc(var(--stagger-i, 0) * 80ms);
}

.group-card:active {
	transform: scale(0.98);
}

@keyframes cardEnter {
	from { opacity: 0; transform: translateY(20rpx); }
	to { opacity: 1; transform: translateY(0); }
}

.card-header {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.group-name {
	font-size: 30rpx;
	font-weight: 500;
	color: var(--zy-title, #1F2937);
	line-height: 1.4;
}

.role-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: var(--zy-bg-tint-warm, #FFF8EE);
}

.role-text {
	font-size: 22rpx;
	color: #FFB547;
}

.group-desc {
	display: block;
	margin-top: 12rpx;
	font-size: 26rpx;
	line-height: 1.6;
	color: var(--zy-content, #4B5563);
}

.card-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 24rpx;
}

.member-info {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.member-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.joined-tag {
	padding: 8rpx 24rpx;
	border-radius: 32rpx;
	background-color: var(--zy-bg, #F8F9FB);
}

.joined-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.full-tag {
	padding: 8rpx 24rpx;
	border-radius: 32rpx;
	background-color: var(--zy-bg, #F8F9FB);
	opacity: 0.6;
}

.full-text {
	font-size: 26rpx;
	color: var(--zy-aid, #9CA3AF);
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 22rpx;
	color: var(--zy-aid, #9CA3AF);
	text-align: center;
}
</style>
