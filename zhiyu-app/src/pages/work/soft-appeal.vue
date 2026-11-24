<template>
	<view :class="zyThemeClass" class="appeal-page">
		<StateBlock v-if="loading" type="loading" shape="detail" title="正在整理这条内容…" />

		<StateBlock
			v-else-if="loadError"
			type="error"
			face="care"
			title="暂时没有加载成功"
			:description="loadError"
			action-text="重新加载"
			@action="loadPage"
		/>

		<StateBlock
			v-else-if="notFound"
			type="empty"
			face="care"
			:title="targetType === 'comment' ? '这条留言暂时没有找到' : '这条内容暂时无法查看'"
			description="它可能已被撤回，或正在整理中；展示状态以消息通知为准。"
			action-text="返回上一页"
			@action="goBack"
		/>

		<view v-else class="appeal-body">
			<!-- 原文 -->
			<view class="appeal-card">
				<text class="card-eyebrow">你分享的内容</text>
				<template v-if="targetType === 'work'">
					<text class="work-title">{{ work.title }}</text>
					<text v-if="work.message" class="work-message">{{ work.message }}</text>
					<text v-else class="work-message muted">这份作品没有填写故事。</text>
				</template>
				<template v-else>
					<text class="comment-content">{{ comment.content }}</text>
					<text class="comment-context">留言于作品《{{ workTitle }}》</text>
				</template>
			</view>

			<!-- 当前状态 -->
			<view class="appeal-card" role="status">
				<text class="card-eyebrow">现在的展示状态</text>
				<template v-if="targetType === 'work'">
					<view v-if="softProtected" class="status-row">
						<text class="status-badge limited">展示受限 · 保护中</text>
						<text class="status-remain">{{ softRemain }}</text>
					</view>
					<view v-else class="status-row">
						<text class="status-badge normal">展示正常</text>
					</view>
					<text v-if="softProtected" class="status-help">
						因内容涉及情绪表达，这条作品暂时排在社区较靠后的位置；这是保护性的临时安排，不代表内容有问题，也只有你能看到这条提示。
					</text>
					<text v-else class="status-help">
						这条作品目前在社区正常展示。如果你收到过「展示受限」的通知，说明保护期已结束或复核已完成。
					</text>
				</template>
				<template v-else>
					<text class="status-help">
						留言的展示安排以保护通知为准：保护期内它会排在较靠后的位置，到期或复核完成后自动恢复；这些提示只有你自己可见。
					</text>
				</template>
			</view>

			<!-- 申诉（作品仅保护期内/已提交/有结果口径时展示，正常展示状态下整卡隐藏，不留空占位） -->
			<view v-if="targetType === 'comment' || softProtected || appealed || appealNote" class="appeal-card">
				<text class="card-eyebrow">想让更多人看到它？</text>

				<view v-if="appealed" class="appealed-block" role="status">
					<XiaotuanPet :size="64" face="happy" aria-hidden="true" />
					<text class="appealed-title">已收到，正在加急人工复核</text>
					<text class="appealed-desc">复核结果会通过消息通知告诉你；无论结果如何，这条内容都会在说明的期限后恢复正常展示。</text>
					<AccessibleButton class="notice-btn" @click="goNotifications">查看消息通知</AccessibleButton>
				</view>

				<template v-else-if="canAppeal">
					<text class="appeal-desc">
						如果你认为这条内容可以正常出现在大家面前，写一句理由告诉我们，我们会尽快人工复核。重复提交不会重复记录，点一次就好。
					</text>
					<FormField
						class="reason-field"
						label="说说你的想法"
						required
						hint="200 字以内，只给复核伙伴看，不会公开。"
						:error="reasonError"
						:maxlength="REASON_MAX"
						:count="reason.length"
					>
						<template #default="{ id, ariaDescribedby, ariaInvalid }">
							<textarea
								:id="id"
								v-model="reason"
								class="reason-input"
								:aria-describedby="ariaDescribedby"
								:aria-invalid="ariaInvalid"
								:maxlength="REASON_MAX"
								:disabled="submitting"
								:adjust-position="true"
								:cursor-spacing="24"
								placeholder="例如：这是我记录日常的一段文字，希望它能正常展示…"
								@input="reasonError = ''"
							/>
						</template>
					</FormField>
					<AccessibleButton class="appeal-btn" :disabled="!reasonText || submitting" :loading="submitting" @click="submitAppeal">
						{{ submitting ? '正在提交…' : '申请人工复核' }}
					</AccessibleButton>
				</template>

				<view v-else class="appeal-note" role="status">
					<text class="status-help">{{ appealNote }}</text>
					<AccessibleButton class="notice-btn" @click="goNotifications">查看消息通知</AccessibleButton>
				</view>

				<text v-if="submitError" class="submit-error" role="alert">{{ submitError }}</text>
			</view>

		</view>

		<Toast />
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { TOKEN_KEY } from '../../utils/config'
import { softActive, softRemainText } from '../../utils/soft-tier'
import StateBlock from '../../components/StateBlock.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import FormField from '../../components/FormField.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import Toast from '../../components/Toast.vue'
import { useToast } from '../../composables/useToast'

