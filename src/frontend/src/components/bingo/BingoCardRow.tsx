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
  structureEditable,
}: {
  card: BingoAdminCardDto
  userId: string
  structureEditable: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [draftProgress, setDraftProgress] = useState<number | null>(null)
  const update = useUpdateAdminBingo(userId)
  const progress = draftProgress ?? card.completedRepeats
  const description = card.translations.en.description ?? ''
  if (isUnauthorized(update.error)) return <Navigate to="/auth" replace />

  return (
    <>
      <TableRow aria-label={`Card: ${description}`} sx={{ '&:hover': { bgcolor: 'action.hover' } }}>
        <TableCell sx={{ px: { xs: 1, sm: 2 }, py: 1.5, overflowWrap: 'anywhere' }}>
          <Typography variant="body2">{description}</Typography>
          {card.translations.en.explanation && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              {card.translations.en.explanation}
            </Typography>
          )}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
            Probability: {card.probability.toLowerCase()}
          </Typography>
          <Button
            size="small"
            endIcon={expanded ? <ExpandLessRounded /> : <ExpandMoreRounded />}
            aria-expanded={expanded}
            aria-controls={`bingo-card-${card.id}`}
            onClick={() => setExpanded(!expanded)}
            sx={{ px: 0, mt: 0.5, textAlign: 'left', justifyContent: 'flex-start' }}
          >
            Edit & translations
          </Button>
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
        <TableCell sx={{ px: { xs: 0.5, sm: 2 }, width: 40 }}>
          <Tooltip title="Save progress">
            <span>
              <IconButton
                size="small"
                color="primary"
                aria-label={`Save progress for ${description}`}
                loading={update.isPending && update.variables.type === 'progress'}
                disabled={update.isPending || progress === card.completedRepeats}
                onClick={() =>
                  update.mutate(
                    { type: 'progress', cardId: card.id, completedRepeats: progress },
                    { onSuccess: () => setDraftProgress(null) },
                  )
                }
              >
                <SaveRounded fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={3} sx={{ p: 0, borderBottom: expanded ? undefined : 0 }}>
          <Collapse in={expanded}>
            <Box
              id={`bingo-card-${card.id}`}
              component="section"
              aria-label={`Edit ${description}`}
              sx={{ p: 2, bgcolor: 'action.hover' }}
            >
              <BingoCardForm
                card={card}
                structureEditable={structureEditable}
                busy={update.isPending}
                onSave={(request, onSaved) =>
                  update.mutate(
                    { type: 'update', cardId: card.id, card: request },
                    { onSuccess: onSaved },
                  )
                }
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
