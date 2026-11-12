import { Box, Paper, Typography } from '@mui/material'
import type { SeasonStandingsDto } from '../../dto/standings'

export default function SeasonSummary({ standings }: { standings: SeasonStandingsDto }) {
  const leader = standings.rows.find(
    (row) => row.participantId === standings.currentLeaderParticipantId,
  )
  const items = [
    { label: 'Current leader', value: leader?.participantName ?? '—' },
    { label: 'Leader points', value: standings.currentLeaderPoints },
    { label: 'Maximum points remaining', value: standings.pointsLeft },
  ]

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(3, minmax(0, 1fr))' },
        gap: 2,
      }}
    >
      {items.map((item) => (
        <Paper key={item.label} variant="outlined" sx={{ p: 2, minWidth: 0 }}>
          <Typography variant="body2" color="text.secondary">
            {item.label}
          </Typography>
          <Typography
            sx={{
              mt: 0.5,
              fontWeight: 600,
              overflowWrap: 'anywhere',
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {item.value}
          </Typography>
        </Paper>
      ))}
    </Box>
  )
}
