const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1024, height: 800 } });
  const page = await ctx.newPage();
  await page.goto("http://finans-frontend/", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(2500);
  const result = await page.evaluate(() => {
    const ariaLive = document.querySelector("[aria-live]");
    const sections = document.querySelectorAll("section");
    return {
      hasAriaLive: !!ariaLive,
      ariaLiveSampleHtml: ariaLive ? (ariaLive.outerHTML.slice(0, 200)) : null,
      bodyChildrenCount: document.body.children.length,
    };
  });
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})();
