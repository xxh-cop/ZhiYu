package com.zhiyu.modules.growth;

import com.zhiyu.common.result.Result;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 活动带领者读取 3 个团体工作坊模板；模板不落库，创建时仍走活动校验。 */
@RestController
@RequestMapping("/api/growth/workshop-templates")
@RequiredArgsConstructor
public class WorkshopTemplateController {

    private final WorkshopTemplateService service;
    private final BusinessAccess access;

    @GetMapping
    public Result<WorkshopTemplateCatalog> list() {
        access.role(1, 2, 3);
        return Result.success(service.catalog());
    }
}
