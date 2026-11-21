import type { Locator, Page } from '@playwright/test'
import type { BingoAdminCardDto, BingoAdminDetailsDto } from '../src/dto/bingo'
import { expect, signIn, test } from './fixtures'

const adminPath = '/adminApi/v1/bingo'

test.beforeEach(async ({ adminPage }) => {
  await adminPage.clock.setFixedTime(new Date('2032-06-01T12:00:00Z'))
})

async function state(page: Page): Promise<BingoAdminDetailsDto> {
  const response = await page.request.get(adminPath)
  await expect(response).toBeOK()
  return response.json()
}

async function addLanguage(form: Locator, code: string, description: string) {
  await form.getByRole('textbox', { name: 'Language code', exact: true }).fill(code)
  await form.getByRole('button', { name: 'Add language', exact: true }).click()
  await form.getByRole('textbox', { name: `Title (${code})`, exact: true }).fill(description)
}

for (const mobile of [false, true]) {
  test(`F1-E2E-${mobile ? '19' : '18'}: bingo admin edits translations and progress on ${mobile ? 'mobile' : 'desktop'}`, async ({
    adminPage,
    createScenario,
  }) => {
    const { admin } = await createScenario({ season: 2025, adminRights: ['EDIT_BINGO'] })
    if (mobile) await adminPage.setViewportSize({ width: 390, height: 844 })
    await signIn(adminPage, admin)
    if (mobile) await adminPage.getByRole('button', { name: 'Open sidebar' }).click()
    await adminPage.locator('a[href="/admin/bingo"]').click()
    await expect(adminPage.getByRole('heading', { name: 'Bingo administration' })).toBeVisible()
    const serverSeason = (await state(adminPage)).season
    await expect(
      adminPage.getByText(`Current season ${serverSeason} · Shared across all leagues.`),
    ).toBeVisible()

    const description = `Grid penalty ${crypto.randomUUID().slice(0, 8)}`
    const updatedDescription = `${description} updated`
    let cardId: string | undefined
    try {
      await adminPage.getByRole('button', { name: 'Add card', exact: true }).click()
      const dialog = adminPage.getByRole('dialog', { name: 'Add bingo card' })
      await expect(dialog.getByRole('button', { name: 'Create card', exact: true })).toBeDisabled()
      await dialog.getByRole('textbox', { name: 'Title (en)', exact: true }).fill(description)
      await dialog
        .getByRole('textbox', { name: 'Description (en)', exact: true })
        .fill('A grid penalty is applied.')
      await dialog.getByRole('spinbutton', { name: 'Required repeats' }).fill('3')
      await addLanguage(dialog, 'ru', 'Штраф на стартовой решётке')
      await dialog.getByRole('button', { name: 'Create card', exact: true }).click()
      await expect(dialog).not.toBeVisible()
      cardId = (await state(adminPage)).data.find(
        (card) => card.translations.en.description === description,
      )?.id
      expect(cardId).toBeTruthy()
      const row = adminPage.getByRole('row', { name: `Card: ${description}`, exact: true })
      await expect(row).toBeVisible()
      await expect(row).not.toContainText('Probability')
      await expect(row).not.toContainText('A grid penalty is applied.')
      await expect(row).not.toContainText('Edit & translations')
      await expect(adminPage.getByRole('textbox', { name: 'Title (ru)', exact: true })).toHaveCount(
        0,
      )
      await row
        .getByRole('button', { name: `Increase progress for ${description}`, exact: true })
        .click()
      await row
        .getByRole('button', { name: `Increase progress for ${description}`, exact: true })
        .click()
      await expect(row.getByLabel(`Progress for ${description}`, { exact: true })).toHaveText(
        '2 / 3',
      )
      await row
        .getByRole('button', { name: `Save changes for ${description}`, exact: true })
        .click()
      await expect
        .poll(
          async () =>
            (await state(adminPage)).data.find((card) => card.id === cardId)?.completedRepeats,
        )
        .toBe(2)

      await row.getByRole('button', { name: 'Edit & translations', exact: true }).click()
      const form = adminPage.getByRole('form', { name: 'Card details', exact: true })
      await expect(form.getByRole('button', { name: 'Save card', exact: true })).toHaveCount(0)
      await form.getByRole('textbox', { name: 'Title (en)', exact: true }).fill(updatedDescription)
      await row
        .getByRole('button', { name: `Decrease progress for ${description}`, exact: true })
        .click()
      await row
        .getByRole('button', { name: `Save changes for ${description}`, exact: true })
        .click()
      const updatedRow = adminPage.getByRole('row', {
        name: `Card: ${updatedDescription}`,
        exact: true,
      })
      await expect(updatedRow).toBeVisible()
      await expect
        .poll(
          async () =>
            (await state(adminPage)).data.find((card) => card.id === cardId)?.completedRepeats,
        )
        .toBe(1)
      await expect(form.getByRole('textbox', { name: 'Title (en)', exact: true })).toHaveValue(
        updatedDescription,
      )
      await updatedRow
        .getByRole('button', { name: `Increase progress for ${updatedDescription}`, exact: true })
        .click()
      await updatedRow
        .getByRole('button', { name: `Save changes for ${updatedDescription}`, exact: true })
        .click()
      await expect
        .poll(
          async () =>
            (await state(adminPage)).data.find((card) => card.id === cardId)?.completedRepeats,
        )
        .toBe(2)
      await adminPage.getByRole('button', { name: 'Refresh bingo', exact: true }).click()
      await expect(
        adminPage.getByRole('button', { name: 'Refresh bingo', exact: true }),
      ).toBeEnabled()
      await expect(form.getByRole('textbox', { name: 'Title (en)', exact: true })).toHaveValue(
        updatedDescription,
      )
      await updatedRow.getByRole('button', { name: 'Edit & translations', exact: true }).click()
      await updatedRow.getByRole('button', { name: 'Edit & translations', exact: true }).click()
      await expect(form.getByRole('textbox', { name: 'Title (en)', exact: true })).toHaveValue(
        updatedDescription,
      )
      await addLanguage(form, 'de', 'Startplatzstrafe')
      await updatedRow
        .getByRole('button', { name: `Save changes for ${updatedDescription}`, exact: true })
        .click()
      await expect(
        updatedRow.getByRole('button', {
          name: `Save changes for ${updatedDescription}`,
          exact: true,
        }),
      ).toBeDisabled()
      await expect
        .poll(
          async () =>
            (await state(adminPage)).data.find((card) => card.id === cardId)?.translations.de
              .description,
        )
        .toBe('Startplatzstrafe')
      const saved = (await state(adminPage)).data.find((card) => card.id === cardId)
      expect(saved?.completedRepeats).toBe(2)
      expect(saved?.translations.ru.description).toBe('Штраф на стартовой решётке')

      await adminPage.reload()
      await expect(
        updatedRow.getByLabel(`Progress for ${updatedDescription}`, { exact: true }),
      ).toHaveText('2 / 3')
      await updatedRow.getByRole('button', { name: 'Edit & translations', exact: true }).click()
      await expect(form.getByRole('textbox', { name: 'Title (de)', exact: true })).toHaveValue(
        'Startplatzstrafe',
      )
      await expect(
        form.getByRole('button', { name: 'Remove translation en', exact: true }),
      ).toHaveCount(0)
      await adminPage.screenshot({
        path: `test-results/bingo-admin-${mobile ? 'mobile' : 'desktop'}.png`,
        fullPage: true,
        animations: 'disabled',
      })
      expect(
        await adminPage.evaluate('document.documentElement.scrollWidth <= window.innerWidth'),
      ).toBe(true)
      await form.getByRole('button', { name: 'Remove translation ru', exact: true }).click()
      const saveChanges = updatedRow.getByRole('button', {
        name: `Save changes for ${updatedDescription}`,
        exact: true,
      })
      await saveChanges.click()
      await expect(saveChanges).toBeDisabled()
      await expect
        .poll(
          async () =>
            (await state(adminPage)).data.find((card) => card.id === cardId)?.translations.ru,
        )
        .toBeUndefined()
      await form.getByRole('button', { name: 'Delete card', exact: true }).click()
      await adminPage
        .getByRole('dialog', { name: 'Delete card?' })
        .getByRole('button', { name: 'Delete', exact: true })
        .click()
      await expect(updatedRow).toHaveCount(0)
    } finally {
      if (cardId) await adminPage.request.delete(`${adminPath}/cards/${cardId}`)
    }
  })
}

