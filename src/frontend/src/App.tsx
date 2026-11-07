import { Button } from '@mui/material'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import StatusScreen from './components/StatusScreen'
import AppLayout from './layout/AppLayout'
import AdminRacesPage from './routes/AdminRacesPage'
import AuthPage, { AuthLoadingScreen } from './routes/AuthPage'
import { authLoader } from './routes/authLoader'
import CalendarPage from './routes/CalendarPage'
import RequireAuth from './routes/RequireAuth'

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
    element: <AuthPage />,
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
          { path: 'calendar', element: <CalendarPage /> },
          { path: 'admin/races', element: <AdminRacesPage /> },
          { path: '*', element: <Navigate to="/calendar" replace /> },
        ],
      },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
