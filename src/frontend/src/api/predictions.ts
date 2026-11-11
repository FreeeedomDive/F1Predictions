import { isAxiosError } from 'axios'
import type { ListResponseDto } from '../dto/listResponse'
import type {
  AdminPredictionDto,
  PredictionDto,
  RaceDetailsDto,
  SavePredictionRequestDto,
} from '../dto/prediction'
import { api } from './client'

function racePath(leagueId: string, raceId: string) {
  return `/api/v1/leagues/${encodeURIComponent(leagueId)}/races/${encodeURIComponent(raceId)}`
}

function adminPredictionsPath(raceId: string) {
  return `/adminApi/v1/races/${encodeURIComponent(raceId)}/predictions`
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

export async function getAdminRacePredictions(raceId: string, signal: AbortSignal) {
  const response = await api.get<ListResponseDto<AdminPredictionDto>>(
    adminPredictionsPath(raceId),
    {
      signal,
    },
  )
  return response.data.data
}

export async function saveAdminPrediction(
  raceId: string,
  predictionId: string,
  values: SavePredictionRequestDto,
) {
  const response = await api.put<PredictionDto>(
    `${adminPredictionsPath(raceId)}/${encodeURIComponent(predictionId)}`,
    values,
  )
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

export function adminPredictionErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ detail?: string }>(error)) {
    const detail = error.response?.data?.detail
    if (typeof detail === 'string' && detail) return detail
    if (error.response?.status === 404) return 'This race or prediction is not available.'
  }
  return fallback
}
