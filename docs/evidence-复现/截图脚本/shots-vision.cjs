// 复现「图片识别 Vision」功能点的截图脚本
// 用法: node shots-vision.cjs  （需先启动应用；测试图片用 ../图片识别/vision-test.png）
const path = require('path');
const { chromium } = require('playwright-core');

const BASE = process.env.BASE_URL || 'http://localhost:8083';
const OUT = path.join(__dirname, '..', '图片识别');
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const IMG = path.join(OUT, 'vision-test.png');
const TXT = path.join(OUT, 'not-image.txt');

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 860 }, locale: 'zh-CN' });
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);

  // 1. 选远程模型 + 通过隐藏 file input 附上测试图片，截「预览条 + 📎 高亮」状态
  await page.selectOption('#modelSelect', 'remote-deepseek');
  await page.setInputFiles('#imageInput', IMG);
  await page.waitForSelector('#previewBar.show', { timeout: 10000 });
  await page.fill('#inputBox', '描述这张图片');
  console.log('预览条可见 =', await page.isVisible('#previewBar.show'));
  console.log('📎 按钮 active =', await page.$eval('#uploadBtn', el => el.classList.contains('active')));
  await page.screenshot({ path: path.join(OUT, '01-附图与预览.png') });

  // 2. 点击发送，走 POST /api/vision（multipart），等模型识别结果
  await page.click('.send-btn');
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
          !document.querySelector('.message.typing'),
    null, { timeout: 120000 });
  await page.waitForTimeout(500);
  const reply = await page.$$eval('.message.bot:not(.typing)', els =>
    els[els.length - 1].textContent);
  console.log('识别结果前 100 字 =', reply.slice(0, 100).replace(/\n/g, ' '));
  await page.screenshot({ path: path.join(OUT, '02-识别结果.png') });

  // 3. 反向验证类型校验：塞一个 .txt 进去，前端不拦截，后端应返回「只支持图片文件」
  await page.click('.new-chat-btn');
  await page.setInputFiles('#imageInput', TXT);
  await page.waitForTimeout(500);
  await page.fill('#inputBox', '看看这个文件');
  await page.click('.send-btn');
  await page.waitForSelector('.message.error', { timeout: 30000 });
  await page.waitForTimeout(300);
  console.log('错误气泡 =', await page.$eval('.message.error', el => el.textContent));
  await page.screenshot({ path: path.join(OUT, '03-非图片被拒.png') });

  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
