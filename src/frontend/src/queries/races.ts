import { skipToken, useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getRaceCalendar } from '../api/races'

export function useRaceCalendar(leagueId: string | undefined, season: number) {
  return useQuery({
    queryKey: ['races', leagueId, season],
    queryFn: leagueId ? ({ signal }) => getRaceCalendar(leagueId, season, signal) : skipToken,
    staleTime: 30_000,
    refetchInterval: 60_000,
    retry: (failureCount, error) => {
      if (isAxiosError(error) && [401, 403, 404].includes(error.response?.status ?? 0)) {
        return false
      }
      return failureCount < 1
    },
  })
}
