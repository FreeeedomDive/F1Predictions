import { DragDropContext, Draggable, Droppable } from '@hello-pangea/dnd'
import type { DropResult } from '@hello-pangea/dnd'
import DragIndicatorRounded from '@mui/icons-material/DragIndicatorRounded'
import {
  IconButton,
  MenuItem,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { RaceLineupTeamDto } from '../../dto/raceLineup'
import type { RaceClassificationEntryDto, RaceDriverStatusDto } from '../../dto/raceResult'
import { DriverLabel } from '../prediction/DriverSelect'

const statusLabels: Record<RaceDriverStatusDto, string> = {
  FINISHED: 'Finished',
  DNF: 'DNF',
  DNS: 'DNS',
  DQ: 'DQ',
}

export default function ResultClassificationTable({
  entries,
  teams,
  positionDriver,
  disabled,
  showStatus = true,
  onChange,
  onDraggingChange,
}: {
  entries: RaceClassificationEntryDto[]
  teams: RaceLineupTeamDto[]
  positionDriver?: string | null
  disabled: boolean
  showStatus?: boolean
  onChange: (entries: RaceClassificationEntryDto[]) => void
  onDraggingChange: (dragging: boolean) => void
}) {
  function moveDriver(result: DropResult) {
    onDraggingChange(false)
    if (!result.destination || result.source.index === result.destination.index) return
    const updated = [...entries]
    const [moved] = updated.splice(result.source.index, 1)
    updated.splice(result.destination.index, 0, moved)
    onChange(updated)
  }

  function changeStatus(driverName: string, status: RaceDriverStatusDto) {
    onChange(
      entries.map((entry) => (entry.driverName === driverName ? { ...entry, status } : entry)),
    )
  }

  return (
    <DragDropContext onDragStart={() => onDraggingChange(true)} onDragEnd={moveDriver}>
      <TableContainer>
        <Table
          size="small"
          aria-label={showStatus ? 'Race result classification' : 'Starting grid'}
          sx={{
            minWidth: showStatus ? 400 : 280,
            tableLayout: 'fixed',
            '& .MuiTableCell-root': { borderColor: 'divider', boxSizing: 'border-box' },
          }}
        >
          <colgroup>
            <col style={{ width: 40 }} />
            <col style={{ width: 40 }} />
            <col />
            {showStatus && <col style={{ width: 116 }} />}
          </colgroup>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 40, px: 0.5 }} />
              <TableCell sx={{ width: 40, px: 0.5, fontSize: '0.75rem' }}>Pos.</TableCell>
              <TableCell sx={{ fontSize: '0.75rem' }}>Driver</TableCell>
              {showStatus && (
                <TableCell align="right" sx={{ fontSize: '0.75rem', width: 116 }}>
                  Status
                </TableCell>
              )}
            </TableRow>
          </TableHead>
          <Droppable droppableId={showStatus ? 'race-result-classification' : 'starting-grid'}>
            {(provided) => (
              <TableBody ref={provided.innerRef} {...provided.droppableProps}>
                {entries.map((entry, index) => (
                  <Draggable
                    key={entry.driverName}
                    draggableId={entry.driverName}
                    index={index}
                    isDragDisabled={disabled}
                  >
                    {(row, snapshot) => (
                      <TableRow
                        ref={row.innerRef}
                        {...row.draggableProps}
                        style={
                          snapshot.isDragging
                            ? {
                                ...row.draggableProps.style,
                                display: 'table',
                                tableLayout: 'fixed',
                              }
                            : row.draggableProps.style
                        }
                        sx={{
                          bgcolor: snapshot.isDragging
                            ? 'background.paper'
                            : entry.status === 'DNF'
                              ? 'rgba(255, 167, 38, 0.07)'
                              : showStatus && index === 9
                                ? 'rgba(165, 180, 252, 0.08)'
                                : 'transparent',
                          '&:hover': { bgcolor: 'rgba(255, 255, 255, 0.05)' },
                        }}
                      >
                        <TableCell sx={{ p: 0.5, width: 40 }}>
                          <IconButton
                            {...row.dragHandleProps}
                            size="small"
                            disabled={disabled}
                            aria-label={`Move ${entry.driverName}`}
                            sx={{ cursor: disabled ? 'default' : 'grab', color: 'text.disabled' }}
                          >
                            <DragIndicatorRounded fontSize="small" />
                          </IconButton>
                        </TableCell>
                        <TableCell sx={{ p: 0.5, width: 40 }}>
                          <Typography
                            variant="body2"
                            sx={{
                              fontVariantNumeric: 'tabular-nums',
                              color: showStatus && index === 9 ? 'primary.main' : 'text.secondary',
                              fontWeight: showStatus && index === 9 ? 700 : 400,
                            }}
                          >
                            {index + 1}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ py: 0.5, px: 1 }}>
                          <DriverLabel
                            name={entry.driverName}
                            teams={teams}
                            highlighted={entry.driverName === positionDriver}
                          />
                        </TableCell>
                        {showStatus && (
                          <TableCell align="right" sx={{ py: 0.25, px: 1, width: 116 }}>
                            <Select<RaceDriverStatusDto>
                              size="small"
                              value={entry.status}
                              disabled={disabled}
                              renderValue={(status) => statusLabels[status]}
                              inputProps={{ 'aria-label': `Status for ${entry.driverName}` }}
                              onChange={(event) =>
                                changeStatus(
                                  entry.driverName,
                                  event.target.value as RaceDriverStatusDto,
                                )
                              }
                              sx={{
                                height: 28,
                                minWidth: 100,
                                fontSize: '0.75rem',
                                color:
                                  entry.status === 'DNF'
                                    ? 'warning.main'
                                    : entry.status === 'DQ'
                                      ? 'error.main'
                                      : 'text.secondary',
                              }}
                            >
                              {Object.entries(statusLabels).map(([status, label]) => (
                                <MenuItem key={status} value={status} sx={{ fontSize: '0.8rem' }}>
                                  {label}
                                </MenuItem>
                              ))}
                            </Select>
                          </TableCell>
                        )}
                      </TableRow>
                    )}
                  </Draggable>
                ))}
                {provided.placeholder}
              </TableBody>
            )}
          </Droppable>
        </Table>
      </TableContainer>
    </DragDropContext>
  )
}
