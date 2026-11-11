import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Button,
  Chip,
  Container,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import { useCallback, useId, useState } from 'react'
import { Navigate } from 'react-router'
import { raceResultErrorMessage } from '../api/raceResults'
import { isUnauthorized } from '../api/session'
import AdminResultEditor from '../components/result/AdminResultEditor'
import type { ResultDraft } from '../components/result/resultDraft'
import { useLeague } from '../context/league'
import { useAdminRaceResult } from '../queries/raceResults'
import { useAdminRaceCalendar } from '../queries/races'

const statusLabels = {
  SCHEDULED: 'Scheduled',
  OPEN: 'Open',
  CLOSED: 'Closed',
  FINISHED: 'Finished',
}

export default function AdminResultsPage() {
  const { profile, selectedSeason } = useLeague()
  const calendar = useAdminRaceCalendar(selectedSeason)
  const [selection, setSelection] = useState<{ season: number; raceId: string } | null>(null)
  const [drafts, setDrafts] = useState<Record<string, ResultDraft | undefined>>({})
  const [busy, setBusy] = useState(false)
  const races = calendar.data ?? []
  const raceId =
    selection?.season === selectedSeason && races.some((race) => race.id === selection.raceId)
      ? selection.raceId
      : undefined
  const editor = useAdminRaceResult(profile.userId, raceId)
  const page = editor.data
  const labelId = useId()
  const changeDraft = useCallback((id: string, draft: ResultDraft | undefined) => {
    setDrafts((current) => ({ ...current, [id]: draft }))
  }, [])

  if (
    (calendar.isError && isUnauthorized(calendar.error)) ||
    (editor.isError && isUnauthorized(editor.error))
  )
    return <Navigate to="/auth" replace />

  function refresh() {
    void calendar.refetch()
    if (raceId) void editor.refetch()
  }

  return (
    <Container
      component="section"
      maxWidth="xl"
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={2}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Result administration
            </Typography>
            <Typography variant="body2" color="text.secondary">
              All races and sprints{selectedSeason !== null && ` · ${selectedSeason}`} · Shared
              across all leagues
            </Typography>
          </Stack>
          <Tooltip title="Refresh results">
            <span>
              <IconButton
                aria-label="Refresh results"
                disabled={
                  busy || selectedSeason === null || calendar.isFetching || editor.isFetching
                }
                onClick={refresh}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {selectedSeason === null ? (
          <Alert severity="info">No seasons are available for this league yet.</Alert>
        ) : calendar.isPending ? (
          <Skeleton variant="rounded" height={56} />
        ) : calendar.isError && !calendar.data ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={refresh}>
                Retry
              </Button>
            }
          >
            {raceResultErrorMessage(calendar.error, 'Could not load the season calendar.')}
          </Alert>
        ) : (
          <>
            {calendar.isError && (
              <Alert severity="warning">
                Could not refresh the calendar. Your local changes are still here.
              </Alert>
            )}
            <FormControl size="small" fullWidth disabled={busy || races.length === 0}>
              <InputLabel id={labelId} shrink>
                Target race
              </InputLabel>
              <Select
                labelId={labelId}
                label="Target race"
                value={raceId ?? ''}
                displayEmpty
                notched
                onChange={(event) =>
                  setSelection(
                    event.target.value
                      ? { season: selectedSeason, raceId: event.target.value }
                      : null,
                  )
                }
              >
                <MenuItem value="">Select a race</MenuItem>
                {races.map((race) => (
                  <MenuItem key={race.id} value={race.id}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
                      <Typography variant="body2">
                        {race.name}
                        {race.type === 'SPRINT' ? ' (Sprint)' : ''} · {statusLabels[race.status]}
                      </Typography>
                      {drafts[race.id] && (
                        <Chip
                          size="small"
                          color="warning"
                          variant="outlined"
                          label="Local changes"
                        />
                      )}
                    </Stack>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            {races.length === 0 ? (
              <Alert severity="info">
                No races in {selectedSeason}. The season calendar has not been imported yet.
              </Alert>
            ) : !raceId ? (
              <Alert severity="info">
                Select a race to enter its result or correct a finished race.
              </Alert>
            ) : editor.isPending ? (
              <Stack spacing={1} role="status" aria-label="Loading result editor">
                <Skeleton variant="rounded" height={100} />
                <Skeleton variant="rounded" height={400} />
              </Stack>
            ) : !page ? (
              <Alert
                severity="error"
                action={
                  <Button color="inherit" size="small" onClick={refresh}>
                    Retry
                  </Button>
                }
              >
                {raceResultErrorMessage(editor.error, 'Could not load this race result.')}
              </Alert>
            ) : (
              <>
                {editor.isError && (
                  <Alert severity="warning">
                    Could not refresh the result. Your local changes are still here.
                  </Alert>
                )}
                <AdminResultEditor
                  key={`${profile.userId}:${page.race.id}`}
                  page={page}
                  userId={profile.userId}
                  draft={drafts[page.race.id]}
                  busy={busy}
                  onDraftChange={changeDraft}
                  onBusyChange={setBusy}
                />
              </>
            )}
          </>
        )}
      </Stack>
    </Container>
  )
}
