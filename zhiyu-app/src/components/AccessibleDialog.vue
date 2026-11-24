<template>
  <view v-if="open" class="dialog-mask" :class="{ 'is-dark': dark }" @click="dismiss">
    <view ref="panel" class="dialog-panel" :class="{ 'is-dark': dark }" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1" @click.stop>
      <text class="dialog-title">{{ title }}</text>
      <slot />
    </view>
  </view>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
const props = defineProps({ open: Boolean, busy: Boolean, dark: Boolean, title: { type: String, required: true } })
const emit = defineEmits(['close'])
const panel = ref(null)
function dismiss() { if (!props.busy) emit('close') }
// #ifdef H5
let previousFocus, previousOverflow, element
function candidates() {
  return [...(element?.querySelectorAll('input,textarea,button,[tabindex]') || [])]
    .filter(el => el.tabIndex >= 0 && !el.disabled && el.getAttribute('aria-disabled') !== 'true' && el.getClientRects().length)
}
function focusFirst() { (candidates()[0] || element)?.focus() }
function keydown(event) {
  if (event.key === 'Escape') { event.preventDefault(); dismiss() }
  if (event.key !== 'Tab') return
  const items = candidates(), first = items[0], last = items[items.length - 1]
  if (!items.length) { event.preventDefault(); element?.focus(); return }
  if (!element?.contains(document.activeElement) || document.activeElement === element) {
    event.preventDefault(); (event.shiftKey ? last : first).focus()
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault(); last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault(); first.focus()
  }
}
function containFocus(event) { if (element && !element.contains(event.target)) focusFirst() }
function release() {
  document.removeEventListener('keydown', keydown, true)
  document.removeEventListener('focusin', containFocus)
  if (previousOverflow !== undefined) document.body.style.overflow = previousOverflow
  previousOverflow = undefined
  element = null
  if (previousFocus?.isConnected) previousFocus.focus()
  previousFocus = null
}
watch(() => props.open, async open => {
  if (!open) { release(); return }
  previousFocus = document.activeElement
  previousOverflow = document.body.style.overflow
  await nextTick()
  if (!props.open) return
  element = panel.value?.$el || panel.value
  // UniApp puts aria-label on its wrapper; name the actual editable H5 element too.
  for (const field of element?.querySelectorAll('uni-input[aria-label],uni-textarea[aria-label]') || []) {
    field.querySelector('input,textarea')?.setAttribute('aria-label', field.getAttribute('aria-label'))
  }
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', keydown, true)
  document.addEventListener('focusin', containFocus)
  focusFirst()
})
onBeforeUnmount(release)
// #endif
</script>

<style scoped>
/* v9.1 对话框：大圆角白卡 + 顶部朱砂短线 + 宋体标题；深色走主题令牌 */
.dialog-mask{position:fixed;inset:0;z-index:9999;background:rgba(13,31,69,.45);padding:24px 20px;box-sizing:border-box;display:flex;align-items:center;justify-content:center}
.dialog-panel{position:relative;box-sizing:border-box;width:100%;max-width:520px;max-height:calc(100vh - 48px);overflow-y:auto;background:var(--zy-card-bg,#fff);border:0;border-radius:24px;padding:34px 26px 24px;color:var(--zy-title);box-shadow:0 24px 70px rgba(13,31,69,.22);animation:dialogIn 260ms cubic-bezier(.2,1.2,.4,1)}
.dialog-panel::before{content:'';position:absolute;left:50%;top:16px;width:32px;height:4px;margin-left:-16px;border-radius:4px;background:var(--zy-cinnabar,#c0392b)}
.dialog-panel.is-dark{background:var(--zy-card-bg,#1c2740);color:var(--zy-title,#eef2f8);box-shadow:0 24px 80px rgba(0,0,0,.45)}
.dialog-panel.is-dark .dialog-title{color:var(--zy-title,#eef2f8)}
.dialog-mask.is-dark{background:rgba(6,10,20,.6)}
.dialog-title{display:block;text-align:center;font-family:var(--zy-font-display,serif);font-size:21px;font-weight:700;line-height:1.5;letter-spacing:1px;margin-bottom:14px}
@keyframes dialogIn{from{opacity:0;transform:scale(.94)}to{opacity:1;transform:scale(1)}}
@media(prefers-reduced-motion:reduce){.dialog-panel{animation:none}}
@media(max-width:480px){.dialog-panel{padding:32px 22px 22px}}
</style>
