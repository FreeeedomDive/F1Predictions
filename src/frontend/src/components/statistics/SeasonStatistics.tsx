import { Box, Divider, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import type { SeasonDriverStatsDto, SeasonStatsDto } from '../../dto/statistics'
import IncidentsTable from './IncidentsTable'
import LeadGapStaircase from './LeadGapStaircase'
import StatsCard from './StatsCard'

function driverItems(stats: SeasonDriverStatsDto[]) {
  return stats.map((item) => ({ id: item.driver, label: item.driver, value: item.score }))
}

function StatsSection({
  title,
  columns = 3,
  children,
}: {
  title: string
  columns?: 2 | 3
  children: ReactNode
}) {
  return (
    <Stack spacing={2} sx={{ py: 2 }}>
      <Typography
        component="h2"
        variant="subtitle1"
        sx={{ fontWeight: 600, color: 'text.secondary' }}
      >
        {title}
      </Typography>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: 'minmax(0, 1fr)',
            md: `repeat(${columns}, minmax(0, 1fr))`,
          },
          gap: 2,
        }}
      >
        {children}
      </Box>
    </Stack>
  )
}

export default function SeasonStatistics({
  stats,
  currentParticipantId,
}: {
  stats: SeasonStatsDto
  currentParticipantId: string
}) {
  return (
    <Stack spacing={1} divider={<Divider sx={{ borderColor: 'rgba(255,255,255,0.12)' }} />}>
      <StatsSection title="10th place">
        <StatsCard
          title="Prediction points by driver"
          description="Total points each driver earned for all participants through 10th-place predictions."
          items={driverItems(stats.tenthPlacePointsRating)}
          unit="point"
        />
        <StatsCard
          title="Most popular picks"
          description="Number of times each driver was selected in a 10th-place prediction."
          items={driverItems(stats.mostPickedForTenthPlace)}
          unit="pick"
        />
        <StatsCard
          title="10th-place picks that retired"
          description="Number of 10th-place picks where the selected driver did not finish the race (DNF)."
          items={driverItems(stats.tenthPickedButDnfed)}
        />
        <StatsCard
          title="Points from actual finishes"
          description="Points you would have earned by picking the same driver for 10th place throughout the season."
          items={driverItems(stats.driverOwnTenthPlacePoints)}
          unit="point"
        />
        <StatsCard
          title="Average points per pick"
          description="Average points each driver earned per 10th-place prediction, rounded down."
          items={driverItems(stats.tenthPlacePredictionEfficiency)}
          unit="point"
        />
        <StatsCard
          title="Unluckiest participants"
          description="Number of 10th-place picks that resulted in a DNF."
          items={stats.tenthPlaceDnfAntiRating.map((item) => ({
            id: item.participantId,
            label: item.participantName,
            value: item.score,
            isMe: item.participantId === currentParticipantId,
          }))}
        />
      </StatsSection>
      <StatsSection title="DNF">
        <StatsCard
          title="Most retirements"
          description="Number of actual DNFs per driver."
          items={driverItems(stats.mostDnfDrivers)}
        />
        <StatsCard
          title="Most popular DNF picks"
          description="Number of times each driver was picked to DNF."
          items={driverItems(stats.mostPickedForDnf)}
          unit="pick"
        />
      </StatsSection>
      <StatsSection title="Incidents" columns={2}>
        <IncidentsTable stats={stats} />
      </StatsSection>
      <StatsSection title="Winning margin" columns={2}>
        <LeadGapStaircase
          data={stats.closestLeadGapPredictions}
          currentParticipantId={currentParticipantId}
        />
      </StatsSection>
    </Stack>
  )
}
