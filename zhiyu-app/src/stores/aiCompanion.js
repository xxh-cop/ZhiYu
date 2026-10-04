import { defineStore } from 'pinia'

const POS_KEY = 'ai-companion-pos'

const DEFAULT_POS = { right: 40, bottom: 160 }

const BUBBLE_TEXTS = [
	'今天也慢慢来呀～',
	'记得给自己一点时间',
	'需要我陪你聊聊吗？',
	'你已经很棒了',
	'深呼吸，一切都会好的',
	'累了就歇一会儿吧',
]

// ── 状态优先级（高 → 低）──
const STATE_PRIORITY = {
	caring: 5,
	happy: 4,
	thinking: 3,
	talking: 2,
	idle: 1,
}

// ── petState → 图片路径映射 ──
const STATE_IMAGE_MAP = {
	idle: '/static/xiaozhi-avatar.png',
	thinking: '/static/xiaozhi-thinking.png',
	talking: '/static/xiaozhi-talk.png',
	happy: '/static/xiaozhi-happy.png',
	caring: '/static/xiaozhi-caring.png',
}

function readPos() {
	try {
		const saved = uni.getStorageSync(POS_KEY)
		if (saved && typeof saved.right === 'number' && typeof saved.bottom === 'number') {
			return saved
		}
	} catch (e) {
		/* 存储不可用时用默认值 */
	}
	return { ...DEFAULT_POS }
}

export const useAiCompanionStore = defineStore('aiCompanion', {
	state: () => ({
		visible: true,
		position: readPos(),
		mode: 'idle', // 'idle' | 'breathing'
		/* ── 多状态机 ── */
		petState: 'idle', // 'idle' | 'thinking' | 'talking' | 'happy' | 'caring'
		isTalking: false,
		/* blink 叠加层 */
		isBlinking: false,
		// 气泡
		showBubble: false,
		bubbleText: '',
		bubbleSide: 'left', // 'left' | 'right'
		// 隐藏
		isHidden: false,
		// 拖动状态
		isDragging: false,
	}),

	getters: {
		/** 当前主状态对应的图片路径（不含 blink 叠加） */
		currentImagePath() {
			return STATE_IMAGE_MAP[this.petState] || STATE_IMAGE_MAP.idle
		},
	},

	actions: {
		setPosition(right, bottom) {
			this.position = { right, bottom }
			try {
				uni.setStorageSync(POS_KEY, { right, bottom })
			} catch (e) {
				/* 存储不可用时只保留内存态 */
			}
		},

		loadPosition() {
			this.position = readPos()
		},

		startBreathing() {
			this.mode = 'breathing'
		},

		stopBreathing() {
			this.mode = 'idle'
		},

		toggleVisible() {
			this.visible = !this.visible
		},

		setDragging(val) {
			this.isDragging = val
		},

		/* ═══════════════════════════════════════ *
		 *  多状态机
		 * ═══════════════════════════════════════ */

		/**
		 * 切换 petState。
		 * - caring / happy：高优先级临时态，覆盖一切
		 * - thinking / talking：SSE 流程驱动，互相可覆盖
		 * - idle：默认态，可被任何状态覆盖
		 * - duration > 0 时，到期后自动回 idle（或 talking）
		 */
		setPetState(state, duration = 0) {
			// 优先级检查：只阻止低优先级覆盖高优先级临时态（caring/happy）
			// thinking ↔ talking 互相可覆盖（SSE 流程正常切换）
			const currentPrio = STATE_PRIORITY[this.petState] || 0
			const newPrio = STATE_PRIORITY[state] || 0
			const currentIsTemp = this.petState === 'caring' || this.petState === 'happy'
			if (currentIsTemp && newPrio < currentPrio) {
				// 当前是临时态（caring/happy），新状态优先级更低 → 拒绝
				return
			}

			this.petState = state

			// 清理旧定时器
			if (this._tempStateTimer) {
				clearTimeout(this._tempStateTimer)
				this._tempStateTimer = null
			}

			if (duration > 0) {
				this._tempStateTimer = setTimeout(() => {
					// 临时态到期：如果正在 talking 则回 talking，否则回 idle
					this.petState = this.isTalking ? 'talking' : 'idle'
					this._tempStateTimer = null
				}, duration)
			}
		},

		showThinking() {
			this.setPetState('thinking')
		},

		showTalking() {
			this.setPetState('talking')
		},

		showHappy() {
			this.setPetState('happy', 2000)
		},

		showCaring() {
			this.setPetState('caring', 3000)
		},

		/**
		 * 延迟 1s 回 idle，让用户看到 talking 结束。
		 * 只在 thinking / talking 时生效。
		 */
		resetToIdle() {
			if (this._resetTimer) clearTimeout(this._resetTimer)
			this._resetTimer = setTimeout(() => {
				if (this.petState === 'talking' || this.petState === 'thinking') {
					this.setPetState('idle')
				}
				this._resetTimer = null
			}, 1000)
		},

		/* ═══════════════════════════════════════ *
		 *  眨眼（叠加层，不改主状态）
		 * ═══════════════════════════════════════ */
		blink() {
			if (this.isBlinking) return

			this.isBlinking = true

			if (this._blinkTimer) clearTimeout(this._blinkTimer)

			this._blinkTimer = setTimeout(() => {
				this.isBlinking = false
				this._blinkTimer = null
			}, 150)
		},

		/* ═══════════════════════════════════════ *
		 *  说话（SSE 流式驱动）
		 * ═══════════════════════════════════════ */
		startTalking() {
			if (this.isTalking) return
			this.isTalking = true
			this.showTalking()
		},

		stopTalking() {
			this.isTalking = false
			this.resetToIdle()
		},

		/* ═══════════════════════════════════════ *
		 *  气泡
		 * ═══════════════════════════════════════ */
		showRandomBubble() {
			this.bubbleText = BUBBLE_TEXTS[Math.floor(Math.random() * BUBBLE_TEXTS.length)]

			// 根据小织当前位置自动判断气泡方向（朝屏幕内侧展开）
			const r = this.position.right
			const leftEdge = 750 - r - 140
			if (r < 100) {
				this.bubbleSide = 'left'
			} else if (leftEdge < 100) {
				this.bubbleSide = 'right'
			} else {
				this.bubbleSide = r > 375 ? 'left' : 'right'
			}

			this.showBubble = true

			if (this._bubbleTimer) {
				clearTimeout(this._bubbleTimer)
			}
			this._bubbleTimer = setTimeout(() => {
				this.showBubble = false
				this._bubbleTimer = null
			}, 3000)
		},

		hideBubble() {
			this.showBubble = false
			if (this._bubbleTimer) {
				clearTimeout(this._bubbleTimer)
				this._bubbleTimer = null
			}
		},

		/* ═══════════════════════════════════════ *
		 *  隐藏 / 恢复
		 * ═══════════════════════════════════════ */
		hide() {
			this.isHidden = true
			this.visible = false
			this.hideBubble()
		},

		unhide() {
			this.isHidden = false
			this.visible = true
			// 回到默认位置
			this.position = { ...DEFAULT_POS }
			try {
				uni.setStorageSync(POS_KEY, DEFAULT_POS)
			} catch (e) {
				/* 存储不可用时只保留内存态 */
			}
			// 恢复后重置为 idle 状态
			this.setPetState('idle')
		},
	},
})
