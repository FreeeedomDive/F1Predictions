import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getRaceLineup, getRaceLineupTarget, saveRaceLineup } from '../api/raceLineups'
import type { RaceLineupDto, SaveRaceLineupRequestDto } from '../dto/raceLineup'
import { invalidateRacePredictions } from './predictions'

interface SaveRaceLineupVariables {
  season: number
  raceId: string
  request: SaveRaceLineupRequestDto
}

const lineupKey = ['admin', 'raceLineups'] as const
const targetKey = (season: number | null) => [...lineupKey, 'target', season] as const
const raceKey = (raceId: string) => [...lineupKey, 'race', raceId] as const

export function useRaceLineupEditor(season: number | null, raceId: string | undefined) {
  return useQuery({
    queryKey: raceId ? raceKey(raceId) : targetKey(season),
    queryFn: raceId
      ? ({ signal }) => getRaceLineup(raceId, signal)
      : season !== null
        ? ({ signal }) => getRaceLineupTarget(season, signal)
        : skipToken,
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
      const direct = raceKey(variables.raceId)
      const target = targetKey(variables.season)
      await Promise.all([
        queryClient.cancelQueries({ queryKey: direct }),
        queryClient.cancelQueries({ queryKey: target }),
      ])
      queryClient.setQueryData(direct, lineup)
      queryClient.setQueryData<RaceLineupDto | null>(target, (current) =>
        current?.race.id === variables.raceId ? lineup : current,
      )
    },
    onSettled: (_, _error, variables) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: lineupKey }),
        invalidateRacePredictions(queryClient, variables.raceId),
      ]),
  })
}
