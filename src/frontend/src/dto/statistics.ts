import type { SafetyCarsCountDto } from './prediction'

export interface SeasonDriverStatsDto {
  driver: string
  score: number
}

export interface SeasonParticipantStatsDto {
  participantId: string
  participantName: string
  score: number
}

export interface SeasonLeadGapPredictionStatsDto {
  participantId: string
  participantName: string
  raceName: string
  predictedGap: number
  actualGap: number
  difference: number
}

export interface SeasonSafetyCarStatsDto {
  safetyCars: SafetyCarsCountDto
  score: number
}

export interface SeasonStatsDto {
  tenthPlacePointsRating: SeasonDriverStatsDto[]
  mostPickedForTenthPlace: SeasonDriverStatsDto[]
  tenthPickedButDnfed: SeasonDriverStatsDto[]
  driverOwnTenthPlacePoints: SeasonDriverStatsDto[]
  tenthPlacePredictionEfficiency: SeasonDriverStatsDto[]
  mostDnfDrivers: SeasonDriverStatsDto[]
  mostPickedForDnf: SeasonDriverStatsDto[]
  tenthPlaceDnfAntiRating: SeasonParticipantStatsDto[]
  closestLeadGapPredictions: SeasonLeadGapPredictionStatsDto[]
  safetyCarPickCounts: SeasonSafetyCarStatsDto[]
  safetyCarActualCounts: SeasonSafetyCarStatsDto[]
  safetyCarCorrectGuesses: SeasonSafetyCarStatsDto[]
}
