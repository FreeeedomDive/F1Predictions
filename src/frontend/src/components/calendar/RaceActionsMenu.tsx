import GroupsRounded from '@mui/icons-material/GroupsRounded'
import LockOpenRounded from '@mui/icons-material/LockOpenRounded'
import LockRounded from '@mui/icons-material/LockRounded'
import MoreVertRounded from '@mui/icons-material/MoreVertRounded'
import { IconButton, ListItemIcon, Menu, MenuItem, Tooltip } from '@mui/material'
import { useId, useState } from 'react'
import { Link } from 'react-router'
import type { RaceDto } from '../../dto/race'

export default function RaceActionsMenu({
  race,
  canEditLineup,
  transitionPending,
  transitioning,
  onTransition,
}: {
  race: RaceDto
  canEditLineup: boolean
  transitionPending: boolean
  transitioning: boolean
  onTransition: (action: 'open' | 'close') => void
}) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const buttonId = useId()
  const menuId = useId()
  const action = race.status === 'SCHEDULED' ? 'open' : race.status === 'OPEN' ? 'close' : null
  const open = !!anchor

  if (!action && !canEditLineup) return null

  function closeMenu() {
    setAnchor(null)
  }

  return (
    <>
      <Tooltip title="Race actions">
        <span>
          <IconButton
            id={buttonId}
            size="small"
            aria-label={`Actions for ${race.name} (${race.type === 'SPRINT' ? 'Sprint' : 'Race'})`}
            aria-haspopup="menu"
            aria-controls={open ? menuId : undefined}
            aria-expanded={open}
            loading={transitioning}
            disabled={transitionPending && !canEditLineup}
            onClick={(event) => setAnchor(event.currentTarget)}
          >
            <MoreVertRounded fontSize="small" />
          </IconButton>
        </span>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={open}
        onClose={closeMenu}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ list: { id: menuId, 'aria-labelledby': buttonId } }}
      >
        {action && (
          <MenuItem
            disabled={transitionPending}
            onClick={() => {
              closeMenu()
              onTransition(action)
            }}
          >
            <ListItemIcon>
              {action === 'open' ? (
                <LockOpenRounded fontSize="small" />
              ) : (
                <LockRounded fontSize="small" />
              )}
            </ListItemIcon>
            {action === 'open' ? 'Open predictions' : 'Close predictions'}
          </MenuItem>
        )}
        {canEditLineup && (
          <MenuItem
            component={Link}
            to={`/admin/teams/${encodeURIComponent(race.id)}`}
            onClick={closeMenu}
          >
            <ListItemIcon>
              <GroupsRounded fontSize="small" />
            </ListItemIcon>
            Edit lineup
          </MenuItem>
        )}
      </Menu>
    </>
  )
}
