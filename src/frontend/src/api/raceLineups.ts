import type { RaceLineupDto, SaveRaceLineupRequestDto } from '../dto/raceLineup'
import { api } from './client'

const raceLineupUrl = (raceId: string) => `/adminApi/v1/races/${encodeURIComponent(raceId)}/lineup`

export async function getRaceLineupTarget(
  season: number,
  signal: AbortSignal,
): Promise<RaceLineupDto | null> {
  const response = await api.get<RaceLineupDto>('/adminApi/v1/raceLineups/target', {
    params: { season },
    signal,
  })
  return response.status === 204 ? null : response.data
}

export async function getRaceLineup(raceId: string, signal: AbortSignal): Promise<RaceLineupDto> {
  const response = await api.get<RaceLineupDto>(raceLineupUrl(raceId), { signal })
  return response.data
}

export async function saveRaceLineup(
  raceId: string,
  request: SaveRaceLineupRequestDto,
): Promise<RaceLineupDto> {
  const response = await api.put<RaceLineupDto>(raceLineupUrl(raceId), request)
  return response.data
}
