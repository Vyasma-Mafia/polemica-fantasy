import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableProps } from 'antd'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import { getCardMerge, listCardMerges } from '../api/cardMerges'
import { ADMIN_UNPAGINATED_SIZE } from '../api/pagination'
import type {
  AdminCardMergeDetailDto,
  AdminCardMergeInputDto,
  AdminCardMergeListItemDto,
  Rarity,
} from '../api/types'

type FilterValues = {
  telegramUserId?: string
  resultUserCardId?: string
}

function dash(value: string | number | null | undefined) {
  return value != null && value !== '' ? value : '—'
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('DD.MM.YYYY HH:mm') : value
}

function rarityColor(rarity: Rarity | string | null | undefined): string {
  switch (rarity) {
    case 'COMMON':
      return 'default'
    case 'RARE':
      return 'cyan'
    case 'EPIC':
      return 'purple'
    case 'LEGENDARY':
      return 'gold'
    default:
      return 'default'
  }
}

function operationColor(operation: string): string {
  switch (operation) {
    case 'COMMON_TO_RARE':
      return 'cyan'
    case 'RARE_TO_EPIC':
      return 'purple'
    default:
      return 'default'
  }
}

function PerkTags({ ids }: { ids: string[] | null | undefined }) {
  if (ids == null || ids.length === 0) {
    return <Typography.Text type="secondary">—</Typography.Text>
  }
  return (
    <Space size={[4, 4]} wrap>
      {ids.map((id) => (
        <Tag key={id}>{id}</Tag>
      ))}
    </Space>
  )
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Unknown error'
}

function hasInputDetails(
  merge: AdminCardMergeDetailDto | AdminCardMergeListItemDto,
): merge is AdminCardMergeDetailDto {
  return 'inputs' in merge && Array.isArray(merge.inputs)
}

