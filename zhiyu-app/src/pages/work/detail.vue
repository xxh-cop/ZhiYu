<template>
	<view :class="zyThemeClass" class="detail-page">
		<view class="detail-nav">
			<text class="detail-nav-kicker">织愈 · 创作故事</text>
			<AccessibleButton class="text-btn" @click="openFavorites">我的收藏</AccessibleButton>
		</view>

		<StateBlock v-if="loading && !work" type="loading" title="正在展开这份创作…" />

		<StateBlock
			v-else-if="notFound || error"
			:type="notFound ? 'empty' : 'error'"
			:face="notFound ? 'care' : 'calm'"
			:title="notFound ? notFoundTitle : '作品暂时没有加载成功'"
			:description="notFound ? notFoundDescription : '稍后重试即可继续查看。'"
			:action-text="notFound ? notFoundActionText : '重新加载'"
			@action="notFound ? goBack() : loadPage()"
		/>

		<view v-else-if="work" class="detail-layout">
			<view class="creation-panel">
				<!-- 作者视角：审核状态条（仅当从「我的作品」进入时展示） -->
				<view v-if="fromMine" class="author-status" :class="'status-' + authorStatus" role="status">
					<text class="status-badge">{{ authorStatusLabel }}</text>
					<text class="status-help">{{ authorStatusHelp }}</text>
					<text v-if="authorStatus === '2' && authorAuditReason" class="status-reason">原因：{{ authorAuditReason }}</text>
				</view>

				<!-- 作者本人视角：「展示受限」保护提示（softDemoteUntil 仅作者本人下发；他人与开关关闭时此处不渲染） -->
				<view v-if="softProtected" class="soft-notice" role="status">
					<view class="soft-notice-head">
						<text class="soft-badge">展示受限 · 保护中</text>
						<text class="soft-remain">{{ softRemain }}</text>
					</view>
					<text class="soft-help">因内容涉及情绪表达，这条作品暂时排在社区较靠后的位置；这是保护性的临时安排，不代表内容有问题，也只有你能看到这条提示。</text>
					<AccessibleButton class="soft-entry" @click="openSoftAppeal">了解详情或说说你的想法</AccessibleButton>
				</view>

				<view class="work-media">
					<MediaVideo v-if="work.mediaType === 'video' && work.videoUrl" :src="work.videoUrl" caption="社区创作视频" />
					<image
						v-else-if="work.coverUrl && !imageFailed"
						class="work-cover"
						:src="fullUrl(work.coverUrl)"
						:alt="'作品封面：' + work.title"
						mode="aspectFill"
						@click="previewCover"
						@error="imageFailed = true"
					/>
					<!-- 统一缺图/文字作品兜底：引用式封面，文字本身成为视觉主体 -->
					<view v-else class="text-cover">
						<text class="text-cover-mark">“</text>
						<text class="text-cover-quote">{{ coverQuote }}</text>
						<view class="text-cover-foot">
							<text class="text-cover-rule"></text>
							<text class="text-cover-label">创作故事 · {{ work.mediaType === 'text' ? '文字' : '未配图手作' }}</text>
						</view>
					</view>
					<text class="media-label">{{ work.mediaType === 'video' ? '创作影像' : work.mediaType === 'text' ? '文字作品' : '手作留影' }}</text>
				</view>

				<view class="creation-copy">
					<view class="work-meta">
						<text>{{ work.shareable ? '社区公开作品' : '仅本人可见 · 不可分享' }}</text>
						<text>{{ formatTime(work.createTime) }}</text>
					</view>
					<text class="work-title">{{ work.title }}</text>
					<view class="author">
						<view class="avatar" aria-hidden="true">{{ (work.authorName || '匿')[0] }}</view>
						<view class="author-info">
							<text class="author-name">{{ work.authorName || '匿名同学' }}</text>
							<text class="author-note">让创作留下自己的节奏</text>
						</view>
						<!-- 装饰性小印章：不表达认证 / 证书含义 -->
						<view class="author-seal" aria-hidden="true">织</view>
					</view>
					<view v-if="work.message && (work.mediaType === 'video' || (work.mediaType === 'image' && work.coverUrl))" class="story">
						<text class="eyebrow">作品故事</text>
						<text class="story-text" selectable>{{ work.message }}</text>
					</view>
					<view class="companion-note">
						<XiaotuanPet :size="44" face="happy" aria-hidden="true" />
						<text>不用做得完美，愿意开始就是一件了不起的小事。</text>
					</view>
				</view>
			</view>

			<view class="conversation-panel">
				<view class="conversation-heading">
					<view>
						<text class="eyebrow">让温暖发生</text>
						<text class="section-title">留暖评论</text>
					</view>
					<text class="count-badge">{{ commentCount }} 条公开留言</text>
				</view>
				<text class="moderation-note">新留言审核通过后公开。待审核或已隐藏的留言仅你自己可见。</text>

				<FormField
					class="composer"
					label="写下一句温暖"
					:error="commentSendError"
					:maxlength="255"
					:count="commentText.length"
				>
					<template #default="{ id, ariaDescribedby, ariaInvalid }">
						<textarea
							:id="id"
							v-model="commentText"
							class="comment-input"
							:aria-describedby="ariaDescribedby"
							:aria-invalid="ariaInvalid"
							maxlength="255"
							:disabled="sending"
							placeholder="分享一个具体的欣赏，或留下一句鼓励…"
							@input="commentSendError = ''"
						/>
					</template>
				</FormField>
				<view class="composer-footer">
					<AccessibleButton class="primary small" :disabled="!commentText.trim() || sending" :loading="sending" @click="handleSendComment">
						{{ sending ? '提交中' : '送出温暖' }}
					</AccessibleButton>
				</view>

				<StateBlock
					v-if="commentError"
					type="error"
					compact
					title="留言没有加载成功"
					:description="commentError"
					action-text="重新加载留言"
					@action="loadComments(failedPage)"
				/>
				<StateBlock
					v-else-if="!comments.length && !commentLoading"
					type="empty"
					compact
					face="listen"
					title="第一句鼓励，就从你开始"
					description="尊重每一份创作，也保护彼此的隐私。"
				/>
				<text v-else-if="commentLoading && !comments.length" class="list-end">正在读取留言…</text>

				<view v-for="comment in comments" :key="comment.id" class="comment-card" :class="{ 'comment-card--anchor': anchorCommentId && String(comment.id) === anchorCommentId }">
					<view class="avatar small-avatar" aria-hidden="true">{{ (comment.authorName || '匿')[0] }}</view>
					<view class="comment-main">
						<view class="comment-heading">
							<text>{{ comment.authorName }}</text>
							<text class="muted">{{ formatTime(comment.createTime) }}</text>
						</view>
						<text class="comment-content" selectable>{{ comment.content }}</text>
						<view v-if="comment.mine" class="comment-controls">
							<text class="comment-status">{{ Number(comment.status) === 0 ? '待审核 · 仅你可见' : Number(comment.status) === 2 ? '已隐藏 · 仅你可见' : '已公开' }}</text>
							<AccessibleButton class="text-btn" :disabled="!!removing" @click="askRemoveComment(comment)">
								{{ removing === comment.id ? '撤回中…' : '撤回' }}
							</AccessibleButton>
						</view>
					</view>
				</view>

				<AccessibleButton v-if="hasMoreComments && !commentError" class="more-btn" :loading="commentLoading" :disabled="commentLoading" @click="loadComments(commentPage + 1)">
					查看更多留言
				</AccessibleButton>
				<text v-else-if="commentLoading && comments.length" class="list-end">正在读取留言…</text>
				<text v-else-if="comments.length && !commentError" class="list-end">已读完所有留言</text>
			</view>
		</view>

		<view v-if="work && !notFound && !error" class="action-dock">
			<view class="action-dock-inner">
				<AccessibleButton class="dock-action" :class="{ active: liked }" :disabled="!!interactionBusy" :aria-pressed="liked" @click="toggle(1)">
					<wd-icon name="heart" size="36rpx" :color="liked ? '#a64842' : '#4a5f54'" />
					<text>{{ liked ? '已点赞' : '点赞' }} · {{ likeCount }}</text>
				</AccessibleButton>
				<AccessibleButton class="dock-action" :class="{ active: favorited }" :disabled="!!interactionBusy" :aria-pressed="favorited" @click="toggle(0)">
					<wd-icon name="star" size="36rpx" :color="favorited ? '#217e76' : '#4a5f54'" />
					<text>{{ favorited ? '已收藏' : '收藏' }}</text>
				</AccessibleButton>
				<AccessibleButton class="share-trigger" :disabled="!work.shareable || shareBusy" :loading="shareBusy" @click="prepareShare">
					{{ work.shareable ? '分享这份创作' : '私密作品' }}
				</AccessibleButton>
			</view>
			<text v-if="!work.shareable" class="dock-hint">这份作品仅自己可见，因此不能分享；发布时选择「公开」并审核通过后可分享。</text>
		</view>

		<text class="footnote">本系统不能替代专业心理咨询</text>

		<!-- 分享面板：ActionSheet 自定义内容 -->
		<ActionSheet
			:open="showShare"
			title="把这份创作送给朋友"
			description="仅分享作品入口，朋友需登录后查看。作品撤下后，原链接也无法继续访问。"
			@close="showShare = false"
		>
			<text class="share-title">{{ work && work.title }}</text>
			<!-- #ifdef H5 -->
			<view class="link-box"><text selectable>{{ shareLink }}</text></view>
			<view class="share-actions">
				<AccessibleButton class="primary" @click="copyLink">复制作品链接</AccessibleButton>
				<AccessibleButton v-if="systemShareSupported" class="more-btn share-system" @click="systemShare">使用系统分享</AccessibleButton>
			</view>
			<!-- #endif -->
			<!-- #ifdef MP-WEIXIN -->
			<button class="primary share-mp" open-type="share">发送给微信好友</button>
			<!-- #endif -->
			<text v-if="shareError" class="error-text" role="alert">{{ shareError }}</text>
			<text v-if="shareNotice" class="share-notice">{{ shareNotice }}</text>
		</ActionSheet>

		<!-- 撤回留言：ActionSheet 危险动作确认 -->
		<ActionSheet
			:open="!!pendingRemove"
			title="撤回这条留言？"
			description="撤回后正文将被清除，无法恢复。"
			:actions="[{ key: 'confirm', label: '确认撤回', tone: 'danger', busy: !!removing }]"
			cancel-text="再想想"
			:busy="!!removing"
			@select="confirmRemoveComment"
			@close="pendingRemove = null"
		/>

		<Toast />
	</view>
