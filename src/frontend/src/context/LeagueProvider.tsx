import { useCallback, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { MeResponseDto } from '../dto/user'
import { LeagueContext } from './league'

function storageKey(userId: string) {
  return `f1predictions.selected-league.${userId}`
}

function savedLeague(userId: string): string | null {
  try {
    return localStorage.getItem(storageKey(userId))
  } catch {
    return null
  }
}

export default function LeagueProvider({
  profile,
  children,
}: {
  profile: MeResponseDto
  children: ReactNode
}) {
  const [preferredId, setPreferredId] = useState(() => savedLeague(profile.userId))
  const selectedLeague =
    profile.leagues.find((league) => league.leagueId === preferredId) ?? profile.leagues[0] ?? null

  const selectLeague = useCallback(
    (leagueId: string) => {
      if (!profile.leagues.some((league) => league.leagueId === leagueId)) return
      setPreferredId(leagueId)
      try {
        localStorage.setItem(storageKey(profile.userId), leagueId)
      } catch {
        // Preferences are optional; the session itself is kept in an HttpOnly cookie.
      }
    },
    [profile],
  )

  const value = useMemo(
    () => ({ profile, selectedLeague, selectLeague }),
    [profile, selectedLeague, selectLeague],
  )

  return <LeagueContext.Provider value={value}>{children}</LeagueContext.Provider>
}
