<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
import {ref} from 'vue'
import {onShow,onReachBottom} from '@dcloudio/uni-app'
import {request} from '../../utils/request'
import {invalidate, TTL_KEYS} from '../../utils/ttlCache'
import {API_BASE_URL} from '../../utils/config'
import {useInteraction} from '../../composables/useInteraction'
import {softActive} from '../../utils/soft-tier'
import MediaVideo from '../../components/MediaVideo.vue'
import StateBlock from '../../components/StateBlock.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import {useToast} from '../../composables/useToast'
const toast=useToast()
const {reducedMotion}=useInteraction()
const rows=ref([]),counts=ref({}),total=ref(0),page=ref(1),filter=ref('all'),loading=ref(false),busy=ref(''),error=ref(''),expanded=ref(''),pendingWithdraw=ref(null)
const failedCovers=ref({})
const filters=[{id:'all',label:'全部作品'},{id:'0',label:'待审核'},{id:'1',label:'已通过'},{id:'2',label:'已下架'}]
let sequence=0
const url=s=>/^https?:\/\//.test(s)||s.startsWith('/static/')?s:API_BASE_URL+s
const quote=w=>String(w.message||'这份创作还没有写下故事。').replace(/\s+/g,' ').slice(0,56)
const markFail=id=>{failedCovers.value={...failedCovers.value,[id]:true}}
const state=w=>Number(w.status)===0?'审核中 · 仅自己可见':Number(w.status)===2?'已下架':Number(w.visible)===1?'社区公开':'仅自己可见'
/* 「展示受限」标记（2026-11-07 处置链）：字段仅作者本人数据且开关开启时才下发，
 * 缺省/过期即不渲染——开关关闭与旧数据零痕迹。 */
