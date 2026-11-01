<template>
 <view class="growth-page" :class="{'motion-reduced':reducedMotion}">
  <view class="growth-row growth-spread"><view class="start-brand"><image class="brand" src="/static/logo.png"/><view><text>织愈 · 此刻</text><text class="start-brand-sub">MAKE ROOM FOR YOURSELF</text></view></view><button class="motion-toggle" :aria-pressed="reducedMotion" @click="toggleMotion">{{reducedMotion?'减少动效：开':'减少动效：关'}}</button></view>
  <view class="growth-hero"><image class="start-art" src="/static/brand/weave-scene.png" mode="aspectFit"/><text class="growth-eyebrow">ZHIYU · 留一点时间给自己</text><text class="growth-title">先做一件，<br/>让自己舒服的小事。</text><text class="growth-subtitle">不必准备很多，先为自己留出一小段时间。</text></view>
  <view class="moment-card"><text class="growth-card-title">此刻，有几分钟？</text><view class="time-tabs"><button v-for="m in [1,3,5,15]" :key="m" :class="{selected:minutes===m}" :aria-pressed="minutes===m" @click="minutes=m">{{m}} 分钟</button></view><text class="mood-label">此刻心情（可选）</text><view class="mood-row"><button v-for="m in moodOptions" :key="m.code" :class="{selected:moodCode===m.code}" :aria-pressed="moodCode===m.code" @click="toggleMood(m.code)">{{m.label}}</button></view><view class="moment-copy" :key="minutes"><text class="moment-title">{{suggestions[minutes]}}</text><text class="growth-muted">{{minutes===15?'数字配色练习 · 完成后记录到成长旅程':'无需准备材料 · 随时可以暂停'}}</text></view><view v-if="shortLoading||shortItems.length||shortError" class="short-suggest" aria-label="短练习推荐"><view class="short-suggest-head"><text class="short-suggest-title">此刻的短练习</text><text class="short-suggest-hint">{{shortLoading?'正在挑选…':'按分钟与心情推荐'}}</text></view><view v-if="shortLoading" class="short-skeleton zy-skeleton" aria-hidden="true"/><template v-else><button v-for="(item,index) in shortItems" :key="item.id" class="short-item" :class="{primary:index===0}" @click="startShort(item)"><view class="short-item-main"><view class="short-item-tags"><text class="short-kind">{{practiceKindLabel(item.kind)}}</text><text v-if="index===0" class="short-recommend">推荐</text><text class="short-minutes">{{item.minutes}} 分钟</text></view><text class="short-title">{{item.title}}</text><text class="short-subtitle">{{item.subtitle}}</text></view><text class="short-arrow">›</text></button></template><button v-if="shortAll.length>3&&!shortLoading" class="short-more" @click="showAllShort=!showAllShort">{{showAllShort?'收起短练习':'查看全部 '+shortAll.length+' 条短练习'}}</button><text v-if="shortError" class="practice-error" role="alert">{{shortError}}</text></view><button v-if="minutes===15" class="growth-button" :disabled="practiceStarting" @click="startHeritagePractice">{{practiceStarting?'正在准备练习…':'开始 15 分钟非遗手作 →'}}</button><button v-else class="growth-button" @click="go('mindfulness/index?minutes='+minutes)">开始 {{minutes}} 分钟呼吸练习 →</button><text v-if="practiceError" class="practice-error" role="alert">{{practiceError}}</text></view>
  <XiaozhiCard @open="openChat()"/><button class="growth-card entry" @click="openMusic()"><text class="entry-number">♫</text><view class="entry-copy"><text class="growth-card-title">听一段音乐，松一口气</text><text class="growth-muted">自主播放 · 定时停止</text></view><text class="arrow">›</text></button><text class="section-label">也可以这样开始</text>
  <button v-for="item in primary" :key="item.path" class="growth-card entry" @click="go(item.path)"><view class="entry-number">{{item.number}}</view><view class="entry-copy"><text class="growth-card-title">{{item.title}}</text><text class="growth-muted">{{item.desc}}</text></view><text class="arrow">›</text></button>
  <text class="section-label">探索与记录</text>
  <view class="growth-grid"><button v-for="item in secondary" :key="item.path" class="growth-card explore" @click="go(item.path)"><text class="growth-card-title">{{item.title}}</text><text class="growth-muted">{{item.desc}}</text><text class="explore-arrow">↗</text></button></view>
  <text class="growth-foot">本系统不能替代专业心理咨询</text>
 </view>
