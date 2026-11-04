/**
 * 表单可达性工具：校验失败后把键盘焦点移到第一个错误字段。
 * 配合 el-form 的 scroll-to-error（负责滚动），本函数负责焦点。
 * 在 validate().catch(...) 分支里 await 后调用。
 */
export async function focusFirstError(scope?: HTMLElement | null): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  const root = scope ?? document.body;
  const target = root.querySelector<HTMLElement>(
    '.is-error input, .is-error textarea, .is-error .el-select__wrapper, .is-error [tabindex]',
  );
  target?.focus();
}
