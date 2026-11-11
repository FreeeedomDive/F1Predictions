import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import ErrorOutlineRounded from '@mui/icons-material/ErrorOutlineRounded'
import MoreVertRounded from '@mui/icons-material/MoreVertRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import UndoRounded from '@mui/icons-material/UndoRounded'
import WarningAmberRounded from '@mui/icons-material/WarningAmberRounded'
import {
  Box,
  Checkbox,
  CircularProgress,
  FormControl,
  FormHelperText,
  IconButton,
  InputAdornment,
  InputLabel,
  ListItemIcon,
  Menu,
  MenuItem,
  Select,
  Stack,
  TableCell,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useId, useState } from 'react'
import { adminPredictionErrorMessage } from '../../api/predictions'
import type {
  AdminPredictionDto,
  PredictionDto,
  SafetyCarsCountDto,
  SavePredictionRequestDto,
} from '../../dto/prediction'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import { useSaveAdminPrediction } from '../../queries/predictions'
import DriverSelect, { DriverLabel } from './DriverSelect'

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

function toDraft(prediction: PredictionDto): Draft {
  return {
    tenth: prediction.tenthPlacePickedDriver ?? '',
    dnf:
      prediction.dnfPickedDrivers == null
        ? []
        : prediction.dnfPickedDrivers.length === 0
          ? [NOBODY]
          : prediction.dnfPickedDrivers,
    safety: prediction.safetyCarsPrediction ?? '',
    position:
      prediction.driverPositionPrediction == null
        ? ''
        : String(prediction.driverPositionPrediction),
    lead:
      prediction.firstPlaceLeadPrediction == null
        ? ''
        : String(prediction.firstPlaceLeadPrediction),
  }
}

function toValues(
  draft: Draft,
  season: number,
  positionDriver: string | null,
): SavePredictionRequestDto {
  return {
    tenthPlacePickedDriver: draft.tenth || null,
    dnfPickedDrivers: draft.dnf.length === 0 ? null : draft.dnf.filter((name) => name !== NOBODY),
    safetyCarsPrediction: season >= 2024 ? draft.safety || null : null,
    driverPositionPrediction: positionDriver && draft.position ? Number(draft.position) : null,
    firstPlaceLeadPrediction:
      season >= 2024 && draft.lead.trim() ? Number(draft.lead.replace(',', '.')) : null,
  }
}

function signature(values: SavePredictionRequestDto) {
  return JSON.stringify({
    ...values,
    dnfPickedDrivers: values.dnfPickedDrivers == null ? null : [...values.dnfPickedDrivers].sort(),
  })
}

