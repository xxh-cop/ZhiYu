<template>
  <view class="mindful-page" :class="{sleeping:sleepMode}">
    <view class="mindful-header"><text class="eyebrow">留一点时间给自己</text><text class="title">正念片刻</text><text class="subtitle">跟随自己的节奏，轻轻地呼吸。</text></view>
    <view class="practice-card">
      <view class="duration-row"><button v-for="value in [1, 3, 5]" :key="value" :class="{ selected: minutes === value }" :aria-pressed="minutes === value" :disabled="running || elapsed > 0" @click="minutes = value">{{ value }} 分钟</button></view>
      <view class="orb-area"><view class="orb" :class="{ expanding: running && inhale }" :style="{ transitionDuration: running ? (inhale ? '4s' : '6s') : '0.3s' }"><text class="phase" role="status" aria-live="polite">{{ completed ? '练习完成' : running ? inhale ? '轻轻吸气' : '缓缓呼气' : elapsed ? '休息一下' : '准备好了' }}</text><text class="phase-detail">{{ running ? inhale ? '4 秒 · 慢慢展开' : '6 秒 · 放松下来' : '不必用力，保持舒适' }}</text></view></view>
      <text class="time" aria-live="off">{{ remainingText }}</text><text class="caption">{{ completed ? '谢谢你，为自己留出了这一刻。' : '4 秒吸气 / 6 秒呼气' }}</text>
      <button class="primary-action" @click="togglePractice">{{ completed ? '再练习一次' : running ? '暂停练习' : elapsed ? '继续练习' : '开始呼吸' }}</button>
      <button v-if="elapsed > 0 && !completed" class="text-action" @click="reset">结束本次练习</button>
    </view>
    <!-- #ifdef H5 -->
    <view class="sound-card"><view><text class="sound-title">柔和白噪音</text><text class="sound-description">让环境安静一点 · 低音量播放</text></view><button class="sound-toggle" :disabled="audioBusy" @click="toggleSound">{{ soundOn ? '关闭声音' : '播放声音' }}</button></view>
    <!-- #endif -->
    <SensoryPractice />
    <view class="sleep-card"><text class="sound-title">睡前安顿</text><text class="sound-description">放松肩膀，调低屏幕亮度，把今晚暂时解决不了的事留到明天。无需强迫自己入睡。</text><button class="text-action" @click="sleepMode = !sleepMode">{{ sleepMode ? '退出夜间安静模式' : '进入夜间安静模式' }}</button></view>
    <view class="other-actions"><button @click="goChat">想说说此刻的感受</button><button @click="goCraft">开始一件手作</button></view>
    <text class="safety-note">保持自然呼吸，如感到不适可随时停止。离开页面时练习与声音会自动暂停。</text>
    <text class="disclaimer">本系统不能替代专业心理咨询</text>
  </view>
</template>

<script setup>
import SensoryPractice from '../../components/SensoryPractice.vue'
import { computed, ref } from 'vue'
import { onHide, onUnload, onLoad } from '@dcloudio/uni-app'
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
    uni.showToast({ title: '当前设备暂不支持声音播放', icon: 'none' })
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
.sleep-card{padding:28rpx;margin-top:24rpx;border:1rpx solid #52657b;border-radius:24rpx;line-height:1.8}.sleeping{background:#0c1824!important}.sleeping .mindful-header,.sleeping .other-actions{display:none}.sleeping .practice-card{background:#142333}.sleeping .orb{box-shadow:none;background:#24464b}

.mindful-page { min-height: 100vh; box-sizing: border-box; padding: 48rpx 32rpx calc(48rpx + env(safe-area-inset-bottom)); background: linear-gradient(160deg,#1e3a5f,#14283e); color: #e9f4f5; }
.mindful-header { text-align: center; margin-bottom: 40rpx; }.eyebrow,.title,.subtitle,.time,.caption,.safety-note,.disclaimer { display: block; }.eyebrow { color: #acd8d4; font-size: 24rpx; letter-spacing: 4rpx; }.title { font-size: 48rpx; margin: 16rpx 0; font-weight: 600; }.subtitle { font-size: 26rpx; color: #bed0df; }
.practice-card { padding: 32rpx; border-radius: 32rpx; border: 1rpx solid #52657b; background: #233c55; text-align: center; }.duration-row { display: flex; gap: 16rpx; }.duration-row button { flex: 1; font-size: 26rpx; background: transparent; color: #d1dfeb; border: 1rpx solid #64768a; border-radius: 16rpx; padding: 0; }.duration-row .selected { background: #365f66; border-color: #a4ded4; color: #fff; }.duration-row button[disabled] { opacity: .7; }
button::after { border: 0; }.orb-area { height: 410rpx; display: flex; align-items: center; justify-content: center; }.orb { width: 270rpx; height: 270rpx; border-radius: 50%; border: 2rpx solid #91cec6; background: #365f66; box-shadow: 0 0 0 22rpx #365f6638,0 0 0 44rpx #365f6626; display: flex; flex-direction: column; justify-content: center; gap: 16rpx; transform: scale(.9); transition: transform ease-in-out; }.orb.expanding { transform: scale(1.12); }.phase { font-size: 34rpx; }.phase-detail { font-size: 22rpx; color: #d1e6e3; }.time { font-size: 52rpx; font-variant-numeric: tabular-nums; }.caption { font-size: 24rpx; color: #bed0df; margin: 8rpx 0 32rpx; }.primary-action { background: #a4ded4; color: #173f45; font-size: 30rpx; border-radius: 16rpx; font-weight: 600; }.text-action { color: #d1dfeb; background: transparent; font-size: 24rpx; margin-top: 12rpx; }
.sound-card { display: flex; align-items: center; gap: 16rpx; justify-content: space-between; margin: 24rpx 0; padding: 24rpx; border: 1rpx solid #52657b; border-radius: 24rpx; }.sound-title,.sound-description { display: block; }.sound-title { font-size: 28rpx; }.sound-description { font-size: 22rpx; color: #bed0df; margin-top: 8rpx; }.sound-toggle { flex-shrink: 0; margin: 0; background: #365f66; color: #e9f4f5; font-size: 24rpx; border-radius: 12rpx; }.other-actions { display: flex; gap: 16rpx; margin-top: 24rpx; }.other-actions button { background: transparent; color: #c4e4e0; font-size: 24rpx; padding: 0 8rpx; }.safety-note,.disclaimer { font-size: 22rpx; color: #bed0df; line-height: 1.8; text-align: center; margin-top: 24rpx; }
@media (prefers-reduced-motion: reduce) { .orb,.orb.expanding { transform: none; transition: none; } }
/* #ifdef H5 */
.mindful-header,.practice-card,.sound-card,.other-actions,.safety-note,.disclaimer { max-width: 560px; margin-left: auto; margin-right: auto; }
/* #endif */

.primary-action:focus-visible,.duration-row button:focus-visible,.text-action:focus-visible,.sound-toggle:focus-visible{outline:3px solid currentColor;outline-offset:3px}
</style>
