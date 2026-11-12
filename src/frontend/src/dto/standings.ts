import type { PredictionPointsDto } from './prediction'
import type { RaceDto } from './race'

export interface SeasonPodiumDto {
  season: number
  place: number
}

export interface SeasonRacePointsDto {
  raceId: string
  points: PredictionPointsDto
}

export interface SeasonStandingsRowDto {
  participantId: string
  participantName: string
  totalPoints: number
  maximumTenthPlaceCount: number
  results: SeasonRacePointsDto[]
  previousPodiums: SeasonPodiumDto[]
}

export interface SeasonParticipantChartDto {
  participantId: string
  points: number[]
}

export interface SeasonStandingsDto {
  season: number
  events: RaceDto[]
  rows: SeasonStandingsRowDto[]
  currentLeaderParticipantId: string | null
  currentLeaderPoints: number
  pointsLeft: number
  charts: SeasonParticipantChartDto[]
  championChart: number[]
}
