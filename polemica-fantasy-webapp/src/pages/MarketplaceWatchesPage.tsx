import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { fetchPerkCatalog } from '../api/perksCatalog'
import { ApiError, apiGet } from '../api/client'
import {
  useCreateMarketplaceWatch,
  useDeleteMarketplaceWatch,
  useMarketplaceWatches,
  useTournamentSubscriptions,
} from '../api/notifications'
import { fetchEconomyInfo } from '../api/userEconomy'
import type { FantasyPlayerBrief, Rarity } from '../api/types'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { useInitData } from '../context/useInitData'

const RARITY_OPTIONS: Rarity[] = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY']

export function MarketplaceWatchesPage() {
  const initData = useInitData()
  const watchesQ = useMarketplaceWatches()
  const subscriptionsQ = useTournamentSubscriptions()
  const createM = useCreateMarketplaceWatch()
  const deleteM = useDeleteMarketplaceWatch()

  const playersQ = useQuery({
    queryKey: ['fantasy-players', initData],
    queryFn: () => apiGet<FantasyPlayerBrief[]>('/api/v1/fantasy-players', initData),
    enabled: !!initData,
  })

  const perksQ = useQuery({
    queryKey: ['perks-catalog', initData],
    queryFn: () => fetchPerkCatalog(initData!),
    enabled: !!initData,
  })

  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
  })

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [playerSearch, setPlayerSearch] = useState('')
  const [playerId, setPlayerId] = useState('')
  const [tournamentId, setTournamentId] = useState('')
  const [rarity, setRarity] = useState<Rarity | ''>('')
  const [selectedPerkIds, setSelectedPerkIds] = useState<string[]>([])
  const [contractFilter, setContractFilter] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  if (!initData) return <MissingInitDataNotice />

  const watches = watchesQ.data?.watches ?? []
  const maxWatches = watchesQ.data?.maxWatches ?? 10
  const atLimit = watches.length >= maxWatches
  const deletingId = deleteM.variables ?? null
  const createError =
    createM.error instanceof ApiError ? createM.error.message : createM.error ? String(createM.error) : null
  const deleteError =
    deleteM.error instanceof ApiError ? deleteM.error.message : deleteM.error ? String(deleteM.error) : null
  const tournamentOptions = subscriptionsQ.data?.availableTournaments ?? []

  const filteredPlayers = useMemo(() => {
    const term = playerSearch.trim().toLowerCase()
    const players = playersQ.data ?? []
    if (!term) return players
    return players.filter((player) => player.nickname.toLowerCase().includes(term))
  }, [playersQ.data, playerSearch])

  function resetForm() {
    setPlayerSearch('')
    setPlayerId('')
    setTournamentId('')
    setRarity('')
    setSelectedPerkIds([])
    setContractFilter('')
    setMaxPrice('')
    setFormError(null)
  }

  function openForm() {
    setIsFormOpen(true)
    setFormError(null)
  }

  function closeForm() {
    setIsFormOpen(false)
    setFormError(null)
  }

  function submitForm() {
    const hasCriteria =
      playerId !== '' ||
      tournamentId !== '' ||
      rarity !== '' ||
      selectedPerkIds.length > 0 ||
      contractFilter !== ''
    if (!hasCriteria) {
      setFormError('Выберите хотя бы один фильтр: игрок, турнир, редкость, контракт или перк.')
      return
    }

    const parsedMaxPrice = maxPrice.trim() === '' ? null : Number(maxPrice)
    if (parsedMaxPrice != null && (!Number.isFinite(parsedMaxPrice) || parsedMaxPrice <= 0)) {
      setFormError('Максимальная цена должна быть положительным числом.')
      return
    }

    setFormError(null)
    createM.mutate(
      {
        fantasyPlayerId: playerId === '' ? null : Number(playerId),
        tournamentId: tournamentId === '' ? null : Number(tournamentId),
        rarity: rarity || null,
        maxPrice: parsedMaxPrice == null ? null : Math.floor(parsedMaxPrice),
        minTimesRenewed: contractFilter === '' ? null : Number(contractFilter),
        maxTimesRenewed: contractFilter === '' ? null : Number(contractFilter),
        perkIds: selectedPerkIds,
      },
      {
        onSuccess: () => {
          resetForm()
          closeForm()
        },
      },
    )
  }

  return (
    <div className="pf-page">
      <PageHeader title={`Отслеживание карт (${watches.length} из ${maxWatches})`} backTo="/notifications" backLabel="Уведомления" />

      {watchesQ.isLoading && <p className="pf-muted">Загрузка фильтров…</p>}
      {watchesQ.isError && <p className="pf-err">{(watchesQ.error as Error).message}</p>}
      {deleteError && <p className="pf-err">{deleteError}</p>}

      <ul className="pf-notify-watch-list">
        {watches.map((watch) => {
          const player = watch.fantasyPlayer?.nickname ?? 'Любой игрок'
          const tournament = watch.tournament?.name ?? 'Любой турнир'
          const watchRarity = watch.rarity ?? 'Любая редкость'
          const price = watch.maxPrice != null ? `до ${watch.maxPrice} ₣` : 'любая цена'
          const contract =
            watch.minTimesRenewed != null && watch.maxTimesRenewed != null && watch.minTimesRenewed === watch.maxTimesRenewed
              ? `↻ ${watch.minTimesRenewed}`
              : watch.minTimesRenewed != null || watch.maxTimesRenewed != null
                ? `↻ ${watch.minTimesRenewed ?? 0}–${watch.maxTimesRenewed ?? '∞'}`
                : 'любой контракт'
          const perks =
            watch.perks.length > 0
              ? watch.perks.map((perk) => perk.name).join(', ')
              : 'любые перки'
          return (
            <li key={watch.id} className="pf-notify-watch">
              <div className="pf-notify-watch__text">
                {player} · {watchRarity} · {price}
                <div className="pf-notify-watch__meta">
                  {tournament} · {contract} · {perks}
                </div>
              </div>
              <button
                type="button"
                className="pf-notify-watch__remove"
                aria-label="Удалить фильтр"
                disabled={deleteM.isPending && deletingId === watch.id}
                onClick={() => deleteM.mutate(watch.id)}
              >
                ✕
              </button>
            </li>
          )
        })}
      </ul>

      {!watchesQ.isLoading && !watchesQ.isError && watches.length === 0 && (
        <p className="pf-muted">У вас пока нет фильтров отслеживания.</p>
      )}

      <button
        type="button"
        className="pf-btn pf-btn--block pf-btn--primary"
        onClick={() => (isFormOpen ? closeForm() : openForm())}
        disabled={atLimit && !isFormOpen}
      >
        {isFormOpen ? 'Скрыть форму' : '+ Добавить фильтр'}
      </button>

      {atLimit && !isFormOpen && <p className="pf-muted">Достигнут лимит фильтров.</p>}

      {isFormOpen && (
        <section className="pf-notify-form">
          <h2 className="pf-notify-form__title">Новый фильтр отслеживания</h2>

          <label className="pf-field">
            <span className="pf-field__label">Поиск игрока</span>
            <input
              type="search"
              className="pf-input"
              placeholder="Начните вводить ник"
              value={playerSearch}
              onChange={(e) => setPlayerSearch(e.target.value)}
            />
          </label>

          <label className="pf-field">
            <span className="pf-field__label">Игрок</span>
            <select className="pf-input" value={playerId} onChange={(e) => setPlayerId(e.target.value)}>
              <option value="">Не выбран</option>
              {filteredPlayers.map((player) => (
                <option key={player.id} value={String(player.id)}>
                  {player.nickname}
                </option>
              ))}
            </select>
          </label>

          <label className="pf-field">
            <span className="pf-field__label">Турнир</span>
            <select
              className="pf-input"
              value={tournamentId}
              onChange={(e) => setTournamentId(e.target.value)}
            >
              <option value="">Не выбран</option>
              {tournamentOptions.map((tournament) => (
                <option key={tournament.tournamentId} value={String(tournament.tournamentId)}>
                  {tournament.tournamentName}
                </option>
              ))}
            </select>
          </label>

          <label className="pf-field">
            <span className="pf-field__label">Редкость</span>
            <select className="pf-input" value={rarity} onChange={(e) => setRarity(e.target.value as Rarity | '')}>
              <option value="">Любая</option>
              {RARITY_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>

          <label className="pf-field">
            <span className="pf-field__label">Перки</span>
            <select
              className="pf-input"
              multiple
              value={selectedPerkIds}
              onChange={(e) => {
                setSelectedPerkIds(
                  Array.from(e.currentTarget.selectedOptions, (option) => option.value),
                )
              }}
              disabled={perksQ.isLoading}
            >
              {(perksQ.data ?? []).map((perk) => (
                <option key={perk.id} value={perk.id}>
                  {perk.name}
                </option>
              ))}
            </select>
          </label>

          <label className="pf-field">
            <span className="pf-field__label">Контракт</span>
            <select
              className="pf-input"
              value={contractFilter}
              onChange={(e) => setContractFilter(e.target.value)}
              disabled={economyQ.isLoading}
            >
              <option value="">Любой</option>
              {Array.from({ length: Math.max(0, economyQ.data?.maxRenewals ?? 0) }, (_, i) => (
                <option key={i} value={String(i)}>
                  ↻ {i}/{economyQ.data?.maxRenewals ?? 0}
                </option>
              ))}
            </select>
          </label>

          <label className="pf-field">
            <span className="pf-field__label">Макс. цена</span>
            <input
              className="pf-input"
              inputMode="numeric"
              placeholder="Необязательно"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
            />
          </label>

          {(formError || createError) && <p className="pf-err">{formError ?? createError}</p>}
          {playersQ.isLoading && <p className="pf-muted">Загрузка списка игроков…</p>}
          {perksQ.isLoading && <p className="pf-muted">Загрузка списка перков…</p>}

          <button
            type="button"
            className="pf-btn pf-btn--primary"
            disabled={createM.isPending}
            onClick={submitForm}
          >
            {createM.isPending ? 'Сохранение…' : 'Сохранить'}
          </button>
        </section>
      )}
    </div>
  )
}
