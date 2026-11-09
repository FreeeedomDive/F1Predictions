import {
  Box,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material'
import { useId } from 'react'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'

export function DriverLabel({
  name,
  teams,
  grid,
  highlighted = false,
}: {
  name: string
  teams: RaceLineupTeamDto[]
  grid?: string[] | null
  highlighted?: boolean
}) {
  const team = teams.find((item) => item.drivers.includes(name))
  const position = (grid?.indexOf(name) ?? -1) + 1
  return (
    <Stack
      component="span"
      direction="row"
      spacing={0.75}
      sx={{ alignItems: 'center', minWidth: 0 }}
    >
      {position > 0 && (
        <Box
          component="span"
          sx={{
            minWidth: 28,
            px: 0.5,
            borderRadius: 0.5,
            bgcolor: 'rgba(165, 180, 252, 0.1)',
            color: 'primary.main',
            textAlign: 'center',
            fontSize: '0.7rem',
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          P{position}
        </Box>
      )}
      {team && (
        <Chip
          component="span"
          size="small"
          label={team.badge}
          title={team.name}
          sx={{
            bgcolor: team.backgroundColor,
            color: team.textColor,
            fontWeight: 650,
            minWidth: 40,
            height: 20,
            fontSize: '0.65rem',
            flexShrink: 0,
            '& .MuiChip-label': { px: 0.75 },
          }}
        />
      )}
      <Typography
        component="span"
        variant="body2"
        noWrap
        sx={{
          minWidth: 0,
          fontSize: '0.8rem',
          color: highlighted ? 'primary.main' : 'text.primary',
          fontWeight: highlighted ? 600 : 400,
        }}
      >
        {name}
      </Typography>
    </Stack>
  )
}

export default function DriverSelect({
  label,
  value,
  teams,
  grid,
  disabled,
  onChange,
}: {
  label: string
  value: string
  teams: RaceLineupTeamDto[]
  grid?: string[] | null
  disabled: boolean
  onChange: (name: string) => void
}) {
  const labelId = useId()
  const roster = teams.flatMap((team) => team.drivers)
  const drivers = [...new Set([...(grid ?? []).filter((name) => roster.includes(name)), ...roster])]
  const options = value && !drivers.includes(value) ? [...drivers, value] : drivers
  return (
    <FormControl size="small" fullWidth disabled={disabled}>
      <InputLabel id={labelId} shrink>
        {label}
      </InputLabel>
      <Select
        labelId={labelId}
        label={label}
        notched
        displayEmpty
        value={value}
        renderValue={(name) =>
          name ? <DriverLabel name={name} teams={teams} grid={grid} /> : 'Not predicted'
        }
        onChange={(event) => onChange(event.target.value)}
      >
        <MenuItem value="">Not predicted</MenuItem>
        {options.map((name) => (
          <MenuItem key={name} value={name} disabled={!drivers.includes(name)}>
            <DriverLabel name={name} teams={teams} grid={grid} />
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}
