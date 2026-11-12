import { Box, Typography } from '@mui/material'
import { Fragment } from 'react'
import type { SeasonLeadGapPredictionStatsDto } from '../../dto/statistics'
import { StatsPanel } from './StatsCard'
import { STATISTICS_BAR_COLOR } from './statisticsStyles'

export default function LeadGapStaircase({
  data,
  currentParticipantId,
}: {
  data: SeasonLeadGapPredictionStatsDto[]
  currentParticipantId: string
}) {
  const maxDifference = Math.max(0.01, ...data.map((item) => item.difference))

  return (
    <StatsPanel
      title="Closest winning-margin predictions"
      description="Smallest absolute difference from the actual winning margin, in seconds."
    >
      {data.length === 0 ? (
        <Typography variant="body2" sx={{ color: 'text.disabled' }}>
          No data yet.
        </Typography>
      ) : (
        <Box
          sx={{
            mt: 1,
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 2fr)',
            alignItems: 'center',
            rowGap: 1,
          }}
        >
          {data.map((item, index) => {
            const label = `${item.participantName} (${item.raceName})`
            const difference = `+${item.difference.toFixed(3)}`

            return (
              <Fragment key={`${item.participantId}_${index}`}>
                <Typography
                  variant="caption"
                  title={label}
                  noWrap
                  sx={{
                    textAlign: 'right',
                    pr: 1.5,
                    minWidth: 0,
                    color:
                      item.participantId === currentParticipantId
                        ? 'primary.main'
                        : 'text.secondary',
                  }}
                >
                  {label}
                </Typography>
                <Box sx={{ pr: 7, minWidth: 0 }}>
                  <Box
                    role="img"
                    aria-label={`${label}: ${difference} seconds`}
                    sx={{
                      width: `${(item.difference / maxDifference) * 100}%`,
                      minWidth: 8,
                      height: 28,
                      bgcolor: item.difference === 0 ? 'success.main' : STATISTICS_BAR_COLOR,
                      borderRadius: '0 4px 4px 0',
                      position: 'relative',
                    }}
                  >
                    <Typography
                      variant="caption"
                      sx={{
                        position: 'absolute',
                        left: '100%',
                        top: '50%',
                        transform: 'translateY(-50%)',
                        pl: 1,
                        fontWeight: 700,
                        whiteSpace: 'nowrap',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {difference}
                    </Typography>
                  </Box>
                </Box>
              </Fragment>
            )
          })}
        </Box>
      )}
    </StatsPanel>
  )
}
