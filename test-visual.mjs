import { chromium } from 'playwright';
import fs from 'fs';

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  const requests_400 = [];

  // Capture errors
  page.on('console', msg => {
    if (msg.type() === 'error') {
      errors.push(msg.text());
    }
  });

  // Capture HTTP responses
  page.on('response', resp => {
    if (resp.status() === 400) {
      requests_400.push({
        url: resp.url(),
        status: resp.status()
      });
    }
  });

  try {
    console.log('📍 Loading app...');
    await page.goto('http://localhost', { waitUntil: 'load', timeout: 30000 });

    console.log('✅ App loaded');

    // Take screenshot
    await page.screenshot({ path: '/tmp/app-screenshot.png' });
    console.log('📸 Screenshot saved');

    // Check page content
    const title = await page.title();
    console.log(`📄 Page title: ${title}`);

    // Wait a bit for any initial requests
    await page.waitForTimeout(2000);

    console.log(`\n📊 Results:`);
    console.log(`  - Console errors: ${errors.length}`);
    if (errors.length > 0) {
      errors.slice(0, 5).forEach(e => console.log(`    • ${e.substring(0, 100)}`));
    }

    console.log(`  - 400 errors: ${requests_400.length}`);
    if (requests_400.length > 0) {
      requests_400.slice(0, 5).forEach(r => console.log(`    • ${r.url}`));
    }

    if (errors.length === 0 && requests_400.length === 0) {
      console.log('\n✅ No repeated 400 errors or console errors on page load!');
    }

  } catch (error) {
    console.error('❌ Error:', error.message);
    process.exit(1);
  } finally {
    await browser.close();
  }
})();
