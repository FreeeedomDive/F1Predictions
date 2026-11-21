import { Stack, Typography } from '@mui/material'
import RulebookCard, { Rule } from './RulebookCard'

export default function BingoRulebook() {
  return (
    <Stack spacing={2} sx={{ maxWidth: 860 }}>
      <Typography component="h2" variant="h5" sx={{ fontWeight: 600 }}>
        Bingo
      </Typography>
      <RulebookCard title="Your seasonal board" badge="5 × 5">
        <Rule>Your board has 25 event cards and no free centre cell.</Rule>
        <Rule>
          On your first visit during the active season, a personal board is created automatically
          once all 25 cards are available.
        </Rule>
        <Rule>Cards and event progress are shared by all players.</Rule>
        <Rule>Cards with an info icon include an explanation of the event conditions.</Rule>
      </RulebookCard>
      <RulebookCard title="Events and bingo" badge="No race points" badgeColor="warning">
        <Rule>
          Administrators update event progress manually. If an event requires several occurrences,
          its card closes only when the completed count reaches the target.
        </Rule>
        <Rule>Bingo is a complete row, column or diagonal: 5 rows, 5 columns and 2 diagonals.</Rule>
        <Rule>
          Low, medium and high probability are informational labels. They do not change the
          completion rules.
        </Rule>
        <Rule>
          Archived seasons show only your already saved board; opening an archive does not create a
          new one. Other players’ boards are not available.
        </Rule>
      </RulebookCard>
    </Stack>
  )
}
