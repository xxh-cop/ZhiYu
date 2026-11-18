<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {request} from '../../utils/request'
import ListeningSummaryCard from '../../components/ListeningSummaryCard.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import StateBlock from '../../components/StateBlock.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
const rows=ref([]),page=ref(1),total=ref(0),loading=ref(false),error=ref(''),detail=ref(null),pendingDelete=ref(null),deleting=ref(false)
async function load(reset=true){if(loading.value)return;loading.value=true;error.value='';try{const next=reset?1:page.value+1;const d=await request({url:'/api/emotion/sessions',data:{page:next}});const recs=(d&&Array.isArray(d.records))?d.records:[];rows.value=reset?recs:[...rows.value,...recs];total.value=Number(d&&d.total)||0;page.value=next}catch(e){error.value='倾听记录暂时没有读取成功，请重试'}finally{loading.value=false}}
async function view(row){try{detail.value=await request({url:'/api/emotion/sessions/'+row.sessionId})}catch(e){error.value=e.message}}
function remove(id){pendingDelete.value=id}
async function confirmDelete(){const id=pendingDelete.value;if(!id||deleting.value)return;deleting.value=true;try{await request({url:'/api/emotion/sessions/'+id,method:'DELETE'});detail.value=null;pendingDelete.value=null;await load()}catch(e){error.value=e.message}finally{deleting.value=false}}
onShow(()=>load())
async function resume(id){try{await request({url:'/api/emotion/sessions/'+id+'/state',method:'PUT',data:{action:'RESUME'}});uni.navigateTo({url:'/pages/emotion/chat?sessionId='+id})}catch(e){error.value=e.message}}
</script>
<template><view :class="zyThemeClass" class="history-page"><text class="eyebrow">LISTENING ARCHIVE · 倾听记录</text><text class="heading">说过的，都收好了</text><text class="intro">只有你能查看这些对话。可以回顾，也可以选择删除。</text><text v-if="error" class="error">{{error}}</text><AccessibleButton v-if="error" size="mini" @click="load()">重试</AccessibleButton><view v-if="detail" class="card"><AccessibleButton size="mini" @click="detail=null">返回列表</AccessibleButton><view v-for="(m,i) in detail.messages" :key="i" class="message"><text class="role">{{m.role==='user'?'我':'小织'}}</text><text>{{m.content}}</text></view><ListeningSummaryCard :session-id="detail.session.sessionId" :session-status="detail.session.status" /><AccessibleButton @click="resume(detail.session.sessionId)">继续这次倾听</AccessibleButton><text class="intro">{{detail.session.summary||'尚未形成摘要'}}</text><AccessibleButton @click="remove(detail.session.sessionId)">删除本次记录</AccessibleButton></view><template v-else><view v-for="r in rows" :key="r.sessionId" class="card" role="button" tabindex="0" @click="view(r)" @keydown.enter.prevent="view(r)" @keydown.space.prevent="view(r)"><text class="date">{{String(r.startTime||'').replace('T',' ').slice(0,16)}}</text><text>{{r.summary||'一次倾听对话'}}</text><text class="open">查看对话 →</text></view><StateBlock v-if="loading&&!rows.length" type="loading" title="正在读取倾听记录…"/><StateBlock v-else-if="!loading&&!rows.length&&!error" type="empty" title="还没有倾听记录" description="想聊时小织都在"/><AccessibleButton v-if="rows.length<total" :loading="loading" @click="load(false)">加载更多</AccessibleButton></template><ActionSheet :open="!!pendingDelete" title="删除倾听记录" description="会话与消息将被删除，独立安全审计按规则保留。是否继续？" :actions="[{key:'confirm',label:'确认删除',tone:'danger',busy:deleting}]" cancel-text="再想想" :busy="deleting" @select="confirmDelete" @close="pendingDelete=null"/><text class="intro">本系统不能替代专业心理咨询</text></view></template>
<style scoped>.history-page{padding:36rpx 32rpx 80rpx;display:flex;flex-direction:column;gap:24rpx;min-height:100vh;max-width:880px;margin:0 auto;box-sizing:border-box;width:100%;background:#f5f8f5}.heading{font-size:var(--zy-text-title-lg, 40rpx);font-weight:700;color:#203f35}.intro{font-size:var(--zy-text-caption, 24rpx);color:#697a72;line-height:1.8}.card{padding:30rpx;border-radius:24rpx;background:white;display:flex;flex-direction:column;gap:20rpx;font-size:var(--zy-text-body, 28rpx);line-height:1.8}.date,.role{font-size:var(--zy-text-caption, 24rpx);color:#697a72}.message{display:flex;flex-direction:column;gap:10rpx;padding-bottom:24rpx;border-bottom:1px solid #edf1ed;white-space:pre-wrap}.open{color:var(--zy-brand-700, #1a645e);font-size:var(--zy-text-caption, 24rpx)}.error{color:#a73d3d}button:focus-visible{outline:3px solid var(--zy-brand-700,var(--zy-brand-700, #1a645e));outline-offset:3px}.eyebrow{font-size:var(--zy-text-micro,22rpx);letter-spacing:2px;color:var(--zy-muted,#6e7b75)}
.heading{font-family:var(--zy-font-display,serif);font-size:56rpx;font-weight:500;line-height:1.35;color:var(--zy-title,#111816);margin-top:10rpx}
.intro{color:var(--zy-muted,#6e7b75)}
.card{padding:24rpx 0;border-radius:0;background:transparent;border-top:1rpx solid var(--zy-border,#d3ded8);gap:16rpx}
.card:last-of-type{border-bottom:1rpx solid var(--zy-border,#d3ded8)}
.date,.role{color:var(--zy-muted,#6e7b75);letter-spacing:1px}
.open{color:var(--zy-brand-700,#0a403b)}
.message{border-bottom:1rpx solid var(--zy-border,#d3ded8)}
button{border-radius:0!important}
</style>
