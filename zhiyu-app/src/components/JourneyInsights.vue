<template>
 <view ref="insightRoot" class="insights">
  <view class="insight-header"><view><text class="insight-title">回看自己的变化</text><text class="insight-note">看见记录，不急着给自己下结论。</text></view><view class="range-control"><AccessibleButton role="button" tabindex="0" v-for="option in ranges" :key="option.days" :class="{chosen:days===option.days}" :aria-pressed="days===option.days" @click="days=option.days">{{option.label}}</AccessibleButton></view></view>
  <text v-if="truncated" class="scope-warning">这里只分析最近 {{limit}} 条记录，较早记录尚未载入；可前往隐私设置分批导出。</text>
  <view class="insight-grid">
   <view class="insight-section"><text class="section-title">自评记录趋势</text><text class="insight-note">只比较同一量表、同一版本的完整作答，未记录不补值。</text>
    <view v-if="!groups.length" class="insight-empty"><text>这个时段还没有完整自评</text><text>已有草稿可在下方记录中继续作答。</text><AccessibleButton role="button" tabindex="0" @click="assessment">去自我了解</AccessibleButton></view>
    <template v-else><view class="scale-control"><AccessibleButton role="button" tabindex="0" v-for="group in groups" :key="group.key" :class="{chosen:active?.key===group.key}" :aria-pressed="active?.key===group.key" @click="groupKey=group.key;selectedId=''">{{group.scale}} · {{group.records.length}} 次<text v-if="groups.filter(g=>g.scale===group.scale).length>1"> · {{group.version}}</text></AccessibleButton></view>
     <text class="version-note">版本：{{active.version}} · 当前时段 {{active.records.length}} 次，图中展示最近 {{chartRecords.length}} 次</text>
     <view class="axis-caption"><text>分数</text><text>图轴 0–{{axisMax}} 分 · 每根柱为一次记录</text></view>
     <view class="score-chart"><AccessibleButton role="button" tabindex="0" v-for="r in chartRecords" :key="r.id" class="score-point" :class="{selected:chosenRecord?.id===r.id}" :aria-label="`${displayDate(r.date)}，${r.score}分，查看记录`" :aria-pressed="chosenRecord?.id===r.id" @click="selectedId=r.id"><text class="score-value">{{r.score}}</text><view class="bar-track"><view class="score-bar" :style="{height:(r.score/axisMax*100)+'%'}"/></view><text class="date-label">{{String(r.date).slice(5,10)}}</text></AccessibleButton></view>
     <view v-if="chosenRecord" class="selected-record" aria-live="polite"><text>{{displayDate(chosenRecord.date)}} · {{chosenRecord.score}} 分</text><text>{{chosenRecord.message||'这是一份当时的自我记录。'}}</text></view>
     <text class="insight-note">{{active.records.length<2?'目前只有一次记录，暂不形成趋势。':'分数变化不等同于状态改善或干预效果。'}} 本系统不能替代专业心理咨询。</text>
    </template>
   </view>
   <view class="insight-section"><text class="section-title">创作前后的感受</text><text class="insight-note">“开始前”是你在创作结束时回顾填写的感受，不是实时测量。</text><view v-if="!reflections.length" class="insight-empty"><text>这个时段还没有创作感受</text><text>完成手作后，可以自愿留下一份记录。</text><AccessibleButton role="button" tabindex="0" @click="start">去选择手作</AccessibleButton></view>
    <view v-for="r in reflections.slice(0,reflectionLimit)" :key="r.id" class="reflection-row"><text class="reflection-title">{{r.title||'一次创作'}}</text><text class="version-note">{{displayDate(r.date)}}</text><view class="comparison"><view><text>开始前 · 回顾</text><text class="mood-value">{{r.beforeLabel}}</text></view><text class="comparison-arrow" aria-hidden="true">→</text><view><text>创作后</text><text class="mood-value">{{r.afterLabel}}</text></view></view><text v-if="r.note" class="reflection-note" selectable>{{r.note}}</text></view>
    <AccessibleButton role="button" tabindex="0" v-if="reflections.length>reflectionLimit" class="more-reflections" @click="reflectionLimit+=5">再看 5 条创作记录</AccessibleButton>
   </view>
  </view>
 </view>
