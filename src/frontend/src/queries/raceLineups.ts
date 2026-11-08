import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  addRaceLineupTeam,
  getRaceLineupTarget,
  swapRaceLineupDrivers,
  updateRaceLineupTeam,
} from '../api/raceLineups'
import type { RaceLineupDto, RaceLineupTeamDto, SwapRaceDriversRequestDto } from '../dto/raceLineup'

interface SaveTeamVariables {
  season: number
  raceId: string
  previousName?: string
  team: RaceLineupTeamDto
}

interface SwapDriversVariables extends SwapRaceDriversRequestDto {
  season: number
  raceId: string
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

export function useSaveRaceLineupTeam() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ raceId, previousName, team }: SaveTeamVariables) =>
      previousName === undefined
        ? addRaceLineupTeam(raceId, team)
        : updateRaceLineupTeam(raceId, previousName, team),
    retry: false,
    onSuccess: async (_, variables) => {
      const queryKey = ['admin', 'raceLineups', variables.season]
      await queryClient.cancelQueries({ queryKey })
      queryClient.setQueryData<RaceLineupDto | null>(queryKey, (current) => {
        if (!current || current.race.id !== variables.raceId) return current
        const teams =
          variables.previousName === undefined
            ? [...current.teams, variables.team]
            : current.teams.map((team) =>
                team.name === variables.previousName ? variables.team : team,
              )
        return { ...current, teams, prepared: true }
      })
    },
    onSettled: (_, _error, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'raceLineups', variables.season] })
    },
  })
}

export function useSwapRaceLineupDrivers() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ raceId, firstDriverName, secondDriverName }: SwapDriversVariables) =>
      swapRaceLineupDrivers(raceId, { firstDriverName, secondDriverName }),
    retry: false,
    onSuccess: async (lineup, variables) => {
      const queryKey = ['admin', 'raceLineups', variables.season]
      await queryClient.cancelQueries({ queryKey })
      queryClient.setQueryData<RaceLineupDto | null>(queryKey, (current) =>
        current?.race.id === variables.raceId ? lineup : current,
      )
    },
    onSettled: (_, _error, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'raceLineups', variables.season] })
    },
  })
}
