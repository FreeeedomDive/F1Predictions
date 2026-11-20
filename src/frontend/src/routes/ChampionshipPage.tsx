import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Avatar,
  Box,
  Button,
  Container,
  IconButton,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import { Navigate } from 'react-router'
import { championshipErrorMessage } from '../api/championship'
import { isUnauthorized } from '../api/session'
import ChampionshipDriverOrder from '../components/championship/ChampionshipDriverOrder'
import ChampionshipPredictionCard from '../components/championship/ChampionshipPredictionCard'
import {
  championshipTableSx,
  pointsCell,
  positionCell,
} from '../components/championship/championshipTableStyles'
import { useLeague } from '../context/league'
import { useChampionship } from '../queries/championship'
import { useParticipantAvatars } from '../queries/participants'

export default function ChampionshipPage() {
  const { profile, selectedLeague, selectedSeason } = useLeague()
  const leagueId = selectedLeague?.leagueId
  const championship = useChampionship(profile.userId, leagueId, selectedSeason, {
    refetchInterval: 30_000,
  })
  const details = championship.data
  const avatars = useParticipantAvatars(profile.userId, details?.rows.length ? leagueId : undefined)

  if (championship.isError && isUnauthorized(championship.error)) {
    return <Navigate to="/auth" replace />
  }

  return (
    <Container
      component="section"
      maxWidth={false}
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={3}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Championship
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {selectedLeague?.leagueName ?? 'No league selected'}
              {selectedSeason !== null && ` · ${selectedSeason}`}
            </Typography>
          </Stack>
          <Tooltip title="Refresh championship">
            <span>
              <IconButton
                aria-label="Refresh championship"
                disabled={!leagueId || selectedSeason === null || championship.isFetching}
                onClick={() => void championship.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {details?.configured && championship.isRefetchError && (
          <Alert severity="warning">
            Could not refresh the championship. Showing the last update.
          </Alert>
        )}
        {!leagueId ? (
          <Alert severity="info">
            Championship predictions will appear once you join a league.
          </Alert>
        ) : selectedSeason === null ? (
          <Alert severity="info">No season is available for this league.</Alert>
        ) : championship.isPending ? (
          <Skeleton variant="rounded" height={160} aria-label="Loading championship" />
        ) : championship.isError && !details?.configured ? (
          <Alert
            severity="error"
            action={<Button onClick={() => void championship.refetch()}>Retry</Button>}
          >
            {championshipErrorMessage(
              championship.error,
              'Could not load the championship. Please try again.',
            )}
          </Alert>
        ) : !details?.configured ? (
          <Alert severity="info">
            Championship predictions are not configured for {selectedSeason}.
          </Alert>
        ) : (
          <Box
            key={`${profile.userId}:${leagueId}:${selectedSeason}`}
            sx={{
              display: 'grid',
              gridTemplateColumns: {
                xs: 'minmax(0, 1fr)',
                md: 'repeat(2, minmax(0, 1fr))',
                lg: 'repeat(4, minmax(0, 1fr))',
              },
              gap: 2,
              alignItems: 'start',
            }}
          >
            <ChampionshipPredictionCard
              userId={profile.userId}
              leagueId={leagueId}
              season={selectedSeason}
              stage="PRE_SEASON"
              roster={details.preSeasonRoster}
              teams={details.teams}
              prediction={details.preSeasonPrediction}
              open={details.currentStage === 'PRE_SEASON' && details.predictionsOpen}
              published={
                details.currentStage === 'MID_SEASON' ||
                (details.stageStarted && !details.predictionsOpen)
              }
            />
            <ChampionshipPredictionCard
              userId={profile.userId}
              leagueId={leagueId}
              season={selectedSeason}
              stage="MID_SEASON"
              roster={details.midSeasonRoster}
              teams={details.teams}
              prediction={details.midSeasonPrediction}
              open={details.currentStage === 'MID_SEASON' && details.predictionsOpen}
              published={details.currentStage === 'MID_SEASON' && !details.predictionsOpen}
            />
            <Paper
              component="section"
              aria-label="Official driver standings"
              variant="outlined"
              sx={{ minWidth: 0, overflow: 'hidden' }}
            >
              <Typography component="h2" variant="h6" sx={{ p: 2 }}>
                Official driver standings
              </Typography>
              {details.driverStandings.length > 0 ? (
                <ChampionshipDriverOrder
                  label="Official driver standings"
                  drivers={details.driverStandings}
                  teams={details.teams}
                  currentDrivers={details.currentDrivers}
                />
              ) : (
                <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 2 }}>
                  Official standings are not available yet.
                </Typography>
              )}
            </Paper>
            <Paper
              component="section"
              aria-label="League points"
              variant="outlined"
              sx={{ minWidth: 0, overflow: 'hidden' }}
            >
              <Typography component="h2" variant="h6" sx={{ p: 2 }}>
                League points
              </Typography>
              {details.rows.length > 0 ? (
                <TableContainer>
                  <Table
                    size="small"
                    aria-label="League championship points"
                    sx={championshipTableSx}
                  >
                    <TableHead>
                      <TableRow>
                        <TableCell align="right" sx={positionCell}>
                          Pos.
                        </TableCell>
                        <TableCell>Participant</TableCell>
                        <TableCell align="right" sx={pointsCell}>
                          Points
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody sx={{ '& tr:last-of-type > td': { borderBottom: 0 } }}>
                      {details.rows.map((row, index) => (
                        <TableRow
                          key={row.participantId}
                          selected={row.participantId === selectedLeague?.participantId}
                        >
                          <TableCell align="right" sx={{ color: 'text.secondary' }}>
                            {index + 1}
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                              <Avatar
                                src={avatars.data?.[row.participantId] ?? undefined}
                                alt=""
                                aria-hidden="true"
                                sx={{ width: 24, height: 24, flexShrink: 0, fontSize: '0.75rem' }}
                              >
                                {row.displayName.trim().slice(0, 1).toLocaleUpperCase()}
                              </Avatar>
                              <Typography
                                variant="body2"
                                noWrap
                                title={row.displayName}
                                sx={{ minWidth: 0 }}
                              >
                                {row.displayName}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell align="right">{row.totalPoints}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              ) : (
                <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 2 }}>
                  Points appear after a stage closes and official standings are available.
                </Typography>
              )}
            </Paper>
          </Box>
        )}
      </Stack>
    </Container>
  )
}
