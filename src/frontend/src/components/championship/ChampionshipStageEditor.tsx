import {
  Alert,
  Button,
  Checkbox,
  FormControlLabel,
  MenuItem,
  Skeleton,
  Stack,
  TextField,
} from '@mui/material'
import { useState } from 'react'
import { Navigate } from 'react-router'
import type { ChampionshipAdminAction } from '../../api/championship'
import { isUnauthorized } from '../../api/session'
import type { ChampionshipSeasonDto, ChampionshipStageDto } from '../../dto/championship'
import { useStageRosterRaces } from '../../queries/championship'

export default function ChampionshipStageEditor({
  state,
  userId,
  busy,
  saving,
  onSave,
}: {
  state: ChampionshipSeasonDto
  userId: string
  busy: boolean
  saving: boolean
  onSave: (action: Extract<ChampionshipAdminAction, { type: 'saveStage' }>) => void
}) {
  const currentStage = state.currentStage ?? 'PRE_SEASON'
  const [stage, setStage] = useState<ChampionshipStageDto>(currentStage)
  const [open, setOpen] = useState(state.predictionsOpen)
  const [lineupRaceId, setLineupRaceId] = useState('')
  const [now] = useState(() => Date.now())
  const changed = stage !== currentStage || open !== state.predictionsOpen
  const roster = stage === 'PRE_SEASON' ? state.preSeasonRoster : state.midSeasonRoster
  const needsLineup = changed && open && roster.length === 0
  const rosterRaces = useStageRosterRaces(userId, needsLineup ? state.season : null)
  const races = rosterRaces.data ?? []
  const byDate = races.toSorted(
    (left, right) => Date.parse(left.closeAt) - Date.parse(right.closeAt),
  )
  const suggested =
    stage === 'PRE_SEASON'
      ? races[0]
      : (byDate.find((race) => Date.parse(race.closeAt) >= now) ?? byDate.at(-1))
  const selected = races.find((race) => race.id === lineupRaceId) ?? suggested
  const canSave = changed && (!needsLineup || (selected && !rosterRaces.isError))

  if (isUnauthorized(rosterRaces.error)) return <Navigate to="/auth" replace />

  return (
    <Stack spacing={1.5} sx={{ alignItems: 'flex-start' }}>
      <TextField
        select
        fullWidth
        size="small"
        label="Stage"
        value={stage}
        disabled={busy}
        onChange={(event) => {
          setStage(event.target.value as ChampionshipStageDto)
          setLineupRaceId('')
        }}
      >
        <MenuItem value="PRE_SEASON">Pre-season</MenuItem>
        <MenuItem value="MID_SEASON">Mid-season</MenuItem>
      </TextField>
      <FormControlLabel
        control={
          <Checkbox
            checked={open}
            disabled={busy}
            onChange={(event) => setOpen(event.target.checked)}
          />
        }
        label="Predictions open"
      />
      {needsLineup &&
        (rosterRaces.isPending ? (
          <Skeleton width="100%" height={40} aria-label="Loading race lineups" />
        ) : rosterRaces.isError ? (
          <Alert
            severity="error"
            action={<Button onClick={() => void rosterRaces.refetch()}>Retry</Button>}
          >
            Could not load race lineups.
          </Alert>
        ) : selected ? (
          <TextField
            select
            fullWidth
            size="small"
            label="Race lineup"
            value={selected.id}
            disabled={busy}
            onChange={(event) => setLineupRaceId(event.target.value)}
          >
            {races.map((race) => (
              <MenuItem key={race.id} value={race.id}>
                {race.name}
              </MenuItem>
            ))}
          </TextField>
        ) : (
          <Alert severity="info">No saved race lineups.</Alert>
        ))}
      <Button
        variant="contained"
        size="small"
        loading={saving}
        disabled={busy || !canSave}
        onClick={() =>
          onSave({ type: 'saveStage', stage, isOpen: open, lineupRaceId: selected?.id })
        }
      >
        Save stage
      </Button>
    </Stack>
  )
}
