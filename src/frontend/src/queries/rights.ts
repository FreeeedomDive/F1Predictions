import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { changeUserRight, getUserRights } from '../api/rights'
import { useLeague } from '../context/league'
import type { RightDto } from '../dto/rights'
import { meQueryOptions } from './me'

export function useRights() {
  return useLeague().profile.rights
}

export function useUserRights(userId: string) {
  return useQuery({
    queryKey: ['admin', 'rights', userId],
    queryFn: ({ signal }) => getUserRights(signal),
    staleTime: 15_000,
    retry: (count, error) => {
      if (isAxiosError(error) && [400, 401, 403, 404].includes(error.response?.status ?? 0))
        return false
      return count < 1
    },
  })
}

export function useChangeUserRight() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (variables: { userId: string; right: RightDto; grant: boolean }) =>
      changeUserRight(variables.userId, variables.right, variables.grant),
    retry: false,
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: meQueryOptions.queryKey })
      const profile = client.getQueryData(meQueryOptions.queryKey)
      if (profile?.rights.includes('EDIT_RIGHTS')) {
        await client.invalidateQueries({ queryKey: ['admin', 'rights'] })
      } else {
        client.removeQueries({ queryKey: ['admin', 'rights'] })
      }
    },
  })
}
