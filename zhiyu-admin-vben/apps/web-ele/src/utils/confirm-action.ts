import { ElMessageBox } from 'element-plus';

export interface ConfirmActionOptions {
  confirmText?: string;
  danger?: boolean;
  message: string;
  title: string;
}

export async function confirmAction({
  confirmText = '确认',
  danger = false,
  message,
  title,
}: ConfirmActionOptions): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      cancelButtonText: '取消',
      confirmButtonText: confirmText,
      distinguishCancelAndClose: true,
      type: danger ? 'warning' : 'info',
    });
    return true;
  } catch {
    return false;
  }
}
