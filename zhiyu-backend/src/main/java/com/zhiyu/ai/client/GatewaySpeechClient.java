package com.zhiyu.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** JSON gateway protocol is documented in docs/speech-gateway.md; no provider is assumed. */
@Component
@ConditionalOnProperty(name = "zhiyu.speech.provider", havingValue = "gateway", matchIfMissing = true)
public class GatewaySpeechClient implements SpeechClient {
    private final String endpoint,key;
    private final boolean enabled;
    private final ObjectMapper json;
    private HttpClient client;
    private synchronized HttpClient client(){
        if(client==null)client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        return client;
    }
    public GatewaySpeechClient(ObjectMapper json,@Value("${ZHIYU_SPEECH_ENABLED:false}") boolean enabled,
        @Value("${ZHIYU_SPEECH_ENDPOINT:}") String endpoint,@Value("${ZHIYU_SPEECH_API_KEY:}") String key){this.json=json;this.enabled=enabled;this.endpoint=endpoint.replaceAll("/+$","");this.key=key;}
    public boolean available(){
        if(!enabled||endpoint.isBlank()||key.isBlank())return false;
        try{var uri=URI.create(endpoint);return uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null&&("https".equals(uri.getScheme())||("http".equals(uri.getScheme())&&Set.of("localhost","127.0.0.1").contains(uri.getHost())));}
        catch(Exception e){return false;}
    }
    private Map<?,?> call(String route,Map<String,Object> body){
        if(!available())throw new BusinessException(4001,"语音服务尚未配置，请使用文字输入或文字引导");
        try{
            var request=HttpRequest.newBuilder(URI.create(endpoint+route)).timeout(Duration.ofSeconds(25))
                .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=client().send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()){
                if(response.statusCode()!=200)throw new IllegalStateException("speech gateway response rejected");
                byte[] bytes=stream.readNBytes(7*1024*1024+1);
                if(bytes.length>7*1024*1024)throw new IllegalStateException("speech gateway response too large");
                return json.readValue(bytes,Map.class);
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new BusinessException(4001,"语音请求已取消");}
        catch(Exception e){throw new BusinessException(4001,"语音服务暂不可用，请使用文字");}
    }
    public String transcribe(byte[] audio,String mimeType){
        Object value=call("/asr",Map.of("audioBase64",Base64.getEncoder().encodeToString(audio),"mimeType",mimeType,"language","zh-CN")).get("text");
        if(!(value instanceof String text)||text.isBlank()||text.length()>2000)throw new BusinessException(4001,"未识别到有效内容，请缩短录音后重试");return text;
    }
    public byte[] synthesize(String text){
        var response=call("/tts",Map.of("text",text,"language","zh-CN","format","mp3"));
        try{byte[] bytes=Base64.getDecoder().decode((String)response.get("audioBase64"));if(bytes.length<3||bytes.length>5*1024*1024||!((bytes[0]=='I'&&bytes[1]=='D'&&bytes[2]=='3')||((bytes[0]&255)==255&&(bytes[1]&224)==224)))throw new IllegalArgumentException();return bytes;}
        catch(Exception e){throw new BusinessException(4001,"语音返回格式无效，请使用文字引导");}
    }
}
