import { Alert, Container, Stack, Typography } from '@mui/material'
import { CURRENT_SEASON } from '../app/season'
import ChampionshipRulebook from '../components/rulebook/ChampionshipRulebook'
import RaceRulebook from '../components/rulebook/RaceRulebook'
import RulebookLinks from '../components/rulebook/RulebookLinks'
import { useLeague } from '../context/league'
import { useChampionship } from '../queries/championship'

export default function RulebookPage() {
  const { profile, selectedLeague, selectedSeason } = useLeague()
  const championship = useChampionship(profile.userId, selectedLeague?.leagueId, selectedSeason)
  const rulebookSeason =
    selectedSeason ?? Math.max(CURRENT_SEASON, selectedLeague?.firstSeason ?? CURRENT_SEASON)

  return (
    <Container
      component="section"
      maxWidth="xl"
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
          <>
            <RaceRulebook
              firstSeason={selectedLeague.firstSeason}
              selectedSeason={rulebookSeason}
            />
            <ChampionshipRulebook
              season={rulebookSeason}
              rulesVersion={championship.data?.rulesVersion}
            />
          </>
        ) : (
          <Alert severity="info">The rulebook will be available once you join a league.</Alert>
        )}
        <RulebookLinks />
      </Stack>
    </Container>
  )
}
