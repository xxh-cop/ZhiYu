<template>
	<view class="culture-poster" aria-hidden="true">
		<canvas
			id="culture-poster"
			canvas-id="culture-poster"
			class="culture-poster-canvas"
			:style="{ width: posterWidth + 'px', height: posterHeight + 'px' }"
			:data-drawn="drawn ? 'true' : 'false'"
		></canvas>
	</view>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { buildHeritageGeometry } from '@/utils/heritage-pattern'
import { drawHeritagePattern } from '@/utils/heritage-studio'

// 文化卡分享图：1080×1440 打印级画布，屏幕外挂载，不进入页面布局。
// 画布颜色只来自 pattern/palette 与中性纸墨色（纸 #F6F1E7 / 墨 #33302A / 次级墨 #5C5648），
// 不使用 CSS 变量（canvas 无法解析 --zy-* 令牌）。
const CANVAS_ID = 'culture-poster'
const POSTER_WIDTH = 1080
const POSTER_HEIGHT = 1440
const PAPER = '#F6F1E7'
const INK = '#33302A'
const INK_SOFT = '#5C5648'
const HEX_COLOR = /^#[0-9a-f]{6}$/i
const SWATCH_LABELS = ['底色', '墨色', '主色', '辅色']

// 坐标常量：竖版三段（题头 / 720×720 纹样区 / 色板与落款）
const FRAME_OUTER = [40, 40, 1000, 1360]
const FRAME_INNER = [56, 56, 968, 1328]
const PLATE = [88, 268, 912, 76]
const PATTERN = { x: 180, y: 356, size: 720 }
const SWATCH_ROW = { firstX: 104, gap: 224, cy: 1118, radius: 20, labelDy: 8, labelGap: 34 }

const props = defineProps({
	pattern: { type: Object, required: true },
	palette: { type: Array, default: () => [] },
	note: { type: String, default: '' },
	tradition: { type: Object, default: null },
})

const instance = getCurrentInstance()
const drawn = ref(false)
const exporting = ref(false)
const posterWidth = POSTER_WIDTH
const posterHeight = POSTER_HEIGHT
let context = null
let redrawTimer = null

const posterPalette = computed(() => {
	const own = Array.isArray(props.palette) ? props.palette.slice(0, 4) : []
	if (own.length === 4 && own.every((color) => HEX_COLOR.test(String(color || '')))) return own
	const ownPattern = props.pattern && Array.isArray(props.pattern.palette) ? props.pattern.palette.slice(0, 4) : []
	if (ownPattern.length === 4 && ownPattern.every((color) => HEX_COLOR.test(String(color || '')))) return ownPattern
	return null
})

// 导出前先确保 canvas 已挂载：nextTick + 短暂延迟，避免小程序原生画布尚未就绪时空白。
function wait(ms) {
	return new Promise((resolve) => {
		setTimeout(resolve, ms)
	})
}

function clipText(value, max) {
	const text = String(value || '').replace(/\s+/g, ' ').trim()
	if (!text) return ''
	return text.length > max ? `${text.slice(0, max)}…` : text
}

function traditionField(key) {
	if (props.tradition && props.tradition[key]) return props.tradition[key]
	return props.pattern && props.pattern[key] ? props.pattern[key] : ''
}

function craftLine() {
	return [traditionField('name'), props.pattern && props.pattern.region, props.pattern && props.pattern.familyLabel]
		.filter(Boolean)
		.join(' · ')
}

// 名录牌只写目录事实（名录编号 / 列入年份），缺任一项就少写一项，两项都缺则整块不画。
function registryLine() {
	const tradition = props.tradition || {}
	const code = tradition.registryCode ? `名录编号 ${tradition.registryCode}` : ''
	const year = tradition.listedYear ? `${tradition.listedYear} 年列入` : ''
	return [code, year].filter(Boolean).join(' · ')
}

// 只用通用族名（serif / sans-serif）：小程序旧版画布对带引号的字体列表解析不可靠，
// 通用族名在 H5 与小程序都能落到衬线 / 无衬线字体，且解析失败也不至于把字号打回默认。
function fontOf(size, weight, serif) {
	return `${weight || 'normal'} ${size}px ${serif ? 'serif' : 'sans-serif'}`
}

function paintText(ctx, value, x, y, options) {
	const settings = options || {}
	const size = settings.size || 26
	try { ctx.font = fontOf(size, settings.weight, settings.serif) } catch (event) { /* H5 旧上下文忽略 font */ }
	try { ctx.setFontSize(size) } catch (event) { /* 部分平台无此方法 */ }
	ctx.setFillStyle(settings.color || INK)
	try { ctx.setTextAlign(settings.align || 'left') } catch (event) { /* 部分平台无此方法 */ }
	ctx.fillText(String(value == null ? '' : value), x, y)
}

