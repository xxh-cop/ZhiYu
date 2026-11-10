/**
 * 非遗材质与实物参考资产（2026-11-10）
 * 材质图：AI 生成微距，origin=AI_TEXTURE，标注「材质示意 · 非实物文物」；
 * 实物参考：见 docs/heritage-media.json，未核实许可不入库。
 * 降级：材质缺失时返回空串，调用方回落到纯色 tint。
 */

const TEXTURES = {
	cutout: '/static/heritage/texture/cutout.jpg',
	porcelain: '/static/heritage/texture/porcelain.jpg',
	stitch: '/static/heritage/texture/stitch.jpg',
	weave: '/static/heritage/texture/weave.jpg',
}

export function textureFor(family) {
	return TEXTURES[family] || TEXTURES.stitch
}

export const TEXTURE_DISCLOSURE = '材质示意 · 非实物文物'
