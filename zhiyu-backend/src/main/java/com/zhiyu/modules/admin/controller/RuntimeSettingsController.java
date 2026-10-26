package com.zhiyu.modules.admin.controller;
import com.zhiyu.modules.admin.service.RuntimeSettings;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/config/runtime")
public class RuntimeSettingsController {
    private final RuntimeSettings settings;
    public record Update(@NotBlank String key,@Min(0) int revision,@NotNull Map<String,Object> value){}
    @GetMapping public Result<?> get(){return Result.success(settings.list());}
    @PutMapping public Result<?> save(@Valid @RequestBody Update input){settings.save(input.key(),input.revision(),input.value());return Result.success();}
}
