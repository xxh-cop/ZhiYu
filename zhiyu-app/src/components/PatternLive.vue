<template>
	<view class="pattern-live" :style="{ backgroundColor: background }">
		<canvas
			v-if="!fallback"
			:id="canvasId"
			:canvas-id="canvasId"
			class="pattern-live-canvas"
			:aria-label="label"
			role="img"
		></canvas>
		<image
			v-else
			class="pattern-live-fallback"
			:src="fallbackSrc"
			mode="aspectFit"
			:alt="label"
		/>
	</view>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { buildHeritageGeometry } from '@/utils/heritage-pattern'
import { drawHeritagePattern } from '@/utils/heritage-studio'
import { patternImage } from '@/utils/heritage-ui'
import { useInteraction } from '@/composables/useInteraction'

const props = defineProps({
	pattern: { type: Object, default: null },
	palette: { type: Array, default: () => [] },
	canvasId: { type: String, required: true },
	animate: { type: Boolean, default: false },
	revealRatio: { type: Number, default: 1 },
	label: { type: String, default: '非遗原创教学纹样' },
})

const { reducedMotion } = useInteraction()
const instance = getCurrentInstance()
const fallback = ref(false)
const drawSize = ref(0)
let context = null
let timer = null

const layers = computed(() => (props.pattern ? buildHeritageGeometry(props.pattern) : []))
const palette = computed(() => {
	if (Array.isArray(props.palette) && props.palette.length === 4) return props.palette
	const own = props.pattern && props.pattern.palette
	return Array.isArray(own) && own.length === 4 ? own : null
})
const background = computed(() => (palette.value ? palette.value[0] : 'transparent'))
const fallbackSrc = computed(() => patternImage(props.pattern && props.pattern.id))

function stopTimer() {
	if (timer) {
		clearTimeout(timer)
		timer = null
	}
}

function paint(count) {
	if (!context || !drawSize.value || !palette.value) return
	drawHeritagePattern(context, layers.value.slice(0, count), drawSize.value, palette.value)
	context.draw()
}

function render() {
	stopTimer()
	if (!layers.value.length || !palette.value) {
		fallback.value = true
		return
	}
	fallback.value = false
	const total = layers.value.length
	const ratio = Math.min(1, Math.max(0.05, props.revealRatio))
	const target = Math.max(1, Math.round(total * ratio))
	if (!props.animate || reducedMotion.value) {
		paint(target)
		return
	}
	let done = 0
	const perTick = Math.max(1, Math.round(target / 14))
	const tick = () => {
		done = Math.min(target, done + perTick)
		paint(done)
		if (done < target) timer = setTimeout(tick, 50)
	}
	tick()
}

onMounted(() => {
	uni.createSelectorQuery()
		.in(instance.proxy)
		.select('#' + props.canvasId)
		.boundingClientRect((rect) => {
			if (!rect || !rect.width) {
				fallback.value = true
				return
			}
			drawSize.value = Math.round(rect.width)
			context = uni.createCanvasContext(props.canvasId, instance.proxy)
			render()
		})
		.exec()
})

watch(() => [props.pattern, props.palette, props.revealRatio], render)
onBeforeUnmount(stopTimer)
</script>

<style scoped>
.pattern-live {
	position: relative;
	width: 100%;
	height: 100%;
	overflow: hidden;
}
.pattern-live-canvas {
	display: block;
	width: 100%;
	height: 100%;
}
.pattern-live-fallback {
	display: block;
	width: 100%;
	height: 100%;
}
</style>
