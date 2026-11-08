<template>
	<view :class="zyThemeClass" class="page">
		<view class="page-head">
			<view>
				<text class="page-title">消息通知</text>
				<text class="page-subtitle">审核结果、回复与小组动态，都会在这里等你。</text>
			</view>
			<view class="head-actions">
				<AccessibleButton v-if="unreadCount > 0" class="read-all-btn" :disabled="readingAll" :loading="readingAll" @click="handleReadAll">
					{{ readingAll ? '正在处理…' : '全部已读' }}
				</AccessibleButton>
				<AccessibleButton v-if="records.length" class="clear-btn" :disabled="clearing" @click="clearOpen = true">
					{{ type ? '清空该类型' : '清空全部' }}
				</AccessibleButton>
			</view>
		</view>

		<scroll-view scroll-x class="filter-scroll" :show-scrollbar="false">
			<view class="filters" role="tablist" aria-label="按类型筛选">
				<AccessibleButton
					v-for="f in filters"
					:key="f.value"
					class="filter-chip"
					:class="{ active: type === f.value }"
					:aria-pressed="type === f.value"
					@click="chooseType(f.value)"
				>{{ f.label }}</AccessibleButton>
			</view>
		</scroll-view>

		<StateBlock v-if="loading && !records.length" type="loading" title="正在整理消息…" />
		<StateBlock
			v-else-if="error && !records.length"
			type="error"
			title="消息没有加载成功"
			:description="error"
			action-text="重试"
			:action-loading="loading"
			@action="load(1)"
		/>
		<StateBlock
			v-else-if="!records.length"
			type="empty"
			face="calm"
			art="notice"
			:title="type ? '这个类型暂时没有消息' : '现在还没有新消息'"
			description="有审核结果或新回复时，会第一时间出现在这里。"
			action-text="去社区看看"
			@action="goCommunity"
		/>

		<view v-else class="notice-list">
			<view v-for="item in records" :key="item.id" class="notice-wrap">
				<AccessibleButton
					class="notice-item"
					:class="{ unread: !isRead(item) }"
					:aria-label="itemAria(item)"
					@click="openItem(item)"
				>
					<view class="notice-icon" :class="'icon-' + item.refType" aria-hidden="true">
						<text>{{ iconOf(item.refType) }}</text>
					</view>
					<view class="notice-main">
						<view class="notice-row">
							<text class="notice-type">{{ typeLabel(item.type) }}</text>
							<text class="notice-time">{{ formatTime(item.createTime || item.time) }}</text>
						</view>
						<text class="notice-text">{{ itemText(item) }}</text>
					</view>
					<view v-if="!isRead(item)" class="unread-dot" aria-label="未读" />
				</AccessibleButton>
				<!-- AQ4 单条删除：低干扰 ✕（键盘可达），点击进 ActionSheet 确认 -->
				<AccessibleButton
					class="notice-del"
					:aria-label="'删除这条' + typeLabel(item.type) + '消息'"
					:disabled="deleting === item.id"
					@click="askDelete(item)"
				>✕</AccessibleButton>
			</view>
		</view>

		<AccessibleButton v-if="hasMore && !error" class="load-more" :loading="loading" :disabled="loading" @click="load(page + 1)">
			{{ loading ? '正在读取…' : '查看更多消息' }}
		</AccessibleButton>
		<text v-else-if="records.length && !error" class="list-end">已读完所有消息</text>
		<StateBlock v-if="error && records.length" type="error" compact title="更多消息没有加载成功" :description="error" action-text="重试" @action="load(failedPage)" />

		<text class="footnote">本系统不能替代专业心理咨询</text>
		<Toast />

		<!-- AQ4 单条删除确认 -->
		<ActionSheet
			:open="!!pendingDelete"
			title="删除这条消息？"
			description="删除后不可恢复。"
			:actions="[{ key: 'confirm', label: '确认删除', tone: 'danger', busy: !!deleting }]"
			cancel-text="再想想"
			:busy="!!deleting"
			@select="confirmDelete"
			@close="pendingDelete = null"
		/>

		<!-- AQ4 批量清空确认（二次确认，文案写清范围与后果） -->
		<AccessibleDialog :open="clearOpen" :busy="clearing" :title="type ? '清空该类型的全部消息？' : '清空全部消息？'" @close="clearOpen = false">
			<text class="clear-desc">{{ type ? `将删除「${typeLabel(type)}」类型下全部通知，不可恢复。` : '将删除全部通知，不可恢复。' }}</text>
			<view class="clear-actions">
				<AccessibleButton class="clear-cancel" :disabled="clearing" @click="clearOpen = false">保留消息</AccessibleButton>
				<AccessibleButton class="clear-confirm" :loading="clearing" :disabled="clearing" @click="confirmClear">{{ clearing ? '正在清空…' : '确认清空' }}</AccessibleButton>
			</view>
		</AccessibleDialog>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { useNotifications } from '../../composables/useNotifications'