</template>

<script setup>
import MediaVideo from '../../components/MediaVideo.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import StateBlock from '../../components/StateBlock.vue'
import FormField from '../../components/FormField.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { useToast } from '../../composables/useToast'
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config'
import { workPath, workLoginUrl, workShareUrl } from '../../utils/work-navigation'
import { softActive, softRemainText } from '../../utils/soft-tier'

const toast = useToast()

const work = ref(null), loading = ref(false), notFound = ref(false), error = ref(''), imageFailed = ref(false)
const liked = ref(false), favorited = ref(false), likeCount = ref(0), commentCount = ref(0), interactionBusy = ref('')
const comments = ref([]), commentText = ref(''), commentSendError = ref(''), commentLoading = ref(false), commentError = ref('')
const commentPage = ref(0), commentTotal = ref(0), failedPage = ref(1), sending = ref(false), removing = ref(''), pendingRemove = ref(null)
const showShare = ref(false), shareBusy = ref(false), shareLink = ref(''), shareError = ref(''), shareNotice = ref(''), systemShareSupported = ref(false)
const hasMoreComments = computed(() => commentPage.value * 10 < commentTotal.value)
const coverQuote = computed(() => {
	const raw = String(work.value?.message || work.value?.title || '').replace(/\s+/g, ' ').trim()
	if (!raw) return '把日常写成一份小小的创作。'
	return raw.length > 56 ? raw.slice(0, 56) + '…' : raw
})

