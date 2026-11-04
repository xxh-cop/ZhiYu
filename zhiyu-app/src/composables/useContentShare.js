import {onShareAppMessage} from '@dcloudio/uni-app'
import {notify} from './useToast'
export function useContentShare(kind, getItem) {
 const pathFor = id => (kind === 'craft' ? '/pages/craft/detail' : '/sub-programs/pages/programs/index') + '?id=' + id
 onShareAppMessage(() => {const item=getItem();return {title:item?.title||'织愈 · 一起创作',path:item?.id?pathFor(item.id):'/pages/start/index'}})
 function share(){const item=getItem();if(!item?.id)return;
  // #ifdef H5
  uni.setClipboardData({data:window.location.origin+window.location.pathname+'#'+pathFor(item.id),success:()=>notify('链接已复制',{tone:'success'})})
  // #endif
 }
 return {share}
}
