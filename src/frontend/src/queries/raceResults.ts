import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  finalizeRaceResult,
  getOwnRaceResult,
  getRaceResultEditor,
  saveRaceResultDraft,
} from '../api/raceResults'
import type { RaceDetailsDto } from '../dto/prediction'
import type { RaceDto } from '../dto/race'
import type { RaceResultEditorDto, RaceResultValuesDto } from '../dto/raceResult'
import { invalidateRacePredictions } from './predictions'

interface ResultVariables {
  userId: string
  raceId: string
  season: number
}

interface SaveDraftVariables extends ResultVariables {
  values: RaceResultValuesDto
}

export const adminRaceResultKey = (userId: string, raceId: string | undefined) =>
  ['admin', 'raceResults', userId, raceId] as const

export const raceResultKey = (
  userId: string,
  leagueId: string | undefined,
  raceId: string | undefined,
) => ['raceResults', userId, leagueId, raceId] as const

function retryResultQuery(count: number, error: Error) {
  if (isAxiosError(error) && [400, 401, 403, 404, 409].includes(error.response?.status ?? 0))
    return false
  return count < 1
}

export function useOwnRaceResult(
  userId: string,
  leagueId: string | undefined,
  raceId: string | undefined,
) {
  return useQuery({
    queryKey: raceResultKey(userId, leagueId, raceId),
    queryFn:
      leagueId && raceId ? ({ signal }) => getOwnRaceResult(leagueId, raceId, signal) : skipToken,
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: retryResultQuery,
  })
}

export function useAdminRaceResult(userId: string, raceId: string | undefined) {
  return useQuery({
    queryKey: adminRaceResultKey(userId, raceId),
    queryFn: raceId ? ({ signal }) => getRaceResultEditor(raceId, signal) : skipToken,
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: retryResultQuery,
  })
}

export function useSaveRaceResultDraft() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ raceId, values }: SaveDraftVariables) => saveRaceResultDraft(raceId, values),
    retry: false,
    onSuccess: async (result, variables) => {
      const key = adminRaceResultKey(variables.userId, variables.raceId)
      await client.cancelQueries({ queryKey: key })
      client.setQueryData<RaceResultEditorDto>(key, (page) =>
        page?.race.id === variables.raceId ? { ...page, result } : page,
      )
    },
    onSettled: (_result, _error, variables) =>
      client.invalidateQueries({
        queryKey: adminRaceResultKey(variables.userId, variables.raceId),
      }),
  })
}

export function useFinalizeRaceResult() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ raceId }: ResultVariables) => finalizeRaceResult(raceId),
    retry: false,
    onSuccess: async (result, variables) => {
      const { userId, raceId, season } = variables
      const editorKey = adminRaceResultKey(userId, raceId)
      await Promise.all([
        client.cancelQueries({ queryKey: editorKey }),
        client.cancelQueries({ queryKey: ['admin', 'races', season] }),
        client.cancelQueries({
          queryKey: ['races'],
          predicate: (query) => query.queryKey[2] === season,
        }),
        client.cancelQueries({
          queryKey: ['raceDetails'],
          predicate: (query) => query.queryKey[3] === raceId,
        }),
        client.cancelQueries({
          queryKey: ['raceResults'],
          predicate: (query) => query.queryKey[3] === raceId,
        }),
        client.cancelQueries({
          queryKey: ['currentPrediction'],
          predicate: (query) => query.queryKey[3] === season,
        }),
      ])
      client.setQueryData<RaceResultEditorDto>(editorKey, (page) =>
        page?.race.id === raceId
          ? { ...page, race: { ...page.race, status: 'FINISHED' }, result }
          : page,
      )
      const finishRace = (race: RaceDto): RaceDto =>
        race.id === raceId ? { ...race, status: 'FINISHED' } : race
      client.setQueryData<RaceDto[]>(['admin', 'races', season], (races) => races?.map(finishRace))
      client.setQueriesData<RaceDto[]>(
        { queryKey: ['races'], predicate: (query) => query.queryKey[2] === season },
        (races) => races?.map(finishRace),
      )
      client.setQueriesData<RaceDetailsDto>(
        { queryKey: ['raceDetails'], predicate: (query) => query.queryKey[3] === raceId },
        (page) => (page ? { ...page, race: finishRace(page.race), predictionsOpen: false } : page),
      )
      client.setQueriesData<RaceDetailsDto | null>(
        { queryKey: ['currentPrediction'], predicate: (query) => query.queryKey[3] === season },
        (page) =>
          page?.race.id === raceId
            ? { ...page, race: finishRace(page.race), predictionsOpen: false }
            : page,
      )
    },
    onSettled: (_result, _error, variables) =>
      Promise.all([
        client.invalidateQueries({ queryKey: ['admin', 'races', variables.season] }),
        client.invalidateQueries({
          queryKey: ['races'],
          predicate: (query) => query.queryKey[2] === variables.season,
        }),
        client.invalidateQueries({ queryKey: ['admin', 'raceLineups'] }),
        invalidateRacePredictions(client, variables.raceId),
      ]),
  })
}
