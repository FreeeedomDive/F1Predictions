import { isAxiosError } from 'axios'
import type {
  RaceResultDetailsDto,
  RaceResultDto,
  RaceResultEditorDto,
  RaceResultValuesDto,
} from '../dto/raceResult'
import { api } from './client'

function resultPath(raceId: string) {
  return `/adminApi/v1/races/${encodeURIComponent(raceId)}/result`
}

export async function getRaceResultEditor(raceId: string, signal: AbortSignal) {
  const response = await api.get<RaceResultEditorDto>(resultPath(raceId), { signal })
  return response.data
}

export async function getOwnRaceResult(leagueId: string, raceId: string, signal: AbortSignal) {
  const response = await api.get<RaceResultDetailsDto>(
    `/api/v1/leagues/${encodeURIComponent(leagueId)}/races/${encodeURIComponent(raceId)}/result`,
    { signal },
  )
  return response.status === 204 ? null : response.data
}

export async function saveRaceResultDraft(raceId: string, values: RaceResultValuesDto) {
  const response = await api.put<RaceResultDto>(`${resultPath(raceId)}/draft`, values)
  return response.data
}

export async function finalizeRaceResult(raceId: string) {
  const response = await api.post<RaceResultDto>(`${resultPath(raceId)}/finalize`)
  return response.data
}

export async function syncStartingGrid(raceId: string) {
  const response = await api.post<RaceResultDto>(
    `${resultPath(raceId)}/starting-grid/sync`,
    undefined,
    { timeout: 45_000 },
  )
  return response.data
}

export function raceResultErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ detail?: string }>(error)) {
    const detail = error.response?.data?.detail
    if (typeof detail === 'string' && detail) return detail
    if (error.response?.status === 404) return 'This race or its saved result is not available.'
    if (error.response?.status === 403) return 'You do not have permission to access this result.'
  }
  return fallback
}
