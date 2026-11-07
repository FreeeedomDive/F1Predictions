export interface UserLeagueDto {
  leagueId: string
  participantId: string
  leagueName: string
  participantName: string
  firstSeason: number
}

export interface MeResponseDto {
  userId: string
  leagues: UserLeagueDto[]
}
