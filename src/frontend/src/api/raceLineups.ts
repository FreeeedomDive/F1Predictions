import type { RaceLineupDto, RaceLineupTeamDto } from '../dto/raceLineup'
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

export async function addRaceLineupTeam(raceId: string, team: RaceLineupTeamDto): Promise<void> {
  await api.post(`/adminApi/v1/races/${encodeURIComponent(raceId)}/lineup/teams`, team)
}

export async function updateRaceLineupTeam(
  raceId: string,
  previousName: string,
  team: RaceLineupTeamDto,
): Promise<void> {
  await api.put(
    `/adminApi/v1/races/${encodeURIComponent(raceId)}/lineup/teams/${encodeURIComponent(previousName)}`,
    team,
  )
}
