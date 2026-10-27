# 织愈管理端 UI 基线 · 主题与契约基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理端主题只由 Vben preferences 驱动，并把加载 / 空 / 错误四态、分页与危险操作确认收敛为可复用契约。

**Architecture:** 删除 `business.scss` 与 `variables.scss` 中与 Vben 运行时冲突的颜色覆盖层，品牌色只保留 `preferences.ts` 一个入口；新增 `DataState`、`TablePagination`、`confirmAction` 三个基础件，逐页替换"错误伪装空态"、固定分页与 `window.confirm`。本计划只做 B1+B2 基础层，试点页与图表桥见后续计划。

**Tech Stack:** Vue 3.5、TypeScript、Vite 8、Element Plus 2、Vue Vben Admin 5.8.0、Vitest（根 `vitest.config.ts`）

**Spec:** `docs/superpowers/specs/2026-10-26-admin-ui-baseline-design.md`

## Global Constraints

- 管理端品牌色唯一入口是 `apps/web-ele/src/preferences.ts`；禁止在任何 `.vue` / `.scss` 新增品牌色 hex。
- 组件取色只用 `--el-*` 或 `--primary*` 变量；新增颜色必须走 Element Plus 变量。
- 不改后端、不改 `openapi/openapi.yaml`、不改路由与权限模型。
- 每个任务结束必须通过：`pnpm --filter @vben/web-ele typecheck` 与 `pnpm --filter @vben/web-ele build`。
- `apps/web-ele/src/views` 下 `:empty-text="errorMsg` 与 `window.confirm` 最终必须为 0 处。
- 文案禁止诊断性措辞；心理相关页面保留「本系统不能替代专业心理咨询」。
- 提交前先给 `git status --short` 与 `git diff --stat` 并得到确认；只 commit，不 push。
- 每个任务的提交信息先写入 `.ai-tmp/commit-msg-admin-<slug>.txt`（内容见该任务末尾），再用 `git commit -F` 提交。
- 工作目录：仓库根 `D:\develop\Zhi_Yu`。管理端命令在 `zhiyu-admin-vben/` 下用 pnpm。

## File Structure

**新增**

| 文件 | 职责 |
|---|---|
| `apps/web-ele/src/components/data-state/resolve.ts` | 四态判定的纯函数，无 Vue 依赖，可单测 |
| `apps/web-ele/src/components/data-state/resolve.test.ts` | 四态判定单测 |
| `apps/web-ele/src/components/data-state/DataState.vue` | 错误 / 加载 / 空态渲染，错误带重试 |
| `apps/web-ele/src/components/table-pagination/TablePagination.vue` | 统一分页，`showSizes` 按后端能力开关 |
| `apps/web-ele/src/utils/confirm-action.ts` | 危险操作二次确认，返回 boolean |

**修改**

| 文件 | 变更 |
|---|---|
| `apps/web-ele/src/preferences.ts` | 品牌色 / 语义色 / 圆角 / builtinType |
| `apps/web-ele/package.json` | version 5.8.0 → 5.8.1（失效旧偏好缓存） |
| `apps/web-ele/src/styles/business.scss` | 删颜色覆盖层，只保留布局 / 焦点 / 动效偏好 |
| `apps/web-ele/src/styles/variables.scss` | 只保留 Vben 不提供的阴影与动效令牌 |
| `apps/web-ele/src/components/InheritorOnboarding.vue` | 6 处 `--zy-admin-*` 改为 `--el-*` |
| 8 个列表页 | 错误伪装空态 → `DataState`（Task 3） |
| 10 个列表页 | 内联分页 → `TablePagination`（Task 4） |
| `apps/web-ele/src/views/zhiyu/programs.vue` | `window.confirm` → `confirmAction` |
| `apps/web-ele/src/views/zhiyu/audit-logs.vue` | 抽屉宽度响应式 |

---

### Task 1: 主题引擎收口

**Files:**
- Modify: `apps/web-ele/src/preferences.ts:20`
- Modify: `apps/web-ele/package.json:3`
- Modify: `apps/web-ele/src/styles/variables.scss`（整文件替换）
- Modify: `apps/web-ele/src/styles/business.scss`（整文件替换）
- Modify: `apps/web-ele/src/components/InheritorOnboarding.vue:244,250,261,264,279,280`
- Modify: `apps/web-ele/src/views/zhiyu/emotion-analysis.vue:127,128,138`
- Test: 无单测；以 typecheck + build + grep + 浏览器实测验收

