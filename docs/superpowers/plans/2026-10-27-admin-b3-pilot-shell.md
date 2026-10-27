# 织愈管理端 B3 试点页与壳层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把管理端壳层与三个试点页从“能用的后台”提升为可交付的企业级界面：去掉模板残留、登录/403/404 品牌化、补齐离线提示，并让 dashboard / 关怀提醒 / 活动预约三页达到统一的状态、密度与无障碍基线。

**Architecture:** 壳层先收口（通知与外部链接下线、offline 全局提示、登录与兜底页令牌化），再逐页改造。三个试点页复用 B1+B2 的主题引擎、`DataState` / `DataErrorBanner` / `TablePagination` / `confirmAction`；仪表盘只做布局与令牌清理，图表主题桥仍归 B4。

**Tech Stack:** Vue 3.5、TypeScript、Vite 8、Element Plus 2.14、Vue Vben Admin 5.8.x、Vitest

**Spec:** `docs/superpowers/specs/2026-10-26-admin-ui-baseline-design.md`

## Global Constraints

- 品牌色唯一入口是 `apps/web-ele/src/preferences.ts`；禁止在任何 `.vue` / `.scss` 新增品牌色 hex。取色只用 `--el-*`。
- 不改后端、不改 `openapi/openapi.yaml`、不改路由与权限模型。
- 每个任务结束必须通过 `pnpm --filter @vben/web-ele typecheck` 与 `pnpm --filter @vben/web-ele build`（在 `zhiyu-admin-vben` 下运行）。
- 文案禁止诊断性措辞；心理相关页面保留「本系统不能替代专业心理咨询」。
- 不接入/不修改 logo；`public/logo.svg`、`/logo.png` 保持现状。
- 不修改 `components/OperationsChart.vue` 的图表色板（B4 负责）。
- 只 commit，不 push；提交前展示 `git status --short` 与 `git diff --stat`（本计划按既有约定由子代理直接 commit，controller 每任务后展示状态）。
- 工作目录：仓库根 `D:\develop\Zhi_Yu`；管理端命令在 `zhiyu-admin-vben/`。

## 已知修正（避免错误前提）

- 页头**没有**调色板控件。`AuthenticationColorToggle` 只存在于登录页 toolbar，本项目的 `layouts/auth.vue` 也未使用；页头只有明暗切换（`themeToggle`）与偏好齿轮。之前“一键改品牌色”的结论作废，本计划不删任何不存在的控件。
- 仪表盘 KPI 已是 4 张，满足 D9；本计划不增加 KPI。
- `FilterBar` 与 `StatusTag` 本计划**不抽象**：关怀提醒只有一个状态筛选，活动预约是状态卡片 + 工具栏，两者形态不同；现在抽一个双用组件是过早抽象。关怀提醒在原位补“重置/响应式”，活动预约补状态流转矩阵。B5 统一剩余列表时再评估 `FilterBar`。

## File Structure

**新增**

| 文件 | 职责 |
|---|---|
| `apps/web-ele/src/components/offline-banner/OfflineBanner.vue` | 全局离线提示条，监听 online/offline |
| `apps/web-ele/src/views/zhiyu/programs-status.ts` | 活动状态流转矩阵（纯函数） |
| `apps/web-ele/src/views/zhiyu/programs-status.test.ts` | 状态矩阵单测 |

**修改**

| 文件 | 变更 |
|---|---|
| `apps/web-ele/src/preferences.ts` | 关闭通知 widget、移除 order 中的 notification；版本 bump 配合 |
| `apps/web-ele/package.json` | 5.8.1 → 5.8.2（失效旧偏好缓存） |
| `apps/web-ele/src/layouts/basic.vue` | 删演示通知、Vben docs/GitHub 菜单、demo 身份 |
| `apps/web-ele/src/app.vue` | 挂载 OfflineBanner |
| `apps/web-ele/src/layouts/auth.vue` | 15 个字面色令牌化 |
| `apps/web-ele/src/views/_core/authentication/login.vue` | 21 个字面色令牌化，保留无障碍 |
| `apps/web-ele/src/views/_core/fallback/forbidden.vue` | 品牌化 403 |
| `apps/web-ele/src/views/_core/fallback/not-found.vue` | 品牌化 404 |
| `apps/web-ele/src/views/zhiyu/components/OperationsBoard.vue` | 28 个字面色令牌化、删本地 night 调色板、错误保留数据 |
| `apps/web-ele/src/views/zhiyu/care-reminders.vue` | 逐行 busy、抽屉 loading/重试、筛选重置 |
| `apps/web-ele/src/views/zhiyu/programs.vue` | 业务页壳、操作收纳、状态流转、客户端分页、四态、去 3 个 hex |

