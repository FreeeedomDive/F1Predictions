import { skipToken, useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getSeasonStats } from '../api/statistics'

function retryStatsQuery(failureCount: number, error: Error) {
  if (isAxiosError(error) && [401, 403, 404].includes(error.response?.status ?? 0)) {
    return false
  }
  return failureCount < 1
}

export function useSeasonStats(
  userId: string,
  leagueId: string | undefined,
  season: number | null,
) {
  return useQuery({
    queryKey: ['statistics', userId, leagueId, season],
    queryFn:
      leagueId && season !== null
        ? ({ signal }) => getSeasonStats(leagueId, season, signal)
        : skipToken,
    staleTime: 30_000,
    retry: retryStatsQuery,
  })
}
