import AddRounded from '@mui/icons-material/AddRounded'
import SwapHorizRounded from '@mui/icons-material/SwapHorizRounded'
import {
  Button,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useRef, useState } from 'react'
import type { RaceLineupDto, RaceLineupTeamDto } from '../../dto/raceLineup'
import TeamEditorRow from './TeamEditorRow'

const emptyTeam: RaceLineupTeamDto = {
  name: '',
  badge: '',
  backgroundColor: '#64748B',
  textColor: '#FFFFFF',
  drivers: ['', ''],
}

export default function LineupEditor({
  lineup,
  busy,
  saving,
  savingTeam,
  onSave,
  onSwapDrivers,
}: {
  lineup: RaceLineupDto
  busy: boolean
  saving: boolean
  savingTeam: string | undefined
  onSave: (team: RaceLineupTeamDto, previousName: string | undefined, onSaved: () => void) => void
  onSwapDrivers: () => void
}) {
  const [newTeamIds, setNewTeamIds] = useState<number[]>([])
  const [savingNewTeamId, setSavingNewTeamId] = useState<number | null>(null)
  const nextNewTeamId = useRef(0)

  function addDraft() {
    const id = nextNewTeamId.current++
    setNewTeamIds((ids) => [...ids, id])
  }

  function removeDraft(id: number) {
    setNewTeamIds((ids) => ids.filter((other) => other !== id))
  }

  return (
    <Stack spacing={2}>
      <Stack
        direction="row"
        sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}
      >
        <Typography variant="body2" color="text.secondary">
          {lineup.teams.length} {lineup.teams.length === 1 ? 'team' : 'teams'} · Save each row
          separately
        </Typography>
        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<SwapHorizRounded />}
            disabled={busy || lineup.teams.flatMap((team) => team.drivers).length < 2}
            onClick={onSwapDrivers}
          >
            Swap drivers
          </Button>
          <Button variant="outlined" startIcon={<AddRounded />} onClick={addDraft}>
            Add team
          </Button>
        </Stack>
      </Stack>
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
            {lineup.teams.map((team) => (
              <TeamEditorRow
                key={`team:${team.name}`}
                team={team}
                busy={busy}
                saving={saving && savingTeam === team.name}
                onSave={(updated, onSaved) => onSave(updated, team.name, onSaved)}
              />
            ))}
            {newTeamIds.map((id) => (
              <TeamEditorRow
                key={`new-team:${id}`}
                team={emptyTeam}
                isNew
                busy={busy}
                saving={saving && savingTeam === undefined && savingNewTeamId === id}
                onSave={(team, onSaved) => {
                  setSavingNewTeamId(id)
                  onSave(team, undefined, () => {
                    onSaved()
                    removeDraft(id)
                    setSavingNewTeamId(null)
                  })
                }}
                onCancel={() => removeDraft(id)}
              />
            ))}
            {lineup.teams.length === 0 && newTeamIds.length === 0 && (
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
    </Stack>
  )
}