**Interfaces:**
- Produces: `--el-color-primary` 及全色阶由 `preferences.ts` 派生；后续任务只需消费 `--el-*`，不再新增品牌色。

- [ ] **Step 1: 修改 preferences.ts**

把 `apps/web-ele/src/preferences.ts` 的 `theme` 改为：

```ts
  theme: {
    mode: 'light',
    // 自定义品牌色必须显式声明 custom，否则抽屉高亮与实际色值不一致
    builtinType: 'custom',
    colorPrimary: '#4F46E5',
    colorSuccess: '#047857',
    colorWarning: '#B45309',
    colorDestructive: '#DC2626',
    radius: '0.5',
  },
```

- [ ] **Step 2: 提升应用版本，失效旧偏好缓存**

把 `apps/web-ele/package.json` 的 `"version": "5.8.0"` 改为 `"version": "5.8.1"`。
原因：`main.ts:12-14` 的偏好 namespace 含 `VITE_APP_VERSION`，缓存优先于默认值，不 bump 则老用户看不到新品牌色。

- [ ] **Step 3: 重写 variables.scss**

用以下内容整体替换 `apps/web-ele/src/styles/variables.scss`：

```scss
// 织愈管理端项目令牌：只保留 Vben / Element Plus 不提供的阴影与动效。
// 品牌色、语义色与圆角由 preferences.ts 经 useElementPlusDesignTokens 统一派生，
// 不要在此文件新增颜色或半径令牌。
:root {
  /* ── 阴影（中性灰，不绑定主色） ── */
  --zy-shadow-xs: 0 1px 2px rgba(0, 0, 0, 0.04);
  --zy-shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.06);
  --zy-shadow-md: 0 4px 16px rgba(0, 0, 0, 0.08);
  --zy-shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.1);
  --zy-shadow-xl: 0 12px 32px rgba(0, 0, 0, 0.12);

  /* ── 动效时长 ── */
  --zy-duration-instant: 100ms;
  --zy-duration-fast: 150ms;
  --zy-duration-base: 250ms;
  --zy-duration-slow: 400ms;
  --zy-duration-slower: 600ms;

  /* ── 缓动函数 ── */
  --zy-ease-linear: linear;
  --zy-ease-in: cubic-bezier(0.4, 0, 1, 1);
  --zy-ease-out: cubic-bezier(0, 0, 0.2, 1);
  --zy-ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
  --zy-ease-elastic: cubic-bezier(0.34, 1.56, 0.64, 1);
  --zy-ease-bounce: cubic-bezier(0.68, -0.55, 0.27, 1.55);
  --zy-ease-smooth: cubic-bezier(0.22, 1, 0.36, 1);
}

.dark {
  --zy-shadow-xs: 0 1px 2px rgba(0, 0, 0, 0.3);
  --zy-shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.4);
  --zy-shadow-md: 0 4px 16px rgba(0, 0, 0, 0.5);
  --zy-shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.6);
  --zy-shadow-xl: 0 12px 32px rgba(0, 0, 0, 0.7);
}
```

- [ ] **Step 4: 重写 business.scss**

用以下内容整体替换 `apps/web-ele/src/styles/business.scss`：

