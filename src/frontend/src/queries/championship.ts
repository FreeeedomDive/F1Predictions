import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  getAdminChampionship,
  getChampionship,
  getChampionshipPredictionStatuses,
  getStageRosterRaces,
  saveChampionshipPrediction,
  updateAdminChampionship,
} from '../api/championship'
import type { ChampionshipAdminAction } from '../api/championship'
import type { ChampionshipDetailsDto, ChampionshipStageDto } from '../dto/championship'

const championshipKey = (userId: string, leagueId: string | undefined, season: number | null) =>
  ['championship', season, userId, leagueId] as const

export const adminChampionshipKey = (userId: string, season: number | null) =>
  ['admin', 'championship', userId, season] as const

function retryChampionshipQuery(count: number, error: Error) {
  return (
    !(isAxiosError(error) && [400, 401, 403, 404].includes(error.response?.status ?? 0)) &&
    count < 1
  )
}

export function useChampionship(
  userId: string,
  leagueId: string | undefined,
  season: number | null,
  { refetchInterval }: { refetchInterval?: number } = {},
) {
  return useQuery({
    queryKey: championshipKey(userId, leagueId, season),
    queryFn:
      leagueId && season !== null
        ? ({ signal }) => getChampionship(leagueId, season, signal)
        : skipToken,
    staleTime: 15_000,
    refetchInterval,
    retry: retryChampionshipQuery,
  })
}

export function useAdminChampionship(userId: string, season: number | null) {
  return useQuery({
    queryKey: adminChampionshipKey(userId, season),
    queryFn: season !== null ? ({ signal }) => getAdminChampionship(season, signal) : skipToken,
    staleTime: 15_000,
    retry: retryChampionshipQuery,
  })
}

export function useStageRosterRaces(userId: string, season: number | null) {
  return useQuery({
    queryKey: [...adminChampionshipKey(userId, season), 'stageRosterRaces'],
    queryFn: season !== null ? ({ signal }) => getStageRosterRaces(season, signal) : skipToken,
    staleTime: 15_000,
    retry: retryChampionshipQuery,
  })
}

export function useChampionshipPredictionStatuses(
  userId: string,
  leagueId: string | undefined,
  season: number | null,
) {
  return useQuery({
    queryKey: [...adminChampionshipKey(userId, season), 'predictionStatuses', leagueId],
    queryFn:
      leagueId && season !== null
        ? ({ signal }) => getChampionshipPredictionStatuses(leagueId, season, signal)
        : skipToken,
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: retryChampionshipQuery,
  })
}

export function useUpdateAdminChampionship(userId: string, season: number) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (action: ChampionshipAdminAction) => updateAdminChampionship(season, action),
    retry: false,
    onSettled: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: adminChampionshipKey(userId, season) }),
        client.invalidateQueries({ queryKey: ['championship', season] }),
      ]),
  })
}

export function useSaveChampionshipPrediction(
  userId: string,
  leagueId: string,
  season: number,
  stage: ChampionshipStageDto,
) {
  const client = useQueryClient()
  const key = championshipKey(userId, leagueId, season)
  return useMutation({
    mutationFn: (driverStandings: string[]) =>
      saveChampionshipPrediction(leagueId, season, stage, driverStandings),
    retry: false,
    onSuccess: async (prediction) => {
      await client.cancelQueries({ queryKey: key })
      client.setQueryData<ChampionshipDetailsDto>(key, (details) => {
        if (!details?.predictionsOpen || details.currentStage !== stage) return details
        return stage === 'PRE_SEASON'
          ? { ...details, preSeasonPrediction: prediction }
          : { ...details, midSeasonPrediction: prediction }
      })
    },
    onSettled: () => client.invalidateQueries({ queryKey: key }),
  })
}