/* 作者视角：从「我的作品」进入时携带 from=mine&status=0|1|2 */
const fromMine = ref(false)
const authorStatus = ref('1')
const AUTHOR_STATUS = {
	0: { label: '审核中', help: '审核中，仅你自己可见；通过后会在社区公开。你仍可在「我的作品」中随时管理。' },
	1: { label: '已通过', help: '' },
	2: { label: '已下架', help: '作品未通过审核或被下架，目前不会出现在社区。你可以修改后重新发布，或在「我的作品」中撤回。' },
}
const authorStatusLabel = computed(() => {
	if (authorStatus.value !== '1') return AUTHOR_STATUS[authorStatus.value].label
	return work.value && Number(work.value.visible) === 1 ? '已通过 · 社区公开中' : '已通过 · 仅自己可见'
})
const authorStatusHelp = computed(() => AUTHOR_STATUS[authorStatus.value]?.help || '')
// 下架原因：后端详情接口（WorkVO）暂未下发 auditReason，字段到位后此处自动展示（留位）。
const authorAuditReason = computed(() => String((work.value && work.value.auditReason) || '').trim())

/* 「展示受限」保护提示（2026-11-07 处置链）：softDemoteUntil 仅作者本人下发，
 * 他人视角与开关关闭时字段缺省 → softProtected 为 false，零渲染零占位。 */
