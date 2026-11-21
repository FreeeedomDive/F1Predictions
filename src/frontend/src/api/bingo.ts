import { isAxiosError } from 'axios'
import type { BingoAdminCardDto, BingoAdminDetailsDto, SaveBingoCardRequestDto } from '../dto/bingo'
import { api } from './client'

const adminPath = '/adminApi/v1/bingo'

export async function getAdminBingo(signal: AbortSignal) {
  const response = await api.get<BingoAdminDetailsDto>(adminPath, { signal })
  return response.data
}

export type BingoAdminAction =
  | { type: 'create'; card: SaveBingoCardRequestDto }
  | { type: 'update'; cardId: string; card: SaveBingoCardRequestDto }
  | { type: 'delete'; cardId: string }
  | { type: 'progress'; cardId: string; completedRepeats: number }
  | { type: 'copyPrevious' }

export async function updateAdminBingo(action: BingoAdminAction) {
  const cardPath =
    'cardId' in action ? `${adminPath}/cards/${encodeURIComponent(action.cardId)}` : ''
  switch (action.type) {
    case 'create':
      return (await api.post<BingoAdminCardDto>(`${adminPath}/cards`, action.card)).data
    case 'update':
      return (await api.put<BingoAdminCardDto>(cardPath, action.card)).data
    case 'delete':
      await api.delete(cardPath)
      return null
    case 'progress':
      return (
        await api.put<BingoAdminCardDto>(`${cardPath}/progress`, {
          completedRepeats: action.completedRepeats,
        })
      ).data
    case 'copyPrevious':
      return (await api.post<BingoAdminDetailsDto>(`${adminPath}/cards/copy-previous`)).data
  }
}

export function bingoErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ detail?: string }>(error)) {
    if (error.response?.status === 403) return 'You do not have permission to edit bingo.'
    const detail = error.response?.data?.detail
    if (typeof detail === 'string' && detail) return detail
  }
  return fallback
}
