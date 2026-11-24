<template>
	<view class="growth-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
		<view class="zy-tabpage-top" aria-hidden="true"></view>
  <ZyPageHead seal="作" title="开始手作" subtitle="不必准备很多，先为自己留出一小段时间。"><template #action><AccessibleButton class="motion-toggle" :aria-pressed="reducedMotion" :aria-label="reducedMotion?'减少动效：已开启':'减少动效：已关闭'" @click="toggleMotion">{{reducedMotion?'动效 少':'动效 常'}}</AccessibleButton></template></ZyPageHead>

  <view class="moment-card"><text class="growth-card-title">此刻，有几分钟？</text><view class="time-tabs"><AccessibleButton v-for="m in [1,3,5,15]" :key="m" :class="{selected:minutes===m}" :aria-pressed="minutes===m" @click="minutes=m">{{m}} 分钟</AccessibleButton></view><text class="mood-label">此刻心情（可选）</text><view class="mood-row"><AccessibleButton v-for="m in moodOptions" :key="m.code" :class="['mood-chip',{selected:moodCode===m.code}]" :aria-pressed="moodCode===m.code" @click="toggleMood(m.code)"><MoodEmoji :kind="m.kind" :size="40"/><text>{{m.label}}</text></AccessibleButton></view><view class="moment-copy" :key="minutes"><text class="moment-title">{{suggestions[minutes]}}</text><text class="growth-muted">{{minutes===15?'数字配色练习 · 完成后记录到成长旅程':'无需准备材料 · 随时可以暂停'}}</text></view><view v-if="shortLoading||shortItems.length||shortError" class="short-suggest" aria-label="短练习推荐"><view class="short-suggest-head"><text class="short-suggest-title">此刻的短练习</text><text class="short-suggest-hint">{{shortLoading?'正在挑选…':'按分钟与心情推荐'}}</text></view><view v-if="shortLoading" class="short-skeleton zy-skeleton" aria-hidden="true"/><template v-else><AccessibleButton v-for="(item,index) in shortItems" :key="item.id" class="short-item" :class="{primary:index===0}" @click="startShort(item)"><view class="short-item-main"><view class="short-item-tags"><text class="short-kind">{{practiceKindLabel(item.kind)}}</text><text v-if="index===0" class="short-recommend">推荐</text><text class="short-minutes">{{item.minutes}} 分钟</text></view><text class="short-title">{{item.title}}</text><text class="short-subtitle">{{item.subtitle}}</text></view><text class="short-arrow">›</text></AccessibleButton></template><AccessibleButton v-if="shortAll.length>3&&!shortLoading" class="short-more" @click="showAllShort=!showAllShort">{{showAllShort?'收起短练习':'查看全部 '+shortAll.length+' 条短练习'}}</AccessibleButton><text v-if="shortError" class="practice-error" role="alert">{{shortError}}</text></view><AccessibleButton v-if="minutes===15" class="growth-button" :disabled="practiceStarting" @click="startHeritagePractice">{{practiceStarting?'正在准备练习…':'开始 15 分钟非遗手作 →'}}</AccessibleButton><AccessibleButton v-else class="growth-button" @click="go('mindfulness/index?minutes='+minutes)">开始 {{minutes}} 分钟呼吸练习 →</AccessibleButton><text v-if="practiceError" class="practice-error" role="alert">{{practiceError}}</text></view>
  <XiaozhiCard @open="openChat()"/><AccessibleButton class="growth-card entry entry--music" @click="openMusic()"><view class="entry-thumb" aria-hidden="true"><ZyIcon name="sound" :size="40" color="#FFFFFF" /></view><view class="entry-copy"><text class="growth-card-title">听一段音乐，松一口气</text><text class="growth-muted">自主播放 · 定时停止</text></view><text class="arrow">›</text></AccessibleButton><text class="section-label">也可以这样开始</text>
  <AccessibleButton v-for="item in primary" :key="item.path" class="growth-card entry" @click="go(item.path)"><view class="entry-stitch zy-stitch-h" aria-hidden="true"/><view class="entry-number zy-t-serif">{{item.number}}</view><view class="entry-copy"><text class="growth-card-title">{{item.title}}</text><text class="growth-muted">{{item.desc}}</text></view><text class="arrow">›</text></AccessibleButton>
  <text class="section-label">探索与记录</text>
  <view class="growth-grid"><AccessibleButton v-for="(item, i) in secondary" :key="item.path" class="growth-card explore" :class="'tone-' + (i % 4)" @click="go(item.path)"><view class="explore-icon" aria-hidden="true"><ZyIcon :name="item.icon" :size="34" :tone="['brand','pine','gold','cinnabar'][i % 4]" /></view><text class="growth-card-title">{{item.title}}</text><text class="growth-muted">{{item.desc}}</text><ZyIcon name="arrow" :size="28" tone="brand" class="explore-arrow" /></AccessibleButton></view>
 </view>
