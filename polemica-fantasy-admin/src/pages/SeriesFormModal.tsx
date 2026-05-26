import { Button, DatePicker, Form, Input, InputNumber, Select } from 'antd'
import dayjs from 'dayjs'
import type { CreateSeriesRequest } from '../api/seriesRequests'
import type { SeriesStatus, TournamentKind } from '../api/types'

interface Values {
  name: string
  namePrefix: string
  gameStartedOn: ReturnType<typeof dayjs> | null
  gameNumFrom: number | null
  gameNumTo: number | null
  gamePhase: number | 'ALL'
  status: SeriesStatus
  startsAt: ReturnType<typeof dayjs>
  teamDeadline: ReturnType<typeof dayjs>
}

export function SeriesFormModal({
  tournamentKind,
  onSubmit,
  loading,
}: {
  tournamentKind: TournamentKind
  onSubmit: (body: CreateSeriesRequest) => void
  loading?: boolean
}) {
  const isCompetition = tournamentKind === 'POLEMICA_COMPETITION'

  return (
    <Form<Values>
      layout="vertical"
      initialValues={{
        name: '',
        namePrefix: '',
        gameStartedOn: null,
        gameNumFrom: null,
        gameNumTo: null,
        gamePhase: 0,
        status: 'UPCOMING',
        startsAt: dayjs(),
        teamDeadline: dayjs().hour(19).minute(10).second(0).millisecond(0),
      }}
      onFinish={(v) => {
        const base = {
          name: v.name,
          status: v.status,
          startsAt: v.startsAt.toISOString(),
          teamDeadline: v.teamDeadline.toISOString(),
        }
        if (isCompetition) {
          onSubmit({
            ...base,
            gameNumFrom: v.gameNumFrom ?? undefined,
            gameNumTo: v.gameNumTo ?? undefined,
            gamePhase: v.gamePhase === 'ALL' ? null : v.gamePhase,
            namePrefix: v.namePrefix?.trim() || undefined,
          })
        } else {
          onSubmit({
            ...base,
            namePrefix: v.namePrefix,
            gameStartedOn: v.gameStartedOn ? v.gameStartedOn.format('YYYY-MM-DD') : null,
          })
        }
      }}
    >
      <Form.Item name="name" label="Name" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      {!isCompetition && (
        <>
          <Form.Item name="namePrefix" label="Name prefix" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="gameStartedOn" label="Game started day (optional)">
            <DatePicker format="YYYY-MM-DD" allowClear style={{ width: '100%' }} />
          </Form.Item>
        </>
      )}
      {isCompetition && (
        <>
          <Form.Item
            name="gameNumFrom"
            label="Game num from (inclusive)"
            rules={[{ required: true, message: 'Required for competition tournament' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="gameNumTo"
            label="Game num to (inclusive)"
            rules={[{ required: true, message: 'Required for competition tournament' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="gamePhase" label="Phase filter">
            <Select
              options={[
                { value: 0, label: 'Phase 0 (default)' },
                { value: 1, label: 'Phase 1 (semifinal)' },
                { value: 2, label: 'Phase 2 (final)' },
                { value: 'ALL', label: 'All phases (null)' },
              ]}
            />
          </Form.Item>
          <Form.Item name="namePrefix" label="Display label (optional)">
            <Input placeholder="Not used for sync" />
          </Form.Item>
        </>
      )}
      <Form.Item name="status" label="Status" rules={[{ required: true }]}>
        <Select
          options={[
            { value: 'UPCOMING', label: 'UPCOMING' },
            { value: 'ACTIVE', label: 'ACTIVE' },
            { value: 'SCORING', label: 'SCORING' },
            { value: 'FINISHED', label: 'FINISHED' },
          ]}
        />
      </Form.Item>
      <Form.Item name="startsAt" label="Starts at" rules={[{ required: true }]}>
        <DatePicker showTime style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item
        name="teamDeadline"
        label="Team deadline"
        rules={[{ required: true }]}
      >
        <DatePicker showTime style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          Create
        </Button>
      </Form.Item>
    </Form>
  )
}
