import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../components/PageHeader'
import { fetchAchievementCatalog } from '../api/achievementsCatalog'
import { fetchEconomyInfo } from '../api/userEconomy'
import { useInitData } from '../context/useInitData'
import type { OccurrenceType, Rarity } from '../api/types'

const RARITIES: Rarity[] = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY']

const RARITY_MODIFIERS: Record<Rarity, number> = {
  COMMON: 1.0,
  RARE: 1.1,
  EPIC: 1.15,
  LEGENDARY: 1.25,
}

function occurrenceLabel(t: OccurrenceType): string {
  if (t === 'MULTIPLE_PER_GAME') return 'каждое срабатывание за игру суммируется'
  return 'не больше одного раза за игру'
}

export function HelpPage() {
  const initData = useInitData()
  const telegramBotUsername = import.meta.env.VITE_TELEGRAM_BOT_USERNAME
  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
  })
  const achievementsQ = useQuery({
    queryKey: ['achievements-catalog', initData],
    queryFn: () => fetchAchievementCatalog(initData!),
    enabled: !!initData,
  })

  if (!initData) return <p className="pf-muted">Нужен initData.</p>

  return (
    <div className="pf-page">
      <PageHeader title="Справка" backTo="/" />

      <div className="pf-help">
        <section className="pf-help__section pf-help__anchor" id="scoring">
          <h2 className="pf-help__section-title">Подсчёт баллов</h2>
          <article className="pf-prose">
            <p>
              За каждую игру серии по карточке считается:{' '}
              <strong>(базовые очки + бонус за достижения) × множитель редкости</strong>.
            </p>
            <p>
              <strong>Базовые очки</strong> — это игровые баллы Polemica для места игрока за столом в этой игре
              (те же значения, что на публичной странице матча).
            </p>
            <p>
              <strong>Бонусы</strong> начисляются только за те достижения, которые привязаны к вашей карточке, и только
              если роль игрока в партии входит в список ролей достижения. У конкретной карточки бонус по ачивке может
              отличаться от «базы» в справочнике ниже — смотрите подсказку на карточке в коллекции.
            </p>
            <p>
              Очки по карточке за серию — складываются из всех игр серии, где игрок участвовал.{' '}
              <strong>Команда</strong> — сумма очков по выставленным карточкам (до трёх слотов).
            </p>
            <p>
              После финала серии награда фантиками за место в лидерборде может быть уменьшена, если в команде было меньше
              трёх карточек: за две — примерно две трети суммы, за одну — примерно треть (с округлением в пользу игрока).
            </p>
          </article>
          <h3 className="pf-economy__h">Множитель редкости</h3>
          <table className="pf-economy__table">
            <thead>
              <tr>
                <th>Редкость</th>
                <th>Множитель</th>
              </tr>
            </thead>
            <tbody>
              {RARITIES.map((r) => (
                <tr key={r}>
                  <td>{r}</td>
                  <td>{RARITY_MODIFIERS[r]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="pf-help__section pf-help__anchor" id="achievements">
          <h2 className="pf-help__section-title">Достижения</h2>
          <p className="pf-muted pf-help__roles-note">
            Роли в списке — внутренние ключи ролей Polemica; учитывается роль игрока в конкретной игре.
          </p>
          {achievementsQ.isLoading && <p className="pf-muted">Загрузка каталога…</p>}
          {achievementsQ.isError && <p className="pf-err">{(achievementsQ.error as Error).message}</p>}
          {achievementsQ.data && (
            <div className="pf-help__achievements">
              {achievementsQ.data.map((a) => (
                <div key={a.id} className="pf-help__achievement">
                  <p className="pf-help__achievement-name">{a.name}</p>
                  {a.description && <p className="pf-help__achievement-desc">{a.description}</p>}
                  <p className="pf-help__achievement-meta">
                    Базовые очки бонуса: <strong>{a.bonusPoints}</strong>
                    {' · '}
                    {occurrenceLabel(a.occurrenceType)}
                    {a.canAppearOnRandomCards ? ' · может попасть в рандом-пак' : ''}
                  </p>
                  <p className="pf-help__achievement-meta">
                    Роли: {a.applicableRoles.length ? a.applicableRoles.join(', ') : '—'}
                  </p>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="pf-help__section pf-help__anchor" id="economy">
          <h2 className="pf-help__section-title">Экономика</h2>
          {economyQ.isLoading && <p className="pf-muted">Загрузка…</p>}
          {economyQ.isError && <p className="pf-err">{(economyQ.error as Error).message}</p>}
          {economyQ.data && (
            <div className="pf-economy">
              <section className="pf-economy__section">
                <h3 className="pf-economy__h">Использования по редкости</h3>
                <table className="pf-economy__table">
                  <thead>
                    <tr>
                      <th>Редкость</th>
                      <th>Использований за серию</th>
                    </tr>
                  </thead>
                  <tbody>
                    {RARITIES.map((r) => (
                      <tr key={r}>
                        <td>{r}</td>
                        <td>{economyQ.data!.usesPerRarity[r]}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
              <section className="pf-economy__section">
                <h3 className="pf-economy__h">Награды за лидерборд серии</h3>
                <ul className="pf-economy__list">
                  {economyQ.data!.seriesRewards.map((t) => (
                    <li key={t.label}>
                      {t.label}: <strong>{t.fantiki}₣</strong>
                    </li>
                  ))}
                </ul>
              </section>
              <section className="pf-economy__section">
                <h3 className="pf-economy__h">Переработка</h3>
                <table className="pf-economy__table">
                  <thead>
                    <tr>
                      <th>Редкость</th>
                      <th>₣</th>
                    </tr>
                  </thead>
                  <tbody>
                    {RARITIES.map((r) => (
                      <tr key={r}>
                        <td>{r}</td>
                        <td>{economyQ.data!.recycleValues[r]}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
              <section className="pf-economy__section">
                <h3 className="pf-economy__h">Продление контракта</h3>
                <p className="pf-muted">Максимум продлений на карту: {economyQ.data!.maxRenewals}</p>
                <table className="pf-economy__table">
                  <thead>
                    <tr>
                      <th>Редкость</th>
                      <th>Стоимость (₣)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {RARITIES.map((r) => (
                      <tr key={r}>
                        <td>{r}</td>
                        <td>{economyQ.data!.renewalCosts[r]}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            </div>
          )}
        </section>

        <section className="pf-help__section" id="feedback">
          <h2 className="pf-help__section-title">Поддержка</h2>
          <article className="pf-prose">
            <p className="pf-muted">
              Вопросы и предложения по игре направляйте <strong>боту</strong> Polemica Fantasy в Telegram: откройте чат с ботом
              {telegramBotUsername ? (
                <>
                  {' '}
                  (
                  <a
                    href={`https://t.me/${telegramBotUsername}`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    @{telegramBotUsername}
                  </a>
                  )
                </>
              ) : (
                ' (через кнопку у бота или меню Telegram) '
              )}
              и напишите сообщение <strong>в чат с ботом</strong>, а не в личку другим людям. Ответ придёт от бота.
            </p>
          </article>
        </section>
      </div>
    </div>
  )
}
