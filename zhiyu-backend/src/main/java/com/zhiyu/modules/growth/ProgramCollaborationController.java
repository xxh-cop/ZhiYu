package com.zhiyu.modules.growth;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/growth")
public class ProgramCollaborationController {
    private final ProgramCollaborationService collaboration;
    private final GuidanceConversationService conversations;
    public record Collaborators(@NotNull @Size(max=5) List<@NotNull @Positive Long> userIds){}
    public record Consent(@NotNull Boolean enabled){}
    public record Message(@NotBlank @Size(max=1000) String content){}
    @GetMapping("/hosts") public Result<?> hosts(){return Result.success(collaboration.hosts());}
    @GetMapping("/programs/{id}/collaborators") public Result<?> team(@PathVariable long id){return Result.success(collaboration.list(id));}
    @PutMapping("/programs/{id}/collaborators") public Result<?> team(@PathVariable long id,@Valid @RequestBody Collaborators r){collaboration.save(id,r.userIds());return Result.success();}
    @GetMapping("/conversations") public Result<?> list(HttpServletResponse response){response.setHeader("Cache-Control","no-store");return Result.success(conversations.conversations());}
    @PutMapping("/bookings/{id}/conversation") public Result<?> consent(@PathVariable long id,@Valid @RequestBody Consent r){conversations.consent(id,r.enabled());return Result.success();}
    @GetMapping("/bookings/{id}/messages") public Result<?> messages(@PathVariable long id,@RequestParam(required=false) Long before,HttpServletResponse response){response.setHeader("Cache-Control","no-store");return Result.success(conversations.messages(id,before));}
    @PostMapping("/bookings/{id}/messages") public Result<?> send(@PathVariable long id,@Valid @RequestBody Message r){conversations.send(id,r.content());return Result.success();}
    @DeleteMapping("/bookings/{id}/messages/{messageId}") public Result<?> remove(@PathVariable long id,@PathVariable long messageId){conversations.remove(id,messageId);return Result.success();}
}
