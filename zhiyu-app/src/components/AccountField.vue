<template>
  <view class="account-field">
    <label :for="id" class="field-label" @click="focus"><text>{{label}}</text><text v-if="optional" class="optional">选填</text></label>
    <view class="field-control" :class="{invalid:!!error,disabled,'is-focused':isFocused}">
      <input ref="control" :id="id" :value="modelValue" :password="secret&&!visible" :disabled="disabled" :maxlength="maxlength" :focus="focused" :placeholder="placeholder" :aria-label="label" :aria-invalid="!!error" :aria-describedby="error||hint?id+'-hint':undefined" :confirm-type="confirmType" @input="$emit('update:modelValue',$event.detail.value)" @focus="isFocused=true" @blur="isFocused=false;focused=false;$emit('blur')" @confirm="$emit('confirm')"/>
      <AccessibleButton v-if="secret" class="field-reveal" :aria-label="(visible?'隐藏':'显示')+label" :aria-pressed="visible" :disabled="disabled" @click="visible=!visible"><ZyIcon :name="visible?'eye-off':'eye'" :size="32" tone="muted" /></AccessibleButton>
    </view>
    <view :id="id+'-hint'" v-if="error||hint" :class="['field-hint',{error:!!error}]" :role="error?'alert':undefined"><ZyIcon v-if="error" name="alert" :size="26" tone="care" /><text>{{error||hint}}</text></view>
  </view>
</template>
<script setup>
import {ref,nextTick,onMounted,watch} from 'vue'
import AccessibleButton from './AccessibleButton.vue'
import ZyIcon from './ZyIcon.vue'
const props=defineProps({id:String,label:String,modelValue:String,placeholder:String,hint:String,error:String,secret:Boolean,disabled:Boolean,optional:Boolean,maxlength:{type:Number,default:100},autocomplete:{type:String,default:'off'},confirmType:{type:String,default:'next'}})
defineEmits(['update:modelValue','blur','confirm'])
const control=ref(null),visible=ref(false),focused=ref(false),isFocused=ref(false)
async function focus(){if(props.disabled)return;focused.value=false;await nextTick();focused.value=true;
// #ifdef H5
const el=control.value?.$el||control.value;el?.querySelector('input')?.focus()
// #endif
}
// UniApp's H5 input is a wrapper; annotate its actual input for browsers and assistive technology.
// #ifdef H5
function annotate(){const el=control.value?.$el||control.value;const input=el?.querySelector('input');if(!input)return;input.setAttribute('aria-label',props.label);input.setAttribute('aria-invalid',String(!!props.error));if(props.error||props.hint)input.setAttribute('aria-describedby',props.id+'-hint');else input.removeAttribute('aria-describedby');input.setAttribute('autocomplete',props.autocomplete);input.setAttribute('name',props.id);input.setAttribute('autocapitalize','none');input.setAttribute('spellcheck','false')}
onMounted(annotate);watch(()=>[props.error,props.disabled,props.autocomplete,visible.value],()=>nextTick(annotate))
// #endif
defineExpose({focus})
</script>
<style scoped>
.account-field{margin-bottom:18px}.field-label{display:flex;align-items:center;gap:8px;font-size:14px;font-weight:500;letter-spacing:1px;color:var(--zy-title,#111816);margin-bottom:8px}.optional{font-size:11px;color:var(--zy-muted,#6e7b75);font-weight:400}.field-control{position:relative;display:flex;align-items:center;min-height:48px;border:0;border-bottom:2rpx solid var(--zy-faint,#8B9994);border-radius:0;background:transparent;padding:0;box-sizing:border-box;transition:border-color .15s}.field-control.is-focused{border-bottom-color:var(--zy-brand-600, #0e5b53)}.field-control.is-focused::after{content:'';position:absolute;left:0;right:0;bottom:2rpx;height:2rpx;background:var(--zy-brand-300,#8FC7B8);opacity:.55;pointer-events:none}.field-control.invalid{border-bottom-color:var(--zy-care, #a64842)}.field-control.disabled{background:var(--zy-bg-sunken,#e9efeb)}.field-control input{flex:1;min-width:0;height:48px;font-size:16px;color:var(--zy-title,#111816)}.field-reveal{margin:0 0 0 6px;padding:0;min-width:44px;min-height:48px;background:transparent;display:flex;align-items:center;justify-content:center}.field-reveal::after{border:0}.field-hint{display:flex;align-items:flex-start;gap:6px;margin-top:7px;font-size:12px;line-height:1.6;color:var(--zy-muted,#6e7b75)}.field-hint.error{color:var(--zy-care, #a64842)}@media(prefers-reduced-motion:reduce){.field-control{transition:none}}
</style>
