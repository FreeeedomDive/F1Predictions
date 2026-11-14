import { Divider, Paper, Stack, Typography } from '@mui/material'
import type { PredictionDto } from '../../dto/prediction'
import type { RaceResultDetailsDto } from '../../dto/raceResult'

export default function PredictionPointsPanel({
  data,
  prediction,
}: {
  data: RaceResultDetailsDto | null | undefined
  prediction: PredictionDto | null | undefined
}) {
  if (!data) return null

  const points = data.points
  const categories = points
    ? [
        { label: '10th place', value: points.tenthPlacePoints },
        { label: data.season === 2023 ? 'First DNF' : 'DNF', value: points.dnfsPoints },
        { label: 'Incidents', value: points.safetyCarsPoints },
        { label: "Winner's gap", value: points.firstPlaceLeadPoints },
        { label: 'Team duels', value: points.teamMatesPoints },
        { label: 'Driver position', value: points.driverPositionPoints },
      ].filter((category) => category.value != null)
    : []

  return (
    <Paper variant="outlined" sx={{ p: 1.5, minWidth: 0, borderRadius: 1 }}>
      <Stack spacing={1.25}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 1 }}
        >
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            Your points
          </Typography>
          {points && (
            <Typography variant="h6" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
              {points.totalPoints}
            </Typography>
          )}
        </Stack>
        {points ? (
          <>
            {categories.map((category) => (
              <Stack
                key={category.label}
                direction="row"
                sx={{ justifyContent: 'space-between', gap: 1 }}
              >
                <Typography variant="body2" color="text.secondary">
                  {category.label}
                </Typography>
                <Typography
                  variant="body2"
                  sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}
                >
                  {category.value}
                </Typography>
              </Stack>
            ))}
            {categories.length === 0 && (
              <Typography variant="caption" color="text.secondary">
                No category points are recorded.
              </Typography>
            )}
            <Divider />
            <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 1 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                Total
              </Typography>
              <Typography
                variant="body2"
                sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
              >
                {points.totalPoints}
              </Typography>
            </Stack>
            {data.race.type === 'SPRINT' && data.season >= 2025 && (
              <Typography variant="caption" color="text.secondary">
                Category points are shown before the 30% sprint coefficient. The total already
                includes it.
              </Typography>
            )}
          </>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {prediction === null
              ? 'You did not submit a prediction for this race.'
              : 'Your calculated points are not available yet.'}
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}
