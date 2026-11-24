<template>
	<view :class="zyThemeClass" class="page">
		<view class="zy-tabpage-top" aria-hidden="true"></view>
		<ZyPageHead seal="组" title="互助小组" subtitle="不必独自完成，也不用急着变好。">
			<template #action>
				<AccessibleButton variant="secondary" size="sm" aria-label="发起小组" @click="goCreate">
					<ZyIcon name="plus" :size="26" tone="brand" />
					<text>发起</text>
				</AccessibleButton>
			</template>
		</ZyPageHead>

		<StateBlock v-if="loading && groups.length === 0" type="loading" shape="list" title="正在整理小组…" />
		<StateBlock v-else-if="groups.length === 0 && !errorMsg" type="empty" art="groups" face="calm" title="还没有小组" description="来创建第一个吧。" action-text="创建小组" @action="goCreate" />
		<StateBlock v-else-if="errorMsg && groups.length === 0" type="error" title="小组加载失败" :description="errorMsg" action-text="重试" @action="loadData" />

		<view v-else>
			<view v-if="errorMsg" class="group-error-row" role="alert">
				<text>{{ errorMsg }}</text>
				<AccessibleButton variant="secondary" size="sm" @click="loadData">重试</AccessibleButton>
			</view>

			<!-- 我加入的：横滑大卡 -->
			<template v-if="joinedGroups.length">
				<view class="sec-head">
					<view class="sec-mark" aria-hidden="true"></view>
					<text class="sec-title">我加入的</text>
					<text class="sec-note">{{ joinedGroups.length }} 个</text>
				</view>
				<scroll-view class="mine-scroll" scroll-x :show-scrollbar="false">
					<view class="mine-track">
						<view v-for="group in joinedGroups" :key="group.id" class="mine-card" role="button" tabindex="0" :aria-label="'进入小组 ' + group.name" @click="openGroup(group)" @keydown.enter.prevent="openGroup(group)">
							<image class="mine-img" :src="textureFor(group.id)" mode="aspectFill" aria-hidden="true" />
							<view class="mine-shade" aria-hidden="true"></view>
							<view v-if="group.myRole === '组长' || group.myRole === 1" class="mine-role">组长</view>
							<view class="mine-copy">
								<text class="mine-name">{{ group.name }}</text>
								<text class="mine-meta">{{ group.memberCount }} 位织友 · 点按进入</text>
							</view>
						</view>
					</view>
				</scroll-view>
			</template>

			<!-- 发现小组 -->
			<view class="sec-head">
				<view class="sec-mark" aria-hidden="true"></view>
				<text class="sec-title">发现小组</text>
			</view>
			<view class="card-list">
				<view v-for="group in otherGroups" :key="group.id" class="group-card">
					<view class="group-thumb">
						<image class="group-thumb-img" :src="textureFor(group.id)" mode="aspectFill" aria-hidden="true" />
						<text class="group-thumb-char">{{ group.name ? group.name.charAt(0) : '小' }}</text>
					</view>
					<view class="card-center">
						<text class="group-name">{{ group.name }}</text>
						<text v-if="group.description" class="group-desc">{{ group.description }}</text>
						<view class="member-row">
							<view class="member-bar" aria-hidden="true"><view class="member-fill" :style="{ width: fillPct(group) }"></view></view>
							<text class="member-text">{{ group.memberCount }}/{{ group.maxCount }}</text>
						</view>
					</view>
					<view v-if="group.memberCount >= group.maxCount" class="full-tag"><text class="full-text">已满</text></view>
					<AccessibleButton v-else variant="primary" size="sm" class="join" :loading="joiningId === group.id" :aria-label="'加入小组 ' + group.name" @click="handleJoin(group)">加入</AccessibleButton>
				</view>
				<view v-if="!otherGroups.length" class="all-joined">
					<text>你已经加入了所有小组，也可以自己发起一个。</text>
				</view>
			</view>
		</view>

		<Toast />
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import StateBlock from '../../components/StateBlock.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ZyIcon from '../../components/ZyIcon.vue'
import ZyPageHead from '../../components/ZyPageHead.vue'
import { useToast } from '../../composables/useToast'
import { textureFor } from '../../utils/covers'

const toast = useToast()

