import ArrowDropDownRounded from '@mui/icons-material/ArrowDropDownRounded'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  ButtonGroup,
  Checkbox,
  Chip,
  Divider,
  FormControl,
  FormHelperText,
  InputAdornment,
  InputLabel,
  Menu,
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useEffect, useId, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Navigate } from 'react-router'
import { predictionErrorMessage } from '../../api/predictions'
import { isUnauthorized } from '../../api/session'
import type {
  PredictionDto,
  RaceDetailsDto,
  SafetyCarsCountDto,
  SavePredictionRequestDto,
} from '../../dto/prediction'
import type { UserLeagueDto } from '../../dto/user'
import { useSavePredictions } from '../../queries/predictions'
import type { PredictionSaveResult } from '../../queries/predictions'
import DriverSelect, { DriverLabel } from './DriverSelect'
import StartingGrid from './StartingGrid'

interface Draft {
  tenth: string
  dnf: string[]
  safety: SafetyCarsCountDto | ''
  position: string
  lead: string
}

const NOBODY = '__no_dnf__'
const safetyLabels: Record<SafetyCarsCountDto, string> = {
  ZERO: '0',
  ONE: '1',
  TWO: '2',
  THREE_PLUS: '3+',
}
const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })

function toDraft(prediction: PredictionDto | null): Draft {
  return {
    tenth: prediction?.tenthPlacePickedDriver ?? '',
    dnf:
      prediction?.dnfPickedDrivers == null
        ? []
        : prediction.dnfPickedDrivers.length === 0
          ? [NOBODY]
          : prediction.dnfPickedDrivers,
    safety: prediction?.safetyCarsPrediction ?? '',
    position:
      prediction?.driverPositionPrediction == null
        ? ''
        : String(prediction.driverPositionPrediction),
    lead:
      prediction?.firstPlaceLeadPrediction == null
        ? ''
        : String(prediction.firstPlaceLeadPrediction),
  }
}

function toValues(draft: Draft, page: RaceDetailsDto): SavePredictionRequestDto {
  return {
    tenthPlacePickedDriver: draft.tenth || null,
    dnfPickedDrivers: draft.dnf.length === 0 ? null : draft.dnf.filter((name) => name !== NOBODY),
    safetyCarsPrediction: page.season >= 2024 ? draft.safety || null : null,
    driverPositionPrediction:
      page.conditions?.positionPredictionDriver && draft.position ? Number(draft.position) : null,
    firstPlaceLeadPrediction:
      page.season >= 2024 && draft.lead.trim() ? Number(draft.lead.replace(',', '.')) : null,
  }
}

function signature(values: SavePredictionRequestDto) {
  return JSON.stringify({
    ...values,
    dnfPickedDrivers: values.dnfPickedDrivers == null ? null : [...values.dnfPickedDrivers].sort(),
  })
}

