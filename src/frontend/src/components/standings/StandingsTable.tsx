import {
  Avatar,
  Badge,
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import type { SeasonStandingsDto } from '../../dto/standings'
import { raceFlag } from '../raceFlag'
import StandingsRow from './StandingsRow'
import { STICKY_BG } from './standingsStyles'

const RACE_COL_WIDTH = 40
const LEFT_COL_MIN_WIDTH = 280
const RIGHT_COL_WIDTH = 80

export default function StandingsTable({
  standings,
  currentParticipantId,
}: {
  standings: SeasonStandingsDto
  currentParticipantId: string
}) {
  const races = standings.events

  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 2,
        bgcolor: STICKY_BG,
        backgroundImage: 'none',
        p: 2,
        minWidth: 0,
        overflow: 'hidden',
      }}
    >
      <Box
        tabIndex={0}
        sx={{
          width: '100%',
          overflowX: 'auto',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,0.2) transparent',
          '&::-webkit-scrollbar': { height: 5 },
          '&::-webkit-scrollbar-track': { backgroundColor: 'transparent' },
          '&::-webkit-scrollbar-thumb': {
            backgroundColor: 'rgba(255,255,255,0.2)',
            borderRadius: 3,
          },
        }}
      >
        <Table
          aria-label={`${standings.season} season standings`}
          sx={{
            tableLayout: 'fixed',
            width: '100%',
            minWidth: LEFT_COL_MIN_WIDTH + races.length * RACE_COL_WIDTH + RIGHT_COL_WIDTH,
          }}
        >
          <TableHead>
            <TableRow>
              <TableCell
                sx={{
                  position: { xs: 'static', md: 'sticky' },
                  left: { xs: 'auto', md: 0 },
                  zIndex: { xs: 0, md: 3 },
                  bgcolor: STICKY_BG,
                  borderRight: '1px solid rgba(255,255,255,0.12)',
                  p: '8px 12px',
                }}
              >
                <Typography variant="body2" color="text.secondary" sx={{ pl: '34px' }}>
                  Participant
                </Typography>
              </TableCell>
              {races.map((race) => {
                const flag = (
                  <Avatar
                    variant="rounded"
                    alt={race.name}
                    src={raceFlag(race.name)}
                    sx={{ width: 24, height: 24, fontSize: '0.65rem' }}
                  >
                    {race.name.slice(0, 1)}
                  </Avatar>
                )
                return (
                  <TableCell key={race.id} align="center" sx={{ width: RACE_COL_WIDTH, p: '4px' }}>
                    <Tooltip
                      title={`${race.name}${race.type === 'SPRINT' ? ' · Sprint' : ''}`}
                      arrow
                    >
                      <Box component="span" sx={{ display: 'inline-flex' }}>
                        {race.type === 'SPRINT' ? (
                          <Badge variant="dot" color="info">
                            {flag}
                          </Badge>
                        ) : (
                          flag
                        )}
                      </Box>
                    </Tooltip>
                  </TableCell>
                )
              })}
              <TableCell
                align="center"
                sx={{
                  position: 'sticky',
                  right: 0,
                  zIndex: 3,
                  bgcolor: STICKY_BG,
                  borderLeft: '1px solid rgba(255,255,255,0.12)',
                  width: RIGHT_COL_WIDTH,
                  p: '8px 12px',
                }}
              >
                <Typography variant="body2" color="text.secondary">
                  Total
                </Typography>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {standings.rows.map((row, index) => (
              <StandingsRow
                key={row.participantId}
                results={row}
                isMe={row.participantId === currentParticipantId}
                races={races}
                season={standings.season}
                position={index + 1}
                pointsLeft={standings.pointsLeft}
                leaderPoints={standings.currentLeaderPoints}
              />
            ))}
          </TableBody>
        </Table>
      </Box>
    </Paper>
  )
}
