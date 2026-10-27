export function homePathForRoles(roles: string[] = []): string {
  if (roles.includes('ROLE_3')) {
    return '/zhiyu/dashboard';
  }
  if (roles.includes('ROLE_2')) {
    return '/zhiyu/business/care-reminders';
  }
  if (roles.includes('ROLE_1')) {
    return '/zhiyu/programs';
  }
  return '/zhiyu/dashboard';
}
