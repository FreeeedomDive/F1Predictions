import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  closeRacePredictions,
  getAdminRaceCalendar,
  getRaceCalendar,
  openRacePredictions,
} from '../api/races'

function retryCalendarQuery(failureCount: number, error: Error) {
  if (isAxiosError(error) && [401, 403, 404].includes(error.response?.status ?? 0)) {
    return false
  }
  return failureCount < 1
}

export function useRaceCalendar(leagueId: string | undefined, season: number | null) {
  return useQuery({
    queryKey: ['races', leagueId, season],
    queryFn:
      leagueId && season !== null
        ? ({ signal }) => getRaceCalendar(leagueId, season, signal)
        : skipToken,
    staleTime: 30_000,
    refetchInterval: 60_000,
    retry: retryCalendarQuery,
  })
}

export function useAdminRaceCalendar(season: number | null) {
  return useQuery({
    queryKey: ['admin', 'races', season],
    queryFn: season !== null ? ({ signal }) => getAdminRaceCalendar(season, signal) : skipToken,
    staleTime: 30_000,
    refetchInterval: 60_000,
    retry: retryCalendarQuery,
  })
}

export function useChangeRacePredictionsStatus() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ raceId, action }: { raceId: string; action: 'open' | 'close' }) =>
      action === 'open' ? openRacePredictions(raceId) : closeRacePredictions(raceId),
    retry: false,
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'races'] }),
        queryClient.invalidateQueries({ queryKey: ['races'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'raceLineups'] }),
      ])
    },
  })
}
