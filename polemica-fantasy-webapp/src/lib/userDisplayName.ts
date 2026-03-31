/** Отображаемое имя: кастомный ник, затем Telegram first_name, username, числовой id. */
export function formatUserDisplayName(user: {
  displayName: string | null
  firstName: string | null
  username: string | null
  telegramId: number
}): string {
  return user.displayName ?? user.firstName ?? user.username ?? String(user.telegramId)
}
