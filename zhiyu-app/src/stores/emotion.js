import { defineStore } from 'pinia'

/**
 * 情绪对话 Pinia store（T1.9a）。
 *
 * 管理 sessionId + 消息列表 + 流式状态。
 * 每次进入 chat 页调 reset()，sessionId 重置为 null。
 */
export const useEmotionStore = defineStore('emotion', {
	state: () => ({
		/** 当前会话 ID，null 表示新会话（后端创建后返回） */
		sessionId: null,
		/** 消息列表 [{ role: 'user'|'assistant', text: '', done: false }] */
		messages: [],
		/** 是否正在流式接收 */
		streaming: false,
	}),

	actions: {
		/** 清空消息列表 + 重置 sessionId（每次进入 chat 页调一次） */
		reset() {
			this.sessionId = null
			this.messages = []
			this.streaming = false
		},

		/** 追加用户消息 */
		appendUser(text) {
			this.messages.push({ role: 'user', text, done: true })
		},

		/** 开始一条新的 assistant 消息（空文本，等待流式追加） */
		startAssistant() {
			this.messages.push({ role: 'assistant', text: '', done: false })
		},

		/** 追加 chunk 到当前（最后一条）assistant 消息 */
		appendAssistantChunk(text) {
			const last = this.messages[this.messages.length - 1]
			if (last && last.role === 'assistant' && !last.done) {
				last.text += text
			}
		},

		/** 标记当前 assistant 消息完成 */
		finishAssistant() {
			const last = this.messages[this.messages.length - 1]
			if (last && last.role === 'assistant') {
				last.done = true
			}
		},

		/** 设置会话 ID（从 done 事件返回后保存，用于连续对话） */
		setSessionId(id) {
			this.sessionId = id
		},
	},
})
