import CloseRounded from '@mui/icons-material/CloseRounded'
import CalendarMonthRounded from '@mui/icons-material/CalendarMonthRounded'
import {
  Avatar,
  Box,
  Divider,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import { NavLink } from 'react-router'
import Brand from '../components/Brand'
import { useLeague } from '../context/league'
import LeagueSelector from './LeagueSelector'
import SeasonSelector from './SeasonSelector'
import SignOutButton from './SignOutButton'

export default function Sidebar({ onClose }: { onClose?: () => void }) {
  const { selectedLeague } = useLeague()
  const name = selectedLeague?.participantName || 'F1 player'
  const initials = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => Array.from(part)[0])
    .join('')
    .toLocaleUpperCase()

  return (
    <Stack sx={{ height: '100%', p: 2.5, gap: 3 }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Brand />
        {onClose && (
          <IconButton onClick={onClose} aria-label="Close sidebar" size="small">
            <CloseRounded fontSize="small" />
          </IconButton>
        )}
      </Stack>
      <Divider />
      <Stack spacing={2}>
        <LeagueSelector />
        <SeasonSelector />
      </Stack>
      <List component="nav" disablePadding aria-label="Navigation">
        <ListItemButton
          component={NavLink}
          to="/calendar"
          onClick={onClose}
          sx={{
            borderRadius: 1,
            '&.active': { bgcolor: 'rgba(165, 180, 252, 0.1)', color: 'primary.main' },
          }}
        >
          <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>
            <CalendarMonthRounded fontSize="small" />
          </ListItemIcon>
          <ListItemText
            primary="Calendar"
            slotProps={{ primary: { sx: { fontSize: 14, fontWeight: 500 } } }}
          />
        </ListItemButton>
      </List>
      <Stack
        direction="row"
        spacing={1.5}
        sx={{ mt: 'auto', pt: 2.5, borderTop: 1, borderColor: 'divider', alignItems: 'center' }}
      >
        <Avatar sx={{ bgcolor: 'rgba(165, 180, 252, 0.14)', color: 'primary.main', fontSize: 16 }}>
          {initials}
        </Avatar>
        <Box sx={{ minWidth: 0, flexGrow: 1 }}>
          <Tooltip title={name}>
            <Typography noWrap sx={{ fontWeight: 600 }}>
              {name}
            </Typography>
          </Tooltip>
        </Box>
        <SignOutButton />
      </Stack>
    </Stack>
  )
}
