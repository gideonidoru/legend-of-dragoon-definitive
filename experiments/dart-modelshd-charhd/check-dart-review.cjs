// AGPL v3. Headless review QA using an existing local Playwright installation.
// Usage: node check-dart-review.cjs PLAYWRIGHT_DIR CHROME_EXECUTABLE NEW_OUTPUT_DIR URL
const fs = require('fs');
const path = require('path');
const [playwright, executablePath, output, url] = process.argv.slice(2);
if (!playwright || !executablePath || !output || !url || fs.existsSync(output)) {
  throw new Error('Supply existing Playwright, Chrome, a new output directory and review URL');
}
const {chromium} = require(playwright);
fs.mkdirSync(output, {recursive: true});
(async () => {
  const browser = await chromium.launch({headless: true, executablePath, args: ['--enable-unsafe-swiftshader']});
  const page = await browser.newPage({viewport: {width: 1200, height: 1200}});
  const errors = [];
  page.on('pageerror', error => errors.push(String(error)));
  await page.goto(url, {waitUntil: 'networkidle'});
  await page.waitForFunction(() => window.dartReview?.loaded);
  const poses = [];
  for (const pose of ['neutral', 'ready', 'guard', 'relaxed', 'stride', 'before']) {
    await page.selectOption('#pose', pose);
    await page.waitForFunction(p => window.dartReview?.pose === p, pose);
    poses.push(await page.evaluate(() => ({pose: window.dartReview.pose, triangles: window.dartReview.triangles})));
    await page.addStyleTag({content: 'header,.note,.controls,#stats{visibility:hidden}'});
    for (const [angle, yaw] of [['front', 0], ['quarter', .6], ['side', Math.PI / 2], ['rear', Math.PI]]) {
      await page.evaluate(y => window.dartReview.view(y), yaw);
      await page.waitForTimeout(180);
      await page.screenshot({path: path.join(output, pose + '-' + angle + '.png')});
    }
    await page.addStyleTag({content: 'header,.note,.controls,#stats{visibility:visible}'});
  }
  await page.selectOption('#pose', 'ready');
  await page.waitForFunction(() => window.dartReview.pose === 'ready');
  await page.click('#clay');
  if (await page.getAttribute('#clay', 'aria-pressed') !== 'true') throw new Error('Clay control failed');
  await page.screenshot({path: path.join(output, 'clay-viewer.png')});
  await page.click('#wire');
  if (await page.getAttribute('#wire', 'aria-pressed') !== 'true') throw new Error('Wire control failed');
  await page.click('#wire');
  await page.click('#colour');
  await page.click('#turn');
  if (await page.getAttribute('#turn', 'aria-pressed') !== 'true') throw new Error('Turntable failed');
  await page.click('#turn');
  await page.click('#reset');
  await page.screenshot({path: path.join(output, 'desktop-viewer.png')});
  // A late obsolete request must not replace the latest selected pose.
  const racing = await browser.newPage();
  await racing.route('**/neutral.glb', async route => {await new Promise(resolve => setTimeout(resolve, 250)); await route.continue();});
  await racing.goto(url);
  await racing.waitForFunction(() => window.dartReview?.loaded);
  await racing.evaluate(async () => {await Promise.all([window.dartReview.setPose('neutral'), window.dartReview.setPose('guard')]);});
  if (await racing.evaluate(() => window.dartReview.pose) !== 'guard') throw new Error('Late load replaced selection');
  // Failed load is visible and recoverable, while the prior actor is retained.
  await racing.route('**/stride.glb', route => route.abort());
  await racing.selectOption('#pose', 'stride');
  await racing.waitForFunction(() => document.querySelector('#status').classList.contains('error'));
  await racing.unroute('**/stride.glb');
  await racing.selectOption('#pose', 'ready');
  await racing.waitForFunction(() => window.dartReview.pose === 'ready' && document.querySelector('#status').hidden);
  const mobile = await browser.newPage({viewport: {width: 390, height: 844}, hasTouch: true, isMobile: true});
  mobile.on('pageerror', error => errors.push(String(error)));
  await mobile.goto(url);
  await mobile.waitForFunction(() => window.dartReview?.loaded);
  await mobile.tap('#clay');
  if (await mobile.getAttribute('#clay', 'aria-pressed') !== 'true') throw new Error('Touch selection failed');
  await mobile.tap('#colour');
  await mobile.screenshot({path: path.join(output, 'touch-viewer.png')});
  const report = {browser: browser.version(), url, poses, pageErrors: errors,
    controls: ['pose', 'clay', 'wire', 'turntable', 'reset', 'touch'], staleLoadProtected: true,
    visibleFailureAndRecovery: true, nativeGame: false, steamDeck: false};
  fs.writeFileSync(path.join(output, 'browser-qa.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report));
  await browser.close();
  if (errors.length) process.exitCode = 1;
})().catch(error => {console.error(error); process.exit(1);});
