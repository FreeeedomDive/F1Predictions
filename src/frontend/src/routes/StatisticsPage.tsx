import InsightsRounded from '@mui/icons-material/InsightsRounded'
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
import SeasonStatistics from '../components/statistics/SeasonStatistics'
import { useLeague } from '../context/league'
import { useSeasonStats } from '../queries/statistics'

function EmptyStatistics({ title, description }: { title: string; description: string }) {
  return (
    <Paper variant="outlined" sx={{ p: { xs: 3, md: 5 }, textAlign: 'center' }}>
      <InsightsRounded sx={{ color: 'text.secondary', fontSize: 36, mb: 1.5 }} />
      <Typography variant="h6" sx={{ mb: 1 }}>
        {title}
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {description}
      </Typography>
    </Paper>
  )
}

export default function StatisticsPage() {
  const { profile, selectedLeague, selectedSeason } = useLeague()
  const statistics = useSeasonStats(profile.userId, selectedLeague?.leagueId, selectedSeason)

  if (statistics.isError && isUnauthorized(statistics.error)) {
    return <Navigate to="/auth" replace />
  }

  const unavailable =
    statistics.isError &&
    isAxiosError(statistics.error) &&
    [403, 404].includes(statistics.error.response?.status ?? 0)
  const empty =
    statistics.data && Object.values(statistics.data).every((items) => items.length === 0)

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
              Statistics
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {selectedLeague?.leagueName ?? 'No league selected'}
              {selectedSeason !== null && ` · ${selectedSeason}`}
            </Typography>
          </Stack>
          <Tooltip title="Refresh statistics">
            <span>
              <IconButton
                aria-label="Refresh statistics"
                disabled={!selectedLeague || selectedSeason === null || statistics.isFetching}
                onClick={() => void statistics.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {!selectedLeague ? (
          <EmptyStatistics
            title="No leagues available"
            description="Season statistics will appear once you join a league."
          />
        ) : selectedSeason === null ? (
          <EmptyStatistics
            title="No seasons available"
            description="This league's first season has not started yet."
          />
        ) : statistics.isPending ? (
          <Stack spacing={2} role="status" aria-label="Loading statistics">
            {[0, 1, 2].map((index) => (
              <Skeleton key={index} variant="rounded" height={180} />
            ))}
          </Stack>
        ) : statistics.isError ? (
          <Alert
            severity={unavailable ? 'warning' : 'error'}
            action={
              <Button color="inherit" onClick={() => void statistics.refetch()}>
                Retry
              </Button>
            }
          >
            {unavailable
              ? 'Statistics are not available for this league.'
              : 'Could not load statistics. Please try again.'}
          </Alert>
        ) : empty ? (
          <EmptyStatistics
            title="No season statistics yet"
            description="Statistics will appear once finished races have results available."
          />
        ) : (
          <SeasonStatistics
            stats={statistics.data}
            currentParticipantId={selectedLeague.participantId}
          />
        )}
      </Stack>
    </Container>
  )
}
