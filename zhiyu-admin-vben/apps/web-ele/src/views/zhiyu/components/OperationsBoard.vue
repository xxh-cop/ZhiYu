<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { CountTo } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { ElButton, ElSkeleton } from 'element-plus';

import { requestClient } from '#/api/request';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';

import OperationsChart from './OperationsChart.vue';
import TherapyFunnelCard from './TherapyFunnelCard.vue';

const props = defineProps<{ analytics?: boolean }>();
const router = useRouter();
const user = useUserStore();

type Item = { name: string; value: number };
type Overview = {
  bookingStates: Item[];
  categories: Item[];
  daily: Array<{
    date: string;
    finished: number;
    sessions: number;
    users: number;
    works: number;
  }>;
  generatedAt: string;
  programKinds: Item[];
  projectStates: Item[];
  sessionStates: Item[];
  totals: Record<string, number>;
};

const data = ref<null | Overview>(null);
const days = ref(7);
const loading = ref(false);
const error = ref('');
let generation = 0;

const isAdmin = computed(() => user.userInfo?.roles?.includes('ROLE_3'));
const nickname = computed(() => user.userInfo?.realName || '工作伙伴');
const reduceMotion =
  typeof window === 'undefined' ||
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;

const cards = [
  {
    caption: '全部已注册用户',
    icon: '◎',
    key: 'users',
    label: '平台用户',
    path: '/zhiyu/business/users',
  },
  {
    caption: '累计开始的创作',
    icon: '◷',
    key: 'sessions',
    label: '创作会话',
    path: '/zhiyu/data-center/analytics',
  },
  {
    caption: '全部作品记录',
    icon: '▧',
    key: 'works',
    label: '创作作品',
    path: '/zhiyu/business/works',
  },
  {
    caption: '当前可体验内容',
    icon: '⌘',
    key: 'projects',
    label: '已上架项目',
    path: '/zhiyu/business/audit',
  },
];

const programKindQuery: Record<string, string> = {
  一对一: 'GUIDANCE',
  一对一指导: 'GUIDANCE',
  个别指导: 'GUIDANCE',
  系列课程: 'COURSE',
  课程: 'COURSE',
  工作坊: 'WORKSHOP',
  主题工作坊: 'WORKSHOP',
};

const projectStateQuery: Record<string, string> = {
  下架: '2',
  已下架: '2',
  上架: '1',
  已上架: '1',
  待审: '0',
  待审核: '0',
};

/** 错峰入场：动画类在 animations.scss，这里只提供序号 */
function stagger(index: number) {
  return { '--stagger-i': String(index) };
}

async function load() {
  const turn = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await requestClient.get<Overview>('/admin/dashboard/operations', {
      params: { days: days.value },
    });
    if (turn === generation) data.value = result;
  } catch {
    if (turn === generation) {
      error.value = '运营数据暂未读取成功，请确认后端与数据库迁移后重试。';
    }
  } finally {
    if (turn === generation) loading.value = false;
  }
}

function range(n: number) {
  days.value = n;
  load();
}

onMounted(() => {
  if (isAdmin.value) load();
});

const date = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
  timeZone: 'Asia/Shanghai',
}).format(new Date());
</script>

