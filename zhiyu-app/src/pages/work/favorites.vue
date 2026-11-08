<template>
	<view :class="zyThemeClass" class="favorites-page"><view class="favorites-hero"><view><text class="eyebrow">灵感收集 · 慢慢回看</text><text class="title">我的收藏</text><text class="subtitle">把触动你的创作，留给下一次相遇。</text></view><XiaotuanPet :size="76" face="care" aria-hidden="true"/></view>
 <view class="toolbar"><text>{{total}} 份可查看的创作</text><AccessibleButton :disabled="loading" @click="load(1)">刷新收藏</AccessibleButton></view><text class="hint">仅你可见的收藏清单。已下架或不可访问的作品不会展示。</text>
 <StateBlock v-if="loading&&!records.length" type="loading" title="正在整理收藏…"/>
 <StateBlock v-else-if="error&&!records.length" type="error" title="收藏没有加载成功" :description="error" action-text="重试" :action-loading="loading" @action="load(failedPage)"/>
 <StateBlock v-else-if="!records.length" type="empty" face="happy" title="还没有收藏的创作" description="遇到喜欢的作品，点一下收藏就能在这里找到。" action-text="去社区发现" @action="community"/>
 <template v-else>
 <view class="grid"><view v-for="item in records" :key="item.id" class="saved-card"><AccessibleButton class="card-open" @click="open(item)"><image v-if="item.coverUrl&&!failedImages[item.id]" class="cover" :src="fullUrl(item.coverUrl)" mode="aspectFill" :alt="'作品封面：'+item.title" @error="failedImages[item.id]=true"/><view v-else class="cover cover-empty"><XiaotuanPet :size="52" face="calm" aria-hidden="true"/><text>{{item.mediaType==='video'?'创作影像':'一份创作的心意'}}</text></view><view class="card-copy"><text class="tag">{{Number(item.visible)===1?'社区作品':'仅本人可见'}} · {{item.mediaType==='video'?'视频':item.mediaType==='text'?'文字':'手作'}}</text><text class="card-title">{{item.title}}</text><text class="excerpt">{{item.message||'点开看看这份创作的故事。'}}</text></view></AccessibleButton><view class="card-footer"><text>收藏于 {{String(item.savedAt||'').slice(0,10)}}</text><AccessibleButton :disabled="!!removing" @click="askRemove(item)">{{removing===item.id?'处理中…':'取消收藏'}}</AccessibleButton></view></view></view>
 <AccessibleButton v-if="records.length<total&&!error" class="load-more" :disabled="loading" :loading="loading" @click="load(page+1)">查看更多收藏</AccessibleButton>
 <StateBlock v-if="error&&records.length" type="error" compact title="更多收藏没有加载成功" :description="error" action-text="重试" @action="load(failedPage)"/>
 </template>
 <text class="footnote">本系统不能替代专业心理咨询</text>
 <ActionSheet :open="!!pendingRemove" title="取消收藏？" description="这会从你的收藏清单移除作品，不会删除作品本身。" :actions="[{key:'confirm',label:'确认取消收藏',tone:'danger',busy:!!removing}]" cancel-text="先留着" :busy="!!removing" @select="confirmRemove" @close="pendingRemove=null"/>
 <Toast/>
 </view>
</template>
<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {http} from '../../utils/request'
import {API_BASE_URL} from '../../utils/config'
import {workPath} from '../../utils/work-navigation'
import StateBlock from '../../components/StateBlock.vue'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import XiaotuanPet from '../../components/XiaotuanPet.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import {useToast} from '../../composables/useToast'
const toast=useToast()
const records=ref([]),total=ref(0),page=ref(0),loading=ref(false),error=ref(''),failedPage=ref(1),removing=ref(''),pendingRemove=ref(null),failedImages=ref({})
onShow(()=>load(1))
async function load(target){if(loading.value)return;loading.value=true;error.value='';failedPage.value=target;try{const data=await http.get('/api/works/favorites',{data:{page:target}});const list=(data&&data.records)||[];records.value=target===1?list:records.value.concat(list);total.value=Number(data&&data.total)||0;page.value=target}catch(e){error.value=e.message||'收藏加载失败'}finally{loading.value=false}}
function askRemove(item){if(removing.value)return;pendingRemove.value=item}
async function confirmRemove(){const item=pendingRemove.value;if(!item||removing.value)return;removing.value=item.id;try{await http.del('/api/works/favorites/'+item.id);pendingRemove.value=null;await load(1);toast.show('已取消收藏')}catch(e){toast.care(e.message||'取消失败，请重试')}finally{removing.value=''}}
function open(item){uni.navigateTo({url:workPath(item.id)})}
function community(){uni.switchTab({url:'/pages/work/list'})}
function fullUrl(url){return /^https?:\/\//.test(url)||url.startsWith('/static/')?url:API_BASE_URL+url}
</script>
<style scoped>
.favorites-page{max-width:1180px;margin:auto;padding:28rpx 28rpx 80rpx;color:var(--zy-title,#233d34);box-sizing:border-box}.favorites-hero{background:linear-gradient(120deg,var(--zy-bg-tint-mint,#e8efe4),var(--zy-bg-tint-warm,#f7efd9));border:1rpx solid var(--zy-border,#dfe7dc);border-radius:28rpx;padding:36rpx;display:flex;align-items:center;justify-content:space-between;gap:20rpx}.eyebrow,.title,.subtitle{display:block}.eyebrow{font-size:22rpx;color:var(--zy-brand-600,#1A645E)}.title{font-family:var(--zy-serif);font-size:44rpx;margin:12rpx 0}.subtitle,.hint{font-size:24rpx;color:var(--zy-text,#4a5f54);line-height:1.7}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:20rpx;margin-top:24rpx;font-size:26rpx}.toolbar button,.card-footer button{font-size:23rpx;color:var(--zy-brand-600,#1A645E);background:transparent;padding:12rpx 0;margin:0}button::after{border:0}button:focus-visible{outline:3px solid var(--zy-brand-700,#1a645e);outline-offset:3px}.hint{display:block;margin:12rpx 0 24rpx}.grid{display:grid;grid-template-columns:minmax(0,1fr);gap:24rpx}.saved-card{background:var(--zy-card-bg,#fff);border:1rpx solid var(--zy-border,#dfe7dc);border-radius:24rpx;overflow:hidden}.card-open{display:block;text-align:left;background:var(--zy-card-bg,#fff);padding:0;margin:0;border-radius:0;line-height:1.6;width:100%}.cover{width:100%;height:320rpx;display:block}.cover-empty{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:16rpx;background:var(--zy-bg-tint-mint,#e8efe4);color:var(--zy-text,#4a5f54);font-size:24rpx}.card-copy{padding:24rpx}.tag{font-size:21rpx;color:var(--zy-brand-600,#1A645E)}.card-title{display:block;font-size:32rpx;margin:12rpx 0;overflow-wrap:anywhere}.excerpt{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;color:var(--zy-text,#4a5f54);font-size:25rpx;overflow-wrap:anywhere}.card-footer{display:flex;align-items:center;justify-content:space-between;padding:12rpx 24rpx;border-top:1rpx solid #eef1eb;font-size:21rpx;color:var(--zy-text,#4a5f54)}.primary,.load-more{background:var(--zy-brand-600,#1A645E);color:#fff;font-size:26rpx;padding:16rpx 28rpx;border-radius:16rpx}.load-more{margin:28rpx auto}.footnote{display:block;text-align:center;color:var(--zy-text,#4a5f54);font-size:22rpx;margin-top:40rpx}@media(min-width:680px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(min-width:1050px){.grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
</style>