```scss
/* Shared business-page layout. Scoped to business screens, not Vben internals.
   Colors come from Vben preferences -> useElementPlusDesignTokens.
   Do not add brand hex or --el-color-* overrides here. */
.zy-business-page {
  max-width: 1600px;
  margin: 0 auto;
  padding: 24px;
  color: var(--el-text-color-primary);

  .zy-business-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    padding: 8px 0;
  }
  .zy-business-heading h1 {
    font-size: 24px;
    font-weight: 600;
    margin: 4px 0 8px;
  }
  .zy-business-eyebrow {
    color: var(--el-color-primary);
    font-size: 12px;
    letter-spacing: 2px;
    margin: 0;
  }
  .zy-business-description {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    margin: 0;
  }
  > .el-card {
    border: 1px solid var(--el-border-color);
    background: var(--el-bg-color);
  }
  .zy-filter-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    font-size: 14px;
    font-weight: 600;
  }
  .zy-filter-content {
    flex-wrap: wrap;
  }
  .el-table {
    --el-table-header-bg-color: var(--el-fill-color-light);
    --el-table-header-text-color: var(--el-text-color-regular);
    --el-table-border-color: var(--el-border-color);
  }
  .el-table th.el-table__cell {
    height: 48px;
    font-weight: 600;
  }
  .el-table td.el-table__cell {
    padding-top: 14px;
    padding-bottom: 14px;
  }
  .el-pagination {
    padding-top: 16px;
    border-top: 1px solid var(--el-border-color);
    overflow-x: auto;
    justify-content: flex-start;
  }
}

.zy-business-page :is(button, a, input, textarea):focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}

@media (max-width: 640px) {
  .zy-business-page {
    padding: 16px;
  }
  .zy-business-page .zy-business-heading {
    align-items: flex-start;
  }
  .zy-business-page .el-card__body {
    padding: 16px;
  }
  .zy-business-page .zy-filter-content > .el-input,
  .zy-business-page .zy-filter-content > .el-select {
    width: 100% !important;
  }
  .el-dialog {
    max-width: calc(100vw - 32px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .zy-business-page *,
  .zy-workspace * {
    animation: none !important;
    transition: none !important;
  }
}
```

- [ ] **Step 5: 替换 InheritorOnboarding 里的平行令牌**

在 `apps/web-ele/src/components/InheritorOnboarding.vue` 中逐条替换（保留行内其余样式不动）：

| 行 | 原值 | 新值 |
|---|---|---|
| 244 | `color: var(--zy-admin-title, #1f2937);` | `color: var(--el-text-color-primary);` |
| 250 | `color: var(--zy-admin-muted, #9ca3af);` | `color: var(--el-text-color-secondary);` |
| 261 | `border-radius: var(--zy-radius-full, 9999px);` | `border-radius: 9999px;` |
| 264 | `border: 2px solid var(--zy-admin-border, #e4e7ed);` | `border: 2px solid var(--el-border-color);` |
| 279 | `background: var(--zy-admin-primary-bg, #eef2ff);` | `background: var(--el-color-primary-light-9);` |
| 280 | `color: var(--zy-admin-muted, #9ca3af);` | `color: var(--el-color-primary);` |

同一文件的 `129` 行 `var(--zy-shadow-sm, …)` **保持不变**，该令牌在 `variables.scss` 中保留。

- [ ] **Step 6: 替换 emotion-analysis.vue 的平行令牌**

在 `apps/web-ele/src/views/zhiyu/emotion-analysis.vue` 中逐条替换：

| 行 | 原值 | 新值 |
|---|---|---|
| 127 | `background: var(--zy-admin-card-bg, #fff);` | `background: var(--el-bg-color);` |
| 128 | `border-radius: var(--zy-radius-lg, 12px);` | `border-radius: var(--el-border-radius-base);` |
| 138 | `color: var(--zy-admin-title, #1f2937);` | `color: var(--el-text-color-primary);` |

`129` 行 `var(--zy-shadow-sm, …)` 保持不变。

- [ ] **Step 7: 类型检查与构建**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

Expected: 两条命令均 PASS，无 TS 报错。

- [ ] **Step 8: 静态门禁**

```bash
rg -- '--zy-admin' zhiyu-admin-vben/apps/web-ele/src    # 期望 0
rg -- '--zy-radius' zhiyu-admin-vben/apps/web-ele/src   # 期望 0
rg 'html:not\(\.dark\)' zhiyu-admin-vben/apps/web-ele/src # 期望 0
rg -- '--el-color-primary:\s*#' zhiyu-admin-vben/apps/web-ele/src # 期望 0
```

Expected: 四条均无输出。

- [ ] **Step 9: 浏览器实测主题链路**

```bash
pnpm --filter @vben/web-ele dev
```

打开打印出的地址，在 DevTools Console 执行：

```js
getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary')
```

