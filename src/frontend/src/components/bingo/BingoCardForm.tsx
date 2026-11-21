import AddRounded from '@mui/icons-material/AddRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import {
  Box,
  Button,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import type {
  BingoAdminCardDto,
  BingoCardProbabilityDto,
  BingoCardTranslationDto,
  SaveBingoCardRequestDto,
} from '../../dto/bingo'

const emptyCard: SaveBingoCardRequestDto = {
  translations: { en: { description: '', explanation: null } },
  probability: 'MEDIUM',
  requiredRepeats: 1,
}

export default function BingoCardForm({
  id,
  card,
  structureEditable,
  busy,
  onSave,
  onDelete,
  onCancel,
  onStateChange,
  hasProgressChanges,
  onDiscardProgress,
}: {
  id?: string
  card?: BingoAdminCardDto
  structureEditable: boolean
  busy: boolean
  onSave: (request: SaveBingoCardRequestDto, onSaved: () => void) => void
  onDelete?: () => void
  onCancel?: () => void
  onStateChange?: (state: { dirty: boolean; valid: boolean }) => void
  hasProgressChanges?: boolean
  onDiscardProgress?: () => void
}) {
  const initial = card
    ? {
        translations: card.translations,
        probability: card.probability,
        requiredRepeats: card.requiredRepeats,
      }
    : emptyCard
  const [draft, setDraft] = useState<SaveBingoCardRequestDto | null>(null)
  const [language, setLanguage] = useState('')
  const [languageError, setLanguageError] = useState('')
  const values = draft ?? initial
  const valid =
    !!values.translations.en?.description?.trim() &&
    Number.isInteger(values.requiredRepeats) &&
    values.requiredRepeats > 0 &&
    values.requiredRepeats >= (card?.completedRepeats ?? 0)

  useEffect(() => {
    onStateChange?.({ dirty: draft !== null, valid })
  }, [draft, onStateChange, valid])

  const changeTranslation = (code: string, field: keyof BingoCardTranslationDto, text: string) =>
    setDraft({
      ...values,
      translations: {
        ...values.translations,
        [code]: { ...values.translations[code], [field]: text },
      },
    })

  const addLanguage = () => {
    let code: string
    try {
      code = Intl.getCanonicalLocales(language.trim())[0]
      if (!code || code === 'und') throw new Error()
    } catch {
      setLanguageError('Enter a language code such as ru, de or pt-BR.')
      return
    }
    if (code in values.translations) {
      setLanguageError('This language is already added.')
      return
    }
    setDraft({
      ...values,
      translations: { ...values.translations, [code]: { description: '', explanation: null } },
    })
    setLanguage('')
    setLanguageError('')
  }

  return (
    <Stack
      id={id}
      component="form"
      aria-label={card ? 'Card details' : 'New bingo card'}
      spacing={2}
      onSubmit={(event) => {
        event.preventDefault()
        if (valid && !busy) onSave(values, () => setDraft(null))
      }}
    >
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: card
            ? 'repeat(2, minmax(0, 1fr)) 120px'
            : 'repeat(2, minmax(0, 1fr))',
          alignItems: 'center',
          gap: 2,
          maxWidth: card ? 600 : 400,
        }}
      >
        <TextField
          select
          fullWidth
          size="small"
          label="Probability"
          value={values.probability}
          disabled={busy}
          onChange={(event) =>
            setDraft({ ...values, probability: event.target.value as BingoCardProbabilityDto })
          }
        >
          <MenuItem value="LOW">Low</MenuItem>
          <MenuItem value="MEDIUM">Medium</MenuItem>
          <MenuItem value="HIGH">High</MenuItem>
        </TextField>
        <TextField
          fullWidth
          required
          size="small"
          type="number"
          label="Required repeats"
          value={values.requiredRepeats}
          disabled={busy || !structureEditable}
          slotProps={{ htmlInput: { min: Math.max(1, card?.completedRepeats ?? 0), step: 1 } }}
          onChange={(event) => setDraft({ ...values, requiredRepeats: Number(event.target.value) })}
        />
        {card && (draft || hasProgressChanges) && (
          <Button
            size="small"
            disabled={busy}
            onClick={() => {
              setDraft(null)
              onDiscardProgress?.()
            }}
          >
            Discard changes
          </Button>
        )}
      </Box>
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '48px minmax(0, 1fr) minmax(0, 1fr) 28px',
            sm: '80px minmax(0, 1fr) minmax(0, 1.5fr) 28px',
          },
          gap: { xs: 0.5, sm: 1 },
          alignItems: 'start',
          '& .MuiInputBase-input': { px: { xs: 0.75, sm: 1.25 } },
          '& .MuiInputBase-multiline': { px: { xs: 0.75, sm: 1.25 } },
          '& textarea.MuiInputBase-input': { px: 0 },
        }}
      >
        {['Language', 'Title', 'Description', ''].map((heading, index) => (
          <Typography key={index} variant="caption" color="text.secondary">
            {heading}
          </Typography>
        ))}
        {[
          'en',
          ...Object.keys(values.translations)
            .filter((code) => code !== 'en')
            .sort(),
        ].map((code) => (
          <Box
            key={code}
            role="group"
            aria-label={`Translation (${code})`}
            sx={{ display: 'contents' }}
          >
            <TextField
              size="small"
              value={code}
              disabled={busy}
              slotProps={{
                input: { readOnly: true },
                htmlInput: { 'aria-label': `Language (${code})` },
              }}
            />
            <TextField
              required={code === 'en'}
              multiline
              maxRows={4}
              size="small"
              value={values.translations[code].description ?? ''}
              disabled={busy}
              slotProps={{ htmlInput: { 'aria-label': `Title (${code})` } }}
              onChange={(event) => changeTranslation(code, 'description', event.target.value)}
            />
            <TextField
              multiline
              maxRows={4}
              size="small"
              value={values.translations[code].explanation ?? ''}
              disabled={busy}
              slotProps={{ htmlInput: { 'aria-label': `Description (${code})` } }}
              onChange={(event) => changeTranslation(code, 'explanation', event.target.value)}
            />
            {code === 'en' ? (
              <Box />
            ) : (
              <IconButton
                size="small"
                aria-label={`Remove translation ${code}`}
                disabled={busy}
                onClick={() => {
                  const translations = { ...values.translations }
                  delete translations[code]
                  setDraft({ ...values, translations })
                }}
              >
                <DeleteOutlineRounded fontSize="small" />
              </IconButton>
            )}
          </Box>
        ))}
      </Box>
      <Stack direction="row" sx={{ alignItems: 'flex-start', gap: 0.5 }}>
        <TextField
          size="small"
          label="Language code"
          placeholder="ru, de, pt-BR"
          value={language}
          disabled={busy}
          error={!!languageError}
          helperText={languageError}
          sx={{ width: 160 }}
          onChange={(event) => {
            setLanguage(event.target.value)
            setLanguageError('')
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              if (!busy && language.trim()) addLanguage()
            }
          }}
        />
        <Tooltip title="Add language">
          <span>
            <IconButton
              aria-label="Add language"
              disabled={busy || !language.trim()}
              onClick={addLanguage}
            >
              <AddRounded />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
      {(!card || onCancel || (card && structureEditable && onDelete)) && (
        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
          {!card && (
            <Button
              type="submit"
              variant="contained"
              size="small"
              startIcon={<SaveRounded />}
              loading={busy}
              disabled={busy || !valid}
            >
              Create card
            </Button>
          )}
          {onCancel && (
            <Button size="small" disabled={busy} onClick={onCancel}>
              Cancel
            </Button>
          )}
          {card && structureEditable && onDelete && (
            <Button
              color="error"
              size="small"
              startIcon={<DeleteOutlineRounded />}
              disabled={busy}
              onClick={onDelete}
              sx={{ ml: 'auto' }}
            >
              Delete card
            </Button>
          )}
        </Stack>
      )}
    </Stack>
  )
}
