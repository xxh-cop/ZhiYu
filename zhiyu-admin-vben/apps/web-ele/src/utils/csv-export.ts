import { ElMessage, ElMessageBox } from 'element-plus';

import { registerExportLog, type AdminExportApi } from '#/api/zhiyu/admin-export';

/**
 * 列表 CSV 导出工具。
 * - UTF-8 BOM 兼容 Excel 中文；字段按 CSV 规则转义（引号/逗号/换行）。
 * - 导出范围 = 当前筛选条件下的全部结果（前端分页拉全量，后端无导出接口）；
 *   只读拉取，不存在「半成功半失败」的写风险。
 * - 2026-11-04 N2 起：**先登记再下载**。登记（导出审计留痕）失败则中止导出，
 *   不得「先导了再说」；文件名使用服务端返回的 fileName（含批次号）；
 *   超阈值 alert 时导出后展示醒目告警。评论导出服务端不开放（3001）。
 */

export interface CsvColumn<T> {
  header: string;
  value: (row: T) => number | string | null | undefined;
}

function escapeCell(value: number | string | null | undefined): string {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

export function toCsv<T>(columns: CsvColumn<T>[], rows: T[]): string {
  const head = columns.map((c) => escapeCell(c.header)).join(',');
  const body = rows.map((row) => columns.map((c) => escapeCell(c.value(row))).join(','));
  return [head, ...body].join('\r\n');
}

export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/** 超过该条数导出需二次确认 */
export const EXPORT_CONFIRM_THRESHOLD = 1000;

/**
 * 统一导出流程：用途（必填）→ 条数预告 →（超阈值二次确认）→ 审计登记 →
 * 拉全量 → 按服务端 fileName 下载 → 超阈值告警展示。
 * 登记失败立即中止（此时未生成任何文件）。
 */
export async function exportCsvWithConfirm<T>(options: {
  columns: CsvColumn<T>[];
  fetchPage: (page: number) => Promise<{ records?: T[]; total?: number | string }>;
  /** 导出对象类型（users/works；comments 服务端不开放） */
  targetType: Exclude<AdminExportApi.TargetType, 'comments'>;
  /** 当前筛选条件的人类可读摘要（登记留痕，不含敏感值） */
  filterSummary: string;
  /** 当前筛选条件下的总条数（列表页已有 total，直接传入避免多一次请求） */
  total: number;
}): Promise<void> {
  const { total, fetchPage, columns, targetType, filterSummary } = options;
  if (total === 0) {
    ElMessage.info('当前筛选条件下没有数据，未导出');
    return;
  }

  // 1. 导出用途（必填，审计追溯）
  let purpose = '';
  try {
    const { value } = await ElMessageBox.prompt(
      `将导出当前筛选条件下的 ${total} 条记录（CSV，UTF-8 带 BOM）。请先填写导出用途：`,
      '导出确认',
      {
        confirmButtonText: '登记并导出',
        cancelButtonText: '取消',
        inputPlaceholder: '必填，200 字内（将写入导出审计留痕）',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写导出用途'),
        inputErrorMessage: '请填写导出用途',
      },
    );
    purpose = value.trim();
    if (total > EXPORT_CONFIRM_THRESHOLD) {
      await ElMessageBox.confirm(
        `记录数 ${total} 超过 ${EXPORT_CONFIRM_THRESHOLD}，导出文件较大且需要多次分页拉取。确认继续？`,
        '大批量导出确认',
        { confirmButtonText: '仍要导出', cancelButtonText: '取消', type: 'warning' },
      );
    }
  } catch {
    return; // 用户取消
  }

  // 2. 审计登记（失败即中止，不生成文件）
  let registration: AdminExportApi.RegisterResult;
  try {
    registration = await registerExportLog({
      targetType,
      filterSummary,
      fieldList: columns.map((c) => c.header).join(','),
      recordCount: total,
      purpose,
    });
  } catch {
    ElMessage.error('导出登记失败，已中止导出（未生成任何文件）。');
    return;
  }

  // 3. 分页拉全量并下载（文件名含批次号）
  const all: T[] = [];
  let page = 1;
  // 循环拉取直到拿满 total 或某页返回空（防死循环）
  while (all.length < total) {
    const res = await fetchPage(page);
    const records = res.records || [];
    if (!records.length) break;
    all.push(...records);
    page += 1;
  }
  downloadCsv(registration.fileName, toCsv(columns, all));

  // 4. 结果与告警
  if (registration.alert) {
    await ElMessageBox.alert(
      `本次导出 ${all.length} 条记录，已触发批量导出告警（批次号 ${registration.batchNo}）。该操作已留痕，可在导出审计中自查。`,
      '批量导出告警',
      { type: 'warning', confirmButtonText: '知道了' },
    );
  } else {
    ElMessage.success(`已导出 ${all.length} 条记录（批次 ${registration.batchNo}）`);
  }
}
