<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { requestClient } from '#/api/request';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';

type Setting = { key: string; revision: number; value: Record<string, any> };

const rows = ref<Setting[]>([]);
const loading = ref(false);
const saving = ref('');
const error = ref('');

/** 配置分组元信息：作用说明 + 后端默认值（来源 RuntimeSettings.defaults，勿凭页面习惯改） */
const meta: Record<string, { effect: string; title: string }> = {
  recommendation: {
    title: '推荐规则',
    effect: '控制学生端「为你推荐」的排序与条数。默认：每次 3 条，标签权重 0.6，易上手权重 0.4，仅低风险材料。',
  },
  model: {
    title: '模型参数',
    effect: '影响已接入大模型的表达。默认：temperature 0.7，单次最多 800 tokens。服务地址与密钥由服务端环境变量管理，不在此配置。',
  },
  prompts: {
    title: '提示模板扩展',
    effect: '追加到既有提示模板末尾，不替换安全提示。默认为空。请勿填入密钥或用户私人信息。',
  },
};

async function load() {
  loading.value = true;
  error.value = '';
  try {
    rows.value = await requestClient.get('/admin/config/runtime');
  } catch {
    error.value = '配置加载失败，请重试';
  } finally {
    loading.value = false;
  }
}

async function save(row: Setting) {
  try {
    await ElMessageBox.confirm(
      '保存后对新请求即时生效，已进行中的对话不会重放。',
      '更新' + (meta[row.key]?.title ?? row.key),
      { confirmButtonText: '确认保存', type: 'warning' },
    );
    saving.value = row.key;
    await requestClient.put('/admin/config/runtime', row);
    ElMessage.success('配置已更新，新请求即刻生效');
    await load();
  } catch {
    // 用户取消或请求失败（拦截器已提示）
  } finally {
    saving.value = '';
  }
}

onMounted(load);
</script>

<template>
  <div class="zy-business-page runtime-page">
    <BusinessPageHeader
      eyebrow="SERVICE SETTINGS"
      title="推荐与陪伴配置"
      description="调整推荐排序与表达方式。安全边界始终生效，密钥只保存在服务端。"
    >
      <el-button :loading="loading" :disabled="!!saving" @click="load">重新读取</el-button>
    </BusinessPageHeader>
    <el-alert
      title="所有修改保存后对新请求即时生效；进行中的对话与推荐不会重放。"
      type="info"
      :closable="false"
      show-icon
    />
    <DataErrorBanner v-if="error && rows.length" :error="error" :retrying="loading" @retry="load" />
    <DataState
      v-if="!rows.length"
      :loading="loading"
      :error="error"
      empty-text="暂无运行配置"
      @retry="load"
    />
    <div v-else class="config-grid">
      <el-card v-for="r in rows" :key="r.key" shadow="never">
        <template #header>
          <div class="card-heading">
            <h2>{{ meta[r.key]?.title ?? r.key }}</h2>
            <span>修订 {{ r.revision }}</span>
          </div>
        </template>
        <p class="card-effect">{{ meta[r.key]?.effect }}</p>
        <el-form label-position="top" :disabled="!!saving || loading">
          <template v-if="r.key === 'recommendation'">
            <el-form-item label="每次推荐数量（默认 3）">
              <el-input-number v-model="r.value.count" :min="1" :max="6" />
            </el-form-item>
            <el-form-item label="偏好标签权重（默认 0.6）">
              <el-input-number v-model="r.value.tagWeight" :min="0" :max="2" :step="0.1" />
            </el-form-item>
            <el-form-item label="易上手权重（默认 0.4）">
              <el-input-number v-model="r.value.easeWeight" :min="0" :max="2" :step="0.1" />
            </el-form-item>
            <el-form-item label="材料安全范围（默认仅低风险）">
              <el-select v-model="r.value.maxSafety">
                <el-option value="LOW" label="仅低风险材料" />
                <el-option value="GUIDED" label="低风险及需带领者指导的项目" />
              </el-select>
            </el-form-item>
            <p>未标注及高风险项目不会进入自动推荐；请在项目管理中维护安全说明。</p>
          </template>
          <template v-else-if="r.key === 'model'">
            <el-form-item label="表达变化程度 temperature（默认 0.7）">
              <el-input-number v-model="r.value.temperature" :min="0" :max="1" :step="0.1" />
            </el-form-item>
            <el-form-item label="单次生成长度上限 tokens（默认 800）">
              <el-input-number v-model="r.value.maxTokens" :min="128" :max="2048" :step="128" />
            </el-form-item>
            <p>仅影响已接入的大模型。服务地址、模型名称和密钥由服务端环境配置管理。</p>
          </template>
          <template v-else>
            <el-form-item
              v-for="(label, key) in { companion: '陪伴对话', recommendation: '推荐说明', work: '作品寄语' }"
              :key="key"
              :label="label"
            >
              <el-input v-model="r.value[key]" type="textarea" :rows="3" maxlength="2000" show-word-limit />
            </el-form-item>
          </template>
          <el-button type="primary" :loading="saving === r.key" @click="save(r)">
            保存{{ meta[r.key]?.title ?? '' }}
          </el-button>
        </el-form>
      </el-card>
    </div>
    <p class="business-note">本系统不能替代专业心理咨询</p>
  </div>
</template>

<style scoped>
.runtime-page {
  padding: 0;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.config-grid > :last-child {
  grid-column: 1 / -1;
}

.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-heading h2 {
  font-size: 18px;
  font-weight: 600;
}

.card-heading span,
.card-effect,
.config-grid p {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}

.card-effect {
  margin: 0 0 16px;
}

@media (max-width: 700px) {
  .config-grid {
    grid-template-columns: 1fr;
  }
}
</style>
