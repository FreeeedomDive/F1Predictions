import type { Locator, Page } from '@playwright/test'
import type { ChampionshipStageDto } from '../src/dto/championship'
import { expect, selectOption, selectSeason, signIn, test } from './fixtures'

test.beforeEach(async ({ page, adminPage }) => {
  const now = new Date('2032-06-01T12:00:00Z')
  await page.clock.setFixedTime(now)
  await adminPage.clock.setFixedTime(now)
})

function adminPath(season: number) {
  return `/adminApi/v1/championship/${season}`
}

async function openStage(
  adminPage: Page,
  season: number,
  stage: ChampionshipStageDto,
  lineupRaceId: string,
) {
  await expect(
    await adminPage.request.put(`${adminPath(season)}/stages/${stage}/open`, {
      data: { lineupRaceId },
    }),
  ).toBeOK()
}

async function closeStage(adminPage: Page, season: number, stage: ChampionshipStageDto) {
  await expect(await adminPage.request.put(`${adminPath(season)}/stages/${stage}/close`)).toBeOK()
}

async function openPreSeason(adminPage: Page, season: number, lineupRaceId: string) {
  await expect(
    await adminPage.request.put(`${adminPath(season)}/configuration`, {
      data: { rulesVersion: 'championship-v2027' },
    }),
  ).toBeOK()
  await openStage(adminPage, season, 'PRE_SEASON', lineupRaceId)
}

async function seedPrediction(
  page: Page,
  season: number,
  leagueId: string,
  driverStandings: string[],
  stage: ChampionshipStageDto = 'PRE_SEASON',
) {
  await expect(
    await page.request.put(
      `/api/v1/leagues/${leagueId}/championship/${season}/predictions/${stage}`,
      { data: { driverStandings } },
    ),
  ).toBeOK()
}

function preSeason(page: Page) {
  return page.getByRole('region', { name: 'Pre-season prediction' })
}

function adminChampionshipLink(page: Page) {
  return page.locator('a[href="/admin/championship"]')
}

async function moveDriverDown(page: Page, table: Locator, driver: string) {
  await table.getByRole('button', { name: `Move ${driver}`, exact: true }).focus()
  await page.keyboard.press('Space')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Space')
}

test('F1-E2E-09: a reordered championship prediction survives reload', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    drivers: [first, second],
  } = await createScenario({
    season: 2027,
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await signIn(page, member)
  await selectSeason(page, season)
  await page.getByRole('link', { name: 'Championship', exact: true }).click()

  const card = preSeason(page)
  const firstPlace = card.getByRole('row').nth(1)
  await expect(firstPlace).toContainText(first)
  await moveDriverDown(page, card, first)
  await expect(firstPlace).toContainText(second)
  await card.getByRole('button', { name: 'Save prediction' }).click()
  await expect(card.getByText('Saved', { exact: true })).toBeVisible()

  await page.reload()
  await selectSeason(page, season)
  await expect(firstPlace).toContainText(second)
})

test('F1-E2E-10: a championship prediction is visible only in its league', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    leagues: [first, second],
    drivers,
  } = await createScenario({
    season: 2028,
    sprintsEnabled: [false, false],
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await signIn(page, member)
  await seedPrediction(page, season, first.id, drivers)
  await selectSeason(page, season)
  await selectOption(page, 'League', first.name)
  await page.getByRole('link', { name: 'Championship', exact: true }).click()

  await expect(preSeason(page).getByText('Saved', { exact: true })).toBeVisible()
  await selectOption(page, 'League', second.name)
  await expect(preSeason(page).getByText('Not saved', { exact: true })).toBeVisible()
})

