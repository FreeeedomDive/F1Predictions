export const rightLabels = {
  EDIT_RACE_RESULTS: 'Edit race results',
  EDIT_PREDICTIONS: 'Edit predictions',
  EDIT_RACE_LINEUPS: 'Edit race lineups',
  EDIT_RIGHTS: 'Manage rights',
} as const

export type RightDto = keyof typeof rightLabels

export const allRights = Object.keys(rightLabels) as RightDto[]

export interface UserRightsDto {
  userId: string
  names: string[]
  rights: RightDto[]
}
