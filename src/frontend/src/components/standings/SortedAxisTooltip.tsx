import { Box, Divider, Paper, Stack, Typography } from '@mui/material'
import { ChartsTooltipContainer, useAxesTooltip } from '@mui/x-charts/ChartsTooltip'
import type { ChartsTooltipProps } from '@mui/x-charts/ChartsTooltip'
import type { RaceDto } from '../../dto/race'

export type StandingsChartMode = 'points' | 'gap' | 'race'

type SortedAxisTooltipProps = ChartsTooltipProps & {
  races: RaceDto[]
  mode?: StandingsChartMode
  referenceSeriesId?: string
}

export default function SortedAxisTooltip({
  races,
  mode = 'points',
  referenceSeriesId,
  ...containerProps
}: SortedAxisTooltipProps) {
  const tooltipData = useAxesTooltip<'line'>()
  const tooltip = tooltipData?.[0]
  if (!tooltip) return null

  const raceIndex = mode === 'race' ? tooltip.dataIndex : tooltip.dataIndex - 1
  const race = raceIndex < 0 ? null : races[raceIndex]
  const raceLabel =
    raceIndex < 0
      ? 'Season start'
      : race
        ? `${race.name}${race.type === 'SPRINT' ? ' (Sprint)' : ''}`
        : null

  const items = tooltip.seriesItems
    .map((item) => ({
      id: item.seriesId,
      label: item.formattedLabel ?? String(item.seriesId),
      value: item.value,
      color: item.color,
    }))
    .filter((item) => mode === 'race' || item.value != null)
    .sort((left, right) => {
      if (left.value == null) return right.value == null ? 0 : 1
      if (right.value == null) return -1
      return right.value - left.value
    })
  const participants = items.filter((item) => item.id !== referenceSeriesId)
  const reference = items.find((item) => item.id === referenceSeriesId)

  return (
    <ChartsTooltipContainer {...containerProps} trigger="axis">
      <Paper sx={{ p: 1, minWidth: 160 }}>
        {raceLabel && (
          <Typography variant="caption" sx={{ display: 'block', mb: 0.5, opacity: 0.6 }}>
            {raceLabel}
          </Typography>
        )}
        <Stack spacing={0.25}>
          {participants.map((item) => (
            <Box key={item.id} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Box
                sx={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  backgroundColor: item.color,
                  flexShrink: 0,
                }}
              />
              <Typography variant="caption" sx={{ flex: 1 }}>
                {item.label}
              </Typography>
              <Typography
                variant="caption"
                sx={{ fontWeight: item.value == null ? 400 : 'bold' }}
                color={item.value == null ? 'text.secondary' : 'inherit'}
              >
                {item.value == null
                  ? 'No prediction'
                  : mode === 'gap' && item.value === 0
                    ? '0 · leader'
                    : item.value}
              </Typography>
            </Box>
          ))}
          {reference && (
            <>
              <Divider sx={{ my: 0.5 }} />
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Box
                  sx={{
                    width: 16,
                    borderTop: `2px dashed ${reference.color}`,
                    flexShrink: 0,
                  }}
                />
                <Typography variant="caption" sx={{ flex: 1 }}>
                  {reference.label}
                </Typography>
                <Typography variant="caption" sx={{ fontWeight: 'bold' }}>
                  {reference.value}
                </Typography>
              </Stack>
            </>
          )}
        </Stack>
      </Paper>
    </ChartsTooltipContainer>
  )
}
