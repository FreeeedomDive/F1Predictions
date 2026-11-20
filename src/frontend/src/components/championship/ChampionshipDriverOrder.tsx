import { DragDropContext, Draggable, Droppable } from '@hello-pangea/dnd'
import type { DropResult } from '@hello-pangea/dnd'
import DragIndicatorRounded from '@mui/icons-material/DragIndicatorRounded'
import {
  Box,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material'
import type { ReactNode } from 'react'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import { DriverLabel } from '../prediction/DriverSelect'
import { championshipTableSx, pointsCell, positionCell } from './championshipTableStyles'

export default function ChampionshipDriverOrder({
  label,
  drivers,
  teams,
  currentDrivers,
  points = [],
  disabled = false,
  onChange,
}: {
  label: string
  drivers: string[]
  teams: RaceLineupTeamDto[]
  currentDrivers?: string[]
  points?: number[]
  disabled?: boolean
  onChange?: (drivers: string[]) => void
}) {
  function moveDriver({ source, destination }: DropResult) {
    if (!onChange || !destination || source.index === destination.index) return
    const reordered = [...drivers]
    const [driver] = reordered.splice(source.index, 1)
    reordered.splice(destination.index, 0, driver)
    onChange(reordered)
  }

  function driverCells(driver: string, index: number, dragHandle?: ReactNode) {
    const score = points[index] ?? 0
    return (
      <>
        <TableCell sx={{ ...positionCell, color: 'text.secondary' }}>
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            {dragHandle}
            <Box component="span" sx={{ ml: 'auto' }}>
              {index + 1}
            </Box>
          </Box>
        </TableCell>
        <TableCell title={driver}>
          <DriverLabel
            name={driver}
            teams={teams}
            showUnknownTeam
            formerTeam={currentDrivers !== undefined && !currentDrivers.includes(driver)}
          />
        </TableCell>
        {points.length > 0 && (
          <TableCell align="right" sx={pointsCell}>
            {score > 0 && (
              <Chip label={`+${score}`} size="small" color="success" sx={{ fontWeight: 700 }} />
            )}
          </TableCell>
        )}
      </>
    )
  }

  const table = (
    <TableContainer>
      <Table size="small" aria-label={label} sx={championshipTableSx}>
        <TableHead>
          <TableRow>
            <TableCell align="right" sx={positionCell}>
              Pos.
            </TableCell>
            <TableCell>Driver</TableCell>
            {points.length > 0 && (
              <TableCell align="right" sx={pointsCell}>
                Points
              </TableCell>
            )}
          </TableRow>
        </TableHead>
        {onChange ? (
          <Droppable droppableId="championship-driver-order" isDropDisabled={disabled}>
            {(body) => (
              <TableBody ref={body.innerRef} {...body.droppableProps}>
                {drivers.map((driver, index) => (
                  <Draggable
                    key={driver}
                    draggableId={driver}
                    index={index}
                    isDragDisabled={disabled}
                  >
                    {(row, snapshot) => (
                      <TableRow
                        ref={row.innerRef}
                        {...row.draggableProps}
                        hover
                        style={{
                          ...row.draggableProps.style,
                          ...(snapshot.isDragging && { display: 'table', tableLayout: 'fixed' }),
                        }}
                        sx={{ bgcolor: snapshot.isDragging ? 'background.paper' : undefined }}
                      >
                        {driverCells(
                          driver,
                          index,
                          <Box
                            {...row.dragHandleProps}
                            aria-label={`Move ${driver}`}
                            sx={{
                              display: 'flex',
                              cursor: disabled ? 'default' : 'grab',
                              color: 'text.secondary',
                            }}
                          >
                            <DragIndicatorRounded fontSize="small" />
                          </Box>,
                        )}
                      </TableRow>
                    )}
                  </Draggable>
                ))}
                {body.placeholder}
              </TableBody>
            )}
          </Droppable>
        ) : (
          <TableBody sx={{ '& tr:last-of-type > td': { borderBottom: 0 } }}>
            {drivers.map((driver, index) => (
              <TableRow key={`${driver}-${index}`} hover>
                {driverCells(driver, index)}
              </TableRow>
            ))}
          </TableBody>
        )}
      </Table>
    </TableContainer>
  )

  return onChange ? <DragDropContext onDragEnd={moveDriver}>{table}</DragDropContext> : table
}
