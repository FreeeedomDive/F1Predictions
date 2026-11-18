import { expect, test as base } from '@playwright/test'
import type { Page } from '@playwright/test'
import type { RaceDetailsDto, SavePredictionRequestDto } from '../src/dto/prediction'
import type { RaceTypeDto } from '../src/dto/race'
import type { RightDto } from '../src/dto/rights'

interface User {
  name: string
  token: string
}

interface NamedEntity {
  id: string
  name: string
}

interface Scenario {
  season: number
  member: User
  admin: User | null
  leagues: NamedEntity[]
  races: NamedEntity[]
  drivers: string[]
}

interface ScenarioOptions {
  sprintsEnabled?: boolean[]
  raceTypes?: RaceTypeDto[]
  adminRights?: RightDto[]
}

export const test = base.extend<{
  createScenario: (options?: ScenarioOptions) => Promise<Scenario>
  adminPage: Page
}>({
  createScenario: async ({ request }, fixtureUse) => {
    await fixtureUse(
      async ({ sprintsEnabled = [false], raceTypes = [], adminRights = [] } = {}) => {
        const response = await request.post('http://127.0.0.1:18081/e2e/fixtures', {
          data: { sprintsEnabled, raceTypes, adminRights },
        })
        expect(response.status(), 'E2E fixture creation').toBe(200)
        return response.json()
      },
    )
  },

  adminPage: async ({ browser, baseURL }, fixtureUse) => {
    const context = await browser.newContext({ baseURL })
    try {
      await fixtureUse(await context.newPage())
    } finally {
      await context.close()
    }
  },
})

export { expect }

export async function signIn(page: Page, user: User | null) {
  if (!user) throw new Error('The scenario has no user with admin rights')
  await page.goto(`/auth?token=${encodeURIComponent(user.token)}`)
  await expect(page).toHaveURL(/\/calendar$/)
  await expect(page.getByRole('heading', { name: 'Calendar', exact: true })).toBeVisible()
}

export async function selectOption(page: Page, field: string, value: string) {
  await page.getByRole('combobox', { name: field, exact: true }).click()
  await page.getByRole('option', { name: value, exact: true }).click()
}

export async function selectSeason(page: Page, season: number) {
  const field = page.getByRole('combobox', { name: 'Season', exact: true })
  await expect(field).toBeVisible()
  if ((await field.textContent())?.trim() !== String(season)) {
    await selectOption(page, 'Season', String(season))
  }
}

export function raceRow(page: Page, race: NamedEntity) {
  return page
    .getByRole('table', { name: 'Race calendar' })
    .getByRole('row')
    .filter({
      has: page.getByRole('link', { name: race.name, exact: true }),
    })
}

export async function transitionRace(adminPage: Page, race: NamedEntity, action: 'open' | 'close') {
  const response = await adminPage.request.patch(`/adminApi/v1/races/${race.id}/${action}`)
  await expect(response).toBeOK()
}

export async function seedPrediction(page: Page, league: NamedEntity, race: NamedEntity) {
  const path = `/api/v1/leagues/${league.id}/races/${race.id}`
  const response = await page.request.get(path)
  await expect(response).toBeOK()
  const { teams, conditions }: RaceDetailsDto = await response.json()
  const drivers = teams.flatMap((team) => team.drivers)
  const position =
    drivers.findIndex((driver) => driver === conditions?.positionPredictionDriver) + 1
  expect(position).toBeGreaterThan(0)
  const prediction: SavePredictionRequestDto = {
    tenthPlacePickedDriver: drivers[9],
    dnfPickedDrivers: [],
    safetyCarsPrediction: 'ONE',
    firstPlaceLeadPrediction: 2,
    driverPositionPrediction: position,
  }
  await expect(await page.request.put(`${path}/prediction`, { data: prediction })).toBeOK()
}
