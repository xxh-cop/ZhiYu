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
		currentImage: 'avatar', // 'avatar' | 'blink' | 'talk'
		isTalking: false,
		// 气泡
		showBubble: false,
		bubbleText: '',
		bubbleSide: 'left', // 'left' | 'right'
		// 隐藏
		isHidden: false,
		// 拖动状态
		isDragging: false,
	}),

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

		/* ── 眨眼（点击触发，非自动） ── */
		blink() {
			// 避免叠加触发
			if (this.currentImage === 'blink') return

			this.currentImage = 'blink'

			// 清理旧定时器
			if (this._blinkTimer) clearTimeout(this._blinkTimer)

			this._blinkTimer = setTimeout(() => {
				// 恢复：如果说话中就回 talk，否则回 avatar
				this.currentImage = this.isTalking ? 'talk' : 'avatar'
				this._blinkTimer = null
			}, 150)
		},

		/* ── 说话嘴巴动 ── */
		startTalking() {
			if (this.isTalking) return
			this.isTalking = true
			this.currentImage = 'talk'
			this._talkTimer = setInterval(() => {
				this.currentImage = this.currentImage === 'talk' ? 'avatar' : 'talk'
			}, 150)
		},

		stopTalking() {
			this.isTalking = false
			if (this._talkTimer) {
				clearInterval(this._talkTimer)
				this._talkTimer = null
			}
			this.currentImage = 'avatar'
		},

		/* ── 点击气泡 ── */
		showRandomBubble() {
			// 随机台词
			this.bubbleText = BUBBLE_TEXTS[Math.floor(Math.random() * BUBBLE_TEXTS.length)]

	
			// 根据小织当前位置自动判断气泡方向（朝屏幕内侧展开）
			// right = 小织距屏幕右边的距离，小织宽 140rpx
			// leftEdge = 小织左边缘距屏幕左边的距离 = 750 - right - 140
			const r = this.position.right
			const leftEdge = 750 - r - 140
			if (r < 100) {
				// 小织贴右边缘 → 气泡朝左
				this.bubbleSide = 'left'
			} else if (leftEdge < 100) {
				// 小织贴左边缘 → 气泡朝右
				this.bubbleSide = 'right'
			} else {
				// 中间区域 → 根据哪边空间更多
				this.bubbleSide = r > 375 ? 'left' : 'right'
			}

			this.showBubble = true

			// 清理旧定时器
			if (this._bubbleTimer) {
				clearTimeout(this._bubbleTimer)
			}
			// 3 秒后自动隐藏
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

		/* ── 拖到底部隐藏 ── */
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
		},
	},
})
