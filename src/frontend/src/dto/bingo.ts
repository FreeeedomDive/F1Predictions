export type BingoCardProbabilityDto = 'LOW' | 'MEDIUM' | 'HIGH'

export interface BingoCardDto {
  id: string
  description: string
  explanation: string | null
  language: string
  probability: BingoCardProbabilityDto
  requiredRepeats: number
  completedRepeats: number
  completed: boolean
}

export interface BingoBoardDto {
  id: string
  participantId: string
  bingoAchieved: boolean
  cards: BingoCardDto[]
}

export interface BingoDetailsDto {
  season: number
  activeSeason: number
  canCreate: boolean
  board: BingoBoardDto | null
}

export interface BingoCardTranslationDto {
  description: string | null
  explanation: string | null
}

export interface SaveBingoCardRequestDto {
  translations: Record<string, BingoCardTranslationDto>
  probability: BingoCardProbabilityDto
  requiredRepeats: number
}

export interface BingoAdminCardDto extends SaveBingoCardRequestDto {
  id: string
  season: number
  completedRepeats: number
  completed: boolean
}

export interface BingoAdminDetailsDto {
  season: number
  structureEditable: boolean
  canCopyPrevious: boolean
  data: BingoAdminCardDto[]
}
