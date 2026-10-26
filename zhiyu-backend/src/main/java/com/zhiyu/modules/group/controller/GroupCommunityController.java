package com.zhiyu.modules.group.controller;
import com.zhiyu.modules.group.service.GroupCommunityService;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/groups/{id}")
public class GroupCommunityController {
 private final GroupCommunityService service;
 public record Message(@NotBlank @Size(max=1000) String content){}
 public record Leader(@NotNull Long memberId){}
 @GetMapping public Result<?> detail(@PathVariable long id){return Result.success(service.detail(id));}
 @PostMapping("/leave") public Result<?> leave(@PathVariable long id){service.leave(id);return Result.success();}
 @PutMapping("/leader") public Result<?> leader(@PathVariable long id,@Valid @RequestBody Leader r){service.transfer(id,r.memberId());return Result.success();}
 @GetMapping("/messages") public Result<?> messages(@PathVariable long id,@RequestParam(required=false) Long before){return Result.success(service.messages(id,before));}
 @PostMapping("/messages") public Result<?> send(@PathVariable long id,@Valid @RequestBody Message r){service.send(id,r.content());return Result.success();}
 @DeleteMapping("/messages/{messageId}") public Result<?> remove(@PathVariable long id,@PathVariable long messageId){service.remove(id,messageId);return Result.success();}
}
