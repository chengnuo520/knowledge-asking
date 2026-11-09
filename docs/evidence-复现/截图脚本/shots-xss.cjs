// 「Markdown 渲染」证据 2：桩服务返回固定 payload（含 XSS 尝试）→ 验证转义与渲染分支
// 前置：node ../桩服务/stub-openai.cjs 9099 --reply-file=../Markdown渲染/xss-payload.md
//       应用以桩配置启动在 8084
const path = require('path');
const { chromium } = require('playwright-core');

const BASE = process.env.BASE_URL || 'http://localhost:8084';
const OUT = path.join(__dirname, '..', 'Markdown渲染');
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 980 }, locale: 'zh-CN' });

  const dialogs = [];
  page.on('dialog', async d => { dialogs.push(d.message()); await d.dismiss(); });

  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);
  await page.selectOption('#modelSelect', 'remote-deepseek');

  await page.fill('#inputBox', '跑一遍渲染自检');
  await page.click('.send-btn');
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
          !document.querySelector('.message.typing'),
    null, { timeout: 60000 });
  await page.waitForTimeout(500);

  const report = await page.evaluate(() => {
    const bubble = [...document.querySelectorAll('.message.bot:not(.typing)')].pop();
    return {
      // 1) 白名单语法是否被渲染成真实标签
      codeBlocks: bubble.querySelectorAll('pre.md-code').length,
      inlineCode: bubble.querySelectorAll('code.md-inline').length,
      listItems: bubble.querySelectorAll('ul.md-ul li, ol.md-ol li').length,
      quotes: bubble.querySelectorAll('blockquote.md-quote').length,
      strong: bubble.querySelectorAll('strong').length,
      // 2) 恶意标签是否只作为文本存在
      imgInsideBubble: bubble.querySelectorAll('img').length,
      scriptInsideBubble: bubble.querySelectorAll('script').length,
      textContainsImgLiteral: bubble.textContent.includes("<img src=x onerror=alert('xss')>"),
      // 3) 代码块内的 ** 不应被当成粗体
      strongInsideCode: bubble.querySelectorAll('pre.md-code strong').length
    };
  });
  console.log('XSS/渲染检查 =', JSON.stringify(report, null, 2));
  console.log('触发的弹窗 =', dialogs.length ? dialogs : '无（符合预期）');

  await page.screenshot({ path: path.join(OUT, '05-XSS用例-转义后仅显示文本.png') });
  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
