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
