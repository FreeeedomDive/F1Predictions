import type { RaceLineupTeamDto, SaveRaceLineupRequestDto } from '../../dto/raceLineup'

export interface TeamDraft {
  id: string
  previousName: string | null
  team: Omit<RaceLineupTeamDto, 'drivers'>
  drivers: DriverDraft[]
}

export interface DriverDraft {
  id: string
  name: string
}

export const hexColor = /^#[0-9a-f]{6}$/i
export const maxDraftDrivers = 3
export const maxSavedDrivers = 2

export function createTeamDraft(
  id: string,
  previousName: string | null,
  value: RaceLineupTeamDto,
): TeamDraft {
  const { drivers, ...team } = value
  return {
    id,
    previousName,
    team,
    drivers: Array.from({ length: Math.max(maxSavedDrivers, drivers.length) }, (_, index) => ({
      id: `driver:${id}:${index}`,
      name: drivers[index] ?? '',
    })),
  }
}

export function teamValue({ team, drivers }: TeamDraft): RaceLineupTeamDto {
  return {
    ...team,
    drivers: drivers.map((driver) => driver.name).filter((name) => name.trim() !== ''),
  }
}

export function isNamedDriver(driver: DriverDraft) {
  return driver.name.trim() !== ''
}

export function moveDriver(
  rows: TeamDraft[],
  source: { droppableId: string; index: number },
  destination: { droppableId: string; index: number },
  driverId: string,
  emptySlot: DriverDraft,
): TeamDraft[] {
  const from = rows.find((row) => row.id === source.droppableId)
  const to = rows.find((row) => row.id === destination.droppableId)
  const moved = from?.drivers[source.index]
  if (!from || !to || !moved || moved.id !== driverId || !isNamedDriver(moved)) return rows
  if (from === to && source.index === destination.index) return rows

  const sourceSlots = from.drivers.filter((slot) => slot.id !== moved.id)
  const targetSlots = from === to ? sourceSlots : to.drivers
  const targetDrivers = targetSlots.filter(isNamedDriver)
  if (from !== to && targetDrivers.length >= maxDraftDrivers) return rows
  const targetIndex = targetSlots.slice(0, destination.index).filter(isNamedDriver).length
  targetDrivers.splice(targetIndex, 0, moved)

  function withEmptySlots(occupied: DriverDraft[], allSlots: DriverDraft[]) {
    if (occupied.length >= maxSavedDrivers) return occupied
    const empty = allSlots.filter((slot) => !isNamedDriver(slot))
    return [...occupied, ...[...empty, emptySlot].slice(0, maxSavedDrivers - occupied.length)]
  }

  return rows.map((row) => {
    if (row === to) return { ...row, drivers: withEmptySlots(targetDrivers, targetSlots) }
    if (row === from) {
      return { ...row, drivers: withEmptySlots(sourceSlots.filter(isNamedDriver), sourceSlots) }
    }
    return row
  })
}

export function normalizeTeam(team: RaceLineupTeamDto): RaceLineupTeamDto {
  return {
    name: team.name.trim(),
    badge: team.badge.trim(),
    backgroundColor: team.backgroundColor.trim().toUpperCase(),
    textColor: team.textColor.trim().toUpperCase(),
    drivers: team.drivers.map((driver) => driver.trim()).filter(Boolean),
  }
}

export function sameTeam(left: RaceLineupTeamDto, right: RaceLineupTeamDto) {
  return (
    left.name === right.name &&
    left.badge === right.badge &&
    left.backgroundColor === right.backgroundColor &&
    left.textColor === right.textColor &&
    sameDrivers(left.drivers, right.drivers)
  )
}

export function sameDrivers(left: string[], right: string[]) {
  return left.length === right.length && left.every((driver, index) => driver === right[index])
}

export function sameTeams(left: RaceLineupTeamDto[], right: RaceLineupTeamDto[]) {
  return left.length === right.length && left.every((team, index) => sameTeam(team, right[index]))
}

export function analyzeDraft(baseTeams: RaceLineupTeamDto[], drafts: TeamDraft[]) {
  const before = new Map(baseTeams.flatMap((team) => team.drivers.map((name) => [name, team.name])))
  const teams = drafts.map((row) => {
    const { previousName } = row
    const team = teamValue(row)
    const original = baseTeams.find((item) => item.name === previousName)
    const current = normalizeTeam(team)
    return {
      previousName,
      team: {
        name: team.name === original?.name ? original.name : current.name,
        badge: team.badge === original?.badge ? original.badge : current.badge,
        backgroundColor:
          current.backgroundColor === original?.backgroundColor.toUpperCase()
            ? original.backgroundColor
            : current.backgroundColor,
        textColor:
          current.textColor === original?.textColor.toUpperCase()
            ? original.textColor
            : current.textColor,
        drivers: team.drivers
          .map((name) => (before.has(name) ? name : name.trim()))
          .filter(Boolean),
      },
    }
  })
  const after = new Map(
    teams.flatMap(({ previousName, team }) => team.drivers.map((name) => [name, previousName])),
  )
  const removed = [...before.keys()].filter((name) => !after.has(name))
  const added = [...after.keys()].filter((name) => !before.has(name))
  const needsPairing =
    removed.length > 0 &&
    !(removed.length === 1 && added.length === 1 && before.get(removed[0]) === after.get(added[0]))
  return { teams, removed, added, needsPairing }
}

export function selectedReplacements(
  removed: string[],
  added: string[],
  pairs: ReadonlyMap<string, string>,
): SaveRaceLineupRequestDto['replacements'] {
  return removed.flatMap((previousDriverName) => {
    const newDriverName = pairs.get(previousDriverName)
    return newDriverName && added.includes(newDriverName)
      ? [{ previousDriverName, newDriverName }]
      : []
  })
}
