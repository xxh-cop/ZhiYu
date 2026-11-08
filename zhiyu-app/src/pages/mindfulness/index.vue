<template>
	<view class="mindful-page" :class="[{sleeping:sleepMode}, zyThemeClass]">
    <view class="mindful-header"><text class="eyebrow">留一点时间给自己</text><text class="title">正念片刻</text><text class="subtitle">跟随自己的节奏，轻轻地呼吸。</text></view>
    <view class="practice-card">
      <view class="duration-row"><AccessibleButton v-for="value in [1, 3, 5]" :key="value" :class="{ selected: minutes === value }" :aria-pressed="minutes === value" :disabled="running || elapsed > 0" @click="minutes = value">{{ value }} 分钟</AccessibleButton></view>
      <view class="orb-area"><view class="orb" :class="{ expanding: running && inhale }" :style="{ transitionDuration: running ? (inhale ? '4s' : '6s') : '0.3s' }"><text class="phase" role="status" aria-live="polite">{{ completed ? '练习完成' : running ? inhale ? '轻轻吸气' : '缓缓呼气' : elapsed ? '休息一下' : '准备好了' }}</text><text class="phase-detail">{{ running ? inhale ? '4 秒 · 慢慢展开' : '6 秒 · 放松下来' : '不必用力，保持舒适' }}</text></view></view>
      <text class="time" aria-live="off">{{ remainingText }}</text><text class="caption">{{ completed ? '谢谢你，为自己留出了这一刻。' : '4 秒吸气 / 6 秒呼气' }}</text>
      <AccessibleButton class="primary-action" @click="togglePractice">{{ completed ? '再练习一次' : running ? '暂停练习' : elapsed ? '继续练习' : '开始呼吸' }}</AccessibleButton>
      <AccessibleButton v-if="elapsed > 0 && !completed" class="text-action" @click="reset">结束本次练习</AccessibleButton>
    </view>
    <!-- #ifdef H5 -->
    <view class="sound-card"><view><text class="sound-title">柔和白噪音</text><text class="sound-description">让环境安静一点 · 低音量播放</text></view><AccessibleButton class="sound-toggle" :disabled="audioBusy" @click="toggleSound">{{ soundOn ? '关闭声音' : '播放声音' }}</AccessibleButton></view>
    <!-- #endif -->
    <SensoryPractice />
    <view class="sleep-card"><text class="sound-title">睡前安顿</text><text class="sound-description">放松肩膀，调低屏幕亮度，把今晚暂时解决不了的事留到明天。无需强迫自己入睡。</text><AccessibleButton class="text-action" @click="sleepMode = !sleepMode">{{ sleepMode ? '退出夜间安静模式' : '进入夜间安静模式' }}</AccessibleButton></view>
    <view class="other-actions"><AccessibleButton @click="goChat">想说说此刻的感受</AccessibleButton><AccessibleButton @click="goCraft">开始一件手作</AccessibleButton></view>
    <text class="safety-note">保持自然呼吸，如感到不适可随时停止。离开页面时练习与声音会自动暂停。</text>
    <text class="disclaimer">本系统不能替代专业心理咨询</text>
    <Toast />
  </view>
</template>

