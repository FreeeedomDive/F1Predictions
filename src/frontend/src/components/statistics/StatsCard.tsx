import { Box, Card, CardContent, LinearProgress, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import StatisticsInfo from './StatisticsInfo'
import { STATISTICS_BAR_COLOR, STATISTICS_TRACK_COLOR } from './statisticsStyles'

export interface StatsItem {
  id: string
  label: string
  value: number
  isMe?: boolean
}

export function StatsPanel({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children: ReactNode
}) {
  return (
    <Card
      sx={{
        minWidth: 0,
        height: '100%',
        borderRadius: '4px',
        bgcolor: 'transparent',
        backgroundImage: 'var(--Paper-overlay)',
        position: 'relative',
      }}
    >
      <CardContent>
        <Typography
          component="h3"
          variant="subtitle2"
          gutterBottom
          sx={{ pr: 3.5, fontWeight: 600, color: 'text.secondary' }}
        >
          {title}
        </Typography>
        {children}
      </CardContent>
      <Box sx={{ position: 'absolute', top: 8, right: 8 }}>
        <StatisticsInfo title={title} description={description} />
      </Box>
    </Card>
  )
}

export default function StatsCard({
  title,
  description,
  items,
  unit = 'time',
}: {
  title: string
  description: string
  items: StatsItem[]
  unit?: 'point' | 'time' | 'pick' | 'race'
}) {
  const maxScore = Math.max(1, ...items.map((item) => item.value))

  return (
    <StatsPanel title={title} description={description}>
      {items.length === 0 ? (
        <Typography variant="body2" sx={{ color: 'text.disabled' }}>
          No data yet.
        </Typography>
      ) : (
        <Stack component="ol" spacing={0.75} sx={{ p: 0, m: 0, listStyle: 'none' }}>
          {items.map((item, index) => (
            <Box component="li" key={item.id}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.25, minWidth: 0 }}>
                <Typography
                  variant="caption"
                  sx={{ width: 18, flexShrink: 0, color: 'text.disabled' }}
                >
                  {index + 1}.
                </Typography>
                <Typography
                  variant="body2"
                  title={item.label}
                  noWrap
                  sx={{
                    flex: 1,
                    minWidth: 0,
                    fontWeight: item.isMe ? 600 : 400,
                    color: item.isMe ? 'primary.main' : 'text.primary',
                  }}
                >
                  {item.label}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}
                >
                  {item.value}
                  <Typography component="span" variant="caption" sx={{ color: 'text.disabled' }}>
                    {' '}
                    {unit}
                    {item.value === 1 ? '' : 's'}
                  </Typography>
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={(item.value / maxScore) * 100}
                aria-label={`${item.label}: ${item.value} ${unit}${item.value === 1 ? '' : 's'}`}
                sx={{
                  height: 4,
                  borderRadius: 2,
                  bgcolor: STATISTICS_TRACK_COLOR,
                  '& .MuiLinearProgress-bar': { bgcolor: STATISTICS_BAR_COLOR },
                }}
              />
            </Box>
          ))}
        </Stack>
      )}
    </StatsPanel>
  )
}