export default function PredictionForm({
  page,
  prediction,
  userId,
  league,
  leagues,
  receivedAt,
  resultPanel,
  pointsPanel,
  predictionState,
}: {
  page: RaceDetailsDto
  prediction: PredictionDto | null
  userId: string
  league: UserLeagueDto
  leagues: UserLeagueDto[]
  receivedAt: number
  resultPanel: ReactNode
  pointsPanel: ReactNode
  predictionState?: ReactNode
}) {
  const [draft, setDraft] = useState<Draft | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const [report, setReport] = useState<PredictionSaveResult[] | null>(null)
  const [retry, setRetry] = useState<{
    leagues: UserLeagueDto[]
    values: SavePredictionRequestDto
  } | null>(null)
  const save = useSavePredictions()
  const dnfLabel = useId()
  const menuId = useId()
  const serverNow = now + Date.parse(page.serverTime) - receivedAt
  const accepting =
    page.race.status === 'OPEN' && page.predictionsOpen && serverNow < Date.parse(page.race.closeAt)
  const current = accepting ? (draft ?? toDraft(prediction)) : toDraft(prediction)
  const roster = page.teams.flatMap((team) => team.drivers)
  const drivers = [
    ...new Set([...page.startingGrid.filter((name) => roster.includes(name)), ...roster]),
  ]
  const positionDriver = page.conditions?.positionPredictionDriver
  const positionTeam = page.teams.find((team) => team.drivers.includes(positionDriver ?? ''))
  const dnfCount = page.season === 2023 ? 1 : 5
  const values = toValues(current, page)
  const dirty = !prediction || signature(values) !== signature(toValues(toDraft(prediction), page))
  const eligible = leagues.filter(
    (item) =>
      item.firstSeason <= page.season && (page.race.type !== 'SPRINT' || item.sprintsEnabled),
  )
  const disabled = !accepting || save.isPending
  const selectedDnf = current.dnf.filter((name) => name !== NOBODY)
  const dnfText = current.dnf.includes(NOBODY)
    ? 'Nobody'
    : current.dnf.length
      ? `${current.dnf.length}/${dnfCount} · ${current.dnf.join(', ')}`
      : `Choose ${dnfCount} driver${dnfCount === 1 ? '' : 's'}${page.season >= 2024 ? ' or Nobody' : ''}`
  const validDnf =
    current.dnf.length === 0 ||
    (page.season >= 2024 && current.dnf.includes(NOBODY)) ||
    selectedDnf.length === dnfCount
  const missingDrivers = [current.tenth, ...selectedDnf].filter(
    (name) => name && !roster.includes(name),
  )
  const validPosition =
    !positionDriver ||
    !current.position ||
    (Number.isInteger(Number(current.position)) &&
      Number(current.position) >= 1 &&
      Number(current.position) <= roster.length)
  const normalizedLead = current.lead.trim().replace(',', '.')
  const validLead =
    page.season < 2024 ||
    !normalizedLead ||
    (/^\d+(?:\.\d{0,3})?$/.test(normalizedLead) && Number(normalizedLead) <= 999999.999)
  const valid = validDnf && validPosition && validLead && missingDrivers.length === 0
  const failed = report?.filter((result) => !result.saved) ?? []
  const sameRetryValues = retry && signature(retry.values) === signature(values)
  const saveHint = save.isPending
    ? 'Saving prediction…'
    : !validDnf
      ? `Choose ${dnfCount} different drivers${page.season >= 2024 ? ' or Nobody' : ''}.`
      : !validLead
        ? 'Check the winner’s gap.'
        : !validPosition
          ? 'Check the finishing position.'
          : !prediction && !draft
            ? 'Make your picks'
            : dirty
              ? 'Unsaved changes'
              : 'Prediction saved'

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  if (report?.some((result) => !result.saved && isUnauthorized(result.error)))
    return <Navigate to="/auth" replace />

  function update(patch: Partial<Draft>) {
    setDraft({ ...current, ...patch })
  }

  async function persist(targets: UserLeagueDto[], submitted = values) {
    if (!accepting || !valid || save.isPending || targets.length === 0) return
    setMenuAnchor(null)
    setReport(null)
    let results: PredictionSaveResult[]
    try {
      results = await save.mutateAsync({
        userId,
        raceId: page.race.id,
        leagues: [...targets],
        values: submitted,
      })
    } catch (error) {
      results = targets.map((target) => ({ league: target, saved: false, error }))
    }
    setReport(results)
    setRetry({
      leagues: results.filter((result) => !result.saved).map((result) => result.league),
      values: submitted,
    })
    if (results.some((result) => result.saved && result.league.leagueId === league.leagueId))
      setDraft(null)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (dirty) void persist([league])
  }

  function changeDnf(next: string[]) {
    if (next.includes(NOBODY) && !current.dnf.includes(NOBODY)) update({ dnf: [NOBODY] })
    else update({ dnf: next.filter((name) => name !== NOBODY) })
  }

  return (
    <Stack spacing={1.5}>
      <Stack spacing={0.5}>
        <Stack direction="row" sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
          <Typography component="h1" variant="h6" sx={{ mr: 0.5, fontWeight: 600 }}>
            {page.race.name} · {page.season}
          </Typography>
          {page.race.type === 'SPRINT' && <Chip size="small" variant="outlined" label="Sprint" />}
          <Chip
            size="small"
            variant="outlined"
            color={accepting ? 'success' : 'default'}
            label={
              page.race.status === 'SCHEDULED'
                ? 'Scheduled'
                : page.race.status === 'FINISHED'
                  ? 'Finished'
                  : accepting
                    ? 'Predictions open'
                    : 'Predictions closed'
            }
          />
          {prediction && (
            <Chip
              size="small"
              variant="outlined"
              color={dirty && accepting ? 'warning' : 'default'}
              label={dirty && accepting ? 'Unsaved changes' : 'Saved'}
            />
          )}
        </Stack>
        <Typography variant="caption" color="text.secondary">
          Opens {dateTime.format(new Date(page.race.openAt))} · Closes{' '}
          {dateTime.format(new Date(page.race.closeAt))} · Local time
        </Typography>
      </Stack>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(3, minmax(0, 1fr))' },
          gap: 2,
          alignItems: 'start',
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <StartingGrid
            grid={page.startingGrid}
            teams={page.teams}
            tenthPlaceDriver={current.tenth}
            dnfDrivers={selectedDnf}
            positionDriver={positionDriver}
            predictedPosition={
              validPosition && current.position ? Number(current.position) : undefined
            }
          />
        </Box>
        <Stack spacing={1.5} sx={{ minWidth: 0, width: '100%' }}>
          {predictionState !== undefined ? (
            predictionState
          ) : !prediction && !accepting ? (
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
              <Stack spacing={1.5}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  Prediction
                </Typography>
                <Alert severity="info">
                  {page.race.status === 'SCHEDULED'
                    ? 'Predictions have not opened yet.'
                    : 'You did not submit a prediction for this race. Predictions are closed.'}
                </Alert>
              </Stack>
            </Paper>
          ) : (
            <Paper
              component="form"
              variant="outlined"
              onSubmit={submit}
              sx={{ p: 2, borderRadius: 1 }}
            >
              <Stack spacing={2}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  Prediction
                </Typography>
                {!accepting && (
                  <Typography variant="caption" color="text.secondary">
                    Your saved prediction is read-only.
                  </Typography>
                )}
                {!accepting && draft && (
                  <Alert severity="warning">
                    Your unsaved edits were not saved. These are the saved values.
                  </Alert>
                )}
                {accepting && missingDrivers.length > 0 && (
                  <Alert severity="warning">
                    The lineup changed. Review these selections: {missingDrivers.join(', ')}.
                  </Alert>
                )}
                {(accepting || prediction?.tenthPlacePickedDriver != null) && (
                  <DriverSelect
                    label="10th place"
                    value={current.tenth}
                    teams={page.teams}
                    grid={page.startingGrid}
                    disabled={disabled}
                    onChange={(tenth) => update({ tenth })}
                  />
                )}
                {(accepting || prediction?.dnfPickedDrivers != null) && (
                  <FormControl
                    size="small"
                    fullWidth
                    disabled={disabled}
                    error={accepting && !validDnf}
                  >
                    <InputLabel id={dnfLabel} shrink>
                      {page.season === 2023 ? 'First DNF' : 'DNF'}
                    </InputLabel>
                    <Select<string[]>
                      multiple
                      displayEmpty
                      notched
                      labelId={dnfLabel}
                      label={page.season === 2023 ? 'First DNF' : 'DNF'}
                      value={current.dnf}
                      MenuProps={{ slotProps: { paper: { sx: { maxHeight: 440 } } } }}
                      onChange={(event) =>
                        changeDnf(
                          typeof event.target.value === 'string'
                            ? event.target.value.split(',')
                            : event.target.value,
                        )
                      }
                      renderValue={() => (
                        <Box
                          title={dnfText}
                          sx={{
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {dnfText}
                        </Box>
                      )}
                    >
                      {page.season >= 2024 && (
                        <MenuItem value={NOBODY}>
                          <Checkbox
                            checked={current.dnf.includes(NOBODY)}
                            size="small"
                            sx={{ p: 0.5, mr: 1 }}
                          />
                          Nobody
                        </MenuItem>
                      )}
                      {[...new Set([...drivers, ...selectedDnf])].map((name) => (
                        <MenuItem
                          key={name}
                          value={name}
                          disabled={selectedDnf.length >= dnfCount && !selectedDnf.includes(name)}
                        >
                          <Checkbox
                            checked={selectedDnf.includes(name)}
                            size="small"
                            sx={{ p: 0.5, mr: 1 }}
                          />
                          <DriverLabel name={name} teams={page.teams} grid={page.startingGrid} />
                        </MenuItem>
                      ))}
                    </Select>
                    {accepting && !validDnf && (
                      <FormHelperText>
                        Select exactly {dnfCount} different drivers
                        {page.season >= 2024 ? ' or Nobody' : ''}.
                      </FormHelperText>
                    )}
                  </FormControl>
                )}
                {page.season >= 2024 && (accepting || prediction?.safetyCarsPrediction != null) && (
                  <Stack spacing={0.5}>
                    <Typography variant="body2">Incidents</Typography>
                    <ToggleButtonGroup
                      value={current.safety}
                      exclusive
                      fullWidth
                      size="small"
                      color="primary"
                      disabled={disabled}
                      aria-label="Incidents"
                      onChange={(_, safety: SafetyCarsCountDto | null) =>
                        update({ safety: safety ?? '' })
                      }
                    >
                      {Object.entries(safetyLabels).map(([value, label]) => (
                        <ToggleButton key={value} value={value} sx={{ flex: 1, py: 0.5 }}>
                          {label}
                        </ToggleButton>
                      ))}
                    </ToggleButtonGroup>
                    <Typography variant="caption" color="text.secondary">
                      SC, VSC and red flags
                    </Typography>
                  </Stack>
                )}
                {page.season >= 2024 &&
                  (accepting || prediction?.firstPlaceLeadPrediction != null) && (
                    <TextField
                      label="Winner's gap"
                      size="small"
                      fullWidth
                      value={current.lead}
                      disabled={disabled}
                      error={accepting && !validLead}
                      helperText={
                        accepting && !validLead
                          ? 'Enter 0–999999.999 seconds, with up to three decimal places.'
                          : undefined
                      }
                      slotProps={{
                        htmlInput: { inputMode: 'decimal' },
                        input: {
                          endAdornment: <InputAdornment position="end">sec</InputAdornment>,
                        },
                      }}
                      onChange={(event) => {
                        const lead = event.target.value.replace(',', '.')
                        if (/^\d*(\.\d*)?$/.test(lead)) update({ lead })
                      }}
                    />
                  )}
                {positionDriver && (accepting || prediction?.driverPositionPrediction != null) && (
                  <Stack spacing={1}>
                    <Stack
                      direction="row"
                      sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 0.75 }}
                    >
                      <Typography variant="body2">
                        Finishing position for {positionDriver}
                      </Typography>
                      {positionTeam && (
                        <Chip
                          size="small"
                          label={positionTeam.badge}
                          title={positionTeam.name}
                          sx={{
                            bgcolor: positionTeam.backgroundColor,
                            color: positionTeam.textColor,
                            fontWeight: 650,
                            minWidth: 40,
                            height: 20,
                            fontSize: '0.65rem',
                            '& .MuiChip-label': { px: 0.75 },
                          }}
                        />
                      )}
                    </Stack>
                    <TextField
                      select
                      size="small"
                      fullWidth
                      label="Position"
                      value={current.position}
                      disabled={disabled}
                      error={accepting && !validPosition}
                      onChange={(event) => update({ position: event.target.value })}
                    >
                      <MenuItem value="">Not predicted</MenuItem>
                      {Array.from({ length: roster.length }, (_, index) => String(index + 1)).map(
                        (position) => (
                          <MenuItem key={position} value={position}>
                            {position}
                          </MenuItem>
                        ),
                      )}
                      {current.position && Number(current.position) > roster.length && (
                        <MenuItem value={current.position} disabled>
                          {current.position}
                        </MenuItem>
                      )}
                    </TextField>
                  </Stack>
                )}
                {(page.season === 2024 || page.season === 2025) &&
                  prediction?.teamsPickedDrivers &&
                  prediction.teamsPickedDrivers.length > 0 && (
                    <Stack spacing={1}>
                      <Typography variant="caption" color="text.secondary">
                        Team selections · legacy
                      </Typography>
                      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                        {prediction.teamsPickedDrivers.map((name, index) => (
                          <Chip key={`${index}:${name}`} label={name} size="small" />
                        ))}
                      </Stack>
                    </Stack>
                  )}
                {accepting && (
                  <>
                    <Divider />
                    <Stack direction="column" spacing={1.5} sx={{ alignItems: 'stretch' }}>
                      <Stack
                        direction="row"
                        spacing={0.75}
                        sx={{ alignItems: 'center', minWidth: 0 }}
                      >
                        {!dirty && <CheckCircleOutlineRounded fontSize="small" color="success" />}
                        <Typography
                          variant="caption"
                          color={dirty ? 'text.secondary' : 'success.main'}
                        >
                          {saveHint}
                        </Typography>
                      </Stack>
                      <Stack
                        direction="row"
                        spacing={0.5}
                        sx={{ flexShrink: 0, alignSelf: 'flex-end' }}
                      >
                        {draft && (
                          <Button
                            size="small"
                            disabled={save.isPending}
                            onClick={() => setDraft(null)}
                          >
                            Discard
                          </Button>
                        )}
                        <ButtonGroup size="small" variant="contained">
                          <Button
                            type="submit"
                            loading={save.isPending}
                            startIcon={<SaveRounded />}
                            disabled={disabled || !valid || !dirty}
                          >
                            Save prediction
                          </Button>
                          {eligible.length > 1 && (
                            <Button
                              size="small"
                              disabled={disabled || !valid}
                              aria-label="More save options"
                              aria-haspopup="menu"
                              aria-controls={menuAnchor ? menuId : undefined}
                              aria-expanded={Boolean(menuAnchor)}
                              onClick={(event) => setMenuAnchor(event.currentTarget)}
                            >
                              <ArrowDropDownRounded fontSize="small" />
                            </Button>
                          )}
                        </ButtonGroup>
                      </Stack>
                    </Stack>
                    <Menu
                      id={menuId}
                      anchorEl={menuAnchor}
                      open={Boolean(menuAnchor) && eligible.length > 1}
                      onClose={() => setMenuAnchor(null)}
                    >
                      <MenuItem onClick={() => void persist(eligible)}>
                        Save to all {eligible.length} leagues
                      </MenuItem>
                    </Menu>
                  </>
                )}
              </Stack>
            </Paper>
          )}
          {pointsPanel}
        </Stack>
        <Box sx={{ minWidth: 0 }}>{resultPanel}</Box>
      </Box>
      {report && (
        <Snackbar
          open
          autoHideDuration={failed.length === 0 ? 6000 : null}
          onClose={(_, reason) => {
            if (reason !== 'clickaway') setReport(null)
          }}
        >
          <Alert
            severity={
              failed.length === 0
                ? 'success'
                : failed.length === report.length
                  ? 'error'
                  : 'warning'
            }
            onClose={() => setReport(null)}
            sx={{ maxWidth: { xs: 'calc(100vw - 32px)', sm: 480 }, overflowWrap: 'anywhere' }}
          >
            <Stack spacing={0.75}>
              {report.length > 1 && (
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {failed.length === 0
                    ? 'Success'
                    : failed.length === report.length
                      ? 'Save failed'
                      : 'Partial success'}
                </Typography>
              )}
              {report.map((result) => (
                <Typography key={result.league.leagueId} variant="body2">
                  {report.length > 1 && `${result.league.leagueName}: `}
                  {result.saved
                    ? report.length > 1
                      ? 'Saved'
                      : 'Prediction saved.'
                    : predictionErrorMessage(result.error)}
                </Typography>
              ))}
              {failed.length > 0 && retry && (
                <Button
                  color="inherit"
                  size="small"
                  sx={{ alignSelf: 'flex-start' }}
                  disabled={disabled || !valid || !sameRetryValues}
                  onClick={() => void persist(retry.leagues, retry.values)}
                >
                  Retry failed leagues
                </Button>
              )}
            </Stack>
          </Alert>
        </Snackbar>
      )}
    </Stack>
  )
}
