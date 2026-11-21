import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getAdminBingo, getOwnBingo, updateAdminBingo } from '../api/bingo'
import type { BingoAdminAction } from '../api/bingo'
import type { BingoAdminDetailsDto } from '../dto/bingo'

export const adminBingoKey = (userId: string) => ['admin', 'bingo', userId] as const

export function useOwnBingo(userId: string, leagueId: string | undefined, season: number | null) {
  return useQuery({
    queryKey: ['bingo', userId, leagueId, season, 'en'],
    queryFn:
      leagueId && season !== null
        ? ({ signal }) => getOwnBingo(leagueId, season, signal)
        : skipToken,
    staleTime: 15_000,
    retry: false,
    refetchInterval: (query) =>
      query.state.status === 'success' && query.state.data?.board ? 30_000 : false,
  })
}

export function useAdminBingo(userId: string) {
  return useQuery({
    queryKey: adminBingoKey(userId),
    queryFn: ({ signal }) => getAdminBingo(signal),
    staleTime: 15_000,
    retry: (count, error) =>
      !(isAxiosError(error) && [400, 401, 403, 404].includes(error.response?.status ?? 0)) &&
      count < 1,
  })
}

export function useUpdateAdminBingo(userId: string) {
  const client = useQueryClient()
  const key = adminBingoKey(userId)
  return useMutation({
    mutationFn: (action: BingoAdminAction) => updateAdminBingo(action),
    retry: false,
    onSuccess: async (result, action) => {
      await client.cancelQueries({ queryKey: key })
      if (result && 'data' in result) {
        client.setQueryData(key, result)
      } else {
        client.setQueryData<BingoAdminDetailsDto>(key, (current) => {
          if (!current) return current
          if (action.type === 'delete') {
            return { ...current, data: current.data.filter((card) => card.id !== action.cardId) }
          }
          if (!result || !('id' in result)) return current
          return {
            ...current,
            canCopyPrevious: false,
            data:
              action.type === 'create'
                ? [...current.data, result]
                : current.data.map((card) => (card.id === result.id ? result : card)),
          }
        })
      }
    },
    onSettled: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: key }),
        client.invalidateQueries({ queryKey: ['bingo'] }),
      ]),
  })
}
