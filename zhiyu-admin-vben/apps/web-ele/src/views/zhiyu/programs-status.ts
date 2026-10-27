export const PROGRAM_STATUS_FLOW: Record<string, string[]> = {
  DRAFT: ['DRAFT', 'PUBLISHED', 'CANCELLED'],
  PUBLISHED: ['PUBLISHED', 'CANCELLED'],
  CANCELLED: ['CANCELLED'],
};

export function allowedStatuses(status: string): string[] {
  return PROGRAM_STATUS_FLOW[status] ?? ['DRAFT'];
}
