import {
  Alert,
  Button,
  Chip,
  Paper,
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
import DriverListSkeleton from '../DriverListSkeleton'
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
  const driverCount = teams.reduce((count, team) => count + team.drivers.length, 0)

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
            <DriverListSkeleton count={driverCount} />
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
          <Stack spacing={1.5} sx={{ px: 1.5, pb: 1.5 }}>
            <Alert severity="info">Race results have not been published yet.</Alert>
            <DriverListSkeleton count={driverCount} />
          </Stack>
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
    </Stack>
  )
}