export default function AdminPredictionRow({
  data,
  userId,
  season,
  teams,
  positionDriver,
  hidden,
  disabled,
  onSavingChange,
  onDirtyChange,
  onSaved,
}: {
  data: AdminPredictionDto
  userId: string
  season: number
  teams: RaceLineupTeamDto[]
  positionDriver: string | null
  hidden: boolean
  disabled: boolean
  onSavingChange: (saving: boolean) => void
  onDirtyChange: (predictionId: string, dirty: boolean) => void
  onSaved: () => void
}) {
  const prediction = data.prediction
  const [draft, setDraft] = useState<Draft>(() => toDraft(prediction))
  const [savedDraft, setSavedDraft] = useState<Draft>(() => toDraft(prediction))
  const [status, setStatus] = useState<'saved' | 'error' | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const save = useSaveAdminPrediction()
  const dnfLabel = useId()
  const menuId = useId()
  const roster = teams.flatMap((team) => team.drivers)
  const drivers = [...new Set(roster)]
  const selectedDnf = draft.dnf.filter((name) => name !== NOBODY)
  const dnfCount = season === 2023 ? 1 : 5
  const dnfText = draft.dnf.includes(NOBODY)
    ? 'Nobody'
    : draft.dnf.length
      ? `${draft.dnf.length}/${dnfCount} · ${draft.dnf.join(', ')}`
      : 'Not predicted'
  const validDnf =
    draft.dnf.length === 0 ||
    (season >= 2024 && draft.dnf.includes(NOBODY)) ||
    selectedDnf.length === dnfCount
  const validPosition =
    !positionDriver ||
    !draft.position ||
    (Number.isInteger(Number(draft.position)) &&
      Number(draft.position) >= 1 &&
      Number(draft.position) <= roster.length)
  const normalizedLead = draft.lead.trim().replace(',', '.')
  const validLead =
    season < 2024 ||
    !normalizedLead ||
    (/^\d+(?:\.\d{0,3})?$/.test(normalizedLead) && Number(normalizedLead) <= 999999.999)
  const missingDrivers = [draft.tenth, ...selectedDnf].filter(
    (name) => name && !roster.includes(name),
  )
  const valid = validDnf && validPosition && validLead && missingDrivers.length === 0
  const values = toValues(draft, season, positionDriver)
  const dirty = signature(values) !== signature(toValues(savedDraft, season, positionDriver))
  const pending = disabled || save.isPending

  function update(patch: Partial<Draft>) {
    const next = { ...draft, ...patch }
    setDraft(next)
    setStatus(null)
    setError(null)
    onDirtyChange(
      prediction.id,
      signature(toValues(next, season, positionDriver)) !==
        signature(toValues(savedDraft, season, positionDriver)),
    )
  }

  function changeDnf(next: string[]) {
    if (next.includes(NOBODY) && !draft.dnf.includes(NOBODY)) update({ dnf: [NOBODY] })
    else update({ dnf: next.filter((name) => name !== NOBODY) })
  }

  function discard() {
    setMenuAnchor(null)
    setDraft(savedDraft)
    setStatus(null)
    setError(null)
    onDirtyChange(prediction.id, false)
  }

  async function persist() {
    if (!dirty || !valid || pending) return
    setMenuAnchor(null)
    onSavingChange(true)
    setStatus(null)
    setError(null)
    try {
      await save.mutateAsync({
        userId,
        raceId: prediction.raceId,
        predictionId: prediction.id,
        values,
      })
      setSavedDraft(draft)
      onDirtyChange(prediction.id, false)
      onSaved()
      setStatus('saved')
    } catch (cause) {
      setError(cause)
      setStatus('error')
    } finally {
      onSavingChange(false)
    }
  }

  return (
    <TableRow
      sx={{
        display: hidden ? 'none' : undefined,
        bgcolor: dirty ? 'rgba(165, 180, 252, 0.04)' : 'transparent',
        '& .MuiTableCell-root': { py: 1.5, verticalAlign: 'top' },
      }}
    >
      <TableCell>
        <Stack sx={{ minHeight: 40, justifyContent: 'center' }}>
          <Typography
            variant="body2"
            title={prediction.participantId}
            sx={{ overflowWrap: 'anywhere' }}
          >
            {data.participantName}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {data.leagueName}
          </Typography>
        </Stack>
      </TableCell>
      <TableCell>
        <DriverSelect
          label="10th place"
          value={draft.tenth}
          teams={teams}
          disabled={pending}
          onChange={(tenth) => update({ tenth })}
        />
      </TableCell>
      <TableCell>
        <FormControl size="small" fullWidth disabled={pending} error={!validDnf}>
          <InputLabel id={dnfLabel} shrink>
            {season === 2023 ? 'First DNF' : 'DNF'}
          </InputLabel>
          <Select<string[]>
            multiple
            displayEmpty
            notched
            labelId={dnfLabel}
            label={season === 2023 ? 'First DNF' : 'DNF'}
            value={draft.dnf}
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
                sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {dnfText}
              </Box>
            )}
          >
            {season >= 2024 && (
              <MenuItem value={NOBODY}>
                <Checkbox
                  checked={draft.dnf.includes(NOBODY)}
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
                disabled={
                  !roster.includes(name) ||
                  (selectedDnf.length >= dnfCount && !selectedDnf.includes(name))
                }
              >
                <Checkbox
                  checked={selectedDnf.includes(name)}
                  size="small"
                  sx={{ p: 0.5, mr: 1 }}
                />
                <DriverLabel name={name} teams={teams} />
              </MenuItem>
            ))}
          </Select>
          {!validDnf && (
            <FormHelperText>
              Select exactly {dnfCount} different drivers{season >= 2024 ? ' or Nobody' : ''}.
            </FormHelperText>
          )}
        </FormControl>
      </TableCell>
      {season >= 2024 && (
        <TableCell>
          <FormControl size="small" fullWidth disabled={pending}>
            <Select
              value={draft.safety}
              displayEmpty
              inputProps={{ 'aria-label': `Incidents — ${data.participantName}` }}
              onChange={(event) =>
                update({ safety: event.target.value as SafetyCarsCountDto | '' })
              }
            >
              <MenuItem value="">Not predicted</MenuItem>
              {Object.entries(safetyLabels).map(([value, label]) => (
                <MenuItem key={value} value={value}>
                  {label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </TableCell>
      )}
      {season >= 2024 && (
        <TableCell>
          <TextField
            size="small"
            fullWidth
            value={draft.lead}
            disabled={pending}
            error={!validLead}
            helperText={!validLead ? '0–999999.999, max. 3 decimals' : undefined}
            slotProps={{
              htmlInput: {
                inputMode: 'decimal',
                'aria-label': `Winner's gap — ${data.participantName}`,
              },
              input: { endAdornment: <InputAdornment position="end">s</InputAdornment> },
            }}
            onChange={(event) => {
              const lead = event.target.value.replace(',', '.')
              if (/^\d*(\.\d*)?$/.test(lead)) update({ lead })
            }}
          />
        </TableCell>
      )}
      {positionDriver && (
        <TableCell>
          <TextField
            select
            size="small"
            fullWidth
            label="Position"
            value={draft.position}
            disabled={pending}
            error={!validPosition}
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
            {draft.position && Number(draft.position) > roster.length && (
              <MenuItem value={draft.position} disabled>
                {draft.position}
              </MenuItem>
            )}
          </TextField>
        </TableCell>
      )}
      <TableCell align="right">
        <Stack
          direction="row"
          spacing={0.5}
          sx={{ alignItems: 'center', justifyContent: 'flex-end', minHeight: 40 }}
        >
          {save.isPending && <CircularProgress size={18} aria-label="Saving prediction" />}
          {!save.isPending && dirty && (
            <Tooltip title="Unsaved changes">
              <WarningAmberRounded fontSize="small" color="warning" />
            </Tooltip>
          )}
          {!save.isPending && !dirty && status === 'saved' && (
            <Tooltip title="Saved">
              <CheckCircleOutlineRounded fontSize="small" color="success" />
            </Tooltip>
          )}
          {status === 'error' && (
            <Tooltip
              title={adminPredictionErrorMessage(
                error,
                'Could not save. Your changes are still here.',
              )}
            >
              <ErrorOutlineRounded fontSize="small" color="error" />
            </Tooltip>
          )}
          <IconButton
            size="small"
            aria-label={`Actions — ${data.participantName}`}
            aria-haspopup="menu"
            aria-controls={menuAnchor ? menuId : undefined}
            aria-expanded={Boolean(menuAnchor)}
            disabled={pending}
            onClick={(event) => setMenuAnchor(event.currentTarget)}
            sx={{ width: 40, height: 40 }}
          >
            <MoreVertRounded fontSize="small" />
          </IconButton>
          <Menu
            id={menuId}
            anchorEl={menuAnchor}
            open={Boolean(menuAnchor)}
            onClose={() => setMenuAnchor(null)}
          >
            <MenuItem disabled={pending || !dirty || !valid} onClick={() => void persist()}>
              <ListItemIcon>
                <SaveRounded fontSize="small" />
              </ListItemIcon>
              Save
            </MenuItem>
            <MenuItem disabled={pending || !dirty} onClick={discard}>
              <ListItemIcon>
                <UndoRounded fontSize="small" />
              </ListItemIcon>
              Discard changes
            </MenuItem>
          </Menu>
        </Stack>
      </TableCell>
    </TableRow>
  )
}
