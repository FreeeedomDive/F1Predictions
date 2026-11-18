import { DragDropContext, Draggable, Droppable } from '@hello-pangea/dnd'
import type { DragStart, DropResult } from '@hello-pangea/dnd'
import AddRounded from '@mui/icons-material/AddRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import UndoRounded from '@mui/icons-material/UndoRounded'
import {
  Alert,
  Button,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useRef, useState } from 'react'
import type {
  RaceLineupDto,
  RaceLineupTeamDto,
  SaveRaceLineupRequestDto,
} from '../../dto/raceLineup'
import {
  analyzeDraft,
  createTeamDraft,
  hexColor,
  maxSavedDrivers,
  moveDriver,
  normalizeTeam,
  sameDrivers,
  sameTeam,
  sameTeams,
  selectedReplacements,
  teamValue,
  type TeamDraft,
} from './lineupDraft'
import TeamEditorRow from './TeamEditorRow'

const emptyTeam: RaceLineupTeamDto = {
  name: '',
  badge: '',
  backgroundColor: '#64748B',
  textColor: '#FFFFFF',
  drivers: ['', ''],
}

const teamDroppableId = 'lineup-teams'

function duplicates(values: string[]) {
  const seen = new Set<string>()
  const repeated = new Set<string>()
  for (const value of values) {
    if (!value) continue
    if (seen.has(value)) repeated.add(value)
    seen.add(value)
  }
  return repeated
}

interface Draft {
  baseTeams: RaceLineupTeamDto[]
  rows: TeamDraft[]
}

interface ActiveDrag {
  draft: Draft
  cellWidths?: number[]
  driverSourceTeam?: string
}

