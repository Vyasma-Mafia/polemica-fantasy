import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { fetchPerkCatalog } from '../api/perksCatalog'
import { fetchLegendaryUpgradeInfo } from '../api/legendaryUpgrade'
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
  const perksQ = useQuery({
    queryKey: ['perks-catalog', initData],
    queryFn: () => fetchPerkCatalog(initData!),
    enabled: !!initData,
  })

  const legendaryInfoQ = useQuery({
    queryKey: ['legendary-upgrade-info', initData],
    queryFn: () => fetchLegendaryUpgradeInfo(initData!),
    enabled: !!initData,
  })
  const budgetLeague = economyQ.data?.leagues.BUDGET
  const budgetRewardScale = budgetLeague?.rewardScale ?? null
  const budgetValueCap = budgetLeague?.valueCap ?? null

  if (!initData) return <MissingInitDataNotice />

  return (
    <div className="pf-page">
      <PageHeader title="Справка" backTo="/" />

      <div className="pf-help">
        <section className="pf-help__section pf-help__anchor" id="global-rating">
          <h2 className="pf-help__section-title">Глобальный рейтинг</h2>
          <article className="pf-prose">
            <p>
              <strong>Глобальный рейтинг</strong> сортирует игроков по суммарной ценности:{' '}
              <strong>баланс фантиков (₣) + сумма ценностей всех ваших карт (₱)</strong>.
            </p>
            <p>
              Колонка <strong>«Призовые»</strong> показывает, сколько фантиков вы получили за награды за места в лидербордах
              серий (сумма начислений за серии). В итоговую сумму «Всего» она <strong>не входит</strong> — это отдельная
              статистика.
            </p>
            <p>
              Учитываются <strong>все</strong> карты — в коллекции, в использованных заявках, в переработанных, выставленных
              на маркетплейсе и т.д. Итог в ₱ — это «стоимость портфеля» по правилам ценности (см. раздел «Ценность
              карты»).
            </p>
            <p>
              На величину влияют: открытие паков, награды в сериях, переработка, покупка и продажа на маркетплейсе и другие
              операции, меняющие баланс или состав карт.
            </p>
            <p>
              <Link to="/rating">Открыть таблицу рейтинга</Link>
            </p>
          </article>
        </section>

        <section className="pf-help__section pf-help__anchor" id="scoring">
          <h2 className="pf-help__section-title">Подсчёт баллов</h2>
          <article className="pf-prose">
            <p>
              За каждую игру серии по карточке считается:{' '}
              <strong>(базовые очки + бонус за перки) × множитель редкости</strong>.
            </p>
            <p>
              <strong>Базовые очки</strong> — это игровые баллы Polemica для места игрока за столом в этой игре
              (те же значения, что на публичной странице матча).
            </p>
            <p>
              <strong>Бонусы</strong> начисляются только за те перки, которые привязаны к вашей карточке, и только
              если роль игрока в партии входит в список ролей перка. У конкретной карточки бонус по перку может
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

        <section className="pf-help__section pf-help__anchor" id="leagues">
          <h2 className="pf-help__section-title">Лиги</h2>
          <article className="pf-prose">
            <p>
              В серии есть как минимум <strong>Основная</strong> и <strong>Бюджетная</strong> лига. У каждой лиги отдельный
              лидерборд и отдельные награды, а итоговые выплаты по лигам суммируются.
            </p>
            <p>
              <strong>Основная лига</strong> — базовые правила без ограничения по ценности команды.{' '}
              <strong>Бюджетная лига</strong> ограничивает суммарную ценность карт.
            </p>
            <p>
              Одна и та же карта может играть в нескольких лигах серии, но тратит по <strong>1 использованию за каждую
              лигу</strong>. Если использований не хватает, карта будет недоступна для дополнительной лиги.
            </p>
            <p>
              В <strong>бюджетной лиге</strong> проверяется сумма ценностей всех карт команды: она должна быть не выше
              лимита лиги. При редактировании состава приложение сразу блокирует карты, которые не помещаются в оставшийся
              бюджет.
            </p>
            <p>
              Награда в бюджетной лиге считается по той же таблице мест, что и в основной, но с отдельным коэффициентом
              лиги. Начисления по основной и бюджетной лигам <strong>складываются</strong>.
            </p>
            <p>
              Турнирный суммарный рейтинг строится по основной лиге. Бюджетная лига остаётся отдельным зачётом внутри
              серии с собственным лидербордом и выплатами.
            </p>
            {budgetLeague && (
              <p className="pf-muted">
                Сейчас для бюджетной лиги: лимит команды <strong>{budgetValueCap != null ? `${budgetValueCap}₱` : 'без ограничения'}</strong>, коэффициент награды{' '}
                <strong>{budgetRewardScale}%</strong> от базовой награды серии. Процент берётся из экономики сервера
                (`economy_config`) и может меняться администратором.
              </p>
            )}
          </article>
          {economyQ.isLoading && <p className="pf-muted">Загрузка параметров лиг…</p>}
          {economyQ.isError && <p className="pf-err">{(economyQ.error as Error).message}</p>}
          {economyQ.data && (
            <table className="pf-economy__table">
              <thead>
                <tr>
                  <th>Лига</th>
                  <th>Кэп ценности</th>
                  <th>Множитель награды</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(economyQ.data.leagues).map(([code, cfg]) => (
                  <tr key={code}>
                    <td>{code === 'MAIN' ? 'Основная' : code === 'BUDGET' ? 'Бюджетная' : code}</td>
                    <td>{cfg.valueCap != null ? `${cfg.valueCap}₱` : 'без ограничения'}</td>
                    <td>{cfg.rewardScale}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="pf-help__section pf-help__anchor" id="perks">
          <h2 className="pf-help__section-title">Перки</h2>
          <p className="pf-muted pf-help__roles-note">
            Роли в списке — внутренние ключи ролей Polemica; учитывается роль игрока в конкретной игре.
          </p>
          {perksQ.isLoading && <p className="pf-muted">Загрузка каталога…</p>}
          {perksQ.isError && <p className="pf-err">{(perksQ.error as Error).message}</p>}
          {perksQ.data && (
            <div className="pf-help__perks">
              {perksQ.data.map((a) => (
                <div key={a.id} className="pf-help__perk">
                  <p className="pf-help__perk-name">{a.name}</p>
                  {a.description && <p className="pf-help__perk-desc">{a.description}</p>}
                  <p className="pf-help__perk-meta">
                    Базовые очки бонуса: <strong>{a.bonusPoints}</strong>
                    {' · '}
                    {occurrenceLabel(a.occurrenceType)}
                    {a.canAppearOnRandomCards ? ' · может попасть в рандом-пак' : ''}
                  </p>
                  <p className="pf-help__perk-meta">
                    Роли: {a.applicableRoles.length ? a.applicableRoles.join(', ') : '—'}
                  </p>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="pf-help__section pf-help__anchor" id="legendary">
          <h2 className="pf-help__section-title">Легендарные карты</h2>
          <article className="pf-prose">
            <p>
              Эпическую карту с <strong>двумя</strong> перками на борту можно улучшить до <strong>легендарной</strong> в
              коллекции или с экрана магазина: добавляется <strong>третий перк</strong> на выбор из каталога, редкость
              и множитель очков растут, к экземпляру карты прибавляется <strong>одно использование</strong>. Сам экземпляр (
              <code>id</code> карты) сохраняется.
            </p>
            <p>
              Базовая стоимость апгрейда:{' '}
              {legendaryInfoQ.isLoading && <span className="pf-muted">…</span>}
              {legendaryInfoQ.data && (
                <strong>{legendaryInfoQ.data.cost.toLocaleString('ru-RU')}₣</strong>
              )}
              {legendaryInfoQ.isError && <span className="pf-muted"> (не удалось загрузить)</span>}. За каждое уже
              сделанное переподписание контракта цена снижается на тот же процент, что и минимум цены на маркетплейсе.
              Улучшать можно только карту с оставшимися использованиями и <strong>не стоящую в команде по незавершённой
              серии</strong> (после финализации серии карту снова можно прокачать, если она не в активной заявке).
            </p>
            <p>
              В одной фэнтези-команде на серию допускается <strong>не больше одной</strong> легендарной карты.
            </p>
          </article>
          {legendaryInfoQ.data && (
            <table className="pf-economy__table">
              <thead>
                <tr>
                  <th>Контракт</th>
                  <th>Цена апгрейда</th>
                </tr>
              </thead>
              <tbody>
                {legendaryInfoQ.data.costTiers.map((tier) => (
                  <tr key={tier.timesRenewed}>
                    <td>↻ {tier.timesRenewed}</td>
                    <td>{tier.cost.toLocaleString('ru-RU')}₣</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="pf-help__section pf-help__anchor" id="card-merge">
          <h2 className="pf-help__section-title">Слияние карт</h2>
          <article className="pf-prose">
            <p>
              В коллекции можно собрать карту выше редкостью из дублей одного игрока:{' '}
              <strong>3 COMMON одного игрока -&gt; 1 RARE</strong> или{' '}
              <strong>3 RARE одного игрока -&gt; 1 EPIC</strong>.
            </p>
            <p>
              EPIC после слияния можно отдельно улучшить до LEGENDARY через обычный legendary upgrade. Само слияние
              LEGENDARY не создаёт.
            </p>
            <p>
              Материалы исчезают из коллекции навсегда. Результат наследует контрактную усталость:{' '}
              <strong>переподписаний = максимум среди материалов</strong>, а использования считаются как{' '}
              <strong>min(baseUses(resultRarity), сумма использований материалов)</strong>.
            </p>
            <p>
              Ценность коллекции обычно уменьшается: вы меняете три карты на одну более сильную точечную карту. Перед
              подтверждением экран слияния показывает перки, контракт, ценность, исчезающие материалы и потерю скинов.
            </p>
            <p>
              Карты на маркетплейсе нужно сначала снять с продажи. Если у нескольких материалов есть скины, переносится
              только выбранный скин, остальные сгорают вместе с материалами.
            </p>
            <p>
              Варианты случайных перков фиксируются для выбранных материалов: повторный preview того же набора не даёт
              бесплатный reroll.
            </p>
            <p>
              <Link to="/cards/merge">Открыть слияние</Link>
            </p>
          </article>
        </section>

        <section className="pf-help__section pf-help__anchor" id="card-value">
          <h2 className="pf-help__section-title">Ценность карты</h2>
          <article className="pf-prose">
            <p>
              <strong>Ценность</strong> считается по формуле:{' '}
              <strong>базовая величина по редкости + число перков на карточке × бонус за перк</strong>. База и
              бонус задаются в экономике сервера и могут меняться.
            </p>
            <p className="pf-muted">
              Ценность <strong>не равна</strong> цене на маркетплейсе (фантики) и <strong>не равна</strong> награде за
              переработку — это отдельные величины.
            </p>
          </article>
          {economyQ.isLoading && <p className="pf-muted">Загрузка таблицы…</p>}
          {economyQ.isError && <p className="pf-err">{(economyQ.error as Error).message}</p>}
          {economyQ.data?.cardValues && (
            <div className="pf-economy">
              <section className="pf-economy__section">
                <h3 className="pf-economy__h">База и примеры итого (₱)</h3>
                <p className="pf-muted" style={{ marginBottom: 8 }}>
                  Бонус за один перк: <strong>{economyQ.data.cardValues.perkBonus}₱</strong>. В колонке «Перки»
                  — суммарный бонус за указанное число перков; итог = база + эта сумма.
                </p>
                <table className="pf-economy__table">
                  <thead>
                    <tr>
                      <th>Редкость</th>
                      <th>База (₱)</th>
                      <th>Перки (пример)</th>
                      <th>Итого (₱)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {RARITIES.map((r, i) => {
                      const base = economyQ.data!.cardValues.baseValues[r] ?? 0
                      const perkCount = i
                      const perkPart = perkCount * economyQ.data!.cardValues.perkBonus
                      const total = base + perkPart
                      return (
                        <tr key={r}>
                          <td>{r}</td>
                          <td>{base}</td>
                          <td>
                            {perkCount === 0
                              ? '0'
                              : `${perkPart} (${perkCount} ${perkCount === 1 ? 'перк' : 'перка'})`}
                          </td>
                          <td>
                            <strong>{total}</strong>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </section>
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
                <p className="pf-muted">
                  Место — итоговая позиция команды в лидерборде серии. Диапазоны: 1-е, 2-е, 3-е, 4–10, 11–25, 26–50,
                  остальные участники. Конкретные суммы — ниже; при неполном составе команды награда может быть ниже (см.
                  раздел «Подсчёт баллов»).
                </p>
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
                <p className="pf-muted">
                  Игрок переподписывает свой контракт не больше {economyQ.data!.maxRenewals} раз. После лимита и расхода
                  энергии он устает от мафии и уходит на покой.
                </p>
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
              <section className="pf-economy__section">
                <h3 className="pf-economy__h">Маркетплейс</h3>
                <p className="pf-muted">
                  На маркетплейсе можно продавать и покупать карты за фантики. С продавца удерживается комиссия{' '}
                  <strong>{economyQ.data!.marketplaceCommissionPercent}%</strong> от цены сделки.
                </p>
                <p className="pf-muted">
                  При покупке игрок переподписывает свой контракт: карта получает полный запас энергии, а счетчик ↻
                  увеличивается. После лимита переподписаний и расхода энергии игрок устает от мафии и уходит на покой:
                  такую карту больше нельзя продлить или снова продать.
                </p>
                <p className="pf-muted">
                  Покупать карты на маркетплейсе можно только после того, как в магазине открыто не менее{' '}
                  <strong>{economyQ.data!.minPackOpensBeforeMarketplacePurchase}</strong> паков (считаются успешные
                  открытия паков).
                </p>
                <p className="pf-muted">
                  Запрещено пользоваться маркетплейсом для переноса фантиков или карт с одного аккаунта на другой, в том
                  числе между своими аккаунтами. Такие сделки нарушают честную конкуренцию и могут повлечь блокировку
                  маркетплейса и иные санкции.
                </p>
                <p className="pf-muted">
                  Диапазон цены листинга по редкости задаётся экономикой сервера (минимум и максимум в таблице ниже; не
                  зависят от стоимости продления контракта). При повторных переподписаниях минимальная цена конкретной
                  карты может быть ниже базового минимума. В этом обновлении комиссия маркетплейса стала 15%, а
                  минимумы для младших карт снижены: COMMON 20₣, RARE 40₣, EPIC 120₣.
                </p>
                <table className="pf-economy__table">
                  <thead>
                    <tr>
                      <th>Редкость</th>
                      <th>Мин. цена</th>
                      <th>Макс. цена</th>
                    </tr>
                  </thead>
                  <tbody>
                    {RARITIES.map((r) => (
                      <tr key={r}>
                        <td>{r}</td>
                        <td>{economyQ.data!.marketplaceMinPrices[r]}₣</td>
                        <td>{economyQ.data!.marketplaceMaxPrices[r]}₣</td>
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
