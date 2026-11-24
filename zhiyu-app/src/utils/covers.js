/**
 * 封面兜底（v9 2026-11-23）：作品 / 小组 / 手作没有图时，按 id 稳定挑一张项目自带的工艺实拍图，
 * 避免列表里出现大片空白灰块。同一个 id 永远得到同一张。
 */
import { API_BASE_URL } from './config'

const CRAFT_COVERS = [
	'/static/covers/ganxiu.jpg',
	'/static/covers/jianzhi.jpg',
	'/static/covers/zharan.jpg',
	'/static/covers/taoyi.jpg',
	'/static/covers/papercut-window.jpg',
	'/static/covers/papercut-medallion.jpg',
	'/static/covers/craft-fan-paper.jpg',
	'/static/covers/craft-cord-bookmark.jpg',
	'/static/covers/craft-thread-card.jpg',
	'/static/covers/papercut-leaf.jpg',
]
const TEXTURES = [
	'/static/heritage/texture/stitch.jpg',
	'/static/heritage/texture/weave.jpg',
	'/static/heritage/texture/cutout.jpg',
	'/static/heritage/texture/porcelain.jpg',
	'/static/heritage/texture/cold-linen.jpg',
]

function hash(value) {
	const s = String(value == null ? '' : value)
	let h = 7
	for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
	return h
}

export function resolveMedia(url) {
	if (!url) return ''
	if (/^https?:|^\/static\//.test(url)) return url
	return API_BASE_URL + url
}

/* 按技艺挑图，避免「赣绣项目配剪纸图」这类错位 */
const BY_CATEGORY = [
	[/绣|针|线/, ['/static/covers/ganxiu.jpg', '/static/covers/craft-thread-card.jpg', '/static/blueprint/hero-cloth.jpg']],
	[/剪纸|窗花|纸/, ['/static/covers/jianzhi.jpg', '/static/covers/papercut-window.jpg', '/static/covers/papercut-medallion.jpg', '/static/covers/papercut-leaf.jpg']],
	[/染/, ['/static/covers/zharan.jpg']],
	[/陶|泥|瓷|釉/, ['/static/covers/taoyi.jpg', '/static/blueprint/hero-glaze.jpg']],
	[/编|绳|结/, ['/static/covers/craft-cord-bookmark.jpg', '/static/heritage/real/bamboo.jpg']],
	[/扇/, ['/static/covers/craft-fan-paper.jpg']],
]

/** 作品 / 手作封面：有图用图；没有就先按技艺、再按 id 取项目自带的工艺实拍 */
export function coverFor(item) {
	if (item && item.coverUrl) return resolveMedia(item.coverUrl)
	const key = hash(item && (item.id || item.title))
	const text = String((item && (item.category || '')) + ' ' + (item && (item.title || '')))
	const hit = BY_CATEGORY.find(([re]) => re.test(text))
	if (hit) return hit[1][key % hit[1].length]
	return CRAFT_COVERS[key % CRAFT_COVERS.length]
}

/** 小组 / 卡片底纹：按 id 取一张工艺肌理 */
export function textureFor(key) {
	return TEXTURES[hash(key) % TEXTURES.length]
}
