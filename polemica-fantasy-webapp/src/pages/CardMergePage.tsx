import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  confirmCardMerge,
  fetchCardMergeOptions,
  fetchCardMergePreview,
} from '../api/cardMerge'
import { ApiError } from '../api/client'
import { cancelMarketplaceListing } from '../api/marketplace'
import { fetchEconomyInfo } from '../api/userEconomy'
import type {
  CardMergeBlockReason,
  CardMergeConfirmResponse,
  CardMergeMaterialCard,
  CardMergeOperation,
  CardMergeOperationOption,
  CardMergePlayerGroup,
  CardMergePreviewResponse,
  PerkCatalogItem,
  Rarity,
  UserCardItem,
} from '../api/types'
import { CardPerkChips } from '../components/CardPerkChips'
import { CardValueBadge } from '../components/CardValueBadge'
import { ContractReissueBadge } from '../components/ContractReissueBadge'
import { MarketplaceListedBadge } from '../components/MarketplaceListedBadge'
import { MissingInitDataNotice } from '../components/MissingInitDataNotice'
import { PageHeader } from '../components/PageHeader'
import { PlayerImage } from '../components/PlayerImage'
import { useInitData } from '../context/useInitData'
import { cardDisplayImageUrl } from '../lib/cardImage'
import { collectionCardRootClass } from '../lib/cardFrameClasses'
import { RARITY_UI } from '../lib/rarity'

const OPERATION_LABEL: Record<CardMergeOperation, string> = {
  COMMON_TO_RARE: 'COMMON -> RARE',
  RARE_TO_EPIC: 'RARE -> EPIC',
}

const RARITY_LABEL: Record<Rarity, string> = {
  COMMON: 'COMMON',
  RARE: 'RARE',
  EPIC: 'EPIC',
  LEGENDARY: 'LEGENDARY',
}

function reasonLabel(reason: CardMergeBlockReason | null | undefined): string {
  if (reason === 'ACTIVE_TEAM') return 'В составе на серию'
  if (reason === 'MARKETPLACE_ACTIVE') return 'На продаже'
  if (reason === 'EXPIRED_CONTRACT') return 'Контракт истёк'
  return reason ? String(reason) : 'Недоступна'
}

function rarityRu(rarity: Rarity): string {
  return RARITY_UI.find((item) => item.value === rarity)?.label ?? rarity
}

function operationRank(option: CardMergeOperationOption): number {
  if (option.eligible && option.operation === 'RARE_TO_EPIC') return 0
  if (option.eligible && option.operation === 'COMMON_TO_RARE') return 1
  if (option.availableCards.length === 2) return 2
  return 3
}

function groupRank(group: CardMergePlayerGroup): number {
  return Math.min(...group.operations.map(operationRank), 9)
}

function canUseOperation(option: CardMergeOperationOption): boolean {
  return option.eligible && option.availableCards.length >= 3
}

function selectedCardsFromIds(option: CardMergeOperationOption | undefined, selectedIds: number[]) {
  if (!option) return []
  const byId = new Map(option.availableCards.map((item) => [item.userCard.id, item.userCard]))
  return selectedIds.map((id) => byId.get(id)).filter((card): card is UserCardItem => Boolean(card))
}

function queryKeyRoot(queryKey: readonly unknown[]): string | null {
  const first = queryKey[0]
  return typeof first === 'string' ? first : null
}

function invalidateMergeRelatedQueries(qc: ReturnType<typeof useQueryClient>) {
  const roots = new Set([
    'cards',
    'me',
    'card-merge-options',
    'my-marketplace-listings',
    'marketplace-listings',
    'marketplace-analytics-summary',
    'marketplace-analytics-detail',
    'achievements',
  ])
  void qc.invalidateQueries({
    predicate: (query) => roots.has(queryKeyRoot(query.queryKey) ?? ''),
  })
}

function compactPerksFromIds(ids: string[], selectedCards: UserCardItem[], selectable: PerkCatalogItem[]) {
  const fromCards = new Map<string, UserCardItem['perks'][number]>()
  for (const card of selectedCards) {
    for (const perk of card.perks) fromCards.set(perk.perkId, perk)
  }
  const fromSelectable = new Map(selectable.map((perk) => [perk.id, perk]))
  return ids.map((id) => {
    const cardPerk = fromCards.get(id)
    if (cardPerk) return { id, name: cardPerk.perkName, bonusPoints: cardPerk.bonusPoints }
    const catalog = fromSelectable.get(id)
    return {
      id,
      name: catalog?.name ?? id,
      bonusPoints: catalog?.bonusPoints ?? 0,
      description: catalog?.description ?? null,
    }
  })
}

