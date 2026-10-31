import SportsMotorsportsRounded from '@mui/icons-material/SportsMotorsportsRounded'
import { Stack, Typography } from '@mui/material'

export default function Brand() {
  return (
    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
      <SportsMotorsportsRounded sx={{ color: '#ff6b6b', fontSize: 32 }} />
      <Typography
        component="span"
        sx={{ fontWeight: 750, fontSize: '1.05rem', letterSpacing: 0.2 }}
      >
        F1 Predictions
      </Typography>
    </Stack>
  )
}
