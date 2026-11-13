import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
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
import { useCallback, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { raceResultErrorMessage } from '../api/raceResults'
import { isUnauthorized } from '../api/session'
import AdminResultEditor from '../components/result/AdminResultEditor'
import type { ResultDraft } from '../components/result/resultDraft'
import { useLeague } from '../context/league'
import { useAdminRacePredictions } from '../queries/predictions'
import { useAdminRaceResult } from '../queries/raceResults'
import { useRights } from '../queries/rights'

export default function AdminResultsPage() {
  const { profile } = useLeague()
  const { raceId } = useParams()
  const canEditPredictions = useRights().includes('EDIT_PREDICTIONS')
  const [drafts, setDrafts] = useState<Record<string, ResultDraft | undefined>>({})
  const [busy, setBusy] = useState(false)
  const editor = useAdminRaceResult(profile.userId, raceId)
  const predictions = useAdminRacePredictions(
    profile.userId,
    canEditPredictions ? raceId : undefined,
  )
  const page = editor.data
  const changeDraft = useCallback((id: string, draft: ResultDraft | undefined) => {
    setDrafts((current) => ({ ...current, [id]: draft }))
  }, [])

  if (
    (editor.isError && isUnauthorized(editor.error)) ||
    (canEditPredictions && predictions.isError && isUnauthorized(predictions.error))
  )
    return <Navigate to="/auth" replace />

  function refresh() {
    if (raceId) {
      void editor.refetch()
      if (canEditPredictions) void predictions.refetch()
    }
  }

  return (
    <Container
      component="section"
      maxWidth="xl"
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={2}>
        <Stack direction="row" sx={{ alignItems: 'center' }}>
          <Button
            component={Link}
            to="/admin/races"
            size="small"
            startIcon={<ArrowBackRounded fontSize="small" />}
          >
            Calendar
          </Button>
        </Stack>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Result administration
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Race results{canEditPredictions ? ' and predictions' : ''}
              {page ? ` · ${page.season}` : ''}
            </Typography>
          </Stack>
          <Tooltip title="Refresh results">
            <span>
              <IconButton
                aria-label="Refresh results"
                disabled={busy || !raceId || editor.isFetching || predictions.isFetching}
                onClick={refresh}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        {!raceId ? (
          <Alert severity="info">
            Select a race in the admin calendar to enter its result or correct a finished race
            {canEditPredictions ? ', or edit predictions' : ''}.
          </Alert>
        ) : editor.isPending ? (
          <Stack spacing={1} role="status" aria-label="Loading result editor">
            <Skeleton variant="rounded" height={100} />
            <Skeleton variant="rounded" height={400} />
          </Stack>
        ) : !page ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={refresh}>
                Retry
              </Button>
            }
          >
            {raceResultErrorMessage(editor.error, 'Could not load this race result.')}
          </Alert>
        ) : (
          <>
            {editor.isError && (
              <Alert severity="warning">
                Could not refresh the result. Your local changes are still here.
              </Alert>
            )}
            <AdminResultEditor
              key={`${profile.userId}:${page.race.id}:${canEditPredictions}`}
              page={page}
              userId={profile.userId}
              draft={drafts[page.race.id]}
              busy={busy}
              canEditPredictions={canEditPredictions}
              predictions={predictions.data}
              predictionsLoading={predictions.isPending}
              predictionsError={predictions.error}
              onDraftChange={changeDraft}
              onBusyChange={setBusy}
              onPredictionsRefresh={() => {
                if (canEditPredictions) void predictions.refetch()
              }}
            />
          </>
        )}
      </Stack>
    </Container>
  )
}
