import AddRounded from '@mui/icons-material/AddRounded'
import ExpandLessRounded from '@mui/icons-material/ExpandLessRounded'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import RemoveRounded from '@mui/icons-material/RemoveRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  Snackbar,
  Stack,
  TableCell,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Navigate } from 'react-router'
import { bingoErrorMessage } from '../../api/bingo'
import { isUnauthorized } from '../../api/session'
import type { BingoAdminCardDto } from '../../dto/bingo'
import { useUpdateAdminBingo } from '../../queries/bingo'
import BingoCardForm from './BingoCardForm'

export default function BingoCardRow({
  card,
  userId,
  language,
  structureEditable,
}: {
  card: BingoAdminCardDto
  userId: string
  language: string
  structureEditable: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [draftProgress, setDraftProgress] = useState<number | null>(null)
  const [detailsState, setDetailsState] = useState({ dirty: false, valid: true })
  const update = useUpdateAdminBingo(userId)
  const progress = draftProgress ?? card.completedRepeats
  const description =
    card.translations[language]?.description || card.translations.en.description || ''
  const formId = `bingo-card-form-${card.id}`
  const saveProgress = () => {
    if (progress === card.completedRepeats) return
    update.mutate(
      { type: 'progress', cardId: card.id, completedRepeats: progress },
      { onSuccess: () => setDraftProgress(null) },
    )
  }
  if (isUnauthorized(update.error)) return <Navigate to="/auth" replace />

  return (
    <>
      <TableRow aria-label={`Card: ${description}`} sx={{ '&:hover': { bgcolor: 'action.hover' } }}>
        <TableCell sx={{ px: { xs: 1, sm: 2 }, py: 1.25, overflowWrap: 'anywhere' }}>
          <Typography variant="body2">{description}</Typography>
        </TableCell>
        <TableCell sx={{ px: 0.5, width: 120 }}>
          <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'center', gap: 0.5 }}>
            <IconButton
              size="small"
              color="error"
              aria-label={`Decrease progress for ${description}`}
              disabled={update.isPending || progress <= 0}
              onClick={() => setDraftProgress(progress - 1)}
            >
              <RemoveRounded fontSize="small" />
            </IconButton>
            <Typography
              component="output"
              variant="body2"
              aria-label={`Progress for ${description}`}
              aria-live="polite"
              sx={{
                minWidth: 40,
                textAlign: 'center',
                whiteSpace: 'nowrap',
                fontWeight: 600,
                bgcolor: 'action.selected',
                borderRadius: 1,
                px: 0.5,
                py: 0.5,
                color: progress === card.requiredRepeats ? 'success.main' : 'text.primary',
              }}
            >
              {progress} / {card.requiredRepeats}
            </Typography>
            <IconButton
              size="small"
              color="success"
              aria-label={`Increase progress for ${description}`}
              disabled={update.isPending || progress >= card.requiredRepeats}
              onClick={() => setDraftProgress(progress + 1)}
            >
              <AddRounded fontSize="small" />
            </IconButton>
          </Stack>
        </TableCell>
        <TableCell sx={{ px: { xs: 0.5, sm: 2 }, width: 72 }}>
          <Stack direction="row">
            <Tooltip title="Save changes">
              <span>
                <IconButton
                  type="submit"
                  form={formId}
                  size="small"
                  color="primary"
                  aria-label={`Save changes for ${description}`}
                  loading={update.isPending}
                  disabled={
                    update.isPending ||
                    (!detailsState.dirty && progress === card.completedRepeats) ||
                    (detailsState.dirty && !detailsState.valid)
                  }
                >
                  <SaveRounded fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title={expanded ? 'Hide editor' : 'Edit & translations'}>
              <IconButton
                size="small"
                aria-label="Edit & translations"
                aria-expanded={expanded}
                aria-controls={`bingo-card-${card.id}`}
                onClick={() => setExpanded(!expanded)}
              >
                {expanded ? <ExpandLessRounded /> : <ExpandMoreRounded />}
              </IconButton>
            </Tooltip>
          </Stack>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={3} sx={{ p: 0, borderBottom: expanded ? undefined : 0 }}>
          <Collapse in={expanded}>
            <Box
              id={`bingo-card-${card.id}`}
              component="section"
              aria-label={`Edit ${description}`}
              sx={{ p: { xs: 1, sm: 2 }, bgcolor: 'action.hover' }}
            >
              <BingoCardForm
                id={formId}
                card={card}
                structureEditable={structureEditable}
                busy={update.isPending}
                onStateChange={setDetailsState}
                hasProgressChanges={draftProgress !== null}
                onDiscardProgress={() => setDraftProgress(null)}
                onSave={(request, onSaved) => {
                  if (!detailsState.dirty) {
                    saveProgress()
                    return
                  }
                  update.mutate(
                    { type: 'update', cardId: card.id, card: request },
                    {
                      onSuccess: () => {
                        onSaved()
                        saveProgress()
                      },
                    },
                  )
                }}
                onDelete={() => setConfirmDelete(true)}
              />
            </Box>
          </Collapse>
          <Snackbar
            open={update.isError || update.isSuccess}
            autoHideDuration={6000}
            onClose={() => update.reset()}
          >
            <Alert severity={update.isError ? 'error' : 'success'} onClose={() => update.reset()}>
              {update.isError
                ? bingoErrorMessage(update.error, 'Could not save the card.')
                : 'Saved.'}
            </Alert>
          </Snackbar>
        </TableCell>
      </TableRow>
      <Dialog
        open={confirmDelete}
        onClose={update.isPending ? undefined : () => setConfirmDelete(false)}
      >
        <DialogTitle>Delete card?</DialogTitle>
        <DialogContent>
          <DialogContentText>{description}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button disabled={update.isPending} onClick={() => setConfirmDelete(false)}>
            Cancel
          </Button>
          <Button
            color="error"
            loading={update.isPending}
            onClick={() => update.mutate({ type: 'delete', cardId: card.id })}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
