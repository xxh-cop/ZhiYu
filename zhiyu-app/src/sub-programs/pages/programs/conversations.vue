<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {request} from '@/utils/request'
import AccessibleButton from '@/components/AccessibleButton.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import XiaotuanPet from '@/components/XiaotuanPet.vue'
const rows=ref([]),selected=ref(null),messages=ref([]),content=ref(''),loading=ref(false),busy=ref(false),error=ref(''),more=ref(false)
const path=()=>'/api/growth/bookings/'+selected.value.id
async function load(){loading.value=true;error.value='';try{rows.value=await request({url:'/api/growth/conversations'});if(selected.value){selected.value=rows.value.find(r=>r.id===selected.value.id)||null;if(selected.value)await read()}}catch(e){error.value=e.message||'读取失败，请重试'}finally{loading.value=false}}
async function read(earlier=false){if(!selected.value)return;const list=await request({url:path()+'/messages',data:earlier?{before:messages.value.at(-1)?.id}:{}});messages.value=earlier?[...messages.value,...list]:list;more.value=list.length===50}
async function select(row){if(busy.value)return;selected.value=row;messages.value=[];content.value='';busy.value=true;error.value='';try{await read()}catch(e){error.value=e.message}finally{busy.value=false}}
async function action(fn){if(busy.value)return;busy.value=true;error.value='';try{await fn();await load()}catch(e){error.value=e.message||'操作失败，内容已保留'}finally{busy.value=false}}
const pendingToggle=ref(false),pendingRemove=ref(null)
function toggle(){if(busy.value)return;pendingToggle.value=true}
function confirmToggle(){pendingToggle.value=false;action(()=>request({url:path()+'/conversation',method:'PUT',data:{enabled:!Number(selected.value.enabled)}}))}
function send(){if(!content.value.trim()||!selected.value.canSend)return;action(async()=>{await request({url:path()+'/messages',method:'POST',data:{content:content.value.trim()}});content.value=''})}
function remove(m){if(busy.value)return;pendingRemove.value=m}
function confirmRemove(){const m=pendingRemove.value;pendingRemove.value=null;if(m)action(()=>request({url:path()+'/messages/'+m.id,method:'DELETE'}))}
async function earlier(){if(busy.value)return;busy.value=true;error.value='';try{await read(true)}catch(e){error.value=e.message}finally{busy.value=false}}
onShow(load)
</script>
<template><view :class="zyThemeClass" class="growth-page conversation-page"><view class="growth-hero"><text class="growth-eyebrow">CONVERSATION · 预约交流</text><text class="growth-title">把想说的话，慢慢说</text><text class="growth-subtitle">只属于你与预约带领者的交流空间。此处不提供紧急响应。</text></view><view v-if="error" class="growth-error" role="alert">{{error}}<AccessibleButton :disabled="busy||loading" @click="load">重试加载</AccessibleButton></view><view class="conversation-layout"><view class="growth-card"><view class="growth-row growth-spread"><text class="growth-card-title">我的预约</text><AccessibleButton class="conversation-refresh" :disabled="busy||loading" @click="load">刷新</AccessibleButton></view><text v-if="loading" class="growth-muted">正在读取…</text><text v-else-if="!rows.length" class="growth-muted">暂无一对一预约。预约确认后，可由学生主动开启交流。</text><AccessibleButton v-for="r in rows" :key="r.id" :class="['conversation-item',{active:selected?.id===r.id}]" :aria-pressed="selected?.id===r.id" :disabled="busy" @click="select(r)"><text>{{r.title}}</text><text class="growth-muted">{{r.canSend?'可以交流':Number(r.enabled)?'当前只读':'未开启交流'}}</text></AccessibleButton></view><view class="growth-card"><template v-if="selected"><text class="growth-card-title">{{selected.title}}</text><AccessibleButton v-if="selected.isStudent" class="growth-button secondary" :disabled="busy" @click="toggle">{{Number(selected.enabled)?'关闭交流授权':'开启交流授权'}}</AccessibleButton><text v-if="!selected.canSend" class="growth-muted">当前为只读状态。预约须已确认、双方账号有效并同意隐私政策，且学生已单独开启交流。</text><view v-for="m in messages" :key="m.id" :class="['conversation-message',{mine:m.mine}]"><view class="growth-row growth-spread"><text>{{m.mine?'我':'预约对方'}}</text><AccessibleButton v-if="m.mine" :disabled="busy" @click="remove(m)">撤回</AccessibleButton></view><text class="message-copy">{{m.content}}</text><text class="growth-muted">{{String(m.create_time).replace('T',' ').slice(0,16)}}</text></view><text v-if="!messages.length" class="growth-muted">这里还没有消息。</text><AccessibleButton v-if="more" :disabled="busy" @click="earlier">读取更早消息</AccessibleButton><textarea v-model="content" class="growth-input growth-textarea" aria-label="预约交流内容" maxlength="1000" :disabled="!selected.canSend||busy" placeholder="可以从想讨论的手作体验开始…"/><view class="growth-row growth-spread"><text class="growth-muted">{{content.length}} / 1000</text><AccessibleButton class="growth-button" :disabled="!selected.canSend||busy||!content.trim()" @click="send">{{busy?'处理中…':'发送消息'}}</AccessibleButton></view></template><view v-else class="conversation-empty"><XiaotuanPet :size="72" face="listen" aria-hidden="true"/><text class="growth-card-title">选择一条预约，开始交流</text><text class="growth-muted">开启与关闭都由你决定，小织陪你尊重自己的节奏。</text></view></view></view><text class="growth-foot">本系统不能替代专业心理咨询</text><ActionSheet :open="pendingToggle" :title="Number(selected&&selected.enabled)?'关闭预约交流':'开启预约交流'" description="仅你与预约带领者可以查看。消息加密保存，可随时关闭或撤回本人消息；此处不提供紧急响应。" :actions="[{key:'confirm',label:Number(selected&&selected.enabled)?'确认关闭':'确认开启',tone:'primary',busy:busy}]" cancel-text="再想想" :busy="busy" @select="confirmToggle" @close="pendingToggle=false"/><ActionSheet :open="!!pendingRemove" title="撤回消息" description="撤回后双方都无法再读取这条消息。" :actions="[{key:'confirm',label:'确认撤回',tone:'danger',busy:busy}]" cancel-text="再想想" :busy="busy" @select="confirmRemove" @close="pendingRemove=null"/></view></template>

