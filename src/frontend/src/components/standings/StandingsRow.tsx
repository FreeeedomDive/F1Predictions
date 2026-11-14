import EmojiEventsRounded from '@mui/icons-material/EmojiEventsRounded'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import KeyboardArrowDownRounded from '@mui/icons-material/KeyboardArrowDownRounded'
import KeyboardArrowUpRounded from '@mui/icons-material/KeyboardArrowUpRounded'
import {
  Avatar,
  Box,
  Collapse,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import { Fragment, useState } from 'react'
import type { RaceDto } from '../../dto/race'
import type { SeasonRacePointsDto, SeasonStandingsRowDto } from '../../dto/standings'
import { STICKY_BG } from './standingsStyles'

const STICKY_BG_ME = '#200056'
const PODIUM_COLORS = ['#FFD700', '#C0C0C0', '#CD7F32']
const HISTORY_POINTS_COL_WIDTH = 120
const HISTORY_TOTAL_COL_WIDTH = 80

function points(value: number | null) {
  return value ?? ''
}

function categoryColumns(season: number) {
  return [
    {
      key: 'tenth',
      label: '10th place',
      value: (result: SeasonRacePointsDto) => result.points.tenthPlacePoints,
    },
    {
      key: 'dnf',
      label: season === 2023 ? 'First DNF' : 'DNF',
      value: (result: SeasonRacePointsDto) => result.points.dnfsPoints,
    },
    ...(season > 2023
      ? [
          {
            key: 'incidents',
            label: 'Safety cars',
            value: (result: SeasonRacePointsDto) => result.points.safetyCarsPoints,
          },
          {
            key: 'lead',
            label: 'First place lead',
            value: (result: SeasonRacePointsDto) => result.points.firstPlaceLeadPoints,
          },
        ]
      : []),
    ...(season === 2024 || season === 2025
      ? [
          {
            key: 'teams',
            label: 'Team mates',
            value: (result: SeasonRacePointsDto) => result.points.teamMatesPoints,
          },
        ]
      : []),
    ...(season >= 2026
      ? [
          {
            key: 'position',
            label: 'Driver position',
            value: (result: SeasonRacePointsDto) => result.points.driverPositionPoints,
          },
        ]
      : []),
  ]
}

function pointsDetails(result: SeasonRacePointsDto, race: RaceDto, season: number) {
  const details = categoryColumns(season).map(
    (column) => `${column.label}: ${points(column.value(result))}`,
  )
  details.push(
    `Total: ${result.points.totalPoints}${race.type === 'SPRINT' && season >= 2025 ? ' (30% for sprint)' : ''}`,
  )
  return details.join('\n')
}

export default function StandingsRow({
  results,
  avatarUrl,
  isMe,
  races,
  season,
  position,
  pointsLeft,
  leaderPoints,
}: {
  results: SeasonStandingsRowDto
  avatarUrl?: string | null
  isMe: boolean
  races: RaceDto[]
  season: number
  position: number
  pointsLeft: number
  leaderPoints: number
}) {
  const [isOpen, setIsOpen] = useState(false)
  const stillInChampionship = leaderPoints - results.totalPoints < pointsLeft
  const stickyBg = isMe ? STICKY_BG_ME : STICKY_BG
  const resultsByRaceId = new Map(results.results.map((result) => [result.raceId, result]))
  const raceById = new Map(races.map((race) => [race.id, race]))
  const columns = categoryColumns(season)
  const initial = results.participantName.trim().slice(0, 1).toLocaleUpperCase()

  return (
    <Fragment>
      <TableRow sx={isMe ? { backgroundColor: STICKY_BG_ME } : {}}>
        <TableCell
          sx={{
            position: { xs: 'static', md: 'sticky' },
            left: { xs: 'auto', md: 0 },
            zIndex: { xs: 0, md: 2 },
            bgcolor: stickyBg,
            borderRight: '1px solid rgba(255,255,255,0.12)',
            p: '4px 8px',
          }}
        >
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <IconButton
              aria-label={`${isOpen ? 'Hide' : 'Show'} predictions history for ${results.participantName}`}
              aria-expanded={isOpen}
              size="small"
              onClick={() => setIsOpen((open) => !open)}
              sx={{ flexShrink: 0 }}
            >
              {isOpen ? <KeyboardArrowUpRounded /> : <KeyboardArrowDownRounded />}
            </IconButton>
            <Avatar
              src={avatarUrl ?? undefined}
              alt={results.participantName}
              sx={{ width: 24, height: 24, flexShrink: 0, fontSize: '0.75rem' }}
            >
              {initial}
            </Avatar>
            <Typography noWrap sx={{ minWidth: 0, fontWeight: isMe ? 600 : 400 }}>
              {results.participantName}
            </Typography>
            <Stack direction="row" spacing={0.25} sx={{ flexShrink: 0 }}>
              {results.previousPodiums.map((podium) => {
                const label = `${podium.season}: ${podium.place}${podium.place === 1 ? 'st' : podium.place === 2 ? 'nd' : 'rd'} place`
                return (
                  <Tooltip key={podium.season} title={label} arrow>
                    <Box
                      component="span"
                      role="img"
                      tabIndex={0}
                      aria-label={label}
                      sx={{ display: 'inline-flex' }}
                    >
                      <EmojiEventsRounded
                        sx={{ color: PODIUM_COLORS[podium.place - 1], fontSize: 19 }}
                      />
                    </Box>
                  </Tooltip>
                )
              })}
            </Stack>
          </Stack>
        </TableCell>
        {races.map((race) => {
          const raceResult = resultsByRaceId.get(race.id)
          return (
            <TableCell
              key={`${results.participantId}_${race.id}_points`}
              align="center"
              sx={{ p: '4px', width: 40, fontVariantNumeric: 'tabular-nums' }}
            >
              {raceResult && (
                <Tooltip
                  title={
                    <Typography sx={{ whiteSpace: 'pre-line', fontSize: '0.75rem' }}>
                      {pointsDetails(raceResult, race, season)}
                    </Typography>
                  }
                  arrow
                  placement="top"
                >
                  <Typography component="span" sx={{ cursor: 'default' }}>
                    {raceResult.points.totalPoints}
                  </Typography>
                </Tooltip>
              )}
            </TableCell>
          )
        })}
        <TableCell
          align="center"
          sx={{
            position: 'sticky',
            right: 0,
            zIndex: 2,
            bgcolor: stickyBg,
            borderLeft: '1px solid rgba(255,255,255,0.12)',
            p: '4px 8px',
          }}
        >
          <Box
            sx={{
              display: 'flex',
              alignItems: 'baseline',
              justifyContent: 'center',
              gap: 0.5,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            <Typography
              sx={{
                fontWeight: 'bold',
                color: position === 1 && pointsLeft === 0 ? PODIUM_COLORS[0] : 'inherit',
              }}
            >
              {results.totalPoints}
            </Typography>
            {position > 1 && pointsLeft > 0 && (
              <Tooltip
                title={
                  stillInChampionship ? 'Still in the title fight' : 'No longer in the title fight'
                }
                arrow
              >
                <Typography
                  variant="caption"
                  sx={{ color: stillInChampionship ? 'error.main' : 'text.disabled' }}
                >
                  {results.totalPoints - leaderPoints}
                </Typography>
              </Tooltip>
            )}
            {position > 1 && pointsLeft === 0 && (
              <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                {results.totalPoints - leaderPoints}
              </Typography>
            )}
          </Box>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell sx={{ py: 0 }} colSpan={2 + races.length}>
          <Collapse in={isOpen} timeout="auto" unmountOnExit>
            <Box sx={{ m: 1, overflowX: 'auto' }}>
              <Typography variant="h6" component="div">
                Predictions history
              </Typography>
              <Table
                size="small"
                aria-label={`Predictions history for ${results.participantName}`}
                sx={{
                  tableLayout: 'fixed',
                  width: '100%',
                  minWidth:
                    240 + columns.length * HISTORY_POINTS_COL_WIDTH + HISTORY_TOTAL_COL_WIDTH,
                  '& .MuiTableCell-root': { px: 1 },
                  '& .MuiTableCell-alignCenter': { fontVariantNumeric: 'tabular-nums' },
                }}
              >
                <TableHead>
                  <TableRow>
                    <TableCell>Race</TableCell>
                    {columns.map((column) => (
                      <TableCell
                        key={column.key}
                        align="center"
                        sx={{ width: HISTORY_POINTS_COL_WIDTH }}
                      >
                        {column.label}
                      </TableCell>
                    ))}
                    <TableCell
                      align="center"
                      sx={{ width: HISTORY_TOTAL_COL_WIDTH, whiteSpace: 'nowrap' }}
                    >
                      Total
                      {season >= 2025 && (
                        <Tooltip title="Sprints award 30% of the points" arrow>
                          <IconButton
                            aria-label="Sprint points information"
                            size="small"
                            sx={{
                              p: 0.5,
                              minWidth: 'auto',
                              borderRadius: '50%',
                              backgroundColor: 'transparent',
                            }}
                          >
                            <InfoOutlined sx={{ fontSize: 18 }} />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {results.results.map((raceResult) => {
                    const race = raceById.get(raceResult.raceId)
                    if (!race) return null
                    return (
                      <TableRow key={`${results.participantId}_${raceResult.raceId}`}>
                        <TableCell>
                          {race.name}
                          {race.type === 'SPRINT' ? ' (Sprint)' : ''}
                        </TableCell>
                        {columns.map((column) => (
                          <TableCell key={column.key} align="center">
                            {points(column.value(raceResult))}
                          </TableCell>
                        ))}
                        <TableCell align="center">{raceResult.points.totalPoints}</TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </Fragment>
  )
}