<template>
  <main class="operations">
    <header class="op-top zy-glass zy-sheen zy-slide-up">
      <div>
        <p class="overline">ZHIYU / {{ analytics ? 'INSIGHTS' : 'WORKSPACE' }}</p>
        <h1>{{ analytics ? '每一份参与，都清晰可见。' : `你好，${nickname}` }}</h1>
        <p class="lede">
          {{
            analytics
              ? '从内容供给到创作参与，了解平台正在发生什么。'
              : '把日常事务安排好，让创作与关怀慢慢发生。'
          }}
        </p>
      </div>
      <div class="top-right">
        <span class="date-chip zy-glass-chip">{{ date }}</span>
        <ElButton v-if="isAdmin" :loading="loading" @click="load">↻ 刷新数据</ElButton>
      </div>
    </header>

    <template v-if="isAdmin">
      <DataErrorBanner v-if="error" :error="error" :retrying="loading" @retry="load" />

      <section
        v-if="!analytics"
        class="task-panel priority-panel zy-glass zy-slide-up zy-stagger"
        :style="stagger(1)"
      >
        <p class="overline">NEEDS ATTENTION</p>
        <h3>今天，优先处理</h3>
        <button type="button" @click="router.push('/zhiyu/business/audit')">
          <span><b>内容审核</b><small>让新的手作与大家见面</small></span>
          <strong class="zy-num">{{ data?.totals.pending ?? '—' }}</strong>
          <span>→</span>
        </button>
        <button type="button" @click="router.push('/zhiyu/business/care-reminders')">
          <span><b>关怀提醒</b><small>仅查看授权的脱敏信息</small></span>
          <strong class="zy-num">{{ data?.totals.care ?? '—' }}</strong>
          <span>→</span>
        </button>
        <button type="button" @click="router.push('/zhiyu/programs')">
          <span><b>预约处理</b><small>确认申请，安排下一次相见</small></span>
          <span>→</span>
        </button>
      </section>

      <section
        v-if="!analytics"
        class="welcome-strip zy-glass zy-sheen zy-slide-up zy-stagger"
        :style="stagger(2)"
      >
        <div>
          <span class="strip-label">让每一次开始，都有人回应</span>
          <h2>今天，从一件值得做的小事开始。</h2>
          <p>查看待办，整理内容，为下一次手作留好位置。</p>
          <button type="button" @click="router.push('/zhiyu/programs')">
            安排活动与预约 <span>↗</span>
          </button>
        </div>
        <img alt="交织的纸带与叶形纹样" src="/weave-scene.svg" />
      </section>

      <section class="op-metrics">
        <button
          v-for="(card, i) in cards"
          :key="card.key"
          :style="stagger(i)"
          class="zy-glass zy-elevate zy-slide-up zy-stagger"
          type="button"
          @click="router.push(card.path)"
        >
          <div class="metric-top">
            <span>{{ card.label }}</span>
            <span class="metric-icon" :class="`tone-${i}`">{{ card.icon }}</span>
          </div>
          <strong class="metric-value zy-num">
            <CountTo
              v-if="data"
              :delay="180 + i * 110"
              :disabled="reduceMotion"
              :duration="1100"
              :end-val="Number(data.totals[card.key]) || 0"
              separator=","
            />
            <template v-else>—</template>
          </strong>
          <div class="metric-bottom">
            <span>{{ card.caption }}</span>
            <span>↗</span>
          </div>
        </button>
      </section>

      <div class="section-heading">
        <div>
          <h2>{{ analytics ? '参与趋势与内容结构' : '运营概览' }}</h2>
          <span>趋势按北京时间自然日统计 · 分布为当前全部记录</span>
        </div>
        <div class="range zy-glass-chip">
          <button
            v-for="n in [7, 30]"
            :key="n"
            :aria-pressed="days === n"
            :class="{ selected: days === n }"
            type="button"
            @click="range(n)"
          >
            近 {{ n }} 天
          </button>
        </div>
      </div>

      <TherapyFunnelCard v-if="!analytics" :days="days" />
      <ElSkeleton v-if="loading && !data" :rows="5" animated />
      <template v-else-if="data">
        <div class="charts-top">
          <OperationsChart
            :kind="'line'"
            :rows="data?.daily || []"
            :subtitle="`近 ${days} 天 · 可点击图例筛选；各曲线按各自发生日期统计`"
            :empty-hint="`近 ${days} 天还没有创作记录`"
            empty-text="暂无创作趋势"
            title="创作与参与趋势"
          />
          <OperationsChart
            :kind="'pie'"
            :rows="data?.sessionStates || []"
            empty-hint="完成一次创作后会在这里显示状态分布"
            empty-text="暂无创作会话"
            subtitle="全部创作会话状态分布"
            title="创作进行到哪一步"
          />
        </div>
        <div class="charts-bottom" :class="{ overview: !analytics }">
          <OperationsChart
            :rows="data?.categories || []"
            drilldown
            subtitle="已上架项目 · 数量前 12 个分类"
            title="手作内容分布"
            empty-text="暂无已上架项目"
            empty-hint="上架手作内容后会在这里显示分类分布"
            :kind="'bar'"
            @select="(row) => router.push({ path: '/zhiyu/business/audit', query: { status: '1', category: row.name } })"
          />
          <OperationsChart
            :rows="data?.projectStates || []"
            drilldown
            subtitle="项目审核与发布状态"
            title="内容上架进度"
            empty-text="暂无审核记录"
            empty-hint="提交项目审核后会在这里显示进度"
            :kind="'pie'"
            @select="(row) => router.push({ path: '/zhiyu/business/audit', query: { status: projectStateQuery[row.name] || '0' } })"
          />
          <OperationsChart
            v-if="analytics"
            :rows="data?.bookingStates || []"
            subtitle="全部报名记录 · 含已取消"
            title="预约与签到"
            empty-text="暂无报名记录"
            empty-hint="产生报名后会在这里显示签到状态"
            :kind="'bar'"
          />
        </div>
        <div v-if="analytics" class="charts-top">
          <OperationsChart
            :rows="data?.programKinds || []"
            drilldown
            subtitle="当前已发布活动 · 按类型"
            title="活动内容供给"
            empty-text="暂无已发布活动"
            empty-hint="发布活动后会在这里显示类型分布"
            :kind="'bar'"
            @select="(row) => router.push({ path: '/zhiyu/programs', query: { kind: programKindQuery[row.name] || row.name } })"
          />
          <section class="method-panel zy-glass">
            <p class="overline">ABOUT THE DATA</p>
            <h3>真实记录，清晰边界</h3>
            <p>
              曲线中的“开始”和“完成”分别按开始日、完成日计数，不能直接相除得到同一批用户的完成率。
            </p>
            <p>零值意味着该日期没有记录；空图意味着尚无相关数据。逻辑删除的记录不计入统计。</p>
            <p>参与次数与作品数量不代表心理改善或临床疗效。</p>
          </section>
        </div>
      </template>

      <p class="updated">
        {{
          data
            ? '数据更新于 ' +
              new Date(data.generatedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
            : '等待真实数据'
        }}
        · 本系统不能替代专业心理咨询
      </p>
    </template>
    <section v-else class="method-panel zy-glass">
      <h2>欢迎回到织愈工作空间</h2>
      <p>在你的授权范围内维护内容、安排课程与活动。</p>
      <ElButton type="primary" @click="router.push('/zhiyu/programs')">
        进入活动与预约
      </ElButton>
      <p>本系统不能替代专业心理咨询</p>
    </section>
  </main>
</template>

<style scoped>
/* 页面自身不再铺底色，环境光由 theme.scss 挂在布局内容区，玻璃才有层次 */
.operations {
  --op-text: var(--el-text-color-primary);
  --op-muted: var(--el-text-color-regular);
  padding: 26px 30px 32px;
  min-height: 100%;
  color: var(--op-text);
}

.op-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  margin-bottom: 22px;
}