</template>
<script setup>
function openChat(){uni.navigateTo({url:'/pages/emotion/chat'})}
function openMusic(){uni.navigateTo({url:'/pages/music/index'})}
function openCredits(){uni.navigateTo({url:'/pages/music/credits'})}
import XiaozhiCard from "../../components/XiaozhiCard.vue"
import{computed,ref,watch}from'vue';import{onLoad}from'@dcloudio/uni-app';import{useInteraction}from'../../composables/useInteraction';import{createPracticeSession,practiceKindLabel}from'../../utils/practice-session'
import{request}from'../../utils/request';import{useUserStore}from'../../stores/user'
const{reducedMotion,toggleMotion}=useInteraction();const minutes=ref(3),suggestions={1:'先停一小会儿',3:'给呼吸留一些空间',5:'慢慢回到自己的节奏',15:'给自己一段完整的手作时间'}
const moodOptions=[{code:'calm',label:'平静'},{code:'tired',label:'有点累'},{code:'low',label:'不太好'},{code:'ok',label:'一般'},{code:'good',label:'还不错'}]
const moodCode=ref(''),practiceStarting=ref(false),practiceError=ref('')
const userStore=useUserStore();const shortAll=ref([]),showAllShort=ref(false),shortLoading=ref(false),shortError=ref('');let shortSeq=0
const shortItems=computed(()=>showAllShort.value?shortAll.value:shortAll.value.slice(0,3))
function toggleMood(code){moodCode.value=moodCode.value===code?'':code}
async function loadShortPractices(){
 if(!userStore.isLogin){shortAll.value=[];return}
 const seq=++shortSeq;shortLoading.value=true;shortError.value=''
 try{
  const query=[];if(minutes.value)query.push('minutes='+minutes.value);if(moodCode.value)query.push('mood='+encodeURIComponent(moodCode.value))
  const rows=await request({url:'/api/practice/short-practices'+(query.length?'?'+query.join('&'):'')})
  if(seq===shortSeq)shortAll.value=rows||[]
 }catch(e){
  if(seq===shortSeq){shortAll.value=[];shortError.value=e?.code===1002?'登录后可以获取短练习推荐':(e?.message||'短练习暂时没有准备好')}
 }finally{if(seq===shortSeq)shortLoading.value=false}
}
function startShort(item){
 if(!item||practiceStarting.value)return
 practiceError.value=''
 const query=['id='+encodeURIComponent(item.id)];if(moodCode.value)query.push('mood='+encodeURIComponent(moodCode.value))
 uni.navigateTo({url:'/pages/practice/short?'+query.join('&')})
}
watch([minutes,moodCode],loadShortPractices)
onLoad(loadShortPractices)
async function startHeritagePractice(){if(practiceStarting.value)return;practiceStarting.value=true;practiceError.value='';try{const session=await createPracticeSession({kind:'HERITAGE',minutesBudget:15,moodCode:moodCode.value||undefined,heritagePatternId:'971201'});uni.navigateTo({url:'/pages/heritage/studio?id=971201&sessionId='+encodeURIComponent(session.practiceSessionId)})}catch(e){practiceError.value=(e&&e.message)||'练习暂时没有准备好，请重试'}finally{practiceStarting.value=false}}
const primary=[{number:'01',title:'和小织聊聊',desc:'把此刻的感受慢慢说出来',path:'emotion/chat'},{number:'02',title:'从第一件手作开始',desc:'材料准备 · 分步引导 · 接着上次做',path:'starter/index'}]
const secondary=[{title:'校内支持',desc:'江西师大 · 官方联系渠道',path:'support/index'},{title:'自我了解',desc:'固定题目 · 私密保存',path:'assessment/index'},{title:'成长旅程',desc:'看见每一次记录',path:'journey/index'},{title:'活动与预约',desc:'工作坊 · 课程 · 指导',path:'programs/index'},{title:'自由绘色',desc:'纹样 · AI 辅助线稿',path:'drawing/index'},{title:'材料探索',desc:'工艺关系与安全依据',path:'materials/index'},{title:'临摹辅助',desc:'相机画面叠加线稿',path:'tracing/index'}]
function go(path){uni.navigateTo({url:'/pages/'+path})}
</script>
<style src="../../styles/growth.css"></style>
<style scoped>.brand{width:86rpx;height:86rpx}.motion-toggle{margin:0;padding:4rpx 20rpx;font-size:22rpx;color:var(--zy-content,#62756e);background:var(--zy-card-bg,#fff);border:1rpx solid var(--zy-border,#dfe8e5);border-radius:999rpx}.growth-hero{padding-top:28rpx}.moment-card{padding:30rpx;border:1rpx solid #cfe1d9;border-radius:26rpx;background:linear-gradient(130deg,var(--zy-card-bg,#fff),var(--zy-bg-tint-mint,#eaf5ef));margin-bottom:36rpx}.time-tabs{display:flex;background:var(--zy-bg,#f3f6f4);padding:8rpx;border-radius:16rpx;gap:8rpx}.time-tabs button{flex:1;background:transparent;color:#58736a;font-size:26rpx;line-height:2.5;border-radius:12rpx}.time-tabs button.selected{background:#2f7566;color:white;box-shadow:0 3rpx 10rpx #2f756630}.moment-copy{padding-top:26rpx;animation:moment-in .2s ease-out}.moment-title{display:block;font-size:30rpx;font-weight:600;margin-bottom:8rpx}.section-label{display:block;font-size:25rpx;color:var(--zy-sub,#73857b);margin:32rpx 0 20rpx}.entry{display:flex;gap:24rpx;align-items:center;width:100%;text-align:left;line-height:1.6}.entry-number{font-size:28rpx;color:#2f7566;background:var(--zy-bg-tint-mint,#edf5f3);border-radius:16rpx;padding:20rpx}.entry-copy{flex:1}.entry .growth-card-title{margin-bottom:6rpx}.arrow{color:#8ba4a5;font-size:40rpx}.explore{position:relative;text-align:left;line-height:1.7;padding:26rpx 26rpx 46rpx;margin:0;width:100%}.explore .growth-card-title{font-size:29rpx}.explore-arrow{position:absolute;bottom:10rpx;right:20rpx;color:#638e7f}button::after{border:0}@keyframes moment-in{from{opacity:.5;transform:translateY(5px)}to{opacity:1;transform:none}}</style>

<style scoped>.growth-page{background:#f6f7f2;color:#2e4936;padding-top:36rpx}.start-brand{display:flex;gap:18rpx;align-items:center;font-size:29rpx;font-weight:550;letter-spacing:2rpx}.start-brand .brand{width:66rpx;height:66rpx}.start-brand-sub{display:block;font-size:13rpx;letter-spacing:2rpx;color:#96a18a;margin-top:6rpx}.growth-hero{position:relative;overflow:hidden;padding:44rpx 0 38rpx;min-height:245rpx}.growth-hero .growth-title{position:relative;font-family:'Songti SC','SimSun',serif;font-size:43rpx;font-weight:500;line-height:1.5;max-width:80%;color:#375038}.growth-hero .growth-subtitle{position:relative;max-width:75%;font-size:22rpx;color:#94a087}.growth-eyebrow{font-size:16rpx;letter-spacing:3rpx;color:#95a183}.start-art{position:absolute;width:390rpx;height:260rpx;right:-160rpx;bottom:0;opacity:.45}.moment-card{background:#e8eee1;border-color:#dbe4d3;padding:28rpx;border-radius:24rpx}.moment-card .growth-card-title{font-size:28rpx;font-weight:500;color:#48613c}.time-tabs{background:#f3f5ec}.time-tabs button.selected{background:#476d4e;box-shadow:none}.time-tabs button{color:#8a9c80}.moment-title{font-family:'Songti SC','SimSun',serif;color:#4e6441;font-weight:500}.growth-muted{font-size:21rpx;color:#98a58d}.growth-button{background:#3a6549;font-size:25rpx;border-radius:14rpx}.section-label{color:#89987b;font-size:21rpx;letter-spacing:3rpx;margin-top:36rpx}.entry{border-color:#e4e9dd;border-radius:20rpx;padding:26rpx}.entry-number{background:#f0f3e9;color:#9aac8b;font-size:24rpx}.entry .growth-card-title{font-size:29rpx;color:#4c6241;font-weight:500}.explore{background:#fdfefa;border-color:#e3e9da;border-radius:20rpx;padding:24rpx 24rpx 40rpx}.explore .growth-card-title{font-size:27rpx;color:#4d6541;font-weight:500}.motion-toggle{color:#8b9b7a;font-size:17rpx;padding:6rpx 12rpx}.growth-foot{color:#a2ad93;font-size:18rpx}</style>
<style scoped>.growth-muted,.growth-hero .growth-subtitle,.section-label,.growth-foot,.motion-toggle{color:#667c59}.growth-muted,.growth-foot{font-size:22rpx}.growth-hero .growth-subtitle{font-size:24rpx}.time-tabs button{color:#697e5e}.time-tabs button.selected{color:#fff}</style>

<style scoped>.growth-page{background:var(--zy-bg)}.growth-hero .growth-title{font-family:var(--zy-sans);font-weight:600;color:var(--zy-title)}.growth-button,.time-tabs button.selected{background:var(--zy-primary)}.moment-card{background:var(--zy-bg-tint-mint);border-color:var(--zy-border)}.mood-label{display:block;margin:18rpx 0 8rpx;font-size:22rpx;color:var(--zy-sub,#73857b)}.mood-row{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8rpx}.mood-row button{min-width:0;min-height:44px;margin:0;padding:0 4rpx;border:1rpx solid var(--zy-border,#dfe8e5);border-radius:12rpx;background:var(--zy-card-bg,#fff);color:var(--zy-content,#62756e);font-size:21rpx;line-height:44px}.mood-row button.selected{border-color:var(--zy-primary);background:var(--zy-bg-tint-mint);color:var(--zy-primary)}.practice-error{display:block;margin-top:10rpx;font-size:22rpx;line-height:1.6;color:#a4453f}</style>

<style scoped>
.short-suggest { margin-top: 24rpx; padding-top: 20rpx; border-top: 1rpx dashed var(--zy-border, #dfe8e5); }
.short-suggest-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; margin-bottom: 12rpx; }
.short-suggest-title { font-size: 24rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.short-suggest-hint { font-size: 19rpx; color: var(--zy-sub, #73857b); }
.short-skeleton { display: block; height: 128rpx; border-radius: 16rpx; }
.short-item { display: flex; align-items: center; gap: 12rpx; width: 100%; margin: 12rpx 0 0; padding: 20rpx 22rpx; border: 1rpx solid var(--zy-border, #dfe8e5); border-radius: 16rpx; background: var(--zy-card-bg, #fff); text-align: left; line-height: 1.5; }
.short-item.primary { border-color: var(--zy-primary, #2f7566); background: var(--zy-bg-tint-mint, #eaf5ef); }
.short-item-main { flex: 1; min-width: 0; }
.short-item-tags { display: flex; align-items: center; gap: 10rpx; margin-bottom: 6rpx; }
.short-kind { padding: 3rpx 10rpx; border-radius: 999rpx; background: var(--zy-bg, #f3f6f4); color: var(--zy-content, #586e62); font-size: 18rpx; }
.short-recommend { padding: 3rpx 10rpx; border-radius: 999rpx; background: var(--zy-accent, #bb914d); color: #fff; font-size: 18rpx; }
.short-minutes { font-size: 19rpx; color: var(--zy-sub, #73857b); }
.short-title { display: block; font-size: 26rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.short-subtitle { display: block; margin-top: 4rpx; font-size: 20rpx; line-height: 1.6; color: var(--zy-sub, #73857b); }
.short-arrow { flex-shrink: 0; font-size: 34rpx; color: var(--zy-primary, #2f7566); }
.short-more { margin: 12rpx 0 0; background: transparent; color: var(--zy-primary, #2f7566); font-size: 21rpx; line-height: 2.2; }
.short-more::after { border: 0; }
@media (prefers-reduced-motion: reduce) {
 .short-item { transition: none; }
}
</style>
