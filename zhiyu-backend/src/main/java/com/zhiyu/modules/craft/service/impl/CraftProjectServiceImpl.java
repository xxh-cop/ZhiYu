package com.zhiyu.modules.craft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.craft.takedown.NoticeTemplateService;
import com.zhiyu.modules.craft.takedown.TakedownProperties;
import com.zhiyu.modules.craft.takedown.TakedownStateResolver;
import com.zhiyu.modules.craft.takedown.TakedownStateResolver.TakedownView;
import com.zhiyu.modules.craft.vo.CraftProjectStepsVO;
import com.zhiyu.modules.craft.vo.CraftStepVO;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 手作项目服务实现（T1.9b-backend）。
 *
 * <p>2026-11-05 分层下架：listSteps 从「非上架即 1004」改为结构化响应。
 * 状态口径由 {@link TakedownStateResolver} 统一推导，提示语按 notice_key
 * 从 {@code resources/notices/*.md} 渲染（代码不硬编码文案）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraftProjectServiceImpl implements CraftProjectService {

    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CraftProjectMapper craftProjectMapper;
    private final CraftStepMapper craftStepMapper;
    private final CraftSessionMapper craftSessionMapper;
    private final com.zhiyu.modules.file.MediaService mediaService;
    private final TakedownProperties takedownProperties;
    private final NoticeTemplateService noticeTemplateService;

    @Override
    public CraftProjectStepsVO listSteps(Long projectId) {
        // 1. 仅「不存在 / 待审」保持 1004（不泄露存在性）；下架/归档走结构化只读视图
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null || project.getStatus() == null || project.getStatus() == 0) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        Long uid = UserContext.getUserId();
        TakedownView view = TakedownStateResolver.resolve(project, takedownProperties,
                java.time.LocalDateTime.now());

        // 2. 查步骤（按 stepNo 升序，逻辑删除自动过滤）；下架/归档均可回看
        LambdaQueryWrapper<CraftStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftStep::getProjectId, projectId)
                .orderByAsc(CraftStep::getStepNo);
        List<CraftStep> steps = craftStepMapper.selectList(wrapper);

        CraftProjectStepsVO vo = new CraftProjectStepsVO();
        vo.setSteps(steps.stream().map(step -> toVO(uid, step)).collect(Collectors.toList()));
        vo.setProjectStatus(view.effectiveStatus());
        vo.setReadOnly(view.readOnly());
        vo.setCanReview(view.canReview());
        vo.setCanExport(view.canExport());
        // exportUntil 仅合规下架落库，运营下架天然为 null，原样下发即可
        vo.setExportUntil(project.getExportUntil());
        // 已归档但 Worker 尚未落库（读路径推导为先）时，archiveAt 用推导截止时间
        vo.setArchiveAt(project.getArchiveAt() != null ? project.getArchiveAt()
                : (view.effectiveStatus() == 3 ? view.deadline() : null));
        vo.setNoticeKey(view.noticeKey());
        vo.setNotice(renderNotice(view, project));

        // 3. 当前用户在该项目最近一个会话（供前端定位「我的创作记录」；匿名/无会话为 null）
        if (uid != null) {
            LambdaQueryWrapper<CraftSession> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.eq(CraftSession::getUserId, uid)
                    .eq(CraftSession::getProjectId, projectId)
                    .orderByDesc(CraftSession::getId)
                    .last("LIMIT 1");
            CraftSession latest = craftSessionMapper.selectOne(sessionWrapper);
            if (latest != null) {
                vo.setSessionId(latest.getId());
                vo.setSessionStatus(latest.getStatus());
            }
        }
        return vo;
    }

    /** 按 notice_key 渲染提示语；模板缺失降级 null（前端用 noticeKey 兜底）。 */
    private String renderNotice(TakedownView view, CraftProject project) {
        if (view.noticeKey() == null) {
            return null;
        }
        String deadline = view.deadline() != null ? view.deadline().format(DEADLINE_FMT) : "此前";
        String exportEntry;
        if (view.canExport()) {
            // 指向真实入口：GET /api/craft/session/{id}/export（会话页「导出我的创作数据」按钮）
            exportEntry = "也可以经会话页的「导出我的创作数据」入口，下载你在该项目下的创作记录。";
        } else if (TakedownStateResolver.NOTICE_COMPLIANCE.equals(view.noticeKey())) {
            exportEntry = takedownProperties.isExportEnabled()
                    ? "导出窗口已关闭，你仍可随时回看。" : "导出功能暂未开放；窗口截止前你仍可随时回看。";
        } else {
            exportEntry = "";
        }
        return noticeTemplateService.render(view.noticeKey(),
                Map.of("title", project.getTitle() == null ? "" : project.getTitle(),
                        "deadline", deadline,
                        "exportEntry", exportEntry));
    }

    private CraftStepVO toVO(Long uid, CraftStep step) {
        CraftStepVO vo = new CraftStepVO();
        vo.setId(step.getId());
        vo.setStepNo(step.getStepNo());
        vo.setTitle(step.getTitle());
        vo.setGuide(step.getGuide());
        // 资产缺失/停用不下发播放入口（前端隐藏）；ready/未登记按 AU3 规则换签名地址
        if ("missing".equals(step.getAssetStatus()) || "disabled".equals(step.getAssetStatus())) {
            vo.setAudioUrl(null);
        } else {
            // AU3：私有媒体引用换 1h 签名播放地址（受众授权）；外链/静态资源原样下发
            vo.setAudioUrl(mediaService.playbackIfMediaRef(uid, step.getAudioUrl()));
        }
        return vo;
    }
}
