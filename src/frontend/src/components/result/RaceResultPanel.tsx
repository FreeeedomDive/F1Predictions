import {
  Alert,
  Button,
  Chip,
  Divider,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Typography,
} from '@mui/material'
import { raceResultErrorMessage } from '../../api/raceResults'
import type { PredictionDto } from '../../dto/prediction'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import type { RaceResultDetailsDto } from '../../dto/raceResult'
import { DriverLabel } from '../prediction/DriverSelect'

export default function RaceResultPanel({
  data,
  loading,
  error,
  teams,
  prediction,
  positionDriver,
  onRefresh,
}: {
  data: RaceResultDetailsDto | null | undefined
  loading: boolean
  error: Error | null
  teams: RaceLineupTeamDto[]
  prediction: PredictionDto | null | undefined
  positionDriver?: string | null
  onRefresh: () => void
}) {
  const points = data?.points
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
    <Stack spacing={1.5} sx={{ minWidth: 0 }}>
      <Paper variant="outlined" sx={{ minWidth: 0, borderRadius: 1 }}>
        <Typography variant="body2" sx={{ p: 1.5, fontWeight: 600 }}>
          Results
        </Typography>
        {loading && data === undefined ? (
          <Stack
            spacing={1}
            sx={{ px: 1.5, pb: 1.5 }}
            role="status"
            aria-label="Loading race results"
          >
            <Skeleton variant="rounded" height={28} />
            <Skeleton variant="rounded" height={180} />
          </Stack>
        ) : error && !data ? (
          <Alert
            severity="error"
            sx={{ mx: 1.5, mb: 1.5 }}
            action={
              <Button size="small" color="inherit" onClick={onRefresh}>
                Retry
              </Button>
            }
          >
            {raceResultErrorMessage(error, 'Could not load the race result and your points.')}
          </Alert>
        ) : !data ? (
          <Typography variant="body2" color="text.secondary" sx={{ px: 1.5, pb: 1.5 }}>
            Race results have not been published yet. They will appear here after the result is
            finalized.
          </Typography>
        ) : (
          <>
            {error && (
              <Alert
                severity="warning"
                sx={{ mx: 1.5, mb: 1.5 }}
                action={
                  <Button size="small" color="inherit" onClick={onRefresh}>
                    Retry
                  </Button>
                }
              >
                Could not refresh the result. Showing the last loaded result and points.
              </Alert>
            )}
            {data.result.classification?.length ? (
              <TableContainer>
                <Table
                  size="small"
                  aria-label="Published race classification"
                  sx={{
                    tableLayout: 'fixed',
                    '& td': { py: 0.5, px: 1, borderColor: 'divider' },
                    '& tr:last-child td': { borderBottom: 0 },
                  }}
                >
                  <colgroup>
                    <col style={{ width: 30 }} />
                    <col />
                    <col style={{ width: 52 }} />
                  </colgroup>
                  <TableBody>
                    {data.result.classification.map((entry, index) => {
                      const tenth = entry.driverName === prediction?.tenthPlacePickedDriver
                      const selectedDnf = prediction?.dnfPickedDrivers?.includes(entry.driverName)
                      return (
                        <TableRow
                          key={entry.driverName}
                          title={
                            tenth
                              ? 'Your 10th-place pick'
                              : selectedDnf
                                ? 'Your DNF pick'
                                : undefined
                          }
                          sx={{
                            bgcolor: tenth
                              ? 'rgba(165, 180, 252, 0.08)'
                              : selectedDnf
                                ? 'rgba(255, 167, 38, 0.05)'
                                : 'transparent',
                          }}
                        >
                          <TableCell>
                            <Typography
                              variant="caption"
                              sx={{
                                fontVariantNumeric: 'tabular-nums',
                                color: index === 9 ? 'primary.main' : 'text.secondary',
                                fontWeight: index === 9 ? 700 : 400,
                              }}
                            >
                              {index + 1}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <DriverLabel
                              name={entry.driverName}
                              teams={teams}
                              highlighted={entry.driverName === positionDriver}
                            />
                          </TableCell>
                          <TableCell align="right" sx={{ pl: 0 }}>
                            {entry.status !== 'FINISHED' && (
                              <Chip
                                size="small"
                                variant="outlined"
                                label={entry.status}
                                color={
                                  entry.status === 'DNF'
                                    ? 'warning'
                                    : entry.status === 'DQ'
                                      ? 'error'
                                      : 'default'
                                }
                                sx={{
                                  height: 19,
                                  fontSize: '0.6rem',
                                  '& .MuiChip-label': { px: 0.6 },
                                }}
                              />
                            )}
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Typography variant="body2" color="text.secondary" sx={{ px: 1.5, pb: 1.5 }}>
                Classification is not available.
              </Typography>
            )}
            <Stack spacing={0.5} sx={{ p: 1.5, borderTop: 1, borderColor: 'divider' }}>
              {data.result.safetyCars != null && (
                <Typography variant="body2">
                  Incidents: <strong>{data.result.safetyCars}</strong>
                </Typography>
              )}
              {data.result.firstPlaceLead != null && (
                <Typography variant="body2">
                  Winner's gap: <strong>{data.result.firstPlaceLead} s</strong>
                </Typography>
              )}
            </Stack>
          </>
        )}
      </Paper>
      {data && (
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
                <Typography
                  variant="h6"
                  sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
                >
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
      )}
    </Stack>
  )
}
