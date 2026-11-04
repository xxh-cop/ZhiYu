<template>
 <view class="growth-page" :class="{'motion-reduced':reducedMotion}">
  <view class="growth-hero"><text class="growth-eyebrow">SLOW COLOR · 自由绘色</text><text class="growth-title">把想象，慢慢画出来</text><text class="growth-subtitle">选择纹样，轻点上色。AI 可以搭配几何结构，不会解读你的心理。</text></view>
  <view class="growth-card"><text class="growth-card-title">原创纹样资源</text><view class="growth-row"><AccessibleButton v-for="c in ['全部','花叶','几何']" :key="c" :aria-pressed="patternCategory===c" @click="patternCategory=c">{{c}}</AccessibleButton></view><view class="pattern-library"><AccessibleButton v-for="p in filteredPatterns" :key="p.id" :disabled="busy" @click="usePattern(p)"><text>{{p.name}}</text><text class="growth-muted">{{p.category}} · {{p.level}} · {{p.petals*p.rings+1}} 块</text></AccessibleButton></view><text class="growth-muted">项目原创几何构图，可自由使用；不标注为真实非遗文物复刻。</text></view>
  <view class="growth-card">
   <view class="growth-row"><AccessibleButton v-for="m in motifs" :key="m.id" :class="['pattern-chip',{chosen:plan.motif===m.id}]" :disabled="busy" @click="switchPattern(m.id)">{{m.name}}</AccessibleButton></view>
   <canvas canvas-id="mandala" id="mandala" :style="{width:size+'px',height:size+'px'}" @touchstart="paint"/>
   <view class="palette"><AccessibleButton v-for="(c,i) in palette" :key="c" :aria-label="colorNames[i]" :aria-pressed="color===c" :class="['swatch',{chosen:color===c}]" :style="{background:c}" @click="color=c"/></view>
   <view class="growth-row"><AccessibleButton class="growth-button secondary" :disabled="busy||!history.length" @click="undo">撤销</AccessibleButton><AccessibleButton class="growth-button secondary" :disabled="busy||!future.length" @click="redo">重做</AccessibleButton><AccessibleButton class="growth-button" :disabled="busy" @click="exportImage">预览图片</AccessibleButton></view>
   <view class="growth-row growth-spread"><text class="growth-muted" aria-live="polite">已绘色 {{filled}} / {{shapes.length}} 块</text><AccessibleButton class="reset-link" :disabled="busy" @click="reset">清空颜色</AccessibleButton></view>
   <text v-if="message" class="growth-notice" role="status">{{message}}</text>
  </view>
  <view class="growth-card"><text class="growth-card-title">让 AI 帮你搭配线稿</text><input v-model="theme" class="growth-input" maxlength="120" placeholder="例如：一片对称展开的叶子" :disabled="busy"/><text class="growth-muted">支持花朵、叶片与涟漪的几何变化。只发送这段主题，不发送你的画面或成长记录。</text><checkbox-group @change="consent=$event.detail.value.length>0"><label class="growth-consent"><checkbox value="yes" :checked="consent" color="#217e76"/>同意发送本次主题用于 AI 生成与安全审核。</label></checkbox-group><AccessibleButton class="growth-button" :disabled="busy||!consent||!theme.trim()" @click="generate">{{busy?'正在搭配线稿…':'生成线稿结构'}}</AccessibleButton><text v-if="error" class="growth-error" role="alert">{{error}}</text><AccessibleButton v-if="error" class="growth-button secondary" @click="help">寻求支持</AccessibleButton></view>
  <AccessibleButton class="growth-button secondary" :disabled="busy" @click="trace">用这张线稿辅助临摹</AccessibleButton><text class="growth-foot">绘色仅留在当前页面，离开前可预览保存。不会自动上传。<br/>本系统不能替代专业心理咨询</text>
  <ActionSheet :open="sheet.open" :title="sheet.title" :description="sheet.description" :actions="[{key:'confirm',label:sheet.confirmLabel,tone:'danger'}]" cancel-text="再想想" @select="onSheetSelect" @close="onSheetClose"/>
  <Toast/>
 </view>
</template>
<script setup>
import {useInteraction} from '../../composables/useInteraction'
import ActionSheet from '../../components/ActionSheet.vue'
import Toast from '../../components/Toast.vue'
import AccessibleButton from '../../components/AccessibleButton.vue'
import {useToast} from '../../composables/useToast'
const {reducedMotion}=useInteraction()
const toast=useToast()

