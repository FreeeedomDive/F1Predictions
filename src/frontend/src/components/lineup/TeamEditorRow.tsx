import type {
  DraggableProvided,
  DraggableProvidedDraggableProps,
  DraggableProvidedDragHandleProps,
} from '@hello-pangea/dnd'
import CloseRounded from '@mui/icons-material/CloseRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import DragIndicatorRounded from '@mui/icons-material/DragIndicatorRounded'
import MoreVertRounded from '@mui/icons-material/MoreVertRounded'
import UndoRounded from '@mui/icons-material/UndoRounded'
import {
  Box,
  Chip,
  IconButton,
  InputAdornment,
  ListItemIcon,
  Menu,
  MenuItem,
  TableCell,
  TableRow,
  TextField,
  Tooltip,
} from '@mui/material'
import { useId, useState } from 'react'
import { hexColor, normalizeTeam, teamValue, type TeamDraft } from './lineupDraft'
import TeamDriversEditor from './TeamDriversEditor'

type TeamFields = TeamDraft['team']

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
  rowRef,
  draggableProps,
  dragHandleProps,
  dragging,
  dragCellWidths,
  row,
  isNew,
  driversEditable,
  dragSourceTeam,
  disabled,
  showErrors,
  duplicateName,
  duplicateDrivers,
  onChange,
  onDiscard,
  onRemove,
}: {
  rowRef: DraggableProvided['innerRef']
  draggableProps: DraggableProvidedDraggableProps
  dragHandleProps: DraggableProvidedDragHandleProps | null
  dragging: boolean
  dragCellWidths?: number[]
  row: TeamDraft
  isNew: boolean
  driversEditable: boolean
  dragSourceTeam?: string
  disabled: boolean
  showErrors: boolean
  duplicateName: boolean
  duplicateDrivers: ReadonlySet<string>
  onChange: (update: (row: TeamDraft) => TeamDraft) => void
  onDiscard?: () => void
  onRemove?: () => void
}) {
  const [actionsAnchor, setActionsAnchor] = useState<HTMLElement | null>(null)
  const actionsId = useId()
  const value = row.team
  const normalized = normalizeTeam(teamValue(row))
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
  function change(field: keyof TeamFields, nextValue: string) {
    onChange((current) => ({ ...current, team: { ...current.team, [field]: nextValue } }))
  }

  function changeDriver(driverId: string, name: string) {
    onChange((current) => ({
      ...current,
      drivers: current.drivers.map((driver) =>
        driver.id === driverId ? { ...driver, name } : driver,
      ),
    }))
  }

  return (
    <TableRow
      ref={rowRef}
      {...draggableProps}
      hover
      style={
        dragging
          ? { ...draggableProps.style, display: 'table', tableLayout: 'fixed' }
          : draggableProps.style
      }
      sx={{
        bgcolor: dragging ? 'background.paper' : isNew ? 'action.hover' : undefined,
        ...Object.fromEntries(
          (dragCellWidths ?? []).map((width, index) => [
            `& > td:nth-of-type(${index + 1})`,
            { width, boxSizing: 'border-box' },
          ]),
        ),
      }}
    >
      <TableCell align="center" sx={{ width: 48, minWidth: 48, boxSizing: 'border-box' }}>
        <IconButton
          {...dragHandleProps}
          size="small"
          disabled={!dragHandleProps}
          aria-label={`Move ${normalized.name || 'new team'}`}
          sx={{ color: 'text.secondary', cursor: dragging ? 'grabbing' : 'grab' }}
        >
          <DragIndicatorRounded fontSize="small" />
        </IconButton>
      </TableCell>
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
        <TeamDriversEditor
          row={row}
          editable={driversEditable}
          disabled={disabled}
          dragSourceTeam={dragSourceTeam}
          duplicateDrivers={duplicateDrivers}
          showErrors={showErrors}
          onChange={changeDriver}
        />
      </TableCell>
      <TableCell align="right" sx={{ width: 64 }}>
        {onRemove ? (
          <>
            <IconButton
              aria-label={`Actions for ${normalized.name || row.previousName}`}
              aria-haspopup="menu"
              aria-controls={actionsAnchor ? actionsId : undefined}
              aria-expanded={!!actionsAnchor}
              disabled={disabled}
              onClick={(event) => setActionsAnchor(event.currentTarget)}
            >
              <MoreVertRounded fontSize="small" />
            </IconButton>
            <Menu
              anchorEl={actionsAnchor}
              open={!!actionsAnchor && !disabled}
              onClose={() => setActionsAnchor(null)}
              slotProps={{ list: { id: actionsId, 'aria-label': 'Team actions' } }}
            >
              {onDiscard && (
                <MenuItem
                  onClick={() => {
                    setActionsAnchor(null)
                    onDiscard()
                  }}
                >
                  <ListItemIcon>
                    <UndoRounded fontSize="small" />
                  </ListItemIcon>
                  Discard team changes
                </MenuItem>
              )}
              <MenuItem
                onClick={() => {
                  setActionsAnchor(null)
                  onRemove()
                }}
              >
                <ListItemIcon>
                  <DeleteOutlineRounded fontSize="small" />
                </ListItemIcon>
                Remove team
              </MenuItem>
            </Menu>
          </>
        ) : (
          onDiscard && (
            <Tooltip title={isNew ? 'Remove new team' : 'Discard team changes'}>
              <IconButton
                aria-label={`${isNew ? 'Remove' : 'Discard changes for'} ${normalized.name || 'new team'}`}
                disabled={disabled}
                onClick={onDiscard}
              >
                {isNew ? <CloseRounded fontSize="small" /> : <UndoRounded fontSize="small" />}
              </IconButton>
            </Tooltip>
          )
        )}
      </TableCell>
    </TableRow>
  )
}