import { useToast } from '../../composables/useToast'
import StateBlock from '../../components/StateBlock.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import AccessibleDialog from '../../components/AccessibleDialog.vue'
import Toast from '../../components/Toast.vue'

/**
 * 消息通知页（K1）。
 * id / refId 为字符串型雪花 ID：全程不转 Number，原样回传给已读接口与跳转参数。
 * 单条点击 = 标记已读（幂等）+ 按 refType 跳转；「全部已读」为批量幂等操作。
 */
const toast = useToast()
const { state: noticeState, refreshUnread, setUnread } = useNotifications()

const TYPE_LABEL = {
	WORK_AUDIT: '作品审核',
	COMMENT_AUDIT: '留暖审核',
	BOOKING_REPLY: '预约回复',
	GROUP_JOIN: '小组动态',
	CARE_REMINDER: '关怀提醒',
	CAMPUS_AUDIT: '身份核验',
	SOFT_TIER_NOTICE: '展示提醒',
}
const filters = [
	{ label: '全部', value: '' },
	{ label: '作品审核', value: 'WORK_AUDIT' },
	{ label: '留暖审核', value: 'COMMENT_AUDIT' },
	{ label: '预约回复', value: 'BOOKING_REPLY' },
	{ label: '小组动态', value: 'GROUP_JOIN' },
	{ label: '关怀提醒', value: 'CARE_REMINDER' },
	{ label: '身份核验', value: 'CAMPUS_AUDIT' },
	{ label: '展示提醒', value: 'SOFT_TIER_NOTICE' },
]
const REF_ICON = { work: '作', comment: '言', booking: '约', group: '组', care_reminder: '怀', campus_verification: '核' }

const records = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const type = ref('')
const loading = ref(false)
const error = ref('')
const failedPage = ref(1)
const readingAll = ref(false)
const marking = ref('')
// AQ4 删除状态
const pendingDelete = ref(null)
const deleting = ref('')
const clearOpen = ref(false)
const clearing = ref(false)

const unreadCount = computed(() => records.value.filter((item) => !isRead(item)).length)
const hasMore = computed(() => page.value * size < total.value)

onShow(() => {
	load(1)
	refreshUnread(true)
})

// T4：下拉刷新（pages.json 已开 enablePullDownRefresh）；失败经 load() 内部错误态 + Toast 提示，不阻塞现有列表
onPullDownRefresh(async () => {
	try {
		await Promise.all([load(1), refreshUnread(true)])
		if (error.value) toast.care(error.value)
	} finally {
		uni.stopPullDownRefresh()
	}
})

