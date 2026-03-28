import { Link } from 'react-router-dom'

type PageHeaderProps = {
  title: string
  subtitle?: string
  backTo?: string
  backLabel?: string
}

export function PageHeader({ title, subtitle, backTo, backLabel = 'Назад' }: PageHeaderProps) {
  return (
    <header className="pf-header">
      {backTo && (
        <Link to={backTo} className="pf-back">
          ← {backLabel}
        </Link>
      )}
      <div className="pf-header__banner">
        <h1 className="pf-header__title">{title}</h1>
        {subtitle && <p className="pf-header__sub">{subtitle}</p>}
      </div>
    </header>
  )
}
