<template>
 <view class="detail-page">
  <view class="detail-nav"><button class="text-btn" @click="goBack">‹ 返回社区</button><text>织愈 · 创作故事</text><button class="text-btn" @click="openFavorites">我的收藏</button></view>
  <view v-if="loading&&!work" class="detail-loading"><view class="zy-skeleton skeleton-cover"/><view class="zy-skeleton skeleton-line"/><text>正在展开这份创作…</text></view>
  <view v-else-if="notFound||error" class="detail-empty"><image src="/static/xiaozhi-caring.png" mode="aspectFit"/><text class="empty-title">{{notFound?'这份作品暂时无法查看':'作品暂时没有加载成功'}}</text><text>{{notFound?'作品可能已撤下，或仅作者自己可见。':'稍后重试即可继续查看。'}}</text><button class="primary" @click="notFound?goBack():loadPage()">{{notFound?'去社区看看':'重新加载'}}</button></view>
  <view v-else-if="work" class="detail-layout">
   <view class="creation-panel">
    <view class="work-media"><MediaVideo v-if="work.mediaType==='video'&&work.videoUrl" :src="work.videoUrl" caption="社区创作视频"/><image v-else-if="work.coverUrl&&!imageFailed" class="work-cover" :src="fullUrl(work.coverUrl)" mode="aspectFit" @click="previewCover" @error="imageFailed=true"/><view v-else class="text-cover"><image src="/static/xiaozhi-happy.png" mode="aspectFit"/><text>{{work.mediaType==='text'?'把日常写成一份小小的创作':'每一份创作，都值得被看见'}}</text></view><text class="media-label">{{work.mediaType==='video'?'创作影像':work.mediaType==='text'?'文字作品':'手作留影'}}</text></view>
    <view class="creation-copy"><view class="work-meta"><text>{{work.shareable?'社区公开作品':'仅本人可见 · 不可分享'}}</text><text>{{formatTime(work.createTime)}}</text></view><text class="work-title">{{work.title}}</text><view class="author"><view class="avatar">{{(work.authorName||'匿')[0]}}</view><view><text class="author-name">{{work.authorName||'匿名同学'}}</text><text class="author-note">让创作留下自己的节奏</text></view></view><view v-if="work.message" class="story"><text class="eyebrow">作品故事</text><text class="story-text" selectable>{{work.message}}</text></view><view class="companion-note"><image src="/static/xiaozhi-avatar.png" mode="aspectFit"/><text>不用做得完美，愿意开始就是一件了不起的小事。</text></view></view>
   </view>
   <view class="conversation-panel">
    <view class="conversation-heading"><view><text class="eyebrow">让温暖发生</text><text class="section-title">留暖评论</text></view><text class="count-badge">{{commentCount}} 条公开留言</text></view><text class="moderation-note">新留言审核通过后公开。待审核或已隐藏的留言仅你自己可见。</text>
    <view class="composer"><textarea v-model="commentText" maxlength="255" :disabled="sending" class="comment-input" placeholder="分享一个具体的欣赏，或留下一句鼓励…"/><view class="composer-footer"><text>{{commentText.length}} / 255</text><button class="primary small" :disabled="!commentText.trim()||sending" :loading="sending" @click="handleSendComment">{{sending?'提交中':'送出温暖'}}</button></view></view>
    <view v-if="commentError" class="error-box"><text>{{commentError}}</text><button class="text-btn" @click="loadComments(failedPage)">重新加载留言</button></view><view v-if="!comments.length&&!commentLoading&&!commentError" class="comment-empty"><image src="/static/xiaozhi-talk.png" mode="aspectFit"/><text>第一句鼓励，就从你开始</text><text class="muted">尊重每一份创作，也保护彼此的隐私。</text></view>
    <view v-for="comment in comments" :key="comment.id" class="comment-card"><view class="avatar small-avatar">{{(comment.authorName||'匿')[0]}}</view><view class="comment-main"><view class="comment-heading"><text>{{comment.authorName}}</text><text class="muted">{{formatTime(comment.createTime)}}</text></view><text class="comment-content" selectable>{{comment.content}}</text><view v-if="comment.mine" class="comment-controls"><text class="comment-status">{{Number(comment.status)===0?'待审核 · 仅你可见':Number(comment.status)===2?'已隐藏 · 仅你可见':'已公开'}}</text><button class="text-btn" :disabled="!!removing" @click="removeComment(comment)">{{removing===comment.id?'撤回中…':'撤回'}}</button></view></view></view>
    <button v-if="hasMoreComments&&!commentError" class="more-btn" :loading="commentLoading" :disabled="commentLoading" @click="loadComments(commentPage+1)">查看更多留言</button><text v-else-if="commentLoading" class="list-end">正在读取留言…</text><text v-else-if="comments.length&&!commentError" class="list-end">已读完所有留言</text>
   </view>
  </view>
  <view v-if="work&&!notFound&&!error" class="action-dock"><view class="action-dock-inner"><button class="dock-action" :class="{active:liked}" :disabled="!!interactionBusy" @click="toggle(1)"><wd-icon name="heart" size="36rpx" :color="liked?'#ae6459':'#718478'"/><text>{{liked?'已点赞':'点赞'}} · {{likeCount}}</text></button><button class="dock-action" :class="{active:favorited}" :disabled="!!interactionBusy" @click="toggle(0)"><wd-icon name="star" size="36rpx" :color="favorited?'#2f7566':'#718478'"/><text>{{favorited?'已收藏':'收藏'}}</text></button><button class="share-trigger" :disabled="!work.shareable||shareBusy" :loading="shareBusy" @click="prepareShare">{{work.shareable?'分享这份创作':'私密作品'}}</button></view></view>
  <text class="footnote">本系统不能替代专业心理咨询</text>
  <view v-if="showShare" class="sheet-mask" @click="showShare=false"><view class="share-sheet" @click.stop><view class="sheet-heading"><text>把这份创作送给朋友</text><button class="text-btn" @click="showShare=false">关闭</button></view><text class="share-title">{{work.title}}</text><text class="muted">仅分享作品入口，朋友需登录后查看。作品撤下后，原链接也无法继续访问。</text>
   <!-- #ifdef H5 -->
   <view class="link-box"><text selectable>{{shareLink}}</text></view><button class="primary" @click="copyLink">复制作品链接</button><button v-if="systemShareSupported" class="more-btn" @click="systemShare">使用系统分享</button>
   <!-- #endif -->
   <!-- #ifdef MP-WEIXIN -->
   <button class="primary" open-type="share">发送给微信好友</button>
   <!-- #endif -->
   <text v-if="shareError" class="error-text">{{shareError}}</text><text v-if="shareNotice" class="share-notice">{{shareNotice}}</text>
  </view></view>
 </view>
