<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { getDashboardStats } from '#/api/zhiyu/admin';

const router = useRouter();
const { isDark } = usePreferences();

/* ── 色板（深靛蓝系，随主题动态切换文字/边框色） ── */
const C = computed(() => {
  const dark = isDark.value;
  return {
    primary: '#4F46E5',
    primaryLight: '#6366F1',
    primaryBg: dark ? '#1E1B4B' : '#EEF2FF',
    accent: '#F59E0B',
    danger: '#EF4444',
    success: '#10B981',
    border: dark ? '#2A2A2A' : '#E5E7EB',
    title: dark ? '#E5E7EB' : '#1F2937',
    text: dark ? '#B0B0B0' : '#4B5563',
    muted: dark ? '#6B7280' : '#9CA3AF',
  };
});

/* ── 实时时间 ── */
const currentTime = ref('');
let timer: ReturnType<typeof setInterval> | null = null;

function updateTime() {
  const now = new Date();
  const h = String(now.getHours()).padStart(2, '0');
  const m = String(now.getMinutes()).padStart(2, '0');
  const s = String(now.getSeconds()).padStart(2, '0');
  currentTime.value = `${h}:${m}:${s}`;
}

/* ── 问候语 ── */
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 9) return '早安';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const todayStr = computed(() => {
  const d = new Date();
  const weekdays = ['日', '一', '二', '三', '四', '五', '六'];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${weekdays[d.getDay()]}`;
});

/* ── KPI 数据（从后端获取） ── */
const stats = ref({
  totalUsers: 0,
  totalWorks: 0,
  pendingProjects: 0,
  pendingCareReminders: 0,
});

/* 欢迎卡片右侧 3 个数据点 */
const welcomeStats = computed(() => [
  { label: '待办', value: stats.value.pendingProjects, suffix: '/ 10', icon: '📋' },
  { label: '项目', value: 8, suffix: '', icon: '📦' },
  { label: '团队', value: 300, suffix: '', icon: '👥' },
]);

/* ── 项目网格（Mock，织愈非遗项目） ── */
const projects = [
  { name: '扎染', desc: '传统防染印花工艺，感受色彩的流动', icon: '🎨', group: '非遗一组', date: '2024-09-15' },
  { name: '赣绣', desc: '江西地方刺绣工艺，以针代笔绣丹青', icon: '🧵', group: '非遗二组', date: '2024-09-20' },
  { name: '剪纸', desc: '民间团花剪纸，一刀一剪见乾坤', icon: '✂️', group: '非遗一组', date: '2024-09-28' },
  { name: '陶艺', desc: '泥与火的对话，捏塑心形器物', icon: '🏺', group: '非遗三组', date: '2024-10-02' },
  { name: '编织', desc: '竹藤编织，经纬交织修心性', icon: '🧶', group: '非遗二组', date: '2024-10-05' },
  { name: '布艺', desc: '拼布与缝缀，碎片拼合愈心灵', icon: '🧵', group: '非遗三组', date: '2024-10-08' },
];

/* ── 快捷导航（织愈管理端导航） ── */
const shortcuts = [
  { icon: '🏠', label: '工作台', path: '/zhiyu/dashboard' },
  { icon: '📊', label: '数据中心', path: '/zhiyu/data-center/analytics' },
  { icon: '👥', label: '用户管理', path: '/zhiyu/business/users' },
  { icon: '🏛', label: '传承人管理', path: '/zhiyu/business/inheritors' },
  { icon: '🎨', label: '作品管理', path: '/zhiyu/business/works' },
  { icon: '🤝', label: '小组管理', path: '/zhiyu/business/groups' },
];

/* ── 最新动态（织愈语义 Mock） ── */
const activities = computed(() => [
  { avatar: '张', name: '张三', action: '发布了作品', target: '《平安符》', time: '2 分钟前', color: C.value.primary },
  { avatar: '李', name: '李老师', action: '认证了传承人', target: '王师傅', time: '1 小时前', color: C.value.accent },
  { avatar: '明', name: '小明', action: '完成了', target: '扎染方巾创作', time: '3 小时前', color: C.value.success },
  { avatar: '赞', name: '匿名用户', action: '点赞了作品', target: '《赣绣书签》', time: '5 小时前', color: C.value.danger },
  { avatar: '组', name: '治愈小组', action: '新增了', target: '3 位成员', time: '昨天', color: C.value.primaryLight },
  { avatar: '系', name: '系统', action: '发布了公告', target: 'v1.0 更新日志', time: '昨天', color: C.value.muted },
  { avatar: '陈', name: '陈同学', action: '加入了', target: '剪纸释压小组', time: '2 天前', color: C.value.success },
  { avatar: '审', name: '审核系统', action: '驳回了', target: '1 个待审项目', time: '3 天前', color: C.value.danger },
]);

/* ── 待办事项（织愈语义 Mock） ── */
const todos = ref([
  { text: '审核待审项目（' , count: 0, done: false, path: '/zhiyu/business/audit' },
  { text: '处理关怀提醒（', count: 0, done: false, path: '/zhiyu/business/care-reminders' },
  { text: '更新非遗内容库', count: 0, done: false, path: '/zhiyu/business/projects' },
  { text: '查看本月数据报表', count: 0, done: false, path: '/zhiyu/data-center/analytics' },
  { text: '回复传承人申请（2 条）', count: 0, done: false, path: '/zhiyu/business/inheritors' },
  { text: '配置小组活动推荐策略', count: 0, done: false, path: '/zhiyu/business/groups' },
]);

function toggleTodo(i: number) {
  todos.value[i].done = !todos.value[i].done;
}

function goTo(path: string) {
  router.push(path);
}

/* ── 访问来源环形图 ── */
const sourceChartRef = ref<EchartsUIType>();
const { renderEcharts: renderSourceChartRaw } = useEcharts(sourceChartRef);

function renderSourceChart() {
  const c = C.value;
  renderSourceChartRaw({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: {
      bottom: 0,
      left: 'center',
      textStyle: { color: c.muted, fontSize: 12 },
      itemWidth: 10,
      itemHeight: 10,
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '60%'],
        center: ['50%', '42%'],
        data: [
          { name: '学生端 App', value: 1280, itemStyle: { color: c.primary } },
          { name: '微信小程序', value: 860, itemStyle: { color: c.primaryLight } },
          { name: '管理端', value: 320, itemStyle: { color: c.accent } },
          { name: '其他', value: 180, itemStyle: { color: c.muted } },
        ],
        label: { color: c.text, fontSize: 12 },
        labelLine: { lineStyle: { color: c.border } },
        itemStyle: { borderColor: isDark.value ? '#1F1F1F' : '#fff', borderWidth: 2 },
      },
    ],
  });
}

/* ── 主题切换时重新渲染图表 ── */
watch(isDark, () => {
  nextTick(() => renderSourceChart());
});

onMounted(async () => {
  updateTime();
  timer = setInterval(updateTime, 1000);

  try {
    const data = await getDashboardStats();
    stats.value = {
      totalUsers: data.totalUsers ?? 0,
      totalWorks: data.totalWorks ?? 0,
      pendingProjects: data.pendingProjects ?? 0,
      pendingCareReminders: data.pendingCareReminders ?? 0,
    };

    // 更新待办数量
    todos.value[0]!.text = `审核待审项目（${stats.value.pendingProjects}）`;
    todos.value[1]!.text = `处理关怀提醒（${stats.value.pendingCareReminders}）`;
  } catch {
    // 拦截器已提示
  }

  renderSourceChart();
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="dashboard-page">
    <!-- ═══ 区块 1：欢迎卡片（全宽） ═══ -->
    <div class="welcome-card">
      <div class="welcome-left">
        <div class="welcome-avatar">织</div>
        <div class="welcome-text">
          <h2 class="welcome-greeting">{{ greeting }}，管理员，开始您一天的工作吧！</h2>
          <p class="welcome-sub">今日晴，20°C - 32°C！{{ todayStr }}</p>
        </div>
      </div>
      <div class="welcome-right">
        <div v-for="item in welcomeStats" :key="item.label" class="welcome-stat">
          <div class="welcome-stat-icon">{{ item.icon }}</div>
          <div class="welcome-stat-info">
            <div class="welcome-stat-value">
              {{ item.value }}<span v-if="item.suffix" class="welcome-stat-suffix">{{ item.suffix }}</span>
            </div>
            <div class="welcome-stat-label">{{ item.label }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══ 区块 2：项目网格 + 快捷导航（2:1） ═══ -->
    <div class="project-shortcut-row">
      <!-- 左：项目网格 -->
      <div class="project-section">
        <div class="section-header">
          <h3 class="section-title">非遗项目</h3>
          <span class="section-more" @click="goTo('/zhiyu/business/projects')">更多 →</span>
        </div>
        <div class="project-grid">
          <div
            v-for="(p, i) in projects"
            :key="p.name"
            class="project-card zy-slide-up zy-stagger zy-hover-lift"
            :style="{ '--stagger-i': i }"
            @click="goTo('/zhiyu/business/projects')"
          >
            <div class="project-icon" :style="{ background: C.primaryBg, color: C.primary }">{{ p.icon }}</div>
            <div class="project-info">
              <div class="project-name">{{ p.name }}</div>
              <div class="project-desc">{{ p.desc }}</div>
              <div class="project-meta">
                <span class="project-group">{{ p.group }}</span>
                <span class="project-date">{{ p.date }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：快捷导航 -->
      <div class="shortcut-section">
        <div class="section-header">
          <h3 class="section-title">快捷导航</h3>
        </div>
        <div class="shortcut-grid">
          <div
            v-for="(sc, i) in shortcuts"
            :key="sc.label"
            class="shortcut-item zy-slide-up zy-stagger zy-hover-lift"
            :style="{ '--stagger-i': i }"
            @click="goTo(sc.path)"
          >
            <div class="shortcut-icon" :style="{ background: C.primaryBg, color: C.primary }">{{ sc.icon }}</div>
            <div class="shortcut-label">{{ sc.label }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══ 区块 3：最新动态 + 待办事项（1:1） ═══ -->
    <div class="dynamic-todo-row">
      <!-- 左：最新动态 -->
      <div class="dynamic-section">
        <div class="section-header">
          <h3 class="section-title">最新动态</h3>
        </div>
        <div class="timeline">
          <div
            v-for="(act, i) in activities"
            :key="i"
            class="timeline-item zy-slide-up zy-stagger"
            :style="{ '--stagger-i': i }"
          >
            <div class="timeline-avatar" :style="{ background: act.color + '18', color: act.color }">{{ act.avatar }}</div>
            <div class="timeline-content">
              <div class="timeline-text">
                <span class="timeline-name">{{ act.name }}</span>
                <span class="timeline-action">{{ act.action }}</span>
                <span class="timeline-target">{{ act.target }}</span>
              </div>
              <div class="timeline-time">{{ act.time }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：待办事项 -->
      <div class="todo-section">
        <div class="section-header">
          <h3 class="section-title">待办事项</h3>
        </div>
        <div class="todo-list">
          <div
            v-for="(todo, i) in todos"
            :key="i"
            class="todo-item"
            :class="{ 'todo-done': todo.done }"
            @click="toggleTodo(i)"
          >
            <div class="todo-checkbox" :class="{ checked: todo.done }">
              <span v-if="todo.done" class="todo-check-icon">✓</span>
            </div>
            <span class="todo-text">{{ todo.text }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══ 区块 4：访问来源（环形图） ═══ -->
    <div class="source-card">
      <div class="section-header">
        <h3 class="section-title">访问来源</h3>
      </div>
      <EchartsUI ref="sourceChartRef" height="280px" />
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

/* ── 通用区块标题 ── */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
}
.section-more {
  font-size: 13px;
  color: var(--zy-admin-primary, #4f46e5);
  cursor: pointer;
  transition: opacity var(--zy-duration-fast, 150ms);
}
.section-more:hover {
  opacity: 0.7;
}

/* ═══ 区块 1：欢迎卡片 ═══ */
.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 32px;
  background: var(--zy-admin-card-bg, #fff);
  border-radius: var(--zy-radius-lg, 12px);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
}
.welcome-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.welcome-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--zy-admin-primary, #4f46e5);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  flex-shrink: 0;
}
.welcome-greeting {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
}
.welcome-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--zy-admin-muted, #9ca3af);
}
.welcome-right {
  display: flex;
  gap: 32px;
}
.welcome-stat {
  display: flex;
  align-items: center;
  gap: 8px;
}
.welcome-stat-icon {
  font-size: 20px;
}
.welcome-stat-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.welcome-stat-suffix {
  font-size: 13px;
  font-weight: 400;
  color: var(--zy-admin-muted, #9ca3af);
}
.welcome-stat-label {
  font-size: 12px;
  color: var(--zy-admin-muted, #9ca3af);
}

/* ═══ 区块 2：项目 + 快捷导航 ═══ */
.project-shortcut-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}
.project-section,
.shortcut-section {
  background: var(--zy-admin-card-bg, #fff);
  border-radius: var(--zy-radius-lg, 12px);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
  padding: 16px 20px;
}

/* 项目网格 */
.project-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.project-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-radius: var(--zy-radius-md, 8px);
  border: 1px solid var(--zy-admin-border, #e5e7eb);
  cursor: pointer;
  transition: border-color var(--zy-duration-fast, 150ms), box-shadow var(--zy-duration-fast, 150ms);
}
.project-card:hover {
  border-color: var(--zy-admin-primary, #4f46e5);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
}
.project-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--zy-radius-md, 8px);
  font-size: 18px;
  flex-shrink: 0;
}
.project-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
}
.project-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--zy-admin-muted, #9ca3af);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.project-meta {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--zy-admin-muted, #9ca3af);
}

/* 快捷导航 */
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
  border-radius: var(--zy-radius-md, 8px);
  cursor: pointer;
  transition: background var(--zy-duration-fast, 150ms);
}
.shortcut-item:hover {
  background: var(--zy-admin-primary-bg, #eef2ff);
}
.shortcut-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--zy-radius-md, 8px);
  font-size: 20px;
  transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}
.shortcut-item:hover .shortcut-icon {
  transform: scale(1.1);
}
.shortcut-label {
  font-size: 12px;
  color: var(--zy-admin-text, #4b5563);
  white-space: nowrap;
}

/* ═══ 区块 3：动态 + 待办 ═══ */
.dynamic-todo-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.dynamic-section,
.todo-section {
  background: var(--zy-admin-card-bg, #fff);
  border-radius: var(--zy-radius-lg, 12px);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
  padding: 16px 20px;
}

/* 时间线 */
.timeline {
  display: flex;
  flex-direction: column;
}
.timeline-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
  position: relative;
}
.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 13px;
  top: 36px;
  bottom: -4px;
  width: 2px;
  background: var(--zy-admin-border, #e5e7eb);
}
.timeline-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  z-index: 1;
}
.timeline-content {
  flex: 1;
  min-width: 0;
}
.timeline-text {
  font-size: 13px;
  color: var(--zy-admin-text, #4b5563);
  line-height: 1.5;
}
.timeline-name {
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
}
.timeline-action {
  margin: 0 2px;
}
.timeline-target {
  color: var(--zy-admin-primary, #4f46e5);
  font-weight: 500;
}
.timeline-time {
  font-size: 12px;
  color: var(--zy-admin-muted, #9ca3af);
  margin-top: 2px;
}

/* 待办事项 */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 4px;
  cursor: pointer;
  border-bottom: 1px solid var(--zy-admin-border, #e5e7eb);
  transition: background var(--zy-duration-fast, 150ms);
}
.todo-item:last-child {
  border-bottom: none;
}
.todo-item:hover {
  background: var(--zy-admin-primary-bg, #eef2ff);
  border-radius: var(--zy-radius-sm, 4px);
}
.todo-checkbox {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 2px solid var(--zy-admin-border, #e5e7eb);
  border-radius: 4px;
  flex-shrink: 0;
  transition: all var(--zy-duration-fast, 150ms);
}
.todo-checkbox.checked {
  background: var(--zy-admin-primary, #4f46e5);
  border-color: var(--zy-admin-primary, #4f46e5);
}
.todo-check-icon {
  font-size: 12px;
  color: #fff;
  font-weight: 700;
}
.todo-text {
  font-size: 13px;
  color: var(--zy-admin-text, #4b5563);
  transition: all var(--zy-duration-fast, 150ms);
}
.todo-done .todo-text {
  text-decoration: line-through;
  color: var(--zy-admin-muted, #9ca3af);
}

/* ═══ 区块 4：访问来源 ═══ */
.source-card {
  background: var(--zy-admin-card-bg, #fff);
  border-radius: var(--zy-radius-lg, 12px);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
  padding: 16px 20px;
}

/* ── 响应式 ── */
@media (max-width: 1280px) {
  .welcome-card {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }
  .welcome-right {
    width: 100%;
    justify-content: space-between;
    gap: 16px;
  }
  .project-shortcut-row {
    grid-template-columns: 1fr;
  }
  .project-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .dynamic-todo-row {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .project-grid {
    grid-template-columns: 1fr;
  }
  .shortcut-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