const loading = ref(false)
const errorMsg = ref('')
const groups = ref([])
const joiningId = ref(null)
const joinedGroups = computed(() => groups.value.filter((g) => g.joined))
const otherGroups = computed(() => groups.value.filter((g) => !g.joined))
function openGroup(group){uni.navigateTo({url:'/pages/group/detail?id='+group.id})}
function fillPct(g) {
	const max = Number(g.maxCount) || 0
	return max ? Math.min(100, Math.round((Number(g.memberCount) || 0) / max * 100)) + '%' : '0%'
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
			groups.value[idx] = updated || { ...group, joined: true, memberCount: (Number(group.memberCount) || 0) + 1 }
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
.page { min-height: 100vh; padding: 28rpx 28rpx 160rpx; box-sizing: border-box; background-color: var(--zy-bg); }
/* #ifdef MP */
.page { padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
/* #endif */

.sec-head { display: flex; align-items: center; gap: 14rpx; margin: 24rpx 0 20rpx; }
.sec-mark { width: 8rpx; height: 34rpx; border-radius: 4rpx; background: var(--zy-cinnabar); }
.sec-title { font-family: var(--zy-font-display); font-size: 36rpx; font-weight: 700; letter-spacing: 2rpx; color: var(--zy-title); }
.sec-note { margin-left: auto; font-size: 22rpx; color: var(--zy-muted); }

/* 我加入的 */
.mine-scroll { width: 100%; white-space: nowrap; }
.mine-track { display: inline-flex; gap: 20rpx; padding: 4rpx 2rpx 18rpx; }
.mine-card { position: relative; display: inline-block; width: 440rpx; height: 260rpx; border-radius: var(--zy-radius-card); overflow: hidden; box-shadow: var(--zy-shadow-pop); background: var(--zy-brand-900); white-space: normal; }
.mine-card:active { transform: scale(.98); }
.mine-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.mine-shade { position: absolute; inset: 0; background: linear-gradient(160deg, rgba(36, 71, 143, .55) 0%, rgba(13, 31, 69, .9) 100%); }
.mine-role { position: absolute; top: 20rpx; right: 20rpx; padding: 4rpx 16rpx; border-radius: 999rpx; background: var(--zy-cinnabar); color: #fff; font-size: 22rpx; font-weight: 600; }
.mine-copy { position: absolute; left: 28rpx; right: 28rpx; bottom: 24rpx; display: flex; flex-direction: column; gap: 6rpx; }
.mine-name { font-family: var(--zy-font-display); font-size: 40rpx; font-weight: 700; color: #fff; letter-spacing: 2rpx; }
.mine-meta { font-size: 22rpx; color: rgba(255, 255, 255, .85); }

/* 发现小组 */
.card-list { display: flex; flex-direction: column; gap: 18rpx; }
.group-card { display: flex; align-items: center; gap: 22rpx; padding: 22rpx; background: var(--zy-card-bg); border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); }
.group-thumb { position: relative; width: 120rpx; height: 120rpx; flex-shrink: 0; border-radius: 24rpx; overflow: hidden; background: var(--zy-brand-600); }
.group-thumb-img { position: absolute; inset: 0; width: 100%; height: 100%; opacity: .55; }
.group-thumb-char { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-family: var(--zy-font-display); font-size: 52rpx; font-weight: 700; color: #fff; }
.card-center { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.group-name { font-size: 32rpx; font-weight: 700; color: var(--zy-title); }
.group-desc { font-size: 24rpx; line-height: 1.55; color: var(--zy-text); display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.member-row { display: flex; align-items: center; gap: 12rpx; margin-top: 6rpx; }
.member-bar { flex: 1; height: 10rpx; border-radius: 999rpx; background: var(--zy-bg-sunken); overflow: hidden; }
.member-fill { height: 100%; border-radius: 999rpx; background: var(--zy-brand-500); }
.member-text { font-size: 22rpx; color: var(--zy-muted); }
.join { flex-shrink: 0; margin: 0; min-width: 120rpx; }
.full-tag { flex-shrink: 0; padding: 10rpx 22rpx; border-radius: 999rpx; background: var(--zy-bg-sunken); }
.full-text { font-size: 24rpx; color: var(--zy-muted); }
.all-joined { padding: 28rpx; border-radius: var(--zy-radius-card); background: var(--zy-brand-50); color: var(--zy-brand-700); font-size: 24rpx; text-align: center; }

.group-error-row { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-bottom: 16rpx; padding: 20rpx 24rpx; border-radius: var(--zy-radius-card); background: var(--zy-bg-tint-care); color: var(--zy-care); font-size: 24rpx; }
.footnote { display: block; margin-top: 40rpx; font-size: 22rpx; color: var(--zy-muted); text-align: center; }
</style>

<style scoped>
/* v9 自定义导航：H5 留状态栏；小程序让出胶囊区 */
.zy-tabpage-top { height: var(--status-bar-height, 0px); }
/* #ifdef MP */
.zy-tabpage-top { height: calc(var(--status-bar-height, 20px) + 88rpx); }
/* #endif */
</style>
