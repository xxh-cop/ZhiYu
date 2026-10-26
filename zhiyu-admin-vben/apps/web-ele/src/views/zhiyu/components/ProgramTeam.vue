<script setup lang="ts">
import {onMounted,ref} from 'vue';import {ElAlert,ElSelect,ElOption,ElButton,ElMessage} from 'element-plus';import {requestClient} from '#/api/request';
const props=defineProps<{programId:string}>();const options=ref<{id:string;nickname:string;role:number}[]>([]),selected=ref<string[]>([]),busy=ref(false),error=ref('');
async function load(){busy.value=true;error.value='';try{const [hosts,team]=await Promise.all([requestClient.get<any[]>('/growth/hosts'),requestClient.get<any[]>('/growth/programs/'+props.programId+'/collaborators')]);options.value=hosts;selected.value=team.map(t=>t.id)}catch{error.value='团队加载失败，请重试'}finally{busy.value=false}}
async function save(){busy.value=true;try{await requestClient.put('/growth/programs/'+props.programId+'/collaborators',{userIds:selected.value});ElMessage.success('协作权限已更新')}catch{}finally{busy.value=false}}
onMounted(load);
</script>
<template><ElAlert title="主办人或管理员可分配最多五名协作带领者。协作者可以编辑活动、课程和签到，不能转授权限，也无法访问一对一交流。" :closable="false"/><ElAlert v-if="error" :title="error" type="error" :closable="false"/><ElSelect v-model="selected" multiple filterable :multiple-limit="5" :disabled="busy||!!error" placeholder="选择带领者" style="width:100%;margin:24px 0"><ElOption v-for="u in options" :key="u.id" :value="u.id" :label="u.nickname+' · '+(u.role===1?'传承人':'咨询师')"/></ElSelect><ElButton :loading="busy" @click="load">重新读取</ElButton><ElButton type="primary" :disabled="busy||!!error" @click="save">保存团队权限</ElButton></template>