</template>
<script setup>
import MoodEmoji from '../../components/MoodEmoji.vue'
function openChat(){uni.navigateTo({url:'/pages/emotion/chat'})}
function openMusic(){uni.navigateTo({url:'/pages/music/index'})}
function openCredits(){uni.navigateTo({url:'/pages/music/credits'})}
import XiaozhiCard from "../../components/XiaozhiCard.vue"
import AccessibleButton from "../../components/AccessibleButton.vue"
import ZyIcon from "../../components/ZyIcon.vue"
import ZyPageHead from "../../components/ZyPageHead.vue"
import{computed,ref,watch}from'vue';import{onLoad}from'@dcloudio/uni-app';import{useInteraction}from'../../composables/useInteraction';import{createPracticeSession,practiceKindLabel}from'../../utils/practice-session'
import{request}from'../../utils/request';import{useUserStore}from'../../stores/user'
const{reducedMotion,toggleMotion}=useInteraction();const minutes=ref(3),suggestions={1:'先停一小会儿',3:'给呼吸留一些空间',5:'慢慢回到自己的节奏',15:'给自己一段完整的手作时间'}
const moodOptions=[{code:'calm',label:'平静',kind:1},{code:'tired',label:'有点累',kind:3},{code:'low',label:'不太好',kind:4},{code:'ok',label:'一般',kind:2},{code:'good',label:'还不错',kind:0}]
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
async function startHeritagePractice(){if(practiceStarting.value)return;practiceStarting.value=true;practiceError.value='';try{const session=await createPracticeSession({kind:'HERITAGE',minutesBudget:15,moodCode:moodCode.value||undefined,heritagePatternId:'971201'});uni.navigateTo({url:'/sub-heritage/pages/heritage/studio?id=971201&sessionId='+encodeURIComponent(session.practiceSessionId)})}catch(e){practiceError.value=(e&&e.message)||'练习暂时没有准备好，请重试'}finally{practiceStarting.value=false}}
const primary=[{number:'01',title:'和小织聊聊',desc:'把此刻的感受慢慢说出来',path:'emotion/chat'},{number:'02',title:'从第一件手作开始',desc:'材料准备 · 分步引导 · 接着上次做',path:'starter/index'}]
const secondary=[{title:'校内支持',icon:'phone',desc:'江西师大 · 官方联系渠道',path:'support/index'},{title:'自我了解',icon:'eye',desc:'固定题目 · 私密保存',path:'assessment/index'},{title:'成长旅程',icon:'clock',desc:'看见每一次记录',path:'journey/index'},{title:'活动与预约',icon:'calendar',desc:'工作坊 · 课程 · 指导',path:'/sub-programs/pages/programs/index'},{title:'自由绘色',icon:'edit',desc:'纹样 · AI 辅助线稿',path:'drawing/index'},{title:'材料探索',icon:'search',desc:'工艺关系与安全依据',path:'materials/index'},{title:'临摹辅助',icon:'picture',desc:'相机画面叠加线稿',path:'tracing/index'}]
function go(path){uni.navigateTo({url:path.startsWith('/')?path:'/pages/'+path})}
</script>

