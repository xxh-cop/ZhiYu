package com.zhiyu.modules.auth.service;

import com.zhiyu.modules.auth.dto.CanContactDTO;
import com.zhiyu.modules.auth.vo.PrivacyStatusVO;

/**
 * 用户隐私合规服务（M3-B1）。
 *
 * <p>覆盖隐私政策同意/撤回、可联系授权、账号注销等操作，
 * 满足《个人信息保护法》的单独同意、可撤回、可删除要求。
 */
public interface UserPrivacyService {

    /**
     * 查询当前用户的隐私状态（隐私政策同意 + 可联系）。
     *
     * <p>用户身份取自 {@code UserContext}，按 userId 回库查询。
     */
    PrivacyStatusVO getPrivacyStatus();

    /**
     * 同意隐私政策（{@code privacyAgreed = 1}）。
     */
    void agreePrivacy();

    /**
     * 撤回隐私政策同意：{@code privacyAgreed = 0}，同时 {@code canContact = 0}。
     *
     * <p>按隐私政策「不影响浏览手作内容」，不限制登录。
     */
    void withdrawPrivacy();

    /**
     * 更新可联系状态。仅用户本人可调。
     *
     * <p>设为 1 时，需校验已同意隐私政策（{@code privacyAgreed = 1}），否则拒绝。
     */
    void updateCanContact(CanContactDTO dto);

    /**
     * 注销账号：匿名化敏感字段 + 逻辑删除。
     *
     * <p>不物理删除数据，保留用于统计和合规追溯。
     * 注销后 username 加 {@code deleted_} 前缀避免冲突，password 清空，
     * nickname 改为「已注销用户」，status 置 0（禁用），deleted 置 1。
     */
    void deleteAccount();
}
