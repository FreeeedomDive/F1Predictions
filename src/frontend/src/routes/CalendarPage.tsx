import CalendarMonthRounded from '@mui/icons-material/CalendarMonthRounded'
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
import { CURRENT_SEASON } from '../app/season'
import RaceTable from '../components/calendar/RaceTable'
import { useLeague } from '../context/league'
import { useRaceCalendar } from '../queries/races'

const timeZone = new Intl.DateTimeFormat().resolvedOptions().timeZone.replaceAll('_', ' ')

function EmptyCalendar({ title, description }: { title: string; description: string }) {
  return (
    <Paper variant="outlined" sx={{ p: { xs: 3, md: 5 }, textAlign: 'center' }}>
      <CalendarMonthRounded sx={{ color: 'text.secondary', fontSize: 36, mb: 1.5 }} />
      <Typography variant="h6" sx={{ mb: 1 }}>
        {title}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {description}
      </Typography>
    </Paper>
  )
}

export default function CalendarPage() {
  const { selectedLeague } = useLeague()
  const calendar = useRaceCalendar(selectedLeague?.leagueId, CURRENT_SEASON)

  if (calendar.isError && isUnauthorized(calendar.error)) {
    return <Navigate to="/auth" replace />
  }

  const unavailable =
    calendar.isError &&
    isAxiosError(calendar.error) &&
    [403, 404].includes(calendar.error.response?.status ?? 0)

  return (
    <Container
      component="section"
      maxWidth="xl"
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={3}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5} sx={{ minWidth: 0 }}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Calendar
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {selectedLeague ? `${selectedLeague.leagueName} · ${CURRENT_SEASON}` : CURRENT_SEASON}
            </Typography>
          </Stack>
          <Tooltip title="Refresh calendar">
            <span>
              <IconButton
                aria-label="Refresh calendar"
                disabled={!selectedLeague || calendar.isFetching}
                onClick={() => void calendar.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>

        {!selectedLeague ? (
          <EmptyCalendar
            title="No leagues available"
            description="Your calendar will appear once you join a league."
          />
        ) : calendar.isPending ? (
          <Stack spacing={1} role="status" aria-label="Loading calendar">
            {[0, 1, 2, 3, 4].map((index) => (
              <Skeleton key={index} variant="rounded" height={56} />
            ))}
          </Stack>
        ) : calendar.isError ? (
          <Alert
            severity={unavailable ? 'warning' : 'error'}
            action={
              <Button color="inherit" onClick={() => void calendar.refetch()}>
                Retry
              </Button>
            }
          >
            {unavailable
              ? 'The calendar is not available for this league.'
              : 'Could not load the calendar. Please try again.'}
          </Alert>
        ) : calendar.data.length === 0 ? (
          <EmptyCalendar
            title={`No races in ${CURRENT_SEASON}`}
            description="There are no calendar entries for this league in the selected season."
          />
        ) : (
          <Stack spacing={2}>
            <Typography variant="caption" color="text.secondary">
              Times in {timeZone}
            </Typography>
            <RaceTable races={calendar.data} />
          </Stack>
        )}
      </Stack>
    </Container>
  )
}