const softProtected = computed(() => softActive(work.value && work.value.softDemoteUntil))
const softRemain = computed(() => (softProtected.value ? softRemainText(work.value.softDemoteUntil) : ''))
function openSoftAppeal() {
	uni.navigateTo({ url: `/pages/work/soft-appeal?targetType=work&targetId=${workId}` })
}

const notFoundTitle = computed(() => (fromMine.value ? '这份作品暂时无法查看详情' : '这份作品暂时无法查看'))
const notFoundDescription = computed(() => {
	if (fromMine.value) return '待审核或已下架的作品不开放社区详情，可回到「我的作品」继续管理。'
	return '作品可能已撤下，或仅作者自己可见。'
})
const notFoundActionText = computed(() => (fromMine.value ? '回到我的作品' : '去社区看看'))

let workId = ''
// 评论区锚点（L1）：通知等入口带 anchor=comments 或 commentId=<评论雪花> 时，
// 详情加载完成后滚动定位到评论区/指定留言并高亮。
const scrollAnchor = ref('')
const anchorCommentId = ref('')
onLoad((query) => {
	workId = String(query.id || '')
	fromMine.value = query.from === 'mine'
	authorStatus.value = ['0', '1', '2'].includes(String(query.status)) ? String(query.status) : '1'
	scrollAnchor.value = query.anchor === 'comments' ? 'comments' : ''
	anchorCommentId.value = String(query.commentId || '')
	// #ifdef MP-WEIXIN
	uni.hideShareMenu()
	// #endif
	if (!workPath(workId)) { notFound.value = true; return }
	if (!uni.getStorageSync(TOKEN_KEY)) { uni.reLaunch({ url: workLoginUrl(workId) }); return }
	// #ifdef H5
	systemShareSupported.value = typeof navigator.share === 'function'
	// #endif
	loadPage().then(() => scrollToAnchor())
})
onShareAppMessage(() => ({
	title: work.value?.shareable ? '织愈 · 一份想与你分享的创作' : '织愈 · 用创作陪伴日常',
	path: work.value?.shareable ? workPath(workId) : '/pages/home/index',
	imageUrl: '/static/logo.png',
}))
onReachBottom(() => { if (hasMoreComments.value && !commentLoading.value && !commentError.value) loadComments(commentPage.value + 1) })

