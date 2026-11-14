import { skipToken, useQuery } from '@tanstack/react-query'
import { getParticipantAvatars } from '../api/participants'

export function useParticipantAvatars(userId: string, leagueId: string | undefined) {
  return useQuery({
    queryKey: ['participant-avatars', userId, leagueId],
    queryFn: leagueId ? ({ signal }) => getParticipantAvatars(leagueId, signal) : skipToken,
    staleTime: 5 * 60_000,
    retry: false,
  })
}
