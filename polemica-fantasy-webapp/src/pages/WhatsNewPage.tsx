import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useMarkReleaseNoteSeen, useReleaseNotes, useTrackProductEvent } from '../api/antiChurn'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'
import { formatDateShortWithTime } from '../lib/tournamentDates'

export function WhatsNewPage() {
  const initData = useInitData()
  const notesQ = useReleaseNotes()
  const markSeen = useMarkReleaseNoteSeen()
  const track = useTrackProductEvent()

  useEffect(() => {
    if (!notesQ.data) return
    const unseen = notesQ.data.notes.filter((note) => !note.seen)
    for (const note of unseen) {
      track({ eventType: 'RELEASE_NOTE_VIEW', releaseNoteId: note.id, subjectType: 'RELEASE_NOTE', subjectId: note.id })
      markSeen.mutate(note.id)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [notesQ.data])

  if (!initData) return <MissingInitDataNotice />
  if (notesQ.isLoading) return <p className="pf-loading">Загрузка…</p>
  if (notesQ.isError) return <p className="pf-err">{(notesQ.error as Error).message}</p>

  const notes = notesQ.data?.notes ?? []

  return (
    <div className="pf-page">
      <PageHeader title="Что нового" backTo="/" backLabel="Турниры" />
      {notes.length === 0 ? (
        <section className="pf-empty-state">
          <p className="pf-muted">Пока нет опубликованных новостей.</p>
          <Link className="pf-btn pf-btn--outline" to="/">
            На главную
          </Link>
        </section>
      ) : (
        <div className="pf-release-list">
          {notes.map((note) => (
            <article key={note.id} className={`pf-release-card ${note.seen ? '' : 'pf-release-card--new'}`}>
              <div className="pf-release-card__head">
                <h2>{note.title}</h2>
                <time>{formatDateShortWithTime(new Date(note.publishedAt))}</time>
              </div>
              <p>{note.body}</p>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
