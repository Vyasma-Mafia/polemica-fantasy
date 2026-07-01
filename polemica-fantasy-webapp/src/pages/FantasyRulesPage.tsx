import { Link, useParams } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'

export function FantasyRulesPage() {
  const { tournamentId } = useParams<{ tournamentId: string }>()
  const back = `/tournaments/${tournamentId}`

  return (
    <div className="pf-page">
      <PageHeader title="Правила фэнтези" backTo={back} />

      <article className="pf-prose">
        <h2>Состав команды</h2>
        <p>На каждую серию вы выставляете ровно три карточки игроков из своей коллекции. Карточки должны относиться к игрокам, включённым в состав этой серии.</p>

        <h2>Дедлайн</h2>
        <p>Изменить состав можно до момента дедлайна серии («Дедлайн» на экране выбора серии). После дедлайна команда фиксируется для расчёта очков.</p>

        <h2>Очки</h2>
        <p>Итог команды складывается из очков по каждой карточке за игры серии: базовые очки игрока плюс бонусы за перки, заданные на карточке.</p>

        <h2>Лидерборд</h2>
        <p>Вкладка «Общий» суммирует очки по всем сериям турнира. Отдельные вкладки показывают рейтинг внутри одной серии.</p>
      </article>

      <p className="pf-footer-link">
        <Link to={back}>← К турниру</Link>
      </p>
    </div>
  )
}
