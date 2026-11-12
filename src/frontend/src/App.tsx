import { Button } from '@mui/material'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import StatusScreen from './components/StatusScreen'
import AppLayout from './layout/AppLayout'
import AuthPage, { AuthLoadingScreen } from './routes/AuthPage'
import { authLoader } from './routes/authLoader'
import CalendarPage from './routes/CalendarPage'
import RequireAuth from './routes/RequireAuth'

const withDevPrefix = (title: string) => (import.meta.env.DEV ? `[dev] ${title}` : title)

const errorScreen = (
  <StatusScreen
    title="Something went wrong"
    description="Reload the app to restore your session."
    action={<Button href="/">Reload app</Button>}
  />
)

const router = createBrowserRouter([
  {
    path: '/auth',
    loader: authLoader,
    element: (
      <>
        <title>{withDevPrefix('Not authorized')}</title>
        <AuthPage />
      </>
    ),
    hydrateFallbackElement: <AuthLoadingScreen />,
    errorElement: errorScreen,
  },
  {
    element: <RequireAuth />,
    errorElement: errorScreen,
    hydrateFallbackElement: (
      <StatusScreen title="Loading page" description="Preparing your workspace." loading />
    ),
    children: [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/calendar" replace /> },
          {
            path: 'calendar',
            element: (
              <>
                <title>{withDevPrefix('Calendar')}</title>
                <CalendarPage />
              </>
            ),
          },
          {
            path: 'standings',
            lazy: async () => {
              const { default: StandingsPage } = await import('./routes/StandingsPage')
              return {
                element: (
                  <>
                    <title>{withDevPrefix('Standings')}</title>
                    <StandingsPage />
                  </>
                ),
              }
            },
          },
          {
            path: 'statistics',
            lazy: async () => {
              const { default: StatisticsPage } = await import('./routes/StatisticsPage')
              return {
                element: (
                  <>
                    <title>{withDevPrefix('Statistics')}</title>
                    <StatisticsPage />
                  </>
                ),
              }
            },
          },
          {
            path: 'prediction',
            lazy: async () => {
              const { default: RacePage } = await import('./routes/RacePage')
              return { element: <RacePage current /> }
            },
          },
          {
            path: 'races/:raceId',
            lazy: async () => {
              const { default: RacePage } = await import('./routes/RacePage')
              return { element: <RacePage /> }
            },
          },
          {
            path: 'admin/races',
            lazy: async () => {
              const { default: AdminRacesPage } = await import('./routes/AdminRacesPage')
              return {
                element: (
                  <>
                    <title>{withDevPrefix('[Admin] Races')}</title>
                    <AdminRacesPage />
                  </>
                ),
              }
            },
          },
          ...['admin/results', 'admin/results/:raceId'].map((path) => ({
            path,
            lazy: async () => {
              const { default: AdminResultsPage } = await import('./routes/AdminResultsPage')
              return {
                element: (
                  <>
                    <title>{withDevPrefix('[Admin] Results')}</title>
                    <AdminResultsPage />
                  </>
                ),
              }
            },
          })),
          {
            path: 'admin/teams',
            lazy: async () => {
              const { default: AdminTeamsPage } = await import('./routes/AdminTeamsPage')
              return {
                element: (
                  <>
                    <title>{withDevPrefix('[Admin] Teams')}</title>
                    <AdminTeamsPage />
                  </>
                ),
              }
            },
          },
          { path: '*', element: <Navigate to="/calendar" replace /> },
        ],
      },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