<script setup>
import SensoryPractice from '../../components/SensoryPractice.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import { useToast } from '../../composables/useToast'
import { computed, ref } from 'vue'
import { onHide, onUnload, onLoad } from '@dcloudio/uni-app'
const toast = useToast()
const sleepMode = ref(false)
const minutes = ref(1)
const elapsed = ref(0)
const running = ref(false)
const completed = ref(false)
const soundOn = ref(false)
const audioBusy = ref(false)
const inhale = computed(() => Math.floor(elapsed.value) % 10 < 4)
const remainingText = computed(() => {
  const remaining = Math.max(0, Math.ceil(minutes.value * 60 - elapsed.value))
  return `${String(Math.floor(remaining / 60)).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`
})
let timer = null
let startedAt = 0
let elapsedAtStart = 0
let audioContext = null
let soundSource = null
let audioGeneration = 0
function stopTimer() { if (timer) clearInterval(timer); timer = null }
function pause() { running.value = false; stopTimer(); stopSound() }
function reset() { pause(); elapsed.value = 0; completed.value = false }
function togglePractice() {
  if (running.value) { pause(); return }
  if (completed.value) reset()
  startedAt = Date.now()
  elapsedAtStart = elapsed.value
  running.value = true
  timer = setInterval(() => {
    elapsed.value = Math.min(minutes.value * 60, elapsedAtStart + (Date.now() - startedAt) / 1000)
    if (elapsed.value >= minutes.value * 60) { pause(); completed.value = true }
  }, 100)
}
function stopSound() {
  audioGeneration++
  if (soundSource) { try { soundSource.stop() } catch {} soundSource.disconnect(); soundSource = null }
  if (audioContext) { audioContext.close().catch(() => {}); audioContext = null }
  soundOn.value = false
}
async function toggleSound() {
  // #ifdef H5
  if (soundOn.value) { stopSound(); return }
  if (audioBusy.value) return
  audioBusy.value = true
  const generation = ++audioGeneration
  try {
    const AudioCtor = window.AudioContext || window.webkitAudioContext
    if (!AudioCtor) throw new Error('unsupported')
    const context = new AudioCtor()
    audioContext = context
    await context.resume()
    if (generation !== audioGeneration) return
    const buffer = context.createBuffer(1, context.sampleRate * 3, context.sampleRate)
    const channel = buffer.getChannelData(0)
    for (let i = 0; i < channel.length; i++) channel[i] = Math.random() * 2 - 1
    const source = context.createBufferSource()
    source.buffer = buffer
    source.loop = true
    const filter = context.createBiquadFilter()
    filter.type = 'lowpass'
    filter.frequency.value = 700
    const gain = context.createGain()
    gain.gain.value = 0.06
    source.connect(filter).connect(gain).connect(context.destination)
    source.start()
    soundSource = source
    soundOn.value = true
  } catch {
    stopSound()
    toast.care('当前设备暂不支持声音播放')
  } finally { audioBusy.value = false }
  // #endif
}
function goChat() { uni.navigateTo({ url: '/pages/emotion/chat' }) }
function goCraft() { uni.navigateTo({ url: '/pages/craft/recommend' }) }
onLoad(q => { const value=Number(q.minutes); if([1,3,5].includes(value)) minutes.value=value })
onHide(pause)
onUnload(pause)
</script>

<style scoped>
/* AZ 视觉深化（2026-11-05）：深蓝夜空系 → 青玉案令牌体系。
   结构/交互不动；夜间安静模式保留暗色，改用令牌内最深的 --zy-title 墨绿。 */
