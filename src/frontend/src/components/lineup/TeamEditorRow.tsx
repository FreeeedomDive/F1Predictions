import CloseRounded from '@mui/icons-material/CloseRounded'
import UndoRounded from '@mui/icons-material/UndoRounded'
import {
  Box,
  Chip,
  IconButton,
  InputAdornment,
  TableCell,
  TableRow,
  TextField,
  Tooltip,
} from '@mui/material'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import { hexColor, normalizeTeam } from './lineupDraft'

type TeamFields = Omit<RaceLineupTeamDto, 'drivers'>

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
  team: value,
  isNew,
  disabled,
  showErrors,
  duplicateName,
  duplicateDrivers,
  onChange,
  onDiscard,
}: {
  team: RaceLineupTeamDto
  isNew: boolean
  disabled: boolean
  showErrors: boolean
  duplicateName: boolean
  duplicateDrivers: ReadonlySet<string>
  onChange: (team: RaceLineupTeamDto) => void
  onDiscard?: () => void
}) {
  const normalized = normalizeTeam(value)
  const nameError = !normalized.name
    ? 'Team name is required.'
    : duplicateName
      ? 'Team must occur only once.'
      : ''
  const badgeError = !normalized.badge
    ? 'Badge is required.'
    : normalized.badge.length > 3
      ? 'Use up to 3 characters.'
      : ''
  const drivers = Array.from(
    { length: Math.max(2, value.drivers.length) },
    (_, index) => value.drivers[index] ?? '',
  )

  function change(field: keyof TeamFields, nextValue: string) {
    onChange({ ...value, [field]: nextValue })
  }

  function changeDriver(index: number, nextValue: string) {
    onChange({ ...value, drivers: drivers.map((driver, i) => (i === index ? nextValue : driver)) })
  }

  return (
    <TableRow hover sx={{ bgcolor: isNew ? 'action.hover' : undefined }}>
      <TableCell sx={{ minWidth: 190 }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Team name"
          value={value.name}
          disabled={disabled}
          error={showErrors && !!nameError}
          helperText={showErrors ? nameError || undefined : undefined}
          onChange={(event) => change('name', event.target.value)}
          slotProps={{ htmlInput: { 'aria-label': 'Team name' } }}
        />
      </TableCell>
      <TableCell sx={{ minWidth: 190 }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Badge"
          value={value.badge}
          disabled={disabled}
          error={!!badgeError && (showErrors || normalized.badge.length > 3)}
          helperText={
            showErrors || normalized.badge.length > 3 ? badgeError || undefined : undefined
          }
          onChange={(event) => change('badge', event.target.value)}
          slotProps={{
            htmlInput: { 'aria-label': 'Team badge', maxLength: 3 },
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
          disabled={disabled}
          showErrors={showErrors}
          onChange={(backgroundColor) => change('backgroundColor', backgroundColor)}
        />
      </TableCell>
      <TableCell>
        <ColorField
          label="Text color"
          value={value.textColor}
          disabled={disabled}
          showErrors={showErrors}
          onChange={(textColor) => change('textColor', textColor)}
        />
      </TableCell>
      <TableCell sx={{ minWidth: 380 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1.5 }}>
          {drivers.map((driver, index) => {
            const error =
              duplicateDrivers.has(driver) || duplicateDrivers.has(driver.trim())
                ? 'Driver must occur only once.'
                : index === 0 && normalized.drivers.length === 0
                  ? 'At least one driver is required.'
                  : ''
            return (
              <TextField
                key={index}
                size="small"
                label={`Driver ${index + 1}`}
                value={driver}
                disabled={disabled}
                error={showErrors && !!error}
                helperText={showErrors ? error || undefined : undefined}
                onChange={(event) => changeDriver(index, event.target.value)}
              />
            )
          })}
        </Box>
      </TableCell>
      <TableCell align="right" sx={{ width: 64 }}>
        {onDiscard && (
          <Tooltip title={isNew ? 'Remove new team' : 'Discard team changes'}>
            <IconButton
              aria-label={`${isNew ? 'Remove' : 'Discard changes for'} ${normalized.name || 'new team'}`}
              disabled={disabled}
              onClick={onDiscard}
            >
              {isNew ? <CloseRounded fontSize="small" /> : <UndoRounded fontSize="small" />}
            </IconButton>
          </Tooltip>
        )}
      </TableCell>
    </TableRow>
  )
}
