import type { RaceLineupTeamDto } from './raceLineup'

export type ChampionshipStageDto = 'PRE_SEASON' | 'MID_SEASON'
export type ChampionshipPredictionSourceDto = 'USER' | 'PRE_SEASON_COPY'

export interface ChampionshipPredictionDto {
  stage: ChampionshipStageDto
  driverStandings: string[]
  predictionSource: ChampionshipPredictionSourceDto
  points: number[]
}

export interface ChampionshipSeasonDto {
  season: number
  configured: boolean
  rulesVersion: string | null
  currentStage: ChampionshipStageDto | null
  stageStarted: boolean
  predictionsOpen: boolean
  preSeasonRoster: string[]
  midSeasonRoster: string[]
  driverStandings: string[]
  standingsUpdate: { lastSyncedRound: number | null; updatedAt: string } | null
  teams: RaceLineupTeamDto[]
  currentDrivers: string[]
}

export interface ChampionshipDetailsDto extends ChampionshipSeasonDto {
  preSeasonPrediction: ChampionshipPredictionDto | null
  midSeasonPrediction: ChampionshipPredictionDto | null
  rows: { participantId: string; displayName: string; totalPoints: number }[]
}

export interface ChampionshipPredictionStatusDto {
  participantId: string
  displayName: string
  hasPreSeasonPrediction: boolean
  midSeasonPredictionSource: ChampionshipPredictionSourceDto | null
}
