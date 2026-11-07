import GroupsRounded from '@mui/icons-material/GroupsRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Button,
  Chip,
  Container,
  IconButton,
  Paper,
  Skeleton,
  Snackbar,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import { isAxiosError } from 'axios'
import { Navigate } from 'react-router'
import { isUnauthorized } from '../api/session'
import LineupEditor from '../components/lineup/LineupEditor'
import { useLeague } from '../context/league'
import type { RaceLineupTeamDto } from '../dto/raceLineup'
import { useRaceLineupTarget, useSaveRaceLineupTeam } from '../queries/raceLineups'

const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })

function errorMessage(error: Error | null, fallback: string) {
  if (isAxiosError<{ detail?: string }>(error)) {
    if (error.response?.status === 409)
      return 'This race is no longer editable. Check the current target race.'
    const detail = error.response?.data?.detail
    if (typeof detail === 'string' && detail) return detail
    if (error.response?.status === 403) return 'You do not have permission to access this lineup.'
    if (error.response?.status === 404) return 'This race no longer exists. Refresh the lineup.'
  }
  return fallback
}

export default function AdminTeamsPage() {
  const { selectedSeason } = useLeague()
  const target = useRaceLineupTarget(selectedSeason)
  const save = useSaveRaceLineupTeam()
  const lineup = target.data

  if (
    (target.isError && isUnauthorized(target.error)) ||
    (save.isError && isUnauthorized(save.error))
  ) {
    return <Navigate to="/auth" replace />
  }

  function saveTeam(
    team: RaceLineupTeamDto,
    previousName: string | undefined,
    onSaved: () => void,
  ) {
    if (!lineup || selectedSeason === null) return
    save.mutate(
      { season: selectedSeason, raceId: lineup.race.id, previousName, team },
      { onSuccess: onSaved },
    )
  }

  return (
    <Container
      component="section"
      maxWidth="xl"
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={3}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Team administration
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Race lineup{selectedSeason !== null && ` · ${selectedSeason}`} · Shared across all
              leagues
            </Typography>
          </Stack>
          <Tooltip title="Refresh lineup">
            <span>
              <IconButton
                aria-label="Refresh lineup"
                disabled={selectedSeason === null || target.isFetching || save.isPending}
                onClick={() => void target.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>

        {selectedSeason === null ? (
          <Alert severity="info">
            No seasons available. This league's first season has not started yet.
          </Alert>
        ) : target.isPending ? (
          <Stack spacing={1} role="status" aria-label="Loading lineup">
            {[0, 1, 2, 3].map((index) => (
              <Skeleton key={index} variant="rounded" height={80} />
            ))}
          </Stack>
        ) : target.isError && !lineup ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" onClick={() => void target.refetch()}>
                Retry
              </Button>
            }
          >
            {errorMessage(target.error, 'Could not load the lineup. Please try again.')}
          </Alert>
        ) : !lineup ? (
          <Paper variant="outlined" sx={{ p: { xs: 3, md: 5 }, textAlign: 'center' }}>
            <GroupsRounded sx={{ color: 'text.secondary', fontSize: 36, mb: 1.5 }} />
            <Typography variant="h6" sx={{ mb: 1 }}>
              No editable race in {selectedSeason}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              There are no open or upcoming scheduled races in this season.
            </Typography>
          </Paper>
        ) : (
          <>
            {target.isError && (
              <Alert severity="warning">
                {errorMessage(
                  target.error,
                  'Could not refresh the lineup. Your changes are still here. Try Refresh again.',
                )}
              </Alert>
            )}
            <Paper variant="outlined" sx={{ p: 2.5 }}>
              <Stack spacing={1}>
                <Typography variant="overline" color="text.secondary">
                  Target race
                </Typography>
                <Stack direction="row" sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
                  <Typography component="h2" variant="h6">
                    {lineup.race.name}
                  </Typography>
                  <Chip
                    size="small"
                    variant="outlined"
                    label={lineup.race.type === 'SPRINT' ? 'Sprint' : 'Race'}
                  />
                  <Chip
                    size="small"
                    color={lineup.race.status === 'OPEN' ? 'success' : 'default'}
                    label={lineup.race.status === 'OPEN' ? 'Predictions open' : 'Scheduled'}
                  />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  Predictions close {dateTime.format(new Date(lineup.race.closeAt))} (local time)
                </Typography>
              </Stack>
            </Paper>
            {!lineup.prepared && (
              <Alert severity="info">
                {lineup.teams.length > 0
                  ? 'This is a preview of the previous lineup. Saving a team will prepare the lineup for this race.'
                  : 'Add the first team to prepare the lineup for this race.'}
              </Alert>
            )}
            <LineupEditor
              key={lineup.race.id}
              lineup={lineup}
              busy={save.isPending || target.isFetching}
              saving={save.isPending && save.variables?.raceId === lineup.race.id}
              savingTeam={save.variables?.previousName}
              onSave={saveTeam}
            />
          </>
        )}
      </Stack>
      <Snackbar
        open={save.isSuccess || save.isError}
        autoHideDuration={6000}
        onClose={() => save.reset()}
      >
        <Alert severity={save.isError ? 'error' : 'success'} onClose={() => save.reset()}>
          {save.isError
            ? errorMessage(save.error, 'Could not save the team. Please try again.')
            : `${save.variables?.team.name ?? 'Team'} saved.`}
        </Alert>
      </Snackbar>
    </Container>
  )
}
