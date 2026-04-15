import { useInitDataState } from '../context/useInitData'

export function MissingInitDataNotice() {
  const { error, pending } = useInitDataState()

  if (pending) {
    return <p className="pf-loading">Загрузка…</p>
  }

  if (error) {
    return (
      <div className="pf-card pf-card--notice">
        <p className="pf-err" style={{ marginBottom: '0.5rem' }}>
          Не удалось получить Telegram initData.
        </p>
        <pre
          className="pf-muted"
          style={{
            fontSize: '0.85em',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            margin: 0,
          }}
        >
          {error}
        </pre>
      </div>
    )
  }

  return (
    <div className="pf-card pf-card--notice">
      <p>
        Нет Telegram initData. Откройте приложение в Telegram или задайте переменную окружения
        VITE_DEV_INIT_DATA для локальной разработки.
      </p>
    </div>
  )
}
