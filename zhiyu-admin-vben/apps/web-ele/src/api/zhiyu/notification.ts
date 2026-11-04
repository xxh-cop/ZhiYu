import { requestClient } from '#/api/request';

/**
 * 通知中心接口（契约见 openapi/openapi.yaml /api/notifications/**）。
 * 注意：
 * - id / refId 是字符串型雪花 ID，禁止 Number() 转换，原样回传；
 * - unread / total / updated 是 JSON number；
 * - 未读数有 5 秒服务端缓存，UI 不承诺实时推送。
 */
export namespace NotificationApi {
  /** 与后端 notification.type 枚举一一对应 */
  export type NotificationType =
    | 'BOOKING_REPLY'
    | 'CARE_REMINDER'
    | 'COMMENT_AUDIT'
    | 'GROUP_JOIN'
    | 'WORK_AUDIT';

  export type RefType =
    | 'booking'
    | 'care_reminder'
    | 'comment'
    | 'group'
    | 'project'
    | 'work';

  export interface NotificationItem {
    id: string;
    type: NotificationType | string;
    title: string;
    content: string;
    refType: RefType | string;
    refId: string;
    read: boolean;
    createTime: string;
  }

  export interface ListParams {
    page: number;
    /** 服务端参数名为 size（非 pageSize） */
    size: number;
    type?: string;
  }

  export interface ListResult {
    records?: NotificationItem[];
    total?: number;
  }
}

export async function getUnreadCount() {
  return requestClient.get<{ unread: number }>('/notifications/unread-count');
}

export async function getNotifications(params: NotificationApi.ListParams) {
  return requestClient.get<NotificationApi.ListResult>('/notifications', { params });
}

/** 单条已读（幂等） */
export async function markNotificationRead(id: string) {
  return requestClient.post(`/notifications/${id}/read`);
}

/** 全部已读（幂等），返回更新条数 */
export async function markAllNotificationsRead() {
  return requestClient.post<{ updated: number }>('/notifications/read-all');
}
