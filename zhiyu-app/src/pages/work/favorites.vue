<template>
	<view :class="zyThemeClass" class="favorites-page"><view class="favorites-hero"><view><ZyPageHead seal="藏" title="留给下一次相遇" subtitle="把触动你的创作，慢慢收好。" /></view><XiaotuanPet :size="76" face="care" aria-hidden="true"/></view>
 <view class="toolbar"><text>{{total}} 份可查看的创作</text><AccessibleButton :disabled="loading" @click="load(1)">刷新收藏</AccessibleButton></view><text class="hint">仅你可见的收藏清单。已下架或不可访问的作品不会展示。</text>
 <StateBlock v-if="loading&&!records.length" type="loading" shape="list" title="正在整理收藏…"/>
 <StateBlock v-else-if="error&&!records.length" type="error" title="收藏没有加载成功" :description="error" action-text="重试" :action-loading="loading" @action="load(failedPage)"/>
 <StateBlock v-else-if="!records.length" type="empty" face="happy" title="还没有收藏的创作" description="遇到喜欢的作品，点一下收藏就能在这里找到。" action-text="去社区发现" @action="community"/>
 <template v-else>
 <view class="grid"><view v-for="item in records" :key="item.id" class="saved-card"><AccessibleButton class="card-open" @click="open(item)"><image v-if="item.coverUrl&&!failedImages[item.id]" class="cover" :src="fullUrl(item.coverUrl)" mode="aspectFill" :alt="'作品封面：'+item.title" @error="failedImages[item.id]=true"/><view v-else class="cover cover-empty"><text class="cover-quote-mark">“</text><text class="cover-quote-title">{{item.title}}</text><text class="cover-quote-meta">{{item.mediaType==='video'?'创作影像 · 暂无封面':'文字与手作 · 未配图'}}</text></view><view class="card-copy"><text class="tag">{{Number(item.visible)===1?'社区作品':'仅本人可见'}} · {{item.mediaType==='video'?'视频':item.mediaType==='text'?'文字':'手作'}}</text><text class="card-title">{{item.title}}</text><text class="excerpt">{{item.message||'点开看看这份创作的故事。'}}</text></view></AccessibleButton><view class="card-footer"><text>收藏于 {{String(item.savedAt||'').slice(0,10)}}</text><AccessibleButton :disabled="!!removing" @click="askRemove(item)">{{removing===item.id?'处理中…':'取消收藏'}}</AccessibleButton></view></view></view>
 <AccessibleButton v-if="records.length<total&&!error" class="load-more" :disabled="loading" :loading="loading" @click="load(page+1)">查看更多收藏</AccessibleButton>
 <StateBlock v-if="error&&records.length" type="error" compact title="更多收藏没有加载成功" :description="error" action-text="重试" @action="load(failedPage)"/>
 </template>
 <ActionSheet :open="!!pendingRemove" title="取消收藏？" description="这会从你的收藏清单移除作品，不会删除作品本身。" :actions="[{key:'confirm',label:'确认取消收藏',tone:'danger',busy:!!removing}]" cancel-text="先留着" :busy="!!removing" @select="confirmRemove" @close="pendingRemove=null"/>
 <Toast/>
 </view>
</template>
<script setup>
import ZyPageHead from '../../components/ZyPageHead.vue'
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
.favorites-page{max-width:1180px;margin:auto;padding:32rpx 32rpx 80rpx;background:var(--zy-bg,#f1f4f1);color:var(--zy-title,#111816);box-sizing:border-box}.favorites-hero{background:transparent;border:0;border-bottom:1rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);padding:8rpx 0 32rpx;display:flex;align-items:center;justify-content:space-between;gap:20rpx}.eyebrow,.title,.subtitle{display:block}.eyebrow{font-size:var(--zy-text-micro, 22rpx);letter-spacing:2px;color:var(--zy-muted,#6e7b75)}.title{font-family:var(--zy-font-display,serif);font-size:56rpx;font-weight:500;margin:12rpx 0}.subtitle,.hint{font-size:var(--zy-text-caption, 24rpx);color:var(--zy-muted,#6e7b75);line-height:1.8}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:20rpx;margin-top:24rpx;font-size:var(--zy-text-caption, 24rpx);color:var(--zy-text,#42524c)}.toolbar button,.card-footer button{font-size:var(--zy-text-caption, 24rpx);color:var(--zy-brand-700,#183369);background:transparent;padding:10rpx 0;margin:0}button::after{border:0}button:focus-visible{outline:3px solid var(--zy-brand-700,#183369);outline-offset:3px}.hint{display:block;margin:12rpx 0 24rpx}.grid{display:grid;grid-template-columns:minmax(0,1fr);gap:32rpx}.saved-card{background:transparent;border:0;border-bottom:1rpx solid var(--zy-border,#d3ded8);border-radius: var(--zy-radius-control, 18rpx);padding-bottom:12rpx;overflow:visible}.card-open{display:block;text-align:left;background:transparent;padding:0;margin:0;border-radius: var(--zy-radius-control, 18rpx);line-height:1.6;width:100%}.cover{width:100%;height:380rpx;display:block}.cover-empty{display:flex;align-items:flex-start;justify-content:center;flex-direction:column;gap:12rpx;padding:32rpx 36rpx;background:var(--zy-bg,#f1f4f1);border-left:3rpx solid var(--zy-care,#a64842);color:var(--zy-muted,#6e7b75);font-size:var(--zy-text-caption, 24rpx);box-sizing:border-box}.cover-quote-mark{font-family:var(--zy-font-display,serif);font-size:64rpx;line-height:1;color:var(--zy-care,#a64842)}.cover-quote-title{font-family:var(--zy-font-display,serif);font-size:38rpx;font-weight:500;line-height:1.45;color:var(--zy-title,#111816);display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}.cover-quote-meta{font-size:var(--zy-text-micro, 22rpx);letter-spacing:1px;color:var(--zy-muted,#6e7b75)}.card-copy{padding:22rpx 0}.tag{font-size:var(--zy-text-micro, 22rpx);letter-spacing:1rpx;color:var(--zy-muted,#6e7b75)}.card-title{display:block;font-family:var(--zy-font-display,serif);font-size:34rpx;font-weight:500;margin:10rpx 0;overflow-wrap:anywhere;color:var(--zy-title,#111816)}.excerpt{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;color:var(--zy-text,#42524c);font-size:var(--zy-text-caption, 24rpx);line-height:1.8;overflow-wrap:anywhere}.card-footer{display:flex;align-items:center;justify-content:space-between;padding:12rpx 0 0;border-top:0;font-size:var(--zy-text-micro, 22rpx);color:var(--zy-muted,#6e7b75)}.primary,.load-more{background:var(--zy-brand-600,#24478f);color:#fff;font-size:var(--zy-text-caption, 24rpx);padding:16rpx 28rpx;border-radius: var(--zy-radius-control, 18rpx)}.load-more{margin:32rpx auto;background:transparent;border:1rpx solid var(--zy-brand-700,#183369);color:var(--zy-brand-700,#183369)}.footnote{display:block;text-align:center;color:var(--zy-muted,#6e7b75);font-size:var(--zy-text-micro, 22rpx);margin-top:40rpx}@media(min-width:680px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(min-width:1050px){.grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
</style>
