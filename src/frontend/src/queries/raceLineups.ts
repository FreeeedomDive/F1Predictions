import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getRaceLineupTarget, saveRaceLineup } from '../api/raceLineups'
import type { RaceLineupDto, SaveRaceLineupRequestDto } from '../dto/raceLineup'
import { invalidateRacePredictions } from './predictions'

interface SaveRaceLineupVariables {
  season: number
  raceId: string
  request: SaveRaceLineupRequestDto
}

export function useRaceLineupTarget(season: number | null) {
  return useQuery({
    queryKey: ['admin', 'raceLineups', season],
    queryFn: season !== null ? ({ signal }) => getRaceLineupTarget(season, signal) : skipToken,
    staleTime: 30_000,
    refetchInterval: 60_000,
    retry: (failureCount, error) => {
      if (isAxiosError(error) && [400, 401, 403, 404].includes(error.response?.status ?? 0)) {
        return false
      }
      return failureCount < 1
    },
  })
}

export function useSaveRaceLineup() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ raceId, request }: SaveRaceLineupVariables) => saveRaceLineup(raceId, request),
    retry: false,
    onSuccess: async (lineup, variables) => {
      const queryKey = ['admin', 'raceLineups', variables.season]
      await queryClient.cancelQueries({ queryKey })
      queryClient.setQueryData<RaceLineupDto | null>(queryKey, (current) =>
        current?.race.id === variables.raceId ? lineup : current,
      )
    },
    onSettled: (_, _error, variables) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'raceLineups', variables.season] }),
        invalidateRacePredictions(queryClient, variables.raceId),
      ]),
  })
}
