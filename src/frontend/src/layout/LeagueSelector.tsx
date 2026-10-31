import { FormControl, FormHelperText, InputLabel, MenuItem, Select } from '@mui/material'
import { useLeague } from '../context/league'

export default function LeagueSelector() {
  const { profile, selectedLeague, selectLeague } = useLeague()

  return (
    <FormControl fullWidth size="small" disabled={profile.leagues.length <= 1}>
      <InputLabel id="league-selector-label" shrink>
        League
      </InputLabel>
      <Select
        labelId="league-selector-label"
        id="league-selector"
        label="League"
        displayEmpty
        value={selectedLeague?.leagueId ?? ''}
        onChange={(event) => selectLeague(event.target.value)}
        sx={{ '& .MuiSelect-select': { overflow: 'hidden', textOverflow: 'ellipsis' } }}
      >
        {profile.leagues.length === 0 && (
          <MenuItem value="" disabled>
            No leagues yet
          </MenuItem>
        )}
        {profile.leagues.map((league) => (
          <MenuItem key={league.leagueId} value={league.leagueId} title={league.leagueName}>
            {league.leagueName}
          </MenuItem>
        ))}
      </Select>
      {profile.leagues.length !== 1 && (
        <FormHelperText sx={{ mx: 0 }}>
          {profile.leagues.length > 1
            ? `${profile.leagues.length} leagues available`
            : 'Run /f1 web in a Discord server to join its league.'}
        </FormHelperText>
      )}
    </FormControl>
  )
}