Expected: ` #4F46E5`（含前导空格）。然后：
1. 打开右上角偏好设置抽屉，切到 `violet` → 再执行上面的取值，值必须变化；
2. 切回自定义色 `#4F46E5`，打开任一业务页，主按钮与选中态为靛蓝；
3. 切深色模式，侧栏 / 表头 / 按钮无残留薄荷青或写死靛蓝。

- [ ] **Step 10: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/preferences.ts \
        zhiyu-admin-vben/apps/web-ele/package.json \
        zhiyu-admin-vben/apps/web-ele/src/styles/variables.scss \
        zhiyu-admin-vben/apps/web-ele/src/styles/business.scss \
        zhiyu-admin-vben/apps/web-ele/src/components/InheritorOnboarding.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/emotion-analysis.vue
git commit -F .ai-tmp/commit-msg-admin-theme.txt
```

提交信息文件内容见本任务末尾"提交信息"。执行前按约定先展示 `git status --short` 与 `git diff --stat`。

**提交信息**

```
fix(admin): 收口主题引擎到 Vben preferences

- preferences.ts 设 custom + #4F46E5 与达标的 success/warning/destructive
- 删除 business.scss 与 variables.scss 中与运行时冲突的颜色覆盖层
- 变量文件只保留 Vben 不提供的阴影与动效令牌
- InheritorOnboarding 改用 --el-* 变量，移除平行令牌命名
- 提升应用版本使旧偏好缓存失效
```

---

### Task 2: DataState 基础件（TDD）

**Files:**
- Create: `apps/web-ele/src/components/data-state/resolve.ts`
- Test: `apps/web-ele/src/components/data-state/resolve.test.ts`
- Create: `apps/web-ele/src/components/data-state/DataState.vue`

**Interfaces:**
- Produces:
  - `type DataStateKind = 'empty' | 'error' | 'loading' | 'ready'`
  - `resolveDataState(input: { error?: string; isEmpty: boolean; loading: boolean }): DataStateKind`
  - `<DataState :loading :error empty-text empty-hint @retry />`，可在 `el-table` 的 `#empty` 槽内使用

- [ ] **Step 1: 写失败测试**

创建 `apps/web-ele/src/components/data-state/resolve.test.ts`：

```ts
import { describe, expect, it } from 'vitest';

import { resolveDataState } from './resolve';

describe('resolveDataState', () => {
  it('error 优先于 loading，重试期间不显示骨架', () => {
    expect(
      resolveDataState({ error: '加载失败', isEmpty: true, loading: true }),
    ).toBe('error');
  });

  it('无错误且加载中时显示 loading', () => {
    expect(
      resolveDataState({ error: '', isEmpty: true, loading: true }),
    ).toBe('loading');
  });

  it('请求成功但没有数据时显示 empty', () => {
    expect(
      resolveDataState({ error: '', isEmpty: true, loading: false }),
    ).toBe('empty');
  });

  it('有数据时返回 ready，由表格自行渲染', () => {
    expect(
      resolveDataState({ error: '', isEmpty: false, loading: false }),
    ).toBe('ready');
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd zhiyu-admin-vben
pnpm vitest run --dom apps/web-ele/src/components/data-state/resolve.test.ts
```

Expected: FAIL，报错信息包含 `Failed to resolve import "./resolve"` 或 `resolveDataState is not a function`。

- [ ] **Step 3: 实现纯函数**

创建 `apps/web-ele/src/components/data-state/resolve.ts`：

```ts
export type DataStateKind = 'empty' | 'error' | 'loading' | 'ready';

export interface DataStateInput {
  error?: string;
  isEmpty: boolean;
  loading: boolean;
}

export function resolveDataState({
  error,
  isEmpty,
  loading,
}: DataStateInput): DataStateKind {
  if (error) {
    return 'error';
  }
  if (loading) {
    return 'loading';
  }
  if (isEmpty) {
    return 'empty';
  }
  return 'ready';
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
pnpm vitest run --dom apps/web-ele/src/components/data-state/resolve.test.ts
```

Expected: PASS，4 tests passed。

- [ ] **Step 5: 实现组件**

创建 `apps/web-ele/src/components/data-state/DataState.vue`：

