package com.zhiyu.modules.auth.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.auth.dto.CanContactDTO;
import com.zhiyu.modules.auth.dto.DeleteAccountDTO;
import com.zhiyu.modules.auth.service.UserPrivacyService;
import com.zhiyu.modules.auth.vo.PrivacyStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户隐私合规接口（M3-B1），对应 {@code /api/user/**}。
 *
 * <p>满足《个人信息保护法》的单独同意、可撤回、可删除要求。
 * 所有接口均需认证（{@code SecurityConfig} 默认 {@code authenticated()}）。
 *
 * <ul>
 *   <li>{@code GET  /api/user/privacy/status} — 查询隐私状态</li>
 *   <li>{@code POST /api/user/privacy/agree} — 同意隐私政策</li>
 *   <li>{@code POST /api/user/privacy/withdraw} — 撤回隐私政策同意</li>
 *   <li>{@code PUT  /api/user/can-contact} — 更新可联系状态</li>
 *   <li>{@code POST /api/user/delete} — 注销账号</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserPrivacyController {

    private final UserPrivacyService userPrivacyService;

    /** 接口 1：查询隐私状态 */
    @GetMapping("/privacy/status")
    public Result<PrivacyStatusVO> getPrivacyStatus() {
        return Result.success(userPrivacyService.getPrivacyStatus());
    }

    /** 接口 2：同意隐私政策 */
    @PostMapping("/privacy/agree")
    public Result<Void> agreePrivacy() {
        userPrivacyService.agreePrivacy();
        return Result.success();
    }

    /** 接口 3：撤回隐私政策同意 */
    @PostMapping("/privacy/withdraw")
    public Result<Void> withdrawPrivacy() {
        userPrivacyService.withdrawPrivacy();
        return Result.success();
    }

    /** 接口 4：更新可联系状态 */
    @PutMapping("/can-contact")
    public Result<Void> updateCanContact(@Valid @RequestBody CanContactDTO dto) {
        userPrivacyService.updateCanContact(dto);
        return Result.success();
    }

    /** 接口 5：注销账号（二次确认 + 匿名化 + 逻辑删除） */
    @PostMapping("/delete")
    public Result<Void> deleteAccount(@Valid @RequestBody DeleteAccountDTO dto) {
        userPrivacyService.deleteAccount();
        return Result.success();
    }
}
