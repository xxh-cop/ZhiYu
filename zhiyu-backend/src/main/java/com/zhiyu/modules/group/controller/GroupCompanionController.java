package com.zhiyu.modules.group.controller;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.group.service.GroupCompanionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/groups/{id}/companions")
public class GroupCompanionController {
    private final GroupCompanionService service;
    public record Invite(@NotNull @Positive Long memberId){}
    public record Response(@NotBlank @Pattern(regexp="ACCEPT|DECLINE|CANCEL") String action){}
    @GetMapping public Result<?> list(@PathVariable long id){return Result.success(service.list(id));}
    @PostMapping public Result<?> invite(@PathVariable long id,@Valid @RequestBody Invite r){service.invite(id,r.memberId());return Result.success();}
    @PutMapping("/{invitationId}") public Result<?> respond(@PathVariable long id,@PathVariable long invitationId,@Valid @RequestBody Response r){service.respond(id,invitationId,r.action());return Result.success();}
}