---

### Task 1: 壳层去模板化 + 全局离线提示

**Files:**
- Modify: `apps/web-ele/src/layouts/basic.vue`
- Modify: `apps/web-ele/src/preferences.ts`
- Modify: `apps/web-ele/package.json:3`
- Modify: `apps/web-ele/src/app.vue`
- Create: `apps/web-ele/src/components/offline-banner/OfflineBanner.vue`

**Interfaces:**
- Produces: 页头不再出现通知铃铛与 docs/GitHub 菜单；全局 `OfflineBanner` 在任意路由可见。

- [ ] **Step 1: 关闭通知 widget 并 bump 版本**

在 `apps/web-ele/src/preferences.ts` 的**顶层**加入 `widget`（Vben 的 `Preferences.widget` 是顶层字段，不是 `app.widget`）：

```ts
  widget: {
    notification: false,
    notificationButtonPosition: 'none',
  },
```

**不要覆盖 `widget.order`**：`initPreferences` 用 defu 合并，数组会拼接而不是替换，冷缓存首屏会把每个页头控件渲染两遍。默认 order 已包含全部控件，notification 在 flag 为 false 时不渲染。

把 `apps/web-ele/package.json` 的 `"version": "5.8.1"` 改为 `"version": "5.8.2"`（namespace 含版本号，旧缓存自然失效）。

- [ ] **Step 2: 删除 `basic.vue` 的演示通知与外部链接**

在 `apps/web-ele/src/layouts/basic.vue` 中删除：

1. 顶部 import：`NotificationItem`、`VBEN_DOC_URL`、`VBEN_GITHUB_URL`、`BookOpenText`、`CircleHelp`、`SvgGithubIcon`、`Notification`、`openWindow`。
2. `const notifications = ref<NotificationItem[]>([...])` 整块（约 27-78 行）。
3. `const showDot = computed(...)`（约 86-88 行）。
4. `menus` 中除 Profile 外的三个菜单项（docs、GitHub、QA），只保留：

```ts
const menus = computed(() => [
  {
    handler: () => {
      router.push({ name: 'Profile' });
    },
    icon: 'lucide:user',
    text: $t('page.auth.profile'),
  },
]);
```

5. 通知相关函数：`handleNoticeClear`、`markRead`、`remove`、`handleMakeAll`、`viewAll`、`handleClick`、`navigateTo`。
6. 模板中的 `<template #notification> ... </template>` 整块。
7. `#user-dropdown` 上的 `description="ann.vben@gmail.com"` 与 `tag-text="Pro"` 两个属性。

保留：`UserDropdown`、`LockScreen`、`AuthenticationLoginExpiredModal`、`InheritorOnboarding`、`handleLogout`、Profile 菜单、水印、`useRouter`。

- [ ] **Step 3: 新建 OfflineBanner**

创建 `apps/web-ele/src/components/offline-banner/OfflineBanner.vue`：

```vue
<script lang="ts" setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';

const online = ref(true);

function sync() {
  online.value = navigator.onLine;
}

onMounted(() => {
  window.addEventListener('online', sync);
  window.addEventListener('offline', sync);
  sync();
});

onBeforeUnmount(() => {
  window.removeEventListener('online', sync);
  window.removeEventListener('offline', sync);
});
</script>

<template>
  <div v-if="!online" class="offline-banner" role="status" aria-live="polite">
    网络已断开，部分内容可能无法加载。网络恢复后请重试。
  </div>
</template>

<style scoped>
.offline-banner {
  position: fixed;
  bottom: 20px;
  left: 50%;
  z-index: 3000;
  max-width: calc(100vw - 32px);
  padding: 10px 16px;
  font-size: 13px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-light);
  transform: translateX(-50%);
}
</style>
```

- [ ] **Step 4: 在 app.vue 挂载**

把 `apps/web-ele/src/app.vue` 改为：

```vue
<script lang="ts" setup>
import { useElementPlusDesignTokens } from '@vben/hooks';

import { ElConfigProvider } from 'element-plus';

import OfflineBanner from '#/components/offline-banner/OfflineBanner.vue';
import { elementLocale } from '#/locales';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <OfflineBanner />
    <RouterView />
  </ElConfigProvider>
</template>
```