function drawPoster(onDone) {
	const finish = (ok) => {
		drawn.value = ok
		if (typeof onDone === 'function') onDone(ok)
	}
	if (!props.pattern || !posterPalette.value) {
		finish(false)
		return false
	}
	try {
		if (!context) context = uni.createCanvasContext(CANVAS_ID, instance.proxy)
		if (!context) {
			finish(false)
			return false
		}
		const ctx = context
		const palette = posterPalette.value
		const geometry = buildHeritageGeometry({
			family: props.pattern.family,
			variant: props.pattern.variant,
			detail: props.pattern.detail,
		})

		// 1. 纸底 + 双线边框
		ctx.clearRect(0, 0, POSTER_WIDTH, POSTER_HEIGHT)
		ctx.setFillStyle(PAPER)
		ctx.fillRect(0, 0, POSTER_WIDTH, POSTER_HEIGHT)
		ctx.setStrokeStyle(INK_SOFT)
		ctx.setLineWidth(3)
		ctx.strokeRect(FRAME_OUTER[0], FRAME_OUTER[1], FRAME_OUTER[2], FRAME_OUTER[3])
		ctx.setLineWidth(1)
		ctx.strokeRect(FRAME_INNER[0], FRAME_INNER[1], FRAME_INNER[2], FRAME_INNER[3])

		// 2. 题头：小字、纹样名（衬线粗体）、工艺行
		paintText(ctx, 'HERITAGE COLOR STUDY · 织愈', 88, 108, { size: 24, color: INK_SOFT })
		paintText(ctx, clipText(props.pattern.name, 14), 88, 196, {
			size: 72, weight: 'bold', color: INK, serif: true,
		})
		const craft = craftLine()
		if (craft) paintText(ctx, craft, 88, 244, { size: 28, color: INK_SOFT })

		// 3. 名录牌（可缺省）
		const registry = registryLine()
		if (registry) {
			ctx.setStrokeStyle(INK_SOFT)
			ctx.setLineWidth(2)
			ctx.strokeRect(PLATE[0], PLATE[1], PLATE[2], PLATE[3])
			paintText(ctx, registry, PLATE[0] + 24, PLATE[1] + 48, { size: 26, color: INK })
		}

		// 4. 纹样：中央 720×720 区域，平移后按归一化几何绘制
		ctx.save()
		ctx.translate(PATTERN.x, PATTERN.y)
		drawHeritagePattern(ctx, geometry, PATTERN.size, palette)
		ctx.restore()
		ctx.setStrokeStyle(INK_SOFT)
		ctx.setLineWidth(2)
		ctx.strokeRect(PATTERN.x, PATTERN.y, PATTERN.size, PATTERN.size)

		// 5. 四色色板
		for (let index = 0; index < 4; index++) {
			const cx = SWATCH_ROW.firstX + index * SWATCH_ROW.gap
			ctx.beginPath()
			ctx.setFillStyle(palette[index])
			ctx.arc(cx, SWATCH_ROW.cy, SWATCH_ROW.radius, 0, Math.PI * 2)
			ctx.fill()
			ctx.setStrokeStyle(INK_SOFT)
			ctx.setLineWidth(1)
			ctx.stroke()
			paintText(ctx, SWATCH_LABELS[index], cx + SWATCH_ROW.labelGap, SWATCH_ROW.cy + SWATCH_ROW.labelDy, {
				size: 24, color: INK_SOFT,
			})
		}

		// 6. 用户备注（可空，单行截断）
		const note = clipText(props.note, 30)
		if (note) paintText(ctx, `备注：${note}`, 88, 1210, { size: 28, color: INK })

		// 7. 缝线分隔
		ctx.setStrokeStyle(INK_SOFT)
		ctx.setLineWidth(1)
		for (let x = 88; x < 1000; x += 24) {
			ctx.beginPath()
			ctx.moveTo(x, 1242)
			ctx.lineTo(Math.min(x + 12, 1000), 1242)
			ctx.stroke()
		}

		// 8. 边界与免责声明 + 落款
		paintText(ctx, '原创教学 · 屏幕练习不是实物工艺', 540, 1282, { size: 24, color: INK_SOFT, align: 'center' })
		paintText(ctx, '本系统不能替代专业心理咨询', 540, 1316, { size: 24, color: INK_SOFT, align: 'center' })
		paintText(ctx, '织愈 · 一针一线', 1000, 1372, { size: 22, color: INK_SOFT, align: 'right' })

		ctx.draw(false, () => finish(true))
		return true
	} catch (event) {
		finish(false)
		return false
	}
}

const cardText = computed(() => {
	const pattern = props.pattern
	if (!pattern) return ''
	const palette = posterPalette.value || []
	const lines = ['【织愈 · 纹样文化卡】', `纹样：${pattern.name || ''}`]
	const craft = craftLine()
	if (craft) lines.push(`工艺：${craft}`)
	const registry = registryLine()
	if (registry) lines.push(`名录：${registry}`)
	if (palette.length === 4) {
		lines.push(`色板：${palette.map((color, index) => `${SWATCH_LABELS[index]} ${color}`).join(' / ')}`)
	}
	const note = clipText(props.note, 120)
	if (note) lines.push(`备注：${note}`)
	lines.push('原创教学 · 屏幕练习不是实物工艺')
	lines.push('本系统不能替代专业心理咨询')
	lines.push('织愈 · 一针一线')
	return lines.join('\n')
})

