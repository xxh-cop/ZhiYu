import { defineStore } from 'pinia'
import { startSSE } from '../utils/sse'
import { request } from '../utils/request'
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

/**
 * AK 灰度（2026-11-04）：调度层统一端点 /api/dialogue/chat 的可用性探测状态。
 * unknown → 先试新端点；404/1004（端点未注册，开关关闭）→ 静默回落旧端点并标记
 * unavailable；成功流出则标记 available。仅页面会话内记忆，不持久化（开关是
 * 部署期决策，刷新页面重新探测即可，避免缓存卡住灰度切换）。
 */
let dialogueEndpointState = 'unknown'

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
		/** 最近一次错误的业务码（1002/1003/…，0 表示无）；配合 errorMsg 做引导态判断 */
		errorCode: 0,
		/** 滚动信号——每次消息变化时递增，组件 watch 此值触发滚动到底部 */
		scrollSignal: 0,
		/** 是否保留消息（面板关闭后跳 chat.vue 时不 reset） */
		preserveMessages: false,
		/** 危机干预触发标记——SSE done 后检测关怀话术命中 */
		crisisTriggered: false,
		/**
		 * 当前是否处于本地兜底模式（2026-11-08 装配层批）：
		 * 由 SSE done 的可选 provider 字段驱动——ollama=true、云端值=false、
		 * 不下发（危机话术等非模型产出）时保持原值。只作展示提示，不影响任何流程。
		 */
		localMode: false,
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
			this.errorCode = 0
			this.preserveMessages = false
			this.crisisTriggered = false
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

		/** revoke 事件：整体替换最后一条 assistant 消息（安全终检撤回，温和替换不提示） */
		replaceLastAssistant(text) {
			const last = this.messages[this.messages.length - 1]
			if (last && last.role === 'assistant' && !last.done) {
				last.text = text
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
			this.errorCode = 0
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

		/** 重试上一次发送（先查历史补全，已回复则不重复调模型） */
		async retryLast() {
			if (this.streaming) return
			this.errorMsg = ''

			// 断线补全：后端可能已生成回复但前端断线未收到；末条已是 assistant 则直接补全展示
			if (this.sessionId) {
				try {
					const detail = await request({ url: '/api/emotion/sessions/' + this.sessionId })
					const history = (detail && detail.messages) || []
					const lastHistory = history[history.length - 1]
					if (lastHistory && lastHistory.role === 'assistant') {
						const last = this.messages[this.messages.length - 1]
						if (last && last.role === 'assistant' && !last.text) {
							last.text = lastHistory.content
							last.done = true
						}
						this.streaming = false
						this._triggerScroll()
						const aiStore = useAiCompanionStore()
						aiStore.stopTalking()
						return
					}
				} catch (e) {
					// 查不到历史就走正常重发（后端对用户消息幂等，不会重复落库）
				}
			}

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
		 * AK 灰度：unknown 状态先试新端点 /api/dialogue/chat（调度层统一入口）；
		 * 端点未注册（404+1004）时静默回落旧 /api/emotion/chat，用户无感。
		 *
		 * @param {string} content 要发送的文本
		 */
		_startSSE(content) {
			this._startSSEVia(content, dialogueEndpointState !== 'unavailable')
		},

		/**
		 * @param {string} content 要发送的文本
		 * @param {boolean} useDialogue true=走调度层新端点；false=走旧情绪端点
		 */
		_startSSEVia(content, useDialogue) {
			// 中断旧 SSE（如果有）
			if (sseController) {
				sseController.abort()
				sseController = null
			}

			const userStore = useUserStore()

			// 首 token 标记：用于 thinking → talking 切换
			let isFirstToken = true

			sseController = startSSE({
				url: API_BASE_URL + (useDialogue ? '/api/dialogue/chat' : '/api/emotion/chat'),
				body: useDialogue
					? {
							sessionId: this.sessionId,
							text: content,
							entryContext: 'LISTENING_PAGE',
						}
					: {
							sessionId: this.sessionId,
							content: content,
							mode: 'live',
						},
				token: userStore.token,
				onMessage: (text) => {
					// 兼容旧后端整段格式：单事件全文
					if (isFirstToken) {
						isFirstToken = false
						if (useDialogue) dialogueEndpointState = 'available'
						const aiStore = useAiCompanionStore()
						aiStore.showTalking()
					}
					this.appendAssistantChunk(text)
					this._triggerScroll()
				},
				onEvent: (name, data) => {
					// SSE A 方案（mode=live）：chunk 句级增量 / revoke 整体替换
					if (name === 'chunk') {
						if (isFirstToken) {
							isFirstToken = false
							if (useDialogue) dialogueEndpointState = 'available'
							const aiStore = useAiCompanionStore()
							aiStore.showTalking()
						}
						this.appendAssistantChunk((data && data.text) || '')
						this._triggerScroll()
					} else if (name === 'revoke') {
						this.replaceLastAssistant((data && data.text) || '')
						this._triggerScroll()
					} else if (data && data.raw) {
						// 兼容旧协议无名事件的纯文本（解析不了 JSON 时 sse.js 以 raw 透传）
						if (isFirstToken) {
							isFirstToken = false
							if (useDialogue) dialogueEndpointState = 'available'
							const aiStore = useAiCompanionStore()
							aiStore.showTalking()
						}
						this.appendAssistantChunk(data.raw)
						this._triggerScroll()
					}
				},
			onDone: (data) => {
				if (useDialogue) dialogueEndpointState = 'available'
				this.finishAssistant()
				const lastMsg = this.messages[this.messages.length - 1]
				if (lastMsg && lastMsg.role === 'assistant' && data) {
					// AK：新端点 done 多 capability/toolUsed——仅记录到消息元数据供调试与
					// 灰度评估，渲染层不展示（倾听页无该信息位，不改变用户看到的内容结构）
					if (data.capability) lastMsg.capability = data.capability
					if (data.toolUsed) lastMsg.toolUsed = data.toolUsed
					// 2026-11-08 装配层批：归属标注只对「真实走了本地」成立。
					// 后端仅在展示文本确为模型产出时下发 provider（危机话术/安全改写不下发）——
					// 无 provider 时本条不标，localMode 维持原值；云端值（deepseek 等）明确复位。
					if (data.provider) {
						lastMsg.provider = data.provider
						this.localMode = data.provider === 'ollama'
					}
				}
				if (data && data.sessionId) {
					this.setSessionId(data.sessionId)
				}
				this.streaming = false
				this._triggerScroll()
				// 【触发点 3】SSE 完成 → 延迟回 idle
				const aiStore = useAiCompanionStore()
				aiStore.stopTalking()

				// 危机标志以 SSE done 事件为准；旧后端未下发 crisis 时才退回关键词识别。
				if (data && typeof data.crisis === 'boolean') {
					this.crisisTriggered = data.crisis
				} else {
					this._checkCrisis()
				}
			},
				onError: (err) => {
					// AK 灰度回落：新端点未注册（开关关闭 → 404+1004）→ 标记并静默回落旧端点。
					// 不弹空气泡、不写错误态、不提示——发送中的气泡保持，旧端点继续流出。
					if (useDialogue && err && err.code === 1004) {
						dialogueEndpointState = 'unavailable'
						this._startSSEVia(content, false)
						return
					}
					// 错误时移除空 assistant 占位气泡，错误态统一由 errorMsg/引导块呈现
					const lastMsg2 = this.messages[this.messages.length - 1]
					if (lastMsg2 && lastMsg2.role === 'assistant' && !lastMsg2.text) {
						this.messages.pop()
					}
					this.finishAssistant()
					this.streaming = false
					this.errorMsg = (err && err.message) || '服务暂时不可用，请稍后重试'
					this.errorCode = (err && typeof err.code === 'number' && err.code) || 0
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

		/**
		 * 旧后端兼容：done 事件缺少 crisis 字段时，按关怀话术关键词兜底识别。
		 * 新后端应优先使用 done.crisis。
		 */
		_checkCrisis() {
			const last = this.messages[this.messages.length - 1]
			if (!last || last.role !== 'assistant' || !last.text) return
			const text = last.text
			if (text.includes('心理中心') || text.includes('热线') || text.includes('你并不孤单')) {
				this.crisisTriggered = true
			}
		},

		/** 关闭危机卡片（用户点「继续聊聊」） */
		dismissCrisis() {
			this.crisisTriggered = false
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
