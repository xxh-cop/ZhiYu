<template>
  <button ref="root" role="button" :tabindex="blocked ? -1 : 0"
    :class="[variant ? 'zy-btn' : '', variant ? 'zy-btn--' + variant : '', size ? 'zy-btn--' + size : '', { 'is-disabled': disabled, 'is-loading': loading, 'zy-focused': focused }]"
    :disabled="disabled" :loading="loading" :aria-disabled="blocked" :aria-busy="loading"
    :aria-label="ariaLabel" :aria-pressed="ariaPressed"
    @click="activate" @focus="focused = true" @blur="focused = false">
    <ZySpinner v-if="loading" :size="26" :tone="['primary', 'danger', 'care'].includes(variant) ? 'light' : 'brand'" />
    <slot />
  </button>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import ZySpinner from './ZySpinner.vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({
  disabled: Boolean,
  loading: Boolean,
  ariaLabel: String,
  ariaPressed: { type: [Boolean, String], default: undefined },
  /** 变体：''（默认，无样式，向后兼容）| primary | tonal | secondary | ghost | danger | care */
  variant: { type: String, default: '' },
  /** 尺寸档：''（默认 44px）| lg | md | sm | mini | icon；sm/mini 视觉收窄但热区经 ::after 扩到 88rpx */
  size: { type: String, default: '' },
})
const emit = defineEmits(['click'])
const root = ref(null)
const focused = ref(false)
const blocked = computed(() => props.disabled || props.loading)
let lastHaptic = 0
/** 仅主行动按钮给轻触觉；80ms 节流，避免连点嗡嗡响。小程序以外为空操作。 */
function haptic() {
	// #ifdef MP
	try {
		if (Date.now() - lastHaptic < 80) return
		lastHaptic = Date.now()
		uni.vibrateShort({ type: 'light', fail: () => {} })
	} catch (e) { /* 设备不支持时忽略 */ }
	// #endif
}
function activate(event) {
	if (blocked.value) return
	if (['primary', 'danger', 'care'].includes(props.variant)) haptic()
	emit('click', event)
}
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
button{position:relative;min-height:44px;box-sizing:border-box;line-height:1.5;display:flex;align-items:center;justify-content:center;gap:8px}
button::after{border:0}
button:focus-visible{outline:3px solid var(--zy-brand-700,#0a403b);outline-offset:3px}
button.is-disabled{opacity:.55;cursor:not-allowed}

/* ── 变体（P0 组件升级 2026-11-18：tonal / 状态层 / loading / 令牌化禁用） ── */
.zy-btn{position:relative;border-radius:var(--zy-radius-control,0);font-weight:500;white-space:nowrap}
.zy-btn--lg{min-height:96rpx;padding:0 44rpx;font-size:var(--zy-text-body-lg,30rpx);letter-spacing:1rpx}
.zy-btn--md{min-height:88rpx;padding:0 32rpx;font-size:var(--zy-text-body,28rpx)}
/* sm/mini：视觉收窄；::after 竖向扩热区到 88rpx（44px），视觉与触达分离 */
.zy-btn--sm{min-height:64rpx;padding:0 24rpx;font-size:var(--zy-text-caption,24rpx)}
.zy-btn--mini{min-height:56rpx;padding:0 16rpx;font-size:var(--zy-text-caption,24rpx);line-height:1.8}
.zy-btn--sm::after,.zy-btn--mini::after{content:'';position:absolute;left:0;right:0;top:50%;transform:translateY(-50%);height:88rpx;border:0}
.zy-btn--icon{width:80rpx;height:80rpx;min-width:80rpx;min-height:80rpx;padding:0;border-radius:0}

.zy-btn--primary{background:var(--zy-brand-600,#0e5b53);color:#FFFFFF}
.zy-btn--tonal{background:var(--zy-brand-50,#eef5f1);color:var(--zy-brand-700,#0a403b)}
.zy-btn--secondary{background:transparent;border:1rpx solid var(--zy-border,#D3DED8);color:var(--zy-brand-700,#0a403b)}
.zy-btn--ghost{background:transparent;color:var(--zy-brand-700,#0a403b)}
.zy-btn--danger{background:transparent;border:1rpx solid rgba(166,72,66,.42);color:var(--zy-care,#a64842)}
.zy-btn--care{background:var(--zy-care,#a64842);color:#FFFFFF}

/* 按压状态层：内容色 10% 叠加（M3 state layer 同构），禁用态不叠加 */
.zy-btn::before{content:'';position:absolute;inset:0;background:currentColor;opacity:0;pointer-events:none}
.zy-btn:active::before{opacity:.1}
@media (hover:hover){.zy-btn:hover::before{opacity:.08}.zy-btn.is-disabled:hover::before{opacity:0}}
.zy-btn.is-disabled:active::before{opacity:0}
.zy-btn:active{transition:opacity var(--zy-duration-instant,100ms) var(--zy-ease-out,ease-out)}
.zy-btn--primary:active{background:var(--zy-brand-700,#0a403b)}
.zy-btn--tonal:active{background:var(--zy-brand-100,#dcece5)}
.zy-btn--care:active{background:#8F3A35}

.zy-btn.is-disabled{opacity:1;background:var(--zy-bg-sunken,#E4ECE8);color:var(--zy-muted,#6E7B75);border:1rpx solid var(--zy-border,#D3DED8);box-shadow:none}
.zy-btn.is-loading{opacity:1}

/* loading 母题改由 ZySpinner（线轴收针 SVG）承担；间距由 button 基础 gap:8px 提供 */
</style>
