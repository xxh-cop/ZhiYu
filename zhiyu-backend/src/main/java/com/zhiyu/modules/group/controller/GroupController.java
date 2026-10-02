package com.zhiyu.modules.group.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.group.dto.GroupCreateReq;
import com.zhiyu.modules.group.service.GroupService;
import com.zhiyu.modules.group.vo.GroupVO;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 互助小组控制器（T1.8b）。
 *
 * <p>GET  /api/groups            —— 小组列表
 * <p>POST /api/groups            —— 创建小组
 * <p>POST /api/groups/{id}/join  —— 加入小组
 */
@Slf4j
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public Result<List<GroupVO>> listGroups() {
        Long userId = UserContext.getUserId();
        return Result.success(groupService.listGroups(userId));
    }

    @PostMapping
    public Result<GroupVO> createGroup(@Valid @RequestBody GroupCreateReq req) {
        Long userId = UserContext.getUserId();
        return Result.success(groupService.createGroup(userId, req));
    }

    @PostMapping("/{id}/join")
    public Result<GroupVO> joinGroup(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.success(groupService.joinGroup(userId, id));
    }
}
