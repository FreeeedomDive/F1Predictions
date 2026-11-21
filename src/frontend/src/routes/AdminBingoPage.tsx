import AddRounded from '@mui/icons-material/AddRounded'
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import {
  Alert,
  Button,
  Chip,
  Container,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Paper,
  Skeleton,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Navigate } from 'react-router'
import { bingoErrorMessage } from '../api/bingo'
import { isUnauthorized } from '../api/session'
import BingoCardForm from '../components/bingo/BingoCardForm'
import BingoCardRow from '../components/bingo/BingoCardRow'
import { useLeague } from '../context/league'
import type { BingoAdminDetailsDto } from '../dto/bingo'
import { adminBingoKey, useAdminBingo, useUpdateAdminBingo } from '../queries/bingo'

function BingoEditor({ state, userId }: { state: BingoAdminDetailsDto; userId: string }) {
  const [adding, setAdding] = useState(false)
  const [language, setLanguage] = useState('en')
  const update = useUpdateAdminBingo(userId)
  const languages = [
    'en',
    ...Array.from(new Set(state.data.flatMap((card) => Object.keys(card.translations))))
      .filter((code) => code !== 'en')
      .sort(),
  ]
  const selectedLanguage = languages.includes(language) ? language : 'en'
  if (isUnauthorized(update.error)) return <Navigate to="/auth" replace />

  return (
    <Stack spacing={2}>
      <Stack direction="row" sx={{ flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>
        <Chip variant="outlined" label={`${state.data.length} / 25 cards`} sx={{ height: 40 }} />
        {state.data.length > 0 && (
          <TextField
            select
            size="small"
            label="Language"
            value={selectedLanguage}
            sx={{ minWidth: 110 }}
            onChange={(event) => setLanguage(event.target.value)}
          >
            {languages.map((code) => (
              <MenuItem key={code} value={code}>
                {code}
              </MenuItem>
            ))}
          </TextField>
        )}
        {state.structureEditable && (
          <Button
            variant="contained"
            size="small"
            startIcon={<AddRounded />}
            disabled={update.isPending || state.data.length >= 25}
            onClick={() => setAdding(true)}
          >
            Add card
          </Button>
        )}
        {state.canCopyPrevious && (
          <Button
            size="small"
            startIcon={<ContentCopyRounded />}
            loading={update.isPending}
            onClick={() => update.mutate({ type: 'copyPrevious' })}
          >
            Copy cards from {state.season - 1}
          </Button>
        )}
      </Stack>
      {state.data.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Stack spacing={1}>
            <Typography component="h2" variant="h6">
              No bingo cards yet
            </Typography>
            <Typography color="text.secondary" variant="body2">
              Add 25 cards to make bingo available. Each card needs an English description; other
              languages are optional.
            </Typography>
          </Stack>
        </Paper>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label="Bingo cards">
            <TableHead>
              <TableRow>
                <TableCell sx={{ px: { xs: 1, sm: 2 } }}>Card</TableCell>
                <TableCell align="center">Progress</TableCell>
                <TableCell aria-label="Actions" />
              </TableRow>
            </TableHead>
            <TableBody>
              {state.data.map((card) => (
                <BingoCardRow
                  key={card.id}
                  card={card}
                  userId={userId}
                  language={selectedLanguage}
                  structureEditable={state.structureEditable}
                />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <Dialog
        open={adding}
        fullWidth
        maxWidth="md"
        onClose={update.isPending ? undefined : () => setAdding(false)}
      >
        <DialogTitle>Add bingo card</DialogTitle>
        <DialogContent>
          {adding && (
            <Stack sx={{ pt: 1 }}>
              <BingoCardForm
                structureEditable={state.structureEditable}
                busy={update.isPending}
                onCancel={() => setAdding(false)}
                onSave={(card, onSaved) =>
                  update.mutate(
                    { type: 'create', card },
                    {
                      onSuccess: () => {
                        onSaved()
                        setAdding(false)
                      },
                    },
                  )
                }
              />
            </Stack>
          )}
        </DialogContent>
      </Dialog>
      <Snackbar
        open={update.isError || update.isSuccess}
        autoHideDuration={6000}
        onClose={() => update.reset()}
      >
        <Alert severity={update.isError ? 'error' : 'success'} onClose={() => update.reset()}>
          {update.isError
            ? bingoErrorMessage(update.error, 'Could not update bingo.')
            : update.variables?.type === 'copyPrevious'
              ? 'Cards copied. Progress starts at zero.'
              : 'Card created.'}
        </Alert>
      </Snackbar>
    </Stack>
  )
}

export default function AdminBingoPage() {
  const { profile } = useLeague()
  const bingo = useAdminBingo(profile.userId)
  const client = useQueryClient()
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
              Bingo administration
            </Typography>
            {bingo.data && (
              <Typography variant="body2" color="text.secondary">
                Current season {bingo.data.season} · Shared across all leagues.
              </Typography>
            )}
          </Stack>
          <Tooltip title="Refresh bingo">
            <span>
              <IconButton
                aria-label="Refresh bingo"
                disabled={bingo.isFetching}
                onClick={() =>
                  void client.invalidateQueries({ queryKey: adminBingoKey(profile.userId) })
                }
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {bingo.isPending ? (
          <Skeleton variant="rounded" height={180} aria-label="Loading bingo" />
        ) : !bingo.data ? (
          <Alert
            severity="error"
            action={<Button onClick={() => void bingo.refetch()}>Retry</Button>}
          >
            {bingoErrorMessage(bingo.error, 'Could not load bingo cards.')}
          </Alert>
        ) : (
          <>
            {bingo.isRefetchError && (
              <Alert severity="warning">
                {bingoErrorMessage(
                  bingo.error,
                  'Could not refresh bingo cards. Your edits have been kept.',
                )}
              </Alert>
            )}
            <BingoEditor key={bingo.data.season} state={bingo.data} userId={profile.userId} />
          </>
        )}
      </Stack>
    </Container>
  )
}