- [ ] **Step 5: 类型检查与构建**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

Expected: 均 PASS。

- [ ] **Step 6: 静态门禁**

```bash
rg 'ann\.vben@gmail\.com|收到了 14 份新周报|VBEN_DOC_URL|VBEN_GITHUB_URL' zhiyu-admin-vben/apps/web-ele/src   # 期望 0
rg 'notification' zhiyu-admin-vben/apps/web-ele/src/layouts/basic.vue   # 期望 0
```

- [ ] **Step 7: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/layouts/basic.vue \
        zhiyu-admin-vben/apps/web-ele/src/preferences.ts \
        zhiyu-admin-vben/apps/web-ele/package.json \
        zhiyu-admin-vben/apps/web-ele/src/app.vue \
        zhiyu-admin-vben/apps/web-ele/src/components/offline-banner
git commit -F .ai-tmp/commit-msg-admin-shell.txt
```

**提交信息**

```
feat(admin): 壳层去模板化并增加全局离线提示

- 关闭通知 widget，删除演示通知与 Vben docs/GitHub 菜单、demo 身份
- 新增 OfflineBanner，监听 online/offline，固定在底部提示
- 版本 bump 至 5.8.2，使旧偏好缓存失效
```

---

### Task 2: 登录页与 auth 壳令牌化

**Files:**
- Modify: `apps/web-ele/src/layouts/auth.vue:3-4`
- Modify: `apps/web-ele/src/views/_core/authentication/login.vue:33-34`

**Interfaces:**
- Consumes: `--el-*` 主题变量（Task B1 已收口）。
- Produces: 登录页与 auth 壳无字面色，支持深浅模式，保留现有 label/aria/键盘行为。

- [ ] **Step 1: 替换 auth.vue 的 15 个字面色**

按下表逐条替换 `apps/web-ele/src/layouts/auth.vue` 的样式（其余规则不动；表中是去重后的值，替换所有出现处）：

| 原值 | 新值 |
|---|---|
| `#f9faf7` | `var(--el-bg-color-page)` |
| `#223b31` | `var(--el-text-color-primary)` |
| `#203f33` | `var(--el-color-primary-dark-2)` |
| `#eef2e9` | `var(--el-color-white)` |
| `#aec4af` | `rgb(255 255 255 / 72%)` |
| `#b6c8a6` | `rgb(255 255 255 / 62%)` |
| `#b7cabe` | `rgb(255 255 255 / 76%)` |
| `#bccbaa` | `rgb(255 255 255 / 66%)` |
| `#adc1b0` | `rgb(255 255 255 / 58%)` |
| `#ffffff20` | `rgb(255 255 255 / 20%)` |
| `#7d8d82` | `var(--el-text-color-secondary)` |
| `#4c8a69` | `var(--el-color-primary)` |
| `#586e62` | `var(--el-text-color-secondary)` |
| `#bdd1aa` | `var(--el-color-primary-light-5)` |

- [ ] **Step 2: 替换 login.vue 的 21 个字面色**

按下表逐条替换 `apps/web-ele/src/views/_core/authentication/login.vue` 的样式（保留所有 `label`、`aria-invalid`、`aria-describedby`、`role="alert"`、44px 显示密码按钮；表中是去重后的值，替换所有出现处）：

| 原值 | 新值 |
|---|---|
| `#243d31` | `var(--el-text-color-primary)` |
| `#586e62` | `var(--el-text-color-secondary)` |
| `#dce3d9` | `var(--el-border-color)` |
| `#588a6c` | `var(--el-color-primary)` |
| `#54886c14`（box-shadow） | `var(--el-color-primary-light-9)` |
| `#8a9f8d` | `var(--el-text-color-placeholder)` |
| `#203c2e` | `var(--el-text-color-primary)` |
| `#a3ada4` | `var(--el-text-color-placeholder)` |
| `#66836c` | `var(--el-text-color-secondary)` |
| `#2d624b` | `var(--el-color-primary)` |
| `#244a3520`（box-shadow） | `var(--el-color-primary-light-9)` |
| `#214e3b` | `var(--el-color-primary-dark-2)` |
| `#e4e8df` | `var(--el-border-color-lighter)` |
| `#718570` | `var(--el-text-color-secondary)` |
| `#b7c4b5` | `var(--el-border-color)` |
| `#a45039` | `var(--el-color-danger)` |
| `#9ac1a4` | `var(--el-color-primary-light-5)` |

