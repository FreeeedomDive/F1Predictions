import { Button } from '@mui/material'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import StatusScreen from './components/StatusScreen'
import AppLayout from './layout/AppLayout'
import AdminRacesPage from './routes/AdminRacesPage'
import AdminTeamsPage from './routes/AdminTeamsPage'
import AuthPage, { AuthLoadingScreen } from './routes/AuthPage'
import { authLoader } from './routes/authLoader'
import CalendarPage from './routes/CalendarPage'
import RequireAuth from './routes/RequireAuth'
import RacePage from './routes/RacePage'

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
            path: 'prediction',
            element: <RacePage current />,
          },
          {
            path: 'races/:raceId',
            element: <RacePage />,
          },
          {
            path: 'admin/races',
            element: (
              <>
                <title>{withDevPrefix('[Admin] Races')}</title>
                <AdminRacesPage />
              </>
            ),
          },
          {
            path: 'admin/teams',
            element: (
              <>
                <title>{withDevPrefix('[Admin] Teams')}</title>
                <AdminTeamsPage />
              </>
            ),
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
