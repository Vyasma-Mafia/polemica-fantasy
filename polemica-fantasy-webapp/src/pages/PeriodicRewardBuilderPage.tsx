import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { fetchPerkCatalog } from '../api/perksCatalog'
import {
  fetchPeriodicRatingReward,
  fetchPeriodicRatingRewardPlayers,
  savePeriodicRatingRewardDraft,
  submitPeriodicRatingReward,
  type PeriodicRatingReward,
  type PeriodicRatingRewardPlayer,
} from '../api/periodicRatings'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { PlayerImage } from '../components/PlayerImage'
import { TrophyCardPreview } from '../components/TrophyCardPreview'
import { useInitData } from '../context/useInitData'

const skinNames: Record<string, string> = { aurora: 'Аврора', crimson: 'Багрянец', nocturne: 'Ноктюрн' }

function skinName(code: string) {
  const accent = Object.keys(skinNames).find((key) => code.endsWith(`_${key}`))
  return accent ? skinNames[accent] : code
}

function useDebounced(value: string, delay: number) {
  const [result, setResult] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setResult(value), delay)
    return () => window.clearTimeout(timer)
  }, [delay, value])
  return result
}

function statusTitle(reward: PeriodicRatingReward) {
  if (reward.status === 'REVIEW_REQUIRED') return 'Карта на проверке'
  if (reward.status === 'FULFILLED') return 'Трофейная карта готова'
  if (reward.status === 'CHANGES_REQUESTED') return 'Исправьте выбор'
  if (reward.status === 'DRAFT') return 'Продолжите создание'
  return 'Создайте свою карту'
}

