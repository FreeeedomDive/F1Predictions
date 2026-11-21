export type BingoCardProbabilityDto = 'LOW' | 'MEDIUM' | 'HIGH'

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
