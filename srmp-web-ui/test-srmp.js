const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  console.log('正在打开前端页面: http://localhost:5181/');

  try {
    await page.goto('http://localhost:5181/', { waitUntil: 'networkidle', timeout: 30000 });
    console.log('✅ 页面加载成功');

    await page.waitForSelector('.leaflet-container', { timeout: 10000 });
    console.log('✅ Leaflet 地图已加载');

    // 输入路线编号并查询
    const inputs = await page.$$('input');
    console.log(`找到 ${inputs.length} 个输入框`);

    // 尝试输入
    for (let i = 0; i < inputs.length; i++) {
      const placeholder = await inputs[i].getAttribute('placeholder');
      const type = await inputs[i].getAttribute('type');
      console.log(`  输入框 ${i}: placeholder="${placeholder}", type="${type}"`);
    }

    if (inputs.length > 0) {
      await inputs[0].fill('G210');
      await page.waitForTimeout(300);
      const searchBtn = await page.$('button:has-text("查询")');
      if (searchBtn) {
        await searchBtn.click();
        console.log('✅ 已点击查询按钮');
        await page.waitForTimeout(5000);
      }
    }

    // 获取页面 HTML 内容
    const mapPaneHtml = await page.$eval('.leaflet-map-pane', el => el.innerHTML.substring(0, 500));
    console.log('\n地图面板内容 (前500字符):');
    console.log(mapPaneHtml);

    // 检查所有 SVG 元素
    const svgs = await page.$$('svg');
    console.log(`\nSVG 元素数量: ${svgs.length}`);

    // 检查所有 path 元素
    const paths = await page.$$('path');
    console.log(`Path 元素数量: ${paths.length}`);

    // 截图
    await page.screenshot({ path: '/tmp/srmp-gis-test.png', fullPage: true });
    console.log('\n✅ 截图已保存到 /tmp/srmp-gis-test.png');

  } catch (err) {
    console.error('❌ 测试失败:', err.message);
    await page.screenshot({ path: '/tmp/srmp-error.png' });
  } finally {
    await browser.close();
  }
})();