const soft=w=>softActive(w&&w.softDemoteUntil)
async function load(more=false){if(more&&loading.value)return;const seq=++sequence;const next=more?page.value+1:1;loading.value=true;error.value='';try{const data=await request({url:'/api/works/mine',data:{page:next,...(filter.value==='all'?{}:{status:filter.value})}});if(seq!==sequence)return;const records=(data&&data.records)||[];rows.value=more?[...rows.value,...records]:records;counts.value=(data&&data.counts)||{};total.value=Number(data&&data.total)||0;page.value=next}catch(e){if(seq===sequence){error.value=e.message||'作品读取失败，请重试';if(!more)rows.value=[]}}finally{if(seq===sequence)loading.value=false}}
function choose(id){if(busy.value)return;filter.value=id;expanded.value='';load()}
function openDetail(w){uni.navigateTo({url:'/pages/work/detail?id='+w.id+'&from=mine&status='+w.status})}
function create(){uni.navigateTo({url:'/pages/work/publish'})}
function withdraw(w){if(busy.value)return;pendingWithdraw.value=w}
async function confirmWithdraw(){const w=pendingWithdraw.value;if(!w||busy.value)return;busy.value=w.id;try{await request({url:'/api/works/'+w.id+'/withdraw',method:'POST'});invalidate(TTL_KEYS.userStats);expanded.value='';pendingWithdraw.value=null;await load();toast.success('作品已撤回')}catch(e){toast.care(e.message||'撤回失败，请重试')}finally{busy.value=''}}
onShow(()=>load());onReachBottom(()=>{if(rows.value.length<total.value)load(true)})
</script>
<template><view class="growth-page personal-works" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]"><view class="portfolio-hero"><view><ZyPageHead seal="作" title="每一份作品，都属于你" subtitle="查看发布状态，也由你决定哪些表达继续被看见。" /></view><XiaotuanPet :size="56" face="happy" aria-hidden="true" class="companion"/></view><view class="portfolio-actions"><text class="growth-muted">{{counts.all||0}} 份作品 · 保留自己的节奏</text><AccessibleButton class="growth-button" @click="create">发布作品</AccessibleButton></view><scroll-view scroll-x class="filter-scroll"><view class="portfolio-filters"><AccessibleButton v-for="f in filters" :key="f.id" :class="{active:filter===f.id}" :disabled="!!busy" :aria-pressed="filter===f.id" @click="choose(f.id)">{{f.label}} {{counts[f.id]||0}}</AccessibleButton></view></scroll-view><StateBlock v-if="loading&&!rows.length" type="loading" shape="card" title="正在整理作品…"/><StateBlock v-else-if="error&&!rows.length" type="error" title="作品读取失败" :description="error" action-text="重新读取" :action-loading="loading" @action="load()"/><StateBlock v-else-if="!rows.length" type="empty" face="calm" :art="filter==='all'?'works':''" :title="filter==='all'?'你的创作故事，从这里开始':'这个分类暂时没有作品'" description="作品不必完美，记录当下就有意义。" :action-text="filter!=='all'?'查看全部作品':'记录第一份作品'" @action="filter!=='all'?choose('all'):create()"/><view v-if="error&&rows.length" class="growth-error" role="alert">{{error}}<AccessibleButton class="growth-button secondary" @click="load()">重新读取</AccessibleButton></view><view class="portfolio-grid"><view v-for="w in rows" :key="w.id" class="portfolio-card"><image v-if="w.coverUrl&&!failedCovers[w.id]" :src="url(w.coverUrl)" mode="aspectFill" class="portfolio-cover" :alt="'作品封面：'+w.title" lazy-load @error="markFail(w.id)"/><view v-else class="portfolio-placeholder"><text class="placeholder-mark">“</text><text class="placeholder-text">{{quote(w)}}</text><text class="placeholder-line">{{w.mediaType==='video'?'影像记录':'文字作品'}} · 未配图</text></view><view class="portfolio-body"><view class="portfolio-meta"><text :class="['state',{pending:Number(w.status)===0,hidden:Number(w.status)===2}]">{{state(w)}}</text><text v-if="soft(w)" class="soft-tag">展示受限 · 保护中</text><text>{{String(w.createTime||'').slice(0,10)}}</text></view><text class="portfolio-title">{{w.title}}</text><text class="growth-muted">{{Number(w.anonymous)===1?'匿名展示':'昵称展示'}} · {{Number(w.visible)===1?'公开范围':'私密范围'}}</text><view v-if="Number(w.status)!==1" class="status-help">{{Number(w.status)===0?'正在等待审核，目前不会出现在社区。':'作品已下架，目前不会出现在社区。你仍可在这里查看或撤回。'}}</view><view v-else-if="soft(w)" class="status-help">这条作品暂时排在社区较靠后的位置，到期自动恢复，只有你能看到这条提示；点「查看详情」可了解或申请人工复核。</view><AccessibleButton class="portfolio-expand" :aria-expanded="expanded===w.id" @click="expanded=expanded===w.id?'':w.id">{{expanded===w.id?'收起内容 −':'查看作品内容 ＋'}}</AccessibleButton><view v-if="expanded===w.id" class="portfolio-detail"><MediaVideo v-if="w.mediaType==='video'&&w.videoUrl" :src="w.videoUrl" caption="我的创作视频"/><text>{{w.message||'这份作品暂未填写故事。'}}</text></view><view class="portfolio-bottom"><AccessibleButton v-if="Number(w.status)===1" class="text-action" @click="openDetail(w)">查看详情</AccessibleButton><text v-else class="growth-muted">{{Number(w.status)===0?'等待审核':'已停止展示'}}</text><AccessibleButton class="text-action withdraw" :disabled="!!busy" @click="withdraw(w)">{{busy===w.id?'撤回中…':'撤回作品'}}</AccessibleButton></view></view></view></view><AccessibleButton v-if="rows.length<total" class="growth-button secondary" :disabled="loading||!!busy" @click="load(true)">{{loading?'正在读取…':'加载更多作品'}}</AccessibleButton><ActionSheet :open="!!pendingWithdraw" title="撤回这份作品？" description="撤回后作品将从列表和社区移除，关联评论及互动记录也会移除。此操作无法恢复。" :actions="[{key:'confirm',label:'确认撤回',tone:'danger',busy:!!busy}]" cancel-text="再想想" :busy="!!busy" @select="confirmWithdraw" @close="pendingWithdraw=null"/><Toast/></view></template>
<style scoped>
<style scoped>
.personal-works {max-width:1100px}
.portfolio-hero {position:relative;display:block;background:transparent;border:0;border-bottom:1rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);padding:12rpx 0 36rpx}
.portfolio-hero>view {max-width:580rpx}

