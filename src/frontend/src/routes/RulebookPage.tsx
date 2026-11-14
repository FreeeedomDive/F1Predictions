import { Alert, Container, Stack, Typography } from '@mui/material'
import { CURRENT_SEASON } from '../app/season'
import RaceRulebook from '../components/rulebook/RaceRulebook'
import { useLeague } from '../context/league'

export default function RulebookPage() {
  const { selectedLeague, selectedSeason } = useLeague()

  return (
    <Container
      component="section"
      maxWidth={false}
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={3}>
        <Stack spacing={0.5}>
          <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
            F1 prediction rulebook
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {selectedLeague?.leagueName ?? 'No league selected'}
            {selectedSeason !== null && ` · ${selectedSeason}`}
          </Typography>
        </Stack>
        {selectedLeague ? (
          <RaceRulebook
            firstSeason={selectedLeague.firstSeason}
            selectedSeason={selectedSeason ?? Math.max(CURRENT_SEASON, selectedLeague.firstSeason)}
          />
        ) : (
          <Alert severity="info">The rulebook will be available once you join a league.</Alert>
        )}
      </Stack>
    </Container>
  )
}
