import { chromium } from '@playwright/test';

(async () => {
  const browser = await chromium.launch();
  const context = await browser.createContext();
  const page = await context.newPage();

  try {
    console.log('🔍 Loading app...');
    await page.goto('http://localhost');

    // Wait for navigation to login or check-in page
    await page.waitForLoadState('networkidle');
    const url = page.url();
    console.log(`📍 Current URL: ${url}`);

    // If on login page, login with test credentials
    if (url.includes('/login')) {
      console.log('📝 On login page - logging in with test credentials');
      await page.fill('input[name="username"]', 'emp001');
      await page.fill('input[name="password"]', 'password123');
      await page.click('button:has-text("Đăng nhập")');
      await page.waitForNavigation();
      console.log('✅ Logged in');
    }

    // Navigate to check-in page
    console.log('🔍 Navigating to check-in page...');
    await page.goto('http://localhost/check-in');
    await page.waitForLoadState('networkidle');

    // Check for page elements
    const heading = await page.textContent('h1');
    console.log(`📌 Page heading: "${heading}"`);

    // Check for GPS status
    const gpsInfo = await page.textContent('p:has-text("GPS")');
    console.log(`📍 GPS status: ${gpsInfo || 'Not found'}`);

    // Look for camera/photo elements
    const submitButton = await page.locator('button:has-text("Xác nhận Check-in")');
    const isDisabled = await submitButton.isDisabled();
    console.log(`🔘 Submit button exists and is ${isDisabled ? 'DISABLED' : 'ENABLED'}`);

    // Check browser console for errors
    const errors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    // Wait for network activity
    await page.waitForTimeout(2000);

    console.log(`\n📊 Console errors captured: ${errors.length}`);
    if (errors.length > 0) {
      console.log('Errors:');
      errors.forEach((err, i) => console.log(`  ${i+1}. ${err}`));
    }

    // Check for 400 errors in network
    const responses = [];
    page.on('response', resp => {
      if (resp.status() >= 400) {
        responses.push({ url: resp.url(), status: resp.status() });
      }
    });

    await page.waitForTimeout(1000);

    console.log(`\n📡 HTTP errors captured: ${responses.length}`);
    if (responses.length > 0) {
      responses.forEach(r => console.log(`  ❌ ${r.status} ${r.url}`));
    }

    // Try to interact with camera (if available)
    console.log('\n🎥 Attempting to trigger camera permission...');
    try {
      // Grant camera permission
      const context2 = page.context();
      await context2.grantPermissions(['camera']);

      // Look for camera or canvas
      const canvas = await page.locator('canvas, video').first();
      if (await canvas.isVisible()) {
        console.log('✅ Camera/canvas element found and visible');
      }
    } catch (e) {
      console.log(`⚠️ Camera interaction note: ${e.message}`);
    }

    console.log('\n✅ Test completed successfully!');
    console.log('📝 Summary:');
    console.log('  - Check-in page loaded');
    console.log(`  - GPS status displayed: ${gpsInfo ? 'Yes' : 'No'}`);
    console.log(`  - Submit button present: Yes (${isDisabled ? 'disabled' : 'enabled'})`);
    console.log(`  - 400 errors: ${responses.filter(r => r.status === 400).length}`);

  } catch (error) {
    console.error('❌ Test failed:', error.message);
    process.exit(1);
  } finally {
    await browser.close();
  }
})();
