// 「Markdown 渲染」证据 1：真实模型回复 → 渲染效果（应用在 8083）
const path = require('path');
const { chromium } = require('playwright-core');

const BASE = process.env.BASE_URL || 'http://localhost:8083';
const OUT = path.join(__dirname, '..', 'Markdown渲染');
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';

const PROMPT = '请用 Markdown 简洁回答，总长控制在 15 行以内：一个二级标题；两个要点的无序列表；一段 Java 代码（用三反引号包裹）；最后一句话用粗体。';

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1180 }, locale: 'zh-CN' });
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);
  await page.selectOption('#modelSelect', 'remote-deepseek');

  await page.fill('#inputBox', PROMPT);
  await page.click('.send-btn');
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
          !document.querySelector('.message.typing'),
    null, { timeout: 120000 });
  await page.waitForTimeout(500);

  const stats = await page.evaluate(() => {
    const bubble = [...document.querySelectorAll('.message.bot:not(.typing)')].pop();
    return {
      hasCodeBlock: !!bubble.querySelector('pre.md-code'),
      codeBlocks: bubble.querySelectorAll('pre.md-code').length,
      hasUnorderedList: !!bubble.querySelector('ul.md-ul'),
      listItems: bubble.querySelectorAll('ul.md-ul li, ol.md-ol li').length,
      headings: bubble.querySelectorAll('.md-h').length,
      strongTags: bubble.querySelectorAll('strong').length,
      rawTextLength: bubble.textContent.length,
      rawMarkdownLeft: /\*\*|```/.test(bubble.textContent)   // 仍残留 ** 或 ``` 说明没渲染干净
    };
  });
  console.log('渲染统计 =', JSON.stringify(stats, null, 2));

  // 把回复气泡顶部滚到视野内，完整截下整段渲染效果
  await page.evaluate(() => {
    const bubble = [...document.querySelectorAll('.message.bot:not(.typing)')].pop();
    bubble.scrollIntoView({ block: 'start' });
  });
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(OUT, '03-渲染后-真实回复.png') });

  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
