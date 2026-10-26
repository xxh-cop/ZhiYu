<script setup lang="ts">
import {onMounted,ref} from 'vue';
import {requestClient} from '#/api/request';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
const rows=ref<any[]>([]),total=ref(0),page=ref(1),loading=ref(false),error=ref('');
async function load(){loading.value=true;error.value='';try{const d=await requestClient.get<any>('/admin/operation-logs',{params:{page:page.value}});rows.value=d.records;total.value=d.total;}catch(e:any){error.value=e.message||'加载失败';}finally{loading.value=false;}}
onMounted(load);
</script>
<template><div class="zy-business-page flex flex-col gap-4"><BusinessPageHeader title="操作记录" description="记录校园审核与关怀跟进的操作人、时间和对象，不记录敏感正文。" :loading="loading" @refresh="load"/><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-card shadow="never"><el-table v-loading="loading" :data="rows" empty-text="暂无操作记录"><el-table-column prop="create_time" label="时间" min-width="180"/><el-table-column prop="actor" label="操作人编号" min-width="170"/><el-table-column prop="action" label="操作" min-width="180"/><el-table-column prop="target" label="对象编号" min-width="170"/></el-table><el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="total,prev,pager,next" class="mt-4" @current-change="load"/></el-card><p class="text-sm text-gray-500">本系统不能替代专业心理咨询</p></div></template>
