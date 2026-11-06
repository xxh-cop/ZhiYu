<template>
  <button ref="root" role="button" :tabindex="blocked ? -1 : 0" :class="{ 'is-disabled': blocked }"
    :disabled="blocked" :loading="loading" :aria-disabled="blocked" :aria-busy="loading"
    :aria-label="ariaLabel" :aria-pressed="ariaPressed" @click="activate">
    <slot />
  </button>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
defineOptions({ options: { virtualHost: true, styleIsolation: 'shared' } })
const props = defineProps({ disabled: Boolean, loading: Boolean, ariaLabel: String, ariaPressed: { type: [Boolean, String], default: undefined } })
const emit = defineEmits(['click'])
const root = ref(null)
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
button:focus-visible{outline:3px solid var(--zy-brand-700,#1a645e);outline-offset:3px}
button.is-disabled{opacity:.55;cursor:not-allowed}
</style>
