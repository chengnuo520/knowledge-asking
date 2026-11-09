// 「零代码换上游」证据截图：应用跑在桩模式（BASE_URL=http://localhost:8084）
// 前置：1) node stub-openai.cjs 9099   2) 应用带 application-stub.yml 启动在 8084
const path = require('path');
const { chromium } = require('playwright-core');

const BASE = process.env.BASE_URL || 'http://localhost:8084';
const ROOT = path.join(__dirname, '..');
const OUT_MODEL = path.join(ROOT, '模型切换');
const OUT_VISION = path.join(ROOT, '图片识别');
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 860 }, locale: 'zh-CN' });

  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);
  console.log('下拉框首项 =', await page.$eval('#modelSelect option', el => el.textContent.trim()));

  // 纯文本请求 → 桩回显
  await page.selectOption('#modelSelect', 'remote-deepseek');
  await page.fill('#inputBox', '这条消息没有经过任何真实模型');
  await page.click('.send-btn');
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
          !document.querySelector('.message.typing'), null, { timeout: 60000 });
  await page.waitForTimeout(400);
  console.log('桩回复 =', await page.$$eval('.message.bot:not(.typing)', els =>
    els[els.length - 1].textContent));
  await page.screenshot({ path: path.join(OUT_MODEL, '05-桩模式-改配置换上游.png') });

  // 图片请求 → 桩回显多模态结构（text + image_url data-url）
  await page.click('.new-chat-btn');
  await page.setInputFiles('#imageInput', path.join(OUT_VISION, 'vision-test.png'));
  await page.waitForSelector('#previewBar.show');
  await page.fill('#inputBox', '描述这张图片');
  await page.click('.send-btn');
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
          !document.querySelector('.message.typing'), null, { timeout: 60000 });
  await page.waitForTimeout(400);
  console.log('桩回显 =', await page.$$eval('.message.bot:not(.typing)', els =>
    els[els.length - 1].textContent));
  await page.screenshot({ path: path.join(OUT_VISION, '04-桩回显-请求体结构.png') });

  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
