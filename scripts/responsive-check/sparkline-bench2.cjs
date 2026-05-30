const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  let batchHits = [];
  let singleHits = 0;
  page.on("response", async (resp) => {
    const u = resp.url();
    if (u.includes("/api/v1/market/history/batch")) {
      batchHits.push({ status: resp.status(), ms: 0 });
    } else if (u.includes("/api/v1/market/history")) {
      singleHits++;
    }
  });
  console.log("--- visit stocks page ---");
  const t0 = Date.now();
  await page.goto("http://finans-frontend/stocks", { waitUntil: "domcontentloaded" });
  // Wait until at least one batch request comes back
  await page.waitForResponse((r) => r.url().includes("/history/batch") && r.status() === 200, { timeout: 60000 }).catch(() => {});
  const t1 = Date.now();
  console.log("  page load → first batch resp:", (t1 - t0), "ms");
  await page.waitForTimeout(3000);
  // count canvases
  const canvases = await page.evaluate(() => document.querySelectorAll("canvas").length);
  console.log("  canvases on page:", canvases);
  console.log("  batch requests:", batchHits.length, "  single requests:", singleHits);
  // localStorage cache check
  const cacheSize = await page.evaluate(() => {
    let n = 0;
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith("mkt-hist:")) n++;
    }
    return n;
  });
  console.log("  cached series in localStorage:", cacheSize);
  console.log("--- reload (warm cache) ---");
  const t2 = Date.now();
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1000);
  const canvases2 = await page.evaluate(() => document.querySelectorAll("canvas").length);
  const t3 = Date.now();
  console.log("  reload → 1s later:", (t3 - t2), "ms, canvases:", canvases2);
  await browser.close();
})();
