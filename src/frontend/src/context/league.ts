import { createContext, useContext } from 'react'
import type { MeResponseDto, UserLeagueDto } from '../dto/user'

export interface LeagueContextValue {
  profile: MeResponseDto
  selectedLeague: UserLeagueDto | null
  selectLeague: (leagueId: string) => void
}

export const LeagueContext = createContext<LeagueContextValue | null>(null)

export function useLeague() {
  const context = useContext(LeagueContext)
  if (!context) throw new Error('useLeague must be used inside LeagueProvider')
  return context
}
