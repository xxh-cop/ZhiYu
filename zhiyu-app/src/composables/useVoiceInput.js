import {ref} from 'vue'
import {onHide,onUnload} from '@dcloudio/uni-app'
import {request} from '../utils/request'
import {API_BASE_URL,TOKEN_KEY} from '../utils/config'

export function useVoiceInput(onText) {
  const state=ref('idle')
  let recorder=null,stream=null,timer=null,cancelled=false,upload=null
  const fail=e=>uni.showToast({title:e?.message||'录音暂不可用，请使用文字输入',icon:'none'})
  const tracks=()=>{stream?.getTracks().forEach(t=>t.stop());stream=null}
  const finish=()=>{clearTimeout(timer);timer=null;tracks();state.value='idle'}
  async function send(blobOrPath){
    if(cancelled)return
    state.value='uploading'
    try{
      let data
      // #ifdef H5
      if(blobOrPath.size>5*1024*1024)throw new Error('录音超过 5MB，请缩短后重试')
      const body=new FormData();body.append('file',blobOrPath,'voice.webm')
      const controller=new AbortController();upload=controller
      timer=setTimeout(()=>controller.abort(),30000)
      const response=await fetch(API_BASE_URL+'/api/speech/transcribe',{method:'POST',headers:{Authorization:'Bearer '+uni.getStorageSync(TOKEN_KEY)},body,signal:controller.signal})
      const result=await response.json();if(!response.ok||result.code!==0)throw new Error(result.message||'识别失败');data=result.data
      // #endif
      // #ifndef H5
      data=await new Promise((resolve,reject)=>{upload=uni.uploadFile({url:API_BASE_URL+'/api/speech/transcribe',filePath:blobOrPath,name:'file',header:{Authorization:'Bearer '+uni.getStorageSync(TOKEN_KEY)},timeout:30000,success:r=>{try{const body=JSON.parse(r.data);if(body.code!==0)throw new Error(body.message||'识别失败');resolve(body.data)}catch(e){reject(e)}},fail:()=>reject(new Error('录音上传失败，请使用文字输入'))})})
      // #endif
      if(!cancelled){onText(data.text);uni.showToast({title:'请确认文字后再发送',icon:'none'})}
    }catch(e){if(!cancelled)fail(e)}finally{upload=null;finish()}
  }
  async function toggle(){
    if(state.value==='recording'){clearTimeout(timer);recorder?.stop();return}
    if(state.value!=='idle')return
    cancelled=false;state.value='starting'
    try{
      const status=await request({url:'/api/speech/status'})
      if(cancelled)return
      if(!status.available){finish();uni.showToast({title:status.message,icon:'none',duration:3500});return}
      const consent=await new Promise(resolve=>uni.showModal({title:'语音输入',content:'录音将交由已配置语音服务识别，不保存原始音频。识别文字需由你确认后发送。',success:r=>resolve(r.confirm),fail:()=>resolve(false)}))
      if(!consent||cancelled){finish();return}
      // #ifdef H5
      if(!navigator.mediaDevices?.getUserMedia||typeof MediaRecorder==='undefined')throw new Error('当前浏览器不支持录音，请使用文字输入')
      stream=await navigator.mediaDevices.getUserMedia({audio:true})
      if(cancelled){tracks();return}
      const chunks=[]
      recorder=new MediaRecorder(stream)
      recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)}
      recorder.onerror=()=>{cancelled=true;finish();fail(new Error('录音失败，请使用文字输入'))}
      recorder.onstop=()=>{const blob=new Blob(chunks,{type:recorder.mimeType||'audio/webm'});tracks();send(blob)}
      recorder.start()
      state.value='recording'
      // #endif
      // #ifndef H5
      if(!recorder){recorder=uni.getRecorderManager();recorder.onStop(r=>{send(r.tempFilePath)});recorder.onError(()=>{finish();if(!cancelled)fail(new Error('无法录音，请检查麦克风权限'))});recorder.onStart(()=>{state.value='recording'})}
      recorder.start({duration:60000,format:'mp3',sampleRate:16000,numberOfChannels:1})
      // #endif
      timer=setTimeout(()=>{if(state.value==='recording')recorder?.stop()},60000)
    }catch(e){finish();if(!cancelled)fail(e)}
  }
  function dispose(){cancelled=true;upload?.abort();if(state.value==='recording')recorder?.stop();finish()}
  onHide(dispose);onUnload(dispose)
  return {state,toggle}
}
