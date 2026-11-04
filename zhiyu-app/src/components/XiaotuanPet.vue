<template>
	<view class="xiaotuan" :class="[{'squish':squishing,'no-motion':reducedMotion,'talking-anim':isTalkingAnim}, 'state-'+faceKey]" :style="{width:size+'px',height:size+'px'}" @click="handleTap">
		<!-- #ifdef H5 -->
		<svg class="pet-svg" :viewBox="'0 0 240 240'" aria-hidden="true">
			<defs>
				<radialGradient id="xtBallHi" cx="36%" cy="28%" r="75%">
					<stop offset="0%" stop-color="#8ADCC6"/><stop offset="45%" stop-color="#5BC4A8"/><stop offset="100%" stop-color="#3D9E8E"/>
				</radialGradient>
				<radialGradient id="xtSoftHi" cx="34%" cy="26%" r="60%">
					<stop offset="0%" stop-color="#FFFFFF" stop-opacity=".32"/><stop offset="55%" stop-color="#FFFFFF" stop-opacity="0"/>
				</radialGradient>
				<linearGradient id="xtNeedle" x1="0" y1="0" x2="1" y2="1">
					<stop offset="0%" stop-color="#D8BC8F"/><stop offset="100%" stop-color="#A9854F"/>
				</linearGradient>
				<clipPath id="xtClip"><circle cx="120" cy="128" r="76"/></clipPath>
			</defs>
			<g class="mascot">
				<g class="tail"><path d="M178 176 q30 4 28 24 q-2 14 -18 12 q-10 -1 -8 -10" fill="none" stroke="#3D9E8E" stroke-width="5" stroke-linecap="round"/></g>
				<g class="acc">
					<path d="M150 74 L172 20 L182 26 L158 80 Z" fill="url(#xtNeedle)"/>
					<path d="M150 74 L158 80 L154 86 Z" fill="#8A6D3F"/>
					<path d="M160 52 L165 55 M164 42 L169 45" stroke="#8A6D3F" stroke-width="1.4" stroke-linecap="round" opacity=".7"/>
					<circle cx="178" cy="21" r="8.5" fill="#D9A441"/>
					<circle cx="175.5" cy="18.5" r="2.6" fill="#F3D9A0"/>
				</g>
				<g class="body">
					<circle cx="120" cy="128" r="76" fill="url(#xtBallHi)"/>
					<g clip-path="url(#xtClip)" fill="none" stroke-linecap="round">
						<path d="M52 96 Q120 66 188 96" stroke="#EAF4F1" stroke-width="4.5" opacity=".95"/>
						<path d="M48 111 Q120 82 192 111" stroke="#3D9E8E" stroke-width="5"/>
						<path d="M46 125 Q120 97 194 125" stroke="#C9E4DD" stroke-width="5" opacity=".9"/>
						<path d="M46 125 Q120 99 194 127" stroke="#1A645E" stroke-width="1.8" opacity=".7"/>
						<path d="M47 139 Q120 112 193 139" stroke="#1A645E" stroke-width="4.5"/>
						<path d="M51 153 Q120 127 189 153" stroke="#3D9E8E" stroke-width="5"/>
						<path d="M58 166 Q120 143 182 166" stroke="#175C55" stroke-width="4.5"/>
						<path d="M68 178 Q120 158 172 178" stroke="#1A645E" stroke-width="3.5"/>
						<path d="M84 64 Q104 128 86 190" stroke="#EAF4F1" stroke-width="3.5" opacity=".9"/>
						<path d="M140 58 Q124 122 150 188" stroke="#3D9E8E" stroke-width="3.5"/>
						<path d="M108 54 Q120 120 112 192" stroke="#1A645E" stroke-width="3" opacity=".65"/>
					</g>
					<circle cx="120" cy="128" r="76" fill="url(#xtSoftHi)"/>
					<circle cx="120" cy="128" r="76" fill="none" stroke="#1A645E" stroke-width="2.5"/>
					<ellipse cx="50" cy="152" rx="11" ry="14" fill="#1A645E"/>
					<ellipse cx="190" cy="152" rx="11" ry="14" fill="#1A645E"/>
					<!-- 表情层：按状态切换，opacity 过渡不硬切 -->
					<g class="face" :class="{on:faceKey==='calm'}">
						<g class="eyes"><ellipse cx="96" cy="122" rx="11" ry="13" fill="#233D34"/><ellipse cx="144" cy="122" rx="11" ry="13" fill="#233D34"/><circle cx="99" cy="117" r="3.5" fill="#fff"/><circle cx="147" cy="117" r="3.5" fill="#fff"/><circle cx="101" cy="126" r="1.6" fill="#fff" opacity=".8"/><circle cx="149" cy="126" r="1.6" fill="#fff" opacity=".8"/></g>
						<path d="M84 106 q8 -6 16 -3" stroke="#175C55" stroke-width="3" fill="none" stroke-linecap="round"/>
						<path d="M140 103 q8 -3 16 3" stroke="#175C55" stroke-width="3" fill="none" stroke-linecap="round"/>
						<ellipse cx="76" cy="140" rx="8" ry="5" fill="#8A6D1F" opacity=".55"/><ellipse cx="164" cy="140" rx="8" ry="5" fill="#8A6D1F" opacity=".55"/>
						<path d="M108 150 q12 9 24 0" stroke="#233D34" stroke-width="4" fill="none" stroke-linecap="round"/>
					</g>
					<g class="face" :class="{on:faceKey==='listen'}">
						<ellipse cx="96" cy="121" rx="12" ry="15" fill="#233D34"/><ellipse cx="144" cy="121" rx="12" ry="15" fill="#233D34"/>
						<circle cx="100" cy="115" r="4" fill="#fff"/><circle cx="148" cy="115" r="4" fill="#fff"/>
						<ellipse cx="76" cy="140" rx="8" ry="5" fill="#8A6D1F" opacity=".6"/><ellipse cx="164" cy="140" rx="8" ry="5" fill="#8A6D1F" opacity=".6"/>
						<!-- talking 口型双帧（R1）：默认静态「轻合」；talking-anim 时 f1(轻开)/f2(轻合) 300ms 轮替 -->
						<circle class="mouth-static" cx="120" cy="150" r="4.5" fill="none" stroke="#233D34" stroke-width="3.5"/>
						<ellipse class="mouth-f1" cx="120" cy="150" rx="6" ry="8" fill="#233D34"/>
						<ellipse class="mouth-f2" cx="120" cy="151" rx="5" ry="3" fill="none" stroke="#233D34" stroke-width="3"/>
					</g>
					<g class="face" :class="{on:faceKey==='think'}">
						<path d="M84 112 q12 -8 24 0" stroke="#175C55" stroke-width="3.5" fill="none" stroke-linecap="round"/>
						<path d="M132 108 q12 -6 24 2" stroke="#175C55" stroke-width="3.5" fill="none" stroke-linecap="round"/>
						<ellipse cx="96" cy="124" rx="11" ry="7" fill="#233D34"/><ellipse cx="144" cy="124" rx="11" ry="7" fill="#233D34"/>
						<circle cx="99" cy="121" r="2.5" fill="#fff" opacity=".9"/><circle cx="147" cy="121" r="2.5" fill="#fff" opacity=".9"/>
						<path d="M110 154 h20" stroke="#233D34" stroke-width="4" stroke-linecap="round"/>
					</g>
					<g class="face" :class="{on:faceKey==='happy'}">
						<path d="M84 124 q12 -14 24 -2" stroke="#233D34" stroke-width="5" fill="none" stroke-linecap="round"/>
						<path d="M132 122 q12 -12 24 2" stroke="#233D34" stroke-width="5" fill="none" stroke-linecap="round"/>
						<ellipse cx="72" cy="138" rx="9" ry="5.5" fill="#8A6D1F" opacity=".7"/><ellipse cx="168" cy="138" rx="9" ry="5.5" fill="#8A6D1F" opacity=".7"/>
						<path d="M102 146 q18 16 36 0 q-6 18 -18 18 q-12 0 -18 -18Z" fill="#233D34"/>
					</g>
					<g class="face" :class="{on:faceKey==='care'}">
						<path d="M84 104 q10 -8 20 -4" stroke="#175C55" stroke-width="3" fill="none" stroke-linecap="round"/>
						<path d="M136 100 q10 -4 20 4" stroke="#175C55" stroke-width="3" fill="none" stroke-linecap="round"/>
						<path d="M85 118 q11 8 22 0" stroke="#233D34" stroke-width="4.5" fill="none" stroke-linecap="round"/>
						<path d="M133 118 q11 8 22 0" stroke="#233D34" stroke-width="4.5" fill="none" stroke-linecap="round"/>
						<ellipse cx="76" cy="138" rx="8" ry="5" fill="#8A6D1F" opacity=".6"/><ellipse cx="164" cy="138" rx="8" ry="5" fill="#8A6D1F" opacity=".6"/>
						<path d="M110 150 q10 7 20 0" stroke="#233D34" stroke-width="4" fill="none" stroke-linecap="round"/>
					</g>
					<g class="face" :class="{on:faceKey==='sleepy'}">
						<path d="M85 122 q11 8 22 0" stroke="#233D34" stroke-width="4.5" fill="none" stroke-linecap="round"/>
						<path d="M133 122 q11 8 22 0" stroke="#233D34" stroke-width="4.5" fill="none" stroke-linecap="round"/>
						<path d="M114 152 q6 5 12 0" stroke="#233D34" stroke-width="3.5" fill="none" stroke-linecap="round"/>
						<text x="168" y="88" class="z" font-size="20">z</text>
						<text x="182" y="70" class="z z2" font-size="26">z</text>
						<text x="198" y="48" class="z z3" font-size="32">z</text>
					</g>
				</g>
			</g>
		</svg>
		<!-- #endif -->
		<!-- #ifndef H5 -->
		<view class="pet-mp">
			<!-- talking（R1）：两张 256px 口型帧 300ms 轮替；reduced-motion 时静态帧 A -->
			<template v-if="isTalkingAnim">
				<image class="pet-frame" :class="{on:mpTalkFrame===1}" src="/static/mascot/xiaotuan-talk-a.png" mode="aspectFit" alt=""/>
				<image class="pet-frame" :class="{on:mpTalkFrame===2}" src="/static/mascot/xiaotuan-talk-b.png" mode="aspectFit" alt=""/>
			</template>
			<image v-else v-for="f in mpFrames" :key="f.key" class="pet-frame" :class="{on:faceKey===f.key}" :src="f.src" mode="aspectFit"/>
		</view>
		<!-- #endif -->
	</view>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useAiCompanionStore } from '../stores/aiCompanion'
