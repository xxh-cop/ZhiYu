<script setup lang="ts">
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import { onMounted, ref } from 'vue';

import { ElMessage } from 'element-plus';
import { requestClient } from '#/api/request';

import { getCareReminders } from '#/api/zhiyu/admin';
import DataState from '#/components/data-state/DataState.vue';

/** 状态筛选 */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待处理' },
  { value: 1, label: '已跟进' },
];
const filterStatus = ref<number | string>('');

/** 表格数据 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const filtersVisible = ref(true);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 16);
}

/** 风险标签 */
function riskLabel(level: number) {
  return level === 2 ? '高危' : '一般';
}
function riskTagType(level: number) {
  return level === 2 ? 'danger' : 'info';
}

/** 状态标签 */
function statusLabel(status: number) {
  return status === 0 ? '待处理' : '已跟进';
}
function statusTagType(status: number) {
  return status === 0 ? 'warning' : 'success';
}

/** 拉取列表 */
let listRequest = 0;
async function fetchList() {
  const requestId = ++listRequest;
  loading.value = true;
  errorMsg.value = '';
  try {
    const params: any = { page: currentPage.value };
    if (filterStatus.value !== '') {
      params.status = filterStatus.value;
    }
    const data = await getCareReminders(params);
    if (requestId !== listRequest) return;
    tableData.value = data.records || [];
    total.value = data.total || 0;
  } catch (e: any) {
    if (requestId !== listRequest) return;
    errorMsg.value = (e && e.message) || '加载失败';
    tableData.value = [];
    total.value = 0;
  } finally {
    if (requestId === listRequest) loading.value = false;
  }
}

/** 状态变化 */
function handleStatusChange() {
  currentPage.value = 1;
  fetchList();
}

/** 翻页 */
function handlePageChange(page: number) {
  currentPage.value = page;
  fetchList();
}

const detail=ref<any>(null),drawer=ref(false),busy=ref(false),detailError=ref('');
const actionLabels:Record<string,string>={CLAIM:'领取任务',REVIEWED:'已查看并评估',CONTACTED:'记录已联系',REFERRED:'记录已转介',CLOSE:'完成任务',REOPEN:'重新打开'};
async function handleView(row:any){drawer.value=true;detail.value=null;detailError.value='';try{detail.value=await requestClient.get('/admin/care-reminders/'+row.id);}catch(e:any){detailError.value=e.message||'详情加载失败';}}
async function follow(action:string){if(!detail.value||busy.value)return;busy.value=true;try{const id=detail.value.id;await requestClient.post('/admin/care-reminders/'+id+'/followups',{action});ElMessage.success('跟进已保存');detail.value=await requestClient.get('/admin/care-reminders/'+id);await fetchList();}catch(e:any){ElMessage.error(e.message||'操作失败');}finally{busy.value=false;}}
async function claim(row:any){if(busy.value)return;busy.value=true;try{await requestClient.post('/admin/care-reminders/'+row.id+'/followups',{action:'CLAIM'});ElMessage.success('任务已领取');await fetchList();await handleView(row);}catch(e:any){ElMessage.error(e.message||'领取失败');}finally{busy.value=false;}}


onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader title="关怀提醒" description="在授权范围内查看脱敏摘要，及时跟进关怀。" :loading="loading" @refresh="fetchList" />
    <p class="m-0 text-sm text-gray-500 leading-relaxed">
      以下为系统自动生成的脱敏汇总，供授权角色查看。原始对话内容已加密存储，不在此展示。
    </p>

    <el-card shadow="never">
      <div class="zy-filter-heading"><span>筛选条件</span><el-button link type="primary" :aria-expanded="filtersVisible" @click="filtersVisible = !filtersVisible">{{ filtersVisible ? '收起筛选' : '展开筛选' }}</el-button></div>
      <div v-show="filtersVisible" class="zy-filter-content flex flex-wrap items-center gap-3">
        <el-select
          v-model="filterStatus"
          placeholder="状态"
          clearable
          style="width: 140px"
          @change="handleStatusChange"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
      >
        <template #empty>
          <DataState
            :loading="loading"
            :error="errorMsg"
            empty-text="暂无关怀提醒"
            @retry="fetchList"
          />
        </template>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" size="small">
              {{ riskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="脱敏摘要" min-width="300">
          <template #default="{ row }">
            {{ row.summary || (row.claimed ? '-' : '领取后可见脱敏摘要') }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button v-if="!row.claimed" type="primary" link size="small" :loading="busy" @click="claim(row)">
              领取
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        class="mt-4 flex justify-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </el-card>
    <el-drawer v-model="drawer" title="关怀跟进" size="min(560px, 100vw)">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
      <template v-if="detail"><el-alert v-if="detail.claimed" :title="Number(detail.can_contact)===1?'用户已授权联系；记录联系前仍会校验授权':'用户未授权联系，请尊重其选择'" :type="Number(detail.can_contact)===1?'success':'warning'" :closable="false"/>
      <p v-if="detail.summary" class="my-4 leading-relaxed">{{detail.summary}}</p>
      <p v-else class="my-4 leading-relaxed text-gray-500">领取后可见脱敏摘要与关联信息。</p>
      <p>任务状态：{{detail.state==='CLOSED'?'已完成':detail.claimed?'已领取':'待领取'}}</p>
      <div class="my-4 flex flex-wrap gap-2"><el-button v-if="!detail.claimed" type="primary" :loading="busy" @click="follow('CLAIM')">领取任务</el-button>
      <template v-else-if="detail.state!=='CLOSED'"><el-button v-for="a in ['REVIEWED','CONTACTED','REFERRED','CLOSE']" :key="a" :disabled="busy||(a==='CONTACTED'&&Number(detail.can_contact)!==1)" @click="follow(a)">{{actionLabels[a]}}</el-button></template>
      <el-button v-else :loading="busy" @click="follow('REOPEN')">重新打开</el-button></div>
      <el-timeline v-if="detail.events && detail.events.length" class="mt-6"><el-timeline-item v-for="(e,i) in detail.events" :key="i" :timestamp="formatTime(e.create_time)">{{actionLabels[e.action]||e.action}}</el-timeline-item></el-timeline>
      <p class="text-sm text-gray-500">只记录处理动作，不收集可识别个人身份的跟进正文。本系统不能替代专业心理咨询。</p></template>
    </el-drawer>
  </div>
</template>
