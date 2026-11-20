import { isAxiosError } from 'axios'
import type {
  ChampionshipDetailsDto,
  ChampionshipPredictionDto,
  ChampionshipPredictionStatusDto,
  ChampionshipSeasonDto,
  ChampionshipStageDto,
} from '../dto/championship'
import type { ListResponseDto } from '../dto/listResponse'
import type { RaceDto } from '../dto/race'
import { api } from './client'

function championshipPath(leagueId: string, season: number) {
  return `/api/v1/leagues/${encodeURIComponent(leagueId)}/championship/${season}`
}

export async function getChampionship(leagueId: string, season: number, signal: AbortSignal) {
  const response = await api.get<ChampionshipDetailsDto>(championshipPath(leagueId, season), {
    signal,
  })
  return response.data
}

export async function saveChampionshipPrediction(
  leagueId: string,
  season: number,
  stage: ChampionshipStageDto,
  driverStandings: string[],
) {
  const response = await api.put<ChampionshipPredictionDto>(
    `${championshipPath(leagueId, season)}/predictions/${stage}`,
    { driverStandings },
  )
  return response.data
}

function adminChampionshipPath(season: number) {
  return `/adminApi/v1/championship/${season}`
}

export async function getAdminChampionship(season: number, signal: AbortSignal) {
  const response = await api.get<ChampionshipSeasonDto>(adminChampionshipPath(season), { signal })
  return response.data
}

export async function getStageRosterRaces(season: number, signal: AbortSignal) {
  const response = await api.get<ListResponseDto<RaceDto>>(
    `${adminChampionshipPath(season)}/stage-roster-races`,
    { signal },
  )
  return response.data.data
}

export async function getChampionshipPredictionStatuses(
  leagueId: string,
  season: number,
  signal: AbortSignal,
) {
  const response = await api.get<ListResponseDto<ChampionshipPredictionStatusDto>>(
    `${adminChampionshipPath(season)}/prediction-statuses`,
    { params: { leagueId }, signal },
  )
  return response.data.data
}

export type ChampionshipAdminAction =
  | { type: 'configure'; rulesVersion: string }
  | { type: 'saveStage'; stage: ChampionshipStageDto; isOpen: boolean; lineupRaceId?: string }
  | { type: 'saveStandings'; driverStandings: string[] }

export async function updateAdminChampionship(season: number, action: ChampionshipAdminAction) {
  const path = adminChampionshipPath(season)
  switch (action.type) {
    case 'configure':
      await api.put(`${path}/configuration`, { rulesVersion: action.rulesVersion })
      break
    case 'saveStage':
      await api.put(
        `${path}/stages/${action.stage}/${action.isOpen ? 'open' : 'close'}`,
        action.isOpen ? { lineupRaceId: action.lineupRaceId } : undefined,
      )
      break
    case 'saveStandings':
      await api.put(`${path}/standings`, { driverStandings: action.driverStandings })
      break
  }
}

export function championshipErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ code?: string; detail?: string }>(error)) {
    const problem = error.response?.data
    if (problem?.code === 'CHAMPIONSHIP_PREDICTIONS_CLOSED') {
      return 'Predictions are no longer open for this stage.'
    }
    if (typeof problem?.detail === 'string' && problem.detail) return problem.detail
  }
  return fallback
}