```vue
<script lang="ts" setup>
import { computed } from 'vue';

import { resolveDataState } from './resolve';

const props = withDefaults(
  defineProps<{
    emptyHint?: string;
    emptyText?: string;
    error?: string;
    loading?: boolean;
  }>(),
  {
    emptyHint: '',
    emptyText: '暂无数据',
    error: '',
    loading: false,
  },
);

const emit = defineEmits<{ retry: [] }>();

const kind = computed(() =>
  resolveDataState({
    error: props.error,
    isEmpty: true,
    loading: props.loading,
  }),
);
</script>

<template>
  <div class="data-state" aria-live="polite">
    <el-result
      v-if="kind === 'error'"
      icon="error"
      role="alert"
      title="加载失败"
      :sub-title="error"
    >
      <template #extra>
        <el-button type="primary" @click="emit('retry')">重试</el-button>
      </template>
    </el-result>
    <p v-else-if="kind === 'loading'" class="data-state__loading">正在加载…</p>
    <el-empty v-else :description="emptyText">
      <p v-if="emptyHint" class="data-state__hint">{{ emptyHint }}</p>
    </el-empty>
  </div>
</template>

<style scoped>
.data-state {
  padding: 24px 0;
  text-align: center;
}
.data-state__loading {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.data-state__hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
```

- [ ] **Step 6: 类型检查**

```bash
pnpm --filter @vben/web-ele typecheck
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/components/data-state
git commit -F .ai-tmp/commit-msg-admin-datastate.txt
```

**提交信息**

```
feat(admin): 新增 DataState 四态基础件

- resolveDataState 纯函数承载 error/loading/empty/ready 判定并覆盖单测
- DataState 在表格空槽内渲染错误与重试，错误不再伪装成空数据
- 全部取色走 --el-* 变量
```

---

### Task 3: 8 个列表页迁移到 DataState

**Files:**
- Modify: `apps/web-ele/src/views/zhiyu/audit.vue:178`
- Modify: `apps/web-ele/src/views/zhiyu/audit-logs.vue:175`
- Modify: `apps/web-ele/src/views/zhiyu/care-reminders.vue:132`
- Modify: `apps/web-ele/src/views/zhiyu/groups.vue:168`
- Modify: `apps/web-ele/src/views/zhiyu/inheritors.vue:328`
- Modify: `apps/web-ele/src/views/zhiyu/my-projects.vue:156`
- Modify: `apps/web-ele/src/views/zhiyu/users.vue:393`
- Modify: `apps/web-ele/src/views/zhiyu/works.vue:221`

**Interfaces:**
- Consumes: `DataState`（Task 2）、各页已有的 `loading` / `errorMsg` / `fetchList()`
- Produces: `src/views` 下不再出现 `:empty-text="errorMsg`

- [ ] **Step 1: 逐页替换（8 个文件同一套动作）**

对下列每个文件执行三步：

1. 在 `<script setup>` 的 import 区末尾加一行：
   ```ts
   import DataState from '#/components/data-state/DataState.vue';
   ```
2. 从该页 `<el-table ...>` 开标签中删除 `:empty-text="errorMsg || '…'"` 属性。
3. 在该 `<el-table ...>` 开标签的 `>` 之后、第一个 `<el-table-column>` 之前插入：
   ```vue
         <template #empty>
           <DataState
             :loading="loading"
             :error="errorMsg"
             empty-text="__EMPTY__"
             @retry="fetchList"
           />
         </template>
   ```

各文件 `__EMPTY__` 取值：

| 文件 | empty-text |
|---|---|
| `audit.vue` | `没有匹配的项目` |
| `audit-logs.vue` | `暂无审计日志` |
| `care-reminders.vue` | `暂无关怀提醒` |
| `groups.vue` | `暂无小组` |
| `inheritors.vue` | `暂无传承人` |
| `my-projects.vue` | `暂无项目` |
| `users.vue` | `暂无用户` |
| `works.vue` | `暂无作品` |

注意：不要改动各页 `catch` 里对 `errorMsg` 的赋值，也不要删除已有的 `el-alert`（如有）。

- [ ] **Step 2: 静态门禁**

```bash
rg ':empty-text="errorMsg' zhiyu-admin-vben/apps/web-ele/src/views
```

Expected: 无输出（0 处）。

- [ ] **Step 3: 类型检查与构建**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

Expected: 均 PASS。