import {patternResources} from '../../data/patterns'
import {ref,computed,getCurrentInstance} from 'vue'
import {onReady,onUnload} from '@dcloudio/uni-app'
import {request} from '../../utils/request'
import {normalizePlan,makeShapes,hitShape,drawShapes} from '../../utils/lineart'
const instance=getCurrentInstance(),size=Math.min(uni.getSystemInfoSync().windowWidth-60,520)
const plan=ref(normalizePlan()),shapes=ref(makeShapes(plan.value)),colors=ref(shapes.value.map(()=>'#ffffff'))
const color=ref('#8ccbb6'),palette=['#8ccbb6','#efc780','#eaa99a','#aebbe0','#c4b8d8','#ffffff'],colorNames=['薄荷绿','暖黄色','珊瑚粉','雾蓝色','淡紫色','白色']
const history=ref([]),future=ref([]),theme=ref(''),consent=ref(false),busy=ref(false),error=ref(''),message=ref('内置花朵模板')
const motifs=[{id:'flower',name:'花朵'},{id:'leaf',name:'叶片'},{id:'ripple',name:'涟漪'}],filled=computed(()=>colors.value.filter(c=>c!=='#ffffff').length)
const patternCategory=ref('全部'),filteredPatterns=computed(()=>patternResources.filter(p=>patternCategory.value==='全部'||p.category===patternCategory.value));
function usePattern(p){confirmReplace(()=>{apply(p);message.value='已选择 '+p.name})}
let ctx,alive=true
function draw(){if(ctx)drawShapes(ctx,shapes.value,size,colors.value)}
function remember(){history.value.push([...colors.value]);if(history.value.length>100)history.value.shift();future.value=[]}
function paint(e){if(busy.value)return;const t=e.touches[0];if(!t)return;const index=hitShape(shapes.value,(t.x??t.clientX)/size,(t.y??t.clientY)/size);if(index<0||colors.value[index]===color.value)return;remember();colors.value[index]=color.value;draw()}
function undo(){if(history.value.length){future.value.push([...colors.value]);colors.value=history.value.pop();draw()}}
function redo(){if(future.value.length){history.value.push([...colors.value]);colors.value=future.value.pop();draw()}}
const sheet=ref({open:false,title:'',description:'',confirmLabel:'',action:null})
function askConfirm(title,description,confirmLabel,action){sheet.value={open:true,title,description,confirmLabel,action}}
function onSheetSelect(key){const action=sheet.value.action;sheet.value={open:false,title:'',description:'',confirmLabel:'',action:null};if(key==='confirm'&&action&&alive)action()}
function onSheetClose(){sheet.value={open:false,title:'',description:'',confirmLabel:'',action:null}}
function confirmReplace(action){if(!filled.value){action();return}askConfirm('更换线稿','更换后当前颜色会清空，可先预览保存。','更换线稿',action)}
function apply(value){plan.value=normalizePlan(value);shapes.value=makeShapes(plan.value);colors.value=shapes.value.map(()=>'#ffffff');history.value=[];future.value=[];draw()}
function switchPattern(motif){confirmReplace(()=>{apply({...plan.value,motif});message.value='已切换内置纹样'})}
function reset(){askConfirm('清空颜色','保留线稿，清除当前颜色？可以撤销。','清空颜色',()=>{remember();colors.value=shapes.value.map(()=>'#ffffff');draw()})}
function generate(){confirmReplace(async()=>{if(busy.value)return;busy.value=true;error.value='';try{const data=await request({url:'/api/ai/lineart',method:'POST',data:{theme:theme.value.trim(),consent:consent.value}});if(alive){apply(data);message.value=(data.generated?'AI 辅助方案 · ':'内置模板 · ')+data.message}}catch(e){if(alive)error.value=e.message||'生成失败，当前线稿已保留。'}finally{busy.value=false}})}
function exportImage(){uni.canvasToTempFilePath({canvasId:'mandala',success:r=>uni.previewImage({urls:[r.tempFilePath]}),fail:()=>toast.care('预览失败，请重试')},instance.proxy)}
function trace(){uni.navigateTo({url:`/pages/tracing/index?motif=${plan.value.motif}&petals=${plan.value.petals}&rings=${plan.value.rings}`})}
function help(){uni.navigateTo({url:'/pages/crisis/index'})}
onReady(()=>{ctx=uni.createCanvasContext('mandala',instance.proxy);draw()});onUnload(()=>{alive=false})
</script>
<style src="../../styles/growth.css"></style>
<style scoped>
.pattern-library{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12rpx;margin:24rpx 0}.pattern-library button{text-align:left;background:#f5f7f2;border:1px solid #dfe7dc;border-radius:16rpx;padding:20rpx;font-size:27rpx;width:100%}.pattern-library text{display:block;line-height:1.8}
.palette{display:flex;justify-content:center;gap:20rpx;margin:28rpx 0}.swatch{width:52rpx;height:52rpx;min-height:0;margin:0;padding:0;border-radius:50%;border:2rpx solid #bbc9c4;box-sizing:border-box}.chosen{outline:3rpx solid #217e76;outline-offset:4rpx}.growth-row{gap:10rpx}.growth-row .growth-button{flex:1;font-size:24rpx;padding:0 10rpx}.pattern-chip{flex:1;background:var(--zy-bg-tint-mint,#edf5f3);font-size:26rpx;line-height:2.8;color:#217e76;border-radius:14rpx}.reset-link{background:transparent;font-size:23rpx;color:#637b74;margin:16rpx 0;padding:0}.reset-link::after,.swatch::after{border:0}.growth-card:first-of-type{padding:16rpx}canvas{margin:20rpx auto;touch-action:none}.growth-notice{display:block;margin-top:20rpx;padding:18rpx;background:var(--zy-bg-tint-mint,#edf5f3);border-radius:14rpx;font-size:24rpx;line-height:1.7}
</style>
