import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Button,
  Container,
  IconButton,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import { useEffect, useRef } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { isUnauthorized } from '../api/session'
import PredictionForm from '../components/prediction/PredictionForm'
import { useLeague } from '../context/league'
import { useCurrentRace, useOwnPrediction, useRaceDetails } from '../queries/predictions'

export default function RacePage({ current = false }: { current?: boolean }) {
  const { raceId } = useParams()
  const { profile, selectedLeague, selectedSeason, selectSeason } = useLeague()
  const specificRace = useRaceDetails(
    profile.userId,
    current ? undefined : selectedLeague?.leagueId,
    raceId,
  )
  const currentRace = useCurrentRace(
    profile.userId,
    current ? selectedLeague?.leagueId : undefined,
    selectedSeason,
  )
  const details = current ? currentRace : specificRace
  const page = details.data
  const own = useOwnPrediction(
    profile.userId,
    selectedLeague?.leagueId,
    current ? page?.race.id : raceId,
  )
  const season = page?.season
  const navigate = useNavigate()
  const previousSeason = useRef(selectedSeason)

  useEffect(() => {
    if (current || season === undefined) return
    if (previousSeason.current !== selectedSeason && selectedSeason !== season)
      void navigate('/calendar')
    previousSeason.current = selectedSeason
  }, [current, selectedSeason, season, navigate])

  useEffect(() => {
    if (!current && season !== undefined) selectSeason(season)
  }, [current, season, selectSeason])

  if (
    (details.isError && isUnauthorized(details.error)) ||
    (own.isError && isUnauthorized(own.error))
  )
    return <Navigate to="/auth" replace />

  function refresh() {
    void details.refetch()
    if (page || !current) void own.refetch()
  }

  return (
    <Container
      component="section"
      maxWidth="xl"
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <title>{`${import.meta.env.DEV ? '[dev] ' : ''}${current ? 'Current prediction' : (page?.race.name ?? 'Race')}`}</title>
      <Stack spacing={1.5}>
        <Stack
          direction="row"
          sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2 }}
        >
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <Button
              component={Link}
              to="/calendar"
              size="small"
              startIcon={<ArrowBackRounded fontSize="small" />}
            >
              Calendar
            </Button>
            <Typography variant="caption" color="text.secondary">
              {selectedLeague?.leagueName}
            </Typography>
          </Stack>
          <Tooltip title="Refresh race">
            <span>
              <IconButton
                size="small"
                aria-label="Refresh race"
                disabled={!selectedLeague || details.isFetching || own.isFetching}
                onClick={refresh}
              >
                <RefreshRounded fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {!selectedLeague ? (
          <Alert severity="info">Select a league to view this race.</Alert>
        ) : current && selectedSeason === null ? (
          <Alert severity="info">No seasons are available for this league yet.</Alert>
        ) : details.isPending ? (
          <Stack spacing={1.5} role="status" aria-label="Loading race">
            <Skeleton width="55%" height={32} />
            <Skeleton variant="rounded" height={360} />
          </Stack>
        ) : !page && current && !details.isError ? (
          <Alert severity="info">
            No open or upcoming race in {selectedSeason}. You can view past predictions in the
            calendar.
          </Alert>
        ) : !page ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={refresh}>
                Retry
              </Button>
            }
          >
            This race is not available for the selected league, or it could not be loaded.
          </Alert>
        ) : (
          <>
            {details.isError && (
              <Alert
                severity="warning"
                action={
                  <Button color="inherit" size="small" onClick={refresh}>
                    Retry
                  </Button>
                }
              >
                Could not refresh the race. Your draft is still here.
              </Alert>
            )}
            {own.isPending ? (
              <Stack spacing={1.5} role="status" aria-label="Loading your prediction">
                <Typography component="h1" variant="h6">
                  {page.race.name} · {page.season}
                </Typography>
                <Skeleton variant="rounded" height={360} />
              </Stack>
            ) : own.isError && own.data === undefined ? (
              <Alert
                severity="error"
                action={
                  <Button color="inherit" size="small" onClick={refresh}>
                    Retry
                  </Button>
                }
              >
                Could not load your saved prediction.
              </Alert>
            ) : (
              <>
                {own.isError && (
                  <Alert severity="warning">
                    Could not refresh your saved prediction. Your draft is still here.
                  </Alert>
                )}
                <PredictionForm
                  key={`${profile.userId}:${selectedLeague.leagueId}:${page.race.id}`}
                  page={page}
                  prediction={own.data ?? null}
                  userId={profile.userId}
                  league={selectedLeague}
                  leagues={profile.leagues}
                  receivedAt={details.dataUpdatedAt}
                />
              </>
            )}
          </>
        )}
      </Stack>
    </Container>
  )
}