- [ ] **Step 4: 浏览器验证错误与空态**

启动 `pnpm --filter @vben/web-ele dev`，对任一列表页：

1. 正常加载：列表有数据时不出现 `DataState`；
2. 把浏览器 DevTools 切到 Offline 后点刷新：表格区域出现「加载失败」与「重试」按钮，不出现「暂无…」；
3. 恢复网络点「重试」：数据回来，错误消失；
4. 用筛选条件造一个 0 结果：显示「暂无…」，不出现重试按钮。

- [ ] **Step 5: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/audit.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/audit-logs.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/care-reminders.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/groups.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/inheritors.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/my-projects.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/users.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/works.vue
git commit -F .ai-tmp/commit-msg-admin-emptystate.txt
```

**提交信息**

```
fix(admin): 列表请求失败不再伪装成空数据

- 8 个列表页的 :empty-text="errorMsg" 改为 DataState 空槽
- 失败时显示错误与重试，成功且无数据时才显示暂无文案
```

---

### Task 4: 统一分页组件（按后端能力分级）

**Files:**
- Create: `apps/web-ele/src/components/table-pagination/TablePagination.vue`
- Modify（开启 showSizes，5 页）:
  - `apps/web-ele/src/views/zhiyu/audit-logs.vue:39,79,205-212`
  - `apps/web-ele/src/views/zhiyu/groups.vue:43,57,227-234`
  - `apps/web-ele/src/views/zhiyu/inheritors.vue:47,99,387-394`
  - `apps/web-ele/src/views/zhiyu/users.vue:54,114,443-450`
  - `apps/web-ele/src/views/zhiyu/works.vue:53,96,294-301`
- Modify（保持固定条数，5 页）:
  - `apps/web-ele/src/views/zhiyu/audit.vue:206-213`
  - `apps/web-ele/src/views/zhiyu/care-reminders.vue:170-177`
  - `apps/web-ele/src/views/zhiyu/campus.vue:27`
  - `apps/web-ele/src/views/zhiyu/operation-logs.vue:9`
  - `apps/web-ele/src/views/zhiyu/my-projects.vue:218-225`

**Interfaces:**
- Produces: `<TablePagination v-model:page v-model:page-size :total :show-sizes @change />`
- Consumes: 后端已支持 `pageSize` 的接口；其余接口 `showSizes=false`

- [ ] **Step 1: 创建组件**

创建 `apps/web-ele/src/components/table-pagination/TablePagination.vue`：

```vue
<script lang="ts" setup>
withDefaults(
  defineProps<{
    page: number;
    pageSize: number;
    pageSizes?: number[];
    showSizes?: boolean;
    total: number;
  }>(),
  {
    pageSizes: () => [10, 20, 50, 100],
    showSizes: false,
  },
);

const emit = defineEmits<{
  'update:page': [value: number];
  'update:pageSize': [value: number];
  change: [page: number];
}>();

function handlePage(value: number) {
  emit('update:page', value);
  emit('change', value);
}

function handleSize(value: number) {
  emit('update:pageSize', value);
  emit('update:page', 1);
  emit('change', 1);
}
</script>

<template>
  <el-pagination
    v-if="total > 0"
    class="mt-4 flex justify-end"
    :current-page="page"
    :layout="
      showSizes
        ? 'total, sizes, prev, pager, next, jumper'
        : 'total, prev, pager, next, jumper'
    "
    :page-size="pageSize"
    :page-sizes="pageSizes"
    :total="total"
    @update:current-page="handlePage"
    @update:page-size="handleSize"
  />
