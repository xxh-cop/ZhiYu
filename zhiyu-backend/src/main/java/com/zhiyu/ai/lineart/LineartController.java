package com.zhiyu.ai.lineart;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/lineart")
public class LineartController {
    private final LineartService service;
    public record Request(@NotBlank @Size(max=120) String theme,@AssertTrue boolean consent) {}
    @PostMapping public Result<LineartService.Plan> generate(@Valid @RequestBody Request request){
        return Result.success(service.generate(request.theme(),request.consent()));
    }
}