function applyWork(data) {
	work.value = data
	// 服务端 WorkVO.status 已下发时以服务端为准（先审后发）；query 的 status 仅作加载前的初始值
	if (data.status !== undefined && data.status !== null) authorStatus.value = String(data.status)
	liked.value = !!data.liked
	favorited.value = !!data.favorited
	likeCount.value = Number(data.likeCount) || 0
	commentCount.value = Number(data.commentCount) || 0
}
async function loadWork() { applyWork(await http.get(`/api/works/${workId}`)) }
async function loadPage() {
	if (loading.value) return
	loading.value = true
	error.value = ''
	try {
		await loadWork()
		await loadComments(1)
	} catch (e) {
		if (e.code === 1004) { notFound.value = true; work.value = null; showShare.value = false }
		else error.value = e.message || '加载失败'
	} finally { loading.value = false }
}
async function loadComments(page) {
	if (commentLoading.value) return
	commentLoading.value = true
	commentError.value = ''
	failedPage.value = page
	try {
		const data = await http.get(`/api/works/${workId}/comments`, { data: { page, size: 10 } })
		comments.value = page === 1 ? data.records : comments.value.concat(data.records || [])
		commentTotal.value = Number(data.total) || 0
		commentPage.value = page
	} catch (e) {
		commentError.value = e.message || '留言加载失败'
	} finally { commentLoading.value = false }
}
function scrollToAnchor() {
	if (!scrollAnchor.value && !anchorCommentId.value) return
	if (!work.value || notFound.value) return
	const target = anchorCommentId.value ? '.comment-card--anchor' : '.conversation-panel'
	setTimeout(() => {
		// #ifdef H5
		const el = document.querySelector(target) || document.querySelector('.conversation-panel')
		if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
		// #endif
		// #ifdef MP-WEIXIN
		uni.pageScrollTo({ selector: target, duration: 300 })
		// #endif
	}, 350)
}
async function toggle(type) {
	if (interactionBusy.value) return
	interactionBusy.value = type === 1 ? 'like' : 'favorite'
	try {
		const old = type === 1 ? liked.value : favorited.value
		const data = await http.post(`/api/works/${workId}/like`, { type })
		if (type === 1) {
			liked.value = data.active
			likeCount.value = Math.max(0, likeCount.value + (data.active ? 1 : 0) - (old ? 1 : 0))
		} else {
			favorited.value = data.active
			toast.show(data.active ? '已保存到我的收藏' : '已取消收藏')
		}
	} catch (e) {
		toast.care(e.message || '操作失败')
	} finally { interactionBusy.value = '' }
}
async function handleSendComment() {
	const content = commentText.value.trim()
	if (!content || sending.value) return
	commentSendError.value = ''
	sending.value = true
	try {
		await http.post(`/api/works/${workId}/comments`, { content })
		commentText.value = ''
		await loadComments(1)
		toast.success('已提交，等待审核')
	} catch (e) {
		commentSendError.value = (e.message || '提交失败') + '，内容已保留'
	} finally { sending.value = false }
}
function askRemoveComment(comment) {
	if (removing.value) return
	pendingRemove.value = comment
}
async function confirmRemoveComment() {
	const comment = pendingRemove.value
	if (!comment || removing.value) return
	removing.value = comment.id
	try {
		await http.del(`/api/works/${workId}/comments/${comment.id}`)
		pendingRemove.value = null
		await loadComments(1)
		await loadWork()
		toast.show('留言已撤回')
	} catch (e) {
		toast.care(e.message || '撤回失败')
	} finally { removing.value = '' }
}
async function prepareShare() {
	if (shareBusy.value || !work.value?.shareable) return
	shareBusy.value = true
	shareError.value = ''
	shareNotice.value = ''
	try {
		await loadWork()
		if (!work.value.shareable) { toast.show('这份作品目前不可分享'); return }
		// #ifdef H5
		shareLink.value = workShareUrl(workId, window.location)
		// #endif
		showShare.value = true
	} catch (e) {
		if (e.code === 1004) { notFound.value = true; work.value = null }
		toast.care(e.message || '暂时无法分享，请重试')
	} finally { shareBusy.value = false }
}
function copyLink() {
	shareError.value = ''
	uni.setClipboardData({
		data: shareLink.value,
		success: () => { shareNotice.value = '链接已复制，可以发送给朋友' },
		fail: () => { shareError.value = '未能复制，请长按上方链接手动复制' },
	})
}
async function systemShare() {
	// #ifdef H5
	shareError.value = ''
	try {
		await navigator.share({ title: '织愈 · 创作故事', url: shareLink.value })
		shareNotice.value = '已调用系统分享'
	} catch (e) {
		if (e.name !== 'AbortError') shareError.value = '系统分享不可用，请复制链接'
	}
	// #endif
}
function fullUrl(url) {
	if (!url) return ''
	return /^https?:\/\//.test(url) || url.startsWith('/static/') ? url : API_BASE_URL + url
}
function previewCover() { uni.previewImage({ urls: [fullUrl(work.value.coverUrl)] }) }
function formatTime(time) { return String(time || '').replace('T', ' ').slice(0, 16) }
function goBack() {
	if (fromMine.value) {
		uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/work/mine' }) })
		return
	}
	uni.switchTab({ url: '/pages/work/list' })
}
function openFavorites() { uni.navigateTo({ url: '/pages/work/favorites' }) }
</script>

<style scoped src="./work-detail.css"></style>
