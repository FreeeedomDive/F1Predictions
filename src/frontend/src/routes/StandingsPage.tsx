import LeaderboardRounded from '@mui/icons-material/LeaderboardRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Button,
  Container,
  IconButton,
  Paper,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import { isAxiosError } from 'axios'
import { Navigate } from 'react-router'
import { isUnauthorized } from '../api/session'
import SeasonSummary from '../components/standings/SeasonSummary'
import StandingsChart from '../components/standings/StandingsChart'
import StandingsTable from '../components/standings/StandingsTable'
import { useLeague } from '../context/league'
import { useSeasonStandings } from '../queries/standings'

function EmptyStandings({ title, description }: { title: string; description: string }) {
  return (
    <Paper variant="outlined" sx={{ p: { xs: 3, md: 5 }, textAlign: 'center' }}>
      <LeaderboardRounded sx={{ color: 'text.secondary', fontSize: 36, mb: 1.5 }} />
      <Typography variant="h6" sx={{ mb: 1 }}>
        {title}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {description}
      </Typography>
    </Paper>
  )
}

export default function StandingsPage() {
  const { profile, selectedLeague, selectedSeason } = useLeague()
  const standings = useSeasonStandings(profile.userId, selectedLeague?.leagueId, selectedSeason)

  if (standings.isError && isUnauthorized(standings.error)) {
    return <Navigate to="/auth" replace />
  }

  const unavailable =
    standings.isError &&
    isAxiosError(standings.error) &&
    [403, 404].includes(standings.error.response?.status ?? 0)

  return (
    <Container
      component="section"
      maxWidth={false}
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={3}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5} sx={{ minWidth: 0 }}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Standings
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {selectedLeague?.leagueName ?? 'No league selected'}
              {selectedSeason !== null && ` · ${selectedSeason}`}
            </Typography>
          </Stack>
          <Tooltip title="Refresh standings">
            <span>
              <IconButton
                aria-label="Refresh standings"
                disabled={!selectedLeague || selectedSeason === null || standings.isFetching}
                onClick={() => void standings.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>

        {!selectedLeague ? (
          <EmptyStandings
            title="No leagues available"
            description="Season standings will appear once you join a league."
          />
        ) : selectedSeason === null ? (
          <EmptyStandings
            title="No seasons available"
            description="This league's first season has not started yet."
          />
        ) : standings.isPending ? (
          <Stack spacing={1} role="status" aria-label="Loading standings">
            {[0, 1, 2, 3, 4].map((index) => (
              <Skeleton key={index} variant="rounded" height={56} />
            ))}
          </Stack>
        ) : standings.isError ? (
          <Alert
            severity={unavailable ? 'warning' : 'error'}
            action={
              <Button color="inherit" onClick={() => void standings.refetch()}>
                Retry
              </Button>
            }
          >
            {unavailable
              ? 'Standings are not available for this league.'
              : 'Could not load standings. Please try again.'}
          </Alert>
        ) : standings.data.rows.length === 0 ? (
          <EmptyStandings
            title={`The ${selectedSeason} season has not started yet`}
            description="Standings and charts will appear after the first race is finished."
          />
        ) : (
          <Stack spacing={2}>
            <SeasonSummary standings={standings.data} />
            <StandingsTable
              standings={standings.data}
              currentParticipantId={selectedLeague.participantId}
            />
            <StandingsChart
              key={`${selectedLeague.leagueId}_${selectedSeason}`}
              standings={standings.data}
              races={standings.data.events}
              currentParticipantId={selectedLeague.participantId}
            />
          </Stack>
        )}
      </Stack>
    </Container>
  )
}
