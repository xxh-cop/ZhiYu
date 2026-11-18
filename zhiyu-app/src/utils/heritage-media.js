/**
 * 非遗材质与实物参考资产（2026-11-10）
 * 材质图：AI 生成微距，origin=AI_TEXTURE，标注「材质示意 · 非实物文物」；
 * 实物参考：见 docs/heritage-media.json，未核实许可不入库。
 * 降级：材质缺失时返回空串，调用方回落到纯色 tint。
 */

const TEXTURES = {
	cutout: '/static/heritage/texture/cold-linen.jpg',
	porcelain: '/static/heritage/texture/porcelain.jpg',
	stitch: '/static/heritage/texture/cold-linen.jpg',
	weave: '/static/blueprint/hero-bamboo.jpg',
}

export function textureFor(family) {
	return TEXTURES[family] || TEXTURES.stitch
}

/**
 * 实物参考图：仅收录已核实许可的开放素材，必须显示作者与许可证；
 * 与纹样是两回事——它是材料和工艺的实物旁证，不是「原创教学构图」。
 */
export const REFERENCES = [
	{
		id: 'ref-bamboo-shengzhou',
		family: 'weave',
		title: '嵊州竹编提篮（浙江省博物馆藏）',
		src: '/static/heritage/real/bamboo.jpg',
		author: '猫猫的日记本',
		license: 'CC BY-SA 4.0',
		licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0',
		attribution: '摄影 猫猫的日记本 / Wikimedia Commons / CC BY-SA 4.0',
	},
	{
		id: 'ref-papercut-dog',
		family: 'cutout',
		title: '狗年剪纸',
		src: '/static/heritage/real/papercut.jpg',
		author: 'Fanghong',
		license: 'CC BY-SA 3.0',
		licenseUrl: 'http://creativecommons.org/licenses/by-sa/3.0/',
		attribution: '作者 Fanghong / Wikimedia Commons / CC BY-SA 3.0',
	},
	{
		id: 'ref-porcelain-yuan',
		family: 'porcelain',
		title: '元代景德镇青花瓷瓶',
		src: '/static/heritage/real/porcelain.jpg',
		author: 'Gary Lee Todd, Ph.D.',
		license: 'CC0 1.0',
		licenseUrl: 'http://creativecommons.org/publicdomain/zero/1.0/deed.en',
		attribution: 'Gary Lee Todd / Wikimedia Commons / CC0 1.0',
	},
]

export function referencesFor(family) {
	return REFERENCES.filter((item) => item.family === family)
}

export const TEXTURE_DISCLOSURE = '材质示意 · 非实物文物'
