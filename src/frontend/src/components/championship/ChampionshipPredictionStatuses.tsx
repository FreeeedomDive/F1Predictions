import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckRounded from '@mui/icons-material/CheckRounded'
import {
  Alert,
  Avatar,
  Button,
  MenuItem,
  Paper,
  Skeleton,
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
import { useState } from 'react'
import { Navigate } from 'react-router'
import { isUnauthorized } from '../../api/session'
import { useLeague } from '../../context/league'
import { useChampionshipPredictionStatuses } from '../../queries/championship'
import { useParticipantAvatars } from '../../queries/participants'

export default function ChampionshipPredictionStatuses({ season }: { season: number }) {
  const { profile, selectedLeague } = useLeague()
  const [filter, setFilter] = useState<string | null>(null)
  const leagues = profile.leagues.filter((league) => league.firstSeason <= season)
  const leagueId =
    leagues.find((league) => league.leagueId === (filter ?? selectedLeague?.leagueId))?.leagueId ??
    leagues[0]?.leagueId
  const statuses = useChampionshipPredictionStatuses(profile.userId, leagueId, season)
  const avatars = useParticipantAvatars(
    profile.userId,
    statuses.data?.length ? leagueId : undefined,
  )
  if (isUnauthorized(statuses.error)) return <Navigate to="/auth" replace />

  return (
    <Paper
      component="section"
      aria-label="Prediction statuses"
      variant="outlined"
      sx={{ minWidth: 0, overflow: 'hidden' }}
    >
      <Stack spacing={1.5} sx={{ p: 2 }}>
        <Stack
          direction="row"
          sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}
        >
          <Typography component="h2" variant="h6">
            Prediction statuses
          </Typography>
          {leagueId && (
            <TextField
              select
              size="small"
              label="League"
              value={leagueId}
              onChange={(event) => setFilter(event.target.value)}
              sx={{ width: 220, maxWidth: '100%', ml: 'auto' }}
            >
              {leagues.map((league) => (
                <MenuItem key={league.leagueId} value={league.leagueId}>
                  {league.leagueName}
                </MenuItem>
              ))}
            </TextField>
          )}
        </Stack>
        {leagueId && statuses.isPending && (
          <Skeleton height={36} aria-label="Loading prediction statuses" />
        )}
        {statuses.isError && (
          <Alert
            severity="error"
            action={<Button onClick={() => void statuses.refetch()}>Retry</Button>}
          >
            Could not load prediction statuses for this league.
          </Alert>
        )}
      </Stack>
      <TableContainer>
        <Table size="small" aria-label="Prediction statuses">
          <TableHead>
            <TableRow>
              <TableCell>Participant</TableCell>
              <TableCell align="center">Pre-season</TableCell>
              <TableCell align="center">Mid-season</TableCell>
            </TableRow>
          </TableHead>
          <TableBody
            sx={{
              '& tr': { height: 34 },
              '& svg': { verticalAlign: 'middle' },
              '& tr:last-of-type > td': { borderBottom: 0 },
            }}
          >
            {statuses.data?.map((row) => (
              <TableRow key={row.participantId}>
                <TableCell>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Avatar
                      src={avatars.data?.[row.participantId] ?? undefined}
                      alt=""
                      aria-hidden="true"
                      sx={{ width: 24, height: 24, flexShrink: 0, fontSize: '0.75rem' }}
                    >
                      {row.displayName.trim().slice(0, 1).toLocaleUpperCase()}
                    </Avatar>
                    <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                      {row.displayName}
                    </Typography>
                  </Stack>
                </TableCell>
                <TableCell align="center">
                  {row.hasPreSeasonPrediction && (
                    <CheckRounded fontSize="small" color="success" titleAccess="Saved" />
                  )}
                </TableCell>
                <TableCell align="center">
                  {row.midSeasonPredictionSource === 'PRE_SEASON_COPY' && (
                    <ArrowForwardRounded
                      fontSize="small"
                      color="warning"
                      titleAccess="Copied from pre-season"
                    />
                  )}
                  {row.midSeasonPredictionSource === 'USER' && (
                    <CheckRounded fontSize="small" color="success" titleAccess="Saved" />
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  )
}
