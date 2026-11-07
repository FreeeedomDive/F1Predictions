export type RaceTypeDto = 'RACE' | 'SPRINT'

export type RaceStatusDto = 'SCHEDULED' | 'OPEN' | 'CLOSED' | 'FINISHED'

export interface RaceDto {
  id: string
  name: string
  type: RaceTypeDto
  status: RaceStatusDto
  openAt: string
  closeAt: string
}