export function PeriodicRewardBuilderPage() {
  const initData = useInitData()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const rewardId = Number(useParams().rewardId)
  const rewardQ = useQuery({
    queryKey: ['periodic-rating-reward', rewardId, initData],
    queryFn: () => fetchPeriodicRatingReward(rewardId, initData!),
    enabled: !!initData && Number.isFinite(rewardId),
    refetchInterval: (query) => query.state.data?.status === 'REVIEW_REQUIRED' ? 10_000 : false,
  })
  const perksQ = useQuery({ queryKey: ['perks-catalog', initData], queryFn: () => fetchPerkCatalog(initData!), enabled: !!initData })
  const reward = rewardQ.data
  const bundledPlayerSelection = reward?.policy.playerSelectionMode === 'BUNDLED_OPTIONS'
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebounced(search.trim(), 350)
  const playerSearchQuery = bundledPlayerSelection ? '' : debouncedSearch
  const [player, setPlayer] = useState<PeriodicRatingRewardPlayer | null>(null)
  const [playerId, setPlayerId] = useState<number | null>(null)
  const [perkIds, setPerkIds] = useState<string[]>([])
  const [skinCode, setSkinCode] = useState('')
  const [saveState, setSaveState] = useState<'idle' | 'dirty' | 'saved' | 'error' | 'stale'>('idle')

  useEffect(() => {
    if (!reward) return
    setPlayer(reward.selectedPlayer ?? null)
    setPlayerId(reward.selection.playerId ?? null)
    setPerkIds(reward.selection.perkIds ?? [])
    setSkinCode(reward.selection.skinCode || reward.policy.skinCodes[0] || '')
  }, [reward?.id])

  const playersQ = useInfiniteQuery({
    queryKey: ['periodic-rating-reward-players', rewardId, playerSearchQuery, initData],
    queryFn: ({ pageParam }) => fetchPeriodicRatingRewardPlayers(rewardId, initData!, playerSearchQuery, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => last.page + 1 < last.totalPages ? last.page + 1 : undefined,
    enabled: !!initData && !!reward && ['AVAILABLE', 'DRAFT', 'CHANGES_REQUESTED', 'OVERDUE'].includes(reward.status),
  })
  const players = playersQ.data?.pages.flatMap((page) => page.content) ?? []

  useEffect(() => {
    if (player || playerId == null) return
    const found = players.find((candidate) => candidate.id === playerId)
    if (found) setPlayer(found)
  }, [player, playerId, players])

  const perkMap = useMemo(() => new Map((perksQ.data ?? []).map((perk) => [perk.id, perk])), [perksQ.data])
  const selectedPerks = perkIds.map((id) => perkMap.get(id)).filter((perk): perk is NonNullable<typeof perk> => !!perk)
  const availablePerks = (reward?.policy.perkPool ?? []).map((id) => perkMap.get(id)).filter((perk): perk is NonNullable<typeof perk> => !!perk)
  const editable = reward && ['AVAILABLE', 'DRAFT', 'CHANGES_REQUESTED', 'OVERDUE'].includes(reward.status)
  const complete = !!reward && playerId != null && !!skinCode && perkIds.length === reward.policy.perkSelectionCount

  const markDirty = () => setSaveState('dirty')
  const saveMut = useMutation({
    mutationFn: () => savePeriodicRatingRewardDraft(rewardId, initData!, { playerId: playerId!, perkIds, skinCode, version: rewardQ.data!.version }),
    onSuccess: (next) => {
      qc.setQueryData(['periodic-rating-reward', rewardId, initData], next)
      qc.invalidateQueries({ queryKey: ['periodic-rating-rewards'] })
      setSaveState('saved')
    },
    onError: (error) => setSaveState(error instanceof ApiError && error.status === 409 ? 'stale' : 'error'),
  })
  const submitMut = useMutation({
    mutationFn: async () => {
      let current = rewardQ.data!
      if (saveState !== 'saved') {
        current = await savePeriodicRatingRewardDraft(rewardId, initData!, { playerId: playerId!, perkIds, skinCode, version: current.version })
        qc.setQueryData(['periodic-rating-reward', rewardId, initData], current)
      }
      return submitPeriodicRatingReward(rewardId, initData!, current.version)
    },
    onSuccess: (next) => {
      qc.setQueryData(['periodic-rating-reward', rewardId, initData], next)
      qc.invalidateQueries({ queryKey: ['periodic-rating-rewards'] })
    },
    onError: (error) => setSaveState(error instanceof ApiError && error.status === 409 ? 'stale' : 'error'),
  })
  const controlsDisabled = saveMut.isPending || submitMut.isPending
  useEffect(() => {
    if (saveState !== 'dirty' || !complete || saveMut.isPending) return
    const timer = window.setTimeout(() => saveMut.mutate(), 650)
    return () => window.clearTimeout(timer)
  }, [complete, perkIds.join(','), playerId, saveState, skinCode])

  if (!initData) return <MissingInitDataNotice />
  if (!Number.isFinite(rewardId)) return <div className="pf-page"><p className="pf-err">Некорректная награда.</p></div>
  if (rewardQ.isLoading) return <div className="pf-page"><p className="pf-loading">Загрузка награды…</p></div>
  if (rewardQ.isError || !reward) return <div className="pf-page"><PageHeader title="Награда" backTo="/rating/rewards" /><div className="pf-period-rating-empty"><h2>Награда не найдена</h2><button className="pf-btn" onClick={() => rewardQ.refetch()}>Повторить</button></div></div>

  const selectPlayer = (next: PeriodicRatingRewardPlayer) => {
    setPlayer(next); setPlayerId(next.id); markDirty()
    if (reward.policy.playerSelectionMode === 'BUNDLED_OPTIONS') {
      setPerkIds(reward.policy.bundles?.find((bundle) => bundle.playerId === next.id)?.perkIds ?? [])
    }
  }
  const togglePerk = (id: string) => {
    if (reward.policy.perkSelectionCount === 1) setPerkIds([id])
    else setPerkIds((current) => current.includes(id) ? current.filter((value) => value !== id) : current.length < reward.policy.perkSelectionCount ? [...current, id] : current)
    markDirty()
  }

  if (reward.status === 'FULFILLED') return <div className="pf-page pf-reward-builder"><PageHeader title="Ваша награда" backTo="/rating/rewards" /><section className="pf-reward-result"><span>✦</span><h1>Карта создана</h1><p>Трофей {reward.serial} теперь в вашей коллекции.</p>{reward.issuedUserCardId && <Link className="pf-btn" to={`/cards?cardId=${reward.issuedUserCardId}`}>Открыть карту</Link>}</section></div>

  const previewPlayer = player ?? (playerId == null ? null : { id: playerId, polemicaUserId: 0, nickname: `Игрок #${playerId}`, photoUrl: null })
  return <div className="pf-page pf-reward-builder">
    <PageHeader title={statusTitle(reward)} backTo="/rating/rewards" />
    <div className="pf-reward-builder__meta"><span>#{reward.rank} место</span><b>{reward.periodTitle}</b><span>{reward.serial}</span></div>
    {reward.status === 'CHANGES_REQUESTED' && <div className="pf-reward-changes"><strong>Администратор попросил изменить выбор</strong><p>{reward.changesRequestedReason || 'Проверьте выбранного игрока, перки и оформление.'}</p></div>}
    {reward.status === 'REVIEW_REQUIRED' && <div className="pf-reward-review"><span className="pf-reward-review__icon">✓</span><h2>Заявка отправлена</h2><ol><li className="is-done">Вы выбрали карту</li><li className="is-current">Проверяем соответствие награде</li><li>Выпустим карту в коллекцию</li></ol><p>Выбор сейчас доступен только для просмотра.</p></div>}

    <div className="pf-reward-builder__preview">
      <TrophyCardPreview player={previewPlayer} rarity={reward.policy.rarity} skinCode={skinCode} perks={selectedPerks} editionTier={reward.policy.editionTier} serial={reward.serial} rank={reward.rank} />
    </div>

    {editable && <div className="pf-reward-builder__steps">
      <section className="pf-reward-step"><div className="pf-reward-step__title"><span>1</span><div><h2>Игрок</h2><p>{bundledPlayerSelection ? 'Выберите один из вариантов награды' : 'Поиск по нику или ID Polemica'}</p></div></div>
        {bundledPlayerSelection
          ? <p className="pf-reward-bundle-note">Для вашего места доступны три актуальные комбинации игрока и перка.</p>
          : <label className="pf-reward-search"><span>⌕</span><input disabled={controlsDisabled} value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Например, mralex или 12345" inputMode="search" /></label>}
        {playersQ.isLoading && <p className="pf-loading">Ищем игроков…</p>}
        {playersQ.isError && <div className="pf-inline-error">Не удалось загрузить игроков. <button onClick={() => playersQ.refetch()}>Повторить</button></div>}
        {!playersQ.isLoading && !playersQ.isError && players.length === 0 && <p className="pf-reward-empty">{bundledPlayerSelection ? 'Варианты награды не сформированы.' : 'Никого не нашли. Проверьте ник или числовой ID.'}</p>}
        <div className="pf-reward-player-list">{players.map((candidate) => <button disabled={controlsDisabled} key={candidate.id} className={playerId === candidate.id ? 'is-selected' : ''} onClick={() => selectPlayer(candidate)}><PlayerImage src={candidate.photoUrl} seedId={candidate.id} variant="avatar" /><span><b>{candidate.nickname}</b><small>Polemica ID {candidate.polemicaUserId}</small></span><i>{playerId === candidate.id ? '✓' : '›'}</i></button>)}</div>
        {playersQ.hasNextPage && <button className="pf-reward-more" disabled={playersQ.isFetchingNextPage} onClick={() => playersQ.fetchNextPage()}>{playersQ.isFetchingNextPage ? 'Загрузка…' : 'Показать ещё'}</button>}
      </section>

      {reward.policy.perkSelectionMode !== 'NONE' && <section className="pf-reward-step"><div className="pf-reward-step__title"><span>2</span><div><h2>Перки</h2><p>Выберите {reward.policy.perkSelectionCount} {reward.policy.perkSelectionCount === 1 ? 'перк' : 'перка'}</p></div></div>
        {reward.policy.perkSelectionMode === 'BUNDLED_OPTIONS' ? <p className="pf-reward-bundle-note">Перк закреплён за выбранным вариантом игрока.</p> : <div className="pf-reward-perks">{availablePerks.map((perk) => <button disabled={controlsDisabled} key={perk.id} className={perkIds.includes(perk.id) ? 'is-selected' : ''} onClick={() => togglePerk(perk.id)}><span><b>{perk.name}</b><small>{perk.description || perk.id}</small></span><strong>+{perk.bonusPoints}</strong></button>)}</div>}
      </section>}

      <section className="pf-reward-step"><div className="pf-reward-step__title"><span>{reward.policy.perkSelectionMode === 'NONE' ? 2 : 3}</span><div><h2>Уникальный скин</h2><p>Все варианты доступны только победителям периода</p></div></div>
        <div className="pf-reward-skins">{reward.policy.skinCodes.map((code) => <button disabled={controlsDisabled} key={code} className={`pf-reward-skin pf-reward-skin--${code}${skinCode === code ? ' is-selected' : ''}`} onClick={() => { setSkinCode(code); markDirty() }}><i /><b>{skinName(code)}</b><small>{code}</small></button>)}</div>
      </section>

      <section className="pf-reward-submit"><div><span>Награда</span><b>#{reward.rank} · {reward.policy.rarity}</b></div><div><span>Серийный номер</span><b>{reward.serial}</b></div>
        {saveState === 'saved' && <p className="pf-save-state pf-save-state--ok">✓ Черновик сохранён</p>}
        {saveState === 'error' && <p className="pf-save-state pf-save-state--error">Не удалось сохранить. Ваш выбор остался на экране — попробуйте ещё раз.</p>}
        {saveState === 'stale' && <p className="pf-save-state pf-save-state--error">Награда изменилась на другом устройстве. <button onClick={async () => { const result = await rewardQ.refetch(); if (result.data) { setPlayer(null); setPlayerId(result.data.selection.playerId ?? null); setPerkIds(result.data.selection.perkIds ?? []); setSkinCode(result.data.selection.skinCode || result.data.policy.skinCodes[0] || ''); setSaveState('idle') } }}>Загрузить свежую версию</button></p>}
        <button className="pf-btn pf-btn--secondary" disabled={!complete || saveMut.isPending} onClick={() => saveMut.mutate()}>{saveMut.isPending ? 'Сохраняем…' : 'Сохранить черновик'}</button>
        <p className="pf-reward-submit__note">Карта сразу появится в коллекции. После подтверждения изменить игрока, перки и скин нельзя.</p>
        <button className="pf-btn" disabled={!complete || saveMut.isPending || submitMut.isPending} onClick={() => submitMut.mutate()}>{submitMut.isPending ? 'Создаём карту…' : 'Подтвердить и получить карту'}</button>
        <button className="pf-link-button" onClick={() => navigate('/rating/rewards')}>Вернуться к наградам</button>
      </section>
    </div>}
  </div>
}
