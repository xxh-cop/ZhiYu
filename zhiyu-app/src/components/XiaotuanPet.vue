<template>
	<view class="xiaotuan" :class="[{ 'no-motion': motionless, squish: squishing }, 'state-' + activeFrame]" :style="{ width: size + 'px', height: size + 'px' }" @click="handleTap">
		<view class="pet-touch">
			<view class="pet-motion">
				<image v-for="frame in frames" :key="frame.key" class="pet-frame" :class="{ on: activeFrame === frame.key }" :style="{ transitionDuration: motionless ? '0ms' : fadeMs + 'ms' }" :src="frame.src" mode="aspectFit" alt="" aria-hidden="true" :draggable="false" @load="frameLoaded(frame.key)" @error="frameFailed(frame.key)" />
				<text v-if="unavailable" class="pet-fallback">小织</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { useAiCompanionStore } from '../stores/aiCompanion'
import { useInteraction } from '../composables/useInteraction'
import { XIAOZHI_FRAMES, XIAOZHI_FADE_MS, xiaozhiFrame } from '../utils/xiaozhi'

// 保留组件入口与 open 事件，让全站旧调用统一换成织结小精灵。
// H5 / 小程序共用 PNG；先等新帧加载，再交叉淡入，避免空白与加载乱序。
const props = defineProps({
	size: { type: Number, default: 120 },
	face: { type: String, default: '' },
	quiet: { type: Boolean, default: false },
})
const emit = defineEmits(['open'])
const aiStore = useAiCompanionStore()
const { reducedMotion } = useInteraction()
const fadeMs = XIAOZHI_FADE_MS
const motionless = computed(() => reducedMotion.value || props.quiet || props.size <= 48)
const requestedFrame = computed(() => xiaozhiFrame(props.face, aiStore.petState, props.size))
const failed = ref({})
const frames = ref([])
const activeFrame = ref('')
const targetFrame = computed(() => failed.value[requestedFrame.value] ? (activeFrame.value || 'standard') : requestedFrame.value)
const unavailable = computed(() => failed.value.standard && !activeFrame.value)
const loaded = new Set()
const squishing = ref(false)
let loadTimer = null
let squishTimer = null
let disposed = false

function reveal(key) {
	clearTimeout(loadTimer)
	if (motionless.value) {
		activeFrame.value = key
		return
	}
	// 给新挂载 image 一个 opacity:0 的绘制机会；不靠定时轮换 PNG 做动画。
	nextTick(() => {
		if (disposed || targetFrame.value !== key) return
		loadTimer = setTimeout(() => {
			if (!disposed && targetFrame.value === key) activeFrame.value = key
		}, 20)
	})
}

watch(targetFrame, (key) => {
	clearTimeout(loadTimer)
	if (failed.value[key]) return
	if (!frames.value.some(frame => frame.key === key)) frames.value.push({ key, src: XIAOZHI_FRAMES[key] })
	if (loaded.has(key)) reveal(key)
}, { immediate: true })

function frameLoaded(key) {
	loaded.add(key)
	if (targetFrame.value === key) reveal(key)
}

function frameFailed(key) {
	failed.value = { ...failed.value, [key]: true }
	// 失败时继续显示上一张可用新形象；首次加载失败则尝试标准形象。
}

function handleTap() {
	if (!motionless.value) {
		clearTimeout(squishTimer)
		squishing.value = false
		nextTick(() => {
			if (disposed) return
			squishing.value = true
			squishTimer = setTimeout(() => { squishing.value = false }, 560)
		})
	}
	emit('open')
}

onBeforeUnmount(() => {
	disposed = true
	clearTimeout(loadTimer)
	clearTimeout(squishTimer)
})
</script>

<style scoped>
.xiaotuan { position: relative; display: block; flex-shrink: 0; }
.pet-touch, .pet-motion { position: relative; width: 100%; height: 100%; transform-origin: 46% 90%; }
.pet-motion { animation: xzBreathe 5.6s cubic-bezier(.45,0,.55,1) infinite; }
.pet-frame { position: absolute; top: 0; left: 0; width: 100%; height: 100%; opacity: 0; transition-property: opacity; transition-timing-function: cubic-bezier(.4,0,.2,1); pointer-events: none; }
.pet-frame.on { opacity: 1; }
.state-welcome .pet-motion { animation: xzWelcome 1.3s ease-in-out 1; }
.state-thinking .pet-motion { animation: xzPonder 5s ease-in-out infinite; }
.state-listening .pet-motion { animation: xzBreathe 7s ease-in-out infinite; }
.state-complete .pet-motion { animation: xzCelebrate .95s cubic-bezier(.22,.7,.35,1) 1; }
.state-rest .pet-motion, .state-avatar .pet-motion { animation: none; }
.squish .pet-touch { animation: xzSquish .55s cubic-bezier(.34,1.2,.64,1) 1; }
.pet-fallback { position: absolute; top: 30%; left: 10%; width: 80%; text-align: center; font-size: 12px; color: var(--zy-title, #0f3d38); }
.no-motion .pet-motion, .no-motion .pet-touch { animation: none !important; }
.no-motion .pet-frame { transition: none !important; }
@media (prefers-reduced-motion: reduce) {
	.pet-motion, .pet-touch { animation: none !important; }
	.pet-frame { transition: none !important; }
}
@keyframes xzBreathe { 0%,100% { transform: scale(1); } 50% { transform: scale(1.012,.993); } }
@keyframes xzWelcome { 0%,100% { transform: rotate(0); } 35% { transform: rotate(-2deg); } 70% { transform: rotate(1.5deg); } }
@keyframes xzPonder { 0%,100% { transform: rotate(0); } 50% { transform: rotate(-1.2deg); } }
@keyframes xzCelebrate { 0%,100% { transform: translateY(0) scale(1); } 25% { transform: translateY(0) scale(1.025,.975); } 52% { transform: translateY(-4px) scale(.99,1.015); } 78% { transform: translateY(0) scale(1.01,.99); } }
@keyframes xzSquish { 0%,100% { transform: scale(1); } 35% { transform: scale(1.045,.95); } 65% { transform: scale(.985,1.02); } }
</style>