test('F1-E2E-20: bingo admin keeps local edits after a failed save and permits an explicit retry', async ({
  adminPage,
  createScenario,
}) => {
  const { admin } = await createScenario({ adminRights: ['EDIT_BINGO'] })
  await signIn(adminPage, admin)
  const response = await adminPage.request.post(`${adminPath}/cards`, {
    data: {
      translations: { en: { description: 'Event to edit', explanation: null } },
      probability: 'LOW',
      requiredRepeats: 2,
    },
  })
  await expect(response).toBeOK()
  const card: BingoAdminCardDto = await response.json()
  let attempts = 0
  let failRefresh = false
  await adminPage.route('**/adminApi/v1/bingo', async (route) => {
    if (failRefresh) {
      await route.fulfill({ status: 503, json: { detail: 'Temporary refresh outage' } })
    } else {
      await route.continue()
    }
  })
  await adminPage.route(`**/adminApi/v1/bingo/cards/${card.id}`, async (route) => {
    if (route.request().method() === 'PUT' && ++attempts === 1) {
      await route.fulfill({ status: 409, json: { detail: 'Please review the card and retry.' } })
    } else {
      failRefresh = true
      await route.continue()
    }
  })
  try {
    await adminPage.goto('/admin/bingo')
    const row = adminPage.getByRole('row', { name: 'Card: Event to edit', exact: true })
    await row.getByRole('button', { name: 'Edit & translations', exact: true }).click()
    const form = adminPage.getByRole('form', { name: 'Card details', exact: true })
    await form.getByRole('textbox', { name: 'Title (en)', exact: true }).fill('Edited event')
    const saveChanges = row.getByRole('button', {
      name: 'Save changes for Event to edit',
      exact: true,
    })
    await saveChanges.click()
    await expect(
      adminPage.getByText('Please review the card and retry.', { exact: true }),
    ).toBeVisible()
    await expect(form.getByRole('textbox', { name: 'Title (en)', exact: true })).toHaveValue(
      'Edited event',
    )
    expect(attempts).toBe(1)
    await saveChanges.click()
    await expect(
      adminPage.getByRole('row', { name: 'Card: Edited event', exact: true }),
    ).toBeVisible()
    await expect(adminPage.getByText('Temporary refresh outage', { exact: true })).toBeVisible()
    await expect(form.getByRole('textbox', { name: 'Title (en)', exact: true })).toHaveValue(
      'Edited event',
    )
    expect(attempts).toBe(2)
  } finally {
    await adminPage.request.delete(`${adminPath}/cards/${card.id}`)
  }
})

