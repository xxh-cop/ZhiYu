<template>
	<!-- #ifdef H5 -->
	<svg class="empty-art" :viewBox="viewBox" aria-hidden="true" v-html="scene"></svg>
	<!-- #endif -->
</template>

<script setup>
import { computed } from 'vue'

/**
 * EmptyArt —— 空态插画（R2），与线团小兽同族：
 * 亮青 #5BC4A8 主体 / 深青 #1A645E 轮廓 / 暖金 #D9A441 仅图形点缀，不新增色相。
 * H5 内联 SVG（零请求）；小程序端由 StateBlock 降级为小织表情（不新增 PNG，见交付说明）。
 */
const props = defineProps({
	variant: { type: String, default: 'works', validator: (v) => ['works', 'community', 'notice'].includes(v) },
})

const viewBox = '0 0 220 150'

const DEFS = `<defs>
	<radialGradient id="eaBall" cx="36%" cy="28%" r="75%">
		<stop offset="0%" stop-color="#8ADCC6"/><stop offset="45%" stop-color="#5BC4A8"/><stop offset="100%" stop-color="#3D9E8E"/>
	</radialGradient>
</defs>`

/** 迷你小织（r≈30）：与 XiaotuanPet 同族简版 */
function beast(cx, cy, r, face) {
	const eyes = face === 'sleepy'
		? `<path d="M${cx - 14} ${cy - 3} q5 4 10 0" stroke="#233D34" stroke-width="2.6" fill="none" stroke-linecap="round"/>
		   <path d="M${cx + 4} ${cy - 3} q5 4 10 0" stroke="#233D34" stroke-width="2.6" fill="none" stroke-linecap="round"/>`
		: `<ellipse cx="${cx - 9}" cy="${cy - 4}" rx="4.2" ry="5.2" fill="#233D34"/>
		   <ellipse cx="${cx + 9}" cy="${cy - 4}" rx="4.2" ry="5.2" fill="#233D34"/>
		   <circle cx="${cx - 7.5}" cy="${cy - 6.5}" r="1.5" fill="#fff"/>
		   <circle cx="${cx + 10.5}" cy="${cy - 6.5}" r="1.5" fill="#fff"/>`
	const mouth = face === 'happy'
		? `<path d="M${cx - 6} ${cy + 8} q6 6 12 0" stroke="#233D34" stroke-width="2.6" fill="none" stroke-linecap="round"/>`
		: `<path d="M${cx - 5} ${cy + 8} q5 4 10 0" stroke="#233D34" stroke-width="2.4" fill="none" stroke-linecap="round"/>`
	return `<g>
		<circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#eaBall)"/>
		<g fill="none" stroke-linecap="round">
			<path d="M${cx - r + 3} ${cy - 9} Q${cx} ${cy - 20} ${cx + r - 3} ${cy - 9}" stroke="#EAF4F1" stroke-width="2.4" opacity=".9"/>
			<path d="M${cx - r} ${cy} Q${cx} ${cy - 11} ${cx + r} ${cy}" stroke="#1A645E" stroke-width="2.4" opacity=".8"/>
			<path d="M${cx - r + 5} ${cy + 9} Q${cx} ${cy} ${cx + r - 5} ${cy + 9}" stroke="#3D9E8E" stroke-width="2.4"/>
			<path d="M${cx - r + 12} ${cy + 17} Q${cx} ${cy + 9} ${cx + r - 12} ${cy + 17}" stroke="#175C55" stroke-width="2"/>
		</g>
		<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#1A645E" stroke-width="1.6"/>
		${eyes}${mouth}
	</g>`
}

const SCENES = {
	// 我的作品暂无：小织把线牵到画框，框里刚开始一颗金色小星
	works: `${DEFS}
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="#EAF4F1"/>
	<path d="M148 132 L160 76 L164 76 L176 132" fill="none" stroke="#1A645E" stroke-width="3" stroke-linecap="round"/>
	<rect x="142" y="52" width="52" height="38" rx="5" fill="#FDFBF6" stroke="#1A645E" stroke-width="3"/>
	<path d="M150 78 h8 m26 0 h2 M150 84 h20" stroke="#C9E4DD" stroke-width="3" stroke-linecap="round" stroke-dasharray="1 7"/>
	<path d="M168 62 l3 6 6 .8 -4.4 4.2 1.1 6.2 -5.7 -3 -5.7 3 1.1 -6.2 -4.4 -4.2 6 -.8 Z" fill="#D9A441"/>
	<path d="M96 118 Q122 108 150 88" fill="none" stroke="#3D9E8E" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${beast(72, 106, 30, 'happy')}
	<path d="M60 70 q-8 -14 4 -22 q10 -6 16 4" fill="none" stroke="#1A645E" stroke-width="2.4" stroke-linecap="round" opacity=".55"/>`,
	// 社区暂无内容：小织坐在坡上看星星，线从脚边向远处弯
	community: `${DEFS}
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="#EAF4F1"/>
	<path d="M20 132 Q70 108 120 126 T220 122 V150 H20 Z" fill="#E8EFE4"/>
	<path d="M0 150 Q50 122 104 134 T220 130 V150 Z" fill="#C9E4DD" opacity=".55"/>
	<circle cx="58" cy="34" r="3" fill="#D9A441"/>
	<circle cx="168" cy="26" r="2.4" fill="#D9A441"/>
	<path d="M128 44 l1.6 3.4 3.4 1.6 -3.4 1.6 -1.6 3.4 -1.6 -3.4 -3.4 -1.6 3.4 -1.6 Z" fill="#D9A441"/>
	<circle cx="196" cy="52" r="1.8" fill="#5BC4A8"/>
	<path d="M96 128 Q140 118 168 128 T212 124" fill="none" stroke="#3D9E8E" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${beast(74, 104, 32, 'calm')}`,
	// 通知暂无：信箱安安静静，小织靠着打盹
	notice: `${DEFS}
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="#EAF4F1"/>
	<rect x="64" y="66" width="88" height="56" rx="9" fill="#FDFBF6" stroke="#1A645E" stroke-width="3"/>
	<path d="M64 76 L108 104 L152 76" fill="none" stroke="#1A645E" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
	<circle cx="146" cy="68" r="9" fill="#D9A441"/>
	<path d="M142.5 68 l2.5 2.5 4.5 -4.5" stroke="#FDFBF6" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
	${beast(168, 108, 26, 'sleepy')}
	<text x="186" y="66" font-size="13" font-weight="700" fill="#3D9E8E" font-family="sans-serif">z</text>
	<text x="196" y="52" font-size="17" font-weight="700" fill="#3D9E8E" font-family="sans-serif" opacity=".8">z</text>`,
}

const scene = computed(() => SCENES[props.variant] || SCENES.works)
</script>

<style scoped>
.empty-art { width: 360rpx; max-width: 72vw; height: auto; display: block; }
</style>
