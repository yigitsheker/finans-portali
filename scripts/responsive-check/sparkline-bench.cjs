const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  let batchHits = 0;
  let singleHits = 0;
  page.on("request", (req) => {
    const u = req.url();
    if (u.includes("/api/v1/market/history/batch")) batchHits++;
    else if (u.includes("/api/v1/market/history")) singleHits++;
  });
  console.log("--- First visit (cold cache) ---");
  const t0 = Date.now();
  await page.goto("http://finans-frontend/stocks", { waitUntil: "domcontentloaded" });
  // Wait for sparklines to appear
  await page.waitForFunction(() => {
    return document.querySelectorAll("canvas").length > 5;
  }, { timeout: 30000 }).catch(() => {});
  const t1 = Date.now();
  console.log("  page load → sparklines ready:", (t1 - t0), "ms");
  console.log("  batch requests:", batchHits, "  single requests:", singleHits);
  await page.waitForTimeout(2000);
  // Reload — should be near-instant from localStorage
  console.log("--- Second visit (warm localStorage) ---");
  batchHits = 0; singleHits = 0;
  const t2 = Date.now();
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => {
    return document.querySelectorAll("canvas").length > 5;
  }, { timeout: 30000 }).catch(() => {});
  const t3 = Date.now();
  console.log("  reload → sparklines ready:", (t3 - t2), "ms");
  console.log("  batch requests:", batchHits, "  single requests:", singleHits);
  await browser.close();
})();