</template>
<script setup>
import MediaVideo from '../../components/MediaVideo.vue'
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage, onReachBottom } from '@dcloudio/uni-app'
import { http } from '../../utils/request'
import { API_BASE_URL, TOKEN_KEY } from '../../utils/config'
import { workPath, workLoginUrl, workShareUrl } from '../../utils/work-navigation'
const work=ref(null),loading=ref(false),notFound=ref(false),error=ref(''),imageFailed=ref(false)
const liked=ref(false),favorited=ref(false),likeCount=ref(0),commentCount=ref(0),interactionBusy=ref('')
const comments=ref([]),commentText=ref(''),commentLoading=ref(false),commentError=ref(''),commentPage=ref(0),commentTotal=ref(0),failedPage=ref(1),sending=ref(false),removing=ref('')
const showShare=ref(false),shareBusy=ref(false),shareLink=ref(''),shareError=ref(''),shareNotice=ref(''),systemShareSupported=ref(false)
const hasMoreComments=computed(()=>commentPage.value*10<commentTotal.value)
let workId=''
onLoad(query=>{
 workId=String(query.id||'')
 // #ifdef MP-WEIXIN
 uni.hideShareMenu()
 // #endif
 if(!workPath(workId)){notFound.value=true;return}
 if(!uni.getStorageSync(TOKEN_KEY)){uni.reLaunch({url:workLoginUrl(workId)});return}
 // #ifdef H5
 systemShareSupported.value=typeof navigator.share==='function'
 // #endif
 loadPage()
})
onShareAppMessage(()=>({title:work.value?.shareable?'织愈 · 一份想与你分享的创作':'织愈 · 用创作陪伴日常',path:work.value?.shareable?workPath(workId):'/pages/home/index',imageUrl:'/static/logo.png'}))
onReachBottom(()=>{if(hasMoreComments.value&&!commentLoading.value&&!commentError.value)loadComments(commentPage.value+1)})
function applyWork(data){work.value=data;liked.value=!!data.liked;favorited.value=!!data.favorited;likeCount.value=Number(data.likeCount)||0;commentCount.value=Number(data.commentCount)||0}
async function loadWork(){const data=await http.get(`/api/works/${workId}`);applyWork(data)}
async function loadPage(){if(loading.value)return;loading.value=true;error.value='';try{await loadWork();await loadComments(1)}catch(e){if(e.code===1004){notFound.value=true;work.value=null;showShare.value=false}else error.value=e.message||'加载失败'}finally{loading.value=false}}
async function loadComments(page){if(commentLoading.value)return;commentLoading.value=true;commentError.value='';failedPage.value=page;try{const data=await http.get(`/api/works/${workId}/comments`,{data:{page,size:10}});comments.value=page===1?data.records:comments.value.concat(data.records||[]);commentTotal.value=Number(data.total)||0;commentPage.value=page}catch(e){commentError.value=e.message||'留言加载失败'}finally{commentLoading.value=false}}
async function toggle(type){if(interactionBusy.value)return;interactionBusy.value=type===1?'like':'favorite';try{const old=type===1?liked.value:favorited.value;const data=await http.post(`/api/works/${workId}/like`,{type});if(type===1){liked.value=data.active;likeCount.value=Math.max(0,likeCount.value+(data.active?1:0)-(old?1:0))}else{favorited.value=data.active;uni.showToast({title:data.active?'已保存到我的收藏':'已取消收藏',icon:'none'})}}catch(e){uni.showToast({title:e.message||'操作失败',icon:'none'})}finally{interactionBusy.value=''}}
async function handleSendComment(){const content=commentText.value.trim();if(!content||sending.value)return;sending.value=true;try{await http.post(`/api/works/${workId}/comments`,{content});commentText.value='';await loadComments(1);uni.showToast({title:'已提交，等待审核',icon:'none'})}catch(e){uni.showToast({title:e.message||'提交失败，内容已保留',icon:'none'})}finally{sending.value=false}}
function removeComment(comment){uni.showModal({title:'撤回留言',content:'撤回后正文将被清除，无法恢复。',success:async r=>{if(!r.confirm||removing.value)return;removing.value=comment.id;try{await http.del(`/api/works/${workId}/comments/${comment.id}`);await loadComments(1);await loadWork()}catch(e){uni.showToast({title:e.message||'撤回失败',icon:'none'})}finally{removing.value=''}}})}
async function prepareShare(){if(shareBusy.value||!work.value?.shareable)return;shareBusy.value=true;shareError.value='';shareNotice.value='';try{await loadWork();if(!work.value.shareable){uni.showToast({title:'这份作品目前不可分享',icon:'none'});return}
 // #ifdef H5
 shareLink.value=workShareUrl(workId,window.location)
 // #endif
 showShare.value=true
 }catch(e){if(e.code===1004){notFound.value=true;work.value=null}uni.showToast({title:e.message||'暂时无法分享，请重试',icon:'none'})}finally{shareBusy.value=false}}
function copyLink(){shareError.value='';uni.setClipboardData({data:shareLink.value,success:()=>{shareNotice.value='链接已复制，可以发送给朋友'},fail:()=>{shareError.value='未能复制，请长按上方链接手动复制'}})}
async function systemShare(){
 // #ifdef H5
 shareError.value='';try{await navigator.share({title:'织愈 · 创作故事',url:shareLink.value});shareNotice.value='已调用系统分享'}catch(e){if(e.name!=='AbortError')shareError.value='系统分享不可用，请复制链接'}
 // #endif
}
function fullUrl(url){if(!url)return '';return /^https?:\/\//.test(url)||url.startsWith('/static/')?url:API_BASE_URL+url}
function previewCover(){uni.previewImage({urls:[fullUrl(work.value.coverUrl)]})}
function formatTime(time){return String(time||'').replace('T',' ').slice(0,16)}
function goBack(){uni.switchTab({url:'/pages/work/list'})}
function openFavorites(){uni.navigateTo({url:'/pages/work/favorites'})}
</script>
<style scoped src="./work-detail.css"></style>