import { useInteraction } from '../composables/useInteraction'

/**
 * 小织「线团小兽」渲染层（2026-11-02 定稿，亮青 #5BC4A8）。
 * H5：内联 SVG + CSS 动效（呼吸/眨眼/次级动作/表情 opacity 过渡）。
 * 小程序：<image> 不支持 SVG（平台限制），用 512px PNG 帧 + opacity 交叉淡入降级；
 * 动效保留整体呼吸/浮动，帧级眨眼在小程序端降级为静态（见 mascot-design 文档 §4 降级阶梯）。
 * 状态机沿用 stores/aiCompanion.js 的 petState，本组件只做渲染。
 */
const props = defineProps({
	size: { type: Number, default: 120 },
	/** 可选：固定表情（calm/listen/think/happy/care/sleepy），缺省跟随全局 petState 的既有映射 */
	face: { type: String, default: '' },
})
const emit = defineEmits(['open'])

const aiStore = useAiCompanionStore()
const { reducedMotion } = useInteraction()
const squishing = ref(false)

const FACE_MAP = { idle: 'calm', thinking: 'think', talking: 'listen', happy: 'happy', caring: 'care' }
const faceKey = computed(() => props.face || FACE_MAP[aiStore.petState] || 'calm')
/** talking 口型动画开关（R1）：固定 face 时不启用（如空态 listen 表情保持静态），不改状态机 */
const isTalkingAnim = computed(() => !props.face && aiStore.petState === 'talking')

