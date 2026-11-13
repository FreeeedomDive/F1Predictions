import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import { DriverLabel } from './DriverSelect'

export default function StartingGrid({
  grid,
  teams,
  tenthPlaceDriver,
  dnfDrivers,
  positionDriver,
  predictedPosition,
}: {
  grid: string[]
  teams: RaceLineupTeamDto[]
  tenthPlaceDriver: string
  dnfDrivers: string[]
  positionDriver?: string | null
  predictedPosition?: number
}) {
  const theme = useTheme()
  const collapsible = useMediaQuery(theme.breakpoints.down('lg'))
  const driverCount = teams.reduce((count, team) => count + team.drivers.length, 0)
  const marker = { height: 19, fontSize: '0.6rem', '& .MuiChip-label': { px: 0.6 } }
  const content =
    grid.length > 0 ? (
      <TableContainer>
        <Table
          size="small"
          aria-label="Starting grid"
          sx={{
            '& td': { py: 0.5, px: 1, borderColor: 'divider' },
            '& tr:last-child td': { borderBottom: 0 },
          }}
        >
          <TableBody>
            {grid.map((name, index) => (
              <TableRow
                key={`${index}:${name}`}
                sx={{
                  bgcolor:
                    name === tenthPlaceDriver
                      ? 'rgba(165, 180, 252, 0.08)'
                      : dnfDrivers.includes(name)
                        ? 'rgba(255, 167, 38, 0.05)'
                        : 'transparent',
                }}
              >
                <TableCell sx={{ width: 30 }}>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ fontVariantNumeric: 'tabular-nums' }}
                  >
                    {index + 1}
                  </Typography>
                </TableCell>
                <TableCell>
                  <DriverLabel name={name} teams={teams} highlighted={name === positionDriver} />
                </TableCell>
                <TableCell align="right" sx={{ pl: 0 }}>
                  <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                    {name === tenthPlaceDriver && (
                      <Chip
                        size="small"
                        label="P10"
                        color="primary"
                        variant="outlined"
                        sx={marker}
                      />
                    )}
                    {name === positionDriver && (
                      <Chip
                        size="small"
                        label={predictedPosition ? `P${predictedPosition}` : 'P?'}
                        title="Predicted finishing position"
                        color="primary"
                        variant="outlined"
                        sx={marker}
                      />
                    )}
                    {dnfDrivers.includes(name) && (
                      <Chip
                        size="small"
                        label="DNF"
                        color="warning"
                        variant="outlined"
                        sx={marker}
                      />
                    )}
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    ) : (
      <Stack spacing={0.5} sx={{ px: 1.5, pb: 1.5 }}>
        <Typography variant="caption" color="text.secondary" sx={{ pb: 0.5 }}>
          The starting grid will appear after qualifying.
        </Typography>
        <Box aria-hidden="true" sx={{ opacity: 0.35 }}>
          {Array.from({ length: driverCount || 22 }, (_, index) => (
            <Stack
              key={index}
              direction="row"
              spacing={1}
              sx={{ height: 27, alignItems: 'center' }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ minWidth: 18 }}>
                {index + 1}
              </Typography>
              <Skeleton variant="rounded" animation={false} width={40} height={17} />
              <Skeleton animation={false} width={index % 2 === 0 ? '50%' : '65%'} height={22} />
            </Stack>
          ))}
        </Box>
      </Stack>
    )

  return collapsible ? (
    <Accordion
      variant="outlined"
      disableGutters
      sx={{ borderRadius: 1, '&::before': { display: 'none' } }}
    >
      <AccordionSummary expandIcon={<ExpandMoreRounded />} sx={{ minHeight: 42 }}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          Starting grid
        </Typography>
      </AccordionSummary>
      <AccordionDetails sx={{ p: 0 }}>{content}</AccordionDetails>
    </Accordion>
  ) : (
    <Paper variant="outlined" sx={{ minWidth: 0, borderRadius: 1 }}>
      <Typography variant="body2" sx={{ p: 1.5, fontWeight: 600 }}>
        Starting grid
      </Typography>
      {content}
    </Paper>
  )
}
