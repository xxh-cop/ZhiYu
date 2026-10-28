<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElAlert, ElButton, ElSkeleton } from 'element-plus';
import { requestClient } from '#/api/request';
type Summary={started:number;finished:number;paused:number;abandoned:number;completionRate:number|null};
const summary=ref<Summary|null>(null),loading=ref(false),error=ref('');
async function load(){if(loading.value)return;loading.value=true;error.value='';try{summary.value=await requestClient.get<Summary>('/admin/starter-quality')}catch{summary.value=null;error.value='过程汇总暂时不可用，请重试。'}finally{loading.value=false}}
onMounted(load);
</script>
<template><section class="starter-quality" aria-label="入门手作过程汇总"><div class="quality-heading"><div><h2>第一次手作，从开始走向完成</h2><p>原创入门目录 · 全部未删除会话 · 同一用户多次创作分别计数</p></div><ElButton :loading="loading" @click="load">刷新过程数据</ElButton></div>
<ElSkeleton v-if="loading" :rows="2" animated/><ElAlert v-else-if="error" :title="error" type="warning" :closable="false"/>
<div v-else-if="summary" class="quality-metrics"><div><span>已开始会话</span><strong>{{summary.started}}</strong></div><div><span>已完成</span><strong>{{summary.finished}}</strong></div><div><span>暂停中</span><strong>{{summary.paused}}</strong></div><div><span>已放弃</span><strong>{{summary.abandoned}}</strong></div><div><span>完成占比</span><strong>{{summary.completionRate===null?'—':summary.completionRate+'%'}}</strong></div></div>
<p class="quality-note">{{summary?.started===0?'目前还没有真实创作记录。':'用于发现内容准备与操作流程的问题。'}}该占比不是首次用户转化率，也不代表心理改善或临床疗效；此处不展示用户身份与心理记录。</p></section></template>
<style scoped>.starter-quality{margin:24px 0;padding:24px;border:1px solid var(--el-border-color);border-radius:16px;background:var(--el-bg-color)}.quality-heading{display:flex;justify-content:space-between;gap:16px;align-items:center}.quality-heading h2{font-size:19px;font-weight:600;margin-bottom:8px}.quality-heading p,.quality-note{font-size:13px;color:var(--el-text-color-regular);line-height:1.8}.quality-metrics{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:16px;margin:24px 0}.quality-metrics div{padding:18px;border-radius:12px;background:var(--el-fill-color-light)}.quality-metrics span{display:block;font-size:13px;color:var(--el-text-color-regular)}.quality-metrics strong{display:block;font-size:28px;margin-top:10px}@media(max-width:800px){.quality-heading{align-items:flex-start;flex-direction:column}.quality-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}</style>