/** 小程序 talking 帧轮替（H5 用 CSS，此处仅 MP 模板消费） */
const mpTalkFrame = ref(1)
let mpTalkTimer = null
watch(isTalkingAnim, (talking) => {
	clearInterval(mpTalkTimer)
	mpTalkFrame.value = 1
	if (talking && !reducedMotion.value) {
		mpTalkTimer = setInterval(() => { mpTalkFrame.value = mpTalkFrame.value === 1 ? 2 : 1 }, 300)
	}
})
onBeforeUnmount(() => clearInterval(mpTalkTimer))

const mpFrames = [
	{ key: 'calm', src: '/static/mascot/xiaotuan-calm.png' },
	{ key: 'listen', src: '/static/mascot/xiaotuan-listen.png' },
	{ key: 'think', src: '/static/mascot/xiaotuan-think.png' },
	{ key: 'happy', src: '/static/mascot/xiaotuan-happy.png' },
	{ key: 'care', src: '/static/mascot/xiaotuan-care.png' },
	{ key: 'sleepy', src: '/static/mascot/xiaotuan-sleepy.png' },
]

let squishTimer = null
function handleTap() {
	squishing.value = false
	requestAnimationFrame(() => {
		squishing.value = true
		clearTimeout(squishTimer)
		squishTimer = setTimeout(() => { squishing.value = false }, 600)
	})
	emit('open')
}
</script>

<style scoped>
.xiaotuan { position: relative; display: block; }
.pet-svg, .pet-mp { width: 100%; height: 100%; display: block; }
.pet-mp { position: relative; animation: xtBob 4.2s cubic-bezier(.45,0,.55,1) infinite; }
.pet-frame { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; transition: opacity .28s ease-in-out; }
.pet-frame.on { opacity: 1; }

