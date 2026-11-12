import {
  Avatar,
  Chip,
  Link,
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
import type { ChipProps } from '@mui/material'
import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router'
import type { RaceDto, RaceStatusDto } from '../../dto/race'
import { raceFlag } from '../raceFlag'

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

export default function RaceTable({
  races,
  renderActions,
  raceLink,
}: {
  races: RaceDto[]
  renderActions?: (race: RaceDto) => ReactNode
  raceLink?: (race: RaceDto) => string
}) {
  return (
    <TableContainer component={Paper} variant="outlined" tabIndex={0}>
      <Table
        aria-label="Race calendar"
        size="small"
        sx={{
          minWidth: renderActions ? 960 : 800,
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
            {renderActions && (
              <TableCell scope="col" align="right">
                Actions
              </TableCell>
            )}
          </TableRow>
        </TableHead>
        <TableBody>
          {races.map((race) => (
            <TableRow key={race.id} hover>
              <TableCell component="th" scope="row" sx={{ fontWeight: 600 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  <Avatar
                    variant="rounded"
                    alt=""
                    src={raceFlag(race.name)}
                    sx={{ width: 24, height: 24, flexShrink: 0, fontSize: '0.65rem' }}
                  >
                    {race.name.slice(0, 1)}
                  </Avatar>
                  {raceLink ? (
                    <Link
                      component={RouterLink}
                      to={raceLink(race)}
                      underline="hover"
                      color="inherit"
                    >
                      {race.name}
                    </Link>
                  ) : (
                    race.name
                  )}
                </Stack>
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
              {renderActions && <TableCell align="right">{renderActions(race)}</TableCell>}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