function perkSourceLabel(perkId: string, selectedCards: UserCardItem[]): string | null {
  const sourceIds = selectedCards
    .filter((card) => card.perks.some((perk) => perk.perkId === perkId))
    .map((card) => `#${card.id}`)
  if (sourceIds.length === 0) return null
  return `из карт ${sourceIds.join(', ')}${sourceIds.length > 1 ? ` x${sourceIds.length}` : ''}`
}

function skinLabel(card: UserCardItem): string {
  return card.skinCode ? `${card.playerNickname}, #${card.id}, ${card.skinCode}` : `${card.playerNickname}, #${card.id}`
}

function materialSort(a: CardMergeMaterialCard, b: CardMergeMaterialCard): number {
  if (Boolean(a.userCard.skinCode) !== Boolean(b.userCard.skinCode)) {
    return a.userCard.skinCode ? 1 : -1
  }
  if (a.userCard.usesRemaining !== b.userCard.usesRemaining) {
    return a.userCard.usesRemaining - b.userCard.usesRemaining
  }
  if (a.userCard.timesRenewed !== b.userCard.timesRenewed) {
    return a.userCard.timesRenewed - b.userCard.timesRenewed
  }
  return new Date(a.userCard.acquiredAt).getTime() - new Date(b.userCard.acquiredAt).getTime()
}

function MaterialCard({
  item,
  selected,
  disabled,
  maxRenewals,
  onToggle,
  onCancelListing,
  cancelingListingId,
}: {
  item: CardMergeMaterialCard
  selected: boolean
  disabled: boolean
  maxRenewals: number
  onToggle: () => void
  onCancelListing?: () => void
  cancelingListingId: number | null
}) {
  const card = item.userCard
  const blocked = item.blockReason != null
  const root = collectionCardRootClass(card, { expired: card.usesRemaining <= 0 })
  const listingId = item.listingId ?? card.activeMarketplaceListing?.listingId ?? null
  return (
    <article className={`pf-merge-material${selected ? ' is-selected' : ''}${blocked ? ' is-blocked' : ''}`}>
      <button
        type="button"
        className="pf-merge-material__pick"
        disabled={disabled}
        onClick={onToggle}
        aria-pressed={selected}
      >
        <span className={root}>
          <span className="pf-collection-card__frame">
            <PlayerImage
              src={cardDisplayImageUrl(card)}
              seedId={card.fantasyPlayerId}
              variant="card"
              className="pf-collection-card__img"
            />
            <span className="pf-uses-badge" title="Осталось использований">
              {card.usesRemaining}
            </span>
            <ContractReissueBadge
              timesRenewed={card.timesRenewed}
              maxRenewals={maxRenewals}
              layout="collection"
            />
            <CardValueBadge value={card.value} layout="collection" expired={card.usesRemaining <= 0} />
            {card.activeMarketplaceListing && (
              <MarketplaceListedBadge listing={card.activeMarketplaceListing} />
            )}
            <span className="pf-collection-card__cap">
              <span className="pf-collection-card__name">{card.playerNickname}</span>
              <span className="pf-collection-card__rarity">{RARITY_LABEL[card.rarity]}</span>
              <CardPerkChips perks={card.perks} max={2} className="pf-card-perk-chips--tight" />
            </span>
          </span>
        </span>
      </button>
      <div className="pf-merge-material__meta">
        <span>#{card.id}</span>
        {card.skinCode && <span>Скин: {card.skinCode}</span>}
        {blocked && <strong>{reasonLabel(item.blockReason)}</strong>}
      </div>
      {item.blockReason === 'MARKETPLACE_ACTIVE' && item.canCancelListing && listingId != null && onCancelListing && (
        <button
          type="button"
          className="pf-btn pf-btn--small pf-btn--outline pf-merge-material__cancel"
          disabled={cancelingListingId === listingId}
          onClick={onCancelListing}
        >
          {cancelingListingId === listingId ? 'Снимаем...' : 'Снять с продажи'}
        </button>
      )}
    </article>
  )
}