test('F1-E2E-11: closing pre-season locks its prediction and publishes points', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    leagues: [league],
    drivers,
  } = await createScenario({
    season: 2029,
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await expect(
    await adminPage.request.put(`${adminPath(season)}/standings`, {
      data: { driverStandings: drivers },
    }),
  ).toBeOK()
  await signIn(page, member)
  await seedPrediction(page, season, league.id, drivers)
  await selectSeason(page, season)
  await page.getByRole('link', { name: 'Championship', exact: true }).click()
  const card = preSeason(page)
  const points = page.getByRole('table', { name: 'League championship points' })
  await expect(card.getByText('Open', { exact: true })).toBeVisible()
  await expect(points).toHaveCount(0)

  await expect(await adminPage.request.put(`${adminPath(season)}/stages/PRE_SEASON/close`)).toBeOK()
  await page.getByRole('button', { name: 'Refresh championship' }).click()
  await expect(card.getByText('Closed', { exact: true })).toBeVisible()
  await expect(card.getByRole('button', { name: `Move ${drivers[0]}`, exact: true })).toHaveCount(0)
  await expect(card.getByRole('button', { name: 'Save prediction' })).toHaveCount(0)
  await expect(
    points.getByRole('row').filter({ hasText: member.name }).getByRole('cell').last(),
  ).toHaveText('200')
})

test('F1-E2E-12: a missing mid-season prediction is shown as a pre-season copy', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    leagues: [league],
    drivers,
  } = await createScenario({
    season: 2030,
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await signIn(page, member)
  const order = [drivers[1], drivers[0], ...drivers.slice(2)]
  await seedPrediction(page, season, league.id, order)
  await openStage(adminPage, season, 'MID_SEASON', race.id)
  await closeStage(adminPage, season, 'MID_SEASON')

  await selectSeason(page, season)
  await page.getByRole('link', { name: 'Championship', exact: true }).click()
  const mid = page.getByRole('region', { name: 'Mid-season prediction' })
  await expect(mid.getByText('Copied from pre-season', { exact: true })).toBeVisible()
  await expect(mid.getByRole('row').nth(1)).toContainText(order[0])
})

