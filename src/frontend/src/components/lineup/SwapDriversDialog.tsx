import SwapHorizRounded from '@mui/icons-material/SwapHorizRounded'
import {
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
} from '@mui/material'
import { useId, useState } from 'react'
import type { RaceLineupDto, RaceLineupTeamDto } from '../../dto/raceLineup'

function DriverLabel({ name, team }: { name: string; team: RaceLineupTeamDto }) {
  return (
    <Stack component="span" direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Chip
        component="span"
        size="small"
        label={team.badge}
        title={team.name}
        sx={{
          minWidth: 48,
          maxWidth: 100,
          bgcolor: team.backgroundColor,
          color: team.textColor,
          fontWeight: 650,
        }}
      />
      <span>{name}</span>
    </Stack>
  )
}

export default function SwapDriversDialog({
  lineup,
  busy,
  onSwap,
  onClose,
}: {
  lineup: RaceLineupDto
  busy: boolean
  onSwap: (firstDriverName: string, secondDriverName: string) => void
  onClose: () => void
}) {
  const titleId = useId()
  const firstLabelId = useId()
  const secondLabelId = useId()
  const [firstDriver, setFirstDriver] = useState('')
  const [secondDriver, setSecondDriver] = useState('')
  const drivers = lineup.teams.flatMap((team) => team.drivers.map((name) => ({ name, team })))
  const first = drivers.some((driver) => driver.name === firstDriver) ? firstDriver : ''
  const second = drivers.some((driver) => driver.name === secondDriver) ? secondDriver : ''

  return (
    <Dialog
      open
      fullWidth
      maxWidth="sm"
      aria-labelledby={titleId}
      onClose={busy ? undefined : onClose}
    >
      <DialogTitle id={titleId}>
        Swap drivers · {lineup.race.name}
        {lineup.race.type === 'SPRINT' ? ' (Sprint)' : ''}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ pt: 1 }}>
          <DialogContentText>
            Choose two drivers to exchange their places in this race's lineup.
          </DialogContentText>
          <FormControl fullWidth disabled={busy}>
            <InputLabel id={firstLabelId}>First driver</InputLabel>
            <Select
              labelId={firstLabelId}
              label="First driver"
              value={first}
              onChange={(event) => setFirstDriver(event.target.value)}
            >
              {drivers.map((driver) => (
                <MenuItem key={driver.name} value={driver.name} disabled={driver.name === second}>
                  <DriverLabel name={driver.name} team={driver.team} />
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl fullWidth disabled={busy}>
            <InputLabel id={secondLabelId}>Second driver</InputLabel>
            <Select
              labelId={secondLabelId}
              label="Second driver"
              value={second}
              onChange={(event) => setSecondDriver(event.target.value)}
            >
              {drivers.map((driver) => (
                <MenuItem key={driver.name} value={driver.name} disabled={driver.name === first}>
                  <DriverLabel name={driver.name} team={driver.team} />
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant="contained"
          startIcon={<SwapHorizRounded />}
          loading={busy}
          disabled={busy || !first || !second || first === second}
          onClick={() => onSwap(first, second)}
        >
          Swap
        </Button>
      </DialogActions>
    </Dialog>
  )
}
