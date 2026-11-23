<script setup>
import AccessibleButton from '../../components/AccessibleButton.vue'
import StateBlock from '../../components/StateBlock.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import GroupCompanions from '../../components/GroupCompanions.vue'
import GroupWorks from '../../components/GroupWorks.vue'
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '../../utils/request'

const id = ref('')
const group = ref(null)
const messages = ref([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const earlierError = ref('')
const content = ref('')
const hasMore = ref(false)
const sheet = ref(null)

const open = computed(() => group.value && Number(group.value.status) === 1)
const members = computed(() => (group.value && group.value.members) || [])
const transferableMembers = computed(() => members.value.filter(member => member.role !== '组长'))

const path = () => '/api/groups/' + id.value

async function load() {
	loading.value = true
	error.value = ''
	earlierError.value = ''
	try {
		const [g, m] = await Promise.all([request({ url: path() }), request({ url: path() + '/messages' })])
		group.value = g
		messages.value = m
		hasMore.value = m.length === 50
		if (g && g.name) uni.setNavigationBarTitle({ title: g.name })
	} catch (e) {
		group.value = null
		messages.value = []
		error.value = '小组加载失败，请重试'
	} finally {
		loading.value = false
	}
}

async function earlier() {
	if (busy.value || !messages.value.length) return
	busy.value = true
	earlierError.value = ''
	try {
		const rows = await request({ url: path() + '/messages', data: { before: messages.value[messages.value.length - 1].id } })
		messages.value.push(...rows)
		hasMore.value = rows.length === 50
	} catch (e) {
		earlierError.value = '更早消息加载失败，请重试'
	} finally {
		busy.value = false
	}
}

async function send() {
	if (busy.value || !content.value.trim()) return
	busy.value = true
	error.value = ''
	try {
		await request({ url: path() + '/messages', method: 'POST', data: { content: content.value.trim() } })
		content.value = ''
		await load()
	} catch (e) {
		error.value = '消息发送失败，内容已保留'
	} finally {
		busy.value = false
	}
}

function confirm(title, message, action, label) {
	sheet.value = { title, message, action, label: label || '确认' }
}

async function sheetConfirm() {
	const s = sheet.value
	if (!s || busy.value) return
	sheet.value = null
	busy.value = true
	error.value = ''
	try {
		await s.action()
	} catch (e) {
		error.value = '操作失败，请重试'
	} finally {
		busy.value = false
	}
}

function leave() {
	confirm('退出小组', '退出后将无法查看组内交流。组长需要先转让；最后一名成员退出后小组关闭。', async () => {
		await request({ url: path() + '/leave', method: 'POST' })
		uni.switchTab({ url: '/pages/group/list' })
	}, '确认退出')
}

function transfer(member) {
	confirm('转让组长', '确认将小组管理权交给 ' + member.nickname + '？', async () => {
		await request({ url: path() + '/leader', method: 'PUT', data: { memberId: member.id } })
		await load()
	}, '确认转让')
}

function remove(message) {
	confirm('删除消息', '删除后其他成员将无法查看这条消息。', async () => {
		await request({ url: path() + '/messages/' + message.id, method: 'DELETE' })
		await load()
	}, '确认删除')
}

function initial(name) {
	return (name && name.charAt(0)) || '同'
}

onLoad(options => {
	id.value = options.id || ''
	if (/^\d+$/.test(id.value)) load()
	else error.value = '小组编号无效'
})
</script>

<template>
	<view :class="zyThemeClass" class="growth-page group-page">
		<view class="group-head">
			<text class="growth-eyebrow">COMMON ROOM · 小组空间</text>
			<text class="group-title">一起，慢慢来</text>
			<text class="group-sub">不追问，不评判。在这里，按自己的节奏与人同行。</text>
			<view v-if="group" class="group-meta">
				<text class="meta-chip">{{ group.member_count }} / {{ group.max_count }} 人</text>
				<text class="meta-chip">{{ group.myRole }}</text>
				<text class="meta-chip">{{ open ? '开放中' : '已关闭' }}</text>
			</view>
		</view>

		<view v-if="error" class="growth-error" role="alert">
			{{ error }}
			<AccessibleButton v-if="!group" class="growth-button secondary" :disabled="loading" @click="load">重新读取</AccessibleButton>
		</view>
		<StateBlock v-if="loading && !group" type="loading" shape="detail" title="正在读取小组…" />

		<template v-if="group">
			<view class="growth-card">
				<text class="growth-card-title">关于小组</text>
				<text class="group-description">{{ group.description || '一起创作，一起交流。' }}</text>

				<view class="member-strip" aria-label="小组成员">
					<view v-for="member in members.slice(0, 6)" :key="member.id" class="member-pill">
						<view class="member-avatar" aria-hidden="true">{{ initial(member.nickname) }}</view>
						<text class="member-name">{{ member.nickname }}</text>
					</view>
					<text v-if="members.length > 6" class="member-more">+{{ members.length - 6 }}</text>
				</view>

				<view v-if="group.myRole === '组长' && transferableMembers.length" class="leader-box">
					<text class="leader-title">组长操作</text>
					<text class="growth-muted">需要离开前，可先把组长交给一位组员。</text>
					<view v-for="member in transferableMembers" :key="member.id" class="leader-row">
						<text>{{ member.nickname }}</text>
						<AccessibleButton size="mini" variant="ghost" :disabled="busy || !open" @click="transfer(member)">转让组长</AccessibleButton>
					</view>
				</view>

				<view class="manage-row">
					<AccessibleButton size="mini" variant="ghost" :disabled="loading || busy" @click="load">刷新</AccessibleButton>
					<AccessibleButton size="mini" variant="ghost" class="leave-link" :disabled="busy" @click="leave">退出小组</AccessibleButton>
				</view>
			</view>

			<GroupCompanions v-if="open" :group-id="id" :members="members" :my-member-id="group.myMemberId" />
			<GroupWorks v-if="open" :group-id="id" />

			<view class="growth-card chat-card">
				<view class="growth-row growth-spread">
					<text class="growth-card-title">组内交流</text>
					<AccessibleButton size="mini" variant="ghost" :disabled="loading || busy" @click="load">刷新</AccessibleButton>
				</view>
				<text class="growth-muted">仅小组成员可见。消息加密保存；作者或组长可以删除消息。</text>

				<template v-if="open">
					<textarea
						v-model="content"
						class="group-input"
						aria-label="组内交流内容"
						maxlength="1000"
						placeholder="分享手作体验，或给同行者一句支持…"
					/>
					<AccessibleButton class="growth-button" :disabled="busy || !content.trim()" @click="send">
						{{ busy ? '处理中…' : '发送到小组' }}
					</AccessibleButton>
				</template>

				<view v-for="message in messages" :key="message.id" class="message">
					<view class="message-head">
						<text class="message-author">{{ message.mine ? '我' : message.nickname }}</text>
						<text class="message-time">{{ String(message.create_time || '').replace('T', ' ').slice(0, 16) }}</text>
						<AccessibleButton
							v-if="message.mine || group.myRole === '组长'"
							size="mini"
							variant="ghost"
							:disabled="busy"
							@click="remove(message)"
						>删除</AccessibleButton>
					</view>
					<text class="message-body">{{ message.content }}</text>
				</view>

				<text v-if="!messages.length" class="growth-muted">还没有交流，从一句简单的问候开始。</text>
				<text v-if="earlierError" class="growth-error" role="alert">{{ earlierError }}</text>
				<AccessibleButton v-if="earlierError" class="growth-button secondary" :disabled="busy" @click="earlier">重试更早消息</AccessibleButton>
				<AccessibleButton v-if="hasMore && !earlierError" class="growth-button secondary" :disabled="busy" @click="earlier">查看更早消息</AccessibleButton>
			</view>
		</template>

		<ActionSheet
			:open="!!sheet"
			:title="sheet ? sheet.title : ''"
			:description="sheet ? sheet.message : ''"
			:actions="sheet ? [{ key: 'confirm', label: sheet.label, tone: 'danger', busy: busy }] : []"
			cancel-text="取消"
			:busy="busy"
			@select="sheetConfirm"
			@close="sheet = null"
		/>
		<text class="growth-foot">本系统不能替代专业心理咨询</text>
	</view>
</template>

<style scoped>
.group-page {
	max-width: 760px;
	margin: 0 auto;
	padding-bottom: calc(48rpx + env(safe-area-inset-bottom));
}
.group-head {
	padding: 8rpx 0 26rpx;
}
.group-title {
	display: block;
	margin-top: 8rpx;
	font-size:var(--zy-text-title-lg, 40rpx);
	font-weight: 600;
	line-height: 1.35;
	color: var(--zy-title, #33302A);
}
.group-sub {
	display: block;
	margin-top: 10rpx;
	font-size:var(--zy-text-caption, 24rpx);
	line-height: 1.7;
	color: var(--zy-text, #5C5648);
}
.group-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 10rpx;
	margin-top: 18rpx;
}
.meta-chip {
	padding: 6rpx 16rpx;
	border-radius: 999rpx;
	background: var(--zy-bg-sunken, #F4F4F2);
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #5C5648);
}
.group-description {
	display: block;
	white-space: pre-wrap;
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.9;
	margin: 8rpx 0 20rpx;
}
.member-strip {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 10rpx;
	padding: 16rpx 0;
	border-top: 1rpx dashed var(--zy-stitch, #C9BFA9);
	border-bottom: 1rpx dashed var(--zy-stitch, #C9BFA9);
}
.member-pill {
	display: inline-flex;
	align-items: center;
	gap: 10rpx;
	padding: 0;
	border-radius: 0;
	background: transparent;
}
.member-avatar {
	width: 44rpx;
	height: 44rpx;
	border-radius: 0;
	border: 1rpx solid var(--zy-border, #d3ded8);
	background: transparent;
	color: var(--zy-brand-700, var(--zy-brand-700, #1a645e));
	font-family: var(--zy-font-display, "Songti SC", serif);
	font-size:var(--zy-text-caption, 24rpx);
	font-weight: 500;
	display: flex;
	align-items: center;
	justify-content: center;
}
.member-name {
	font-size:var(--zy-text-caption, 24rpx);
	color: var(--zy-text, #42524c);
}
.member-more {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-muted, #6e7b75);
}
.leader-box {
	margin-top: 18rpx;
	padding: 14rpx 0 14rpx 18rpx;
	border-radius: 0;
	border-left: 3rpx solid var(--zy-brand-700, #0a403b);
	background: transparent;
}
.leader-title {
	display: block;
	font-size:var(--zy-text-caption, 24rpx);
	font-weight: 600;
	margin-bottom: 6rpx;
}
.leader-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
	padding-top: 10rpx;
	font-size:var(--zy-text-caption, 24rpx);
}
.manage-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
	margin-top: 18rpx;
}
.leave-link {
	color: var(--zy-care, var(--zy-care, #a64842));
}
.chat-card .growth-card-title {
	margin-bottom: 6rpx;
}
.group-head + .growth-card{border-top:0}
.group-input {
	display: block;
	width: 100%;
	height: 160rpx;
	margin: 16rpx 0 8rpx;
	padding: 18rpx;
	box-sizing: border-box;
	border: 1rpx solid var(--zy-border, #E4DCCB);
	border-radius: 16rpx;
	background: var(--zy-bg, #FFFFFF);
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.7;
	color: var(--zy-title, #33302A);
}
.message {
	padding: 20rpx 0;
	border-bottom: 1rpx solid var(--zy-border, #E4DCCB);
}
.message:last-of-type {
	border-bottom: 0;
}
.message-head {
	display: flex;
	align-items: center;
	gap: 12rpx;
}
.message-author {
	font-size:var(--zy-text-caption, 24rpx);
	font-weight: 600;
	color: var(--zy-title, #33302A);
}
.message-time {
	font-size:var(--zy-text-micro, 22rpx);
	color: var(--zy-text, #5C5648);
}
.message-head button {
	margin: 0 0 0 auto;
}
.message-body {
	display: block;
	margin-top: 8rpx;
	font-size:var(--zy-text-body, 28rpx);
	line-height: 1.8;
	color: var(--zy-text, #5C5648);
	white-space: pre-wrap;
	overflow-wrap: anywhere;
}
/* ── v9 冷瓷展签收口 ── */
.group-page { padding: 36rpx 32rpx 120rpx; background-color: var(--zy-bg); }
.group-head { padding: 0 0 30rpx; border-bottom: 1rpx solid var(--zy-border); margin-bottom: 8rpx; }
.group-head .growth-eyebrow { letter-spacing: 2px; color: var(--zy-muted); }
.group-title { margin-top: 14rpx; font-family: var(--zy-font-display, serif); font-size: 64rpx; font-weight: 500; line-height: 1.25; color: var(--zy-title); }
.group-sub { margin-top: 10rpx; color: var(--zy-muted); }
.group-meta { gap: 0; margin-top: 16rpx; border-top: 0; padding-top: 0; }
.meta-chip { padding: 0 16rpx; border-radius: 0; background: transparent; border-right: 1rpx solid var(--zy-border); }
.meta-chip:first-child { padding-left: 0; }
.meta-chip:last-child { border-right: 0; }
.group-page .growth-card { padding: 28rpx 0; background: transparent; border: 0; border-top: 1rpx solid var(--zy-border); border-radius: 0; }
.group-page .growth-card-title { font-family: var(--zy-font-display, serif); font-size: 38rpx; font-weight: 500; }
.group-description { color: var(--zy-text); }
.member-strip { border-top: 1rpx solid var(--zy-border); border-bottom: 1rpx solid var(--zy-border); gap: 16rpx; }
.member-pill { padding: 0; border-radius: 0; background: transparent; gap: 10rpx; }
.member-avatar { width: 40rpx; height: 40rpx; border-radius: 0; background: var(--zy-bg-sunken); color: var(--zy-brand-700); font-family: var(--zy-font-display, serif); font-weight: 500; }
.member-name { color: var(--zy-text); }
.leader-box { border-left: 3rpx solid var(--zy-accent, #a64842); border-radius: 0; background: transparent; padding: 14rpx 0 14rpx 18rpx; }
.leader-title { font-weight: 500; color: var(--zy-accent, #a64842); }
.manage-row { margin-top: 14rpx; border-top: 1rpx solid var(--zy-border); padding-top: 16rpx; }
.group-head + .growth-card{border-top:0}
.group-input { border: 0; border-bottom: 1rpx solid var(--zy-border); border-radius: 0; background: transparent; padding: 10rpx 0; }
.message { border-bottom: 1rpx solid var(--zy-border); }
.message-author { font-family: var(--zy-font-display, serif); font-weight: 500; color: var(--zy-title); }
.message-time { color: var(--zy-muted); }
.message-body { color: var(--zy-text); }
</style>
