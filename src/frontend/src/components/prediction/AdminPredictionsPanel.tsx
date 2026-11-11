import SearchRounded from '@mui/icons-material/SearchRounded'
import {
  Alert,
  Button,
  InputAdornment,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material'
import { useState } from 'react'
import { adminPredictionErrorMessage } from '../../api/predictions'
import type { AdminPredictionDto } from '../../dto/prediction'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import AdminPredictionRow from './AdminPredictionRow'

export default function AdminPredictionsPanel({
  data,
  loading,
  error,
  userId,
  season,
  teams,
  positionDriver,
  disabled,
  onRefresh,
  onSavingChange,
  onDirtyChange,
  onSaved,
}: {
  data: AdminPredictionDto[] | undefined
  loading: boolean
  error: Error | null
  userId: string
  season: number
  teams: RaceLineupTeamDto[]
  positionDriver: string | null
  disabled: boolean
  onRefresh: () => void
  onSavingChange: (saving: boolean) => void
  onDirtyChange: (predictionId: string, dirty: boolean) => void
  onSaved: () => void
}) {
  const [search, setSearch] = useState('')
  const normalizedSearch = search.trim().toLocaleLowerCase()
  const matches = (item: AdminPredictionDto) =>
    !normalizedSearch ||
    [item.participantName, item.leagueName, item.prediction.participantId].some((value) =>
      value.toLocaleLowerCase().includes(normalizedSearch),
    )
  const visibleCount = data?.filter(matches).length ?? 0
  const additional = season >= 2024
  const columnCount = 4 + (additional ? 2 : 0) + (positionDriver ? 1 : 0)

  if (loading && data === undefined) {
    return (
      <Stack spacing={1} role="status" aria-label="Loading predictions">
        <Skeleton variant="rounded" height={56} />
        <Skeleton variant="rounded" height={400} />
      </Stack>
    )
  }

  if (error && !data) {
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={onRefresh}>
            Retry
          </Button>
        }
      >
        {adminPredictionErrorMessage(error, 'Could not load predictions for this race.')}
      </Alert>
    )
  }

  if (!data || data.length === 0) {
    return <Alert severity="info">No predictions have been submitted for this race yet.</Alert>
  }

  return (
    <Stack spacing={2}>
      {error && (
        <Alert
          severity="warning"
          action={
            <Button color="inherit" size="small" onClick={onRefresh}>
              Retry
            </Button>
          }
        >
          Could not refresh predictions. Unsaved changes are still here.
        </Alert>
      )}
      <TextField
        size="small"
        placeholder="Search by participant or league"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        sx={{ width: { xs: '100%', sm: 320 } }}
        slotProps={{
          htmlInput: { 'aria-label': 'Search predictions' },
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchRounded fontSize="small" />
              </InputAdornment>
            ),
          },
        }}
      />
      <Paper variant="outlined" sx={{ borderRadius: 2, overflow: 'hidden', minWidth: 0 }}>
        <TableContainer>
          <Table
            size="small"
            aria-label="Race predictions"
            sx={{
              minWidth: 760 + (additional ? 225 : 0) + (positionDriver ? 150 : 0),
              '& .MuiTableCell-root': { borderColor: 'divider' },
              '& .MuiTableHead-root .MuiTableCell-root': {
                color: 'text.secondary',
                fontSize: '0.75rem',
                fontWeight: 600,
                py: 1.5,
                whiteSpace: 'nowrap',
              },
            }}
          >
            <TableHead>
              <TableRow>
                <TableCell sx={{ minWidth: 180 }}>Participant</TableCell>
                <TableCell sx={{ minWidth: 175 }}>10th place</TableCell>
                <TableCell sx={{ minWidth: 280 }}>
                  {season === 2023 ? 'First DNF' : 'DNF · Nobody / 5 drivers'}
                </TableCell>
                {additional && <TableCell sx={{ minWidth: 110 }}>Incidents</TableCell>}
                {additional && <TableCell sx={{ minWidth: 140 }}>Winner's gap</TableCell>}
                {positionDriver && (
                  <TableCell sx={{ minWidth: 130 }}>Position · {positionDriver}</TableCell>
                )}
                <TableCell align="right" sx={{ minWidth: 110 }}>
                  Actions
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((item) => (
                <AdminPredictionRow
                  key={item.prediction.id}
                  data={item}
                  userId={userId}
                  season={season}
                  teams={teams}
                  positionDriver={positionDriver}
                  hidden={!matches(item)}
                  disabled={disabled}
                  onSavingChange={onSavingChange}
                  onDirtyChange={onDirtyChange}
                  onSaved={onSaved}
                />
              ))}
              {visibleCount === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={columnCount}
                    sx={{ py: 4, textAlign: 'center', color: 'text.secondary' }}
                  >
                    No participants match your search.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Stack>
  )
}
