import { defineStore } from 'pinia'
import { startSSE } from '../utils/sse'
import { useUserStore } from './user'
import { useAiCompanionStore } from './aiCompanion'
import { API_BASE_URL } from '../utils/config.js'

/**
 * 情绪对话 Pinia store（T1.9a → M2 重构）。
 *
 * M2 变更：SSE 逻辑从 chat.vue 抽取到 store，ChatPanel 与 chat.vue 共用。
 * 管理 sessionId + 消息列表 + 流式状态 + SSE 控制器。
 *
 * sseController 是模块级变量（不需要响应式），在 reset() 中必须清空。
 */
let sseController = null

export const useEmotionStore = defineStore('emotion', {
	state: () => ({
		/** 当前会话 ID，null 表示新会话（后端创建后返回） */
		sessionId: null,
		/** 消息列表 [{ role: 'user'|'assistant', text: '', done: false }] */
		messages: [],
		/** 是否正在流式接收 */
		streaming: false,
		/** 错误信息（空字符串表示无错误） */
		errorMsg: '',
		/** 滚动信号——每次消息变化时递增，组件 watch 此值触发滚动到底部 */
		scrollSignal: 0,
		/** 是否保留消息（面板关闭后跳 chat.vue 时不 reset） */
		preserveMessages: false,
	}),

	actions: {
		/**
		 * 重置会话：中断 SSE + 清空消息 + 重置 sessionId。
		 * chat.vue onLoad 在 preserveMessages=false 时调此方法。
		 */
		reset() {
			this.abortSSE()
			this.sessionId = null
			this.messages = []
			this.streaming = false
			this.errorMsg = ''
			this.preserveMessages = false
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

		/** 触发滚动到底部（组件 watch scrollSignal） */
		_triggerScroll() {
			this.scrollSignal++
		},

		/**
		 * 发送消息 + 启动 SSE。
		 * chat.vue 和 ChatPanel 共用此方法。
		 *
		 * @param {string} content 用户输入文本
		 */
		sendMessage(content) {
			if (this.streaming) return
			if (!content || !content.trim()) return

			this.errorMsg = ''
			this._lastSentContent = content

			// 1. 追加用户消息
			this.appendUser(content)
			// 2. 开始 assistant 消息
			this.startAssistant()
			// 3. 禁用发送
			this.streaming = true
			this._triggerScroll()

			// 【触发点 1】用户发消息后 → 小织变 thinking
			const aiStore = useAiCompanionStore()
			aiStore.showThinking()

			// 4. 发起 SSE
			this._startSSE(content)
		},

		/** 重试上一次发送 */
		retryLast() {
			if (this.streaming) return
			this.errorMsg = ''
			// 移除上一条空的 assistant 消息
			if (this.messages.length > 0) {
				const last = this.messages[this.messages.length - 1]
				if (last.role === 'assistant' && !last.text) {
					this.messages.pop()
				}
			}
			// 重新发送
			this.startAssistant()
			this.streaming = true
			this._triggerScroll()

			// 【触发点 1】重试也触发 thinking
			const aiStore = useAiCompanionStore()
			aiStore.showThinking()

			// 发起 SSE（用上次发送的内容）
			this._startSSE(this._lastSentContent)
		},

		/**
		 * 内部方法：发起 SSE 请求并绑定回调。
		 * sendMessage 和 retryLast 共用，消除重复代码。
		 *
		 * @param {string} content 要发送的文本
		 */
		_startSSE(content) {
			// 中断旧 SSE（如果有）
			if (sseController) {
				sseController.abort()
				sseController = null
			}

			const userStore = useUserStore()

			// 首 token 标记：用于 thinking → talking 切换
			let isFirstToken = true

			sseController = startSSE({
				url: API_BASE_URL + '/api/emotion/chat',
				body: {
					sessionId: this.sessionId,
					content: content,
				},
				token: userStore.token,
				onMessage: (text) => {
					// 【触发点 2】收到第一个 token → 小织变 talking
					if (isFirstToken) {
						isFirstToken = false
						const aiStore = useAiCompanionStore()
						aiStore.showTalking()
					}
					this.appendAssistantChunk(text)
					this._triggerScroll()
				},
				onDone: (data) => {
					this.finishAssistant()
					if (data && data.sessionId) {
						this.setSessionId(data.sessionId)
					}
					this.streaming = false
					this._triggerScroll()
					// 【触发点 3】SSE 完成 → 延迟回 idle
					const aiStore = useAiCompanionStore()
					aiStore.stopTalking()
				},
				onError: (err) => {
					this.finishAssistant()
					this.streaming = false
					this.errorMsg = (err && err.message) || '服务暂时不可用，请稍后重试'
					this._triggerScroll()
					// 【触发点 3+4】SSE 错误 → 回 idle 或变 caring
					const aiStore = useAiCompanionStore()
					if (err && err.code === 1001) {
						// 内容安全过滤触发 → 关怀态
						aiStore.showCaring()
					} else {
						// 其他错误 → 立即回 idle
						aiStore.setPetState('idle')
					}
				},
			})
		},

		/** 中断当前 SSE（面板关闭 / 页面卸载时调） */
		abortSSE() {
			if (sseController) {
				sseController.abort()
				sseController = null
			}
			this.streaming = false
		},
	},
})
