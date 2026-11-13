import AdminPanelSettingsRounded from '@mui/icons-material/AdminPanelSettingsRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  Container,
  IconButton,
  InputAdornment,
  LinearProgress,
  MenuItem,
  Paper,
  Skeleton,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Navigate } from 'react-router'
import { rightsErrorMessage } from '../api/rights'
import { isUnauthorized } from '../api/session'
import { useLeague } from '../context/league'
import { allRights, rightLabels } from '../dto/rights'
import type { RightDto } from '../dto/rights'
import { useChangeUserRight, useUserRights } from '../queries/rights'

export default function AdminRightsPage() {
  const { profile } = useLeague()
  const users = useUserRights(profile.userId)
  const change = useChangeUserRight()
  const [search, setSearch] = useState('')
  const [rightFilter, setRightFilter] = useState<RightDto | ''>('')
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [preferredRight, setPreferredRight] = useState<RightDto | ''>('')
  const [notice, setNotice] = useState<{ severity: 'success' | 'error'; message: string } | null>(
    null,
  )
  const data = users.data ?? []
  const selectedUser = data.find((user) => user.userId === selectedUserId) ?? null
  const availableRights = allRights.filter((right) => !selectedUser?.rights.includes(right))
  const grantRight =
    availableRights.find((right) => right === preferredRight) ?? availableRights[0] ?? ''
  const normalizedSearch = search.trim().toLocaleLowerCase()
  const visibleUsers = data.filter(
    (user) =>
      user.rights.length > 0 &&
      (!rightFilter || user.rights.includes(rightFilter)) &&
      (!normalizedSearch ||
        [...user.names, user.userId].some((name) =>
          name.toLocaleLowerCase().includes(normalizedSearch),
        )),
  )

  if (isUnauthorized(users.error) || isUnauthorized(change.error))
    return <Navigate to="/auth" replace />

  async function update(userId: string, right: RightDto, grant: boolean) {
    setNotice(null)
    try {
      await change.mutateAsync({ userId, right, grant })
      setNotice({
        severity: 'success',
        message: `${rightLabels[right]} ${grant ? 'granted' : 'revoked'}.`,
      })
    } catch (error) {
      setNotice({
        severity: 'error',
        message: rightsErrorMessage(
          error,
          `Could not ${grant ? 'grant' : 'revoke'} this right. Please try again.`,
        ),
      })
    }
  }

  return (
    <Container
      component="section"
      maxWidth="xl"
      sx={{ py: { xs: 3, md: 4 }, px: { xs: 2, md: 4 } }}
    >
      <Stack spacing={3}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
        >
          <Stack spacing={0.5}>
            <Typography component="h1" variant="h4" sx={{ fontWeight: 650 }}>
              Rights administration
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Global user permissions across all leagues.
            </Typography>
          </Stack>
          <Tooltip title="Refresh rights">
            <span>
              <IconButton
                aria-label="Refresh rights"
                disabled={users.isFetching || change.isPending}
                onClick={() => void users.refetch()}
              >
                <RefreshRounded />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>

        {users.isPending ? (
          <Stack spacing={1} role="status" aria-label="Loading rights">
            <Skeleton variant="rounded" height={140} />
            <Skeleton variant="rounded" height={280} />
          </Stack>
        ) : users.isError && !users.data ? (
          <Alert
            severity="error"
            action={
              <Button color="inherit" onClick={() => void users.refetch()}>
                Retry
              </Button>
            }
          >
            {rightsErrorMessage(users.error, 'Could not load user rights. Please try again.')}
          </Alert>
        ) : (
          <>
            {users.isError && (
              <Alert
                severity="warning"
                action={
                  <Button color="inherit" onClick={() => void users.refetch()}>
                    Retry
                  </Button>
                }
              >
                {rightsErrorMessage(
                  users.error,
                  'Could not refresh user rights. The list may be out of date.',
                )}
              </Alert>
            )}
            <Paper variant="outlined" sx={{ p: 2.5 }}>
              <Stack spacing={2}>
                <Typography component="h2" variant="h6">
                  Grant a right
                </Typography>
                <Stack
                  direction={{ xs: 'column', md: 'row' }}
                  spacing={2}
                  sx={{ alignItems: { md: 'flex-start' } }}
                >
                  <Autocomplete
                    options={data}
                    value={selectedUser}
                    getOptionKey={(user) => user.userId}
                    getOptionLabel={(user) =>
                      user.names.length > 0
                        ? `${user.names.join(' / ')} · ${user.userId}`
                        : user.userId
                    }
                    isOptionEqualToValue={(option, value) => option.userId === value.userId}
                    onChange={(_, user) => setSelectedUserId(user?.userId ?? null)}
                    disabled={change.isPending}
                    noOptionsText="No matching users"
                    sx={{ flex: 1, width: { xs: '100%', md: 'auto' }, minWidth: { md: 300 } }}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label="User"
                        size="small"
                        helperText="Search all known users, including those without rights."
                      />
                    )}
                  />
                  <TextField
                    select
                    label="Right"
                    size="small"
                    value={selectedUser ? grantRight : ''}
                    onChange={(event) => setPreferredRight(event.target.value as RightDto)}
                    disabled={!selectedUser || availableRights.length === 0 || change.isPending}
                    sx={{ width: { xs: '100%', md: 240 } }}
                  >
                    <MenuItem value="" disabled>
                      Select a right
                    </MenuItem>
                    {availableRights.map((right) => (
                      <MenuItem key={right} value={right}>
                        {rightLabels[right]}
                      </MenuItem>
                    ))}
                  </TextField>
                  <Button
                    variant="contained"
                    disabled={!selectedUser || !grantRight || change.isPending}
                    onClick={() => {
                      if (selectedUser && grantRight)
                        void update(selectedUser.userId, grantRight, true)
                    }}
                  >
                    Grant
                  </Button>
                </Stack>
                {selectedUser && availableRights.length === 0 && (
                  <Alert severity="info">This user already has all rights.</Alert>
                )}
                {change.isPending && (
                  <Stack spacing={1} role="status">
                    <LinearProgress aria-label="Saving rights" />
                    <Typography variant="caption" color="text.secondary">
                      Saving rights…
                    </Typography>
                  </Stack>
                )}
              </Stack>
            </Paper>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Search assigned users"
                size="small"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                sx={{ flex: 1 }}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchRounded fontSize="small" />
                      </InputAdornment>
                    ),
                  },
                }}
              />
              <TextField
                select
                label="Filter by right"
                size="small"
                value={rightFilter}
                onChange={(event) => setRightFilter(event.target.value as RightDto | '')}
                sx={{ minWidth: 240 }}
              >
                <MenuItem value="">All rights</MenuItem>
                {allRights.map((right) => (
                  <MenuItem key={right} value={right}>
                    {rightLabels[right]}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>

            {visibleUsers.length === 0 ? (
              <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
                <AdminPanelSettingsRounded
                  sx={{ color: 'text.secondary', fontSize: 36, mb: 1.5 }}
                />
                <Typography variant="h6">
                  {search || rightFilter ? 'No users match your filters' : 'No assigned rights'}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  Use the form above to grant a right to a known user.
                </Typography>
              </Paper>
            ) : (
              <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
                <TableContainer>
                  <Table size="small" aria-label="User rights" sx={{ minWidth: 640 }}>
                    <TableHead>
                      <TableRow>
                        <TableCell>User</TableCell>
                        <TableCell>Rights</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {visibleUsers.map((user) => (
                        <TableRow key={user.userId}>
                          <TableCell sx={{ minWidth: 240, verticalAlign: 'top' }}>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                              {user.names.join(' / ') || user.userId}
                            </Typography>
                            {user.names.length > 0 && (
                              <Typography variant="caption" color="text.secondary">
                                {user.userId}
                              </Typography>
                            )}
                            {user.userId === profile.userId && (
                              <Typography
                                variant="caption"
                                color="primary"
                                sx={{ display: 'block' }}
                              >
                                You
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell>
                            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, py: 1 }}>
                              {user.rights.map((right) => (
                                <Chip
                                  key={right}
                                  size="small"
                                  variant="outlined"
                                  label={rightLabels[right]}
                                  disabled={change.isPending}
                                  onDelete={() => void update(user.userId, right, false)}
                                  aria-label={`Revoke ${rightLabels[right]} from ${user.names.join(' / ') || user.userId}`}
                                />
                              ))}
                            </Box>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Paper>
            )}
          </>
        )}
      </Stack>
      <Snackbar open={notice !== null} autoHideDuration={6000} onClose={() => setNotice(null)}>
        <Alert severity={notice?.severity} variant="filled" onClose={() => setNotice(null)}>
          {notice?.message}
        </Alert>
      </Snackbar>
    </Container>
  )
}