function isRead(item) {
	return Boolean(item.read ?? item.isRead)
}
function typeLabel(t) {
	return TYPE_LABEL[t] || '消息'
}
function iconOf(refType) {
	return REF_ICON[refType] || '讯'
}
function itemText(item) {
	// 后端已脱敏；前端只展示服务端文案，不自行拼接敏感内容。
	// 统一规则（AH2 排查）：全部 6 类通知都是「title 短结论 + content 完整正文」结构，
	// content 才含作品名/节目名/小组名/驳回原因等关键信息——content 非空一律优先展示 content，
	// 不回退到按类型 if 补丁。title 结论义由类型标签（typeLabel）与 content 正文共同承载。
	if (item && item.content) return item.content
	return (item && item.title) || item.summary || '有一条新消息，点开看看。'
}
function itemAria(item) {
	return `${typeLabel(item.type)}，${itemText(item)}${isRead(item) ? '' : '，未读'}`
}
function formatTime(time) {
	return String(time || '').replace('T', ' ').slice(5, 16)
}

async function load(target) {
	if (loading.value) return
	loading.value = true
	error.value = ''
	failedPage.value = target
	try {
		const params = { page: target, size }
		if (type.value) params.type = type.value
		const data = await http.get('/api/notifications', { data: params })
		const list = (data && data.records) || []
		records.value = target === 1 ? list : records.value.concat(list)
		total.value = Number(data && data.total) || 0
		page.value = target
	} catch (e) {
		error.value = e.message || '消息加载失败'
		if (target === 1) records.value = []
	} finally {
		loading.value = false
	}
}

function chooseType(value) {
	if (type.value === value) return
	type.value = value
	load(1)
}

function routeFor(item) {
	const refId = String(item.refId || '')
	// 「展示提醒」（2026-11-07 处置链）：直达展示说明与申诉页——作者在这里看到原文、
	// 当前状态、期限与申诉入口；refType 为 soft_review 的仅供管理员，学生端不出现。
	if (item.type === 'SOFT_TIER_NOTICE') {
		const subRefId = String(item.subRefId || item.sub_ref_id || '')
		if (item.refType === 'work' && refId) return `/pages/work/soft-appeal?targetType=work&targetId=${refId}`
		if (item.refType === 'comment' && refId && subRefId) {
			return `/pages/work/soft-appeal?targetType=comment&targetId=${subRefId}&workId=${refId}`
		}
		if (item.refType === 'comment' && refId) return `/pages/work/detail?id=${refId}`
		return ''
	}
	switch (item.refType) {
		case 'work':
			return refId ? `/pages/work/detail?id=${refId}` : '/pages/work/list'
		case 'comment': {
			// 等契约的临时路径（Q3）：当前后端 refId 是评论 ID，无法反查作品（缺口 G1）。
			// 契约将改为 ref_id=work_id + sub_ref_id=commentId；本分支向前兼容——
			// 后端契约到位后无需再改前端，自动直达详情并锚点定位到该留言。
			const subRefId = String(item.subRefId || item.sub_ref_id || '')
			if (refId && subRefId) return `/pages/work/detail?id=${refId}&commentId=${subRefId}`
			return ''
		}
		case 'booking':
			return '/sub-programs/pages/programs/index'
		case 'group':
			return refId ? `/pages/group/detail?id=${refId}` : '/pages/group/list'
		case 'care_reminder':
			return '/pages/support/index'
		case 'campus_verification':
			// AH3：CAMPUS_AUDIT 通知，refId=核验记录 id；核验状态以「校园身份」页为准，落到该页
			return '/pages/campus/index'
		default:
			return ''
	}
}

async function openItem(item) {
	// 先标记已读（乐观更新，失败回滚），再按 refType 跳转
	if (!isRead(item) && marking.value !== item.id) {
		marking.value = item.id
		const previous = records.value.slice()
		item.read = true
		setUnread(noticeState.unread - 1)
		try {
			await http.post(`/api/notifications/${item.id}/read`, {})
		} catch (e) {
			records.value = previous
			setUnread(noticeState.unread + 1)
			toast.care(e.message || '标记已读失败，请重试')
			marking.value = ''
			return
		}
		marking.value = ''
	}
	const url = routeFor(item)
	if (!url) {
		if (item.refType === 'comment') toast.care('这条留暖暂时不能直达作品')
		return
	}
	if (url.startsWith('/pages/work/list') || url.startsWith('/pages/group/list')) {
		uni.switchTab({ url })
	} else {
		uni.navigateTo({ url })
	}
}

