import { Box, CircularProgress, Container, Paper, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import Brand from './Brand'

interface Props {
  title: string
  description: string
  loading?: boolean
  children?: ReactNode
  action?: ReactNode
}

export default function StatusScreen({ title, description, loading, children, action }: Props) {
  return (
    <Container
      maxWidth="xs"
      sx={{ minHeight: '100dvh', display: 'grid', alignItems: 'center', py: 4 }}
    >
      <Paper variant="outlined" sx={{ p: { xs: 3, sm: 4 } }}>
        <Stack spacing={3}>
          <Brand />
          <Box>
            <Typography component="h1" variant="h5" sx={{ fontWeight: 650, mb: 1 }}>
              {title}
            </Typography>
            <Typography color="text.secondary" variant="body2" sx={{ lineHeight: 1.7 }}>
              {description}
            </Typography>
          </Box>
          {loading && <CircularProgress size={28} aria-label="Loading" />}
          {children}
          {action}
        </Stack>
      </Paper>
    </Container>
  )
}