test('F1-E2E-21: bingo admin shows copied cards, zero progress and locked repeat targets', async ({
  adminPage,
  createScenario,
}) => {
  const { admin } = await createScenario({ adminRights: ['EDIT_BINGO'] })
  await signIn(adminPage, admin)
  const season = (await state(adminPage)).season
  const cards: BingoAdminCardDto[] = Array.from({ length: 25 }, (_, index) => ({
    id: crypto.randomUUID(),
    season,
    translations: { en: { description: `Copied event ${index + 1}`, explanation: null } },
    probability: 'MEDIUM',
    requiredRepeats: 3,
    completedRepeats: 0,
    completed: false,
  }))
  let copied = false
  let issued = false
  const details = (): BingoAdminDetailsDto => ({
    season,
    structureEditable: !issued,
    canCopyPrevious: !copied,
    data: copied ? cards : [],
  })
  await adminPage.route('**/adminApi/v1/bingo', (route) => route.fulfill({ json: details() }))
  await adminPage.route('**/adminApi/v1/bingo/cards/copy-previous', async (route) => {
    expect(route.request().method()).toBe('POST')
    copied = true
    await route.fulfill({ json: details() })
  })
  await adminPage.goto('/admin/bingo')
  await adminPage
    .getByRole('button', { name: `Copy cards from ${season - 1}`, exact: true })
    .click()
  await expect(adminPage.getByText('25 / 25 cards', { exact: true })).toBeVisible()
  await expect(adminPage.getByRole('button', { name: 'Add card', exact: true })).toBeDisabled()
  const first = adminPage.getByRole('row', { name: 'Card: Copied event 1', exact: true })
  await expect(first.getByLabel('Progress for Copied event 1', { exact: true })).toHaveText('0 / 3')
  issued = true
  await adminPage.getByRole('button', { name: 'Refresh bingo', exact: true }).click()
  await first.getByRole('button', { name: 'Edit & translations', exact: true }).click()
  const form = adminPage.getByRole('form', { name: 'Card details', exact: true })
  await expect(adminPage.getByText(/Boards have already been issued/)).toHaveCount(0)
  await expect(form.getByRole('spinbutton', { name: 'Required repeats' })).toBeDisabled()
  await expect(form.getByRole('textbox', { name: 'Title (en)', exact: true })).toBeEnabled()
  await expect(form.getByRole('combobox', { name: 'Probability', exact: true })).toBeEnabled()
  await expect(form.getByRole('button', { name: 'Delete card', exact: true })).toHaveCount(0)
})

test('F1-E2E-22: bingo administration is hidden and inaccessible without EDIT_BINGO', async ({
  page,
  createScenario,
}) => {
  const { member } = await createScenario()
  await signIn(page, member)
  await expect(page.locator('a[href="/admin/bingo"]')).toHaveCount(0)
  await page.goto('/admin/bingo')
  await expect(page.getByRole('heading', { name: 'Access denied', exact: true })).toBeVisible()
  expect((await page.request.get(adminPath)).status()).toBe(403)
})
