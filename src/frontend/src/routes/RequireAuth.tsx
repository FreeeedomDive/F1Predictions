import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { Button } from '@mui/material'
import { Navigate, Outlet } from 'react-router'
import StatusScreen from '../components/StatusScreen'
import LeagueProvider from '../context/LeagueProvider'
import { useMe } from '../queries/me'

export default function RequireAuth() {
  const profile = useMe()

  if (profile.isPending) {
    return (
      <StatusScreen
        title="Loading your workspace"
        description="Fetching your profile and leagues."
        loading
      />
    )
  }

  if (profile.isError) {
    return (
      <StatusScreen
        title="Could not load your workspace"
        description="The server could not be reached. Your session may still be active."
        action={
          <Button startIcon={<RefreshRounded />} onClick={() => void profile.refetch()}>
            Try again
          </Button>
        }
      />
    )
  }

  if (!profile.data) return <Navigate to="/auth" replace />

  return (
    <LeagueProvider key={profile.data.userId} profile={profile.data}>
      <Outlet />
    </LeagueProvider>
  )
}