</template>
<script setup>
import {computed,ref,watch,onMounted,onBeforeUnmount} from 'vue'
import {withinRange,assessmentGroups,reflectionRecords} from '../utils/journey-insights'
import AccessibleButton from './AccessibleButton.vue'
const props=defineProps({records:{type:Array,default:()=>[]},limit:{type:Number,default:365},truncated:Boolean})
const ranges=[{days:30,label:'近30天'},{days:90,label:'近90天'},{days:0,label:'已载入记录'}]
const days=ref(30),groupKey=ref(''),selectedId=ref(''),reflectionLimit=ref(5)
const insightRoot=ref(null)
// UniApp normalizes component events; H5 keyboard events need their native key/target.
// #ifdef H5
let keyboardRoot
onMounted(()=>{keyboardRoot=insightRoot.value?.$el||insightRoot.value;keyboardRoot?.addEventListener('keydown',activateFromKeyboard)})
onBeforeUnmount(()=>keyboardRoot?.removeEventListener('keydown',activateFromKeyboard))
// #endif
const filtered=computed(()=>withinRange(props.records,days.value)),groups=computed(()=>assessmentGroups(filtered.value))
const active=computed(()=>groups.value.find(g=>g.key===groupKey.value)||groups.value[0])
const chartRecords=computed(()=>active.value?.records.slice(-12)||[])
const chosenRecord=computed(()=>chartRecords.value.find(r=>r.id===selectedId.value)||chartRecords.value[chartRecords.value.length-1])
// The axis is a display range, not a clinical threshold. Never mix scale versions.
const axisMax=computed(()=>Math.max(({PHQ9:27,GAD7:21})[active.value?.scale]||1,...chartRecords.value.map(r=>r.score)))
const reflections=computed(()=>reflectionRecords(filtered.value))
watch(days,()=>{selectedId.value='';reflectionLimit.value=5})
function displayDate(value){return String(value||'').replace('T',' ').slice(0,16)}
function activateFromKeyboard(event){
 // #ifdef H5
 if (['Enter',' '].includes(event.key) && event.target?.tagName==='UNI-BUTTON') {
  event.preventDefault(); event.target.click()
 }
 // #endif
}
function assessment(){uni.navigateTo({url:'/pages/assessment/index'})}
function start(){uni.switchTab({url:'/pages/start/index'})}
</script>
<style scoped>
.insights .score-point{max-width:72px}
.insights button:focus-visible{outline:3px solid var(--zy-brand-700,#1a645e);outline-offset:3px}
.insights{margin:32rpx 0;color:var(--zy-title,#233d34)}.insight-header{display:flex;justify-content:space-between;align-items:flex-start;gap:24rpx;flex-wrap:wrap;margin-bottom:24rpx}.insight-title{display:block;font-size:36rpx;font-weight:600}.insight-note{display:block;color:var(--zy-content,#4a5f54);font-size:24rpx;line-height:1.8;margin-top:12rpx}.range-control,.scale-control{display:flex;gap:10rpx;flex-wrap:wrap}.insights button{margin:0;font-size:24rpx;line-height:1.5;min-height:44px;padding:16rpx 20rpx;border-radius:12rpx;background:#edf2e9;color:#315e51;display:flex;align-items:center;justify-content:center}.insights button::after{border:0}.insights button.chosen{background:#217e76;color:#fff}.insight-grid{display:grid;gap:24rpx}.insight-section{background:white;padding:28rpx;border:1rpx solid #dfe7dc;border-radius:24rpx;min-width:0}.section-title{font-size:30rpx;font-weight:600}.scale-control{margin-top:24rpx}.scale-control button{flex-wrap:wrap;overflow-wrap:anywhere;text-align:left}.version-note{display:block;font-size:22rpx;color:#4a5f54;overflow-wrap:anywhere;line-height:1.7;margin:16rpx 0}.axis-caption{display:flex;justify-content:space-between;gap:12rpx;color:#4a5f54;font-size:21rpx;margin-top:24rpx}.score-chart{display:flex;align-items:stretch;overflow-x:auto;padding:16rpx 4rpx;border-bottom:1rpx solid #dfe7dc;gap:8rpx}.insights .score-point{flex:1;flex-direction:column;justify-content:flex-end;background:transparent;border:1rpx solid transparent;padding:10rpx 4rpx;min-width:44px;border-radius:10rpx}.insights .score-point.selected{background:#eef4eb;border-color:#217e76}.score-value{font-variant-numeric:tabular-nums;font-size:24rpx;color:#233d34}.bar-track{height:160rpx;width:24rpx;display:flex;align-items:flex-end;margin:10rpx 0}.score-bar{width:100%;background:#518b76;border-radius:6rpx 6rpx 0 0;min-height:2px}.date-label{font-size:20rpx;white-space:nowrap}.selected-record{display:flex;flex-direction:column;gap:10rpx;padding:20rpx;margin-top:20rpx;background:#f5f7f2;border-radius:12rpx;font-size:24rpx;line-height:1.7}.insight-empty{padding:36rpx 0;display:flex;flex-direction:column;gap:20rpx;font-size:26rpx;color:#4a5f54;line-height:1.7}.insight-empty button{align-self:flex-start}.reflection-row{padding:24rpx 0;border-bottom:1rpx solid var(--zy-border,#dfe7dc)}.reflection-title{display:block;font-size:28rpx;font-weight:500;overflow-wrap:anywhere}.comparison{display:flex;align-items:center;justify-content:space-between;gap:16rpx;background:#f5f7f2;padding:20rpx;border-radius:16rpx;font-size:22rpx;color:#4a5f54}.mood-value{display:block;color:#233d34;font-size:29rpx;margin-top:10rpx}.comparison-arrow{color:#4a5f54}.reflection-note{display:block;margin-top:18rpx;font-size:25rpx;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}.scope-warning{display:block;padding:20rpx;background:var(--zy-bg-tint-warm,#f7efd9);color:var(--zy-warning-text,#8a6d1f);font-size:24rpx;line-height:1.7;margin-bottom:20rpx;border-radius:12rpx}.insights .more-reflections{width:100%;margin-top:20rpx}@media(min-width:960px){.insight-grid{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.insight-section{padding:24px}.insight-title{font-size:24px}.section-title{font-size:19px}.insight-note,.version-note,.selected-record{font-size:14px}.insights button{font-size:14px}.score-chart{gap:4px}.comparison{font-size:14px}}
</style>