.overline {
  margin: 0 0 12px;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 2.8px;
  color: var(--el-text-color-regular);
}

.op-top h1 {
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -0.8px;
}

.lede {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--op-muted);
}

.top-right {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--op-muted);
}

.date-chip {
  padding: 8px 14px;
  white-space: nowrap;
}

.priority-panel {
  display: grid;
  grid-template-columns: 1fr repeat(3, minmax(0, 1fr));
  align-items: center;
  gap: 24px;
  margin-bottom: 22px;
  padding: 24px 28px;
}

.priority-panel > .overline {
  display: none;
}

.priority-panel h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.priority-panel button {
  border: 0;
  padding: 12px 0;
  background: transparent;
}

.welcome-strip {
  position: relative;
  display: flex;
  justify-content: space-between;
  overflow: hidden;
  padding: 30px 36px;
  margin-bottom: 22px;
  color: var(--op-text);
  background-image:
    radial-gradient(38rem 20rem at 4% 0%, hsl(var(--primary) / 14%), transparent 66%),
    linear-gradient(120deg, transparent, hsl(var(--primary) / 7%));
}

.welcome-strip > div {
  z-index: 1;
}

.strip-label {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--el-text-color-regular);
}

.welcome-strip h2 {
  margin: 14px 0 10px;
  font-size: 25px;
  font-weight: 550;
}

