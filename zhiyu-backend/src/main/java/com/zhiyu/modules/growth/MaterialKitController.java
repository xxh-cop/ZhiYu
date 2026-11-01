package com.zhiyu.modules.growth;

import com.zhiyu.common.result.Result;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学生与带领者均可读取工作坊材料包；只返回准备清单，不含采购链接或价格。 */
@RestController
@RequestMapping("/api/growth/material-kits")
@RequiredArgsConstructor
public class MaterialKitController {

    private final MaterialKitService service;
    private final BusinessAccess access;

    @GetMapping
    public Result<MaterialKitCatalog> list() {
        access.user();
        return Result.success(service.catalog());
    }
}
