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
.dialog-mask{position:fixed;inset:0;z-index:9999;background:rgba(23,43,35,.5);padding:24px 16px;box-sizing:border-box;display:flex;align-items:center;justify-content:center}
.dialog-panel{box-sizing:border-box;width:100%;max-width:520px;max-height:calc(100vh - 48px);overflow-y:auto;background:#fff;border:1px solid var(--zy-border);border-radius:20px;padding:28px;color:var(--zy-title);box-shadow:0 24px 80px rgba(23,43,35,.18)}
.dialog-panel.is-dark{background:#0F1C19;border:1px solid rgba(241,244,241,.22);border-radius: var(--zy-radius-control, 18rpx);color:#F1F4F1;box-shadow:0 24px 80px rgba(0,0,0,.45)}
.dialog-panel.is-dark .dialog-title{color:#F1F4F1}
.dialog-mask.is-dark{background:rgba(4,10,9,.72)}
.dialog-title{display:block;font-size:21px;font-weight:600;line-height:1.5;margin-bottom:16px;padding-left:14px;border-left:4rpx solid var(--zy-brand-600,#24478f)}
.dialog-panel.is-dark .dialog-title{border-left-color:var(--zy-d-primary,#9db8ec)}
@media(max-width:480px){.dialog-panel{padding:24px 20px}}
</style>