async function handleReadAll() {
	if (readingAll.value || !records.value.some((item) => !isRead(item))) return
	readingAll.value = true
	try {
		await http.post('/api/notifications/read-all', {})
		records.value = records.value.map((item) => ({ ...item, read: true }))
		setUnread(0)
		toast.success('已全部标记为已读')
	} catch (e) {
		toast.care(e.message || '操作失败，请重试')
	} finally {
		readingAll.value = false
	}
}

/* ── AQ4 删除（单条 + 批量清空）──
 * 失败口径：不做乐观删除——先请求成功再移除条目，失败 toast 且列表保持原状，
 * 不留幽灵条目。1004/1003 由 request 层解析为业务错误码原文提示。 */
function askDelete(item) {
	if (deleting.value) return
	pendingDelete.value = item
}
async function confirmDelete() {
	const item = pendingDelete.value
	if (!item || deleting.value) return
	deleting.value = item.id
	try {
		await http.del(`/api/notifications/${item.id}`)
		records.value = records.value.filter((r) => r.id !== item.id)
		total.value = Math.max(0, total.value - 1)
		if (!isRead(item)) setUnread(noticeState.unread - 1)
		pendingDelete.value = null
		toast.success('已删除')
	} catch (e) {
		toast.care((e && e.message) || '删除失败，请重试')
	} finally {
		deleting.value = ''
	}
}
async function confirmClear() {
	if (clearing.value) return
	clearing.value = true
	try {
		await http.del(type.value ? `/api/notifications?type=${type.value}` : '/api/notifications')
		clearOpen.value = false
		await load(1)
		refreshUnread(true)
		toast.success('已清空')
	} catch (e) {
		toast.care((e && e.message) || '清空失败，请重试')
	} finally {
		clearing.value = false
	}
}

function goCommunity() {
	uni.switchTab({ url: '/pages/work/list' })
}
</script>

