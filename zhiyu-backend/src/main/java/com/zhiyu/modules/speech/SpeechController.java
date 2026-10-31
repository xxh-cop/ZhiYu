package com.zhiyu.modules.speech;
import com.zhiyu.ai.AiClient;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController @RequiredArgsConstructor
public class SpeechController {
    private final AiClient ai;
    private final BusinessAccess access;
    @Value("${zhiyu.speech.provider:gateway}") private String provider;
    public record Text(@NotBlank @Size(max=2000) String text){}
    private boolean qianwen(){return "qianwen".equalsIgnoreCase(provider);}
    private Map<String,Object> status(){return Map.of("available",ai.speechAvailable(),"message",ai.speechAvailable()?(qianwen()?"千问语音适配器已配置，实际可用性以请求结果为准":"语音网关已配置，实际可用性以请求结果为准"):"语音服务尚未配置，请使用文字输入或文字引导");}
    @GetMapping("/api/speech/status") public Result<?> capability(){access.user();return Result.success(status());}
    @GetMapping("/api/admin/config/speech") public Result<?> config(){access.role(3);List<String> variables=qianwen()?List.of("SPEECH_PROVIDER","QIANWEN_SPEECH_ENABLED","QIANWEN_API_KEY","QIANWEN_BASE_URL","QIANWEN_ASR_MODEL","QIANWEN_TTS_MODEL","QIANWEN_TTS_VOICE","QIANWEN_ASR_SAMPLE_RATE"):List.of("SPEECH_PROVIDER","ZHIYU_SPEECH_ENABLED","ZHIYU_SPEECH_ENDPOINT","ZHIYU_SPEECH_API_KEY");return Result.success(Map.of("status",status(),"variables",variables,"protocol",qianwen()?"QIANWEN_ASR_TTS_V1":"ZHIYU_JSON_GATEWAY_V1","note",qianwen()?"千问语音适配器由服务端环境变量管理；密钥不会下发前端。已配置不等于模型权限/额度已开通，需用短录音实际验证。":"配置由服务端环境变量管理，修改后重启后端；须先接入真实服务商或本地语音网关。"));}
    @PostMapping("/api/speech/transcribe") public Result<?> asr(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        access.consentedUser();if(file.isEmpty()||file.getSize()>5L*1024*1024)throw new BusinessException(1001,"请选择不超过 5MB 的录音");
        String mime=Objects.toString(file.getContentType(),"").split(";")[0];
        byte[] audio=file.getBytes();
        if(audio.length>=3&&((audio[0]=='I'&&audio[1]=='D'&&audio[2]=='3')||((audio[0]&255)==255&&(audio[1]&224)==224)))mime="audio/mpeg";
        if(!Set.of("audio/webm","audio/mpeg","audio/mp3","audio/wav","audio/x-wav","audio/mp4","audio/ogg").contains(mime))throw new BusinessException(1001,"不支持的音频格式");
        return Result.success(Map.of("text",ai.transcribe(audio,mime)));
    }
    @PostMapping("/api/speech/synthesize") public Result<?> tts(@Valid @RequestBody Text r){access.consentedUser();return Result.success(Map.of("audioBase64",Base64.getEncoder().encodeToString(ai.synthesize(r.text())),"mimeType","audio/mpeg"));}
}