- [ ] **Step 3: 类型检查、构建与门禁**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
cd ..
rg --pcre2 '#[0-9a-fA-F]{3,8}|rgba\(' zhiyu-admin-vben/apps/web-ele/src/layouts/auth.vue zhiyu-admin-vben/apps/web-ele/src/views/_core/authentication/login.vue
```

Expected: 前两条 PASS；第三条无输出（`rgb(255 255 255 / …)` 是函数式写法，不含 `#`/`rgba(`）。

- [ ] **Step 4: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/layouts/auth.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/_core/authentication/login.vue
git commit -F .ai-tmp/commit-msg-admin-auth.txt
```

**提交信息**

```
fix(admin): 登录页与 auth 壳改用主题令牌

- auth 壳左栏改为主色深阶，文字层级改白色透明阶
- 登录表单按钮/焦点/边框/错误态全部改用 --el-* 变量
- 保留 label、aria-invalid、role=alert 与 44px 密码切换按钮
```

---

### Task 3: 403 / 404 品牌化兜底

**Files:**
- Modify: `apps/web-ele/src/views/_core/fallback/forbidden.vue`
- Modify: `apps/web-ele/src/views/_core/fallback/not-found.vue`

**Interfaces:**
- Consumes: `vue-router`、Element Plus `ElResult` / `ElButton`。
- Produces: 403/404 使用项目文案与返回工作台动作，不再显示 Vben 通用文案。

- [ ] **Step 1: 重写 403**

用以下内容整体替换 `apps/web-ele/src/views/_core/fallback/forbidden.vue`：

```vue
<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { ElButton, ElResult } from 'element-plus';

defineOptions({ name: 'ForbiddenView' });

const router = useRouter();
</script>

<template>
  <div class="fallback-page">
    <ElResult
      icon="warning"
      title="没有管理端访问权限"
      sub-title="当前账号无权访问该页面。学生账号请使用学生端；如需开通权限，请联系平台管理员。"
    >
      <template #extra>
        <ElButton type="primary" @click="router.push('/zhiyu/dashboard')">
          返回工作台
        </ElButton>
      </template>
    </ElResult>
  </div>
</template>

<style scoped>
.fallback-page {
  display: grid;
  place-items: center;
  min-height: 60vh;
  padding: 48px 24px;
}
</style>
```

- [ ] **Step 2: 重写 404**

用以下内容整体替换 `apps/web-ele/src/views/_core/fallback/not-found.vue`：

```vue
<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { ElButton, ElResult } from 'element-plus';

defineOptions({ name: 'NotFoundView' });

const router = useRouter();
</script>

<template>
  <div class="fallback-page">
    <ElResult
      icon="info"
      title="页面不存在或已移动"
      sub-title="请检查链接是否完整；也可以返回工作台继续处理待办。"
    >
      <template #extra>
        <ElButton type="primary" @click="router.push('/zhiyu/dashboard')">
          返回工作台
        </ElButton>
      </template>
    </ElResult>
  </div>
</template>

<style scoped>
.fallback-page {
  display: grid;
  place-items: center;
  min-height: 60vh;
  padding: 48px 24px;
}
</style>
```

- [ ] **Step 3: 类型检查与构建**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
```

- [ ] **Step 4: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/views/_core/fallback/forbidden.vue \
        zhiyu-admin-vben/apps/web-ele/src/views/_core/fallback/not-found.vue
git commit -F .ai-tmp/commit-msg-admin-fallback.txt
```

**提交信息**

```
fix(admin): 403/404 改用项目文案与返回动作

- 403 说明学生账号与联系管理员路径，404 说明检查链接
- 统一返回工作台，不再使用 Vben 通用兜底文案
```

---

### Task 4: Dashboard 令牌化与错误保留

**Files:**
- Modify: `apps/web-ele/src/views/zhiyu/components/OperationsBoard.vue`

**Interfaces:**
- Consumes: `DataErrorBanner`（B2）、`--el-*` 变量。
- Produces: dashboard 无字面色、无本地 night 调色板、刷新失败保留已加载数据。

- [ ] **Step 1: 替换模板里的 night 类与错误提示**

1. 把 `<main class="operations" :class="{night:isDark}">` 改为 `<main class="operations">`。
2. 把 `<ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon/>` 改为：

```vue
<DataErrorBanner
  v-if="error"
  :error="error"
  :retrying="loading"
  @retry="load"
