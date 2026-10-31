import { Container, Paper, Typography } from '@mui/material'
import StatusScreen from '../components/StatusScreen'

export function AuthLoadingScreen() {
  return (
    <StatusScreen
      title="Signing you in"
      description="Checking your session and loading your leagues."
      loading
    />
  )
}

export default function AuthPage() {
  return (
    <>
      <title>Not authorized · F1 Predictions</title>
      <Container
        component="main"
        maxWidth="xs"
        sx={{ minHeight: '100dvh', display: 'grid', alignItems: 'center', py: 4 }}
      >
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <Typography sx={{ fontWeight: 700 }}>Not authorized</Typography>
        </Paper>
      </Container>
    </>
  )
}
