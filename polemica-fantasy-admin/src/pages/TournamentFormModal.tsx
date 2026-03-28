import { Button, Form, Input, Select } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { listPolemicaCompetitions } from '../api/polemica'
import type { TournamentKind, TournamentStatus } from '../api/types'

interface Values {
  name: string
  description?: string | null
  status: TournamentStatus
  kind: TournamentKind
  polemicaCompetitionId?: number | null
}

export function TournamentFormModal({
  initial,
  onSubmit,
  loading,
  submitLabel = 'Create',
}: {
  initial?: Partial<Values>
  onSubmit: (v: Values) => void
  loading?: boolean
  submitLabel?: string
}) {
  const [form] = Form.useForm<Values>()

  const kind = Form.useWatch('kind', form)

  const { data: competitions } = useQuery({
    queryKey: ['admin', 'polemica', 'competitions'],
    queryFn: listPolemicaCompetitions,
    enabled: kind === 'POLEMICA_COMPETITION',
  })

  useEffect(() => {
    form.setFieldsValue({
      name: initial?.name ?? '',
      description: initial?.description ?? '',
      status: initial?.status ?? 'DRAFT',
      kind: initial?.kind ?? 'STANDALONE',
      polemicaCompetitionId: initial?.polemicaCompetitionId ?? undefined,
    })
  }, [initial, form])

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={{
        name: initial?.name ?? '',
        description: initial?.description ?? '',
        status: initial?.status ?? 'DRAFT',
        kind: initial?.kind ?? 'STANDALONE',
        polemicaCompetitionId: initial?.polemicaCompetitionId ?? undefined,
      }}
      onFinish={(v) => onSubmit(v)}
    >
      <Form.Item name="name" label="Name" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item name="description" label="Description">
        <Input.TextArea rows={3} />
      </Form.Item>
      <Form.Item name="status" label="Status" rules={[{ required: true }]}>
        <Select
          options={[
            { value: 'DRAFT', label: 'DRAFT' },
            { value: 'ACTIVE', label: 'ACTIVE' },
            { value: 'FINISHED', label: 'FINISHED' },
          ]}
        />
      </Form.Item>
      <Form.Item name="kind" label="Tournament kind" rules={[{ required: true }]}>
        <Select
          options={[
            { value: 'STANDALONE', label: 'Standalone (profile + name prefix)' },
            {
              value: 'POLEMICA_COMPETITION',
              label: 'Polemica competition (games by num range)',
            },
          ]}
        />
      </Form.Item>
      {kind === 'POLEMICA_COMPETITION' && (
        <Form.Item
          name="polemicaCompetitionId"
          label="Polemica competition"
          rules={[{ required: true, message: 'Select a competition' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="Select competition"
            loading={!competitions}
            options={competitions?.map((c) => ({
              value: c.id,
              label: `${c.id} — ${c.name}`,
            }))}
          />
        </Form.Item>
      )}
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          {submitLabel}
        </Button>
      </Form.Item>
    </Form>
  )
}
