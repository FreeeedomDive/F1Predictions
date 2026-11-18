import type { RaceLineupDto, SaveRaceLineupRequestDto } from '../dto/raceLineup'
import { api } from './client'

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

export async function saveRaceLineup(
  raceId: string,
  request: SaveRaceLineupRequestDto,
): Promise<RaceLineupDto> {
  const response = await api.put<RaceLineupDto>(
    `/adminApi/v1/races/${encodeURIComponent(raceId)}/lineup`,
    request,
  )
  return response.data
}
