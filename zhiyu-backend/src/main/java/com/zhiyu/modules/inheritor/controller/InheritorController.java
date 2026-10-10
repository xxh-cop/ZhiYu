package com.zhiyu.modules.inheritor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.Result;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.inheritor.dto.InheritorProfileSelfUpdateDTO;
import com.zhiyu.modules.inheritor.dto.MaterialCreateDTO;
import com.zhiyu.modules.inheritor.dto.ProjectCreateDTO;
import com.zhiyu.modules.inheritor.dto.ProjectUpdateDTO;
import com.zhiyu.modules.inheritor.dto.StepCreateDTO;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
import com.zhiyu.modules.inheritor.service.InheritorProjectService;
import com.zhiyu.modules.inheritor.vo.InheritorProfileSelfVO;
import com.zhiyu.modules.inheritor.vo.InheritorProjectDetailVO;
import com.zhiyu.modules.inheritor.vo.InheritorProjectListItemVO;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 传承人端控制器（M2 批 5A）。
 *
 * <p>提供 12 个接口（全部仅 ROLE_1 可访问，由 SecurityConfig 限定）：
 * <ul>
 *   <li>GET    /api/inheritor/profile                           — 查看自己的传承人资料</li>
 *   <li>PUT    /api/inheritor/profile                           — 更新自己的传承人资料</li>
 *   <li>POST   /api/inheritor/projects                          — 创建项目</li>
 *   <li>GET    /api/inheritor/projects                          — 我的项目列表</li>
 *   <li>GET    /api/inheritor/projects/{id}                     — 项目详情（含步骤+材料）</li>
 *   <li>PUT    /api/inheritor/projects/{id}                     — 编辑项目</li>
 *   <li>DELETE /api/inheritor/projects/{id}                     — 删除项目</li>
 *   <li>POST   /api/inheritor/projects/{id}/steps               — 添加步骤</li>
 *   <li>PUT    /api/inheritor/projects/{id}/steps/{stepId}      — 编辑步骤</li>
 *   <li>DELETE /api/inheritor/projects/{id}/steps/{stepId}      — 删除步骤</li>
 *   <li>POST   /api/inheritor/projects/{id}/materials           — 添加材料</li>
 *   <li>DELETE /api/inheritor/projects/{id}/materials/{materialId} — 删除材料</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/inheritor")
@RequiredArgsConstructor
public class InheritorController {

    private final InheritorProjectService inheritorProjectService;
    private final InheritorProfileMapper inheritorProfileMapper;
    private final UserMapper userMapper;

    // ── 传承人资料 ──

    /**
     * 查看自己的传承人资料。
     */
    @GetMapping("/profile")
    public Result<InheritorProfileSelfVO> getMyProfile() {
        Long userId = UserContext.getUserId();
        InheritorProfile profile = findMyProfile(userId);
        return Result.success(toSelfVO(profile));
    }