<style scoped>.brand{width:86rpx;height:86rpx}.motion-toggle{margin:0;padding:4rpx 20rpx;font-size:var(--zy-text-micro, 22rpx);color:var(--zy-text);background:var(--zy-card-bg);border:1rpx solid var(--zy-border);border-radius:999rpx}.growth-hero{padding-top:28rpx}.moment-card{padding:30rpx;border:1rpx solid var(--zy-border);border-radius:26rpx;background:linear-gradient(130deg,var(--zy-card-bg),var(--zy-bg-tint-mint));margin-bottom:36rpx}.time-tabs{display:flex;background:var(--zy-bg);padding:8rpx;border-radius:16rpx;gap:8rpx}.time-tabs button{flex:1;background:transparent;color:var(--zy-text);font-size:var(--zy-text-caption, 24rpx);line-height:2.5;border-radius:12rpx}.time-tabs button.selected{background:var(--zy-brand-700);color:#fff;box-shadow:none}.moment-copy{padding-top:26rpx;animation:moment-in .2s ease-out}.moment-title{display:block;font-size:var(--zy-text-body-lg, 30rpx);font-weight:600;margin-bottom:8rpx}.section-label{display:block;font-size:var(--zy-text-caption, 24rpx);color:var(--zy-muted);margin:32rpx 0 20rpx}.entry{display:flex;gap:24rpx;align-items:center;width:100%;text-align:left;line-height:1.6}.entry-number{font-size:var(--zy-text-body, 28rpx);color:var(--zy-brand-700);background:var(--zy-bg-tint-mint);border-radius:16rpx;padding:20rpx}.entry-copy{flex:1}.entry .growth-card-title{margin-bottom:6rpx}.arrow{color:var(--zy-muted);font-size:var(--zy-text-title-lg, 40rpx)}.explore{position:relative;text-align:left;line-height:1.7;padding:26rpx 26rpx 46rpx;margin:0;width:100%}.explore .growth-card-title{font-size:var(--zy-text-body-lg, 30rpx)}.explore-arrow{position:absolute;bottom:10rpx;right:20rpx;color:var(--zy-brand-700)}button::after{border:0}@keyframes moment-in{from{opacity:.5;transform:translateY(5px)}to{opacity:1;transform:none}}</style>

<style scoped>.growth-page{background:var(--zy-bg);color:var(--zy-text);padding-top:32rpx}.start-brand{display:flex;gap:18rpx;align-items:center;font-size:var(--zy-text-body-lg, 30rpx);font-weight:500;letter-spacing:2rpx}.start-brand .brand{width:66rpx;height:66rpx}.start-brand-sub{display:block;font-size:var(--zy-text-micro, 22rpx);letter-spacing:2rpx;color:var(--zy-muted);margin-top:6rpx}.growth-hero{position:relative;overflow:hidden;padding:44rpx 0 38rpx;min-height:245rpx}.growth-hero .growth-title{position:relative;font-family:var(--zy-font-display);font-size:var(--zy-text-title-lg, 40rpx);font-weight:500;line-height:1.5;max-width:80%;color:var(--zy-title)}.growth-hero .growth-subtitle{position:relative;max-width:75%;font-size:var(--zy-text-micro, 22rpx);color:var(--zy-muted)}.growth-eyebrow{font-size:var(--zy-text-micro, 22rpx);letter-spacing:3rpx;color:var(--zy-muted)}.start-art{position:absolute;width:390rpx;height:260rpx;right:-160rpx;bottom:0;opacity:.45}.moment-card{background:var(--zy-bg-tint-mint);border-color:var(--zy-border);padding:28rpx;border-radius:24rpx}.moment-card .growth-card-title{font-size:var(--zy-text-body, 28rpx);font-weight:500;color:var(--zy-title)}.time-tabs{background:var(--zy-bg-sunken)}.time-tabs button.selected{background:var(--zy-brand-700);box-shadow:none}.time-tabs button{color:var(--zy-muted)}.moment-title{font-family:var(--zy-font-display);color:var(--zy-title);font-weight:500}.growth-muted{font-size:var(--zy-text-micro, 22rpx);color:var(--zy-muted)}.growth-button{background:var(--zy-brand-700);font-size:var(--zy-text-caption, 24rpx);border-radius:14rpx}.section-label{color:var(--zy-muted);font-size:var(--zy-text-micro, 22rpx);letter-spacing:3rpx;margin-top:36rpx}.entry{border-color:var(--zy-border);border-radius:20rpx;padding:26rpx}.entry-number{background:var(--zy-bg-sunken);color:var(--zy-muted);font-size:var(--zy-text-caption, 24rpx)}.entry .growth-card-title{font-size:var(--zy-text-body-lg, 30rpx);color:var(--zy-title);font-weight:500}.explore{background:var(--zy-card-bg);border-color:var(--zy-border);border-radius:20rpx;padding:24rpx 24rpx 40rpx}.explore .growth-card-title{font-size:var(--zy-text-body, 28rpx);color:var(--zy-title);font-weight:500}.motion-toggle{color:var(--zy-muted);font-size:var(--zy-text-micro, 22rpx);padding:6rpx 12rpx}.growth-foot{color:var(--zy-muted);font-size:var(--zy-text-micro, 22rpx)}</style>
<style scoped>.growth-muted,.growth-hero .growth-subtitle,.section-label,.growth-foot,.motion-toggle{color:var(--zy-muted)}.growth-muted,.growth-foot{font-size:var(--zy-text-micro, 22rpx)}.growth-hero .growth-subtitle{font-size:var(--zy-text-caption, 24rpx)}.time-tabs button{color:var(--zy-text)}.time-tabs button.selected{color:#fff}</style>

<style scoped>.growth-page{background:var(--zy-bg)}.growth-hero .growth-title{font-family:var(--zy-sans);font-weight:600;color:var(--zy-title)}.growth-button,.time-tabs button.selected{background:var(--zy-brand-700)}.moment-card{background:var(--zy-bg-tint-mint);border-color:var(--zy-border)}.mood-label{display:block;margin:18rpx 0 8rpx;font-size:var(--zy-text-micro, 22rpx);color:var(--zy-muted)}.mood-row{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8rpx}.mood-row button{min-width:0;min-height:44px;margin:0;padding:0 4rpx;border:1rpx solid var(--zy-border,#dfe8e5);border-radius:12rpx;background:var(--zy-card-bg,#fff);color:var(--zy-content,#62756e);font-size:var(--zy-text-micro, 22rpx);line-height:44px}.mood-row button.selected{border-color:var(--zy-brand-700);background:var(--zy-bg-tint-mint);color:var(--zy-brand-700)}.practice-error{display:block;margin-top:10rpx;font-size:var(--zy-text-micro, 22rpx);line-height:1.6;color:var(--zy-care, var(--zy-care, #a64842))}</style>

<style scoped>
.short-suggest { margin-top: 24rpx; padding-top: 20rpx; border-top: 1rpx dashed var(--zy-border, #dfe8e5); }
.short-suggest-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12rpx; margin-bottom: 12rpx; }
.short-suggest-title { font-size:var(--zy-text-caption, 24rpx); font-weight: 600; color: var(--zy-title, #233d34); }
.short-suggest-hint { font-size:var(--zy-text-micro, 22rpx); color: var(--zy-muted); }
.short-skeleton { display: block; height: 128rpx; border-radius: 16rpx; }
.short-item { display: flex; align-items: center; gap: 12rpx; width: 100%; margin: 12rpx 0 0; padding: 20rpx 22rpx; border: 1rpx solid var(--zy-border, #dfe8e5); border-radius: 16rpx; background: var(--zy-card-bg, #fff); text-align: left; line-height: 1.5; }
.short-item.primary { border-color: var(--zy-primary, var(--zy-brand-600, #24478f)); background: var(--zy-bg-tint-mint, #eef3fb); }
.short-item-main { flex: 1; min-width: 0; }
.short-item-tags { display: flex; align-items: center; gap: 10rpx; margin-bottom: 6rpx; }
.short-kind { padding: 3rpx 10rpx; border-radius: 999rpx; background: var(--zy-bg, #f3f6f4); color: var(--zy-content, #4a5f54); font-size:var(--zy-text-micro, 22rpx); }
.short-recommend { padding: 3rpx 10rpx; border-radius: 999rpx; background: var(--zy-accent, var(--zy-accent, #7a5c12)); color: #fff; font-size:var(--zy-text-micro, 22rpx); }
.short-minutes { font-size:var(--zy-text-micro, 22rpx); color: var(--zy-muted); }
.short-title { display: block; font-size:var(--zy-text-caption, 24rpx); font-weight: 600; color: var(--zy-title, #233d34); }
.short-subtitle { display: block; margin-top: 4rpx; font-size:var(--zy-text-micro, 22rpx); line-height: 1.6; color: var(--zy-muted); }
.short-arrow { flex-shrink: 0; font-size:var(--zy-text-title, 32rpx); color: var(--zy-brand-700, var(--zy-brand-600, #24478f)); }
.short-more { margin: 12rpx 0 0; background: transparent; color: var(--zy-brand-700, var(--zy-brand-600, #24478f)); font-size:var(--zy-text-micro, 22rpx); line-height: 2.2; }
.short-more::after { border: 0; }
/* 气息深化·阶段二：入口卡顶部缝线（纯装饰；编号衬线见 .zy-t-serif） */
.entry { position: relative; }
.entry-stitch { position: absolute; top: 0; left: 24rpx; right: 24rpx; }
.explore-stitch { position: absolute; top: 0; left: 20rpx; right: 20rpx; }
@media (prefers-reduced-motion: reduce) {
 .short-item { transition: none; }
}
/* #ifdef MP */
/* 小程序 custom tabBar 不占页面视口，页面自留底部空间（含安全区） */
.growth-page { padding-bottom: calc(112rpx + env(safe-area-inset-bottom)); }
/* #endif */

/* ── v9 冷瓷展签收口 ── */
.growth-page { padding: var(--zy-space-lg) var(--zy-space-md) 120rpx; background: var(--zy-bg); color: var(--zy-text); }
.growth-muted, .growth-foot { color: var(--zy-muted); }
.start-brand { color: var(--zy-title); }
.motion-toggle { padding: 0; border: 0; background: transparent; color: var(--zy-muted); letter-spacing: 1rpx; }
.start-brand { font-family: var(--zy-font-display, serif); font-weight: 500; letter-spacing: 1rpx; }
.start-seal { width: 66rpx; height: 66rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; border: 1rpx solid var(--zy-care); color: var(--zy-care); font-family: var(--zy-font-display, serif); font-size: 30rpx; line-height: 1; }
.motion-toggle { text-decoration: underline; text-underline-offset: 4rpx; }
.start-brand-sub { color: var(--zy-muted); letter-spacing: 2px; }
.growth-hero { padding: 44rpx 0 34rpx; border-bottom: 1rpx solid var(--zy-border); }
.growth-hero .growth-title { max-width: 100%; font-family: var(--zy-font-display, serif); font-weight: 500; font-size: 56rpx; line-height: 1.32; }
.growth-hero .growth-subtitle { color: var(--zy-muted); max-width: 600rpx; }
.start-mark { position: absolute; top: 72rpx; right: 12rpx; display: flex; flex-direction: column; align-items: center; gap: 6rpx; color: var(--zy-muted); }
.start-mark-rule { width: 2rpx; height: 68rpx; margin-bottom: 10rpx; background: var(--zy-care); }
.start-mark text { font-family: var(--zy-font-display, serif); font-size: 22rpx; line-height: 1.5; letter-spacing: 4rpx; opacity: .62; }
.growth-hero .growth-title { max-width: 78%; }
.moment-card { padding: 32rpx 28rpx; background: var(--zy-card-bg); border: 2rpx solid var(--zy-card-line); border-radius: var(--zy-radius-card, 24rpx); box-shadow: var(--zy-shadow-card); margin-bottom: 36rpx; }
.moment-card .growth-card-title { font-family: var(--zy-font-display, serif); font-size: 38rpx; font-weight: 500; color: var(--zy-title); }
/* v8：选项 = 白底描边块，选中 = 青绿实底白字（不再只靠一根细线区分） */
.time-tabs { background: transparent; padding: 0; border: 0; gap: 14rpx; }
.time-tabs button { border: 2rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); background: var(--zy-card-bg); color: var(--zy-text); }
.time-tabs button.selected { background: var(--zy-brand-600); border-color: var(--zy-brand-600); color: #fff; font-weight: 600; box-shadow: var(--zy-shadow-cta); }
.mood-label { color: var(--zy-muted); letter-spacing: 1rpx; }
.mood-row { gap: 12rpx; border: 0; flex-wrap: wrap; }
.mood-row button { border: 2rpx solid var(--zy-border); border-radius: 999rpx; background: var(--zy-card-bg); color: var(--zy-text); padding: 0 24rpx; }
.mood-row button.selected { background: var(--zy-brand-600); border-color: var(--zy-brand-600); color: #fff; font-weight: 600; }
.moment-title { font-family: var(--zy-font-display, serif); font-weight: 500; color: var(--zy-title); }
.short-suggest { border-top: 1rpx solid var(--zy-border); }
.short-suggest-title { font-family: var(--zy-font-display, serif); font-weight: 500; }
.short-item { padding: 20rpx 0; border: 0; border-bottom: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); background: transparent; }
.short-item.primary { border: 0; border-left: 3rpx solid var(--zy-care); border-bottom: 1rpx solid var(--zy-border); background: transparent; padding-left: 18rpx; }
.short-kind { border-radius: var(--zy-radius-control, 18rpx); background: transparent; border: 1rpx solid var(--zy-border); padding: 2rpx 8rpx; color: var(--zy-muted); }
.short-recommend { border-radius: var(--zy-radius-control, 18rpx); background: var(--zy-care); padding: 2rpx 8rpx; letter-spacing: 1rpx; }
.short-title { font-family: var(--zy-font-display, serif); font-size: 34rpx; font-weight: 500; }
.short-subtitle { color: var(--zy-muted); }
.growth-button { border-radius: var(--zy-radius-control, 18rpx); }
.entry { gap: 20rpx; padding: 24rpx 0; border: 0; border-bottom: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); background: transparent; }
.entry-number { padding: 0; width: 60rpx; height: 60rpx; display: flex; align-items: center; justify-content: center; background: transparent; border: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); font-family: var(--zy-font-display, serif); }
.entry .growth-card-title { font-family: var(--zy-font-display, serif); font-weight: 500; }
.section-label { color: var(--zy-muted); letter-spacing: 2px; }
.explore { padding: 22rpx 0 44rpx; background: transparent; border: 0; border-top: 1rpx solid var(--zy-border); border-radius: var(--zy-radius-control, 18rpx); }
.explore .growth-card-title { font-family: var(--zy-font-display, serif); font-weight: 500; }
</style>
<style scoped>
/* ── v9 青花瓷 · 手作页 ── */
.growth-page { background: var(--zy-bg); }
.motion-toggle { min-height: 56rpx; margin: 0; padding: 0 20rpx; border-radius: 999rpx; font-size: 22rpx; white-space: nowrap; }
.moment-card { border-radius: var(--zy-radius-feature, 36rpx); border: 0; box-shadow: var(--zy-shadow-pop); position: relative; overflow: hidden; }
.moment-card .growth-card-title { font-size: 40rpx; font-weight: 700; }
.section-label { display: flex; align-items: center; gap: 14rpx; margin: 40rpx 0 18rpx; font-family: var(--zy-font-display); font-size: 34rpx; font-weight: 700; letter-spacing: 2rpx; color: var(--zy-title); }
.section-label::before { content: ''; width: 8rpx; height: 32rpx; border-radius: 4rpx; background: var(--zy-cinnabar); }
.growth-card.entry { display: flex; align-items: center; gap: 22rpx; padding: 26rpx; margin-bottom: 16rpx; background: var(--zy-card-bg); border: 0; border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); }
.entry-stitch { display: none; }
.entry-number { width: 80rpx; height: 80rpx; flex-shrink: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: var(--zy-brand-50); color: var(--zy-brand-600); font-size: 30rpx; font-weight: 700; }
.entry-thumb { width: 80rpx; height: 80rpx; flex-shrink: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: var(--zy-pine, #2f7d68); }
.entry--music { background: var(--zy-pine-50, #e6f3ee); }
.entry .growth-card-title { font-size: 32rpx; font-weight: 700; color: var(--zy-title); }
.arrow { color: var(--zy-brand-600); font-size: 40rpx; }
.growth-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; }
.growth-card.explore { position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 8rpx; min-height: 200rpx; padding: 24rpx; border: 0; border-radius: var(--zy-radius-card); box-shadow: var(--zy-shadow-card); text-align: left; }
.explore.tone-0 { background: var(--zy-brand-50); }
.explore.tone-1 { background: var(--zy-pine-50, #e6f3ee); }
.explore.tone-2 { background: var(--zy-gamboge-50, #fbf1de); }
.explore.tone-3 { background: var(--zy-cinnabar-50, #fbecea); }
.explore-icon { width: 68rpx; height: 68rpx; border-radius: 20rpx; background: var(--zy-card-bg); display: flex; align-items: center; justify-content: center; margin-bottom: 6rpx; }
.explore .growth-card-title { font-size: 30rpx; font-weight: 700; color: var(--zy-title); }
.explore-arrow { position: absolute; right: 22rpx; top: 28rpx; }
.explore-stitch { display: none; }
</style>

<style scoped>
/* v9 自定义导航：H5 留状态栏；小程序让出胶囊区 */
.zy-tabpage-top { height: var(--status-bar-height, 0px); }
/* #ifdef MP */
.zy-tabpage-top { height: calc(var(--status-bar-height, 20px) + 88rpx); }
/* #endif */
</style>
<style scoped>
/* v9：心情选择带表情；选中 = 暖色浅底，而不是冷蓝实底 */
.mood-row { display: flex; flex-wrap: wrap; gap: 12rpx; }
.mood-row button.mood-chip { flex: 0 0 auto; width: auto; min-width: 0; min-height: 64rpx; display: inline-flex; flex-direction: row; align-items: center; gap: 8rpx; padding: 0 22rpx 0 10rpx; white-space: nowrap; margin: 0; }
.mood-row button.mood-chip.selected { background: #fff4e8; border-color: #f0a35e; color: #8a4b2a; box-shadow: 0 6rpx 16rpx rgba(201,120,60,.2); }
</style>
