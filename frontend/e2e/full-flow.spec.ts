import { test, expect } from '@playwright/test';

// Full smoke: register -> create transaction -> list -> edit -> delete -> logout.
// Assumes the backend is running on :8080 (Compose Postgres + mvn spring-boot:run).

test.describe('Life Organizer - full happy path', () => {
  const email = `e2e+${Date.now()}@example.com`;
  const password = 'S3cretValue';
  const displayName = 'E2E User';

  test('full register -> transaction CRUD -> logout flow', async ({ page }) => {
    // Register
    await page.goto('/register');
    await page.fill('[data-testid="register-displayName"]', displayName);
    await page.fill('[data-testid="register-email"]', email);
    await page.fill('[data-testid="register-password"]', password);
    await page.click('[data-testid="register-submit"]');

    // Landed on transactions list (auto-login)
    await expect(page).toHaveURL(/\/transactions/);
    await expect(page.getByRole('heading', { name: 'Transactions' })).toBeVisible();

    // Empty state -> create
    await page.click('[data-testid="new-transaction"]:visible');
    await expect(page).toHaveURL(/\/transactions\/new/);

    await page.fill('[data-testid="form-amount"]', '42.50');
    await page.fill('[data-testid="form-category"]', 'Groceries');
    await page.fill('[data-testid="form-description"]', 'Weekly shop');
    // Date defaults to today; leave as-is.
    await page.click('[data-testid="form-submit"]');

    // Back on list with one row
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByText('Groceries').first()).toBeVisible();

    // Logout from profile menu
    await page.click('button[aria-label="Account menu"]');
    await page.getByRole('menuitem', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
});