    /**
     * 更新自己的传承人资料（skill / intro / honor / avatarUrl）。
     *
     * <p>avatarUrl 非空时同步更新 user.avatar_url。
     */
    @PutMapping("/profile")
    @Transactional
    public Result<InheritorProfileSelfVO> updateMyProfile(
            @Valid @RequestBody InheritorProfileSelfUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        InheritorProfile profile = findMyProfile(userId);

        if (dto.getSkill() != null) {
            profile.setSkill(dto.getSkill());
        }
        if (dto.getIntro() != null) {
            profile.setIntro(dto.getIntro());
        }
        if (dto.getHonor() != null) {
            profile.setHonor(dto.getHonor());
        }
        inheritorProfileMapper.updateById(profile);

        // avatarUrl 非空时同步更新 user.avatar_url
        if (dto.getAvatarUrl() != null && !dto.getAvatarUrl().isBlank()) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setAvatarUrl(dto.getAvatarUrl());
                userMapper.updateById(user);
            }
        }

        log.info("传承人自更新资料: userId={}, profileId={}", userId, profile.getId());
        return Result.success(toSelfVO(profile));
    }

    // ── 项目 CRUD ──

    @PostMapping("/projects")
    public Result<Map<String, Long>> createProject(@Valid @RequestBody ProjectCreateDTO dto) {
        Long id = inheritorProjectService.createProject(UserContext.getUserId(), dto);
        return Result.success(Map.of("id", id));
    }

    @GetMapping("/projects")
    public Result<PageVO<InheritorProjectListItemVO>> listProjects(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(inheritorProjectService.listProjects(UserContext.getUserId(), page, pageSize, status));
    }

    @GetMapping("/projects/{id}")
    public Result<InheritorProjectDetailVO> getProjectDetail(@PathVariable Long id) {
        return Result.success(inheritorProjectService.getProjectDetail(UserContext.getUserId(), id));
    }

    @PutMapping("/projects/{id}")
    public Result<Void> updateProject(@PathVariable Long id, @RequestBody ProjectUpdateDTO dto) {
        inheritorProjectService.updateProject(UserContext.getUserId(), id, dto);
        return Result.success();
    }

    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@PathVariable Long id) {
        inheritorProjectService.deleteProject(UserContext.getUserId(), id);
        return Result.success();
    }

    // ── 步骤 CRUD ──

    @PostMapping("/projects/{id}/steps")
    public Result<Map<String, Long>> addStep(
            @PathVariable Long id,
            @Valid @RequestBody StepCreateDTO dto) {
        Long stepId = inheritorProjectService.addStep(UserContext.getUserId(), id, dto);
        return Result.success(Map.of("id", stepId));
    }

    @PutMapping("/projects/{id}/steps/{stepId}")
    public Result<Void> updateStep(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @Valid @RequestBody StepCreateDTO dto) {
        inheritorProjectService.updateStep(UserContext.getUserId(), id, stepId, dto);
        return Result.success();
    }

    @DeleteMapping("/projects/{id}/steps/{stepId}")
    public Result<Void> deleteStep(
            @PathVariable Long id,
            @PathVariable Long stepId) {
        inheritorProjectService.deleteStep(UserContext.getUserId(), id, stepId);
        return Result.success();
    }

    // ── 材料 CRUD ──

    @PostMapping("/projects/{id}/materials")
    public Result<Map<String, Long>> addMaterial(
            @PathVariable Long id,
            @Valid @RequestBody MaterialCreateDTO dto) {
        Long materialId = inheritorProjectService.addMaterial(UserContext.getUserId(), id, dto);
        return Result.success(Map.of("id", materialId));
    }

    @DeleteMapping("/projects/{id}/materials/{materialId}")
    public Result<Void> deleteMaterial(
            @PathVariable Long id,
            @PathVariable Long materialId) {
        inheritorProjectService.deleteMaterial(UserContext.getUserId(), id, materialId);
        return Result.success();
    }

    // ── 内部方法 ──

    /** 按 userId 查传承人 profile，不存在抛 1004。 */
    private InheritorProfile findMyProfile(Long userId) {
        LambdaQueryWrapper<InheritorProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InheritorProfile::getUserId, userId);
        InheritorProfile profile = inheritorProfileMapper.selectOne(wrapper);
        if (profile == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return profile;
    }

    /** InheritorProfile → InheritorProfileSelfVO，附带 user.avatarUrl。 */
    private InheritorProfileSelfVO toSelfVO(InheritorProfile profile) {
        InheritorProfileSelfVO vo = new InheritorProfileSelfVO();
        vo.setId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setSkill(profile.getSkill());
        vo.setIntro(profile.getIntro());
        vo.setHonor(profile.getHonor());
        vo.setVerified(profile.getVerified());
        User user = userMapper.selectById(profile.getUserId());
        if (user != null) {
            vo.setAvatarUrl(user.getAvatarUrl());
        }
        return vo;
    }
}
