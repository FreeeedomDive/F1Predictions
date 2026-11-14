import {
  Alert,
  Avatar,
  Box,
  Button,
  Checkbox,
  Divider,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import type { ChartsTooltipProps } from '@mui/x-charts/ChartsTooltip'
import { LineChart } from '@mui/x-charts/LineChart'
import type { LineSeriesType } from '@mui/x-charts/models'
import { useMemo, useState } from 'react'
import type { RaceDto } from '../../dto/race'
import type { SeasonStandingsDto } from '../../dto/standings'
import type { ParticipantAvatarUrls } from '../../dto/participant'
import SortedAxisTooltip from './SortedAxisTooltip'
import type { StandingsChartMode } from './SortedAxisTooltip'

interface Participant {
  participantId: string
  seriesId: string
  isMe: boolean
  participantName: string
  avatarUrl?: string
  color: string
  points: number[]
  racePoints: (number | null)[]
}

const THRESHOLD_ID = 'prediction_title_threshold'
const THRESHOLD_COLOR = '#90a4ae'
const COLORS = [
  '#ffd700',
  '#c0c0c0',
  '#cd7f32',
  '#f44336',
  '#2196f3',
  '#4caf50',
  '#ff9800',
  '#9c27b0',
  '#00bcd4',
  '#e91e63',
  '#3f51b5',
  '#009688',
  '#8bc34a',
  '#ff5722',
  '#795548',
  '#607d8b',
  '#cddc39',
  '#40c4ff',
  '#69f0ae',
  '#ea80fc',
]
const PANEL_SX = {
  borderRadius: 2,
  bgcolor: 'rgba(255,255,255,0.025)',
  backgroundImage: 'none',
  minWidth: 0,
} as const

function pointsAt(points: number[], index: number): number {
  return points[index] ?? points.at(-1) ?? 0
}

export default function StandingsChart({
  standings,
  races,
  currentParticipantId,
  avatars = {},
}: {
  standings: SeasonStandingsDto
  races: RaceDto[]
  currentParticipantId: string
  avatars?: ParticipantAvatarUrls
}) {
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('md'))
  const [mode, setMode] = useState<StandingsChartMode>('points')
  const [selectedParticipants, setSelectedParticipants] = useState<Set<string> | null>(null)
  const [showThreshold, setShowThreshold] = useState(true)
  const [search, setSearch] = useState('')

  const standingsByParticipantId = new Map(standings.rows.map((row) => [row.participantId, row]))
  const participants: Participant[] = standings.charts.map((chart, index) => {
    const row = standingsByParticipantId.get(chart.participantId)
    const resultsByRaceId = new Map(
      (row?.results ?? []).map((result) => [result.raceId, result.points.totalPoints]),
    )
    const participantName = row?.participantName || chart.participantId
    return {
      participantId: chart.participantId,
      seriesId: `prediction_points_${chart.participantId}`,
      isMe: chart.participantId === currentParticipantId,
      participantName,
      avatarUrl: avatars[chart.participantId] ?? undefined,
      color:
        chart.participantId === currentParticipantId ? '#ffffff' : COLORS[index % COLORS.length],
      points: chart.points,
      racePoints: races.map((race) => resultsByRaceId.get(race.id) ?? null),
    }
  })
  const visibleParticipants = participants.filter(
    (participant) =>
      selectedParticipants === null || selectedParticipants.has(participant.participantId),
  )
  const normalizedSearch = search.trim().toLocaleLowerCase()
  const filteredParticipants = participants.filter((participant) =>
    participant.participantName.toLocaleLowerCase().includes(normalizedSearch),
  )
  const allSelected = visibleParticipants.length === participants.length
  const partlySelected = visibleParticipants.length > 0 && !allSelected
  const stageCount = Math.max(0, ...participants.map((participant) => participant.points.length))
  const stages = Array.from({ length: stageCount }, (_, index) => index)
  const axisStages = mode === 'race' ? races.map((_, index) => index + 1) : stages
  const leaderPoints = stages.map((index) =>
    Math.max(0, ...participants.map((participant) => pointsAt(participant.points, index))),
  )
  const firstThresholdStage = standings.championChart.findIndex((value) => value > 0)
  const hasThreshold = firstThresholdStage >= 0
  const ownParticipant = participants.find((participant) => participant.isMe)
  const ownSeriesId = ownParticipant?.seriesId

  const chartSeries: Omit<LineSeriesType, 'type'>[] = visibleParticipants.map((participant) => ({
    id: participant.seriesId,
    label: participant.participantName,
    color: participant.color,
    curve: 'linear',
    area: false,
    showMark: mode === 'race',
    shape: 'circle',
    connectNulls: false,
    data:
      mode === 'race'
        ? participant.racePoints
        : stages.map((index) => {
            const points = pointsAt(participant.points, index)
            return mode === 'gap' ? points - leaderPoints[index] : points
          }),
  }))

  if (mode !== 'race' && showThreshold && hasThreshold) {
    chartSeries.push({
      id: THRESHOLD_ID,
      label: 'Title contention threshold',
      color: THRESHOLD_COLOR,
      curve: 'linear',
      area: false,
      showMark: false,
      shape: 'circle',
      data: stages.map((index) => {
        const threshold = standings.championChart[index] ?? 0
        if (threshold <= 0 && index !== firstThresholdStage - 1) return null
        return mode === 'gap' ? threshold - leaderPoints[index] : threshold
      }),
    })
  }

  const values = chartSeries.flatMap((series) =>
    (series.data ?? []).filter((value): value is number => value !== null),
  )
  const minY = mode === 'gap' ? Math.min(-1, ...values) : 0
  const maxY = mode === 'gap' ? 0 : Math.max(1, ...values)
  const axisTooltip = useMemo(
    () => (props: ChartsTooltipProps) => (
      <SortedAxisTooltip {...props} races={races} mode={mode} referenceSeriesId={THRESHOLD_ID} />
    ),
    [races, mode],
  )

  function toggleParticipant(id: string) {
    setSelectedParticipants((previous) => {
      const next = new Set(previous ?? participants.map((participant) => participant.participantId))
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function selectMe() {
    if (!ownParticipant) return
    setSelectedParticipants(new Set([ownParticipant.participantId]))
    setSearch('')
  }

  if (participants.length === 0 || axisStages.length === 0) {
    return <Alert severity="info">No chart data yet.</Alert>
  }

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: {
          xs: 'minmax(0, 1fr)',
          lg: 'minmax(0, 1fr) 280px',
        },
        gap: 2,
        alignItems: 'stretch',
      }}
    >
      <Paper
        variant="outlined"
        sx={{ ...PANEL_SX, p: 2, display: 'flex', flexDirection: 'column' }}
      >
        <Stack spacing={0.5}>
          <Typography sx={{ fontWeight: 600 }}>Season progress</Typography>
          <Typography variant="caption" color="text.secondary">
            {mode === 'points'
              ? 'Cumulative points after each event'
              : mode === 'gap'
                ? 'Gap to the leader after each event · 0 — leader'
                : 'Points per race · Blank — no prediction'}
          </Typography>
        </Stack>
        {chartSeries.length > 0 ? (
          <Box
            sx={{
              width: '100%',
              minWidth: 0,
            }}
          >
            <LineChart
              height={desktop ? 560 : 420}
              series={chartSeries}
              xAxis={[
                {
                  data: axisStages,
                  scaleType: 'linear',
                  min: axisStages[0] - (axisStages.length === 1 ? 0.5 : 0),
                  max: axisStages[axisStages.length - 1] + (axisStages.length === 1 ? 0.5 : 0),
                  domainLimit: 'strict',
                  tickMinStep: 1,
                  tickInterval: axisStages,
                  label: 'Season event',
                },
              ]}
              yAxis={[
                {
                  min: minY,
                  max: maxY,
                  scaleType: 'linear',
                  tickMinStep: 1,
                  label:
                    mode === 'gap' ? 'Gap, points' : mode === 'race' ? 'Race points' : 'Points',
                },
              ]}
              margin={{ top: 16, bottom: 4, left: 0, right: 16 }}
              grid={{ horizontal: true }}
              hideLegend
              slots={{ tooltip: axisTooltip }}
              slotProps={{
                line: ({ seriesId }) => ({
                  style: {
                    strokeWidth:
                      seriesId === THRESHOLD_ID ? 1.5 : seriesId === ownSeriesId ? 3 : 1.75,
                    strokeDasharray: seriesId === THRESHOLD_ID ? '6 4' : undefined,
                  },
                }),
              }}
              sx={{
                '& .MuiChartsGrid-line': { stroke: 'rgba(255,255,255,0.08)' },
                '& .MuiChartsAxis-line, & .MuiChartsAxis-tick': {
                  stroke: 'rgba(255,255,255,0.2)',
                },
              }}
            />
          </Box>
        ) : (
          <Box
            sx={{
              flex: 1,
              minHeight: 420,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Typography variant="body2" color="text.secondary">
              Select participants in the chart settings
            </Typography>
          </Box>
        )}
      </Paper>
      <Box sx={{ minWidth: 0, position: 'relative' }}>
        <Paper
          variant="outlined"
          sx={{
            ...PANEL_SX,
            p: 2,
            display: 'flex',
            flexDirection: 'column',
            position: { lg: 'absolute' },
            inset: { lg: 0 },
          }}
        >
          <Stack spacing={2} sx={{ flex: 1, minHeight: 0 }}>
            <Typography sx={{ fontWeight: 600 }}>Chart settings</Typography>
            <ToggleButtonGroup
              value={mode}
              exclusive
              fullWidth
              size="small"
              color="primary"
              aria-label="Chart mode"
              sx={{
                '& .MuiToggleButton-root': {
                  px: 0.75,
                  fontSize: '0.75rem',
                  whiteSpace: 'nowrap',
                },
              }}
              onChange={(_, value: StandingsChartMode | null) => {
                if (value !== null) setMode(value)
              }}
            >
              <ToggleButton value="points" sx={{ flex: 1, textTransform: 'none' }}>
                Points
              </ToggleButton>
              <ToggleButton value="gap" sx={{ flex: 1, textTransform: 'none' }}>
                Gap
              </ToggleButton>
              <ToggleButton
                value="race"
                sx={{ flex: 1, textTransform: 'none' }}
                disabled={races.length === 0}
              >
                Per race
              </ToggleButton>
            </ToggleButtonGroup>
            <Divider />
            <Stack spacing={1} sx={{ flex: 1, minHeight: 0 }}>
              <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  Participants
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {visibleParticipants.length} / {participants.length}
                </Typography>
              </Stack>
              <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                <Checkbox
                  size="small"
                  checked={allSelected}
                  indeterminate={partlySelected}
                  onChange={(_, checked) =>
                    setSelectedParticipants(checked ? null : new Set<string>())
                  }
                  slotProps={{
                    input: {
                      'aria-label': allSelected
                        ? 'Deselect all participants'
                        : 'Select all participants',
                      'aria-checked': partlySelected ? 'mixed' : allSelected,
                    },
                  }}
                  sx={{ p: 0.5, flexShrink: 0 }}
                />
                <TextField
                  size="small"
                  type="search"
                  placeholder="Search by name"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  slotProps={{ htmlInput: { 'aria-label': 'Search participants by name' } }}
                  sx={{
                    flex: 1,
                    minWidth: 0,
                    '& .MuiInputBase-input': { px: 1, fontSize: '0.8rem' },
                    '& .MuiInputBase-root': { height: 36 },
                  }}
                />
                <Button
                  size="small"
                  variant="outlined"
                  onClick={selectMe}
                  disabled={!ownParticipant}
                  title="Show only my results"
                  sx={{ textTransform: 'none', minWidth: 32, height: 36, px: 1, flexShrink: 0 }}
                >
                  Me
                </Button>
              </Stack>
              <Box
                tabIndex={0}
                aria-label="Participants"
                sx={{
                  flex: 1,
                  maxHeight: { xs: 320, lg: 'none' },
                  overflowY: 'auto',
                  minHeight: 0,
                  minWidth: 0,
                  py: 0.5,
                  pr: 0.5,
                  scrollbarGutter: 'stable',
                  scrollbarWidth: 'thin',
                  scrollbarColor: 'rgba(255,255,255,0.2) transparent',
                  '&::-webkit-scrollbar': { width: 5 },
                  '&::-webkit-scrollbar-track': { backgroundColor: 'transparent' },
                  '&::-webkit-scrollbar-thumb': {
                    backgroundColor: 'rgba(255,255,255,0.2)',
                    borderRadius: 3,
                  },
                  '&::-webkit-scrollbar-thumb:hover': {
                    backgroundColor: 'rgba(255,255,255,0.35)',
                  },
                }}
              >
                {filteredParticipants.map((participant) => (
                  <FormControlLabel
                    key={participant.participantId}
                    sx={{
                      m: 0,
                      width: '100%',
                      px: 0.5,
                      py: 0.5,
                      minWidth: 0,
                      display: 'flex',
                      minHeight: 36,
                      flexShrink: 0,
                      borderRadius: 1,
                      bgcolor: participant.isMe ? 'rgba(255,255,255,0.04)' : 'transparent',
                      '& .MuiFormControlLabel-label': { flex: 1, minWidth: 0 },
                    }}
                    control={
                      <Checkbox
                        size="small"
                        checked={
                          selectedParticipants === null ||
                          selectedParticipants.has(participant.participantId)
                        }
                        onChange={() => toggleParticipant(participant.participantId)}
                        slotProps={{
                          input: { 'aria-label': `Show ${participant.participantName}` },
                        }}
                        sx={{
                          p: 0.5,
                          mr: 0.5,
                          color: participant.color,
                          '&.Mui-checked': { color: participant.color },
                        }}
                      />
                    }
                    label={
                      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                        <Avatar
                          src={participant.avatarUrl}
                          alt={participant.participantName}
                          sx={{ width: 22, height: 22, fontSize: '0.7rem', flexShrink: 0 }}
                        >
                          {participant.participantName.slice(0, 1)}
                        </Avatar>
                        <Typography
                          variant="body2"
                          sx={{
                            flex: 1,
                            minWidth: 0,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            fontWeight: participant.isMe ? 600 : 400,
                          }}
                        >
                          {participant.participantName}
                        </Typography>
                        <Typography
                          variant="caption"
                          color="text.secondary"
                          title="Total season points"
                          sx={{ fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}
                        >
                          {pointsAt(participant.points, stageCount - 1)}
                        </Typography>
                      </Stack>
                    }
                  />
                ))}
                {filteredParticipants.length === 0 && (
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ py: 2, textAlign: 'center' }}
                  >
                    No participants found
                  </Typography>
                )}
              </Box>
            </Stack>
            {mode !== 'race' && (
              <>
                <Divider />
                <Stack spacing={0.5}>
                  <FormControlLabel
                    sx={{ m: 0, alignItems: 'center' }}
                    control={
                      <Switch
                        size="small"
                        checked={hasThreshold && showThreshold}
                        disabled={!hasThreshold}
                        onChange={(_, checked) => setShowThreshold(checked)}
                      />
                    }
                    label={<Typography variant="body2">Title contention threshold</Typography>}
                  />
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Box
                      sx={{ width: 24, borderTop: `2px dashed ${THRESHOLD_COLOR}`, flexShrink: 0 }}
                    />
                    <Typography variant="caption" color="text.secondary">
                      {hasThreshold
                        ? 'Minimum points required to remain in the title fight'
                        : 'Threshold not available yet'}
                    </Typography>
                  </Stack>
                </Stack>
              </>
            )}
          </Stack>
        </Paper>
      </Box>
    </Box>
  )
}
