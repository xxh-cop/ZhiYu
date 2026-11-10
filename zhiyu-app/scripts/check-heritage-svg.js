/**
 * 非遗纹样几何一致性闸口（2026-11-10，气息深化阶段一）
 * 核对后端目录 catalog 与分包 manifest 的 24 份纹样 ID 闭合，
 * 并断言确定性渲染器 buildHeritageGeometry 对每份纹样产出合法 layers
 * （点数 2..100、坐标归一 [0,1]、fill/stroke ∈ null|0..3、palette 长度 4）。
 * 活体渲染 PatternLive 与静态 PNG 同源，此闸口保证两者不会漂移。
 *
 * 用法：node scripts/check-heritage-svg.js
 * 退出码：0 = 全部一致；1 = 存在漂移或非法几何（逐条打印）。
 */
const fs = require('fs')
const path = require('path')
const { pathToFileURL } = require('url')

const MAX_POINTS = 100

async function main() {
	const catalogPath = path.join(__dirname, '../../zhiyu-backend/src/main/resources/catalog/heritage-catalog.json')
	const manifestPath = path.join(__dirname, '../src/sub-heritage/static/heritage/manifest.json')
	const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf8'))
	const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
	const { buildHeritageGeometry } = await import(pathToFileURL(path.join(__dirname, '../src/utils/heritage-pattern.js')).href)
	const { buildPatternSvg } = await import(pathToFileURL(path.join(__dirname, '../src/utils/heritage-svg.js')).href)

	const problems = []
	const patterns = Array.isArray(catalog.patterns) ? catalog.patterns : []
	const manifestIds = new Set((manifest.files || []).map(f => String(f.id)))
	const catalogIds = new Set(patterns.map(p => String(p.id)))

	if (patterns.length !== 24) problems.push(`catalog 纹样数应为 24，实为 ${patterns.length}`)
	if (manifest.count !== patterns.length) problems.push(`manifest.count(${manifest.count}) != catalog(${patterns.length})`)
	for (const id of catalogIds) if (!manifestIds.has(id)) problems.push(`manifest 缺少纹样 ${id}`)
	for (const id of manifestIds) if (!catalogIds.has(id)) problems.push(`catalog 缺少纹样 ${id}`)

	for (const pattern of patterns) {
		const layers = buildHeritageGeometry(pattern)
		if (!Array.isArray(layers) || layers.length === 0) {
			problems.push(`${pattern.id} 几何为空`)
			continue
		}
		if (!Array.isArray(pattern.palette) || pattern.palette.length !== 4) {
			problems.push(`${pattern.id} palette 长度应为 4`)
		}
		layers.forEach((layer, index) => {
			if (!Array.isArray(layer.points) || layer.points.length < 2 || layer.points.length > MAX_POINTS) {
				problems.push(`${pattern.id} layer${index} 点数非法`)
				return
			}
			for (const [x, y] of layer.points) {
				if (!(x >= 0 && x <= 1 && y >= 0 && y <= 1)) problems.push(`${pattern.id} layer${index} 坐标越界`)
			}
			for (const key of ['fill', 'stroke']) {
				const v = layer[key]
				if (v !== null && !(Number.isInteger(v) && v >= 0 && v <= 3)) problems.push(`${pattern.id} layer${index} ${key} 非法`)
			}
		})
		const svg = buildPatternSvg(pattern)
		if (!svg.startsWith('data:image/svg+xml,')) {
			problems.push(`${pattern.id} SVG 序列化失败`)
		} else {
			const decoded = decodeURIComponent(svg.slice('data:image/svg+xml,'.length))
			if (!decoded.includes('<path') || !decoded.includes('viewBox=')) problems.push(`${pattern.id} SVG 结构不完整`)
		}
	}

	const traditions = Array.isArray(catalog.traditions) ? catalog.traditions : []
	for (const tradition of traditions) {
		if (!Array.isArray(tradition.process) || tradition.process.length === 0) {
			problems.push(`tradition ${tradition.id} 缺少 process（工序条无内容）`)
		}
	}

	if (problems.length) {
		console.error('非遗几何一致性失败：')
		for (const p of problems) console.error('  - ' + p)
		process.exit(1)
	}
	console.log(`非遗几何一致性通过：${patterns.length} 纹样 / ${traditions.length} 工艺，manifest 闭合，layers 合法。`)
}

main().catch(err => {
	console.error(err)
	process.exit(1)
})
