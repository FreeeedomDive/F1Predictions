import { FormControl, InputLabel, MenuItem, Select } from '@mui/material'
import { CURRENT_SEASON } from '../app/season'

export default function SeasonSelector() {
  return (
    <FormControl fullWidth size="small" disabled>
      <InputLabel id="season-selector-label">Season</InputLabel>
      <Select<number>
        labelId="season-selector-label"
        id="season-selector"
        label="Season"
        value={CURRENT_SEASON}
      >
        <MenuItem value={CURRENT_SEASON}>{CURRENT_SEASON}</MenuItem>
      </Select>
    </FormControl>
  )
}
