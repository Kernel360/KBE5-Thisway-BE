import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { writeFile } from 'node:fs/promises';
const require = createRequire(resolve('../KBE5-Thisway-FE/package.json'));
const { chromium } = require('@playwright/test');
const [url, output] = process.argv.slice(2);
if (!/^http:\/\/(127\.0\.0\.1|localhost):[0-9]+$/.test(url)) throw new Error('Only loopback fixture URLs are allowed');
const browser = await chromium.launch({headless:true});
try {
  const page = await browser.newPage({viewport:{width:1440,height:1100}});
  const errors=[]; page.on('pageerror', error => errors.push(error.name));
  await page.goto(url+'/d/thisway-reliability?from=now-5m&to=now', {waitUntil:'networkidle'});
  await page.getByText('HTTP requests / second',{exact:true}).waitFor();
  await page.waitForFunction(() => document.querySelectorAll('canvas').length > 0);
  await page.waitForTimeout(1500); // Let Grafana finish canvas sizing after panel mount.
  await page.screenshot({path:resolve(output,'dashboard-top.png')});
  await page.mouse.move(1100,700); await page.mouse.wheel(0,1000);
  await page.waitForTimeout(1500);
  await page.screenshot({path:resolve(output,'dashboard-middle.png')});
  await page.mouse.wheel(0,1400); await page.waitForTimeout(1500);
  await page.screenshot({path:resolve(output,'dashboard-bottom.png')});
  await page.mouse.wheel(0,2500); await page.waitForTimeout(1500);
  await page.getByText('Admitted to DB commit p95 / consumer attempt',{exact:true}).waitFor();
  await page.screenshot({path:resolve(output,'dashboard-commit.png')});
  await writeFile(resolve(output,'browser.json'),JSON.stringify({capturedAt:new Date().toISOString(),pageErrors:errors,viewport:{width:1440,height:1100}},null,2));
  if (errors.length) throw new Error('Grafana page errors recorded');
} finally {await browser.close();}
