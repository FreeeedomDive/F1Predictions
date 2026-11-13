import type { PredictionPointsDto } from './prediction'
import type { RaceDto } from './race'
import type { RaceLineupTeamDto } from './raceLineup'

export type RaceDriverStatusDto = 'FINISHED' | 'DNF' | 'DNS' | 'DQ'

export interface RaceClassificationEntryDto {
  driverName: string
  status: RaceDriverStatusDto
}

export interface RaceResultValuesDto {
  classification: RaceClassificationEntryDto[] | null
  safetyCars: number | null
  firstPlaceLead: number | null
}

export interface RaceResultDto {
  raceId: string
  rulesVersion: string
  result: RaceResultValuesDto | null
  draft: RaceResultValuesDto | null
  startingGrid: string[]
}

export interface RaceResultEditorDto {
  race: RaceDto
  season: number
  teams: RaceLineupTeamDto[]
  lineupPrepared: boolean
  conditions: { positionPredictionDriver: string | null } | null
  result: RaceResultDto | null
}

export interface RaceResultDetailsDto {
  race: RaceDto
  season: number
  rulesVersion: string
  result: RaceResultValuesDto
  points: PredictionPointsDto | null
}
