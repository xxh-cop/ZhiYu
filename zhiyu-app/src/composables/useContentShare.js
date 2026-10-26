import {onShareAppMessage} from '@dcloudio/uni-app'
export function useContentShare(kind, getItem) {
 const pathFor = id => `/pages/${kind === 'craft' ? 'craft/detail' : 'programs/index'}?id=${id}`
 onShareAppMessage(() => {const item=getItem();return {title:item?.title||'织愈 · 一起创作',path:item?.id?pathFor(item.id):'/pages/start/index'}})
 function share(){const item=getItem();if(!item?.id)return;
  // #ifdef H5
  uni.setClipboardData({data:window.location.origin+window.location.pathname+'#'+pathFor(item.id),success:()=>uni.showToast({title:'链接已复制',icon:'none'})})
  // #endif
 }
 return {share}
}