/**
 * 「展示受限」说明与申诉页（2026-11-07 处置链，设计 §2.3 逃生通道）。
 *
 * 入口：作品详情「展示受限」提示条、SOFT_TIER_NOTICE 通知。
 * 契约：
 *  - 作品状态数据源 = GET /api/works/{id} 的 softDemoteUntil（仅作者本人下发）；
 *  - 评论契约无软档字段 → 状态以通知文案 + 申诉接口错误码驱动（1004 无进行中处置 / 3001 已终裁）；
 *  - POST /api/soft-tier-appeals 幂等：重复提交直接成功，不产生多条记录。
 * 文案纪律：不出现「违规 / 处罚 / 降权 / 折叠 / 限流」，基调是保护性说明。
 */
const toast = useToast()

const targetType = ref('work')
const targetId = ref('')
const workId = ref('')

const loading = ref(false)
const loadError = ref('')
const notFound = ref(false)
const work = ref(null)
const comment = ref(null)
const workTitle = ref('')

const submitting = ref(false)
const appealed = ref(false)
const submitError = ref('')
const appealNote = ref('')

/* 申诉理由（2026-11-07 契约补项）：必填、纯文本、上限 200 字。
 * REASON_MAX 为前端校验常量——若与后端口径不一致，以后端为准（openapi 补登后对齐）。 */
const REASON_MAX = 200
const reason = ref('')
const reasonError = ref('')
const reasonText = computed(() => reason.value.trim())

const softProtected = computed(() => targetType.value === 'work' && softActive(work.value && work.value.softDemoteUntil))
const softRemain = computed(() => (softProtected.value ? softRemainText(work.value.softDemoteUntil) : ''))
/** 作品：仅在保护期内展示申诉入口；评论：契约无状态字段，入口常开、由接口口径兜底。 */
const canAppeal = computed(() => !appealNote.value && (targetType.value === 'comment' || softProtected.value))

onLoad((query) => {
	targetType.value = query.targetType === 'comment' ? 'comment' : 'work'
	targetId.value = String(query.targetId || '')
	workId.value = String(query.workId || '')
	if (!uni.getStorageSync(TOKEN_KEY)) {
		uni.reLaunch({ url: '/pages/login/index' })
		return
	}
	if (!targetId.value || (targetType.value === 'comment' && !workId.value)) {
		notFound.value = true
		return
	}
	loadPage()
})

async function loadPage() {
	if (loading.value) return
	loading.value = true
	loadError.value = ''
	notFound.value = false
	try {
		if (targetType.value === 'work') {
			work.value = await http.get(`/api/works/${targetId.value}`)
		} else {
			const workData = await http.get(`/api/works/${workId.value}`)
			workTitle.value = (workData && workData.title) || '未命名作品'
			comment.value = await findOwnComment()
			if (!comment.value) {
				notFound.value = true
				return
			}
		}
	} catch (e) {
		if (e.code === 1004) notFound.value = true
		else loadError.value = e.message || '加载失败，请重试'
	} finally {
		loading.value = false
	}
}

/* 评论契约无单条查询端点：沿作品评论列表（公开 + 本人可见，size≤50）定位本人该条。
 * 最多翻 4 页（200 条），找不到按空态处理，不伪造原文。 */
async function findOwnComment() {
	for (let page = 1; page <= 4; page += 1) {
		const data = await http.get(`/api/works/${workId.value}/comments`, { data: { page, size: 50 } })
		const records = (data && data.records) || []
		const hit = records.find((item) => String(item.id) === targetId.value && item.mine)
		if (hit) return hit
		const total = Number(data && data.total) || 0
		if (page * 50 >= total) break
	}
	return null
}

async function submitAppeal() {
	if (submitting.value || appealed.value) return
	const text = reasonText.value
	reasonError.value = ''
	if (!text) {
		reasonError.value = '先写一句理由，再提交复核。'
		return
	}
	if (text.length > REASON_MAX) {
		reasonError.value = `理由请控制在 ${REASON_MAX} 字以内。`
		return
	}
	submitting.value = true
	submitError.value = ''
	try {
		// targetId 按字符串传递（雪花；后端 Jackson 转 Long，避免 JS Number 精度丢失）；
		// reason 为去首尾空白后的纯文本，后端过内容安全过滤。
		await http.post('/api/soft-tier-appeals', { targetType: targetType.value, targetId: targetId.value, reason: text })
		appealed.value = true
		toast.success('已收到，正在加急复核')
		// 作品侧申诉后降权窗口延长为「复核期间」，刷新状态口径（复核完成后恢复）
		if (targetType.value === 'work') {
			try { work.value = await http.get(`/api/works/${targetId.value}`) } catch (e) { /* 状态展示以本次提交为准 */ }
		}
	} catch (e) {
		if (e.code === 1004) {
			appealNote.value = '这条内容当前没有进行中的展示受限安排，可能已恢复正常展示。'
			if (targetType.value === 'work') {
				try { work.value = await http.get(`/api/works/${targetId.value}`) } catch (err) { /* 保留当前展示 */ }
			}
		} else if (e.code === 3001) {
			appealNote.value = '这条内容的复核已经有结果了，结果会通过消息通知告诉你。'
		} else if (e.code === 1003) {
			submitError.value = '只能对自己分享的内容提交复核申请。'
		} else if (e.code === 1001) {
			submitError.value = '这个功能暂时没有开放，稍后再来看看。'
		} else {
			submitError.value = (e.message || '提交没有成功') + '，请稍后重试。'
		}
	} finally {
		submitting.value = false
	}
}

