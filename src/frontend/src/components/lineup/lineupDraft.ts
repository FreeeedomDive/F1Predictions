import type { RaceLineupTeamDto, SaveRaceLineupRequestDto } from '../../dto/raceLineup'

export interface TeamDraft {
  id: string
  previousName: string | null
  team: RaceLineupTeamDto
}

export const hexColor = /^#[0-9a-f]{6}$/i

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
  const teams = drafts.map(({ previousName, team }) => {
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