<style scoped>
.page { min-height: 100vh; padding: 32rpx 28rpx 120rpx; box-sizing: border-box; background: var(--zy-bg, #f5f7f2); }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; margin-bottom: 24rpx; }
.page-title { display: block; font-family: var(--zy-serif); font-size: 40rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.page-subtitle { display: block; margin-top: 8rpx; font-size: 24rpx; color: var(--zy-text, #4a5f54); line-height: 1.6; }
.read-all-btn { flex-shrink: 0; padding: 12rpx 24rpx; margin: 0; border-radius: var(--zy-radius-full, 9999rpx); background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-brand-700, #1a645e); font-size: 24rpx; line-height: 1.5; }
.read-all-btn::after { border: 0; }
/* AQ4 删除相关 */
.head-actions { display: flex; flex-direction: column; gap: 12rpx; align-items: flex-end; flex-shrink: 0; }
.clear-btn { padding: 10rpx 22rpx; margin: 0; border-radius: var(--zy-radius-full, 9999rpx); background: transparent; color: var(--zy-text, #4a5f54); font-size: 22rpx; line-height: 1.5; }
.clear-btn::after { border: 0; }
.notice-wrap { position: relative; }
.notice-del { position: absolute; top: 8rpx; right: 8rpx; min-height: 88rpx; min-width: 88rpx; padding: 0 10rpx; margin: 0; background: transparent; color: var(--zy-text, #4a5f54); font-size: 22rpx; line-height: 44rpx; opacity: 0.65; }
.notice-del::after { border: 0; }
.notice-del:focus-visible { outline: 3px solid var(--zy-brand-700, #1a645e); outline-offset: 2px; opacity: 1; }
.clear-desc { display: block; font-size: 26rpx; line-height: 1.8; color: var(--zy-text, #4a5f54); margin: 8rpx 0 24rpx; }
.clear-actions { display: flex; gap: 20rpx; justify-content: flex-end; }
.clear-cancel { padding: 12rpx 28rpx; background: transparent; color: var(--zy-text, #4a5f54); font-size: 26rpx; }
.clear-confirm { padding: 12rpx 28rpx; background: var(--zy-care, #a64842); color: #fff; font-size: 26rpx; border-radius: 999rpx; }

.filter-scroll { white-space: nowrap; margin-bottom: 24rpx; }
.filters { display: inline-flex; gap: 12rpx; }
.filter-chip { margin: 0; padding: 10rpx 24rpx; min-height: 44px; border-radius: var(--zy-radius-full, 9999rpx); background: var(--zy-card-bg, #fff); border: 1rpx solid var(--zy-border, #dfe7dc); color: var(--zy-text, #4a5f54); font-size: 24rpx; line-height: 1.5; }
.filter-chip::after { border: 0; }
.filter-chip.active { background: var(--zy-brand-600, #1A645E); border-color: var(--zy-brand-600, #1A645E); color: #fff; }

.notice-list { display: flex; flex-direction: column; gap: 16rpx; }
.notice-item { position: relative; display: flex; align-items: flex-start; gap: 20rpx; width: 100%; margin: 0; padding: 24rpx; box-sizing: border-box; background: var(--zy-card-bg, #fff); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: var(--zy-radius-card, 32rpx); line-height: 1.6; text-align: left; }
.notice-item::after { border: 0; }
.notice-item.unread { border-left: 6rpx solid var(--zy-brand-600, #217e76); }
/* BI（2026-11-05）：基础类自带默认配色（沿用 icon-work 同款薄荷系令牌）——
 * 任何未知 refType（如后端新增的 inheritor_profile / project）都有可见图标底，
 * 不会再出现「只有字、没有圆块」的空图标位；具体 refType 配色仍在下方覆盖。
 * 顺带兜住 campus_verification（AH 批加了图标字但漏配样式）。 */
.notice-icon { flex-shrink: 0; width: 64rpx; height: 64rpx; border-radius: var(--zy-radius-full, 9999rpx); display: flex; align-items: center; justify-content: center; font-size: 26rpx; font-weight: 600; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-brand-700, #1a645e); }
.icon-work, .icon-comment { background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-brand-700, #1a645e); }
.icon-booking, .icon-group { background: var(--zy-brand-50, #eaf4f1); color: var(--zy-brand-600, #1A645E); }
.icon-care_reminder { background: var(--zy-bg-tint-warm, #f7efd9); color: var(--zy-accent, #8a6d1f); }
.notice-main { flex: 1; min-width: 0; }
.notice-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.notice-type { font-size: 24rpx; font-weight: 600; color: var(--zy-brand-700, #1a645e); }
.notice-time { font-size: 20rpx; color: var(--zy-text, #4a5f54); font-variant-numeric: tabular-nums; }
.notice-text { display: block; margin-top: 8rpx; font-size: 27rpx; color: var(--zy-title, #233d34); line-height: 1.7; overflow-wrap: anywhere; }
.unread-dot { position: absolute; top: 28rpx; right: 24rpx; width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--zy-care, #a64842); }

.load-more { display: block; margin: 28rpx auto 0; padding: 16rpx 40rpx; border-radius: var(--zy-radius-full, 9999rpx); background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-brand-700, #1a645e); font-size: 26rpx; }
.load-more::after { border: 0; }
.list-end { display: block; text-align: center; font-size: 22rpx; color: var(--zy-text, #4a5f54); padding: 28rpx 0; }
.footnote { display: block; margin-top: 40rpx; text-align: center; font-size: 22rpx; color: var(--zy-text, #4a5f54); }
button:focus-visible, .filter-chip:focus-visible, .notice-item:focus-visible { outline: 3px solid var(--zy-brand-700, #1a645e); outline-offset: 3px; }
</style>