.mascot { transform-origin: 50% 88%; animation: xtBob 4.2s cubic-bezier(.45,0,.55,1) infinite; }
.mascot .body { transform-origin: 50% 92%; animation: xtBreathe 4.2s cubic-bezier(.45,0,.55,1) infinite; }
.state-calm .mascot .eyes { transform-origin: center; transform-box: fill-box; animation: xtBlink 5s ease-in-out infinite; }
.mascot .acc { transform-origin: bottom center; transform-box: fill-box; animation: xtSway 5.6s ease-in-out .5s infinite; }
.mascot .tail { transform-origin: 0% 0%; transform-box: fill-box; animation: xtTail 4.2s ease-in-out .8s infinite; }

.face { opacity: 0; transition: opacity .28s ease-in-out; }
.face.on { opacity: 1; }

/* talking 口型双帧（R1）：300ms 一循环，steps 硬切两帧 */
.mouth-f1, .mouth-f2 { opacity: 0; }
.talking-anim .mouth-static { opacity: 0; }
.talking-anim .mouth-f1 { animation: xtTalkOpen .3s steps(1, end) infinite; }
.talking-anim .mouth-f2 { animation: xtTalkShut .3s steps(1, end) infinite; }
.no-motion .mouth-f1, .no-motion .mouth-f2 { animation: none !important; }
.no-motion.talking-anim .mouth-f1 { opacity: 1; }
.no-motion.talking-anim .mouth-f2 { opacity: 0; }

.state-think .mascot { animation: xtPonder 1.3s ease-in-out infinite; }
.state-think .mascot .acc { animation: xtNeedleJit .9s ease-in-out infinite; }
.state-happy .mascot { animation: xtCheer 1.6s cubic-bezier(.34,1.3,.64,1) infinite; }
.state-care .mascot, .state-care .mascot .body { animation: xtCalm 6s ease-in-out infinite; }
.state-sleepy .mascot { animation: xtDoze 7s ease-in-out infinite; }
.z { opacity: 0; animation: xtZzz 3.2s ease-in infinite; font: bold 16px sans-serif; fill: #3d9e8e; }
.z2 { animation-delay: 1.1s; } .z3 { animation-delay: 2.2s; }

.squish .mascot, .squish.pet-mp, .squish .pet-mp { animation: xtSquish .55s cubic-bezier(.34,1.56,.64,1) 1; }

.no-motion .mascot, .no-motion .mascot .body, .no-motion .mascot .acc, .no-motion .mascot .tail,
.no-motion .mascot .eyes, .no-motion .z, .no-motion .pet-mp { animation: none !important; }

@keyframes xtBob { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-5px); } }
@keyframes xtBreathe { 0%,100% { transform: scale(1,1); } 50% { transform: scale(1.03,.972); } }
/* 眨眼（R1 细化）：快合 120ms（91%→93.4%）/ 慢开 180ms（93.4%→97%），仅 calm 态启用；caring 不眨眼 */
@keyframes xtBlink { 0%,91%,100% { transform: scaleY(1); } 93.4% { transform: scaleY(.07); } 97% { transform: scaleY(1); } }
@keyframes xtTalkOpen { 0%,49.9% { opacity: 1; } 50%,100% { opacity: 0; } }
@keyframes xtTalkShut { 0%,49.9% { opacity: 0; } 50%,100% { opacity: 1; } }
@keyframes xtSway { 0%,100% { transform: rotate(-2.4deg); } 50% { transform: rotate(2.8deg); } }
@keyframes xtTail { 0%,100% { transform: rotate(2deg); } 50% { transform: rotate(-3.2deg); } }
@keyframes xtSquish { 0% { transform: scale(1,1); } 30% { transform: scale(1.14,.78); } 55% { transform: scale(.94,1.07); } 75% { transform: scale(1.03,.98); } 100% { transform: scale(1,1); } }
@keyframes xtPonder { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
@keyframes xtNeedleJit { 0%,100% { transform: rotate(-1.5deg); } 50% { transform: rotate(1.8deg); } }
@keyframes xtCalm { 0%,100% { transform: scale(1,1); } 50% { transform: scale(1.022,.982); } }
@keyframes xtCheer { 0% { transform: scale(1,1) translateY(0); } 22% { transform: scale(1.15,.8) translateY(0); } 45% { transform: scale(.92,1.1) translateY(-18px); } 62% { transform: scale(1.06,.92) translateY(0); } 80% { transform: scale(.98,1.02) translateY(-4px); } 100% { transform: scale(1,1) translateY(0); } }
@keyframes xtDoze { 0%,100% { transform: rotate(0); } 45%,65% { transform: rotate(7deg) translateY(2px); } }
@keyframes xtZzz { 0% { opacity: 0; transform: translate(0,0) scale(.7); } 18% { opacity: 1; } 100% { opacity: 0; transform: translate(16px,-40px) scale(1.2); } }
</style>
