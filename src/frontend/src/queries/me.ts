import { queryOptions, useQuery } from '@tanstack/react-query'
import { getMe } from '../api/session'

export const meQueryOptions = queryOptions({
  queryKey: ['me'],
  queryFn: ({ signal }) => getMe(signal),
  staleTime: 30_000,
})

export function useMe() {
  return useQuery(meQueryOptions)
}
