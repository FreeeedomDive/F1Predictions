import { Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { SafetyCarsCountDto } from '../../dto/prediction'
import type { SeasonStatsDto } from '../../dto/statistics'
import { StatsPanel } from './StatsCard'

const INCIDENT_COUNTS: { value: SafetyCarsCountDto; label: string }[] = [
  { value: 'ZERO', label: '0' },
  { value: 'ONE', label: '1' },
  { value: 'TWO', label: '2' },
  { value: 'THREE_PLUS', label: '3+' },
]

export default function IncidentsTable({ stats }: { stats: SeasonStatsDto }) {
  const picks = new Map(stats.safetyCarPickCounts.map((item) => [item.safetyCars, item.score]))
  const actuals = new Map(stats.safetyCarActualCounts.map((item) => [item.safetyCars, item.score]))
  const correct = new Map(
    stats.safetyCarCorrectGuesses.map((item) => [item.safetyCars, item.score]),
  )
  const hasData = picks.size + actuals.size + correct.size > 0

  return (
    <StatsPanel
      title="Incident counts"
      description="Picks and correct guesses count predictions; actuals count races. The 3+ category includes three or more incidents."
    >
      {hasData ? (
        <Table
          size="small"
          aria-label="Season incident statistics"
          sx={{
            '& .MuiTableCell-root': { px: 0.75, fontVariantNumeric: 'tabular-nums' },
            '& .MuiTableCell-head': { color: 'text.secondary' },
          }}
        >
          <TableHead>
            <TableRow>
              <TableCell>Count</TableCell>
              <TableCell align="right">Picks</TableCell>
              <TableCell align="right">Correct</TableCell>
              <TableCell align="right">Actuals</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {INCIDENT_COUNTS.map((option) => (
              <TableRow key={option.value}>
                <TableCell component="th" scope="row">
                  {option.label}
                </TableCell>
                <TableCell align="right">{picks.get(option.value) ?? 0}</TableCell>
                <TableCell align="right">{correct.get(option.value) ?? 0}</TableCell>
                <TableCell align="right">{actuals.get(option.value) ?? 0}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        <Typography variant="body2" sx={{ color: 'text.disabled' }}>
          No data yet.
        </Typography>
      )}
    </StatsPanel>
  )
}
