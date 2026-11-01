package com.zhiyu.modules.heritage.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageAdminContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端非遗内容工作流；权限由 Security 与 service 的 role(3) 双重校验。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/heritage")
public class HeritageAdminController {

    private final HeritageAdminContentService service;

    public record SaveDraftRequest(int revision, HeritageCatalog catalog) {
    }

    public record PublishRequest(int revision, String note) {
    }

    public record RollbackRequest(int version, String note) {
    }

    public record NoteRequest(String note) {
    }

    /** 兼容入口：已发布目录（学生端当前内容）。 */
    @GetMapping("/catalog")
    public Result<HeritageAdminContentService.PublishedSnapshot> published() {
        return Result.success(service.published());
    }

    @GetMapping("/workflow")
    public Result<HeritageAdminContentService.WorkflowSnapshot> workflow() {
        return Result.success(service.workflow());
    }

    @PutMapping("/workflow/draft")
    public Result<HeritageAdminContentService.WorkflowSnapshot> saveDraft(
            @RequestBody SaveDraftRequest request) {
        return Result.success(service.saveDraft(request.revision(), request.catalog()));
    }

    @DeleteMapping("/workflow/draft")
    public Result<HeritageAdminContentService.WorkflowSnapshot> discardDraft() {
        return Result.success(service.discardDraft());
    }

    @PostMapping("/workflow/publish")
    public Result<HeritageAdminContentService.WorkflowSnapshot> publish(
            @RequestBody PublishRequest request) {
        return Result.success(service.publish(request.revision(), request.note()));
    }

    @PostMapping("/workflow/rollback")
    public Result<HeritageAdminContentService.WorkflowSnapshot> rollback(
            @RequestBody RollbackRequest request) {
        return Result.success(service.rollback(request.version(), request.note()));
    }

    @PostMapping("/workflow/unpublish")
    public Result<HeritageAdminContentService.WorkflowSnapshot> unpublish(
            @RequestBody(required = false) NoteRequest request) {
        return Result.success(service.unpublish(request == null ? null : request.note()));
    }

    @DeleteMapping("/workflow/published")
    public Result<HeritageAdminContentService.WorkflowSnapshot> reset() {
        return Result.success(service.reset());
    }

    @GetMapping("/workflow/versions")
    public Result<List<HeritageAdminContentService.VersionSummary>> versions() {
        return Result.success(service.versions());
    }

    @GetMapping("/workflow/versions/{version}")
    public Result<HeritageAdminContentService.VersionDetail> versionDetail(@PathVariable int version) {
        return Result.success(service.versionDetail(version));
    }
}
