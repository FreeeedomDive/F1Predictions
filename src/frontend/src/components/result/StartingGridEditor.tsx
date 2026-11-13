import SaveRounded from '@mui/icons-material/SaveRounded'
import { Button, Chip, Paper, Stack, Typography } from '@mui/material'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import ResultClassificationTable from './ResultClassificationTable'

export default function StartingGridEditor({
  grid,
  savedGrid,
  teams,
  positionDriver,
  hasDraft,
  disabled,
  saving,
  dragging,
  onChange,
  onSave,
  onDiscard,
  onDraggingChange,
}: {
  grid: string[]
  savedGrid: string[]
  teams: RaceLineupTeamDto[]
  positionDriver?: string | null
  hasDraft: boolean
  disabled: boolean
  saving: boolean
  dragging: boolean
  onChange: (grid: string[]) => void
  onSave: () => void
  onDiscard: () => void
  onDraggingChange: (dragging: boolean) => void
}) {
  const dirty = JSON.stringify(grid) !== JSON.stringify(savedGrid)

  return (
    <Paper variant="outlined" sx={{ minWidth: 0, borderRadius: 2 }}>
      <Stack
        direction="row"
        sx={{
          p: 2,
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 1,
        }}
      >
        <Typography sx={{ fontWeight: 600 }}>Starting grid</Typography>
        <Chip
          size="small"
          variant="outlined"
          color={dirty ? 'warning' : 'default'}
          label={savedGrid.length === 0 ? 'Not saved' : dirty ? 'Unsaved changes' : 'Saved'}
        />
      </Stack>
      <ResultClassificationTable
        entries={grid.map((driverName) => ({ driverName, status: 'FINISHED' as const }))}
        teams={teams}
        positionDriver={positionDriver}
        disabled={disabled}
        showStatus={false}
        onChange={(entries) => onChange(entries.map((entry) => entry.driverName))}
        onDraggingChange={onDraggingChange}
      />
      <Stack direction="row" spacing={1} sx={{ p: 2, flexWrap: 'wrap', rowGap: 1 }}>
        <Button
          variant="outlined"
          size="small"
          startIcon={<SaveRounded />}
          loading={saving}
          disabled={disabled || dragging || !dirty || grid.length === 0}
          onClick={onSave}
        >
          Save grid
        </Button>
        {hasDraft && (
          <Button size="small" disabled={disabled || dragging} onClick={onDiscard}>
            Discard
          </Button>
        )}
      </Stack>
    </Paper>
  )
}
