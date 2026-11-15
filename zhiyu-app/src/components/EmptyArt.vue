<template>
	<!-- #ifdef H5 -->
	<svg class="empty-art" :viewBox="viewBox" aria-hidden="true" v-html="scene"></svg>
	<!-- #endif -->
</template>

<script setup>
import { computed } from 'vue'
import { XIAOZHI_FRAMES, xiaozhiFrame } from '../utils/xiaozhi'

/** H5 空状态场景：道具沿用，角色与全站共用织结小精灵 PNG。 */
const props = defineProps({
	variant: { type: String, default: 'works', validator: (v) => ['works', 'community', 'notice'].includes(v) },
})

const viewBox = '0 0 220 150'

function sprite(cx, cy, r, face) {
	const size = r * 2.85
	const src = XIAOZHI_FRAMES[xiaozhiFrame(face)]
	return `<image href="${src}" x="${cx - size / 2}" y="${cy + r - size}" width="${size}" height="${size}" />`
}

const SCENES = {
	// 我的作品暂无：小织把线牵到画框，框里刚开始一颗金色小星
	works: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="#EAF4F1"/>
	<path d="M148 132 L160 76 L164 76 L176 132" fill="none" stroke="#1A645E" stroke-width="3" stroke-linecap="round"/>
	<rect x="142" y="52" width="52" height="38" rx="5" fill="#FDFBF6" stroke="#1A645E" stroke-width="3"/>
	<path d="M150 78 h8 m26 0 h2 M150 84 h20" stroke="#C9E4DD" stroke-width="3" stroke-linecap="round" stroke-dasharray="1 7"/>
	<path d="M168 62 l3 6 6 .8 -4.4 4.2 1.1 6.2 -5.7 -3 -5.7 3 1.1 -6.2 -4.4 -4.2 6 -.8 Z" fill="#D9A441"/>
	<path d="M96 118 Q122 108 150 88" fill="none" stroke="#3D9E8E" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${sprite(72, 106, 30, 'happy')}`,
	// 社区暂无内容：小织坐在坡上看星星，线从脚边向远处弯
	community: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="#EAF4F1"/>
	<path d="M20 132 Q70 108 120 126 T220 122 V150 H20 Z" fill="#E8EFE4"/>
	<path d="M0 150 Q50 122 104 134 T220 130 V150 Z" fill="#C9E4DD" opacity=".55"/>
	<circle cx="58" cy="34" r="3" fill="#D9A441"/>
	<circle cx="168" cy="26" r="2.4" fill="#D9A441"/>
	<path d="M128 44 l1.6 3.4 3.4 1.6 -3.4 1.6 -1.6 3.4 -1.6 -3.4 -3.4 -1.6 3.4 -1.6 Z" fill="#D9A441"/>
	<circle cx="196" cy="52" r="1.8" fill="#5BC4A8"/>
	<path d="M96 128 Q140 118 168 128 T212 124" fill="none" stroke="#3D9E8E" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${sprite(74, 104, 32, 'calm')}`,
	// 通知暂无：信箱安安静静，小织靠着打盹
	notice: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="#EAF4F1"/>
	<rect x="64" y="66" width="88" height="56" rx="9" fill="#FDFBF6" stroke="#1A645E" stroke-width="3"/>
	<path d="M64 76 L108 104 L152 76" fill="none" stroke="#1A645E" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
	<circle cx="146" cy="68" r="9" fill="#D9A441"/>
	<path d="M142.5 68 l2.5 2.5 4.5 -4.5" stroke="#FDFBF6" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
	${sprite(168, 108, 26, 'sleepy')}`,
}

const scene = computed(() => SCENES[props.variant] || SCENES.works)
</script>

<style scoped>
.empty-art { width: 360rpx; max-width: 72vw; height: auto; display: block; }
</style>