export function CardMergesPage() {
  const [form] = Form.useForm<FilterValues>()
  const [filters, setFilters] = useState<FilterValues>({})
  const [selectedMergeId, setSelectedMergeId] = useState<number | null>(null)

  const listQ = useQuery({
    queryKey: ['admin', 'card-merges', filters],
    queryFn: () =>
      listCardMerges({
        page: 0,
        size: ADMIN_UNPAGINATED_SIZE,
        telegramUserId: filters.telegramUserId,
        resultUserCardId: filters.resultUserCardId,
      }),
  })

  const detailQ = useQuery({
    queryKey: ['admin', 'card-merge', selectedMergeId],
    queryFn: () => getCardMerge(selectedMergeId!),
    enabled: selectedMergeId != null,
  })

  const selectedListItem = useMemo(
    () => listQ.data?.content.find((item) => item.id === selectedMergeId) ?? null,
    [listQ.data?.content, selectedMergeId],
  )
  const selectedMerge: AdminCardMergeDetailDto | AdminCardMergeListItemDto | null =
    detailQ.data ?? selectedListItem

  const columns = useMemo<TableProps<AdminCardMergeListItemDto>['columns']>(
    () => [
      {
        title: 'ID',
        dataIndex: 'id',
        key: 'id',
        width: 90,
      },
      {
        title: 'User',
        key: 'user',
        render: (_: unknown, record) => (
          <Space direction="vertical" size={0}>
            <Typography.Text>{dash(record.telegramUserDisplayName)}</Typography.Text>
            <Typography.Text type="secondary">{record.telegramUserId}</Typography.Text>
          </Space>
        ),
      },
      {
        title: 'Result card',
        dataIndex: 'resultUserCardId',
        key: 'resultUserCardId',
        width: 130,
      },
      {
        title: 'Operation',
        key: 'operation',
        render: (_: unknown, record) => (
          <Space>
            <Tag color={operationColor(record.operation)}>{record.operation}</Tag>
            <Tag color={rarityColor(record.resultRarity)}>{record.resultRarity}</Tag>
          </Space>
        ),
      },
      {
        title: 'Player',
        key: 'player',
        render: (_: unknown, record) => (
          <Space direction="vertical" size={0}>
            <Typography.Text>{record.fantasyPlayerNickname}</Typography.Text>
            <Typography.Text type="secondary">#{record.fantasyPlayerId}</Typography.Text>
          </Space>
        ),
      },
      {
        title: 'Selected perks',
        key: 'selectedPerkIds',
        render: (_: unknown, record) => <PerkTags ids={record.selectedPerkIds} />,
      },
      {
        title: 'Cost',
        dataIndex: 'costFantiki',
        key: 'costFantiki',
        align: 'right',
        width: 110,
        render: (value: number) => `${value.toLocaleString('ru-RU')} ₣`,
      },
      {
        title: 'Created',
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 160,
        render: (value: string) => formatDate(value),
      },
    ],
    [],
  )

  const inputColumns = useMemo<TableProps<AdminCardMergeInputDto>['columns']>(
    () => [
      {
        title: 'Input card',
        dataIndex: 'inputUserCardId',
        key: 'inputUserCardId',
        width: 110,
      },
      {
        title: 'Template',
        dataIndex: 'inputCardTemplateId',
        key: 'inputCardTemplateId',
        width: 110,
      },
      {
        title: 'Rarity',
        dataIndex: 'inputRarity',
        key: 'inputRarity',
        width: 110,
        render: (value: string) => <Tag color={rarityColor(value)}>{value}</Tag>,
      },
      {
        title: 'Perks',
        dataIndex: 'inputPerkIds',
        key: 'inputPerkIds',
        render: (ids: string[]) => <PerkTags ids={ids} />,
      },
      {
        title: 'Uses',
        dataIndex: 'inputUsesRemaining',
        key: 'inputUsesRemaining',
        align: 'right',
        width: 80,
      },
      {
        title: 'Renewed',
        dataIndex: 'inputTimesRenewed',
        key: 'inputTimesRenewed',
        align: 'right',
        width: 90,
      },
      {
        title: 'Skin',
        dataIndex: 'inputSkinCode',
        key: 'inputSkinCode',
        render: (value: string | null | undefined) => dash(value),
      },
    ],
    [],
  )

  const applyFilters = (values: FilterValues) => {
    setFilters({
      telegramUserId: values.telegramUserId?.trim() || undefined,
      resultUserCardId: values.resultUserCardId?.trim() || undefined,
    })
  }

  const resetFilters = () => {
    form.resetFields()
    setFilters({})
  }

  const hasFilters = filters.telegramUserId != null || filters.resultUserCardId != null

  return (
    <div>
      <Typography.Title level={3}>Card merges</Typography.Title>
      <Typography.Paragraph type="secondary">
        Read-only support history for irreversible card merge operations.
      </Typography.Paragraph>

      <Form form={form} layout="inline" onFinish={applyFilters} style={{ marginBottom: 16 }}>
        <Form.Item name="telegramUserId">
          <Input
            allowClear
            placeholder="Telegram user ID"
            inputMode="numeric"
            style={{ width: 220 }}
          />
        </Form.Item>
        <Form.Item name="resultUserCardId">
          <Input
            allowClear
            placeholder="Result card ID"
            inputMode="numeric"
            style={{ width: 220 }}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              Apply
            </Button>
            <Button onClick={resetFilters}>Reset</Button>
          </Space>
        </Form.Item>
      </Form>

      {listQ.isError ? (
        <Alert
          type="error"
          showIcon
          message="Failed to load card merges"
          description={errorMessage(listQ.error)}
          style={{ marginBottom: 16 }}
        />
      ) : null}

      <Table<AdminCardMergeListItemDto>
        rowKey="id"
        loading={listQ.isLoading || listQ.isFetching}
        dataSource={listQ.data?.content ?? []}
        columns={columns}
        onRow={(record) => ({
          onClick: () => setSelectedMergeId(record.id),
          style: { cursor: 'pointer' },
        })}
        locale={{
          emptyText: hasFilters ? 'No card merges match the filters' : 'No card merges yet',
        }}
        pagination={false}
      />

      <Drawer
        title={selectedMergeId == null ? 'Card merge' : `Card merge #${selectedMergeId}`}
        width={760}
        open={selectedMergeId != null}
        onClose={() => setSelectedMergeId(null)}
        destroyOnHidden
      >
        {detailQ.isError ? (
          <Alert
            type="error"
            showIcon
            message="Failed to load card merge detail"
            description={errorMessage(detailQ.error)}
            style={{ marginBottom: 16 }}
          />
        ) : null}

        {selectedMerge == null ? (
          <Table loading pagination={false} dataSource={[]} columns={[]} />
        ) : (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="Telegram user">
                <Space direction="vertical" size={0}>
                  <Typography.Text>{dash(selectedMerge.telegramUserDisplayName)}</Typography.Text>
                  <Typography.Text type="secondary">
                    {selectedMerge.telegramUserId}
                  </Typography.Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="Operation">
                <Space>
                  <Tag color={operationColor(selectedMerge.operation)}>
                    {selectedMerge.operation}
                  </Tag>
                  <Tag color={rarityColor(selectedMerge.sourceRarity)}>
                    {selectedMerge.sourceRarity}
                  </Tag>
                  <Typography.Text type="secondary">→</Typography.Text>
                  <Tag color={rarityColor(selectedMerge.resultRarity)}>
                    {selectedMerge.resultRarity}
                  </Tag>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="Result card">
                {selectedMerge.resultUserCardId}
              </Descriptions.Item>
              <Descriptions.Item label="Fantasy player">
                {selectedMerge.fantasyPlayerNickname} #{selectedMerge.fantasyPlayerId}
              </Descriptions.Item>
              <Descriptions.Item label="Preview ID">
                {dash(selectedMerge.previewId)}
              </Descriptions.Item>
              <Descriptions.Item label="Cost">
                {selectedMerge.costFantiki.toLocaleString('ru-RU')} ₣
              </Descriptions.Item>
              <Descriptions.Item label="Created">
                {formatDate(selectedMerge.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label="Selected perks">
                <PerkTags ids={selectedMerge.selectedPerkIds} />
              </Descriptions.Item>
              <Descriptions.Item label="Offered perks">
                <PerkTags ids={selectedMerge.offeredPerkIds} />
              </Descriptions.Item>
            </Descriptions>

            <div>
              <Typography.Title level={5}>Inputs</Typography.Title>
              <Table<AdminCardMergeInputDto>
                rowKey="inputUserCardId"
                size="small"
                pagination={false}
                loading={detailQ.isLoading || detailQ.isFetching}
                dataSource={hasInputDetails(selectedMerge) ? selectedMerge.inputs : []}
                columns={inputColumns}
                locale={{ emptyText: 'No input detail returned yet' }}
              />
            </div>
          </Space>
        )}
      </Drawer>
    </div>
  )
}
