import type { SeasonStandingsDto } from '../dto/standings'
import { api } from './client'

export async function getSeasonStandings(
  leagueId: string,
  season: number,
  signal: AbortSignal,
): Promise<SeasonStandingsDto> {
  const response = await api.get<SeasonStandingsDto>(
    `/api/v1/leagues/${encodeURIComponent(leagueId)}/standings`,
    { params: { season }, signal },
  )
  return response.data
}