<style scoped>
.conversation-page{background:var(--zy-bg,#f1f4f1)}
.conversation-layout{display:grid;grid-template-columns:minmax(220px,.7fr) minmax(0,1.3fr);gap:40px;align-items:start}
.conversation-layout .growth-card{border-top:0}
.conversation-refresh{background:transparent!important;color:var(--zy-brand-700,#0a403b)!important;font-size:var(--zy-text-caption,24rpx)!important;padding:0!important;line-height:1.6!important;border:0!important}
.conversation-item{display:flex;flex-direction:column;align-items:flex-start;text-align:left;gap:10rpx;padding:20rpx 0!important;border:0!important;border-bottom:1rpx solid var(--zy-border,#d3ded8)!important;border-left:3rpx solid transparent!important;border-radius:0!important;margin:0!important;width:100%;background:transparent!important}
.conversation-item.active{border-left-color:var(--zy-brand-600,#0e5b53)!important;background:transparent!important}
.conversation-message{margin:20rpx 0;padding:16rpx 0;border:0;border-top:1rpx solid var(--zy-border,#d3ded8);border-radius:0}
.conversation-message.mine{background:transparent;border-left:2rpx solid var(--zy-brand-600,#0e5b53);padding-left:16rpx}
.message-copy{display:block;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;margin:12rpx 0;color:var(--zy-text,#42524c)}
.conversation-empty{display:flex;flex-direction:column;align-items:center;text-align:center;padding:32rpx 16rpx;gap:18rpx}
.conversation-empty image{width:110rpx;height:130rpx}
@media(max-width:700px){.conversation-layout{display:block}.conversation-layout .growth-card{margin-bottom:0}}
</style>
