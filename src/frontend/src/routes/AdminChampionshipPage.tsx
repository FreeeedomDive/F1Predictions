import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  IconButton,
  MenuItem,
  Paper,
  Skeleton,
  Snackbar,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate } from 'react-router'
import { championshipErrorMessage } from '../api/championship'
import { isUnauthorized } from '../api/session'
import ChampionshipPredictionStatuses from '../components/championship/ChampionshipPredictionStatuses'
import ChampionshipStageEditor from '../components/championship/ChampionshipStageEditor'
import ChampionshipStandingsEditor from '../components/championship/ChampionshipStandingsEditor'
import { useLeague } from '../context/league'
import type { ChampionshipSeasonDto } from '../dto/championship'
import {
  adminChampionshipKey,
  useAdminChampionship,
  useUpdateAdminChampionship,
} from '../queries/championship'

function ChampionshipEditor({ state, userId }: { state: ChampionshipSeasonDto; userId: string }) {
  const { season, currentStage, predictionsOpen } = state
  const [rulesVersion, setRulesVersion] = useState(
    season <= 2026 ? 'championship-v2026' : 'championship-v2027',
  )
  const update = useUpdateAdminChampionship(userId, season)
  if (isUnauthorized(update.error)) {
    return <Navigate to="/auth" replace />
  }

  return (
    <Stack spacing={2}>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))' },
          gap: 2,
          alignItems: 'start',
        }}
      >
        {state.configured && (
          <ChampionshipStandingsEditor
            state={state}
            busy={update.isPending}
            saving={update.isPending && update.variables.type === 'saveStandings'}
            onSave={(driverStandings, onSaved) =>
              update.mutate({ type: 'saveStandings', driverStandings }, { onSuccess: onSaved })
            }
          />
        )}
        <Stack spacing={2} sx={{ minWidth: 0 }}>
          <Paper
            component="section"
            aria-label="Championship stages"
            variant="outlined"
            sx={{ p: 2 }}
          >
            <Stack spacing={2}>
              <Typography component="h2" variant="h6">
                Championship stages
              </Typography>
              {!state.configured ? (
                <>
                  <TextField
                    select
                    fullWidth
                    size="small"
                    label="Rules version"
                    value={rulesVersion}
                    disabled={update.isPending}
                    onChange={(event) => setRulesVersion(event.target.value)}
                  >
                    <MenuItem value="championship-v2026">2026</MenuItem>
                    <MenuItem value="championship-v2027">2027</MenuItem>
                  </TextField>
                  <Button
                    variant="contained"
                    size="small"
                    loading={update.isPending}
                    sx={{ alignSelf: 'flex-start' }}
                    onClick={() => update.mutate({ type: 'configure', rulesVersion })}
                  >
                    Configure season
                  </Button>
                </>
              ) : (
                <>
                  <Chip
                    size="small"
                    variant="outlined"
                    label={state.rulesVersion}
                    sx={{ alignSelf: 'flex-start' }}
                  />
                  <ChampionshipStageEditor
                    key={`${currentStage}:${predictionsOpen}`}
                    state={state}
                    userId={userId}
                    busy={update.isPending}
                    saving={update.isPending && update.variables.type === 'saveStage'}
                    onSave={(action) => update.mutate(action)}
                  />
                </>
              )}
            </Stack>
          </Paper>
          <ChampionshipPredictionStatuses season={season} />
        </Stack>
      </Box>
      <Snackbar
        open={update.isSuccess || update.isError}
        autoHideDuration={6000}
        onClose={() => update.reset()}
      >
        <Alert severity={update.isError ? 'error' : 'success'} onClose={() => update.reset()}>
          {update.isError
            ? championshipErrorMessage(update.error, 'Could not update the championship.')
            : update.variables?.type === 'saveStandings'
              ? 'Standings saved.'
              : 'Championship updated.'}
        </Alert>
      </Snackbar>
    </Stack>
  )
}

export default function AdminChampionshipPage() {
  const { profile, selectedSeason } = useLeague()
  const championship = useAdminChampionship(profile.userId, selectedSeason)
  const client = useQueryClient()
  if (isUnauthorized(championship.error)) return <Navigate to="/auth" replace />

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
              Championship administration
            </Typography>
            {selectedSeason !== null && (
              <Typography variant="body2" color="text.secondary">
                Season {selectedSeason}
              </Typography>
            )}
          </Stack>
          <Tooltip title="Refresh championship">
            <span>
              <IconButton
                aria-label="Refresh championship"
                disabled={selectedSeason === null || championship.isFetching}
                onClick={() =>
                  void client.invalidateQueries({
                    queryKey: adminChampionshipKey(profile.userId, selectedSeason),
                  })
                }
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {selectedSeason === null ? (
          <Alert severity="info">No season selected.</Alert>
        ) : championship.isPending ? (
          <Skeleton variant="rounded" height={160} aria-label="Loading championship" />
        ) : !championship.data ? (
          <Alert
            severity="error"
            action={<Button onClick={() => void championship.refetch()}>Retry</Button>}
          >
            {championshipErrorMessage(championship.error, 'Could not load the championship.')}
          </Alert>
        ) : (
          <>
            {championship.isRefetchError && (
              <Alert severity="warning">Could not refresh the championship.</Alert>
            )}
            <ChampionshipEditor
              key={selectedSeason}
              state={championship.data}
              userId={profile.userId}
            />
          </>
        )}
      </Stack>
    </Container>
  )
}
