import { defineStore } from 'pinia'
import { invalidate, TTL_KEYS } from '../utils/ttlCache'

/**
 * 创作会话 Pinia store（T1.9b）。
 *
 * 管理创作会话的全局状态：sessionId / steps / currentStep / flowState。
 */
export const useCraftStore = defineStore('craft', {
	state: () => ({
		/** 当前创作会话 ID */
		sessionId: null,
		/** 当前项目 ID */
		projectId: null,
		/** 项目标题 */
		projectTitle: '',
		/** 步骤列表 [{ id, stepNo, title, guide, audioUrl }] */
		steps: [],
		/** 当前步骤号（从 1 开始） */
		currentStep: 1,
		/** 总步骤数 */
		totalSteps: 0,
		/** 会话状态码：0待开始 1进行中 2已暂停 3已完成 4已放弃 */
		status: 0,
		/** 是否处于心流状态 */
		inFlow: false,
		/** 是否需要呼吸引导 */
		needBreathing: false,
		/* ── BR（2026-11-05 分层下架）：listSteps 结构化响应的包装字段（字段名与后端一致） ── */
		/** 有效项目状态：1 上架 / 2 下架 / 3 归档 */
		projectStatus: 1,
		/** 会话只读：true 时不可推进步骤（仅回看） */
		readOnly: false,
		/** 可回看历史步骤 */
		canReview: true,
		/** 可导出创作数据标记（导出端点尚未建立，仅作截止说明展示，不渲染按钮） */
		canExport: false,
		/** 导出/只读窗口截止（ISO 字符串或 null） */
		exportUntil: null,
		/** 归档时间/截止（ISO 字符串或 null） */
		archiveAt: null,
		/** 提示语模板 key */
		noticeKey: '',
		/** 后端渲染好的提示文案（原样展示，前端不另写） */
		notice: '',
	}),

	actions: {
		/** 重置所有状态 */
		reset() {
			this.sessionId = null
			this.projectId = null
			this.projectTitle = ''
			this.steps = []
			this.currentStep = 1
			this.totalSteps = 0
			this.status = 0
			this.inFlow = false
			this.needBreathing = false
			this.projectStatus = 1
			this.readOnly = false
			this.canReview = true
			this.canExport = false
			this.exportUntil = null
			this.archiveAt = null
			this.noticeKey = ''
			this.notice = ''
		},

		/** 从 CraftSessionVO 初始化会话 */
		setSession(vo) {
			this.sessionId = vo.sessionId
			this.projectId = vo.projectId
			this.projectTitle = vo.projectTitle || ''
			this.status = vo.status || 0
			this.currentStep = vo.currentStep || 1
			this.totalSteps = vo.totalSteps || 0
		},

		/** 设置步骤列表 */
		setSteps(list) {
			this.steps = list || []
		},

		/**
		 * BR（2026-11-05）：消费 listSteps 的结构化响应。
		 * 拆出步骤数组写入 steps，并把包装字段原样透出（字段名与后端一致）。
		 * 兼容旧裸数组响应（防御：若后端回退，steps 直接是数组）。
		 */
		setStepsPayload(payload) {
			const p = payload || {}
			const list = Array.isArray(p) ? p : (p.steps || [])
			this.setSteps(list)
			if (Array.isArray(p)) {
				this.projectStatus = 1
				this.readOnly = false
				this.canReview = true
				this.canExport = false
				this.exportUntil = null
				this.archiveAt = null
				this.noticeKey = ''
				this.notice = ''
				return
			}
			this.projectStatus = p.projectStatus ?? 1
			this.readOnly = Boolean(p.readOnly)
			this.canReview = p.canReview !== false
			this.canExport = Boolean(p.canExport)
			this.exportUntil = p.exportUntil || null
			this.archiveAt = p.archiveAt || null
			this.noticeKey = p.noticeKey || ''
			this.notice = p.notice || ''
		},

		/** 步骤前进 */
		stepForward() {
			if (this.currentStep < this.totalSteps) {
				this.currentStep++
			}
		},

		/** 步骤回退 */
		stepBack() {
			if (this.currentStep > 1) {
				this.currentStep--
			}
		},

		/** 更新心流状态 */
		setFlowState(inFlow, needBreathing) {
			this.inFlow = inFlow
			this.needBreathing = needBreathing
		},

		/** 标记完成 */
		finish() {
			this.status = 3
			// X2：会话统计/进行中缓存即刻失效（首页回到 30s 前的数据不可接受）
			invalidate(TTL_KEYS.userStats, TTL_KEYS.craftSessionActive)
		},
	},
})