function paintAndWait() {
	return new Promise((resolve) => {
		let settled = false
		const done = () => {
			if (settled) return
			settled = true
			clearTimeout(timer)
			resolve()
		}
		const timer = setTimeout(done, 900)
		if (!drawPoster(done)) done()
	})
}

function toTempFilePath() {
	return new Promise((resolve, reject) => {
		uni.canvasToTempFilePath({
			canvasId: CANVAS_ID,
			fileType: 'png',
			width: POSTER_WIDTH,
			height: POSTER_HEIGHT,
			destWidth: POSTER_WIDTH,
			destHeight: POSTER_HEIGHT,
			success: (result) => {
				if (result && result.tempFilePath) resolve(result.tempFilePath)
				else reject(new Error('canvas export empty'))
			},
			fail: (error) => reject(error || new Error('canvas export failed')),
		}, instance.proxy)
	})
}

function fileName() {
	const raw = String((props.pattern && props.pattern.name) || '纹样')
	const safe = raw.replace(/[\\/:*?"<>|\s]+/g, '-') || '纹样'
	return `织愈-${safe}-文化卡.png`
}

function copyCardText(fallbackMessage) {
	const data = cardText.value
	if (!data) return Promise.resolve({ ok: false, mode: 'copy', message: '当前没有可导出的文化卡' })
	return new Promise((resolve) => {
		uni.setClipboardData({
			data,
			success: () => resolve({ ok: true, mode: 'copy', message: '当前无法保存图片，已复制文化卡文字' }),
			fail: () => resolve({
				ok: false,
				mode: 'copy',
				message: fallbackMessage || '当前无法保存图片，请长按文化卡文字手动复制',
			}),
		})
	})
}

// #ifdef H5
// H5：canvasToTempFilePath 返回临时文件（base64），用隐藏 <a download> 触发下载；
// 下载失败时打开图片预览，让用户长按保存。
function downloadOnH5(filePath) {
	try {
		if (typeof document === 'undefined') throw new Error('no document')
		const link = document.createElement('a')
		link.href = filePath
		link.download = fileName()
		link.rel = 'noopener'
		document.body.appendChild(link)
		link.click()
		document.body.removeChild(link)
		return Promise.resolve({ ok: true, mode: 'download', message: '分享图已保存' })
	} catch (event) {
		try {
			uni.previewImage({ urls: [filePath] })
			return Promise.resolve({ ok: false, mode: 'download', message: '已打开图片预览，长按图片可保存' })
		} catch (error) {
			return Promise.resolve({ ok: false, mode: 'download', message: '当前无法保存图片，请长按图片或复制文化卡文字' })
		}
	}
}
// #endif

// #ifndef H5
// 小程序：先保存到相册；用户拒绝相册授权（auth deny）时降级复制文化卡文字，不阻断主流程。
function saveToAlbum(filePath) {
	return new Promise((resolve) => {
		uni.saveImageToPhotosAlbum({
			filePath,
			success: () => resolve({ ok: true, mode: 'album', message: '已保存到相册' }),
			fail: (error) => {
				const message = String((error && (error.errMsg || error.message)) || '')
				if (/cancel/i.test(message)) {
					resolve({ ok: false, mode: 'album', message: '已取消保存分享图' })
					return
				}
				copyCardText('当前无法保存图片，已复制文化卡文字').then(resolve)
			},
		})
	})
}
// #endif

async function exportImage() {
	if (exporting.value) return { ok: false, mode: 'copy', message: '分享图正在生成，请稍候' }
	if (!props.pattern || !posterPalette.value) {
		return { ok: false, mode: 'copy', message: '当前没有可导出的文化卡' }
	}
	exporting.value = true
	try {
		await nextTick()
		await wait(100)
		await paintAndWait()
		let filePath = ''
		try {
			filePath = await toTempFilePath()
		} catch (event) {
			return copyCardText('当前无法保存图片，已复制文化卡文字')
		}
		// #ifdef H5
		return downloadOnH5(filePath)
		// #endif
		// #ifndef H5
		return saveToAlbum(filePath)
		// #endif
	} catch (event) {
		return copyCardText('当前无法保存图片，已复制文化卡文字')
	} finally {
		exporting.value = false
	}
}

function scheduleDraw() {
	if (redrawTimer) clearTimeout(redrawTimer)
	redrawTimer = setTimeout(() => {
		redrawTimer = null
		drawPoster()
	}, 200)
}

onMounted(() => nextTick(scheduleDraw))
watch(() => [props.pattern, props.palette, props.note, props.tradition], scheduleDraw, { deep: true })
onBeforeUnmount(() => {
	if (redrawTimer) clearTimeout(redrawTimer)
})

defineExpose({ exportImage })
</script>

<style scoped>
/* 屏幕外画布：固定定位 + 负偏移，不参与页面布局，也不产生横向滚动 */
.culture-poster {
	position: fixed;
	top: 0;
	left: -10000px;
	width: 1080px;
	height: 1440px;
	overflow: hidden;
	pointer-events: none;
}
.culture-poster-canvas {
	display: block;
	width: 1080px;
	height: 1440px;
}
</style>