.companion {position:absolute;right:0;top:0;width:96rpx;height:112rpx;opacity:.92}
.portfolio-actions {display:flex;align-items:center;justify-content:space-between;gap:20rpx;margin:24rpx 0}
.portfolio-actions button {font-size:var(--zy-text-caption, 24rpx);padding:0 24rpx;margin:0;line-height:2.8}
.filter-scroll {white-space:nowrap;margin-bottom:28rpx}
.portfolio-filters {display:inline-flex;gap:0}
.portfolio-filters button {margin:0 -1rpx 0 0;padding:16rpx 24rpx;background:transparent;color:var(--zy-muted,#6e7b75);font-size:var(--zy-text-caption, 24rpx);border-radius: var(--zy-radius-control, 18rpx);border:1rpx solid var(--zy-border,#d3ded8);line-height:1.4}
.portfolio-filters button.active {background:var(--zy-brand-600);border:2rpx solid var(--zy-brand-600);color:#fff;font-weight:600;box-shadow:none}
.portfolio-filters button::after,.text-action::after,.portfolio-expand::after {border:0}
.portfolio-filters button:focus-visible,.text-action:focus-visible,.portfolio-expand:focus-visible {outline:3px solid var(--zy-brand-700,#183369);outline-offset:3px}
.portfolio-grid {display:grid;grid-template-columns:minmax(0,1fr);gap:36rpx}
.portfolio-card {min-width:0;background:transparent;border:0;border-bottom:1rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);padding-bottom:16rpx;overflow:visible}
.portfolio-cover {width:100%;height:340rpx;display:block}
.portfolio-placeholder {min-height:240rpx;padding:24rpx 20rpx;box-sizing:border-box;display:flex;flex-direction:column;justify-content:center;gap:12rpx;background:#e7ebe8;color:var(--zy-title,#111816)}
.placeholder-mark {font-family:var(--zy-font-display,serif);font-size:56rpx;line-height:1;color:var(--zy-care,#a64842)}
.placeholder-text {font-family:var(--zy-font-display,serif);font-size:32rpx;line-height:1.5;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.placeholder-line {font-size:var(--zy-text-micro, 22rpx);letter-spacing:1rpx;color:var(--zy-muted,#6e7b75)}
.portfolio-body {padding:24rpx 0 0}
.portfolio-meta {display:flex;justify-content:space-between;gap:12rpx;font-size:var(--zy-text-micro, 22rpx);color:var(--zy-muted,#6e7b75)}
.state {color:var(--zy-brand-700,#183369)}.state.pending {color:var(--zy-muted,#6e7b75)}.state.hidden {color:var(--zy-care,#a64842)}
.soft-tag {flex:1;text-align:center;color:var(--zy-muted,#6e7b75)}
.portfolio-title {display:block;font-family:var(--zy-font-display,serif);font-size:36rpx;line-height:1.5;font-weight:500;margin:14rpx 0;overflow-wrap:anywhere;color:var(--zy-title,#111816)}
.status-help {font-size:var(--zy-text-caption, 24rpx);line-height:1.8;color:var(--zy-text,#42524c);background:transparent;border-left:2rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);padding:12rpx 0 12rpx 16rpx;margin-top:16rpx}
.portfolio-expand {font-size:var(--zy-text-caption, 24rpx);color:var(--zy-brand-700,#183369);background:transparent;text-align:left;padding:16rpx 0;margin-top:8rpx}
.portfolio-detail {font-size:var(--zy-text-caption, 24rpx);line-height:1.9;white-space:pre-wrap;overflow-wrap:anywhere;background:transparent;border-top:1rpx solid var(--zy-border,#d3ded8);border-bottom:1rpx solid var(--zy-border,#d3ded8);padding:18rpx 0;border-radius: var(--zy-radius-control, 18rpx)}
.portfolio-bottom {display:flex;align-items:center;justify-content:space-between;gap:12rpx;border-top:1rpx solid var(--zy-border,#d3ded8);margin-top:16rpx;padding-top:16rpx}
.text-action {background:transparent;color:var(--zy-brand-700,#183369);padding:8rpx 0;margin:0;font-size:var(--zy-text-caption, 24rpx)}
.withdraw {color:var(--zy-care,#a64842)}
@media(min-width:760px) {.portfolio-grid {grid-template-columns:repeat(2,minmax(0,1fr))}.portfolio-hero {padding:16px 0 32px}.companion {width:96px;height:112px}}
</style>