.mindful-page {
	min-height: 100vh;
	box-sizing: border-box;
	padding: 48rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
	background: linear-gradient(170deg, var(--zy-bg-tint-mint, #e8efe4) 0%, var(--zy-bg, #ffffff) 42%);
	color: var(--zy-content, #4a5f54);
}
.mindful-header { text-align: center; margin-bottom: 40rpx; }
.eyebrow,.title,.subtitle,.time,.caption,.safety-note,.disclaimer { display: block; }
.eyebrow { color: var(--zy-brand-700, #1a645e); font-size: 22rpx; letter-spacing: 4rpx; }
.title { font-family: var(--zy-serif); font-size: 48rpx; margin: 16rpx 0; font-weight: 600; color: var(--zy-title, #233d34); }
.subtitle { font-size: 26rpx; color: var(--zy-text, #4a5f54); }

.practice-card {
	padding: 36rpx 32rpx;
	border-radius: var(--zy-radius-card, 32rpx);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	background: var(--zy-card-bg, #ffffff);
	box-shadow: var(--zy-shadow-sm, 0 4rpx 16rpx rgba(35, 61, 52, 0.04));
	text-align: center;
}
.duration-row { display: flex; gap: 16rpx; }
.duration-row button {
	flex: 1;
	font-size: 26rpx;
	background: transparent;
	color: var(--zy-text, #4a5f54);
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 16rpx;
	padding: 0;
}
.duration-row .selected {
	background: var(--zy-bg-tint-mint, #e8efe4);
	border-color: var(--zy-brand-600, #217e76);
	color: var(--zy-brand-700, #1a645e);
	font-weight: 600;
}
.duration-row button.is-disabled { opacity: .7; }

button::after { border: 0; }
.orb-area { height: 410rpx; display: flex; align-items: center; justify-content: center; }
.orb {
	width: 270rpx;
	height: 270rpx;
	border-radius: 50%;
	border: 2rpx solid var(--zy-brand-300, #5bc4a8);
	background: var(--zy-brand-600, #217e76);
	box-shadow: 0 0 0 22rpx rgba(33, 126, 118, 0.10), 0 0 0 44rpx rgba(33, 126, 118, 0.06);
	display: flex;
	flex-direction: column;
	justify-content: center;
	gap: 16rpx;
	transform: scale(.9);
	transition: transform ease-in-out;
}
.orb.expanding { transform: scale(1.12); }
.phase { font-size: 34rpx; color: #ffffff; }
.phase-detail { font-size: 22rpx; color: rgba(255, 255, 255, 0.85); }
.time { font-size: 52rpx; font-variant-numeric: tabular-nums; color: var(--zy-title, #233d34); }
.caption { font-size: 24rpx; color: var(--zy-text, #4a5f54); margin: 8rpx 0 32rpx; }
.primary-action {
	background: var(--zy-brand-600, #217e76);
	color: #ffffff;
	font-size: 30rpx;
	border-radius: var(--zy-radius-full, 9999rpx);
	font-weight: 600;
	box-shadow: var(--zy-shadow-md, 0 8rpx 24rpx rgba(33, 126, 118, 0.18));
}
.text-action { color: var(--zy-brand-700, #1a645e); background: transparent; font-size: 24rpx; margin-top: 12rpx; }

.sound-card {
	display: flex;
	align-items: center;
	gap: 16rpx;
	justify-content: space-between;
	margin: 24rpx 0;
	padding: 24rpx;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: 24rpx;
	background: var(--zy-card-bg, #ffffff);
}
.sound-title,.sound-description { display: block; }
.sound-title { font-size: 28rpx; color: var(--zy-title, #233d34); }
.sound-description { font-size: 22rpx; color: var(--zy-text, #4a5f54); margin-top: 8rpx; }
.sound-toggle {
	flex-shrink: 0;
	margin: 0;
	background: var(--zy-bg-tint-mint, #e8efe4);
	color: var(--zy-brand-700, #1a645e);
	font-size: 24rpx;
	border-radius: 12rpx;
}
.other-actions { display: flex; gap: 16rpx; margin-top: 24rpx; }
.other-actions button { background: transparent; color: var(--zy-brand-700, #1a645e); font-size: 24rpx; padding: 0 8rpx; }
.safety-note,.disclaimer { font-size: 22rpx; color: var(--zy-text, #4a5f54); line-height: 1.8; text-align: center; margin-top: 24rpx; }

/* 睡前安顿卡 + 夜间安静模式：暗色保留，统一为令牌墨绿 */
.sleep-card { padding: 28rpx; margin-top: 24rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 24rpx; line-height: 1.8; background: var(--zy-card-bg, #ffffff); }
.sleeping { background: var(--zy-title, #233d34) !important; }
.sleeping .mindful-header,.sleeping .other-actions { display: none; }
.sleeping .practice-card { background: rgba(255, 255, 255, 0.06); border-color: rgba(255, 255, 255, 0.14); box-shadow: none; }
.sleeping .time { color: var(--zy-bg-tint-mint, #e8efe4); }
.sleeping .caption { color: rgba(232, 239, 228, 0.75); }
.sleeping .duration-row button { color: rgba(232, 239, 228, 0.85); border-color: rgba(255, 255, 255, 0.2); }
.sleeping .duration-row .selected { background: rgba(91, 196, 168, 0.2); border-color: var(--zy-brand-300, #5bc4a8); color: var(--zy-bg-tint-mint, #e8efe4); }
.sleeping .orb { box-shadow: none; background: var(--zy-brand-700, #1a645e); border-color: var(--zy-brand-600, #217e76); }
.sleeping .primary-action { background: var(--zy-brand-300, #5bc4a8); color: var(--zy-title, #233d34); box-shadow: none; }
.sleeping .text-action { color: rgba(232, 239, 228, 0.85); }
.sleeping .sound-card { background: rgba(255, 255, 255, 0.06); border-color: rgba(255, 255, 255, 0.14); }
.sleeping .sound-title { color: var(--zy-bg-tint-mint, #e8efe4); }
.sleeping .sound-description { color: rgba(232, 239, 228, 0.75); }
.sleeping .sound-toggle { background: rgba(91, 196, 168, 0.2); color: var(--zy-bg-tint-mint, #e8efe4); }
.sleeping .safety-note,.sleeping .disclaimer { color: rgba(232, 239, 228, 0.7); }
/* 子组件根元素受父级 scoped 影响：感官落地卡在夜间模式同样压暗 */
.sleeping .sensory { background: rgba(255, 255, 255, 0.06); border-color: rgba(255, 255, 255, 0.14); }
.sleeping .sensory .heading,.sleeping .sensory .prompt { color: var(--zy-bg-tint-mint, #e8efe4); }
.sleeping .sensory .intro { color: rgba(232, 239, 228, 0.75); }
.sleeping .sensory .actions button { background: rgba(91, 196, 168, 0.2); color: var(--zy-bg-tint-mint, #e8efe4); }

@media (prefers-reduced-motion: reduce) { .orb,.orb.expanding { transform: none; transition: none; } }
/* #ifdef H5 */
.mindful-header,.practice-card,.sound-card,.other-actions,.safety-note,.disclaimer { max-width: 560px; margin-left: auto; margin-right: auto; }
/* #endif */

.primary-action:focus-visible,.duration-row button:focus-visible,.text-action:focus-visible,.sound-toggle:focus-visible{outline:3px solid currentColor;outline-offset:3px}
</style>
