package com.zhiyu.modules.file;
import com.zhiyu.common.result.Result;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/**
 * 媒体读取（S1 2026-11-04 门控说明）：
 * mine 返回本人媒体清单（个人数据），挂隐私同意门；
 * playback/content 为受众授权播放机制（§5.1 媒体权限体系独立校验本人/受众），
 * 播放公开或小组内容不产生新的个人数据收集，不挂同意门。
 */
@RestController @RequiredArgsConstructor @RequestMapping("/api/media")
public class MediaController {
 private final MediaService media;
 private final BusinessAccess access;
 @GetMapping("/mine") public Result<?> mine(@RequestParam(required=false) Long before){return Result.success(media.mine(access.consentedUser(),before));}
 @GetMapping("/{id}/playback") public Result<Map<String,String>> playback(@PathVariable long id){return Result.success(Map.of("url",media.playback(UserContext.getUserId(),id)));}
 /**
  * 本人删除自己上传的媒体（2026-11-05 健壮性批）：只删未引用媒体，被引用 → 3001。
  * 权限语义在 MediaService.deleteOwned：未登录 1002 / 非本人 1003 / 不存在 1004。
  */
 @DeleteMapping("/{id}") public Result<Map<String,Boolean>> delete(@PathVariable long id){media.deleteOwned(UserContext.getUserId(),id);return Result.success(Map.of("deleted",true));}
 @GetMapping("/{id}/content") public ResponseEntity<Resource> content(@PathVariable long id,@RequestParam Long uid,@RequestParam long expires,@RequestParam String signature){Resource resource=media.content(id,uid,expires,signature);return ResponseEntity.ok().contentType(MediaType.valueOf(media.contentType(id))).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(resource);}
}