/>
```

3. 在 `<script setup>` 顶部加入：

```ts
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
```

4. 从 `element-plus` 导入中删除 `ElAlert`（已由 `DataErrorBanner` 取代）。

- [ ] **Step 2: 错误时保留数据**

把 `load()` 的 catch 改为（删除 `data.value = null`）：

```ts
  } catch {
    if (turn === generation) {
      error.value = '运营数据暂未读取成功，请确认后端与数据库迁移后重试。';
    }
  } finally {
```

- [ ] **Step 3: 删除 `isDark` 依赖**

删除 `import { usePreferences } from '@vben/preferences';` 与 `const {isDark}=usePreferences();` 中的 `isDark`；若 `usePreferences` 无其他用途，整行 import 一并删除。
删除 `<style scoped>` 里的 `.operations.night{...}` 整条规则（本地 light/dark 调色板）。

- [ ] **Step 4: 按下表替换 28 个字面色**

表中是去重后的值，替换所有出现处：
| 原值 | 新值 |
|---|---|
| `--op-card:#fff` | `--op-card: var(--el-bg-color)` |
| `--op-border:#e2e9e4` | `--op-border: var(--el-border-color)` |
| `--op-text:#203b32` | `--op-text: var(--el-text-color-primary)` |
| `--op-muted:#586e62` | `--op-muted: var(--el-text-color-secondary)` |
| `background:#f5f7f4` | `background: var(--el-bg-color-page)` |
| `.overline … color:#749184` | `color: var(--el-text-color-secondary)` |
| `.welcome-strip background:#e7eee4` | `background: var(--el-color-primary-light-9)` |
| `.welcome-strip border:1px solid #dce6d8` | `border: 1px solid var(--el-color-primary-light-8)` |
| `.welcome-strip … color:#284335` | `color: var(--el-text-color-primary)` |
| `.strip-label color:#72856a` | `color: var(--el-text-color-secondary)` |
| `.welcome-strip p color:#6d7f68` | `color: var(--el-text-color-secondary)` |
| `.welcome-strip button background:#2e614c` | `background: var(--el-color-primary)` |
| `.metric-icon background:#eaf3ee` | `background: var(--el-color-primary-light-9)` |
| `.metric-icon color:#43896d` | `color: var(--el-color-primary)` |
| `.tone-1 background:#f7f0e2` | `background: var(--el-color-warning-light-9)` |
| `.tone-1 color:#bc965c` | `color: var(--el-color-warning)` |
| `.tone-2 background:#eaf0f7` | `background: var(--el-color-info-light-9)` |
| `.tone-2 color:#7195b7` | `color: var(--el-color-info)` |
| `.tone-3 background:#f1edf5` | `background: var(--el-color-success-light-9)` |
| `.tone-3 color:#9a82ac` | `color: var(--el-color-success)` |
| `.range .selected background:#2b6751` | `background: var(--el-color-primary)` |
| `button:focus-visible outline:3px solid #65a88b` | `outline: 3px solid var(--el-color-primary)` |

- [ ] **Step 5: 类型检查、构建与门禁**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
cd ..
rg --pcre2 '#[0-9a-fA-F]{3,8}' zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/OperationsBoard.vue
```

Expected: build/typecheck PASS；grep 无输出。

- [ ] **Step 6: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/OperationsBoard.vue
git commit -F .ai-tmp/commit-msg-admin-dashboard.txt
```

**提交信息**

```
fix(admin): 工作台改用主题令牌并保留刷新失败数据

- OperationsBoard 28 个字面色改为 --el-* 变量
- 删除本地 night 调色板，深色模式交给主题引擎
- 刷新失败不再清空 data，改为 DataErrorBanner 可重试
```

---

### Task 5: 关怀提醒逐行忙碌与抽屉加载

**Files:**
- Modify: `apps/web-ele/src/views/zhiyu/care-reminders.vue`

**Interfaces:**
- Consumes: 现有 `fetchList` / `getCareReminders` / `handleView`。
- Produces: `busy: Record<string, string>` 逐行状态；抽屉 `detailLoading` 与重试；筛选重置。

- [ ] **Step 1: 用逐行 busy 替换全局 busy**

在 `care-reminders.vue` 中：

1. 把 `import { onMounted, ref } from 'vue';` 改为 `import { onMounted, reactive, ref } from 'vue';`。
2. 把 `const detail=ref<any>(null),drawer=ref(false),busy=ref(false),detailError=ref('');` 改为：

```ts
const detail = ref<any>(null);
const drawer = ref(false);
const busy = reactive<Record<string, string>>({});
const detailLoading = ref(false);
const detailError = ref('');
const currentRow = ref<any>(null);
```

3. 重写三个函数：

```ts
async function handleView(row: any) {
  currentRow.value = row;
  drawer.value = true;
  detail.value = null;
  detailError.value = '';
  detailLoading.value = true;
  try {
    detail.value = await requestClient.get('/admin/care-reminders/' + row.id);
  } catch (e: any) {
    detailError.value = e.message || '详情加载失败';
  } finally {
    detailLoading.value = false;
  }
}

async function follow(action: string) {
  const id = detail.value?.id;
  if (!id || busy[id]) return;
  busy[id] = action;
  try {
    await requestClient.post('/admin/care-reminders/' + id + '/followups', { action });
    ElMessage.success('跟进已保存');
    detail.value = await requestClient.get('/admin/care-reminders/' + id);
    await fetchList();
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败');
  } finally {
    delete busy[id];
  }
}

async function claim(row: any) {
  if (busy[row.id]) return;
  busy[row.id] = 'CLAIM';
  try {
    await requestClient.post('/admin/care-reminders/' + row.id + '/followups', { action: 'CLAIM' });
    ElMessage.success('任务已领取');
    await fetchList();
    await handleView(row);
  } catch (e: any) {
    ElMessage.error(e.message || '领取失败');
  } finally {
    delete busy[row.id];
  }
}
```

- [ ] **Step 2: 更新模板**

1. 列表“领取”按钮：

```vue
<el-button
  v-if="!row.claimed"
  type="primary"
  link
  size="small"
  :loading="busy[row.id] === 'CLAIM'"
  @click="claim(row)"
>
  领取
</el-button>
```

2. 抽屉内部改为：

```vue
<el-skeleton v-if="detailLoading" :rows="4" animated />
<el-alert
  v-else-if="detailError"
  :title="detailError"
  type="error"
  :closable="false"
>
  <template #default>
    <el-button size="small" @click="handleView(currentRow)">重试</el-button>
  </template>
</el-alert>
<template v-else-if="detail">
  <!-- 原有详情内容保持不变 -->
</template>
```

3. 抽屉按钮的 busy 绑定：

```vue
<el-button
  v-if="!detail.claimed"
  type="primary"
  :loading="busy[detail.id] === 'CLAIM'"
  @click="follow('CLAIM')"
>
  领取任务
</el-button>
<template v-else-if="detail.state !== 'CLOSED'">
  <el-button
    v-for="a in ['REVIEWED', 'CONTACTED', 'REFERRED', 'CLOSE']"
    :key="a"
    :loading="busy[detail.id] === a"
    :disabled="(!!busy[detail.id] && busy[detail.id] !== a) || (a === 'CONTACTED' && Number(detail.can_contact) !== 1)"
    @click="follow(a)"
  >
    {{ actionLabels[a] }}
  </el-button>
</template>
<el-button v-else :loading="busy[detail.id] === 'REOPEN'" @click="follow('REOPEN')">
  重新打开
</el-button>
```

- [ ] **Step 3: 筛选重置**

1. 在状态筛选 `<el-select>` 之后加入：

```vue
<el-button @click="resetFilters">重置</el-button>
```

2. 加入函数：

```ts
function resetFilters() {
  filterStatus.value = '';
  currentPage.value = 1;
  fetchList();
}
```

- [ ] **Step 4: 类型检查、构建与门禁**

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
cd ..
rg 'busy\.value|:loading="busy"' zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/care-reminders.vue
```

Expected: 前两条 PASS；grep 无输出。

- [ ] **Step 5: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/care-reminders.vue
git commit -F .ai-tmp/commit-msg-admin-care.txt
```

**提交信息**

```
fix(admin): 关怀提醒改为逐行忙碌并补抽屉加载

- busy 从全局布尔改为按任务 id 记录动作，其他行保持可操作
- 抽屉打开即 skeleton，详情失败可重试
- 筛选增加重置按钮并回到第一页
```

---

### Task 6: 活动预约壳层、操作收纳与状态流转

**Files:**
- Create: `apps/web-ele/src/views/zhiyu/programs-status.ts`
- Test: `apps/web-ele/src/views/zhiyu/programs-status.test.ts`
- Modify: `apps/web-ele/src/views/zhiyu/programs.vue`

**Interfaces:**
- Produces: `allowedStatuses(status: string): string[]`
- Consumes: `DataState` / `DataErrorBanner` / `TablePagination` / `confirmAction`。

- [ ] **Step 1: 写失败测试**

创建 `apps/web-ele/src/views/zhiyu/programs-status.test.ts`：

```ts
import { describe, expect, it } from 'vitest';

import { allowedStatuses } from './programs-status';

describe('allowedStatuses', () => {
  it('草稿可以保存、发布或取消', () => {
    expect(allowedStatuses('DRAFT')).toEqual(['DRAFT', 'PUBLISHED', 'CANCELLED']);
  });

  it('已发布只能保持或取消', () => {
    expect(allowedStatuses('PUBLISHED')).toEqual(['PUBLISHED', 'CANCELLED']);
  });

  it('已取消是终态', () => {
    expect(allowedStatuses('CANCELLED')).toEqual(['CANCELLED']);
  });

  it('未知状态回退到草稿', () => {
    expect(allowedStatuses('UNKNOWN')).toEqual(['DRAFT']);
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd zhiyu-admin-vben
pnpm vitest run --dom apps/web-ele/src/views/zhiyu/programs-status.test.ts
```

Expected: FAIL，`Failed to resolve import "./programs-status"`。

- [ ] **Step 3: 实现状态矩阵**

创建 `apps/web-ele/src/views/zhiyu/programs-status.ts`：

```ts
export const PROGRAM_STATUS_FLOW: Record<string, string[]> = {
  DRAFT: ['DRAFT', 'PUBLISHED', 'CANCELLED'],
  PUBLISHED: ['PUBLISHED', 'CANCELLED'],
  CANCELLED: ['CANCELLED'],
};

export function allowedStatuses(status: string): string[] {
  return PROGRAM_STATUS_FLOW[status] ?? ['DRAFT'];
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
pnpm vitest run --dom apps/web-ele/src/views/zhiyu/programs-status.test.ts
```

Expected: PASS，4 tests。

- [ ] **Step 5: 壳层与颜色**

1. 根节点 `<div class="program-page">` 改为 `<div class="zy-business-page">`。
2. 删除 scoped 样式中的 `.program-page{padding:24px}`、`header{...}`、`h1{...}`、`p{...}`（`BusinessPageHeader` 已提供页头）。
3. 把 3 处 `#217e76` 改为 `var(--el-color-primary)`：`.status-cards button.active` 的 `border-color`、`.status-cards button:focus-visible` 的 `outline`、`.eyebrow` 的 `color`。

- [ ] **Step 6: 操作列收纳**

把操作列整块替换为：

```vue
<ElTableColumn label="操作" width="200" fixed="right">
  <template #default="{ row }">
    <ElButton link type="primary" @click="edit(row as Program)">编辑</ElButton>
    <ElButton link type="primary" @click="showAttendees(row as Program)">报名与签到</ElButton>
    <ElDropdown trigger="click">
      <ElButton link type="primary">更多</ElButton>
      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem
            v-if="row.kind === 'COURSE'"
            @click="courseId = row.id; courseDialog = true"
          >
            课节
          </ElDropdownItem>
          <ElDropdownItem @click="feedbackId = row.id; feedbackDialog = true">
            反馈
          </ElDropdownItem>
          <ElDropdownItem
            v-if="row.kind !== 'GUIDANCE'"
            @click="teamId = row.id; teamDialog = true"
          >
            协作团队
          </ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>
  </template>
</ElTableColumn>
```

- [ ] **Step 7: 状态流转**

1. 在 script 中加入：

```ts
import { allowedStatuses } from './programs-status';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

const editingStatus = ref('DRAFT');
```

2. 把现有 `vue` 导入改为：

```ts
import { computed, onMounted, reactive, ref, watch } from 'vue';
```

3. 把现有 `element-plus` 导入中的 `ElAlert` 删除，并加入下拉组件：

```ts
import { ElButton, ElCard, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu, ElForm, ElFormItem, ElInput, ElInputNumber, ElSelect, ElOption, ElDatePicker, ElTable, ElTableColumn, ElTag, ElMessage, ElMessageBox } from 'element-plus';
```

2. `edit(p?: Program)` 开头加入：

```ts
  editingStatus.value = p?.status ?? 'DRAFT';
```

3. `save()` 在 `saving.value = true` 之前加入：

```ts
  if (form.status === 'CANCELLED' && editingStatus.value !== 'CANCELLED') {
    const confirmed = await confirmAction({
      confirmText: '取消活动',
      danger: true,
      message: '取消后用户会看到已取消状态，且无法恢复到发布状态。',
      title: '取消活动',
    });
    if (!confirmed) return;
  }
```

4. 发布状态下拉改为：

```vue
<ElSelect v-model="form.status" :disabled="allowedStatuses(editingStatus).length === 1">
  <ElOption
    v-for="s in allowedStatuses(editingStatus)"
    :key="s"
    :label="names[s]"
    :value="s"
  />
</ElSelect>
```

- [ ] **Step 8: 客户端分页与四态**

1. 在 script 中加入：

```ts
const page = ref(1);
const pageSize = 10;
const pagedRows = computed(() =>
  visible.value.slice((page.value - 1) * pageSize, page.value * pageSize),
);

watch([kindFilter, statusFilter, query], () => {
  page.value = 1;
});
```

2. 表格 `:data="visible"` 改为 `:data="pagedRows"`，删除 `empty-text`，在 `<ElTable>` 开标签后插入：

```vue
  <template #empty>
    <DataState
      :loading="loading"
      :error="error"
      empty-text="暂无活动，创建草稿后可发布"
      empty-hint="点击「新建活动」开始创建"
      @retry="load"
    />
  </template>
```

3. 在表格下方加入：

```vue
<TablePagination v-model:page="page" :page-size="10" :total="visible.length" />
```

4. 把 `<ElAlert v-if="error" .../>` 改为：

```vue
<DataErrorBanner
  v-if="error"
  :error="error"
  :retrying="loading"
  @retry="load"
/>
```

5. 在 import 区加入：

```ts
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';
```

- [ ] **Step 9: 类型检查、构建与门禁**

```bash
cd zhiyu-admin-vben
pnpm vitest run --dom apps/web-ele/src/views/zhiyu/programs-status.test.ts
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
cd ..
rg '#217e76|empty-text="暂无活动' zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/programs.vue
```

Expected: 前三 PASS；grep 无输出。

- [ ] **Step 10: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/programs-status.ts \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/programs-status.test.ts \
        zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/programs.vue
git commit -F .ai-tmp/commit-msg-admin-programs.txt
```

**提交信息**

```
feat(admin): 活动预约接入业务壳并收敛操作与状态

- 走 .zy-business-page，去掉页内重复页头与 3 个硬编码绿色
- 操作列收纳为编辑/报名 + 更多，避免 5 个按钮直出
- 状态流转矩阵化，取消发布需二次确认，已取消为终态
- 客户端分页 + DataState/DataErrorBanner 四态
```

---

## 自检记录

- **Spec 覆盖**：D5 缓存 → Task 1 version bump；D6 四态 → Task 4/6（dashboard 错误保留、programs DataState）；D7 列表契约 → Task 5/6；D8 图表桥 → 明确排除（B4）；D10 无障碍 → Task 2/3/5/6；D11 合规 → 保留免责声明与脱敏文案；D12 壳层 → Task 1/2/3；试点页 → Task 4/5/6。
- **占位符**：无 TBD / TODO；CSS 用逐值映射表，组件/函数给出完整代码。
- **类型一致性**：`allowedStatuses` 返回 `string[]`，模板 `v-for` 与 `:disabled` 一致；`busy: Record<string, string>` 与模板 `busy[row.id]` 一致；`DataState` props 与 B2 定义一致；`TablePagination` 的 `v-model:page` / `:page-size` 与 B2 定义一致。
- **已知偏差**：`FilterBar`/`StatusTag` 延后（见“已知修正”）；dashboard 图表色板不在本计划（B4）。

## 后续

- **B4**：`useChartTheme` + `ChartCard` + `OperationsChart` / `emotion-analysis` 色板与空态。
- **B5**：其余列表页与 `OperationsBoard` 残留、`VideoUpload` 旧薄荷青、`comments.vue` 内联分页、`docs/ui-tokens.md` 进一步收敛。
