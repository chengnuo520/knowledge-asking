// 复现「模型切换」功能点的截图脚本
// 用法: node shots-model.cjs  （需先启动应用，默认 http://localhost:8083）
const path = require('path');
const { chromium } = require('playwright-core');

const BASE = process.env.BASE_URL || 'http://localhost:8083';
const OUT = path.join(__dirname, '..', '模型切换');
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';

(async () => {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 860 }, locale: 'zh-CN' });

  // 1. 首页：等待侧边栏下拉框被 /api/models 填充
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.querySelectorAll('#modelSelect option').length > 0);
  const options = await page.$$eval('#modelSelect option', els =>
    els.map(e => ({ value: e.value, text: e.textContent.trim() })));
  console.log('下拉框选项 =', JSON.stringify(options, null, 2));
  console.log('当前选中 =', await page.$eval('#modelSelect', el => el.value));
  await page.screenshot({ path: path.join(OUT, '01-首页-模型下拉框.png') });

  // 2. 直接访问 /api/models，浏览器原生渲染 JSON
  await page.goto(BASE + '/api/models', { waitUntil: 'networkidle' });
  await page.screenshot({ path: path.join(OUT, '02-api-models-返回.png') });

  // 3. 切到远程模型 remote-deepseek，发一条消息，拿到回复
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  await page.selectOption('#modelSelect', 'remote-deepseek');
  await page.fill('#inputBox', '用一句话说明你是谁');
  console.log('发送前 model =', await page.$eval('#modelSelect', el => el.value));
  await page.click('.send-btn');
  // 首条 .message.bot 是欢迎语，等「思考中」消失且至少出现两条正式回复
  await page.waitForFunction(
    () => document.querySelectorAll('.message.bot:not(.typing)').length >= 2 &&
          !document.querySelector('.message.typing'),
    null, { timeout: 90000 });
  await page.waitForTimeout(500);
  console.log('远程模型回复 =', (await page.$$eval('.message.bot:not(.typing)', els =>
    els[els.length - 1].textContent)).slice(0, 80));
  await page.screenshot({ path: path.join(OUT, '03-远程模型-对话成功.png') });

  // 4. 切到本地 Ollama 模型（本机未启动 Ollama），验证 model 参数真的改变了后端目标地址
  await page.click('.new-chat-btn');
  await page.selectOption('#modelSelect', 'ollama-deepseek-r1');
  console.log('切换后 model =', await page.$eval('#modelSelect', el => el.value));
  await page.fill('#inputBox', '你好');
  await page.click('.send-btn');
  await page.waitForSelector('.message.error', { timeout: 60000 });
  await page.waitForTimeout(300);
  console.log('错误气泡 =', await page.$eval('.message.error', el => el.textContent));
  await page.screenshot({ path: path.join(OUT, '04-本地模型-失败气泡.png') });

  await browser.close();
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
