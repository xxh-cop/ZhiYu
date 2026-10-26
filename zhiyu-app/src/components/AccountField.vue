<template>
  <view class="account-field">
    <label :for="id" class="field-label" @click="focus"><text>{{label}}</text><text v-if="optional" class="optional">选填</text></label>
    <view class="field-control" :class="{invalid:!!error,disabled}">
      <input ref="control" :id="id" :value="modelValue" :password="secret&&!visible" :disabled="disabled" :maxlength="maxlength" :focus="focused" :placeholder="placeholder" :aria-label="label" :aria-invalid="!!error" :aria-describedby="error||hint?id+'-hint':undefined" :confirm-type="confirmType" @input="$emit('update:modelValue',$event.detail.value)" @blur="focused=false;$emit('blur')" @confirm="$emit('confirm')"/>
      <AccessibleButton v-if="secret" class="field-reveal" :aria-label="(visible?'隐藏':'显示')+label" :aria-pressed="visible" :disabled="disabled" @click="visible=!visible">{{visible?'隐藏':'显示'}}</AccessibleButton>
    </view>
    <text :id="id+'-hint'" v-if="error||hint" :class="['field-hint',{error:!!error}]" :role="error?'alert':undefined">{{error||hint}}</text>
  </view>
</template>
<script setup>
import {ref,nextTick,onMounted,watch} from 'vue'
import AccessibleButton from './AccessibleButton.vue'
const props=defineProps({id:String,label:String,modelValue:String,placeholder:String,hint:String,error:String,secret:Boolean,disabled:Boolean,optional:Boolean,maxlength:{type:Number,default:100},autocomplete:{type:String,default:'off'},confirmType:{type:String,default:'next'}})
defineEmits(['update:modelValue','blur','confirm'])
const control=ref(null),visible=ref(false),focused=ref(false)
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
.account-field{margin-bottom:18px}.field-label{display:flex;align-items:center;gap:8px;font-size:14px;font-weight:550;color:#233d34;margin-bottom:8px}.optional{font-size:11px;color:#586e62;font-weight:400}.field-control{display:flex;align-items:center;min-height:50px;border:1px solid #b9cabc;border-radius:10px;background:#fff;padding:0 12px;box-sizing:border-box;transition:border-color .15s,box-shadow .15s}.field-control:focus-within{border-color:#2f7566;box-shadow:0 0 0 3px #2f75661a}.field-control.invalid{border-color:#a94438}.field-control.disabled{background:#f2f4ee}.field-control input{flex:1;min-width:0;height:48px;font-size:16px;color:#233d34}.field-reveal{margin:0 0 0 6px;padding:0 4px;min-width:44px;background:transparent;font-size:12px;color:#285f50}.field-hint{display:block;margin-top:7px;font-size:12px;line-height:1.6;color:#586e62}.field-hint.error{color:#a23f33}@media(prefers-reduced-motion:reduce){.field-control{transition:none}}
</style>