.welcome-strip p {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.welcome-strip button {
  display: flex;
  gap: 40px;
  margin-top: 20px;
  padding: 12px 18px;
  font-size: 12px;
  color: var(--el-color-white);
  background: var(--el-color-primary);
  border: 0;
  border-radius: calc(var(--radius) * 1.8);
  transition: box-shadow var(--zy-duration-base) var(--zy-ease-out);
}

.welcome-strip button:hover {
  box-shadow: 0 12px 26px -14px var(--zy-accent-glow);
}

.welcome-strip img {
  position: absolute;
  top: -40px;
  right: 0;
  width: 420px;
  height: 280px;
  opacity: 0.9;
  pointer-events: none;
}

.op-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin: 22px 0 28px;
}

.op-metrics > button {
  padding: 20px 24px;
  text-align: left;
}

.metric-top,
.metric-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--op-muted);
  font-size: 12px;
}

.metric-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  font-size: 21px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  background: color-mix(in oklab, currentColor 13%, transparent);
  border-radius: calc(var(--radius) * 1.4);
  box-shadow: inset 0 0 0 1px color-mix(in oklab, currentColor 16%, transparent);
}

.tone-1 {
  color: var(--el-color-warning);
}

.tone-2 {
  color: var(--el-color-info);
}

.tone-3 {
  color: var(--el-color-success);
}

.metric-value {
  display: block;
  margin: 10px 0 12px;
  font-size: 38px;
  font-weight: 600;
}

.metric-bottom {
  font-size: 11px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin: 28px 0 18px;
}

.section-heading h2 {
  margin-bottom: 7px;
  font-size: 19px;
  font-weight: 600;
}

.section-heading span {
  font-size: 11px;
  color: var(--op-muted);
}

.range {
  display: flex;
  gap: 4px;
  padding: 4px;
}

.range button {
  padding: 8px 14px;
  font-size: 12px;
  white-space: nowrap;
  color: var(--op-text);
  background: transparent;
  border: 0;
  border-radius: calc(var(--radius) * 1.2);
}

.range .selected {
  color: var(--el-color-white);
  background: var(--el-color-primary);
  box-shadow: 0 8px 18px -10px var(--zy-accent-glow);
}

.charts-top {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(0, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.charts-bottom {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.charts-bottom.overview {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.task-panel,
.method-panel {
  min-width: 0;
}

.method-panel {
  padding: 24px;
}

.task-panel h3,
.method-panel h3 {
  margin: 12px 0 16px;
  font-size: 18px;
  font-weight: 600;
}

.task-panel button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 0;
  font-size: 12px;
  text-align: left;
  border-bottom: 1px solid var(--zy-glass-border);
}

.task-panel b {
  font-weight: 550;
}

.task-panel small {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--op-muted);
}

.task-panel strong {
  font-size: 26px;
  font-weight: 550;
}

.method-panel p {
  margin: 18px 0;
  font-size: 13px;
  line-height: 2;
  color: var(--op-muted);
}

.updated {
  margin-top: 28px;
  font-size: 12px;
  text-align: center;
  color: var(--op-muted);
}

button:focus-visible {
  outline: 3px solid var(--el-color-primary);
  outline-offset: 3px;
}

@media (max-width: 1100px) {
  .charts-bottom {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .welcome-strip img {
    opacity: 0.3;
  }

  .date-chip {
    display: none;
  }
}

@media (max-width: 1000px) {
  .priority-panel {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .priority-panel h3 {
    grid-column: 1 / -1;
    margin: 0;
  }
}

@media (max-width: 720px) {
  .operations {
    padding: 18px 14px 24px;
  }

  .op-top,
  .welcome-strip {
    padding: 22px 20px;
  }

  .op-top {
    align-items: flex-start;
  }

  .op-top h1 {
    font-size: 24px;
  }

  .welcome-strip h2 {
    font-size: 20px;
  }

  .op-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .op-metrics > button {
    padding: 18px;
  }

  .metric-value {
    font-size: 30px;
  }

  .charts-top,
  .charts-bottom {
    grid-template-columns: 1fr;
  }

  .section-heading {
    align-items: flex-start;
  }

  .section-heading span {
    display: none;
  }

  .op-top .lede {
    max-width: 230px;
  }

  .top-right {
    padding-top: 24px;
  }

  .priority-panel {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .priority-panel button {
    border-bottom: 1px solid var(--zy-glass-border);
  }
}

@media (prefers-reduced-motion: reduce) {
  .op-metrics > button,
  .welcome-strip button {
    transition: none;
  }

  .op-metrics > button:hover {
    transform: none;
  }
}
</style>
