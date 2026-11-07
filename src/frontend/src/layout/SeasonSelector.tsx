import { FormControl, InputLabel, MenuItem, Select } from '@mui/material'

export default function SeasonSelector({
  seasons,
  value,
  onChange,
}: {
  seasons: number[]
  value: number | null
  onChange: (season: number) => void
}) {
  return (
    <FormControl fullWidth size="small" disabled={seasons.length <= 1}>
      <InputLabel id="season-selector-label" shrink>
        Season
      </InputLabel>
      <Select<number | ''>
        labelId="season-selector-label"
        id="season-selector"
        label="Season"
        displayEmpty
        value={value ?? ''}
        onChange={(event) => onChange(Number(event.target.value))}
      >
        {seasons.length === 0 && (
          <MenuItem value="" disabled>
            No seasons available
          </MenuItem>
        )}
        {seasons.map((season) => (
          <MenuItem key={season} value={season}>
            {season}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}
