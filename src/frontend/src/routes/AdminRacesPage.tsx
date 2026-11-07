import EditCalendarRounded from '@mui/icons-material/EditCalendarRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Button,
  Container,
  IconButton,
  Paper,
  Skeleton,
  Snackbar,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import { isAxiosError } from 'axios'
import { Navigate } from 'react-router'
import { isUnauthorized } from '../api/session'
import RaceTable from '../components/calendar/RaceTable'
import { useLeague } from '../context/league'
import { useAdminRaceCalendar, useChangeRacePredictionsStatus } from '../queries/races'

const timeZone = new Intl.DateTimeFormat().resolvedOptions().timeZone.replaceAll('_', ' ')

function actionErrorMessage(error: Error | null, action: 'open' | 'close') {
  if (isAxiosError(error)) {
    switch (error.response?.status) {
      case 403:
        return `You do not have permission to ${action} predictions.`
      case 404:
        return 'This race no longer exists.'
      case 409:
        return `This race can no longer be ${action === 'open' ? 'opened' : 'closed'}. Check its current status.`
    }
  }
  return `Could not ${action} predictions. Please try again.`
}

export default function AdminRacesPage() {
  const { selectedSeason } = useLeague()
  const calendar = useAdminRaceCalendar(selectedSeason)
  const transition = useChangeRacePredictionsStatus()
  const action = transition.variables?.action ?? 'open'

  if (
    (calendar.isError && isUnauthorized(calendar.error)) ||
    (transition.isError && isUnauthorized(transition.error))
  ) {
    return <Navigate to="/auth" replace />
  }

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
              Race administration
            </Typography>
            <Typography variant="body2" color="text.secondary">
              All races and sprints
              {selectedSeason !== null && ` · ${selectedSeason}`}
            </Typography>
          </Stack>
          <Tooltip title="Refresh calendar">
            <span>
              <IconButton
                aria-label="Refresh calendar"
                disabled={selectedSeason === null || calendar.isFetching || transition.isPending}
                onClick={() => void calendar.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>

        {selectedSeason === null ? (
          <Alert severity="info">
            No seasons available. This league's first season has not started yet.
          </Alert>
        ) : calendar.isPending ? (
          <Stack spacing={1} role="status" aria-label="Loading calendar">
            {[0, 1, 2, 3, 4].map((index) => (
              <Skeleton key={index} variant="rounded" height={56} />
            ))}
          </Stack>
        ) : calendar.isError ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" onClick={() => void calendar.refetch()}>
                Retry
              </Button>
            }
          >
            {isAxiosError(calendar.error) && calendar.error.response?.status === 403
              ? 'You do not have permission to view the admin calendar.'
              : 'Could not load the admin calendar. Please try again.'}
          </Alert>
        ) : calendar.data.length === 0 ? (
          <Paper variant="outlined" sx={{ p: { xs: 3, md: 5 }, textAlign: 'center' }}>
            <EditCalendarRounded sx={{ color: 'text.secondary', fontSize: 36, mb: 1.5 }} />
            <Typography variant="h6" sx={{ mb: 1 }}>
              No races in {selectedSeason}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              The season calendar has not been imported yet.
            </Typography>
          </Paper>
        ) : (
          <Stack spacing={2}>
            <Typography variant="caption" color="text.secondary">
              Times in {timeZone}
            </Typography>
            <RaceTable
              races={calendar.data}
              renderActions={(race) => {
                const action =
                  race.status === 'SCHEDULED' ? 'open' : race.status === 'OPEN' ? 'close' : null
                if (!action) return null

                const label = action === 'open' ? 'Open predictions' : 'Close predictions'

                return (
                  <Button
                    size="small"
                    variant="outlined"
                    color={action === 'close' ? 'warning' : 'primary'}
                    aria-label={`${label} for ${race.name} (${race.type === 'SPRINT' ? 'Sprint' : 'Race'})`}
                    loading={transition.isPending && transition.variables?.raceId === race.id}
                    disabled={transition.isPending}
                    onClick={() => transition.mutate({ raceId: race.id, action })}
                    sx={{ whiteSpace: 'nowrap' }}
                  >
                    {label}
                  </Button>
                )
              }}
            />
          </Stack>
        )}
      </Stack>
      <Snackbar
        open={transition.isSuccess || transition.isError}
        autoHideDuration={6000}
        onClose={() => transition.reset()}
      >
        <Alert
          severity={transition.isError ? 'error' : 'success'}
          onClose={() => transition.reset()}
        >
          {transition.isError
            ? actionErrorMessage(transition.error, action)
            : `Predictions ${action === 'open' ? 'opened' : 'closed'}.`}
        </Alert>
      </Snackbar>
    </Container>
  )
}
