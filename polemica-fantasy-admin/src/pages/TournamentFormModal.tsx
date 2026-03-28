import { Button, Form, Input, InputNumber, Select } from 'antd'
import { useEffect } from 'react'
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
          label="Polemica competition ID"
          extra="Numeric competition id from Polemica (no list fetch required)."
          rules={[
            { required: true, message: 'Enter a competition id' },
            {
              type: 'number',
              min: 1,
              message: 'Id must be a positive integer',
            },
          ]}
        >
          <InputNumber
            style={{ width: '100%' }}
            min={1}
            precision={0}
            placeholder="e.g. 5045"
            controls={false}
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
