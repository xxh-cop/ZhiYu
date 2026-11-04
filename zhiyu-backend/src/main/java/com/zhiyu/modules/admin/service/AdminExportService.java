package com.zhiyu.modules.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.admin.entity.AdminExportLog;
import com.zhiyu.modules.admin.mapper.AdminExportLogMapper;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导出审计服务（2026-11-04 N2，设计见 docs/export-audit-20261104.md）。
 *
 * <p>职责：
 * <ol>
 *   <li><b>登记</b>：校验导出请求（targetType 合法、字段黑名单、用途必填），
 *       写入留痕（只追加），返回批次号与建议文件名（CSV 水印不可行的替代，§3）；</li>
 *   <li><b>批量告警</b>：recordCount 超阈值（{@code zhiyu.export.alert-threshold}，
 *       默认 5000）时 alertFlag=1 落库 + WARN 日志（不含数据原文）；</li>
 *   <li><b>评论导出不开放</b>：targetType=comments 一律 3001，开放条件见设计文档 §6。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminExportService {

    /** 目标类型白名单。 */
    private static final Set<String> TARGETS = Set.of("users", "works", "comments");

    /** 导出字段黑名单（小写包含即拒）：密码/密钥/加密列永不出库。 */
    private static final List<String> FIELD_BLACKLIST = List.of(
            "password", "secret", "cipher", "token", "api_key", "apikey");

    private final AdminExportLogMapper exportLogMapper;
    private final BusinessAccess access;

    @Value("${zhiyu.export.alert-threshold:5000}")
    private int alertThreshold;

    /**
     * 登记一次导出。
     *
     * @return data: batchNo（字符串）/ fileName（含批次的建议文件名）/ alert（是否超阈值）
     */
    public Map<String, Object> register(String targetType, String filterSummary,
                                        String fieldList, Integer recordCount, String purpose) {
        long actor = access.role(3);

        if (targetType == null || !TARGETS.contains(targetType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "导出对象类型无效");
        }
        // 评论导出暂不开放（docs/export-audit-20261104.md §6 的开放条件未齐备）
        if ("comments".equals(targetType)) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "评论导出暂未开放");
        }
        if (purpose == null || purpose.isBlank() || purpose.length() > 200) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请填写导出用途（200 字内）");
        }
        if (fieldList == null || fieldList.isBlank() || fieldList.length() > 500) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "导出字段清单无效");
        }
        String lowered = fieldList.toLowerCase();
        for (String banned : FIELD_BLACKLIST) {
            if (lowered.contains(banned)) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "导出字段包含禁止导出的敏感列：" + banned);
            }
        }
        if (filterSummary != null && filterSummary.length() > 500) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "筛选摘要过长");
        }
        int count = recordCount == null || recordCount < 0 ? 0 : recordCount;

        AdminExportLog entry = new AdminExportLog();
        entry.setActorId(actor);
        entry.setActorRole(3);
        entry.setTargetType(targetType);
        entry.setFilterSummary(filterSummary);
        entry.setFieldList(fieldList);
        entry.setRecordCount(count);
        entry.setPurpose(purpose.trim());
        entry.setBatchNo(String.valueOf(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId()));
        boolean alert = count > alertThreshold;
        entry.setAlertFlag(alert ? 1 : 0);
        exportLogMapper.insert(entry);

        String fileName = "export-" + entry.getBatchNo() + "-" + targetType
                + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        if (alert) {
            log.warn("批量导出超阈值告警: actor={}, target={}, count={}, threshold={}, batch={}",
                    actor, targetType, count, alertThreshold, entry.getBatchNo());
        }
        log.info("导出留痕: actor={}, target={}, count={}, batch={}", actor, targetType, count, entry.getBatchNo());

        Map<String, Object> data = new HashMap<>();
        data.put("batchNo", entry.getBatchNo());
        data.put("fileName", fileName);
        data.put("alert", alert);
        return data;
    }

    /** 审计自查：分页查询留痕（role=3，可选按 targetType 过滤）。 */
    public Map<String, Object> list(int page, String targetType) {
        access.role(3);
        if (page < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "分页参数无效");
        }
        if (targetType != null && !targetType.isBlank() && !TARGETS.contains(targetType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "导出对象类型无效");
        }
        Page<AdminExportLog> result = exportLogMapper.selectPage(new Page<>(page, 10),
                Wrappers.<AdminExportLog>lambdaQuery()
                        .eq(targetType != null && !targetType.isBlank(),
                                AdminExportLog::getTargetType, targetType)
                        .orderByDesc(AdminExportLog::getCreateTime)
                        .orderByDesc(AdminExportLog::getId));
        List<Map<String, Object>> records = result.getRecords().stream().map(e -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", String.valueOf(e.getId()));
            row.put("actorId", String.valueOf(e.getActorId()));
            row.put("actorRole", e.getActorRole());
            row.put("targetType", e.getTargetType());
            row.put("filterSummary", e.getFilterSummary());
            row.put("fieldList", e.getFieldList());
            row.put("recordCount", e.getRecordCount());
            row.put("purpose", e.getPurpose());
            row.put("batchNo", e.getBatchNo());
            row.put("alertFlag", e.getAlertFlag());
            row.put("createTime", e.getCreateTime() == null ? null : e.getCreateTime().toString());
            return row;
        }).toList();
        // S3（2026-11-04）：分页元数据统一为 JSON number；recordCount 为单条留痕属性，保持实体类型不动
        return Map.of("records", records, "total", Math.toIntExact(result.getTotal()));
    }
}
