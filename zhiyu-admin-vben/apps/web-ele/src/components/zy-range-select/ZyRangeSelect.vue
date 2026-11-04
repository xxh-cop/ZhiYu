<script lang="ts" setup>
/**
 * 统计时间窗口统一控件（监控/看板类页面专用）。
 * 视觉沿用看板的 chip 样式；各页只传可选天数。
 */
withDefaults(
  defineProps<{
    modelValue: number;
    /** 可选天数，默认 [7, 30] */
    options?: number[];
  }>(),
  { options: () => [7, 30] },
);

const emit = defineEmits<{
  'update:modelValue': [value: number];
  change: [value: number];
}>();

function pick(value: number) {
  emit('update:modelValue', value);
  emit('change', value);
}
</script>

<template>
  <div class="zy-range zy-glass-chip" role="group" aria-label="统计时间窗口">
    <button
      v-for="n in options"
      :key="n"
      type="button"
      :aria-pressed="modelValue === n"
      :class="{ selected: modelValue === n }"
      @click="pick(n)"
    >
      近 {{ n }} 天
    </button>
  </div>
</template>

<style scoped>
.zy-range {
  display: flex;
  gap: 4px;
  padding: 4px;
}

.zy-range button {
  padding: 8px 14px;
  font-size: 12px;
  white-space: nowrap;
  color: var(--el-text-color-primary);
  background: transparent;
  border: 0;
  border-radius: calc(var(--radius) * 1.2);
  cursor: pointer;
}

.zy-range .selected {
  color: var(--el-color-white);
  background: var(--el-color-primary);
  box-shadow: 0 8px 18px -10px var(--zy-accent-glow);
}

.zy-range button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .zy-range button {
    transition: none;
  }
}
</style>