function PerkPicker({
  preview,
  selectedCards,
  selectedPerkIds,
  onChange,
}: {
  preview: CardMergePreviewResponse
  selectedCards: UserCardItem[]
  selectedPerkIds: string[]
  onChange: (ids: string[]) => void
}) {
  const fixed = compactPerksFromIds(preview.fixedPerkIds ?? [], selectedCards, preview.selectablePerks ?? [])
  const required = preview.requiredSelections ?? 0
  const selectable = preview.selectablePerks ?? []
  if (fixed.length === 0 && selectable.length === 0 && required <= 0) {
    return null
  }
  return (
    <section className="pf-merge-section" aria-label="Перки результата">
      <div className="pf-merge-section__head">
        <h2>Перки результата</h2>
        <span>{required > 0 ? `Выберите ${required}` : 'Выбор не нужен'}</span>
      </div>
      {preview.sameRollForInputSet && (
        <p className="pf-merge-note">Варианты перков зафиксированы для выбранных карт</p>
      )}
      {fixed.length > 0 && (
        <div className="pf-merge-fixed">
          {fixed.map((perk) => (
            <span key={perk.id} className="pf-merge-perk is-fixed">
              <span>{perk.name}{perk.bonusPoints ? ` +${perk.bonusPoints}` : ''}</span>
              {perkSourceLabel(perk.id, selectedCards) && <small>{perkSourceLabel(perk.id, selectedCards)}</small>}
            </span>
          ))}
        </div>
      )}
      {required > 0 && (
        <div className="pf-merge-perks">
          {selectable.map((perk) => {
            const active = selectedPerkIds.includes(perk.id)
            const disabled = !active && selectedPerkIds.length >= required
            return (
              <button
                type="button"
                key={perk.id}
                className={`pf-merge-perk${active ? ' is-selected' : ''}`}
                disabled={disabled}
                aria-pressed={active}
                onClick={() => {
                  if (active) {
                    onChange(selectedPerkIds.filter((id) => id !== perk.id))
                    return
                  }
                  onChange([...selectedPerkIds, perk.id])
                }}
              >
                <span>{perk.name}</span>
                <strong>+{perk.bonusPoints}</strong>
                {perkSourceLabel(perk.id, selectedCards) && <small>{perkSourceLabel(perk.id, selectedCards)}</small>}
                {perk.applicableRoles?.length > 0 && <small>{perk.applicableRoles.join(', ')}</small>}
                {perk.description && <small>{perk.description}</small>}
              </button>
            )
          })}
        </div>
      )}
      {required > 0 && selectedPerkIds.length >= required && (
        <p className="pf-merge-note">Чтобы выбрать другой перк, сначала снимите один из выбранных.</p>
      )}
    </section>
  )
}

