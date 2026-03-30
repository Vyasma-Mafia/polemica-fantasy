import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../components/PageHeader'
import { fetchEconomyInfo } from '../api/userEconomy'
import { useInitData } from '../context/InitDataContext'
import type { Rarity } from '../api/types'

const RARITIES: Rarity[] = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY']

export function EconomyInfoPage() {
  const initData = useInitData()
  const q = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
  })

  if (!initData) return <p className="pf-muted">Нужен initData.</p>

  return (
    <div className="pf-page">
      <PageHeader title="Экономика" backTo="/" />
      {q.isLoading && <p className="pf-muted">Загрузка…</p>}
      {q.isError && <p className="pf-err">{(q.error as Error).message}</p>}
      {q.data && (
        <div className="pf-economy">
          <section className="pf-economy__section">
            <h2 className="pf-economy__h">Использования по редкости</h2>
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
                    <td>{q.data.usesPerRarity[r]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
          <section className="pf-economy__section">
            <h2 className="pf-economy__h">Награды за лидерборд серии</h2>
            <ul className="pf-economy__list">
              {q.data.seriesRewards.map((t) => (
                <li key={t.label}>
                  {t.label}: <strong>{t.fantiki}₣</strong>
                </li>
              ))}
            </ul>
          </section>
          <section className="pf-economy__section">
            <h2 className="pf-economy__h">Переработка</h2>
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
                    <td>{q.data.recycleValues[r]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
          <section className="pf-economy__section">
            <h2 className="pf-economy__h">Продление контракта</h2>
            <p className="pf-muted">Максимум продлений на карту: {q.data.maxRenewals}</p>
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
                    <td>{q.data.renewalCosts[r]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </div>
      )}
    </div>
  )
}
