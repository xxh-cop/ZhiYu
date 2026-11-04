<script lang="ts" setup>
/**
 * 统一表单字段壳：标签 / 必填星标 / 就地错误由 el-form-item 承载，
 * 本组件只补 el-form-item 没有的「字段级提示文案」并固定其排版。
 * 校验规则请从 #/utils/form-rules 取共享规则组，避免每页各写一套。
 */
withDefaults(
  defineProps<{
    /** 字段标签 */
    label: string;
    /** 关联 el-form 校验字段名（与 rules 对应） */
    prop?: string;
    /** 字段级提示（可选），展示在控件下方 */
    hint?: string;
  }>(),
  {
    prop: undefined,
    hint: '',
  },
);
</script>

<template>
  <!-- 不传 required：el-form-item 会把显式 required=false 覆盖进规则，
       导致 rules 里的 required:true 被改写、空值跳过校验。必填一律由 rules 表达。 -->
  <el-form-item :label="label" :prop="prop">
    <div class="zy-form-field-body">
      <slot />
      <p v-if="hint" class="zy-form-field-hint">{{ hint }}</p>
    </div>
  </el-form-item>
</template>

<style scoped>
.zy-form-field-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.zy-form-field-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
</style>
