import { defineStore } from 'pinia'

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
		},
	},
})
