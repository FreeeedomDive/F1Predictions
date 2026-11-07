import type { RaceDto } from '../dto/race'
import { api } from './client'

export async function getRaceCalendar(
  leagueId: string,
  season: number,
  signal: AbortSignal,
): Promise<RaceDto[]> {
  const response = await api.get<RaceDto[]>(
    `/api/v1/leagues/${encodeURIComponent(leagueId)}/races`,
    { params: { season }, signal },
  )
  return response.data
}

export async function getAdminRaceCalendar(
  season: number,
  signal: AbortSignal,
): Promise<RaceDto[]> {
  const response = await api.get<RaceDto[]>('/adminApi/v1/races', {
    params: { season },
    signal,
  })
  return response.data
}

export async function openRacePredictions(raceId: string): Promise<void> {
  await api.patch(`/adminApi/v1/races/${encodeURIComponent(raceId)}/open`)
}

export async function closeRacePredictions(raceId: string): Promise<void> {
  await api.patch(`/adminApi/v1/races/${encodeURIComponent(raceId)}/close`)
}
