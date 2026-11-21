import type { BingoBoardDto, BingoDetailsDto } from '../src/dto/bingo'
import { expect, selectOption, selectSeason, signIn, test } from './fixtures'

const activeSeason = new Date().getUTCFullYear()
const ownRoute = '**/api/v1/leagues/*/bingo/boards/me*'

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date(`${activeSeason + 3}-06-01T12:00:00Z`))
  await page.addInitScript("Object.defineProperty(navigator, 'language', { get: () => 'ru-RU' })")
})

function board(prefix: string): BingoBoardDto {
  return {
    id: crypto.randomUUID(),
    participantId: crypto.randomUUID(),
    bingoAchieved: true,
    cards: Array.from({ length: 25 }, (_, position) => {
      const description = `${prefix} event ${((position * 7) % 25) + 1}`
      return {
        id: crypto.randomUUID(),
        description,
        explanation: `Conditions for ${description}.`,
        language: 'en',
        probability: (['LOW', 'MEDIUM', 'HIGH'] as const)[position % 3],
        requiredRepeats: 3,
        completedRepeats: position === 0 ? 3 : position % 3,
        completed: position === 0,
      }
    }),
  }
}

function empty(season: number, canCreate = false): BingoDetailsDto {
  return { season, activeSeason, canCreate, board: null }
}

