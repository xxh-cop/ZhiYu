package com.zhiyu.modules.file;
import com.zhiyu.common.result.Result;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/api/media")
public class MediaController {
 private final MediaService media;
 @GetMapping("/mine") public Result<?> mine(@RequestParam(required=false) Long before){return Result.success(media.mine(UserContext.getUserId(),before));}
 @GetMapping("/{id}/playback") public Result<Map<String,String>> playback(@PathVariable long id){return Result.success(Map.of("url",media.playback(UserContext.getUserId(),id)));}
 @GetMapping("/{id}/content") public ResponseEntity<Resource> content(@PathVariable long id,@RequestParam Long uid,@RequestParam long expires,@RequestParam String signature){Resource resource=media.content(id,uid,expires,signature);return ResponseEntity.ok().contentType(MediaType.valueOf(media.contentType(id))).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(resource);}
}
