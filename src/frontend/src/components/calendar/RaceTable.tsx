import {
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { ChipProps } from '@mui/material'
import type { RaceDto, RaceStatusDto } from '../../dto/race'

const statuses: Record<RaceStatusDto, { label: string; color: ChipProps['color'] }> = {
  SCHEDULED: { label: 'Scheduled', color: 'default' },
  OPEN: { label: 'Open', color: 'success' },
  CLOSED: { label: 'Closed', color: 'warning' },
  FINISHED: { label: 'Finished', color: 'default' },
}

const dateTime = new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function RaceTime({ value }: { value: string }) {
  return (
    <Typography
      component="time"
      dateTime={value}
      variant="body2"
      sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}
    >
      {dateTime.format(new Date(value))}
    </Typography>
  )
}

export default function RaceTable({ races }: { races: RaceDto[] }) {
  return (
    <TableContainer component={Paper} variant="outlined" tabIndex={0}>
      <Table
        aria-label="Race calendar"
        size="small"
        sx={{
          minWidth: 800,
          '& th, & td': { px: 2, py: 1.5 },
          '& thead th': { fontWeight: 650, whiteSpace: 'nowrap' },
          '& tbody tr:last-child > *': { borderBottom: 0 },
        }}
      >
        <TableHead sx={{ bgcolor: 'action.hover' }}>
          <TableRow>
            <TableCell scope="col">Name</TableCell>
            <TableCell scope="col">Type</TableCell>
            <TableCell scope="col">Status</TableCell>
            <TableCell scope="col">Predictions open</TableCell>
            <TableCell scope="col">Predictions close</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {races.map((race) => (
            <TableRow key={race.id} hover>
              <TableCell component="th" scope="row" sx={{ fontWeight: 600 }}>
                {race.name}
              </TableCell>
              <TableCell>
                <Chip
                  label={race.type === 'SPRINT' ? 'Sprint' : 'Race'}
                  color={race.type === 'SPRINT' ? 'secondary' : 'default'}
                  variant="outlined"
                  size="small"
                />
              </TableCell>
              <TableCell>
                <Chip {...statuses[race.status]} size="small" />
              </TableCell>
              <TableCell>
                <RaceTime value={race.openAt} />
              </TableCell>
              <TableCell>
                <RaceTime value={race.closeAt} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
