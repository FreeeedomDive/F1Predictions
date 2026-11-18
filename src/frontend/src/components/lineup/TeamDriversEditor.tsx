import { Draggable, Droppable } from '@hello-pangea/dnd'
import DragIndicatorRounded from '@mui/icons-material/DragIndicatorRounded'
import { Box, IconButton, InputAdornment, TextField } from '@mui/material'
import type { ReactNode } from 'react'
import type { TeamDraft } from './lineupDraft'
import { isNamedDriver, maxDraftDrivers } from './lineupDraft'

export default function TeamDriversEditor({
  row,
  editable,
  disabled,
  dragSourceTeam,
  showErrors,
  duplicateDrivers,
  onChange,
}: {
  row: TeamDraft
  editable: boolean
  disabled: boolean
  dragSourceTeam?: string
  showErrors: boolean
  duplicateDrivers: ReadonlySet<string>
  onChange: (driverId: string, name: string) => void
}) {
  const occupied = row.drivers.filter(isNamedDriver)
  const atCapacity = occupied.length >= maxDraftDrivers

  function field(index: number, name: string, readOnly: boolean, id: string, handle?: ReactNode) {
    const error =
      duplicateDrivers.has(name) || duplicateDrivers.has(name.trim())
        ? 'Driver must occur only once.'
        : index === 0 && occupied.length === 0
          ? 'At least one driver is required.'
          : ''
    return (
      <TextField
        key={id}
        fullWidth
        size="small"
        label={`Driver ${index + 1}`}
        value={name}
        disabled={disabled}
        slotProps={{
          input: {
            readOnly,
            startAdornment: handle && <InputAdornment position="start">{handle}</InputAdornment>,
          },
        }}
        error={showErrors && !!error}
        helperText={showErrors ? error || undefined : undefined}
        onChange={(event) => onChange(id, event.target.value)}
      />
    )
  }

  if (!editable) {
    return (
      <Box sx={{ display: 'flex', gap: 1.5 }}>
        {occupied.map((driver, index) => field(index, driver.name, true, driver.id))}
      </Box>
    )
  }

  return (
    <Droppable
      droppableId={row.id}
      type="DRIVER"
      direction="horizontal"
      isDropDisabled={disabled || (atCapacity && !!dragSourceTeam && dragSourceTeam !== row.id)}
    >
      {(list, snapshot) => (
        <Box
          ref={list.innerRef}
          {...list.droppableProps}
          sx={{
            display: 'flex',
            gap: 1.5,
            minWidth: 360,
            borderRadius: 1,
            ...(snapshot.isDraggingOver && {
              bgcolor: 'action.hover',
              outline: '1px dashed',
              outlineColor: 'primary.main',
            }),
          }}
        >
          {row.drivers.map((driver, index) => (
            <Draggable
              key={driver.id}
              draggableId={driver.id}
              index={index}
              isDragDisabled={disabled || !isNamedDriver(driver)}
              disableInteractiveElementBlocking
            >
              {({ innerRef, draggableProps, dragHandleProps }, drag) => (
                <Box
                  ref={innerRef}
                  {...draggableProps}
                  sx={{
                    flex: '1 1 0',
                    minWidth: 0,
                    borderRadius: 1,
                    bgcolor: drag.isDragging ? 'background.paper' : undefined,
                  }}
                >
                  {field(
                    index,
                    driver.name,
                    false,
                    driver.id,
                    dragHandleProps && (
                      <IconButton
                        {...dragHandleProps}
                        size="small"
                        edge="start"
                        aria-label={`Move driver ${driver.name}`}
                        sx={{ cursor: drag.isDragging ? 'grabbing' : 'grab' }}
                      >
                        <DragIndicatorRounded fontSize="small" />
                      </IconButton>
                    ),
                  )}
                </Box>
              )}
            </Draggable>
          ))}
          {list.placeholder}
        </Box>
      )}
    </Droppable>
  )
}