function PreviewBlock({
  preview,
  selectedCards,
  selectedSkinSourceId,
  selectedPerkIds,
  maxRenewals,
}: {
  preview: CardMergePreviewResponse
  selectedCards: UserCardItem[]
  selectedSkinSourceId: number | null
  selectedPerkIds: string[]
  maxRenewals: number
}) {
  const fixed = preview.fixedPerkIds ?? []
  const finalPerkIds = [...fixed, ...selectedPerkIds]
  const finalPerks = compactPerksFromIds(finalPerkIds, selectedCards, preview.selectablePerks ?? [])
  const result = preview.result
  const resultName = result.nickname ?? selectedCards[0]?.playerNickname ?? 'Игрок'
  const selectedSkinCard = selectedSkinSourceId != null
    ? selectedCards.find((card) => card.id === selectedSkinSourceId)
    : selectedCards.find((card) => card.skinCode)
  const lostSkins = selectedCards.filter(
    (card) => card.skinCode && card.id !== selectedSkinCard?.id,
  )
  const valueDelta = preview.valueAfter - preview.valueBefore
  const materials = preview.materialCards ?? preview.materials ?? selectedCards
  const normalizedMaterials = materials.map((item) => ('userCard' in item ? item.userCard : item))
  const valueWarning = `Ценность коллекции: ${preview.valueBefore} -> ${preview.valueAfter}₱ (${valueDelta > 0 ? '+' : ''}${valueDelta}₱)`
  const otherWarnings = preview.warnings?.filter((warning) => warning.code !== 'PORTFOLIO_VALUE_DECREASE') ?? []
  const resultRarityClass = result.rarity.toLowerCase()
  return (
    <section className="pf-merge-preview" aria-label="Предпросмотр слияния">
      <div className="pf-merge-section__head">
        <h2>Получится карта</h2>
        <span>{RARITY_LABEL[result.rarity]}</span>
      </div>
      <div className={`pf-merge-result-card pf-merge-result-card--${resultRarityClass}`}>
        <PlayerImage
          src={result.photoUrl ?? selectedCards[0]?.playerPhotoUrl ?? null}
          seedId={result.fantasyPlayerId ?? selectedCards[0]?.fantasyPlayerId ?? preview.previewId}
          variant="card"
          className="pf-merge-result-card__img"
        />
        <div className="pf-merge-result-card__body">
          <span className="pf-merge-result-card__rarity">{rarityRu(result.rarity)}</span>
          <strong>{resultName}</strong>
          <p>{result.usesRemaining} использ. · ↻ {result.timesRenewed}/{result.maxRenewals ?? maxRenewals}</p>
          <div className="pf-merge-result-card__perks">
            {finalPerks.length > 0
              ? finalPerks.map((perk) => (
                <span key={perk.id}>
                  {perk.name}{perk.bonusPoints ? ` +${perk.bonusPoints}` : ''}
                </span>
              ))
              : <span>Перки не выбраны</span>}
          </div>
        </div>
      </div>
      <div className="pf-merge-risk">
        <strong>Будут списаны 3 {RARITY_LABEL[selectedCards[0]?.rarity ?? 'COMMON']} карты</strong>
        <span>{valueWarning}</span>
        {(selectedSkinCard?.skinCode || result.skinCode) && (
          <span>Скин: {selectedSkinCard?.skinCode ?? result.skinCode}</span>
        )}
      </div>
      {otherWarnings.length > 0 && (
        <div className="pf-merge-warnings">
          {otherWarnings.map((warning, index) => (
            <p key={`${warning.code ?? 'warning'}-${index}`}>{warning.message}</p>
          ))}
        </div>
      )}
      {result.timesRenewed >= (result.maxRenewals ?? maxRenewals) && (
        <p className="pf-merge-warn">Результат будет на лимите переподписаний.</p>
      )}
      {lostSkins.length > 0 && (
        <p className="pf-merge-warn">
          Остальные скины будут потеряны: {lostSkins.map((card) => card.skinCode).join(', ')}
        </p>
      )}
      {result.rarity === 'EPIC' && (
        <p className="pf-merge-note">Можно будет улучшить до LEGENDARY отдельно</p>
      )}
      <details className="pf-merge-materials-lost">
        <summary>Материалы: {normalizedMaterials.map((card) => `#${card.id}`).join(', ')}</summary>
        <ul>
          {normalizedMaterials.map((card) => (
            <li key={card.id}>
              #{card.id} {card.playerNickname}, {card.rarity}, {card.usesRemaining} использ., ↻ {card.timesRenewed}
              {card.skinCode ? `, скин ${card.skinCode}` : ''}
            </li>
          ))}
        </ul>
      </details>
    </section>
  )
}

