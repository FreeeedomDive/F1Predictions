import AddRounded from '@mui/icons-material/AddRounded'
import BlockRounded from '@mui/icons-material/BlockRounded'
import DoneRounded from '@mui/icons-material/DoneRounded'
import RemoveRounded from '@mui/icons-material/RemoveRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  InputAdornment,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { Link, Navigate } from 'react-router'
import { raceResultErrorMessage } from '../../api/raceResults'
import { isUnauthorized } from '../../api/session'
import type { RaceResultEditorDto } from '../../dto/raceResult'
import { useFinalizeRaceResult, useSaveRaceResultDraft } from '../../queries/raceResults'
import { useChangeRacePredictionsStatus } from '../../queries/races'
import ResultClassificationTable from './ResultClassificationTable'
import { resultDraft, resultSignature, resultValues } from './resultDraft'
import type { ResultDraft } from './resultDraft'

const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })
const time = new Intl.DateTimeFormat('en-GB', { timeStyle: 'short' })
const statusLabels = {
  SCHEDULED: 'Scheduled',
  OPEN: 'Predictions open',
  CLOSED: 'Predictions closed',
  FINISHED: 'Finished',
}

export default function AdminResultEditor({
  page,
  userId,
  draft,
  busy,
  onDraftChange,
  onBusyChange,
}: {
  page: RaceResultEditorDto
  userId: string
  draft: ResultDraft | undefined
  busy: boolean
  onDraftChange: (raceId: string, draft: ResultDraft | undefined) => void
  onBusyChange: (busy: boolean) => void
}) {
  const save = useSaveRaceResultDraft()
  const finalize = useFinalizeRaceResult()
  const transition = useChangeRacePredictionsStatus()
  const [operation, setOperation] = useState<'save' | 'finalize' | 'close' | null>(null)
  const [dragging, setDragging] = useState(false)
  const [lastSaved, setLastSaved] = useState<number | null>(null)
  const [notice, setNotice] = useState<{ severity: 'success' | 'error'; message: string } | null>(
    null,
  )
  const inFlight = useRef(false)
  const current = draft ?? resultDraft(page)
  const values = resultValues(current, page.season)
  const savedValues = page.result?.draft ?? page.result?.result
  const dirty = draft !== undefined && resultSignature(values) !== resultSignature(savedValues)
  const additional = page.season >= 2024
  const normalizedLead = current.lead.trim().replace(',', '.')
  const validLead =
    !additional ||
    !normalizedLead ||
    (/^\d+(?:\.\d{1,3})?$/.test(normalizedLead) && Number(normalizedLead) <= 999999.999)
  const incidents = Number(current.incidents)
  const validIncidents =
    !additional ||
    !current.incidents.trim() ||
    (/^\d+$/.test(current.incidents.trim()) &&
      Number.isInteger(incidents) &&
      incidents <= 2147483647)
  const roster = page.teams.flatMap((team) => team.drivers)
  const rosterMatches =
    current.classification.length === roster.length &&
    roster.length > 0 &&
    current.classification.every((entry) => roster.includes(entry.driverName))
  const expectedRules = page.season === 2027 ? '2027-v1' : String(page.season)
  const supportedRules =
    page.season >= 2023 &&
    page.season <= 2027 &&
    (!page.result || page.result.rulesVersion === expectedRules)
  const editable = page.race.status !== 'SCHEDULED' && page.lineupPrepared && supportedRules
  const pending = busy || operation !== null
  const canSave = editable && validLead && validIncidents && rosterMatches
  const complete =
    canSave && (!additional || (normalizedLead !== '' && current.incidents.trim() !== ''))
  const canFinalize = complete && (page.race.status === 'CLOSED' || page.race.status === 'FINISHED')
  const variables = { userId, raceId: page.race.id, season: page.season }

  function update(changes: Partial<ResultDraft>) {
    onDraftChange(page.race.id, { ...current, ...changes })
  }

  async function persistDraft(automatic = false) {
    if (!canSave || !dirty || pending || dragging || inFlight.current) return
    inFlight.current = true
    setOperation('save')
    onBusyChange(true)
    try {
      await save.mutateAsync({ ...variables, values })
      onDraftChange(page.race.id, undefined)
      setLastSaved(Date.now())
      if (!automatic) setNotice({ severity: 'success', message: 'Result draft saved.' })
    } catch (error) {
      setNotice({
        severity: 'error',
        message: raceResultErrorMessage(
          error,
          'Could not save the draft. Your changes are still here.',
        ),
      })
    } finally {
      inFlight.current = false
      setOperation(null)
      onBusyChange(false)
    }
  }

  async function finalizeResult() {
    if (!canFinalize || pending || dragging || inFlight.current) return
    inFlight.current = true
    setOperation('finalize')
    onBusyChange(true)
    const correction = page.race.status === 'FINISHED'
    try {
      if (dirty) await save.mutateAsync({ ...variables, values })
      await finalize.mutateAsync(variables)
      onDraftChange(page.race.id, undefined)
      setNotice({
        severity: 'success',
        message: correction
          ? 'Race results updated and points recalculated.'
          : 'Race finished and prediction points calculated.',
      })
    } catch (error) {
      setNotice({
        severity: 'error',
        message: raceResultErrorMessage(
          error,
          'Could not finalize the result. Your changes are still here.',
        ),
      })
    } finally {
      inFlight.current = false
      setOperation(null)
      onBusyChange(false)
    }
  }

  async function closePredictions() {
    if (pending || dragging || inFlight.current) return
    inFlight.current = true
    setOperation('close')
    onBusyChange(true)
    try {
      await transition.mutateAsync({ raceId: page.race.id, action: 'close' })
      setNotice({ severity: 'success', message: 'Predictions closed.' })
    } catch (error) {
      setNotice({
        severity: 'error',
        message: raceResultErrorMessage(error, 'Could not close predictions.'),
      })
    } finally {
      inFlight.current = false
      setOperation(null)
      onBusyChange(false)
    }
  }

  const autosave = useEffectEvent(() => {
    if (canSave && dirty) void persistDraft(true)
  })

  useEffect(() => {
    const timer = window.setInterval(() => autosave(), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  if ([save.error, finalize.error, transition.error].some(isUnauthorized))
    return <Navigate to="/auth" replace />

  function syncClassification() {
    const kept = current.classification.filter((entry) => roster.includes(entry.driverName))
    const names = new Set(kept.map((entry) => entry.driverName))
    update({
      classification: [
        ...kept,
        ...roster
          .filter((name) => !names.has(name))
          .map((driverName) => ({ driverName, status: 'FINISHED' as const })),
      ],
    })
  }

  const savedText =
    operation === 'save'
      ? 'Saving draft…'
      : operation === 'finalize'
        ? save.isPending
          ? 'Saving the latest draft…'
          : 'Calculating points…'
        : dirty
          ? 'Unsaved changes · autosave every minute'
          : page.result?.draft
            ? lastSaved === null
              ? 'Saved draft loaded'
              : `Draft saved at ${time.format(lastSaved)}`
            : page.result?.result
              ? 'Published result'
              : 'No result saved yet'

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={1}>
          <Typography variant="overline" color="text.secondary">
            Target race · {page.season}
          </Typography>
          <Stack direction="row" sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
            <Typography component="h2" variant="h6">
              {page.race.name}
            </Typography>
            <Chip
              size="small"
              variant="outlined"
              label={page.race.type === 'SPRINT' ? 'Sprint' : 'Race'}
            />
            <Chip
              size="small"
              variant="outlined"
              color={page.race.status === 'OPEN' ? 'success' : 'default'}
              label={statusLabels[page.race.status]}
            />
            {page.result?.rulesVersion && (
              <Chip size="small" variant="outlined" label={`Rules ${page.result.rulesVersion}`} />
            )}
            {page.result?.result && (dirty || page.result.draft) && (
              <Chip size="small" variant="outlined" color="warning" label="Unpublished changes" />
            )}
            {page.race.status === 'OPEN' && (
              <Button
                size="small"
                color="warning"
                variant="outlined"
                startIcon={<BlockRounded />}
                loading={operation === 'close'}
                disabled={pending || dragging}
                onClick={() => void closePredictions()}
                sx={{ ml: { sm: 'auto' } }}
              >
                Close predictions
              </Button>
            )}
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Predictions close {dateTime.format(new Date(page.race.closeAt))} (local time)
          </Typography>
        </Stack>
      </Paper>
      {!page.lineupPrepared && (
        <Alert severity="info">
          Prepare the race lineup in <Link to="/admin/teams">Teams</Link> before entering a result.
        </Alert>
      )}
      {page.race.status === 'SCHEDULED' && (
        <Alert severity="info">
          Open predictions in the <Link to="/admin/races">calendar</Link> before entering a result.
        </Alert>
      )}
      {!supportedRules && (
        <Alert severity="info">
          This season or saved rules version is available for reading only.
        </Alert>
      )}
      {!rosterMatches && page.lineupPrepared && (
        <Alert
          severity="warning"
          action={
            <Button
              color="inherit"
              size="small"
              disabled={!editable || pending || dragging}
              onClick={syncClassification}
            >
              Use current lineup
            </Button>
          }
        >
          The classification does not match this race's lineup. Update it before saving.
        </Alert>
      )}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: 'minmax(0, 1fr)',
            lg: 'minmax(0, 2fr) minmax(280px, 1fr)',
          },
          gap: 2,
          alignItems: 'start',
        }}
      >
        <Paper variant="outlined" sx={{ minWidth: 0, borderRadius: 2 }}>
          <Stack
            direction="row"
            sx={{
              p: 2,
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 1,
            }}
          >
            <Typography sx={{ fontWeight: 600 }}>Race result</Typography>
            <Chip
              size="small"
              variant="outlined"
              color={dirty ? 'warning' : 'default'}
              label={savedText}
            />
          </Stack>
          <ResultClassificationTable
            entries={current.classification}
            teams={page.teams}
            positionDriver={page.conditions?.positionPredictionDriver}
            disabled={!editable || pending}
            onChange={(classification) => update({ classification })}
            onDraggingChange={setDragging}
          />
          <Stack direction="row" spacing={1} sx={{ p: 2, flexWrap: 'wrap', rowGap: 1 }}>
            <Button
              variant="outlined"
              size="small"
              startIcon={<SaveRounded />}
              loading={operation === 'save'}
              disabled={!canSave || !dirty || pending || dragging}
              onClick={() => void persistDraft()}
            >
              Save draft
            </Button>
            {draft !== undefined && (
              <Button
                size="small"
                disabled={pending || dragging}
                onClick={() => onDraftChange(page.race.id, undefined)}
              >
                Discard local changes
              </Button>
            )}
          </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ p: 2, minWidth: 0, borderRadius: 2 }}>
          <Stack spacing={2}>
            <Typography sx={{ fontWeight: 600 }}>Race parameters</Typography>
            {additional && (
              <>
                <Stack spacing={0.5}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <IconButton
                      size="small"
                      aria-label="Decrease incident count"
                      disabled={
                        !editable ||
                        pending ||
                        !current.incidents.trim() ||
                        !validIncidents ||
                        incidents <= 0
                      }
                      onClick={() => update({ incidents: String(incidents - 1) })}
                      sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}
                    >
                      <RemoveRounded fontSize="small" />
                    </IconButton>
                    <TextField
                      label="Incidents"
                      size="small"
                      fullWidth
                      value={current.incidents}
                      disabled={!editable || pending}
                      error={!validIncidents}
                      slotProps={{ htmlInput: { inputMode: 'numeric' } }}
                      onChange={(event) => update({ incidents: event.target.value })}
                    />
                    <IconButton
                      size="small"
                      aria-label="Increase incident count"
                      disabled={!editable || pending || !validIncidents || incidents >= 2147483647}
                      onClick={() => update({ incidents: String(incidents + 1) })}
                      sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}
                    >
                      <AddRounded fontSize="small" />
                    </IconButton>
                  </Stack>
                  <Typography variant="caption" color={validIncidents ? 'text.secondary' : 'error'}>
                    {validIncidents
                      ? 'VSC, SC and red flags. Enter 0 if none.'
                      : 'Enter a non-negative whole number.'}
                  </Typography>
                </Stack>
                <TextField
                  label="Winner's gap"
                  size="small"
                  fullWidth
                  value={current.lead}
                  disabled={!editable || pending}
                  error={!validLead}
                  helperText={
                    validLead
                      ? 'Gap to second place; comma or dot accepted.'
                      : 'Use 0–999999.999 seconds, up to 3 decimal places.'
                  }
                  slotProps={{
                    htmlInput: { inputMode: 'decimal' },
                    input: { endAdornment: <InputAdornment position="end">s</InputAdornment> },
                  }}
                  onChange={(event) => update({ lead: event.target.value.replace(',', '.') })}
                />
                <Divider />
              </>
            )}
            <Typography sx={{ fontWeight: 600 }}>
              {page.race.status === 'FINISHED'
                ? 'Recalculate predictions'
                : 'Calculate predictions'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {page.race.status === 'OPEN'
                ? 'Close predictions before finalizing the result.'
                : !complete
                  ? 'Fill the complete classification and all race parameters before calculating.'
                  : 'The latest draft is saved first, then the result and points are finalized for all leagues.'}
            </Typography>
            <Button
              variant="contained"
              startIcon={<DoneRounded />}
              loading={operation === 'finalize'}
              disabled={!canFinalize || pending || dragging}
              onClick={() => void finalizeResult()}
            >
              {page.race.status === 'FINISHED' ? 'Save & recalculate' : 'Calculate & finish'}
            </Button>
            <Typography variant="caption" color="text.secondary">
              Drafts are autosaved every minute while this editor is open. Saving a draft does not
              calculate points.
            </Typography>
          </Stack>
        </Paper>
      </Box>
      <Snackbar open={notice !== null} autoHideDuration={6000} onClose={() => setNotice(null)}>
        <Alert severity={notice?.severity} variant="filled" onClose={() => setNotice(null)}>
          {notice?.message}
        </Alert>
      </Snackbar>
    </Stack>
  )
}
