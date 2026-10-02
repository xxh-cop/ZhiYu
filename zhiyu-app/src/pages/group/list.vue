<template>
	<view class="page">
		<!-- 顶部标题 + 创建入口 -->
		<view class="header">
			<text class="page-title">互助小组</text>
			<view class="create-btn" @click="goCreate">
				<text class="create-text">+ 创建小组</text>
			</view>
		</view>

		<!-- 加载中 -->
		<view v-if="loading" class="status-box">
			<text class="status-text">正在加载…</text>
		</view>

		<!-- 空状态 -->
		<view v-else-if="groups.length === 0 && !errorMsg" class="status-box">
			<text class="status-text">还没有小组，来创建第一个吧</text>
		</view>

		<!-- 错误状态 -->
		<view v-else-if="errorMsg" class="status-box">
			<text class="error-text">{{ errorMsg }}</text>
			<view class="retry-btn" @click="loadData">
				<text class="retry-text">重试</text>
			</view>
		</view>

		<!-- 小组卡片列表 -->
		<view v-else class="card-list">
			<view
				v-for="group in groups"
				:key="group.id"
				class="group-card"
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
						<text class="member-text">👥 {{ group.memberCount }} / {{ group.maxCount }}</text>
					</view>

					<!-- 已加入 -->
					<view v-if="group.joined" class="joined-tag">
						<text class="joined-text">已加入</text>
					</view>
					<!-- 已满 -->
					<view v-else-if="group.memberCount >= group.maxCount" class="full-btn">
						<text class="full-text">已满</text>
					</view>
					<!-- 加入 -->
					<view
						v-else
						class="join-btn"
						:class="{ 'btn-loading': joiningId === group.id }"
						@click="handleJoin(group)"
					>
						<text class="join-text">{{ joiningId === group.id ? '...' : '加入' }}</text>
					</view>
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
	padding: 32rpx 24rpx 80rpx;
	box-sizing: border-box;
	background-color: #f7f4ef;
}

.header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 24rpx;
}

.page-title {
	font-size: 44rpx;
	font-weight: 600;
	color: #3d3a34;
}

.create-btn {
	padding: 8rpx 24rpx;
	border: 2rpx solid #8b6f47;
	border-radius: 8px;
}

.create-text {
	font-size: 26rpx;
	color: #8b6f47;
}

.status-box {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 80rpx 0;
	gap: 24rpx;
}

.status-text {
	font-size: 28rpx;
	color: #9c8f7d;
}

.error-text {
	font-size: 28rpx;
	color: #b85450;
}

.retry-btn {
	padding: 12rpx 40rpx;
	border-radius: 8px;
	background-color: #8b6f47;
}

.retry-text {
	font-size: 26rpx;
	color: #fffdfa;
}

.card-list {
	display: flex;
	flex-direction: column;
	gap: 24rpx;
}

.group-card {
	padding: 24rpx;
	border-radius: 12px;
	background-color: #fffdfa;
	border: 2rpx solid #e6ddcf;
	box-shadow: 0 2px 8px rgba(139, 111, 71, 0.08);
}

.card-header {
	display: flex;
	align-items: center;
	gap: 12rpx;
}

.group-name {
	font-size: 34rpx;
	font-weight: 600;
	color: #3d3a34;
}

.role-badge {
	padding: 4rpx 16rpx;
	border-radius: 999rpx;
	background-color: rgba(200, 155, 110, 0.15);
}

.role-text {
	font-size: 22rpx;
	color: #c89b6e;
}

.group-desc {
	display: block;
	margin-top: 12rpx;
	font-size: 28rpx;
	line-height: 1.6;
	color: #5a534a;
}

.card-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 20rpx;
}

.member-info {
	display: flex;
	align-items: center;
}

.member-text {
	font-size: 26rpx;
	color: #8a8378;
}

.joined-tag {
	padding: 8rpx 24rpx;
	border-radius: 8px;
	background-color: #f0ebe1;
}

.joined-text {
	font-size: 26rpx;
	color: #8a8378;
}

.full-btn {
	padding: 8rpx 24rpx;
	border-radius: 8px;
	background-color: #f0ebe1;
	opacity: 0.6;
}

.full-text {
	font-size: 26rpx;
	color: #bdb2a1;
}

.join-btn {
	padding: 8rpx 32rpx;
	border-radius: 8px;
	background-color: #8b6f47;
}

.btn-loading {
	opacity: 0.6;
}

.join-text {
	font-size: 26rpx;
	color: #fffdfa;
}

.footnote {
	display: block;
	margin-top: 48rpx;
	font-size: 24rpx;
	color: #bdb2a1;
	text-align: center;
}
</style>
