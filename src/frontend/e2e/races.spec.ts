import {
  expect,
  test,
  raceRow,
  seedPrediction,
  selectOption,
  selectSeason,
  signIn,
  transitionRace,
} from './fixtures'

test('F1-E2E-05: the draft stays hidden and publishing the result awards 55 points', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    leagues: [league],
    races: [race],
    drivers,
  } = await createScenario({ raceTypes: ['RACE'], adminRights: ['EDIT_RACE_RESULTS'] })
  await signIn(adminPage, admin)
  await transitionRace(adminPage, race, 'open')
  await signIn(page, member)
  await seedPrediction(page, league, race)
  await transitionRace(adminPage, race, 'close')

  await adminPage.goto(`/admin/results/${race.id}`)
  await adminPage.getByRole('textbox', { name: 'Incidents', exact: true }).fill('1')
  await adminPage.getByRole('textbox', { name: "Winner's gap" }).fill('2')
  await adminPage.getByRole('button', { name: 'Save draft' }).click()
  await expect(adminPage.getByText('Result draft saved.')).toBeVisible()

  await page.goto(`/races/${race.id}`)
  await expect(page.getByText('Race results have not been published yet.')).toBeVisible()
  const classification = page.getByRole('table', { name: 'Published race classification' })
  await expect(classification).toHaveCount(0)

  await adminPage.getByRole('button', { name: 'Calculate & finish' }).click()
  await expect(adminPage.getByText('Race finished and prediction points calculated.')).toBeVisible()
  await page.getByRole('button', { name: 'Refresh race' }).click()
  await expect(page.getByText('Finished', { exact: true })).toBeVisible()
  await expect(classification.getByRole('row')).toHaveCount(drivers.length)
  await expect(
    page.getByRole('region', { name: 'Your points' }).getByRole('heading', { name: '55' }),
  ).toBeVisible()

  await page.getByRole('link', { name: 'Standings', exact: true }).click()
  const standings = page.getByRole('table', { name: `${season} season standings` })
  const row = standings.getByRole('row').filter({ hasText: member.name })
  await expect(row.getByRole('cell').last()).toHaveText('55')
})

test('F1-E2E-07: sprints are visible only in leagues that enable them', async ({
  page,
  createScenario,
}) => {
  const {
    season,
    member,
    leagues: [withoutSprints, withSprints],
    races: [race, sprint],
  } = await createScenario({ sprintsEnabled: [false, true], raceTypes: ['RACE', 'SPRINT'] })
  await signIn(page, member)
  await selectSeason(page, season)
  await selectOption(page, 'League', withSprints.name)
  await expect(raceRow(page, race)).toBeVisible()
  await expect(raceRow(page, sprint)).toBeVisible()

  await selectOption(page, 'League', withoutSprints.name)
  await expect(raceRow(page, race)).toBeVisible()
  await expect(raceRow(page, sprint)).toHaveCount(0)
})

test('F1-E2E-08: a saved prediction updates when a driver is replaced', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    member,
    admin,
    leagues: [league],
    races: [race],
    drivers,
  } = await createScenario({
    raceTypes: ['RACE'],
    adminRights: ['EDIT_RACE_RESULTS', 'EDIT_RACE_LINEUPS'],
  })
  await signIn(adminPage, admin)
  await transitionRace(adminPage, race, 'open')
  await signIn(page, member)
  await seedPrediction(page, league, race)
  await page.goto(`/races/${race.id}`)
  const pick = page.getByRole('combobox', { name: '10th place', exact: true })
  await expect(pick).toContainText(drivers[9])

  await adminPage.goto(`/admin/teams/${race.id}`)
  const row = adminPage.getByRole('row').filter({
    has: adminPage.getByRole('button', { name: `Move driver ${drivers[9]}`, exact: true }),
  })
  const replacement = 'Reserve Driver'
  await row.getByRole('textbox', { name: 'Driver 2', exact: true }).fill(replacement)
  const save = adminPage.getByRole('button', { name: 'Save lineup' })
  await save.click()
  await expect(adminPage.getByText('Lineup saved.', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Refresh race' }).click()
  await expect(pick).toContainText(replacement)
  await expect(page.getByText('Saved', { exact: true })).toBeVisible()
})
