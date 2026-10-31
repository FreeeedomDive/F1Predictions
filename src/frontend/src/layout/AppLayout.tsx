import MenuRounded from '@mui/icons-material/MenuRounded'
import { AppBar, Box, Drawer, IconButton, Toolbar, useMediaQuery, useTheme } from '@mui/material'
import { useState } from 'react'
import { Outlet } from 'react-router'
import Brand from '../components/Brand'
import Sidebar from './Sidebar'

const SIDEBAR_WIDTH = 280

export default function AppLayout() {
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('md'))
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <>
      <title>F1 Predictions</title>
      <Box sx={{ display: 'flex', minHeight: '100dvh' }}>
        {!desktop && (
          <AppBar
            position="fixed"
            elevation={0}
            sx={{ bgcolor: '#05050b', borderBottom: 1, borderColor: 'divider' }}
          >
            <Toolbar sx={{ gap: 1 }}>
              <IconButton
                edge="start"
                aria-label="Open sidebar"
                aria-controls="app-sidebar"
                aria-expanded={sidebarOpen}
                onClick={() => setSidebarOpen(true)}
              >
                <MenuRounded />
              </IconButton>
              <Brand />
            </Toolbar>
          </AppBar>
        )}
        <Box
          component="aside"
          aria-label="Workspace sidebar"
          sx={{ width: desktop ? SIDEBAR_WIDTH : 0, flexShrink: 0 }}
        >
          <Drawer
            variant={desktop ? 'permanent' : 'temporary'}
            open={desktop || sidebarOpen}
            onClose={() => setSidebarOpen(false)}
            elevation={0}
            slotProps={{
              paper: {
                id: 'app-sidebar',
                sx: { width: SIDEBAR_WIDTH, bgcolor: '#05050b', borderColor: 'divider' },
              },
            }}
          >
            <Sidebar onClose={desktop ? undefined : () => setSidebarOpen(false)} />
          </Drawer>
        </Box>
        <Box
          component="main"
          aria-label="Application content"
          sx={{ flexGrow: 1, minWidth: 0, pt: desktop ? 0 : 8 }}
        >
          <Outlet />
        </Box>
      </Box>
    </>
  )
}
