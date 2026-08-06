import { test as base, expect } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.route('/api/v1/auth/refresh', async route => {
      const profile = await page.evaluate(() => {
        const value = localStorage.getItem('smartqueue.profile');
        return value ? JSON.parse(value) : null;
      });

      if (!profile || profile.forceRefreshFailure) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ success: false, error: { message: 'No test session' } }),
        });
        return;
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: { ...profile, accessToken: 'mock-refresh-access-token' },
        }),
      });
    });
    await use(page);
  },
});

export { expect };
