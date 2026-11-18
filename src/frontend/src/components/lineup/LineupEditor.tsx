import AddRounded from '@mui/icons-material/AddRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import SwapHorizRounded from '@mui/icons-material/SwapHorizRounded'
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
  hexColor,
  normalizeTeam,
  sameTeam,
  sameTeams,
  selectedReplacements,
  type TeamDraft,
} from './lineupDraft'
import SwapDriversDialog from './SwapDriversDialog'
import TeamEditorRow from './TeamEditorRow'

const emptyTeam: RaceLineupTeamDto = {
  name: '',
  badge: '',
  backgroundColor: '#64748B',
  textColor: '#FFFFFF',
  drivers: ['', ''],
}

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

export default function LineupEditor({
  lineup,
  busy,
  saving,
  onSave,
}: {
  lineup: RaceLineupDto
  busy: boolean
  saving: boolean
  onSave: (request: SaveRaceLineupRequestDto, onSaved: () => void) => void
}) {
  const [draft, setDraft] = useState<Draft | null>(null)
  const [showErrors, setShowErrors] = useState(false)
  const [swapOpen, setSwapOpen] = useState(false)
  const [pairs, setPairs] = useState<ReadonlyMap<string, string>>(new Map())
  const nextNewTeamId = useRef(0)
  const initial: Draft = {
    baseTeams: lineup.teams,
    rows: lineup.teams.map((team) => ({ id: `team:${team.name}`, previousName: team.name, team })),
  }
  const { baseTeams, rows } = draft ?? initial
  const { teams, removed, added, needsPairing } = analyzeDraft(baseTeams, rows)
  const normalized = teams.map(({ team }) => team)
  const dirty =
    !sameTeams(normalized, baseTeams) ||
    teams.some(({ previousName }, index) => previousName !== baseTeams[index]?.name)
  const stale = draft !== null && !sameTeams(baseTeams, lineup.teams)
  const duplicateNames = duplicates(normalized.map((team) => team.name))
  const drivers = normalized.flatMap((team) => team.drivers)
  const duplicateDrivers = duplicates(drivers)
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
        team.drivers.length > 0 &&
        team.drivers.length <= 2,
    )
  const replacements = needsPairing ? selectedReplacements(removed, added, pairs) : []
  const canPair = added.length >= removed.length
  const pairingComplete =
    !needsPairing ||
    (replacements.length === removed.length &&
      duplicates(replacements.map((pair) => pair.newDriverName)).size === 0)

  function editRows(update: (rows: TeamDraft[]) => TeamDraft[]) {
    setDraft((current) => {
      const base = current ?? initial
      const next = update(base.rows)
      return sameTeams(
        next.map(({ team }) => team),
        base.baseTeams,
      ) && next.every(({ previousName }, index) => previousName === base.baseTeams[index].name)
        ? null
        : { ...base, rows: next }
    })
  }

  function addDraft() {
    const id = `new:${nextNewTeamId.current++}`
    editRows((current) => [...current, { id, previousName: null, team: emptyTeam }])
  }

  function discardDraft() {
    setDraft(null)
    setShowErrors(false)
    setPairs(new Map())
  }

  function swapDrivers(first: string, second: string) {
    editRows((current) =>
      current.map((row) => ({
        ...row,
        team: {
          ...row.team,
          drivers: row.team.drivers.map((name) => {
            if (name === first || name.trim() === first) return second
            if (name === second || name.trim() === second) return first
            return name
          }),
        },
      })),
    )
    setSwapOpen(false)
  }

  function save() {
    setShowErrors(true)
    if (teamFieldsValid && pairingComplete) onSave({ baseTeams, teams, replacements }, discardDraft)
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
            <Button startIcon={<UndoRounded />} disabled={saving} onClick={discardDraft}>
              Discard changes
            </Button>
          )}
          <Button
            variant="outlined"
            startIcon={<SwapHorizRounded />}
            disabled={saving || stale || drivers.length < 2 || duplicateDrivers.size > 0}
            onClick={() => setSwapOpen(true)}
          >
            Swap drivers
          </Button>
          <Button
            variant="outlined"
            startIcon={<AddRounded />}
            disabled={saving || stale}
            onClick={addDraft}
          >
            Add team
          </Button>
          <Button
            variant="contained"
            startIcon={<SaveRounded />}
            loading={saving}
            disabled={busy || stale || rows.length === 0 || (!dirty && lineup.prepared)}
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
            <Button color="inherit" onClick={discardDraft}>
              Discard draft
            </Button>
          }
        >
          The lineup changed while you were editing it. Your draft is still here. Discard it to load
          the latest lineup.
        </Alert>
      )}
      {showErrors &&
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
      <TableContainer component={Paper} variant="outlined" tabIndex={0}>
        <Table
          size="small"
          aria-label="Race team lineup editor"
          sx={{
            minWidth: 1220,
            '& th, & td': { px: 1.5, py: 2 },
            '& thead th': { fontWeight: 650, whiteSpace: 'nowrap' },
            '& tbody td': { verticalAlign: 'top' },
            '& tbody tr:last-child > *': { borderBottom: 0 },
          }}
        >
          <TableHead sx={{ bgcolor: 'action.hover' }}>
            <TableRow>
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
          <TableBody>
            {rows.map((row, index) => {
              const original = baseTeams.find((team) => team.name === row.previousName)
              const changed =
                !original || !sameTeam(normalizeTeam(row.team), normalizeTeam(original))
              return (
                <TeamEditorRow
                  key={row.id}
                  team={row.team}
                  isNew={!original}
                  disabled={saving || stale}
                  showErrors={showErrors}
                  duplicateName={duplicateNames.has(normalized[index].name)}
                  duplicateDrivers={duplicateDrivers}
                  onChange={(team) =>
                    editRows((current) =>
                      current.map((item) => (item.id === row.id ? { ...item, team } : item)),
                    )
                  }
                  onDiscard={
                    changed
                      ? () =>
                          editRows((current) =>
                            original
                              ? current.map((item) =>
                                  item.id === row.id ? { ...item, team: original } : item,
                                )
                              : current.filter((item) => item.id !== row.id),
                          )
                      : undefined
                  }
                />
              )
            })}
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} sx={{ textAlign: 'center' }}>
                  <Typography color="text.secondary">
                    No teams yet. Add the first team to prepare this lineup.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {swapOpen && (
        <SwapDriversDialog
          lineup={{ ...lineup, teams: normalized }}
          onSwap={swapDrivers}
          onClose={() => setSwapOpen(false)}
        />
      )}
    </Stack>
  )
}
