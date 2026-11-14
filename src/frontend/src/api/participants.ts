import type { ListResponseDto } from '../dto/listResponse'
import type { ParticipantAvatarDto, ParticipantAvatarUrls } from '../dto/participant'
import { api } from './client'

export async function getParticipantAvatars(
  leagueId: string,
  signal: AbortSignal,
): Promise<ParticipantAvatarUrls> {
  const response = await api.get<ListResponseDto<ParticipantAvatarDto>>(
    `/api/v1/leagues/${encodeURIComponent(leagueId)}/participants/avatars`,
    { signal },
  )
  return Object.fromEntries(
    response.data.data.map((participant) => [participant.participantId, participant.avatarUrl]),
  )
}
