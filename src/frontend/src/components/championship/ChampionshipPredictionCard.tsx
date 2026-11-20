import SaveRounded from '@mui/icons-material/SaveRounded'
import { Alert, Button, Chip, Paper, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { championshipErrorMessage } from '../../api/championship'
import type { ChampionshipPredictionDto, ChampionshipStageDto } from '../../dto/championship'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import { useSaveChampionshipPrediction } from '../../queries/championship'
import ChampionshipDriverOrder from './ChampionshipDriverOrder'

export default function ChampionshipPredictionCard({
  userId,
  leagueId,
  season,
  stage,
  roster,
  teams,
  open,
  published,
  prediction,
}: {
  userId: string
  leagueId: string
  season: number
  stage: ChampionshipStageDto
  roster: string[]
  teams: RaceLineupTeamDto[]
  open: boolean
  published: boolean
  prediction: ChampionshipPredictionDto | null
}) {
  const [draft, setDraft] = useState<string[] | null>(null)
  const save = useSaveChampionshipPrediction(userId, leagueId, season, stage)
  const title = stage === 'PRE_SEASON' ? 'Pre-season prediction' : 'Mid-season prediction'
  const savedOrder = prediction?.driverStandings
  const matchesRoster =
    savedOrder?.length === roster.length &&
    new Set(savedOrder).size === roster.length &&
    roster.every((driver) => savedOrder.includes(driver))
  const drivers = open ? (draft ?? (matchesRoster ? savedOrder : roster)) : (savedOrder ?? [])
  const changed = !matchesRoster || drivers.some((driver, index) => driver !== savedOrder[index])
  const state = open ? 'Open' : published ? 'Closed' : 'Not open yet'
  const predictionStatus =
    prediction?.predictionSource === 'PRE_SEASON_COPY'
      ? 'Copied from pre-season'
      : open && prediction && !changed
        ? 'Saved'
        : open || (published && !prediction)
          ? 'Not saved'
          : null

  return (
    <Paper
      component="section"
      aria-label={title}
      variant="outlined"
      sx={{ minWidth: 0, overflow: open ? undefined : 'hidden' }}
    >
      <Stack spacing={1} sx={{ p: 2 }}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}
        >
          <Typography component="h2" variant="h6">
            {title}
          </Typography>
          <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
            {predictionStatus && (
              <Chip
                label={predictionStatus}
                color={predictionStatus === 'Saved' ? 'success' : 'default'}
                size="small"
                variant="outlined"
              />
            )}
            <Chip
              label={state}
              color={open ? 'success' : 'default'}
              size="small"
              variant="outlined"
            />
          </Stack>
        </Stack>
        {open && savedOrder && !matchesRoster && (
          <Alert severity="info">
            The saved order does not match this stage’s driver list. Rank the drivers and save a new
            prediction.
          </Alert>
        )}
      </Stack>
      {drivers.length > 0 && (
        <ChampionshipDriverOrder
          label={title}
          drivers={drivers}
          teams={teams}
          points={open ? undefined : prediction?.points}
          disabled={save.isPending}
          onChange={open ? setDraft : undefined}
        />
      )}
      {(open || save.isError) && (
        <Stack spacing={1} sx={{ p: 2, alignItems: 'flex-start' }}>
          {open && (
            <Button
              variant="contained"
              size="small"
              startIcon={<SaveRounded />}
              disabled={!changed || drivers.length === 0}
              loading={save.isPending}
              onClick={() => save.mutate(drivers, { onSuccess: () => setDraft(null) })}
            >
              Save prediction
            </Button>
          )}
          {save.isError && (
            <Alert severity="error">
              {championshipErrorMessage(
                save.error,
                'Could not save your prediction. Please try again.',
              )}
            </Alert>
          )}
        </Stack>
      )}
    </Paper>
  )
}