for (const mobile of [false, true]) {
  test(`F1-E2E-${mobile ? '24' : '23'}: own bingo is created once in English and keeps its five-column order on ${mobile ? 'mobile' : 'desktop'}`, async ({
    page,
    createScenario,
  }) => {
    const { member } = await createScenario({ season: activeSeason - 2 })
    const saved = board('Season')
    let created = false
    let writes = 0
    const languages: (string | null)[] = []
    await page.route(ownRoute, async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const season = Number(url.searchParams.get('season'))
      languages.push(url.searchParams.get('lang'))
      if (request.method() === 'PUT') {
        expect(season).toBe(activeSeason)
        created = true
        writes++
        await route.fulfill({ json: { ...empty(activeSeason), board: saved } })
      } else {
        await route.fulfill({
          json:
            season === activeSeason
              ? created
                ? { ...empty(activeSeason), board: saved }
                : empty(activeSeason, true)
              : empty(season),
        })
      }
    })
    if (mobile) await page.setViewportSize({ width: 390, height: 844 })
    await signIn(page, member)
    if (mobile) await page.getByRole('button', { name: 'Open sidebar' }).click()
    await selectSeason(page, activeSeason)
    await page.locator('a[href="/bingo"]').click()
    const list = page.getByRole('list', { name: 'Your bingo board', exact: true })
    const tiles = list.getByRole('article')
    await expect(tiles).toHaveCount(25)
    await expect(page.getByRole('alert').filter({ hasText: 'Bingo achieved!' })).toBeVisible()
    expect(writes).toBe(1)
    const order = await tiles.allTextContents()
    const first = await tiles.nth(0).boundingBox()
    const fifth = await tiles.nth(4).boundingBox()
    const sixth = await tiles.nth(5).boundingBox()
    expect(first).not.toBeNull()
    expect(fifth?.y).toBe(first?.y)
    expect(sixth!.y).toBeGreaterThan(first!.y)
    const info = tiles.first().getByRole('button', { name: 'Explanation for card 1', exact: true })
    const tooltip = page.getByRole('tooltip', { name: saved.cards[0].explanation!, exact: true })
    await info.hover()
    await expect(tooltip).toBeVisible()
    await page.getByRole('heading', { name: 'Bingo', exact: true }).hover()
    await expect(tooltip).not.toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await page.screenshot({
      path: `test-results/bingo-user-${mobile ? 'mobile' : 'desktop'}.png`,
      fullPage: true,
      animations: 'disabled',
    })
    expect(await page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')).toBe(
      true,
    )
    await page.reload()
    if (mobile) await page.getByRole('button', { name: 'Open sidebar' }).click()
    await selectSeason(page, activeSeason)
    if (mobile) await page.getByRole('button', { name: 'Close sidebar' }).click()
    await expect(tiles).toHaveCount(25)
    expect(await tiles.allTextContents()).toEqual(order)
    expect(writes).toBe(1)
    expect(languages.length).toBeGreaterThan(1)
    expect(languages.every((language) => language === 'en')).toBe(true)
    await page.getByRole('link', { name: 'Bingo rules', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Bingo', exact: true })).toBeVisible()
    await expect(
      page.getByText('Your board has 25 event cards and no free centre cell.', { exact: false }),
    ).toBeVisible()
  })
}

test('F1-E2E-25: archived bingo shows saved cards but never creates missing boards', async ({
  page,
  createScenario,
}) => {
  const { member } = await createScenario({ season: activeSeason - 2 })
  const saved = board('Archived')
  let writes = 0
  await page.route(ownRoute, async (route) => {
    const url = new URL(route.request().url())
    const season = Number(url.searchParams.get('season'))
    expect(url.searchParams.get('lang')).toBe('en')
    if (route.request().method() === 'PUT') writes++
    await route.fulfill({
      json: { ...empty(season), board: season === activeSeason - 1 ? saved : null },
    })
  })
  await signIn(page, member)
  await selectSeason(page, activeSeason - 1)
  await page.locator('a[href="/bingo"]').click()
  await expect(
    page.getByRole('list', { name: 'Your bingo board' }).getByRole('article'),
  ).toHaveCount(25)
  await expect(
    page.getByText('Archived season — your saved board is read-only.', { exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('spinbutton')).toHaveCount(0)
  await selectSeason(page, activeSeason - 2)
  await expect(
    page.getByText(
      `No saved bingo board for ${activeSeason - 2}. Archived seasons do not create new boards.`,
      { exact: true },
    ),
  ).toBeVisible()
  await expect(page.getByRole('list', { name: 'Your bingo board' })).toHaveCount(0)
  expect(writes).toBe(0)
})

test('F1-E2E-26: a read error is not an empty bingo season and unavailable cards do not trigger creation', async ({
  page,
  createScenario,
}) => {
  const { member } = await createScenario({ season: activeSeason - 1 })
  let failing = true
  let writes = 0
  await page.route(ownRoute, async (route) => {
    const url = new URL(route.request().url())
    expect(url.searchParams.get('lang')).toBe('en')
    if (route.request().method() === 'PUT') writes++
    await route.fulfill(
      failing
        ? { status: 503, json: { detail: 'Temporary bingo outage' } }
        : { json: empty(Number(url.searchParams.get('season'))) },
    )
  })
  await signIn(page, member)
  await selectSeason(page, activeSeason)
  await page.locator('a[href="/bingo"]').click()
  await expect(page.getByText('Temporary bingo outage', { exact: true })).toBeVisible()
  await expect(page.getByText(/Bingo cards are not ready/)).toHaveCount(0)
  failing = false
  await page.getByRole('button', { name: 'Retry', exact: true }).click()
  await expect(
    page.getByText(`Bingo cards are not ready for ${activeSeason}. Please check back later.`, {
      exact: true,
    }),
  ).toBeVisible()
  expect(writes).toBe(0)
})

test('F1-E2E-27: switching leagues never shows a board from the previous league', async ({
  page,
  createScenario,
}) => {
  const {
    member,
    leagues: [first, second],
  } = await createScenario({
    season: activeSeason - 1,
    sprintsEnabled: [false, false],
  })
  const saved = board('First league')
  await page.route(ownRoute, async (route) => {
    const url = new URL(route.request().url())
    const season = Number(url.searchParams.get('season'))
    expect(url.searchParams.get('lang')).toBe('en')
    expect(route.request().method()).toBe('GET')
    await route.fulfill({
      json: {
        ...empty(season),
        board: url.pathname.includes(first.id) && season === activeSeason ? saved : null,
      },
    })
  })
  await signIn(page, member)
  await selectSeason(page, activeSeason)
  await selectOption(page, 'League', first.name)
  await page.locator('a[href="/bingo"]').click()
  const list = page.getByRole('list', { name: 'Your bingo board', exact: true })
  await expect(list.getByRole('article')).toHaveCount(25)
  await selectOption(page, 'League', second.name)
  await expect(list).toHaveCount(0)
  await expect(page.getByText(saved.cards[0].description, { exact: true })).toHaveCount(0)
  await selectOption(page, 'League', first.name)
  await expect(list.getByRole('article')).toHaveCount(25)
})

test('F1-E2E-28: retrying a lost creation response reads the existing English board without a second PUT', async ({
  page,
  createScenario,
}) => {
  const { member } = await createScenario({ season: activeSeason - 1 })
  const saved = board('Recovered')
  let created = false
  let writes = 0
  await page.route(ownRoute, async (route) => {
    const url = new URL(route.request().url())
    const season = Number(url.searchParams.get('season'))
    expect(url.searchParams.get('lang')).toBe('en')
    if (route.request().method() === 'PUT') {
      created = true
      writes++
      await route.fulfill({ status: 503, json: { detail: 'Creation response lost' } })
    } else {
      await route.fulfill({
        json:
          season === activeSeason
            ? created
              ? { ...empty(season), board: saved }
              : empty(season, true)
            : empty(season),
      })
    }
  })
  await signIn(page, member)
  await selectSeason(page, activeSeason)
  await page.locator('a[href="/bingo"]').click()
  await expect(page.getByText('Creation response lost', { exact: true })).toBeVisible()
  expect(writes).toBe(1)
  await page.getByRole('button', { name: 'Retry', exact: true }).click()
  await expect(
    page.getByRole('list', { name: 'Your bingo board' }).getByRole('article'),
  ).toHaveCount(25)
  expect(writes).toBe(1)
})

test('F1-E2E-29: losing league access hides the cached bingo board', async ({
  page,
  createScenario,
}) => {
  const { member } = await createScenario({ season: activeSeason - 1 })
  const saved = board('Private')
  let denied = false
  await page.route(ownRoute, async (route) => {
    const url = new URL(route.request().url())
    expect(url.searchParams.get('lang')).toBe('en')
    await route.fulfill(
      denied
        ? { status: 403 }
        : { json: { ...empty(Number(url.searchParams.get('season'))), board: saved } },
    )
  })
  await signIn(page, member)
  await selectSeason(page, activeSeason)
  await page.locator('a[href="/bingo"]').click()
  const list = page.getByRole('list', { name: 'Your bingo board' })
  await expect(list.getByRole('article')).toHaveCount(25)
  denied = true
  await page.getByRole('button', { name: 'Refresh bingo', exact: true }).click()
  await expect(page.getByText('You do not have access to bingo.', { exact: true })).toBeVisible()
  await expect(list).toHaveCount(0)
})
