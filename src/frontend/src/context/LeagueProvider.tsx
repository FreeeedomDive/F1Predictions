import { useCallback, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { CURRENT_SEASON } from '../app/season'
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
  const [preferredSeason, setPreferredSeason] = useState(CURRENT_SEASON)
  const selectedLeague =
    profile.leagues.find((league) => league.leagueId === preferredId) ?? profile.leagues[0] ?? null
  const firstSeason = selectedLeague?.firstSeason
  const seasons = useMemo(
    () =>
      firstSeason === undefined
        ? [CURRENT_SEASON]
        : Array.from(
            { length: Math.max(0, CURRENT_SEASON - firstSeason + 1) },
            (_, index) => CURRENT_SEASON - index,
          ),
    [firstSeason],
  )
  const selectedSeason = seasons.includes(preferredSeason) ? preferredSeason : (seasons[0] ?? null)

  const selectLeague = useCallback(
    (leagueId: string) => {
      const league = profile.leagues.find((league) => league.leagueId === leagueId)
      if (!league) return
      setPreferredId(leagueId)
      setPreferredSeason((season) =>
        season >= league.firstSeason && season <= CURRENT_SEASON ? season : CURRENT_SEASON,
      )
      try {
        localStorage.setItem(storageKey(profile.userId), leagueId)
      } catch {
        // Preferences are optional; the session itself is kept in an HttpOnly cookie.
      }
    },
    [profile],
  )

  const selectSeason = useCallback(
    (season: number) => {
      if (seasons.includes(season)) setPreferredSeason(season)
    },
    [seasons],
  )

  const value = useMemo(
    () => ({ profile, selectedLeague, selectLeague, seasons, selectedSeason, selectSeason }),
    [profile, selectedLeague, selectLeague, seasons, selectedSeason, selectSeason],
  )

  return <LeagueContext.Provider value={value}>{children}</LeagueContext.Provider>
}
