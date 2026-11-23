<script setup>
import {ref} from 'vue'
import {onLoad} from '@dcloudio/uni-app'
import {request} from '@/utils/request'
import {useInteraction} from '@/composables/useInteraction'
import ActionSheet from '@/components/ActionSheet.vue'
import AccessibleButton from '@/components/AccessibleButton.vue'
import StateBlock from '@/components/StateBlock.vue'
import Toast from '@/components/Toast.vue'
import {useToast} from '@/composables/useToast'
const {reducedMotion}=useInteraction()
const toast=useToast()
const id=ref(''),data=ref(null),rating=ref(0),note=ref(''),consent=ref(false),loading=ref(false),saving=ref(false),error=ref(''),saved=ref(false)
const labels=['不太合适','有些不合适','一般','比较合适','很合适']
const path=()=>'/api/growth/programs/'+id.value+'/feedback'
async function load(){loading.value=true;error.value='';try{data.value=await request({url:path()});rating.value=data.value.feedback?.rating||0;note.value=data.value.feedback?.note||'';consent.value=false;saved.value=!!data.value.feedback}catch(e){data.value=null;error.value=e.message||'读取失败，请重试'}finally{loading.value=false}}
async function save(){if(saving.value||!rating.value||!consent.value)return;saving.value=true;error.value='';try{await request({url:path(),method:'PUT',data:{rating:rating.value,note:note.value,consent:consent.value}});await load();toast.success('体验已保存')}catch(e){error.value=e.message||'保存失败，填写的内容仍在'}finally{saving.value=false}}
const confirmingRemove=ref(false)
function remove(){if(saving.value)return;confirmingRemove.value=true}
async function confirmRemove(){if(saving.value)return;confirmingRemove.value=false;saving.value=true;try{await request({url:path(),method:'DELETE'});await load();toast.success('反馈已删除')}catch(e){error.value=e.message||'删除失败'}finally{saving.value=false}}
onLoad(options=>{id.value=options.id||'';if(/^\d+$/.test(id.value))load();else error.value='活动编号无效'})
</script>
<template>
	<view class="growth-page feedback-page" :class="[{'motion-reduced':reducedMotion}, zyThemeClass]">
		<view class="feedback-hero">
			<text class="growth-eyebrow">PAUSE & REFLECT · 回看这一刻</text>
			<text class="growth-title">体验过后，听听自己</text>
			<view class="hero-rule" aria-hidden="true"></view>
			<text class="growth-subtitle">没有标准答案，真实的感受就很好。</text>
		</view>

		<view v-if="error" class="growth-error" role="alert">
			{{ error }}
			<AccessibleButton v-if="!data" class="growth-button secondary" :disabled="loading" @click="load">重试</AccessibleButton>
		</view>
		<StateBlock v-if="loading" type="loading" shape="detail" title="正在读取这次体验…" />

		<template v-else-if="data">
			<view class="feedback-context">
				<text class="context-eyebrow">这次参与</text>
				<text class="context-title">{{ data.title }}</text>
				<text v-if="saved" class="saved-note">已保存 · 可修改或删除</text>
			</view>
			<view v-if="!data.eligible" class="eligibility-note">{{ data.reason }}</view>

			<view class="growth-card">
				<text class="step-number">01 / 体验感受</text>
				<text class="growth-card-title">这次活动适合你吗？</text>
				<text class="growth-muted">评价活动安排与自己的适配程度，不是对自己打分。</text>
				<view class="rating-options" role="radiogroup" aria-label="活动适配程度">
					<AccessibleButton v-for="(label,index) in labels" :key="label" :class="['rating-option',{selected:rating===index+1}]" :aria-pressed="rating===index+1" :disabled="saving||!data.eligible" @click="rating=index+1">
						<text class="rating-number">{{ index + 1 }}</text>
						<text class="rating-label">{{ label }}</text>
					</AccessibleButton>
				</view>
			</view>

			<view class="growth-card">
				<text class="step-number">02 / 留给自己的话</text>
				<text class="growth-card-title">有什么想记住的？<text class="optional">选填</text></text>
				<text class="growth-muted">可以记下一个瞬间、一种材料的触感，或下次想尝试的事。笔记仅你自己可见。</text>
				<textarea v-model="note" maxlength="1000" :disabled="saving||!data.eligible" class="growth-input feedback-note" placeholder="例如：今天愿意慢下来，试着完成一个小步骤。" placeholder-style="color:#8B9994;font-size:24rpx"></textarea>
				<text class="word-count">{{ note.length }} / 1000</text>
			</view>

			<view class="privacy-card">
				<text class="privacy-title">这份记录由你掌握</text>
				<text>{{ data.kind==='GUIDANCE' ? '一对一的评分和笔记仅本人可见，不向带领者提供评分汇总。' : '笔记仅本人可见。至少五位参加者提交后，评分才计入带领者可查看的汇总。' }}</text>
				<text>记录加密保存，可以随时删除。不代表心理状态或治疗效果。</text>
			</view>

			<template v-if="data.eligible">
				<checkbox-group @change="consent=$event.detail.value.includes('agree')">
					<label class="growth-consent">
						<checkbox value="agree" :checked="consent" :disabled="saving" color="#0e5b53"></checkbox>
						<text>我已了解上述可见范围，同意保存这次评分和笔记。</text>
					</label>
				</checkbox-group>
				<AccessibleButton class="growth-button" :disabled="saving||!rating||!consent" @click="save">{{ saving ? '正在处理…' : saved ? '更新这次体验' : '保存这次体验' }}</AccessibleButton>
				<text v-if="!rating||!consent" class="submit-hint">{{ !rating ? '请选择一个体验评分' : '确认保存授权后即可提交' }}</text>
			</template>

			<AccessibleButton v-if="saved" class="growth-button secondary" :disabled="saving" @click="remove">删除这次反馈</AccessibleButton>
		</template>

		<text class="growth-foot">本系统不能替代专业心理咨询</text>
		<ActionSheet :open="confirmingRemove" title="删除这次反馈？" description="评分和笔记将被删除，也不再计入后续汇总。无法恢复。" :actions="[{key:'confirm',label:'确认删除',tone:'danger',busy:saving}]" cancel-text="再想想" :busy="saving" @select="confirmRemove" @close="confirmingRemove=false" />
		<Toast />
	</view>
