<script setup lang="ts">
import { computed, ref } from 'vue';

import { createIconifyIcon } from '@vben/icons';

import {
  getCommentAiSuggestion,
  getWorkAiSuggestion,
  type AiSuggestion,
} from '#/api/zhiyu/admin';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { AI_MODERATION_TENDENCY } from '#/constants/status-dict';

/**
 * AI 审核辅助建议面板（BM，后端 BL）。
 * - 按需调用：只有审核员点击「AI 建议」才触发真实模型调用（有费用），
 *   绝不预跑/批量调；同一对象会话内只调一次，重复点击直接展示缓存，
 *   「重新生成」才会再次请求。
 * - 加载可放弃：reasoner 可能数十秒，放弃走竞态守卫（迟到响应被忽略），
 *   按钮立即回到可点状态。
 * - available=false 是「暂无建议」（正常降级，非错误），渲染 note 原文；
 *   频控超限（400/1001）是唯一显式错误，由拦截器上屏。
 * - disclaimer 常驻可见（合规要求，不折叠不 hover）。
 */
const props = defineProps<{
  targetType: 'comment' | 'work';
  targetId: string;
}>();

const SparklesIcon = createIconifyIcon('lucide:sparkles');

/** 会话级结果缓存：切走再回来/重复点击不重调（一次点击一次真实调用） */
const resultCache = new Map<string, AiSuggestion>();
const cacheKey = computed(() => `${props.targetType}:${props.targetId}`);

type Phase = 'done' | 'idle' | 'loading';
const phase = ref<Phase>('idle');
const result = ref<AiSuggestion | null>(null);
let turn = 0;

async function request(force = false) {
  if (!force && resultCache.has(cacheKey.value)) {
    result.value = resultCache.get(cacheKey.value)!;
    phase.value = 'done';
    return;
  }
  const my = ++turn;
  phase.value = 'loading';
  try {
    const data =
      props.targetType === 'work'
        ? await getWorkAiSuggestion(props.targetId)
        : await getCommentAiSuggestion(props.targetId);
    if (my !== turn) return; // 已放弃
    result.value = data;
    resultCache.set(cacheKey.value, data);
    phase.value = 'done';
  } catch {
    // 频控（1001）等显式错误由拦截器上屏；按钮回可点状态
    if (my === turn) phase.value = 'idle';
  }
}

/** 放弃等待：竞态守卫使迟到响应被忽略，不取消底层请求（服务端调用已发生） */
function giveUp() {
  turn++;
  phase.value = 'idle';
}
</script>

<template>
  <div class="ai-suggest">
    <!-- 入口：弱样式，不抢「通过/驳回」主位 -->
    <div v-if="phase === 'idle'" class="ai-suggest__entry">
      <button type="button" class="ai-suggest__trigger" @click="request(false)">
        <SparklesIcon class="ai-suggest__trigger-icon" />
        AI 建议
      </button>
    </div>

    <!-- 加载态：可放弃（reasoner 可能数十秒） -->
    <div v-else-if="phase === 'loading'" class="ai-suggest__loading" role="status">
      <span class="ai-suggest__spinner" aria-hidden="true"></span>
      <span>正在生成 AI 建议…（推理模型可能需要数十秒）</span>
      <button type="button" class="ai-suggest__giveup" @click="giveUp">
        放弃
      </button>
    </div>

    <!-- 结果 / 暂无建议：与人工结论区隔的机器提示卡 -->
    <section v-else-if="result" class="ai-suggest__card" aria-label="AI 辅助建议">
      <header class="ai-suggest__head">
        <SparklesIcon class="ai-suggest__head-icon" />
        <span class="ai-suggest__title">AI 辅助建议</span>
        <ZyStatusTag
          v-if="result.available && result.tendency"
          :dict="AI_MODERATION_TENDENCY"
          :value="result.tendency"
        />
        <el-tag v-else size="small" type="info">暂无建议</el-tag>
        <button
          type="button"
          class="ai-suggest__refresh"
          title="重新调用一次 AI（消耗一次调用额度）"
          @click="request(true)"
        >
          重新生成
        </button>
      </header>

      <template v-if="result.available">
        <ul
          v-if="result.riskPoints?.length"
          class="ai-suggest__risks"
        >
          <li v-for="(rp, i) in result.riskPoints" :key="i">
            <el-tag size="small" effect="plain" type="warning">
              {{ rp.location }}
            </el-tag>
            <span class="ai-suggest__risk-note">{{ rp.note }}</span>
          </li>
        </ul>
        <p v-if="result.uncertainty" class="ai-suggest__uncertainty">
          不确定因素：{{ result.uncertainty }}
        </p>
      </template>
      <p v-else class="ai-suggest__note">
        {{ result.note || 'AI 暂时无法给出建议，请按人工流程审核。' }}
      </p>

      <footer class="ai-suggest__disclaimer">{{ result.disclaimer }}</footer>
    </section>
  </div>
</template>

<style scoped>
.ai-suggest {
  margin: 10px 0;
}
.ai-suggest__entry {
  display: flex;
  justify-content: flex-start;
}
.ai-suggest__trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 0;
  border: none;
  background: none;
  font-size: 13px;
  color: var(--el-color-primary-dark-2);
  cursor: pointer;
  border-radius: 4px;
}
.ai-suggest__trigger:hover {
  text-decoration: underline;
}
.ai-suggest__trigger:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.ai-suggest__trigger-icon {
  width: 14px;
  height: 14px;
}
.ai-suggest__loading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.ai-suggest__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--el-border-color);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: ai-suggest-spin 0.9s linear infinite;
  flex-shrink: 0;
}
.ai-suggest__giveup {
  border: none;
  background: none;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  text-decoration: underline;
  border-radius: 4px;
}
.ai-suggest__giveup:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
/* 机器提示卡：虚线边框 + 浅底，与人工决策区一眼区隔 */
.ai-suggest__card {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ai-suggest__head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ai-suggest__head-icon {
  width: 14px;
  height: 14px;
  color: var(--el-color-primary);
}
.ai-suggest__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.ai-suggest__refresh {
  margin-left: auto;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  border-radius: 4px;
}
.ai-suggest__refresh:hover {
  color: var(--el-color-primary-dark-2);
  text-decoration: underline;
}
.ai-suggest__refresh:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.ai-suggest__risks {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ai-suggest__risks li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}
.ai-suggest__risk-note {
  flex: 1;
  min-width: 0;
  word-break: break-word;
}
.ai-suggest__uncertainty {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.ai-suggest__note {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
/* disclaimer 常驻可见（合规），不折叠不 hover */
.ai-suggest__disclaimer {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 6px;
}
@keyframes ai-suggest-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .ai-suggest__spinner {
    animation: none;
  }
}
</style>
