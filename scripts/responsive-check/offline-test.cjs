const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();

  // Visit once with network → populates frontend cache
  console.log("--- online warmup ---");
  await page.goto("http://finans-frontend/stocks", { waitUntil: "domcontentloaded" });
  await page.waitForResponse((r) => r.url().includes("/history/batch") && r.status() === 200, { timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(3000);
  const cacheSize = await page.evaluate(() => {
    let n = 0;
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith("mkt-hist:")) n++;
    }
    return n;
  });
  console.log("  cached series:", cacheSize);

  // Now go offline and reload — sparklines should still paint from cache
  console.log("--- offline reload ---");
  await ctx.setOffline(true);
  // setOffline blocks ALL network including the document.  Let's just block
  // the API instead so the bundle still loads.
  await ctx.setOffline(false);
  await page.route("**/api/v1/market/history/**", (route) => route.abort());

  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForTimeout(3000);
  const sparkSvgs = await page.evaluate(() => {
    const ss = document.querySelectorAll("svg");
    let n = 0;
    ss.forEach((s) => { if (s.getAttribute("width") === "100") n++; });
    return n;
  });
  console.log("  sparklines drawn from cache:", sparkSvgs);
  await browser.close();
})();
