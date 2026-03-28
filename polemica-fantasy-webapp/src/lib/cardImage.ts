import type { UserCardItem } from '../api/types'

/** Основное изображение карточки — фото игрока; при отсутствии — картинка шаблона. */
export function cardDisplayImageUrl(c: Pick<UserCardItem, 'playerPhotoUrl' | 'imageUrl'>): string | null {
  return c.playerPhotoUrl ?? c.imageUrl ?? null
}
