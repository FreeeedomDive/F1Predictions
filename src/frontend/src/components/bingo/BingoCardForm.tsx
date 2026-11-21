import AddRounded from '@mui/icons-material/AddRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import SaveRounded from '@mui/icons-material/SaveRounded'
import {
  Box,
  Button,
  Divider,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
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
  card,
  structureEditable,
  busy,
  onSave,
  onDelete,
  onCancel,
}: {
  card?: BingoAdminCardDto
  structureEditable: boolean
  busy: boolean
  onSave: (request: SaveBingoCardRequestDto, onSaved: () => void) => void
  onDelete?: () => void
  onCancel?: () => void
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
      component="form"
      aria-label={card ? 'Card details' : 'New bingo card'}
      spacing={2}
      onSubmit={(event) => {
        event.preventDefault()
        if (valid && !busy && (!card || draft)) onSave(values, () => setDraft(null))
      }}
    >
      <TextField
        fullWidth
        required
        multiline
        size="small"
        label="English description"
        value={values.translations.en?.description ?? ''}
        disabled={busy}
        onChange={(event) => changeTranslation('en', 'description', event.target.value)}
      />
      <TextField
        fullWidth
        multiline
        size="small"
        label="English explanation"
        value={values.translations.en?.explanation ?? ''}
        disabled={busy}
        onChange={(event) => changeTranslation('en', 'explanation', event.target.value)}
      />
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' },
          gap: 2,
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
      </Box>
      <Divider />
      <Typography component="h3" variant="subtitle2">
        Other languages (optional)
      </Typography>
      {Object.keys(values.translations)
        .filter((code) => code !== 'en')
        .sort()
        .map((code) => (
          <Stack key={code} spacing={1.5}>
            <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
              <Typography variant="subtitle2">{code}</Typography>
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
            </Stack>
            <TextField
              fullWidth
              multiline
              size="small"
              label={`Description (${code})`}
              value={values.translations[code].description ?? ''}
              disabled={busy}
              onChange={(event) => changeTranslation(code, 'description', event.target.value)}
            />
            <TextField
              fullWidth
              multiline
              size="small"
              label={`Explanation (${code})`}
              value={values.translations[code].explanation ?? ''}
              disabled={busy}
              onChange={(event) => changeTranslation(code, 'explanation', event.target.value)}
            />
          </Stack>
        ))}
      <Stack direction="row" sx={{ alignItems: 'flex-start', gap: 1 }}>
        <TextField
          fullWidth
          size="small"
          label="Language code"
          placeholder="ru, de, pt-BR"
          value={language}
          disabled={busy}
          error={!!languageError}
          helperText={languageError}
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
        <Button
          startIcon={<AddRounded />}
          disabled={busy || !language.trim()}
          onClick={addLanguage}
          sx={{ flexShrink: 0 }}
        >
          Add language
        </Button>
      </Stack>
      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
        <Button
          type="submit"
          variant="contained"
          size="small"
          startIcon={<SaveRounded />}
          loading={busy}
          disabled={busy || !valid || (!!card && !draft)}
        >
          {card ? 'Save card' : 'Create card'}
        </Button>
        {card && draft && (
          <Button size="small" disabled={busy} onClick={() => setDraft(null)}>
            Discard changes
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
    </Stack>
  )
}
