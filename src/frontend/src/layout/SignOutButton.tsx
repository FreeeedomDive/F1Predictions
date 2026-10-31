import LogoutRounded from '@mui/icons-material/LogoutRounded'
import { Alert, CircularProgress, IconButton, Snackbar, Tooltip } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { logout } from '../api/session'

export default function SignOutButton() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const mutation = useMutation({
    mutationFn: async () => {
      await queryClient.cancelQueries()
      await logout()
    },
    onSuccess: async () => {
      queryClient.clear()
      await navigate('/auth', { replace: true })
    },
  })

  return (
    <>
      <Tooltip title="Sign out">
        <span>
          <IconButton
            aria-label="Sign out"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
            size="small"
          >
            {mutation.isPending ? (
              <CircularProgress size={20} />
            ) : (
              <LogoutRounded fontSize="small" />
            )}
          </IconButton>
        </span>
      </Tooltip>
      <Snackbar open={mutation.isError} autoHideDuration={6000} onClose={() => mutation.reset()}>
        <Alert severity="error" onClose={() => mutation.reset()}>
          Could not sign out. Please try again.
        </Alert>
      </Snackbar>
    </>
  )
}
