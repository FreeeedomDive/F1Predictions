import { skipToken, useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getSeasonStandings } from '../api/standings'

function retryStandingsQuery(failureCount: number, error: Error) {
  if (isAxiosError(error) && [401, 403, 404].includes(error.response?.status ?? 0)) {
    return false
  }
  return failureCount < 1
}

export function useSeasonStandings(
  userId: string,
  leagueId: string | undefined,
  season: number | null,
) {
  return useQuery({
    queryKey: ['standings', userId, leagueId, season],
    queryFn:
      leagueId && season !== null
        ? ({ signal }) => getSeasonStandings(leagueId, season, signal)
        : skipToken,
    staleTime: 30_000,
    retry: retryStandingsQuery,
  })
}
