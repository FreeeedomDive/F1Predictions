import type { Page } from '@playwright/test'
import {
  expect,
  test,
  raceRow,
  selectOption,
  selectSeason,
  signIn,
  transitionRace,
} from './fixtures'

const predictionForm = (page: Page) => page.getByRole('form', { name: 'Prediction' })

async function fillPrediction(page: Page, driver: string, gap = '2') {
  const form = predictionForm(page)
  await form.getByRole('combobox', { name: '10th place', exact: true }).click()
  await page
    .getByRole('option')
    .filter({ has: page.getByText(driver, { exact: true }) })
    .click()
  await selectOption(page, 'DNF', 'Nobody')
  await page.keyboard.press('Escape')
  await form
    .getByRole('group', { name: 'Incidents' })
    .getByRole('button', { name: '1', exact: true })
    .click()
  await form.getByRole('textbox', { name: "Winner's gap" }).fill(gap)
  await selectOption(page, 'Position', '3')
}

async function expectPrediction(page: Page, driver: string, gap = '2') {
  const form = predictionForm(page)
  await expect(form.getByRole('combobox', { name: '10th place', exact: true })).toContainText(
    driver,
  )
  await expect(form.getByRole('combobox', { name: 'DNF', exact: true })).toHaveText('Nobody')
  await expect(
    form.getByRole('group', { name: 'Incidents' }).getByRole('button', { name: '1', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true')
  await expect(form.getByRole('textbox', { name: "Winner's gap" })).toHaveValue(gap)
  await expect(form.getByRole('combobox', { name: 'Position', exact: true })).toHaveText('3')
}

async function savePrediction(page: Page) {
  await page.getByRole('button', { name: 'Save prediction' }).click()
  await expect(page.getByText('Prediction saved.', { exact: true })).toBeVisible()
}

test('F1-E2E-02: a complete prediction survives a page reload', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    drivers: [driver],
  } = await createScenario({ raceTypes: ['RACE'], adminRights: ['EDIT_RACE_RESULTS'] })
  await signIn(adminPage, admin)
  await transitionRace(adminPage, race, 'open')
  await signIn(page, member)
  await selectSeason(page, season)
  await page.getByRole('link', { name: race.name, exact: true }).click()

  await fillPrediction(page, driver)
  await savePrediction(page)
  await page.reload()
  await expect(page.getByText('Saved', { exact: true })).toBeVisible()
  await expectPrediction(page, driver)
})

test('F1-E2E-03: save a prediction to the current league, then to all leagues', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    member,
    admin,
    leagues: [leagueA, leagueB],
    races: [race],
    drivers: [driver],
  } = await createScenario({
    sprintsEnabled: [false, false],
    raceTypes: ['RACE'],
    adminRights: ['EDIT_RACE_RESULTS'],
  })
  await signIn(adminPage, admin)
  await transitionRace(adminPage, race, 'open')
  await signIn(page, member)
  await selectOption(page, 'League', leagueA.name)
  await page.goto(`/races/${race.id}`)

  await fillPrediction(page, driver, '4.5')
  await savePrediction(page)
  await selectOption(page, 'League', leagueB.name)
  await expect(predictionForm(page).getByRole('textbox', { name: "Winner's gap" })).toBeEmpty()
  await expect(page.getByText('Make your picks', { exact: true })).toBeVisible()

  await fillPrediction(page, driver, '6.5')
  await page.getByRole('button', { name: 'More save options' }).click()
  await page.getByRole('menuitem', { name: 'Save to all 2 leagues' }).click()
  await expect(page.getByRole('alert').getByText('Success', { exact: true })).toBeVisible()
  await page.reload()
  await expectPrediction(page, driver, '6.5')
  await selectOption(page, 'League', leagueA.name)
  await expectPrediction(page, driver, '6.5')
})

test('F1-E2E-04: a saved prediction becomes read-only when submissions close', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const {
    season,
    member,
    admin,
    races: [race],
    drivers: [driver],
  } = await createScenario({ raceTypes: ['RACE'], adminRights: ['EDIT_RACE_RESULTS'] })
  await signIn(adminPage, admin)
  await adminPage.goto('/admin/races')
  await selectSeason(adminPage, season)
  const row = raceRow(adminPage, race)
  const actions = row.getByRole('button', { name: `Actions for ${race.name} (Race)` })
  await expect(row.getByText('Scheduled', { exact: true })).toBeVisible()
  await actions.click()
  await adminPage.getByRole('menuitem', { name: 'Open predictions' }).click()
  await expect(row.getByText('Open', { exact: true })).toBeVisible()

  await signIn(page, member)
  await page.goto(`/races/${race.id}`)
  await fillPrediction(page, driver)
  await savePrediction(page)

  await actions.click()
  await adminPage.getByRole('menuitem', { name: 'Close predictions' }).click()
  await expect(row.getByText('Closed', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Refresh race' }).click()
  await expect(page.getByText('Predictions closed', { exact: true })).toBeVisible()
  await expect(page.getByText('Your saved prediction is read-only.')).toBeVisible()
  await expectPrediction(page, driver)
  await expect(predictionForm(page).getByRole('textbox', { name: "Winner's gap" })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Save prediction' })).toHaveCount(0)
})
