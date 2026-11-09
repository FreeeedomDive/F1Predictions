export interface UserLeagueDto {
  leagueId: string
  participantId: string
  leagueName: string
  participantName: string
  firstSeason: number
  sprintsEnabled: boolean
}

export interface MeResponseDto {
  userId: string
  leagues: UserLeagueDto[]
}