function EmptyGoals({ groups }: { groups: CardMergePlayerGroup[] }) {
  const goals = groups
    .flatMap((group) =>
      group.operations.map((operation) => ({
        group,
        operation,
        total: operation.availableCards.length + operation.blockedCards.length,
        available: operation.availableCards.length,
        blocked: operation.blockedCards.length,
      })),
    )
    .filter((item) => item.total > 0)
    .sort((a, b) => {
      const aScore = Math.min(a.available, 2) * 10 + a.blocked
      const bScore = Math.min(b.available, 2) * 10 + b.blocked
      return bScore - aScore
    })
    .slice(0, 3)

  return (
    <section className="pf-merge-empty">
      <h2>Нужны 3 карты одного игрока</h2>
      <p>Для слияния нужны три COMMON или три RARE карты одного и того же игрока.</p>
      {goals.length > 0 && (
        <div className="pf-merge-goals">
          {goals.map(({ group, operation, available, blocked }) => {
            const reasons = operation.blockedCards
              .map((card) => reasonLabel(card.blockReason))
              .filter(Boolean)
              .slice(0, 2)
              .join(', ')
            return (
              <div key={`${group.fantasyPlayerId}-${operation.operation}`} className="pf-merge-goal">
                <strong>{group.nickname}</strong>
                <span>{OPERATION_LABEL[operation.operation]}: {available}/3 доступно</span>
                {blocked > 0 && <small>Блокировки: {reasons || blocked}</small>}
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}

export function CardMergePage() {
  const initData = useInitData()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null)
  const [selectedOperation, setSelectedOperation] = useState<CardMergeOperation | null>(null)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [selectedSkinSourceId, setSelectedSkinSourceId] = useState<number | null>(null)
  const [selectedPerkIds, setSelectedPerkIds] = useState<string[]>([])
  const [preview, setPreview] = useState<CardMergePreviewResponse | null>(null)
  const [success, setSuccess] = useState<CardMergeConfirmResponse | null>(null)
  const [cancelingListingId, setCancelingListingId] = useState<number | null>(null)
  const previewSkinSourceId = useRef<number | null>(null)
  const workAreaRef = useRef<HTMLDivElement | null>(null)

  const optionsQ = useQuery({
    queryKey: ['card-merge-options', initData],
    queryFn: () => fetchCardMergeOptions(initData),
    enabled: !!initData,
    staleTime: 20_000,
  })

  const economyQ = useQuery({
    queryKey: ['economy-info', initData],
    queryFn: () => fetchEconomyInfo(initData!),
    enabled: !!initData,
  })

  const groups = useMemo(
    () => [...(optionsQ.data?.groups ?? [])].sort((a, b) => groupRank(a) - groupRank(b)),
    [optionsQ.data],
  )

  const selectedGroup = groups.find((group) => group.fantasyPlayerId === selectedPlayerId) ?? null
  const operation = selectedGroup?.operations.find((item) => item.operation === selectedOperation) ?? null
  const selectedCards = useMemo(
    () => selectedCardsFromIds(operation ?? undefined, selectedIds),
    [operation, selectedIds],
  )
  const skinnedSelectedCards = useMemo(
    () => selectedCards.filter((card) => card.skinCode),
    [selectedCards],
  )
  const needsSkinChoice = skinnedSelectedCards.length > 1
  const maxRenewals = preview?.result.maxRenewals ?? economyQ.data?.maxRenewals ?? 0
  const hasEligibleCombo = groups.some((group) => group.operations.some(canUseOperation))
  const requiredSelections = preview?.requiredSelections ?? 0
  const finalSelectedPerkIds = [...(preview?.fixedPerkIds ?? []), ...selectedPerkIds]
  const otherGroups = groups.filter((group) => group.fantasyPlayerId !== selectedPlayerId)
  const chooseOperation = (
    group: CardMergePlayerGroup,
    operationToSelect?: CardMergeOperation | null,
    scrollToWork = false,
  ) => {
    const firstOp = group.operations.find(canUseOperation) ?? group.operations[0]
    setSelectedPlayerId(group.fantasyPlayerId)
    setSelectedOperation(operationToSelect ?? firstOp?.operation ?? null)
    if (scrollToWork) {
      window.setTimeout(() => {
        workAreaRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }, 0)
    }
  }

  useEffect(() => {
    const firstEligible = groups.find((group) => group.operations.some(canUseOperation))
    if (!firstEligible || selectedPlayerId != null) return
    const op = firstEligible.operations.find(canUseOperation)
    setSelectedPlayerId(firstEligible.fantasyPlayerId)
    setSelectedOperation(op?.operation ?? null)
  }, [groups, selectedPlayerId])

  useEffect(() => {
    setSelectedIds([])
    setSelectedSkinSourceId(null)
    setSelectedPerkIds([])
    setPreview(null)
    setSuccess(null)
  }, [selectedPlayerId, selectedOperation])

  const selectedIdsKey = selectedIds.join(':')
  const skinnedSelectedIdsKey = skinnedSelectedCards.map((card) => card.id).join(':')

  useEffect(() => {
    setPreview(null)
    setSelectedPerkIds([])
    if (selectedIds.length !== 3) {
      setSelectedSkinSourceId(null)
      return
    }
    if (skinnedSelectedCards.length === 1) {
      setSelectedSkinSourceId(skinnedSelectedCards[0].id)
    } else if (!skinnedSelectedCards.some((card) => card.id === selectedSkinSourceId)) {
      setSelectedSkinSourceId(null)
    }
  }, [selectedIds.length, selectedIdsKey, skinnedSelectedIdsKey])

  useEffect(() => {
    if (!preview || previewSkinSourceId.current === selectedSkinSourceId) return
    setPreview(null)
    setSelectedPerkIds([])
  }, [preview, selectedSkinSourceId])

  const previewMut = useMutation({
    mutationFn: () => {
      if (!initData || !selectedOperation) throw new Error('Нет операции')
      return fetchCardMergePreview(initData, {
        operation: selectedOperation,
        inputUserCardIds: selectedIds,
        selectedSkinSourceUserCardId: selectedSkinSourceId ?? undefined,
      })
    },
    onSuccess: (data) => {
      previewSkinSourceId.current = selectedSkinSourceId
      setPreview(data)
      setSelectedPerkIds([])
    },
    onError: (e: Error) => window.alert(e instanceof ApiError ? e.message : String(e)),
  })

  const confirmMut = useMutation({
    mutationFn: () => {
      if (!initData || !selectedOperation || !preview) throw new Error('Preview не готов')
      return confirmCardMerge(initData, {
        operation: selectedOperation,
        inputUserCardIds: selectedIds,
        selectedPerkIds: finalSelectedPerkIds,
        selectedSkinSourceUserCardId: selectedSkinSourceId ?? undefined,
        previewId: preview.previewId,
      })
    },
    onSuccess: (data) => {
      setSuccess(data)
      invalidateMergeRelatedQueries(qc)
    },
    onError: (e: Error) => window.alert(e instanceof ApiError ? e.message : String(e)),
  })

  const cancelListingMut = useMutation({
    mutationFn: (listingId: number) => {
      if (!initData) throw new Error('Нет initData')
      setCancelingListingId(listingId)
      return cancelMarketplaceListing(initData, listingId)
    },
    onSuccess: () => {
      setCancelingListingId(null)
      invalidateMergeRelatedQueries(qc)
    },
    onError: (e: Error) => {
      setCancelingListingId(null)
      window.alert(e instanceof ApiError ? e.message : String(e))
    },
  })

  if (!initData) return <MissingInitDataNotice />

  const previewReady =
    selectedIds.length === 3 &&
    (!needsSkinChoice || selectedSkinSourceId != null) &&
    selectedCards.length === 3
  const confirmReady =
    preview != null &&
    selectedIds.length === 3 &&
    selectedPerkIds.length === requiredSelections &&
    (!needsSkinChoice || selectedSkinSourceId != null)

  if (success) {
    const card = success.card
    return (
      <div className="pf-page pf-merge">
        <PageHeader title="Слияние карт" backTo="/cards" backLabel="Коллекция" />
        <section className="pf-merge-success">
          <PlayerImage
            src={cardDisplayImageUrl(card)}
            seedId={card.fantasyPlayerId}
            variant="card"
            className="pf-merge-success__img"
          />
          <div>
            <h2>{card.playerNickname}</h2>
            <p>{OPERATION_LABEL[selectedOperation ?? 'COMMON_TO_RARE']} -&gt; {card.rarity}</p>
            <p>Фантики: {success.newBalance}₣{success.spentFantiki > 0 ? `, списано ${success.spentFantiki}₣` : ''}</p>
            <CardPerkChips perks={card.perks} max={4} />
          </div>
        </section>
        <div className="pf-merge-actions">
          <Link className="pf-btn pf-btn--primary" to="/cards">В коллекцию</Link>
          <button
            type="button"
            className="pf-btn"
            onClick={() => {
              setSuccess(null)
              setSelectedIds([])
              setPreview(null)
              setSelectedPerkIds([])
              void optionsQ.refetch()
            }}
          >
            Собрать ещё
          </button>
          {card.rarity === 'EPIC' && (
            <button
              type="button"
              className="pf-btn pf-btn--outline"
              onClick={() => navigate(`/cards?legendaryUpgrade=${card.id}`)}
            >
              Улучшить до LEGENDARY
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="pf-page pf-merge">
      <PageHeader
        title="Слияние карт"
        subtitle="Выберите 3 карты одного игрока и соберите карту выше редкостью."
        backTo="/cards"
        backLabel="Коллекция"
      />

      {optionsQ.isLoading && <p className="pf-muted">Загрузка вариантов...</p>}
      {optionsQ.isError && <p className="pf-err">{(optionsQ.error as Error).message}</p>}

      {!optionsQ.isLoading && !optionsQ.isError && !hasEligibleCombo && (
        <EmptyGoals groups={groups} />
      )}

      {!optionsQ.isLoading && !optionsQ.isError && groups.length > 0 && (
        <>
          {selectedGroup && (
            <div ref={workAreaRef} className="pf-merge-work">
              <section className="pf-merge-section" aria-label="Выбранный игрок и операция">
                <div className="pf-merge-section__head">
                  <h2>{selectedGroup.nickname}</h2>
                  <span>{operation ? OPERATION_LABEL[operation.operation] : 'Операция'}</span>
                </div>
                <article className="pf-merge-player is-selected">
                  <button
                    type="button"
                    className="pf-merge-player__main"
                    onClick={() => chooseOperation(selectedGroup)}
                  >
                    <PlayerImage
                      src={selectedGroup.photoUrl}
                      seedId={selectedGroup.fantasyPlayerId}
                      variant="avatar"
                      className="pf-merge-player__avatar"
                    />
                    <span>
                      <strong>{selectedGroup.nickname}</strong>
                      <small>
                        {selectedGroup.operations.reduce((sum, op) => sum + op.availableCards.length, 0)} доступно,{' '}
                        {selectedGroup.operations.reduce((sum, op) => sum + op.blockedCards.length, 0)} заблокировано
                      </small>
                    </span>
                  </button>
                  <div className="pf-merge-operation-tabs" role="group" aria-label={`Операции ${selectedGroup.nickname}`}>
                    {selectedGroup.operations.map((op) => (
                      <button
                        key={op.operation}
                        type="button"
                        className={`pf-merge-operation${selectedOperation === op.operation ? ' is-selected' : ''}`}
                        onClick={() => chooseOperation(selectedGroup, op.operation)}
                      >
                        {OPERATION_LABEL[op.operation]}
                        <span>{op.availableCards.length}/3</span>
                      </button>
                    ))}
                  </div>
                </article>
              </section>

              {operation && (
                <section className="pf-merge-section" aria-label="Материалы">
                  <div className="pf-merge-section__head">
                    <h2>Материалы</h2>
                    <span>{selectedIds.length}/3</span>
                  </div>
                  <div className="pf-merge-slots" aria-label="Выбранные слоты">
                    {[0, 1, 2].map((slot) => {
                      const card = selectedCards[slot]
                      return (
                        <div key={slot} className={`pf-merge-slot${card ? ' is-filled' : ''}`}>
                          {card ? `#${card.id}` : `Слот ${slot + 1}`}
                        </div>
                      )
                    })}
                  </div>
                  {needsSkinChoice && (
                    <div className="pf-merge-skin-pick">
                      <strong>Выберите переносимый скин</strong>
                      <p>Остальные скины будут потеряны.</p>
                      <div>
                        {skinnedSelectedCards.map((card) => (
                          <button
                            type="button"
                            key={card.id}
                            className={`pf-merge-skin${selectedSkinSourceId === card.id ? ' is-selected' : ''}`}
                            onClick={() => setSelectedSkinSourceId(card.id)}
                          >
                            {skinLabel(card)}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                  {skinnedSelectedCards.length === 1 && (
                    <p className="pf-merge-note">Скин будет перенесён: {skinnedSelectedCards[0].skinCode}</p>
                  )}
                  <div className="pf-merge-material-grid">
                    {[...operation.availableCards].sort(materialSort).map((item) => {
                      const selected = selectedIds.includes(item.userCard.id)
                      return (
                        <MaterialCard
                          key={item.userCard.id}
                          item={item}
                          selected={selected}
                          disabled={!selected && selectedIds.length >= 3}
                          maxRenewals={maxRenewals}
                          cancelingListingId={cancelingListingId}
                          onToggle={() => {
                            setSelectedIds((prev) => {
                              if (prev.includes(item.userCard.id)) {
                                return prev.filter((id) => id !== item.userCard.id)
                              }
                              if (prev.length >= 3) return prev
                              return [...prev, item.userCard.id]
                            })
                          }}
                        />
                      )
                    })}
                    {operation.blockedCards.map((item) => {
                      const listingId = item.listingId ?? item.userCard.activeMarketplaceListing?.listingId ?? null
                      return (
                        <MaterialCard
                          key={item.userCard.id}
                          item={item}
                          selected={false}
                          disabled
                          maxRenewals={maxRenewals}
                          cancelingListingId={cancelingListingId}
                          onToggle={() => undefined}
                          onCancelListing={
                            listingId != null ? () => cancelListingMut.mutate(listingId) : undefined
                          }
                        />
                      )
                    })}
                  </div>
                  {operation.availableCards.length < 3 && (
                    <p className="pf-merge-note">
                      Не хватает карт: доступно {operation.availableCards.length}/3. Заблокированные карты можно освободить, если причина позволяет.
                    </p>
                  )}
                  <div className="pf-merge-actions">
                    <button
                      type="button"
                      className="pf-btn pf-btn--primary"
                      disabled={!previewReady || previewMut.isPending}
                      onClick={() => previewMut.mutate()}
                    >
                      {previewMut.isPending ? 'Готовим...' : 'Показать результат'}
                    </button>
                    {needsSkinChoice && selectedSkinSourceId == null && (
                      <span className="pf-merge-action-note">Нужно выбрать переносимый скин</span>
                    )}
                  </div>
                </section>
              )}

              {preview && (
                <>
                  <PerkPicker
                    preview={preview}
                    selectedCards={selectedCards}
                    selectedPerkIds={selectedPerkIds}
                    onChange={setSelectedPerkIds}
                  />
                  <PreviewBlock
                    preview={preview}
                    selectedCards={selectedCards}
                    selectedSkinSourceId={selectedSkinSourceId}
                    selectedPerkIds={selectedPerkIds}
                    maxRenewals={maxRenewals}
                  />
                  <div className="pf-merge-actions pf-merge-actions--confirm">
                    <p className="pf-merge-warn pf-merge-actions__warning">
                      Операция необратима: выбранные 3 карты исчезнут из коллекции.
                    </p>
                    <button
                      type="button"
                      className="pf-btn"
                      disabled={confirmMut.isPending}
                      onClick={() => {
                        setPreview(null)
                        setSelectedPerkIds([])
                      }}
                    >
                      Назад к материалам
                    </button>
                    <button
                      type="button"
                      className="pf-btn pf-btn--primary"
                      disabled={!confirmReady || confirmMut.isPending}
                      onClick={() => confirmMut.mutate()}
                    >
                      {confirmMut.isPending ? 'Собираем...' : 'Собрать карту'}
                    </button>
                  </div>
                </>
              )}
            </div>
          )}

          <section className="pf-merge-section pf-merge-section--chooser" aria-label="Выбрать другого игрока">
            <div className="pf-merge-section__head">
              <h2>Другой игрок</h2>
              <span>{otherGroups.length}</span>
            </div>
            <div className="pf-merge-players">
              {otherGroups.map((group) => (
                <article
                  key={group.fantasyPlayerId}
                  className={`pf-merge-player${group.fantasyPlayerId === selectedPlayerId ? ' is-selected' : ''}`}
                >
                  <button
                    type="button"
                    className="pf-merge-player__main"
                    onClick={() => chooseOperation(group, null, true)}
                  >
                    <PlayerImage
                      src={group.photoUrl}
                      seedId={group.fantasyPlayerId}
                      variant="avatar"
                      className="pf-merge-player__avatar"
                    />
                    <span>
                      <strong>{group.nickname}</strong>
                      <small>
                        {group.operations.reduce((sum, op) => sum + op.availableCards.length, 0)} доступно,{' '}
                        {group.operations.reduce((sum, op) => sum + op.blockedCards.length, 0)} заблокировано
                      </small>
                    </span>
                  </button>
                  <div className="pf-merge-operation-tabs" role="group" aria-label={`Операции ${group.nickname}`}>
                    {group.operations.map((op) => (
                      <button
                        key={op.operation}
                        type="button"
                        className={`pf-merge-operation${selectedPlayerId === group.fantasyPlayerId && selectedOperation === op.operation ? ' is-selected' : ''}`}
                        onClick={() => chooseOperation(group, op.operation, true)}
                      >
                        {OPERATION_LABEL[op.operation]}
                        <span>{op.availableCards.length}/3</span>
                      </button>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
