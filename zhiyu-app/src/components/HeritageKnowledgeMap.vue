<template>
	<view class="knowledge-map">
		<canvas
			id="heritage-knowledge-map"
			canvas-id="heritage-knowledge-map"
			class="knowledge-map-canvas"
			:style="{ width: size + 'px', height: size + 'px' }"
			role="img"
			:aria-label="canvasLabel"
			:data-drawn="drawn ? 'true' : 'false'"
		></canvas>
		<view class="knowledge-map-legend" aria-label="关系图图例">
			<view class="legend-item"><view class="legend-dot tradition"></view><text>工艺</text></view>
			<view class="legend-item"><view class="legend-dot source"></view><text>已登记来源</text></view>
		</view>
		<view class="knowledge-map-list">
			<view v-for="group in graph.groups" :key="group.id" class="knowledge-group">
				<text class="knowledge-group-title">{{ group.title }} · {{ group.patternCount }} 个纹样</text>
				<text class="knowledge-group-meta">{{ group.region }} · 来源：{{ group.sources.join('、') }}</text>
				<text class="knowledge-group-patterns">{{ group.patterns.join('、') }}</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
	graph: { type: Object, required: true },
})

const instance = getCurrentInstance()
const size = computed(() => Math.min(Math.max(240, (uni.getSystemInfoSync().windowWidth || 375) - 96), 360))
const label = computed(() => props.graph.groups
	.map(group => `${group.title} ${group.patternCount} 个纹样`)
	.join('；'))
const canvasLabel = computed(() => `非遗关系图：${label.value}。完整关系在下方列表。`)
const drawn = ref(false)
let context = null
let timer = null

function draw() {
	if (!props.graph || !props.graph.nodes) return
	if (!context) context = uni.createCanvasContext('heritage-knowledge-map', instance.proxy)
	const sizeValue = size.value
	const byId = new Map(props.graph.nodes.map(node => [node.id, node]))
	drawn.value = false
	context.clearRect(0, 0, sizeValue, sizeValue)
	context.setFillStyle('#f7f9f4')
	context.fillRect(0, 0, sizeValue, sizeValue)
	context.setStrokeStyle('#c9d8ca')
	context.setLineWidth(1)
	for (const edge of props.graph.edges) {
		const from = byId.get(edge.from)
		const to = byId.get(edge.to)
		if (!from || !to) continue
		context.beginPath()
		context.moveTo((from.x / 100) * sizeValue, (from.y / 100) * sizeValue)
		context.lineTo((to.x / 100) * sizeValue, (to.y / 100) * sizeValue)
		context.stroke()
	}
	for (const node of props.graph.nodes) {
		const x = (node.x / 100) * sizeValue
		const y = (node.y / 100) * sizeValue
		const radius = node.type === 'tradition' ? 18 : 15
		context.beginPath()
		context.setFillStyle(node.type === 'tradition' ? '#217e76' : '#bb914d')
		context.arc(x, y, radius, 0, Math.PI * 2)
		context.fill()
		context.setFillStyle('#233d34')
		context.setFontSize(node.type === 'tradition' ? 11 : 8)
		context.setTextAlign('center')
		context.fillText(node.label, x, y + 3)
	}
	context.draw(false, () => { drawn.value = true })
}

function drawLater() {
	if (timer) clearTimeout(timer)
	timer = setTimeout(draw, 160)
}

onMounted(() => nextTick(drawLater))
watch(() => props.graph, () => nextTick(drawLater), { deep: true })
onBeforeUnmount(() => {
	if (timer) clearTimeout(timer)
})
</script>

<style scoped>
.knowledge-map { padding: 8rpx 0 0; }
.knowledge-map-canvas { display: block; margin: 0 auto; border-radius: 20rpx; background: #f7f9f4; }
.knowledge-map-legend { display: flex; justify-content: center; gap: 24rpx; margin: 12rpx 0 16rpx; }
.legend-item { display: flex; align-items: center; gap: 8rpx; font-size: 21rpx; color: var(--zy-text, #4a5f54); }
.legend-dot { width: 16rpx; height: 16rpx; border-radius: 50%; }
.legend-dot.tradition { background: #217e76; }
.legend-dot.source { background: #bb914d; }
.knowledge-map-list { display: flex; flex-direction: column; gap: 12rpx; }
.knowledge-group { padding: 14rpx 16rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 14rpx; background: var(--zy-bg, #ffffff); }
.knowledge-group-title { display: block; font-size: 24rpx; color: var(--zy-title, #233d34); font-weight: 600; }
.knowledge-group-meta, .knowledge-group-patterns { display: block; margin-top: 4rpx; font-size: 21rpx; line-height: 1.6; color: var(--zy-text, #4a5f54); word-break: break-word; }
</style>
