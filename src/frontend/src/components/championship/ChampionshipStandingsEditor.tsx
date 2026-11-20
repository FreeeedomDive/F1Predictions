import SaveRounded from '@mui/icons-material/SaveRounded'
import { Button, Paper, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import type { ChampionshipSeasonDto } from '../../dto/championship'
import ChampionshipDriverOrder from './ChampionshipDriverOrder'

const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })

export default function ChampionshipStandingsEditor({
  state,
  busy,
  saving,
  onSave,
}: {
  state: ChampionshipSeasonDto
  busy: boolean
  saving: boolean
  onSave: (drivers: string[], onSaved: () => void) => void
}) {
  const [draft, setDraft] = useState<string[] | null>(null)
  const saved = state.driverStandings
  const drivers = draft ?? saved
  const roster = state.currentStage === 'MID_SEASON' ? state.midSeasonRoster : state.preSeasonRoster
  const changed =
    drivers.length !== saved.length || drivers.some((driver, index) => driver !== saved[index])
  const update = state.standingsUpdate

  return (
    <Paper component="section" aria-label="Official driver standings" variant="outlined">
      <Stack spacing={1} sx={{ p: 2 }}>
        <Typography component="h2" variant="h6">
          Official driver standings
        </Typography>
        {update && (
          <Typography variant="body2" color="text.secondary">
            Last updated {dateTime.format(new Date(update.updatedAt))}
            {update.lastSyncedRound !== null && ` · Last synced round ${update.lastSyncedRound}`}
          </Typography>
        )}
        {drivers.length === 0 && roster.length > 0 && (
          <Button disabled={busy} onClick={() => setDraft(roster)} sx={{ alignSelf: 'flex-start' }}>
            Use stage driver list
          </Button>
        )}
      </Stack>
      {drivers.length > 0 && (
        <ChampionshipDriverOrder
          label="Official driver standings"
          drivers={drivers}
          teams={state.teams}
          currentDrivers={state.currentDrivers}
          disabled={busy}
          onChange={setDraft}
        />
      )}
      <Stack spacing={2} sx={{ p: 2 }}>
        <Stack direction="row" spacing={1}>
          <Button
            variant="contained"
            size="small"
            startIcon={<SaveRounded />}
            loading={saving}
            disabled={busy || !changed || drivers.length === 0}
            onClick={() => onSave(drivers, () => setDraft(null))}
          >
            Save standings
          </Button>
          {changed && (
            <Button size="small" disabled={busy} onClick={() => setDraft(null)}>
              Discard
            </Button>
          )}
        </Stack>
      </Stack>
    </Paper>
  )
}
