<template>
  <button ref="root" role="button" :tabindex="blocked ? -1 : 0"
    :class="[variant ? 'zy-btn' : '', variant ? 'zy-btn--' + variant : '', size ? 'zy-btn--' + size : '', { 'is-disabled': disabled, 'is-loading': loading, 'zy-focused': focused }]"
    :disabled="disabled" :loading="loading" :aria-disabled="blocked" :aria-busy="loading"
    :aria-label="ariaLabel" :aria-pressed="ariaPressed"
    @click="activate" @focus="focused = true" @blur="focused = false">
    <slot />
  </button>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
  disabled: Boolean,
  loading: Boolean,
  ariaLabel: String,
  ariaPressed: { type: [Boolean, String], default: undefined },
  /** 暖纸青玉 v6 变体：''（默认，无样式，向后兼容）| primary | secondary | ghost | danger | care */
  variant: { type: String, default: '' },
  /** 尺寸档：''（默认）| lg | md | sm | icon */
  size: { type: String, default: '' },
})
const emit = defineEmits(['click'])
const root = ref(null)
const focused = ref(false)
const blocked = computed(() => props.disabled || props.loading)
function activate(event) { if (!blocked.value) emit('click', event) }
// H5 UniApp buttons render as custom elements without native keyboard activation.
// #ifdef H5
let element
function keydown(event) {
  if (event.target?.tagName === 'UNI-BUTTON' && ['Enter', ' '].includes(event.key)) {
    event.preventDefault()
    event.stopPropagation()
    if (!event.repeat) activate(event)
  }
}
onMounted(() => { element = root.value?.$el || root.value; element?.addEventListener('keydown', keydown) })
onBeforeUnmount(() => element?.removeEventListener('keydown', keydown))
// #endif
</script>

<style scoped>
button{min-height:44px;box-sizing:border-box;line-height:1.5;display:flex;align-items:center;justify-content:center;gap:8px}
button::after{border:0}
button:focus-visible{outline:3px solid var(--zy-brand-700,var(--zy-brand-700, #1a645e));outline-offset:3px}
button.is-disabled{opacity:.55;cursor:not-allowed}

/* ── 暖纸青玉 v6 变体（规格 docs/ui-upgrade-student-spec-20261108.md §2-C1） ── */
.zy-btn{border-radius:var(--zy-radius-full,9999rpx);font-weight:500}
.zy-btn--lg{min-height:96rpx;padding:0 48rpx;font-size:var(--zy-text-title, 32rpx)}
.zy-btn--md{min-height:88rpx;padding:0 40rpx;font-size:var(--zy-text-body, 28rpx)}
.zy-btn--sm{min-height:64rpx;padding:0 28rpx;font-size:var(--zy-text-caption, 24rpx)}
.zy-btn--icon{width:72rpx;height:72rpx;min-width:72rpx;min-height:72rpx;padding:0;border-radius:50%}

.zy-btn--primary{background:var(--zy-brand-600,var(--zy-brand-700, #1a645e));color:#FFFFFF;box-shadow:var(--zy-shadow-cta)}
.zy-btn--secondary{background:var(--zy-bg-tint-mint,#e4efe7);color:var(--zy-brand-700,var(--zy-brand-700, #1a645e))}
.zy-btn--ghost{background:transparent;color:var(--zy-brand-700,var(--zy-brand-700, #1a645e))}
.zy-btn--danger{background:#F7ECEA;color:var(--zy-care,var(--zy-care, #a64842))}
.zy-btn--care{background:var(--zy-care,var(--zy-care, #a64842));color:#FFFFFF}

.zy-btn:active{transform:scale(.97);transition:transform var(--zy-duration-instant,100ms) var(--zy-ease-out,ease-out)}
.zy-btn--primary:active{background:var(--zy-brand-700,var(--zy-brand-700, #1a645e))}
.zy-btn--secondary:active{background:var(--zy-brand-100,var(--zy-brand-100, #c9e4dd))}
.zy-btn--ghost:active{background:var(--zy-brand-50,var(--zy-brand-50, #eaf4f1))}
.zy-btn--danger:active{background:#F1E0DC}
.zy-btn--care:active{background:#8F3A35}
.zy-btn.is-disabled{opacity:1;background:#EDE7DA;color:#8A8378;box-shadow:none}
.zy-btn.is-loading{opacity:1}
</style>
