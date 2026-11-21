import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Button,
  Container,
  IconButton,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import { isAxiosError } from 'axios'
import { Navigate } from 'react-router'
import { bingoErrorMessage } from '../api/bingo'
import { isUnauthorized } from '../api/session'
import BingoBoard from '../components/bingo/BingoBoard'
import { useLeague } from '../context/league'
import { useOwnBingo } from '../queries/bingo'

export default function BingoPage() {
  const { profile, selectedLeague, selectedSeason } = useLeague()
  const leagueId = selectedLeague?.leagueId
  const bingo = useOwnBingo(profile.userId, leagueId, selectedSeason)
  const details = bingo.data
  const denied = isAxiosError(bingo.error) && [403, 404].includes(bingo.error.response?.status ?? 0)
  if (isUnauthorized(bingo.error)) return <Navigate to="/auth" replace />

  return (
    <Container
      component="section"
      maxWidth="xl"
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={3}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Bingo
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {selectedLeague?.leagueName ?? 'No league selected'}
              {selectedSeason !== null && ` · ${selectedSeason}`}
            </Typography>
          </Stack>
          <Tooltip title="Refresh bingo">
            <span>
              <IconButton
                aria-label="Refresh bingo"
                disabled={!leagueId || selectedSeason === null || bingo.isFetching}
                onClick={() => void bingo.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {!leagueId ? (
          <Alert severity="info">Bingo will appear once you join a league.</Alert>
        ) : selectedSeason === null ? (
          <Alert severity="info">No season is available for this league.</Alert>
        ) : bingo.isPending ? (
          <Skeleton variant="rounded" height={360} aria-label="Loading bingo board" />
        ) : bingo.isError && (!details?.board || denied) ? (
          <Alert
            severity="error"
            action={<Button onClick={() => void bingo.refetch()}>Retry</Button>}
          >
            {bingoErrorMessage(bingo.error, 'Could not load your bingo board. Please try again.')}
          </Alert>
        ) : !details?.board ? (
          <Alert severity="info">
            {details && details.season < details.activeSeason
              ? `No saved bingo board for ${details.season}. Archived seasons do not create new boards.`
              : details && details.season > details.activeSeason
                ? `Bingo is not available for ${details.season}. The active season is ${details.activeSeason}.`
                : `Bingo cards are not ready for ${selectedSeason}. Please check back later.`}
          </Alert>
        ) : (
          <>
            {bingo.isRefetchError && (
              <Alert severity="warning">Could not refresh bingo. Showing the last update.</Alert>
            )}
            {details.season < details.activeSeason && (
              <Alert severity="info">Archived season — your saved board is read-only.</Alert>
            )}
            {details.board.bingoAchieved && (
              <Alert severity="success">
                Bingo achieved! Your first completed row, column or diagonal stays recorded even if
                progress is later corrected.
              </Alert>
            )}
            <BingoBoard
              key={`${profile.userId}:${leagueId}:${details.season}:${details.board.id}`}
              board={details.board}
            />
          </>
        )}
      </Stack>
    </Container>
  )
}
