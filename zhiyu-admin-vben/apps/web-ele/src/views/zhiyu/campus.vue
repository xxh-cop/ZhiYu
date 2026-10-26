<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { requestClient } from '#/api/request';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
const rows=ref<any[]>([]),total=ref(0),page=ref(1),loading=ref(false),saving=ref(false),error=ref('');
const labels:Record<string,string>={PENDING:'待审核',APPROVED:'已通过',REJECTED:'未通过'};
async function load(){loading.value=true;error.value='';try{const d=await requestClient.get<any>('/admin/campus-verifications',{params:{page:page.value}});rows.value=d.records;total.value=d.total;}catch(e:any){error.value=e.message||'加载失败';}finally{loading.value=false;}}
async function review(row:any,decision:string){
 try{const {value}=await ElMessageBox.prompt(`请确认已通过学校认可的方式核实身份，再${decision==='APPROVED'?'通过':'驳回'}申请。不要填写证件号等敏感内容。`,'校园身份审核',{inputType:'textarea',inputValidator:(v)=>!!v?.trim()&&v.length<=300||'请填写 1–300 字审核说明',confirmButtonText:'确认审核'});
 saving.value=true;await requestClient.post(`/admin/campus-verifications/${row.id}/review`,{decision,reason:value});ElMessage.success('审核已保存');await load();
 }catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message||'审核失败');}finally{saving.value=false;}
}
onMounted(load);
</script>
<template><div class="zy-business-page flex flex-col gap-4">
 <BusinessPageHeader title="校园身份审核" description="核实校园身份，保留处理说明与操作记录。审核不改变账号权限。" :loading="loading" @refresh="load" />
 <el-alert title="身份资料仅供审核使用。请通过学校认可的渠道核实，不要下载或另行传播。" type="info" :closable="false" />
 <el-alert v-if="error" :title="error" type="error" :closable="false" />
 <el-card shadow="never"><el-table v-loading="loading" :data="rows" empty-text="暂无身份申请">
 <el-table-column label="申请人" min-width="100"><template #default="{row}">{{row.identity?.realName}}</template></el-table-column>
 <el-table-column label="学号" min-width="150"><template #default="{row}">{{row.identity?.studentNumber}}</template></el-table-column>
 <el-table-column prop="school" label="学校" min-width="170"/><el-table-column prop="college" label="学院" min-width="170"/>
 <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='APPROVED'?'success':row.status==='REJECTED'?'danger':'warning'">{{labels[row.status]}}</el-tag></template></el-table-column>
 <el-table-column prop="review_reason" label="审核说明" min-width="180"/>
 <el-table-column label="操作" width="150"><template #default="{row}"><template v-if="row.status==='PENDING'"><el-button link type="primary" :disabled="saving" @click="review(row,'APPROVED')">通过</el-button><el-button link type="danger" :disabled="saving" @click="review(row,'REJECTED')">驳回</el-button></template><span v-else>已处理</span></template></el-table-column>
 </el-table><el-pagination v-model:current-page="page" class="mt-4" layout="total,prev,pager,next" :page-size="20" :total="total" @current-change="load"/></el-card>
 <p class="text-sm text-gray-500">本系统不能替代专业心理咨询</p>
</div></template>
