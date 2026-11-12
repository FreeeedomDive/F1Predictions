import type { SeasonStatsDto } from '../dto/statistics'
import { api } from './client'

export async function getSeasonStats(
  leagueId: string,
  season: number,
  signal: AbortSignal,
): Promise<SeasonStatsDto> {
  const response = await api.get<SeasonStatsDto>(
    `/api/v1/leagues/${encodeURIComponent(leagueId)}/stats`,
    { params: { season }, signal },
  )
  return response.data
}