test('F1-E2E-13: a championship admin opens a stage from a saved race lineup', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [sprint, race],
    drivers,
  } = await createScenario({
    season: 2031,
    raceTypes: ['SPRINT', 'RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(page, member)
  await expect(adminChampionshipLink(page)).toHaveCount(0)
  await page.goto('/admin/championship')
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible()

  await signIn(adminPage, admin)
  await selectSeason(adminPage, season)
  await adminChampionshipLink(adminPage).click()
  await adminPage.getByRole('button', { name: 'Configure season' }).click()
  await adminPage.getByRole('checkbox', { name: 'Predictions open' }).check()
  const lineup = adminPage.getByRole('combobox', { name: 'Race lineup' })
  await lineup.click()
  await expect(adminPage.getByRole('option', { name: sprint.name })).toHaveCount(0)
  await adminPage.getByRole('option', { name: race.name }).click()
  await adminPage.getByRole('button', { name: 'Save stage' }).click()
  await expect(lineup).toHaveCount(0)

  await selectSeason(page, season)
  await page.getByRole('link', { name: 'Championship', exact: true }).click()
  await expect(preSeason(page).getByText('Open', { exact: true })).toBeVisible()
  await expect(preSeason(page).getByRole('row').nth(1)).toContainText(drivers[0])
})

test('F1-E2E-14: a manual standings update leaves the prediction stage open', async ({
  adminPage,
  createScenario,
}) => {
  const {
    season,
    admin,
    races: [race],
    drivers,
  } = await createScenario({
    season: 2032,
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await selectSeason(adminPage, season)
  await adminChampionshipLink(adminPage).click()
  const standings = adminPage.getByRole('region', { name: 'Official driver standings' })
  await standings.getByRole('button', { name: 'Use stage driver list' }).click()
  await moveDriverDown(adminPage, standings, drivers[0])
  await standings.getByRole('button', { name: 'Save standings' }).click()
  await expect(adminPage.getByText('Standings saved.')).toBeVisible()

  await adminPage.reload()
  await selectSeason(adminPage, season)
  await expect(standings.getByRole('row').nth(1)).toContainText(drivers[1])
  await expect(adminPage.getByRole('checkbox', { name: 'Predictions open' })).toBeChecked()
})

test('F1-E2E-15: the admin sees a missing mid-season prediction become a copy', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    leagues: [league],
    drivers,
  } = await createScenario({
    season: 2025,
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  if (!admin) throw new Error('Expected a championship admin in the fixture')
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await signIn(page, member)
  await seedPrediction(page, season, league.id, drivers)
  await openStage(adminPage, season, 'MID_SEASON', race.id)
  await seedPrediction(adminPage, season, league.id, drivers, 'MID_SEASON')

  await selectSeason(adminPage, season)
  await adminChampionshipLink(adminPage).click()
  const statuses = adminPage.getByRole('table', { name: 'Prediction statuses' })
  const memberMidSeason = statuses
    .getByRole('row')
    .filter({ hasText: member.name })
    .getByRole('cell')
    .last()
  const adminMidSeason = statuses
    .getByRole('row')
    .filter({ hasText: admin.name })
    .getByRole('cell')
    .last()
  await expect(memberMidSeason).toBeEmpty()
  await adminPage.getByRole('checkbox', { name: 'Predictions open' }).uncheck()
  await adminPage.getByRole('button', { name: 'Save stage' }).click()
  await expect(memberMidSeason.getByRole('img', { name: 'Copied from pre-season' })).toBeVisible()
  await expect(adminMidSeason.getByRole('img', { name: 'Saved' })).toBeVisible()
})

test('F1-E2E-16: the admin filters prediction statuses without changing the sidebar league', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    leagues: [first, second],
    drivers,
  } = await createScenario({
    season: 2024,
    sprintsEnabled: [false, false],
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await signIn(page, member)
  await seedPrediction(page, season, first.id, drivers)

  await selectSeason(adminPage, season)
  await selectOption(adminPage, 'League', first.name)
  await adminChampionshipLink(adminPage).click()
  const statuses = adminPage.getByRole('region', { name: 'Prediction statuses' })
  const memberRow = statuses.getByRole('row').filter({ hasText: member.name })
  await expect(memberRow.getByRole('img', { name: 'Saved' })).toBeVisible()
  await statuses.getByRole('combobox', { name: 'League', exact: true }).click()
  await adminPage.getByRole('option', { name: second.name, exact: true }).click()
  await expect(statuses.getByRole('combobox', { name: 'League', exact: true })).toHaveText(
    second.name,
  )
  await expect(memberRow.getByRole('cell').nth(1)).toBeEmpty()
  await expect(
    adminPage
      .getByRole('complementary', { name: 'Workspace sidebar' })
      .getByRole('combobox', { name: 'League', exact: true }),
  ).toHaveText(first.name)
})

test('F1-E2E-17: rolling back a closed stage removes its automatic copies', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    leagues: [league],
    drivers,
  } = await createScenario({
    season: 2023,
    raceTypes: ['RACE'],
    adminRights: ['EDIT_CHAMPIONSHIP'],
  })
  await signIn(adminPage, admin)
  await openPreSeason(adminPage, season, race.id)
  await signIn(page, member)
  await seedPrediction(page, season, league.id, drivers)
  await openStage(adminPage, season, 'MID_SEASON', race.id)
  await closeStage(adminPage, season, 'MID_SEASON')

  await selectSeason(adminPage, season)
  await adminChampionshipLink(adminPage).click()
  const row = adminPage
    .getByRole('table', { name: 'Prediction statuses' })
    .getByRole('row')
    .filter({ hasText: member.name })
  const midSeason = row.getByRole('cell').last()
  await expect(midSeason.getByRole('img', { name: 'Copied from pre-season' })).toBeVisible()
  await selectOption(adminPage, 'Stage', 'Pre-season')
  await adminPage.getByRole('checkbox', { name: 'Predictions open' }).check()
  await adminPage.getByRole('button', { name: 'Save stage' }).click()
  await expect(midSeason).toBeEmpty()
  await expect(row.getByRole('img', { name: 'Saved' })).toBeVisible()

  await selectSeason(page, season)
  await page.getByRole('link', { name: 'Championship', exact: true }).click()
  await expect(preSeason(page).getByText('Open', { exact: true })).toBeVisible()
})
