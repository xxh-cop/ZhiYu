<template>
	<!-- #ifdef H5 -->
	<svg class="empty-art" :viewBox="viewBox" aria-hidden="true" v-html="scene"></svg>
	<!-- #endif -->
</template>

<script setup>
import { computed } from 'vue'
import { XIAOZHI_FRAMES, xiaozhiFrame } from '../utils/xiaozhi'

/**
 * H5 空状态场景：道具沿用，角色与全站共用织结小精灵 PNG。
 * P2 2026-11-18：色板收口 v7（窑青 #24478f / 朱砂 #A64842 / 瓷白 / 浅青 #9db8ec），
 * 补 notes / groups / programs 三幅，同一笔触（3 描边 / 圆端点 / 虚线缝线）。
 */
const props = defineProps({
	variant: { type: String, default: 'works', validator: (v) => ['works', 'community', 'notice', 'notes', 'groups', 'programs'].includes(v) },
})

const viewBox = '0 0 220 150'

function sprite(cx, cy, r, face) {
	const size = r * 2.85
	const src = XIAOZHI_FRAMES[xiaozhiFrame(face)]
	return `<image href="${src}" x="${cx - size / 2}" y="${cy + r - size}" width="${size}" height="${size}" />`
}

const SCENES = {
	// 我的作品暂无：小织把线牵到画框，框里刚开始一颗朱砂小星
	works: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="var(--zy-art-ground,#E9EFEB)"/>
	<path d="M148 132 L160 76 L164 76 L176 132" fill="none" stroke="var(--zy-art-line,#24478f)" stroke-width="3" stroke-linecap="round"/>
	<rect x="142" y="52" width="52" height="38" rx="5" fill="var(--zy-art-paper,#FFFFFF)" stroke="var(--zy-art-line,#24478f)" stroke-width="3"/>
	<path d="M150 78 h8 m26 0 h2 M150 84 h20" stroke="var(--zy-art-soft,#dde6f6)" stroke-width="3" stroke-linecap="round" stroke-dasharray="1 7"/>
	<path d="M168 62 l3 6 6 .8 -4.4 4.2 1.1 6.2 -5.7 -3 -5.7 3 1.1 -6.2 -4.4 -4.2 6 -.8 Z" fill="var(--zy-art-cinnabar,#A64842)"/>
	<path d="M96 118 Q122 108 150 88" fill="none" stroke="var(--zy-art-thread,#9db8ec)" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${sprite(72, 106, 30, 'happy')}`,
	// 社区暂无内容：小织坐在坡上看星星，线从脚边向远处弯
	community: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="var(--zy-art-ground,#E9EFEB)"/>
	<path d="M20 132 Q70 108 120 126 T220 122 V150 H20 Z" fill="var(--zy-art-ground,#E9EFEB)"/>
	<path d="M0 150 Q50 122 104 134 T220 130 V150 Z" fill="var(--zy-art-soft,#dde6f6)" opacity=".55"/>
	<circle cx="58" cy="34" r="3" fill="var(--zy-art-cinnabar,#A64842)"/>
	<circle cx="168" cy="26" r="2.4" fill="var(--zy-art-cinnabar,#A64842)"/>
	<path d="M128 44 l1.6 3.4 3.4 1.6 -3.4 1.6 -1.6 3.4 -1.6 -3.4 -3.4 -1.6 3.4 -1.6 Z" fill="var(--zy-art-cinnabar,#A64842)"/>
	<circle cx="196" cy="52" r="1.8" fill="var(--zy-art-thread,#9db8ec)"/>
	<path d="M96 128 Q140 118 168 128 T212 124" fill="none" stroke="var(--zy-art-thread,#9db8ec)" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${sprite(74, 104, 32, 'calm')}`,
	// 通知暂无：信箱安安静静，小织靠着打盹
	notice: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="var(--zy-art-ground,#E9EFEB)"/>
	<rect x="64" y="66" width="88" height="56" rx="9" fill="var(--zy-art-paper,#FFFFFF)" stroke="var(--zy-art-line,#24478f)" stroke-width="3"/>
	<path d="M64 76 L108 104 L152 76" fill="none" stroke="var(--zy-art-line,#24478f)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
	<circle cx="146" cy="68" r="9" fill="var(--zy-art-cinnabar,#A64842)"/>
	<path d="M142.5 68 l2.5 2.5 4.5 -4.5" stroke="var(--zy-art-paper,#FFFFFF)" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
	${sprite(168, 108, 26, 'sleepy')}`,
	// 心情便签暂无：一张刚起头的便签，线头牵到小织手边
	notes: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="var(--zy-art-ground,#E9EFEB)"/>
	<rect x="96" y="56" width="76" height="62" fill="var(--zy-art-paper,#FFFFFF)" stroke="var(--zy-art-line,#24478f)" stroke-width="3"/>
	<path d="M106 72 h40 M106 84 h52 M106 96 h30" stroke="var(--zy-art-soft,#dde6f6)" stroke-width="3" stroke-linecap="round" stroke-dasharray="1 7"/>
	<path d="M150 108 l14 14" stroke="var(--zy-art-cinnabar,#A64842)" stroke-width="3" stroke-linecap="round"/>
	<path d="M96 118 Q74 126 60 114" fill="none" stroke="var(--zy-art-thread,#9db8ec)" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${sprite(50, 104, 28, 'calm')}`,
	// 小组暂无：矮桌两杯热茶，杯间一根线，小织在桌旁等同伴
	groups: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="var(--zy-art-ground,#E9EFEB)"/>
	<path d="M64 106 h92" stroke="var(--zy-art-line,#24478f)" stroke-width="3" stroke-linecap="round"/>
	<path d="M74 106 v26 M146 106 v26" stroke="var(--zy-art-line,#24478f)" stroke-width="3" stroke-linecap="round"/>
	<rect x="82" y="88" width="18" height="18" fill="var(--zy-art-paper,#FFFFFF)" stroke="var(--zy-art-line,#24478f)" stroke-width="3"/>
	<rect x="120" y="88" width="18" height="18" fill="var(--zy-art-paper,#FFFFFF)" stroke="var(--zy-art-line,#24478f)" stroke-width="3"/>
	<path d="M91 80 q3 -5 0 -9 M129 80 q3 -5 0 -9" stroke="var(--zy-art-thread,#9db8ec)" stroke-width="2.6" fill="none" stroke-linecap="round"/>
	<path d="M100 97 Q110 103 120 97" fill="none" stroke="var(--zy-art-cinnabar,#A64842)" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 7"/>
	${sprite(178, 106, 26, 'happy')}`,
	// 活动暂无：绣绷上刚落两针朱砂十字，针还穿着线
	programs: `
	<ellipse cx="110" cy="138" rx="86" ry="9" fill="var(--zy-art-ground,#E9EFEB)"/>
	<circle cx="138" cy="84" r="30" fill="var(--zy-art-paper,#FFFFFF)" stroke="var(--zy-art-line,#24478f)" stroke-width="3"/>
	<circle cx="138" cy="84" r="23" fill="none" stroke="var(--zy-art-soft,#dde6f6)" stroke-width="2.6"/>
	<path d="M130 78 l6 6 m0 -6 l-6 6 M142 88 l6 6 m0 -6 l-6 6" stroke="var(--zy-art-cinnabar,#A64842)" stroke-width="2.6" stroke-linecap="round"/>
	<path d="M158 62 l16 -14" stroke="var(--zy-art-line,#24478f)" stroke-width="2.6" stroke-linecap="round"/>
	<circle cx="176" cy="46" r="2.4" fill="none" stroke="var(--zy-art-line,#24478f)" stroke-width="2"/>
	<path d="M108 112 Q90 122 74 114" fill="none" stroke="var(--zy-art-thread,#9db8ec)" stroke-width="2.6" stroke-linecap="round" stroke-dasharray="1 8"/>
	${sprite(56, 104, 28, 'think')}`,
}

const scene = computed(() => SCENES[props.variant] || SCENES.works)
</script>

<style scoped>
.empty-art { width: 360rpx; max-width: 72vw; height: auto; display: block; }
</style>
