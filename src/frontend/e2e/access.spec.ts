import { expect, test, selectOption, signIn } from './fixtures'

test('F1-E2E-01: sign in via a link, stay signed in after reload, and sign out', async ({
  page,
  createScenario,
}) => {
  const {
    member,
    leagues: [league],
  } = await createScenario()

  await page.goto('/calendar')
  await expect(page).toHaveURL(/\/auth$/)
  await expect(page.getByText('Not authorized', { exact: true })).toBeVisible()

  await signIn(page, member)
  await expect(page.getByRole('combobox', { name: 'League' })).toHaveText(league.name)
  await page.reload()
  await expect(page).toHaveURL(/\/calendar$/)
  await expect(page.getByRole('combobox', { name: 'League' })).toHaveText(league.name)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/auth$/)
  await page.goto('/calendar')
  await expect(page).toHaveURL(/\/auth$/)
  await expect(page.getByText('Not authorized', { exact: true })).toBeVisible()
})

test('F1-E2E-06: granting and revoking a right controls admin access', async ({
  page,
  adminPage,
  createScenario,
}) => {
  const { member, admin } = await createScenario({ adminRights: ['EDIT_RIGHTS'] })
  await signIn(page, member)
  await page.goto('/admin/races')
  const denied = page.getByText('Access denied', { exact: true })
  await expect(denied).toBeVisible()

  await signIn(adminPage, admin)
  await adminPage.goto('/admin/rights')
  await adminPage.getByRole('combobox', { name: 'User', exact: true }).fill(member.name)
  await adminPage.getByRole('option', { name: member.name }).click()
  await selectOption(adminPage, 'Right', 'Edit race results')
  await adminPage.getByRole('button', { name: 'Grant', exact: true }).click()
  await expect(adminPage.getByText('Edit race results granted.')).toBeVisible()

  await page.reload()
  await expect(page.getByRole('heading', { name: 'Race administration' })).toBeVisible()

  await adminPage
    .getByRole('button', { name: `Revoke Edit race results from ${member.name}` })
    .press('Delete')
  await expect(adminPage.getByText('Edit race results revoked.')).toBeVisible()
  await page.reload()
  await expect(denied).toBeVisible()
})
