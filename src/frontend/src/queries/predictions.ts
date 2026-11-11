import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  getAdminRacePredictions,
  getCurrentRace,
  getOwnPrediction,
  getRaceDetails,
  saveAdminPrediction,
  savePrediction,
} from '../api/predictions'
import type { AdminPredictionDto, PredictionDto, SavePredictionRequestDto } from '../dto/prediction'
import type { UserLeagueDto } from '../dto/user'

export interface SavePredictionsVariables {
  userId: string
  raceId: string
  leagues: UserLeagueDto[]
  values: SavePredictionRequestDto
}

export type PredictionSaveResult =
  | { league: UserLeagueDto; saved: true; prediction: PredictionDto }
  | { league: UserLeagueDto; saved: false; error: unknown }

export const predictionKey = (userId: string, leagueId: string, raceId: string) =>
  ['predictions', userId, leagueId, raceId] as const

function retryPredictionQuery(count: number, error: Error) {
  if (isAxiosError(error) && [400, 401, 403, 404, 409].includes(error.response?.status ?? 0))
    return false
  return count < 1
}

export function useRaceDetails(
  userId: string,
  leagueId: string | undefined,
  raceId: string | undefined,
) {
  return useQuery({
    queryKey: ['raceDetails', userId, leagueId, raceId],
    queryFn:
      leagueId && raceId ? ({ signal }) => getRaceDetails(leagueId, raceId, signal) : skipToken,
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: retryPredictionQuery,
  })
}

export function useCurrentRace(
  userId: string,
  leagueId: string | undefined,
  season: number | null,
) {
  return useQuery({
    queryKey: ['currentPrediction', userId, leagueId, season],
    queryFn:
      leagueId && season !== null
        ? ({ signal }) => getCurrentRace(leagueId, season, signal)
        : skipToken,
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: retryPredictionQuery,
  })
}

export function useOwnPrediction(
  userId: string,
  leagueId: string | undefined,
  raceId: string | undefined,
) {
  return useQuery({
    queryKey: ['predictions', userId, leagueId, raceId],
    queryFn:
      leagueId && raceId ? ({ signal }) => getOwnPrediction(leagueId, raceId, signal) : skipToken,
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: retryPredictionQuery,
  })
}

export const adminRacePredictionsKey = (userId: string, raceId: string | undefined) =>
  ['admin', 'racePredictions', userId, raceId] as const

export function useAdminRacePredictions(userId: string, raceId: string | undefined) {
  return useQuery({
    queryKey: adminRacePredictionsKey(userId, raceId),
    queryFn: raceId ? ({ signal }) => getAdminRacePredictions(raceId, signal) : skipToken,
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: retryPredictionQuery,
  })
}

export function invalidateRacePredictions(client: QueryClient, raceId: string) {
  return Promise.all([
    ...['raceDetails', 'predictions', 'raceResults'].map((prefix) =>
      client.invalidateQueries({
        queryKey: [prefix],
        predicate: (query) => query.queryKey[3] === raceId,
      }),
    ),
    ...['raceResults', 'racePredictions'].map((key) =>
      client.invalidateQueries({
        queryKey: ['admin', key],
        predicate: (query) => query.queryKey[3] === raceId,
      }),
    ),
    client.invalidateQueries({ queryKey: ['currentPrediction'] }),
  ])
}

export function useSaveAdminPrediction() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({
      raceId,
      predictionId,
      values,
    }: {
      userId: string
      raceId: string
      predictionId: string
      values: SavePredictionRequestDto
    }) => saveAdminPrediction(raceId, predictionId, values),
    retry: false,
    onSuccess: async (prediction, variables) => {
      const key = adminRacePredictionsKey(variables.userId, variables.raceId)
      await client.cancelQueries({ queryKey: key })
      client.setQueryData<AdminPredictionDto[]>(key, (predictions) =>
        predictions?.map((item) =>
          item.prediction.id === prediction.id ? { ...item, prediction } : item,
        ),
      )
    },
    onSettled: (_data, _error, variables) => invalidateRacePredictions(client, variables.raceId),
  })
}

export function useSavePredictions() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({
      leagues,
      raceId,
      values,
    }: SavePredictionsVariables): Promise<PredictionSaveResult[]> => {
      const results = await Promise.allSettled(
        leagues.map((league) => savePrediction(league.leagueId, raceId, values)),
      )
      return results.map<PredictionSaveResult>((result, index) =>
        result.status === 'fulfilled'
          ? { league: leagues[index], saved: true, prediction: result.value }
          : { league: leagues[index], saved: false, error: result.reason as unknown },
      )
    },
    retry: false,
    onSuccess: async (results, variables) => {
      await Promise.all(
        results.map(async (result) => {
          if (!result.saved) return
          const key = predictionKey(variables.userId, result.league.leagueId, variables.raceId)
          await client.cancelQueries({ queryKey: key })
          client.setQueryData(key, result.prediction)
        }),
      )
    },
    onSettled: (_data, _error, variables) =>
      Promise.all(
        variables.leagues.flatMap((league) => [
          client.invalidateQueries({
            queryKey: predictionKey(variables.userId, league.leagueId, variables.raceId),
          }),
          client.invalidateQueries({
            queryKey: ['raceDetails', variables.userId, league.leagueId, variables.raceId],
          }),
          client.invalidateQueries({
            queryKey: ['currentPrediction', variables.userId, league.leagueId],
          }),
        ]),
      ),
  })
}
