/**
 * 非遗纹样 SVG 序列化（2026-11-10，气息深化阶段一）
 * 与 heritage-pattern.js 的确定性 layers 同源，供 H5 列表缩略图使用：
 * 避免 dev 环境分包 PNG 路径不可达、也避免为每张卡创建 canvas。
 * 小程序端不走 SVG（image 对 data-uri SVG 支持不稳），仍用分包 PNG。
 */
import { buildHeritageGeometry } from './heritage-pattern.js'

const DEFAULT_SIZE = 240

function pathData(points, closed, size) {
	return points
		.map(([x, y], index) => `${index === 0 ? 'M' : 'L'}${(x * size).toFixed(2)} ${(y * size).toFixed(2)}`)
		.join(' ') + (closed ? ' Z' : '')
}

export function buildPatternSvg(pattern, size = DEFAULT_SIZE, options = {}) {
	const layers = buildHeritageGeometry(pattern)
	const palette = pattern && Array.isArray(pattern.palette) && pattern.palette.length === 4 ? pattern.palette : null
	if (!layers.length || !palette) return ''
	const showBackground = options.background !== false
	const body = layers
		.map((layer) => {
			const d = pathData(layer.points, layer.closed, size)
			const fill = layer.fill === null ? 'none' : palette[layer.fill]
			const stroke = layer.stroke === null ? 'none' : palette[layer.stroke]
			const width = Math.max(1, layer.width * size).toFixed(2)
			return `<path d="${d}" fill="${fill}" stroke="${stroke}" stroke-width="${width}" stroke-linejoin="round"/>`
		})
		.join('')
	const background = showBackground ? `<rect width="${size}" height="${size}" fill="${palette[0]}"/>` : ''
	const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}">${background}${body}</svg>`
	return 'data:image/svg+xml,' + encodeURIComponent(svg)
}
