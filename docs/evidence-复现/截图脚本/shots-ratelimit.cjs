// 「接口限流」证据截图：应用需以 --rate-limit.limit=3 --rate-limit.window-seconds=60 启动在 8086
const path = require('path');
const { chromium } = require('playwright-core');

const BASE = process.env.BASE_URL || 'http://localhost:8086';
const OUT = path.join(__dirname, '..', '限流');
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';

const waitReply = page => page.waitForFunction(
  () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
        !document.querySelector('.message.typing'),
  null, { timeout: 90000 });

const countErrors = page => page.$$eval('.message.error', els => els.length);

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 860 }, locale: 'zh-CN' });

  // 页面加载本身会 GET /api/models，占掉 1 次额度（limit=3 时剩 2 次）
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);
  console.log('页面加载完成，额度已用 1 次');

  // 第 1 条消息：正常
  await page.fill('#inputBox', '说一句你好');
  await page.click('.send-btn');
  await waitReply(page);
  await page.waitForTimeout(300);
  console.log('第1条消息：错误气泡数 =', await countErrors(page));
  await page.screenshot({ path: path.join(OUT, '02-限流前-正常回复.png') });

  // 第 2 条消息：正常（额度用尽）
  await page.fill('#inputBox', '再说一句');
  await page.click('.send-btn');
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 3 &&
          !document.querySelector('.message.typing') || document.querySelector('.message.error'),
    null, { timeout: 90000 });
  await page.waitForTimeout(300);
  console.log('第2条消息：错误气泡数 =', await countErrors(page));

  // 第 3 条消息：应被 429 拦截
  await page.fill('#inputBox', '第三条应该被拦住');
  await page.click('.send-btn');
  await page.waitForSelector('.message.error', { timeout: 30000 });
  await page.waitForTimeout(300);

  const errors = await page.$$eval('.message.error', els => els.map(e => e.textContent.trim()));
  const bots = await page.$$eval('.message.bot:not(.typing)', els =>
    els.slice(1).map(e => e.textContent.trim().slice(0, 30)));
  console.log('错误气泡 =', JSON.stringify(errors));
  console.log('机器人回复条数 =', bots.length);
  await page.screenshot({ path: path.join(OUT, '03-触发限流-错误气泡.png') });

  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
