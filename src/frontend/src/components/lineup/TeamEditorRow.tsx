import CloseRounded from '@mui/icons-material/CloseRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import UndoRounded from '@mui/icons-material/UndoRounded'
import {
  Box,
  Button,
  Chip,
  IconButton,
  InputAdornment,
  Stack,
  TableCell,
  TableRow,
  TextField,
  Tooltip,
} from '@mui/material'
import { useState } from 'react'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'

const hexColor = /^#[0-9a-f]{6}$/i

function normalizeTeam(team: RaceLineupTeamDto): RaceLineupTeamDto {
  return {
    name: team.name.trim(),
    badge: team.badge.trim(),
    backgroundColor: team.backgroundColor.trim().toUpperCase(),
    textColor: team.textColor.trim().toUpperCase(),
    drivers: team.drivers.map((driver) => driver.trim()).filter(Boolean),
  }
}

function sameTeam(left: RaceLineupTeamDto, right: RaceLineupTeamDto) {
  return (
    left.name === right.name &&
    left.badge === right.badge &&
    left.backgroundColor.toUpperCase() === right.backgroundColor.toUpperCase() &&
    left.textColor.toUpperCase() === right.textColor.toUpperCase() &&
    left.drivers.length === right.drivers.length &&
    left.drivers.every((driver, index) => driver === right.drivers[index])
  )
}

function ColorField({
  label,
  value,
  disabled,
  showErrors,
  onChange,
}: {
  label: string
  value: string
  disabled: boolean
  showErrors: boolean
  onChange: (value: string) => void
}) {
  const valid = hexColor.test(value.trim())
  return (
    <TextField
      size="small"
      value={value}
      disabled={disabled}
      error={showErrors && !valid}
      helperText={showErrors && !valid ? 'Use #RRGGBB.' : undefined}
      onChange={(event) => onChange(event.target.value)}
      sx={{ width: 160 }}
      slotProps={{
        htmlInput: { 'aria-label': label, spellCheck: false },
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <Box
                component="input"
                type="color"
                aria-label={`Choose ${label.toLowerCase()}`}
                value={valid ? value.trim() : '#000000'}
                disabled={disabled}
                onChange={(event) => onChange(event.target.value)}
                sx={{
                  width: 24,
                  height: 28,
                  p: 0,
                  border: 0,
                  bgcolor: 'transparent',
                  cursor: 'pointer',
                }}
              />
            </InputAdornment>
          ),
        },
      }}
    />
  )
}

export default function TeamEditorRow({
  team,
  isNew = false,
  busy,
  saving,
  onSave,
  onCancel,
}: {
  team: RaceLineupTeamDto
  isNew?: boolean
  busy: boolean
  saving: boolean
  onSave: (team: RaceLineupTeamDto, onSaved: () => void) => void
  onCancel?: () => void
}) {
  const [draft, setDraft] = useState<RaceLineupTeamDto | null>(null)
  const [showErrors, setShowErrors] = useState(false)
  const value = draft ?? team
  const normalized = normalizeTeam(value)
  const dirty = isNew || !sameTeam(normalized, team)
  const drivers = Array.from(
    { length: Math.max(2, value.drivers.length) },
    (_, index) => value.drivers[index] ?? '',
  )
  const driverErrors = drivers.map((driver, index) => {
    const name = driver.trim()
    if (!name && (isNew || index < team.drivers.length)) return 'Driver name is required.'
    if (name && drivers.filter((other) => other.trim() === name).length > 1)
      return 'Driver must occur only once.'
    return ''
  })
  const valid =
    normalized.name.length > 0 &&
    normalized.badge.length > 0 &&
    hexColor.test(normalized.backgroundColor) &&
    hexColor.test(normalized.textColor) &&
    driverErrors.every((error) => !error)

  function change(patch: Partial<RaceLineupTeamDto>) {
    const next = { ...value, ...patch }
    setDraft(sameTeam(next, team) ? null : next)
  }

  function reset() {
    setDraft(null)
    setShowErrors(false)
  }

  function save() {
    setShowErrors(true)
    if (valid) onSave(normalized, reset)
  }

  return (
    <TableRow hover sx={{ bgcolor: isNew ? 'action.hover' : undefined }}>
      <TableCell sx={{ minWidth: 190 }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Team name"
          value={value.name}
          disabled={saving}
          error={showErrors && !normalized.name}
          helperText={showErrors && !normalized.name ? 'Team name is required.' : undefined}
          onChange={(event) => change({ name: event.target.value })}
          slotProps={{ htmlInput: { 'aria-label': 'Team name' } }}
        />
      </TableCell>
      <TableCell sx={{ minWidth: 190 }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Badge"
          value={value.badge}
          disabled={saving}
          error={showErrors && !normalized.badge}
          helperText={showErrors && !normalized.badge ? 'Badge is required.' : undefined}
          onChange={(event) => change({ badge: event.target.value })}
          slotProps={{
            htmlInput: { 'aria-label': 'Team badge' },
            input: {
              endAdornment: (
                <InputAdornment position="end">
                  <Chip
                    size="small"
                    aria-label="Badge preview"
                    label={value.badge || '…'}
                    sx={{
                      maxWidth: 90,
                      bgcolor: hexColor.test(normalized.backgroundColor)
                        ? normalized.backgroundColor
                        : '#64748B',
                      color: hexColor.test(normalized.textColor) ? normalized.textColor : '#FFFFFF',
                      fontWeight: 650,
                    }}
                  />
                </InputAdornment>
              ),
            },
          }}
        />
      </TableCell>
      <TableCell>
        <ColorField
          label="Background color"
          value={value.backgroundColor}
          disabled={saving}
          showErrors={showErrors}
          onChange={(backgroundColor) => change({ backgroundColor })}
        />
      </TableCell>
      <TableCell>
        <ColorField
          label="Text color"
          value={value.textColor}
          disabled={saving}
          showErrors={showErrors}
          onChange={(textColor) => change({ textColor })}
        />
      </TableCell>
      <TableCell sx={{ minWidth: 380 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1.5 }}>
          {drivers.map((driver, index) => (
            <TextField
              key={index}
              size="small"
              label={`Driver ${index + 1}`}
              value={driver}
              disabled={saving}
              error={showErrors && !!driverErrors[index]}
              helperText={showErrors ? driverErrors[index] || undefined : undefined}
              onChange={(event) => {
                const updated = [...value.drivers]
                updated[index] = event.target.value
                change({ drivers: updated })
              }}
            />
          ))}
        </Box>
      </TableCell>
      <TableCell align="right">
        <Stack
          direction="row"
          spacing={0.5}
          sx={{ alignItems: 'center', justifyContent: 'flex-end' }}
        >
          <Button
            aria-label={isNew ? 'Add new team' : `Save team ${team.name}`}
            size="small"
            variant="outlined"
            startIcon={<SaveRounded />}
            loading={saving}
            disabled={busy || !dirty}
            onClick={save}
          >
            {isNew ? 'Add' : 'Save'}
          </Button>
          {(isNew || dirty) && (
            <Tooltip title={isNew ? 'Cancel new team' : 'Discard changes'}>
              <span>
                <IconButton
                  size="small"
                  aria-label={isNew ? 'Cancel new team' : `Discard changes for ${team.name}`}
                  disabled={saving}
                  onClick={isNew ? onCancel : reset}
                >
                  {isNew ? <CloseRounded fontSize="small" /> : <UndoRounded fontSize="small" />}
                </IconButton>
              </span>
            </Tooltip>
          )}
        </Stack>
      </TableCell>
    </TableRow>
  )
}
