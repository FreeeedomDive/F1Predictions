export interface UserLeagueDto {
  leagueId: string
  participantId: string
  leagueName: string
  participantName: string
}

export interface MeResponseDto {
  userId: string
  leagues: UserLeagueDto[]
}
