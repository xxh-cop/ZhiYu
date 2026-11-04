import {ref,computed} from 'vue'
import {notify} from './useToast'
const preference=ref(false),systemReduced=ref(false)
let initialized=false
export function useInteraction(){
 if(!initialized){initialized=true;try{preference.value=uni.getStorageSync('zy-reduce-motion')===true}catch{}
  // #ifdef H5
  const media=window.matchMedia('(prefers-reduced-motion: reduce)');systemReduced.value=media.matches
  media.addEventListener?.('change',e=>{systemReduced.value=e.matches})
  // #endif
 }
 const reducedMotion=computed(()=>preference.value||systemReduced.value)
 function toggleMotion(){if(systemReduced.value){notify('已跟随系统减少动效设置');return}preference.value=!preference.value;try{uni.setStorageSync('zy-reduce-motion',preference.value)}catch{}}
 return {reducedMotion,toggleMotion}
}
