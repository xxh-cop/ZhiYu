<script setup lang="ts">
import {onMounted,ref} from 'vue';
import {requestClient} from '#/api/request';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
const data=ref<any>(null),loading=ref(false),error=ref('');
async function load(){loading.value=true;error.value='';try{data.value=await requestClient.get('/admin/config/speech');}catch(e:any){error.value=e.message||'加载失败';}finally{loading.value=false;}}
onMounted(load);
</script>
<template><div class="zy-business-page flex flex-col gap-4"><BusinessPageHeader title="语音服务" description="检查语音网关配置；凭据始终由服务端环境变量管理。" :loading="loading" @refresh="load"/><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-card v-if="data" shadow="never"><el-alert :title="data.status.message" :type="data.status.available?'success':'info'" :closable="false"/><p class="my-4 leading-relaxed">{{data.note}}</p><ol class="list-decimal pl-5 leading-8"><li>选择真实语音服务或本地服务，实现织愈 JSON 网关协议。</li><li>在后端部署环境设置以下变量，密钥不要填写在前端或代码中。</li><li>重启后端并刷新状态，使用测试录音验证识别与合成。</li></ol><el-table :data="data.variables.map((name:string)=>({name}))" class="my-4"><el-table-column prop="name" label="服务端环境变量"/></el-table><p class="text-sm text-gray-500">当前接入协议：{{data.protocol}}。已配置不等于服务可用；真实服务质量和授权仍需部署时核验。</p></el-card><p class="text-sm text-gray-500">本系统不能替代专业心理咨询</p></div></template>
