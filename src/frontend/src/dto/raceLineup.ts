import type { RaceDto } from './race'

export interface RaceLineupTeamDto {
  name: string
  badge: string
  backgroundColor: string
  textColor: string
  drivers: string[]
}

export interface RaceLineupDto {
  race: RaceDto
  teams: RaceLineupTeamDto[]
  prepared: boolean
}

export interface SaveRaceLineupRequestDto {
  baseTeams: RaceLineupTeamDto[]
  teams: { previousName: string | null; team: RaceLineupTeamDto }[]
  replacements: { previousDriverName: string; newDriverName: string }[]
}