export default function LineupEditor({
  lineup,
  driversEditable,
  busy,
  saving,
  onSave,
}: {
  lineup: RaceLineupDto
  driversEditable: boolean
  busy: boolean
  saving: boolean
  onSave: (request: SaveRaceLineupRequestDto, onSaved: () => void) => void
}) {
  const [draft, setDraft] = useState<Draft | null>(null)
  const [activeDrag, setActiveDrag] = useState<ActiveDrag | null>(null)
  const [showErrors, setShowErrors] = useState(false)
  const [pairs, setPairs] = useState<ReadonlyMap<string, string>>(new Map())
  const nextNewTeamId = useRef(0)
  const nextDriverId = useRef(0)
  const table = useRef<HTMLTableElement>(null)
  const initial: Draft = {
    baseTeams: lineup.teams,
    rows: lineup.teams.map((team) => createTeamDraft(`team:${team.name}`, team.name, team)),
  }
  const dragging = activeDrag !== null
  const { baseTeams, rows } = draft ?? activeDrag?.draft ?? initial
  const { teams, removed, added, needsPairing } = analyzeDraft(baseTeams, rows)
  const normalized = teams.map(({ team }) => team)
  const dirty =
    !sameTeams(normalized, baseTeams) ||
    teams.some(({ previousName }, index) => previousName !== baseTeams[index]?.name)
  const stale = (draft !== null || dragging) && !sameTeams(baseTeams, lineup.teams)
  const lockedChanges =
    !driversEditable &&
    (teams.length !== baseTeams.length ||
      teams.some(({ previousName, team }) => {
        const original = baseTeams.find((item) => item.name === previousName)
        return !original || !sameDrivers(team.drivers, original.drivers)
      }))
  const duplicateNames = duplicates(normalized.map((team) => team.name))
  const drivers = normalized.flatMap((team) => team.drivers)
  const duplicateDrivers = duplicates(drivers)
  const overCapacity =
    driversEditable && normalized.some((team) => team.drivers.length > maxSavedDrivers)
  const teamFieldsValid =
    duplicateNames.size === 0 &&
    duplicateDrivers.size === 0 &&
    normalized.every(
      (team) =>
        team.name &&
        team.badge &&
        team.badge.length <= 3 &&
        hexColor.test(team.backgroundColor) &&
        hexColor.test(team.textColor) &&
        team.drivers.length > 0,
    )
  const replacements = needsPairing ? selectedReplacements(removed, added, pairs) : []
  const canPair = added.length >= removed.length
  const pairingComplete =
    !needsPairing ||
    (replacements.length === removed.length &&
      duplicates(replacements.map((pair) => pair.newDriverName)).size === 0)

  function editRows(update: (rows: TeamDraft[]) => TeamDraft[]) {
    setDraft((current) => {
      const base = current ?? activeDrag?.draft ?? initial
      const next = update(base.rows)
      return sameTeams(next.map(teamValue), base.baseTeams) &&
        next.every(({ previousName }, index) => previousName === base.baseTeams[index].name)
        ? null
        : { ...base, rows: next }
    })
  }

  function startDrag({ source, type }: DragStart) {
    if (type === 'DRIVER') {
      setActiveDrag({ draft: draft ?? initial, driverSourceTeam: source.droppableId })
      return
    }
    const row = table.current?.tBodies.item(0)?.rows.item(source.index)
    setActiveDrag({
      draft: draft ?? initial,
      cellWidths: Array.from(row?.cells ?? [], (cell) => cell.getBoundingClientRect().width),
    })
  }

  function finishDrag({ destination, source, draggableId, type }: DropResult) {
    if (destination) {
      if (type === 'DRIVER' && driversEditable) {
        const emptySlot = { id: `empty:${nextDriverId.current++}`, name: '' }
        editRows((current) => moveDriver(current, source, destination, draggableId, emptySlot))
      } else if (type === 'TEAM' && destination.index !== source.index) {
        editRows((current) => {
          if (current[source.index]?.id !== draggableId) return current
          const next = [...current]
          const [moved] = next.splice(source.index, 1)
          next.splice(destination.index, 0, moved)
          return next
        })
      }
    }
    setActiveDrag(null)
  }

  function addDraft() {
    const id = `new:${nextNewTeamId.current++}`
    editRows((current) => [...current, createTeamDraft(id, null, emptyTeam)])
  }

  function discardRow(row: TeamDraft, original: RaceLineupTeamDto | undefined) {
    if (!original) {
      editRows((current) => current.filter((item) => item.id !== row.id))
      return
    }
    const restored = createTeamDraft(
      `restored:${nextDriverId.current++}`,
      row.previousName,
      original,
    )
    editRows((current) =>
      current.map((item) => (item.id === row.id ? { ...restored, id: row.id } : item)),
    )
  }

  function discardDraft() {
    setDraft(null)
    setShowErrors(false)
    setPairs(new Map())
  }

  function save() {
    setShowErrors(true)
    if (!lockedChanges && !overCapacity && teamFieldsValid && pairingComplete) {
      onSave({ baseTeams, teams, replacements }, discardDraft)
    }
  }

  return (
    <Stack spacing={2}>
      <Stack
        direction="row"
        sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}
      >
        <Typography variant="body2" color="text.secondary">
          {rows.length} {rows.length === 1 ? 'team' : 'teams'}
          {dirty && ' · Unsaved changes'}
        </Typography>
        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
          {dirty && !stale && (
            <Button
              startIcon={<UndoRounded />}
              disabled={saving || dragging}
              onClick={discardDraft}
            >
              Discard changes
            </Button>
          )}
          {driversEditable && (
            <Button
              variant="outlined"
              startIcon={<AddRounded />}
              disabled={saving || stale || dragging}
              onClick={addDraft}
            >
              Add team
            </Button>
          )}
          <Button
            variant="contained"
            startIcon={<SaveRounded />}
            loading={saving}
            disabled={
              busy ||
              stale ||
              dragging ||
              lockedChanges ||
              overCapacity ||
              rows.length === 0 ||
              (!dirty && lineup.prepared)
            }
            onClick={save}
          >
            Save lineup
          </Button>
        </Stack>
      </Stack>
      {stale && !saving && (
        <Alert
          severity="warning"
          action={
            <Button color="inherit" disabled={dragging} onClick={discardDraft}>
              Discard draft
            </Button>
          }
        >
          The lineup changed while you were editing it. Your draft is still here. Discard it to load
          the latest lineup.
        </Alert>
      )}
      {!stale && lockedChanges && (
        <Alert severity="warning">
          This race no longer allows changes to drivers or the team list. Discard those edits before
          saving.
        </Alert>
      )}
      {!stale && overCapacity && (
        <Alert severity="warning">Move a driver out of each three-driver team before saving.</Alert>
      )}
      {driversEditable &&
        showErrors &&
        duplicateDrivers.size === 0 &&
        needsPairing &&
        (canPair ? (
          <Stack spacing={2}>
            <Alert severity="info">
              Confirm who replaced each removed driver. Saved picks and the starting grid will
              follow these replacements.
            </Alert>
            <Stack direction="row" useFlexGap sx={{ flexWrap: 'wrap', gap: 2 }}>
              {removed.map((name) => {
                const selected =
                  replacements.find((pair) => pair.previousDriverName === name)?.newDriverName ?? ''
                return (
                  <TextField
                    key={name}
                    select
                    size="small"
                    sx={{ minWidth: 210 }}
                    label={`Replacement for ${name}`}
                    value={selected}
                    error={showErrors && !selected}
                    disabled={saving || stale}
                    onChange={(event) => {
                      const value = event.target.value
                      setPairs((current) => new Map(current).set(name, value))
                    }}
                  >
                    <MenuItem value="">Select a driver</MenuItem>
                    {added.map((candidate) => (
                      <MenuItem
                        key={candidate}
                        value={candidate}
                        disabled={replacements.some(
                          (pair) =>
                            pair.newDriverName === candidate && pair.previousDriverName !== name,
                        )}
                      >
                        {candidate}
                      </MenuItem>
                    ))}
                  </TextField>
                )
              })}
            </Stack>
          </Stack>
        ) : (
          <Alert severity="warning">Each removed driver needs a replacement.</Alert>
        ))}
      <DragDropContext onBeforeDragStart={startDrag} onDragEnd={finishDrag}>
        <TableContainer component={Paper} variant="outlined" tabIndex={0}>
          <Table
            ref={table}
            size="small"
            aria-label="Race team lineup editor"
            sx={{
              minWidth: 1220,
              '& th, & td': { px: 1.5, py: 2 },
              '& thead th': { fontWeight: 650, whiteSpace: 'nowrap' },
              '& tbody td:first-of-type': { px: 0.5, verticalAlign: 'middle' },
              '& tbody td:not(:first-of-type)': { verticalAlign: 'top' },
              '& tbody tr:last-child > *': { borderBottom: 0 },
            }}
          >
            <TableHead sx={{ bgcolor: 'action.hover' }}>
              <TableRow>
                <TableCell scope="col" aria-label="Reorder teams" />
                <TableCell scope="col">Team</TableCell>
                <TableCell scope="col">Badge</TableCell>
                <TableCell scope="col">Background</TableCell>
                <TableCell scope="col">Text</TableCell>
                <TableCell scope="col">Drivers</TableCell>
                <TableCell scope="col" align="right">
                  Actions
                </TableCell>
              </TableRow>
            </TableHead>
            <Droppable droppableId={teamDroppableId} type="TEAM">
              {(list) => (
                <TableBody ref={list.innerRef} {...list.droppableProps}>
                  {rows.map((row, index) => {
                    const original = baseTeams.find((team) => team.name === row.previousName)
                    const changed =
                      !original || !sameTeam(normalizeTeam(teamValue(row)), normalizeTeam(original))
                    return (
                      <Draggable
                        key={row.id}
                        draggableId={row.id}
                        index={index}
                        isDragDisabled={saving || (!dragging && stale) || rows.length < 2}
                        disableInteractiveElementBlocking
                      >
                        {({ innerRef, draggableProps, dragHandleProps }, snapshot) => (
                          <TeamEditorRow
                            rowRef={innerRef}
                            draggableProps={draggableProps}
                            dragHandleProps={dragHandleProps}
                            dragging={snapshot.isDragging}
                            dragCellWidths={
                              snapshot.isDragging ? activeDrag?.cellWidths : undefined
                            }
                            row={row}
                            isNew={!original}
                            driversEditable={driversEditable || !!activeDrag?.driverSourceTeam}
                            dragSourceTeam={activeDrag?.driverSourceTeam}
                            disabled={saving || (!dragging && stale)}
                            showErrors={showErrors}
                            duplicateName={duplicateNames.has(normalized[index].name)}
                            duplicateDrivers={duplicateDrivers}
                            onChange={(update) =>
                              editRows((current) =>
                                current.map((item) => (item.id === row.id ? update(item) : item)),
                              )
                            }
                            onDiscard={changed ? () => discardRow(row, original) : undefined}
                          />
                        )}
                      </Draggable>
                    )
                  })}
                  {rows.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} sx={{ textAlign: 'center' }}>
                        <Typography color="text.secondary">
                          No teams yet. Add the first team to prepare this lineup.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                  {list.placeholder}
                </TableBody>
              )}
            </Droppable>
          </Table>
        </TableContainer>
      </DragDropContext>
    </Stack>
  )
}