</template>
```

- [ ] **Step 2: 五个支持 pageSize 的页面开启尺寸切换**

对 `audit-logs.vue`、`groups.vue`、`inheritors.vue`、`users.vue`、`works.vue` 各做三处修改：

1. 把 `const pageSize = 10;` 改为 `const pageSize = ref(20);`（这些文件已从 vue 引入 `ref`）。
2. 在 `fetchList()` 的 `const params: any = { page: currentPage.value };` 后插入一行：
   ```ts
       pageSize: pageSize.value,
   ```
   这些接口的 params 类型已包含 `pageSize?: number`，无需改 `src/api/zhiyu/*`。
3. 用以下代码替换原 `<el-pagination ... />` 整块：
   ```vue
         <TablePagination
           v-model:page="currentPage"
           v-model:page-size="pageSize"
           :show-sizes="true"
           :total="total"
           @change="handlePageChange"
         />
   ```
   并在 `<script setup>` import 区加入：
   ```ts
   import TablePagination from '#/components/table-pagination/TablePagination.vue';
   ```

- [ ] **Step 3: 五个不支持 pageSize 的页面关闭尺寸切换**

对 `audit.vue`、`care-reminders.vue`、`my-projects.vue` 用：

```vue
      <TablePagination
        v-model:page="currentPage"
        :page-size="pageSize"
        :total="total"
        @change="handlePageChange"
      />
```

对 `campus.vue`、`operation-logs.vue`（原为硬编码 20）用：

```vue
      <TablePagination
        v-model:page="page"
        :page-size="20"
        :total="total"
        @change="load"
      />
```

注意：`campus.vue` 与 `operation-logs.vue` 的分页变量名是 `page`、加载函数是 `load`，不要照抄成 `currentPage` / `fetchList`。同时给这 5 个文件加 `TablePagination` import。

- [ ] **Step 4: 静态检查**

```bash
rg 'layout="total, prev, pager, next"' zhiyu-admin-vben/apps/web-ele/src/views
rg 'layout="total,prev,pager,next"' zhiyu-admin-vben/apps/web-ele/src/views
```

Expected: 均无输出（内联分页已全部替换）。

- [ ] **Step 5: 类型检查与构建**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

Expected: 均 PASS。

- [ ] **Step 6: 浏览器验证**

1. 打开「作品管理」：分页出现每页条数选择，选 50 后请求参数含 `pageSize=50`，表格行数变化；
2. 打开「关怀提醒」：分页**没有**每页条数选择，且请求仍只带 `page`；
3. 在任一支持尺寸的页面把每页改成 50 后翻到第 3 页，再改回 10：页码回到 1，列表重新加载。

- [ ] **Step 7: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/components/table-pagination \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/audit-logs.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/groups.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/inheritors.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/users.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/works.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/audit.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/care-reminders.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/campus.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/operation-logs.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/my-projects.vue
git commit -F .ai-tmp/commit-msg-admin-pagination.txt
```

**提交信息**

```
refactor(admin): 统一列表分页并按接口能力分级

- 新增 TablePagination，showSizes 控制每页条数选择
- 仅对后端支持 pageSize 的 5 个接口开启尺寸切换并透传参数
- 仅接受 page 的接口保持固定条数，避免前端与服务端不一致
```

---

### Task 5: 危险操作确认与抽屉宽度收尾

**Files:**
- Create: `apps/web-ele/src/utils/confirm-action.ts`
- Modify: `apps/web-ele/src/views/zhiyu/programs.vue:27`（`window.confirm`）
- Modify: `apps/web-ele/src/views/zhiyu/audit-logs.vue:220`（`size="50%"`）

**Interfaces:**
- Produces: `confirmAction(options: { confirmText?: string; danger?: boolean; message: string; title: string }): Promise<boolean>`

- [ ] **Step 1: 创建确认工具**

创建 `apps/web-ele/src/utils/confirm-action.ts`：

```ts
import { ElMessageBox } from 'element-plus';

export interface ConfirmActionOptions {
  confirmText?: string;
  danger?: boolean;
  message: string;
  title: string;
}

export async function confirmAction({
  confirmText = '确认',
  danger = false,
  message,
  title,
}: ConfirmActionOptions): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      cancelButtonText: '取消',
      confirmButtonText: confirmText,
      distinguishCancelAndClose: true,
      type: danger ? 'warning' : 'info',
    });
    return true;
  } catch {
    return false;
  }
}
```

- [ ] **Step 2: 替换 programs.vue 的 window.confirm**

把 `programs.vue:27` 的整行：

```ts
function template(){if(form.description&&!window.confirm('用八周课程结构替换当前课程说明？'))return;form.kind='COURSE';form.description='第 1 周：认识材料与安全约定\n第 2 周：感官觉察与基础技法\n第 3 周：以色彩表达当下\n第 4 周：体验节奏与重复\n第 5 周：纹样与个人故事\n第 6 周：合作创作与边界\n第 7 周：整理作品与回看\n第 8 周：分享与后续计划\n\n请补充每周实际日期、地点、带领者与所需材料。课程不替代专业心理咨询。'}
```

改为：

```ts
async function template(){if(form.description&&!(await confirmAction({confirmText:'替换',message:'用八周课程结构替换当前课程说明？现有说明会被覆盖。',title:'替换课程说明'})))return;form.kind='COURSE';form.description='第 1 周：认识材料与安全约定\n第 2 周：感官觉察与基础技法\n第 3 周：以色彩表达当下\n第 4 周：体验节奏与重复\n第 5 周：纹样与个人故事\n第 6 周：合作创作与边界\n第 7 周：整理作品与回看\n第 8 周：分享与后续计划\n\n请补充每周实际日期、地点、带领者与所需材料。课程不替代专业心理咨询。'}
```

在 import 区加入：

```ts
import { confirmAction } from '#/utils/confirm-action';
```

- [ ] **Step 3: 抽屉宽度响应式**

把 `audit-logs.vue:220` 的 `size="50%"` 改为 `size="min(560px, 100vw)"`。

- [ ] **Step 4: 静态门禁**

```bash
rg 'window\.confirm' zhiyu-admin-vben/apps/web-ele/src/views   # 期望 0
rg 'size="50%"' zhiyu-admin-vben/apps/web-ele/src/views        # 期望 0
```

- [ ] **Step 5: 类型检查与构建**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

Expected: 均 PASS。

- [ ] **Step 6: 浏览器验证**

1. 打开「项目管理」，点"套用八周模板"且已有说明：弹出 Element Plus 确认框，Esc / 取消不修改，确认后替换；
2. 打开「审计日志」详情抽屉，把窗口缩到 375px：抽屉占满宽度且内容可滚动，无横向溢出。

- [ ] **Step 7: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/utils/confirm-action.ts \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/programs.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/audit-logs.vue
git commit -F .ai-tmp/commit-msg-admin-confirm.txt
```

**提交信息**

```
fix(admin): 统一危险操作确认与抽屉宽度

- 新增 confirmAction 包装 ElMessageBox，返回 boolean 便于早退
- programs.vue 移除 window.confirm，保留原有覆盖提示语义
- 审计日志抽屉改为 min(560px, 100vw)，窄屏可用
```

---

## 自检记录

- **Spec 覆盖**：B1 → Task 1；D6 四态 → Task 2 + Task 3；D7 分页 → Task 4；D7 确认与抽屉 → Task 5。D10 焦点环在 Task 1 的 business.scss 中保留。D5 缓存失效在 Task 1 Step 2。B3 试点页、B4 图表桥、B5 滚动改造不在本计划，见下节。
- **占位符**：无 TBD / TODO；每个代码步骤给出可直接粘贴的内容。
- **类型一致性**：`resolveDataState` 的入参与 `DataState` props 对应；`TablePagination` 的 `update:page` / `update:pageSize` 与 `v-model:page` / `v-model:page-size` 对应；`confirmAction` 返回 `Promise<boolean>`，调用处用 `await` 取反。

## 后续计划拆分（本计划不含）

| 计划 | 范围 | 依赖 |
|---|---|---|
| `2026-10-26-admin-pilot-pages.md` | B3：dashboard / care-reminders / programs 三试点页 + `basic.vue` 去演示内容 + 403/404 + 登录页令牌化 | 本计划的 DataState / TablePagination / confirmAction |
| `2026-10-26-admin-chart-theme.md` | B4：`useChartTheme` + `ChartCard` + `OperationsChart` / `emotion-analysis` 色板替换 + 空态 | 本计划的主题引擎（Task 1） |
| `2026-10-26-admin-remaining-pages.md` | B5：其余列表页与 `OperationsBoard` / `layouts/auth.vue` 硬编码清理 | 以上三份 |

后端补齐 `pageSize`（`/admin/project-catalog`、`/admin/care-reminders`、`/admin/campus-verifications`、`/admin/operation-logs`）与 `audit.vue` 搜索正确性，属独立后端任务，需同步 OpenAPI 与测试，不纳入前端计划。
