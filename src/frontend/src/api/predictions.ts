import { isAxiosError } from 'axios'
import type { PredictionDto, RaceDetailsDto, SavePredictionRequestDto } from '../dto/prediction'
import { api } from './client'

function racePath(leagueId: string, raceId: string) {
  return `/api/v1/leagues/${encodeURIComponent(leagueId)}/races/${encodeURIComponent(raceId)}`
}

export async function getRaceDetails(leagueId: string, raceId: string, signal: AbortSignal) {
  const response = await api.get<RaceDetailsDto>(racePath(leagueId, raceId), { signal })
  return response.data
}

export async function getCurrentRace(leagueId: string, season: number, signal: AbortSignal) {
  const response = await api.get<RaceDetailsDto>(
    `/api/v1/leagues/${encodeURIComponent(leagueId)}/races/current`,
    { params: { season }, signal },
  )
  return response.status === 204 ? null : response.data
}

export async function getOwnPrediction(leagueId: string, raceId: string, signal: AbortSignal) {
  const response = await api.get<PredictionDto>(`${racePath(leagueId, raceId)}/prediction`, {
    signal,
  })
  return response.status === 204 ? null : response.data
}

export async function savePrediction(
  leagueId: string,
  raceId: string,
  values: SavePredictionRequestDto,
) {
  const response = await api.put<PredictionDto>(`${racePath(leagueId, raceId)}/prediction`, values)
  return response.data
}

export function predictionErrorMessage(error: unknown) {
  if (isAxiosError<{ detail?: string }>(error)) {
    const detail = error.response?.data?.detail
    if (error.response?.status === 409) return 'Predictions are no longer open for this race.'
    if (error.response?.status === 404)
      return 'This race or your participation is not available in this league.'
    if (typeof detail === 'string' && detail) return detail
  }
  return 'Could not save the prediction. Please try again.'
}