function goNotifications() {
	uni.navigateTo({ url: '/pages/notification/index' })
}
function goBack() {
	uni.navigateBack({ fail: () => uni.navigateTo({ url: '/pages/notification/index' }) })
}
</script>

<style scoped>
.appeal-page { min-height: 100vh; padding: 0 28rpx 120rpx; box-sizing: border-box; background: var(--zy-bg, #ffffff); }





.appeal-body { max-width: 760px; margin: 12rpx auto 0; display: flex; flex-direction: column; gap: 24rpx; }
.appeal-card { background: var(--zy-card-bg, #fff); border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 28rpx; padding: 28rpx; display: flex; flex-direction: column; gap: 16rpx; min-width: 0; }
.card-eyebrow { font-size:var(--zy-text-micro, 22rpx); letter-spacing: 2rpx; color: var(--zy-brand-700, var(--zy-brand-700, #24478f)); font-weight: 600; }

.work-title { font-family: var(--zy-serif); font-size:var(--zy-text-title, 32rpx); font-weight: 600; color: var(--zy-title, #233d34); line-height: 1.5; overflow-wrap: anywhere; }
.work-message { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-text, #4a5f54); line-height: 1.8; overflow-wrap: anywhere; white-space: pre-wrap; }
.work-message.muted { opacity: .8; }
.comment-content { font-size:var(--zy-text-body, 28rpx); color: var(--zy-title, #233d34); line-height: 1.8; overflow-wrap: anywhere; white-space: pre-wrap; }
.comment-context { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-text, #4a5f54); line-height: 1.6; overflow-wrap: anywhere; }

.status-row { display: flex; align-items: center; gap: 16rpx; flex-wrap: wrap; }
.status-badge { flex-shrink: 0; padding: 6rpx 18rpx; border-radius: var(--zy-radius-full, 9999rpx); color: #fff; font-size:var(--zy-text-micro, 22rpx); font-weight: 600; }
.status-badge.limited { background: var(--zy-warning-text, var(--zy-accent, #7a5c12)); }
.status-badge.normal { background: var(--zy-brand-600, var(--zy-brand-600, #24478f)); }
.status-remain { font-size:var(--zy-text-micro, 22rpx); color: var(--zy-warning-text, var(--zy-accent, #7a5c12)); line-height: 1.6; }
.status-help { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-text, #4a5f54); line-height: 1.8; overflow-wrap: anywhere; }

.appeal-desc { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-text, #4a5f54); line-height: 1.8; overflow-wrap: anywhere; }
.reason-field { display: block; }
.reason-input { width: 100%; height: 180rpx; padding: 20rpx; box-sizing: border-box; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 20rpx; background: var(--zy-bg, #ffffff); font-size:var(--zy-text-caption, 24rpx); line-height: 1.7; color: var(--zy-title, #233d34); }
.appeal-btn { align-self: flex-start; margin: 4rpx 0 0; padding: 16rpx 44rpx; min-height: 44px; border-radius: var(--zy-radius-full, 9999rpx); background: var(--zy-brand-600, var(--zy-brand-700, #24478f)); color: #fff; font-size:var(--zy-text-caption, 24rpx); line-height: 1.5; }
.appeal-btn::after { border: 0; }

.appealed-block { display: flex; flex-direction: column; align-items: flex-start; gap: 14rpx; }
.appealed-title { font-size:var(--zy-text-body, 28rpx); font-weight: 600; color: var(--zy-title, #233d34); line-height: 1.6; }
.appealed-desc { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-text, #4a5f54); line-height: 1.8; overflow-wrap: anywhere; }
.appeal-note { display: flex; flex-direction: column; align-items: flex-start; gap: 14rpx; }
.notice-btn { align-self: flex-start; margin: 0; padding: 12rpx 32rpx; min-height: 44px; border-radius: var(--zy-radius-full, 9999rpx); background: var(--zy-bg-tint-mint, #eef3fb); color: var(--zy-brand-700, var(--zy-brand-700, #24478f)); font-size:var(--zy-text-caption, 24rpx); line-height: 1.5; }
.notice-btn::after { border: 0; }

.submit-error { font-size:var(--zy-text-caption, 24rpx); color: var(--zy-care, var(--zy-care, #a64842)); line-height: 1.7; overflow-wrap: anywhere; }


.appeal-btn:focus-visible,
.notice-btn:focus-visible { outline: 3px solid var(--zy-brand-700, var(--zy-brand-700, #24478f)); outline-offset: 3px; }
</style>
