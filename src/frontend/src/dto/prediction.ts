import type { RaceDto } from './race'
import type { RaceLineupTeamDto } from './raceLineup'

export type SafetyCarsCountDto = 'ZERO' | 'ONE' | 'TWO' | 'THREE_PLUS'

export interface SavePredictionRequestDto {
  tenthPlacePickedDriver: string | null
  dnfPickedDrivers: string[] | null
  safetyCarsPrediction: SafetyCarsCountDto | null
  driverPositionPrediction: number | null
  firstPlaceLeadPrediction: number | null
}

export interface PredictionDto extends SavePredictionRequestDto {
  id: string
  leagueId: string
  participantId: string
  raceId: string
  teamsPickedDrivers: string[] | null
}

export interface RaceDetailsDto {
  race: RaceDto
  season: number
  teams: RaceLineupTeamDto[]
  lineupPrepared: boolean
  conditions: { positionPredictionDriver: string | null } | null
  predictionsOpen: boolean
  serverTime: string
  startingGrid?: string[] | null
}