</template>

<style scoped>
.feedback-page { max-width: 780px; }
.feedback-hero { padding: 4rpx 0 30rpx; border-bottom: 1rpx solid var(--zy-border, #d3ded8); margin-bottom: 0; }
.hero-rule { width: 48rpx; height: 2rpx; background: var(--zy-care, #a64842); margin: 24rpx 0 0; }
.feedback-context { padding: 26rpx 0; border-bottom: 1rpx solid var(--zy-border, #d3ded8); }
.context-eyebrow { display: block; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); letter-spacing: 2px; }
.context-title { display: block; font-family: var(--zy-font-display, "Songti SC", serif); font-size: 36rpx; font-weight: 500; line-height: 1.5; margin: 8rpx 0; }
.saved-note { display: inline-block; color: var(--zy-care, #a64842); font-size: var(--zy-text-caption, 24rpx); border-bottom: 1rpx solid var(--zy-care, #a64842); margin-top: 4rpx; }
.feedback-context + .growth-card { border-top: 0; }
.step-number { display: block; color: var(--zy-muted, #6e7b75); letter-spacing: 2px; font-size: var(--zy-text-micro, 22rpx); margin-bottom: 14rpx; }
.rating-options { display: flex; flex-direction: column; margin-top: 20rpx; }
.rating-option { display: flex; align-items: center; gap: 20rpx; margin: 0; padding: 20rpx 0; border: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; color: var(--zy-text, #42524c); text-align: left; font-size: var(--zy-text-body, 28rpx); line-height: 1.5; }
.rating-option:last-child { border-bottom: 1rpx solid var(--zy-border, #d3ded8); }
.rating-option::after { border: 0; }
.rating-option.selected { background: transparent; color: var(--zy-brand-700, #0a403b); box-shadow: inset 0 -3rpx 0 var(--zy-brand-700, #0a403b); }
.rating-number { display: flex; align-items: center; justify-content: center; width: 44rpx; height: 44rpx; border: 1rpx solid var(--zy-border, #d3ded8); border-radius: 0; background: transparent; font-family: var(--zy-font-display, "Songti SC", serif); font-weight: 500; color: var(--zy-title, #111816); }
.rating-option.selected .rating-number { background: var(--zy-brand-600, #0e5b53); border-color: var(--zy-brand-600, #0e5b53); color: #fff; }
.rating-label { flex: 1; }
.optional { font-size: var(--zy-text-micro, 22rpx); font-weight: 400; color: var(--zy-muted, #6e7b75); margin-left: 16rpx; }
.feedback-note { height: 230rpx; line-height: 1.8; border: 0; border-bottom: 2rpx solid var(--zy-faint, #8B9994); border-radius: 0; background: transparent; padding: 16rpx 0; }
.word-count { display: block; text-align: right; font-size: var(--zy-text-micro, 22rpx); color: var(--zy-muted, #6e7b75); }
.privacy-card { display: flex; flex-direction: column; gap: 12rpx; border: 0; border-top: 1rpx solid var(--zy-border, #d3ded8); border-bottom: 1rpx solid var(--zy-border, #d3ded8); border-left: 2rpx solid var(--zy-care, #a64842); background: transparent; border-radius: 0; padding: 24rpx 0 24rpx 20rpx; font-size: var(--zy-text-caption, 24rpx); line-height: 1.8; color: var(--zy-text, #42524c); }
.privacy-title { font-family: var(--zy-font-display, "Songti SC", serif); font-size: var(--zy-text-body, 28rpx); font-weight: 500; color: var(--zy-title, #111816); }
.eligibility-note { padding: 0 0 0 18rpx; border-left: 3rpx solid var(--zy-care, #a64842); background: transparent; color: var(--zy-care, #a64842); border-radius: 0; font-size: var(--zy-text-caption, 24rpx); line-height: 1.7; margin: 24rpx 0; }
.submit-hint { display: block; text-align: center; font-size: var(--zy-text-caption, 24rpx); color: var(--zy-muted, #6e7b75); margin-top: 16rpx; }
@media (min-width: 760px) {
  .rating-options { display: grid; grid-template-columns: repeat(5, 1fr); border-bottom: 1rpx solid var(--zy-border, #d3ded8); }
  .rating-option { flex-direction: column; text-align: center; font-size: 14px; padding: 18px 8px; border-top: 0; border-right: 1rpx solid var(--zy-border, #d3ded8); }
  .rating-option:last-child { border-right: 0; border-bottom: 0; }
}
</style>
