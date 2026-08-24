import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, Form, Input, InputNumber, Select, Space } from 'antd'
import { useEffect } from 'react'
import { MAX_EXPECTED_GAME_COUNT } from '../api/seriesRequests'
import type { TournamentKind, TournamentStatus } from '../api/types'

interface Values {
  name: string
  description?: string | null
  status: TournamentStatus
  kind: TournamentKind
  polemicaCompetitionId?: number | null
  defaultExpectedGameCount?: number | null
  streamLinks: { label?: string | null; url: string }[]
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
      defaultExpectedGameCount: initial?.defaultExpectedGameCount ?? null,
      streamLinks: initial?.streamLinks ?? [],
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
        defaultExpectedGameCount: initial?.defaultExpectedGameCount ?? null,
        streamLinks: initial?.streamLinks ?? [],
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
      <Form.Item
        name="defaultExpectedGameCount"
        label="Default expected game count"
        extra="Optional. New series inherit this value unless they specify another count. Existing series are not changed."
      >
        <InputNumber
          min={1}
          max={MAX_EXPECTED_GAME_COUNT}
          precision={0}
          style={{ width: '100%' }}
          placeholder="No tournament default"
        />
      </Form.Item>
      <Form.Item label="Tournament stream links">
        <Form.List name="streamLinks">
          {(fields, { add, remove }) => (
            <Space direction="vertical" style={{ width: '100%' }}>
              {fields.map((field) => (
                <Space key={field.key} align="baseline" style={{ display: 'flex' }}>
                  <Form.Item name={[field.name, 'label']} style={{ marginBottom: 8 }}>
                    <Input placeholder="Label, e.g. Table 1" />
                  </Form.Item>
                  <Form.Item
                    name={[field.name, 'url']}
                    rules={[{ required: true, message: 'URL is required' }]}
                    style={{ marginBottom: 8, flex: 1 }}
                  >
                    <Input placeholder="https://..." />
                  </Form.Item>
                  <Button
                    aria-label="Remove stream link"
                    icon={<DeleteOutlined />}
                    onClick={() => remove(field.name)}
                  />
                </Space>
              ))}
              <Button icon={<PlusOutlined />} onClick={() => add({ label: '', url: '' })}>
                Add stream link
              </Button>
            </Space>
          )}
        </Form.List>
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          {submitLabel}
        </Button>
      </Form.Item>
    </Form>
  )
}
