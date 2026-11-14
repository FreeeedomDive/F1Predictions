export interface ParticipantAvatarDto {
  participantId: string
  avatarUrl: string | null
}

export type ParticipantAvatarUrls = Record<string, string | null>
