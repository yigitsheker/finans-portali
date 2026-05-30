const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1024, height: 800 } });
  const page = await ctx.newPage();
  await page.goto("http://finans-frontend/", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(2500);
  // Find any aria-live region anywhere
  const result = await page.evaluate(() => {
    const ariaLive = document.querySelectorAll("[aria-live]");
    const out = [];
    ariaLive.forEach((el) => {
      out.push({
        tag: el.tagName,
        cls: el.className?.toString?.() || "",
        role: el.getAttribute("role"),
        aria: el.getAttribute("aria-live"),
      });
    });
    // also look for portal divs
    const portals = document.querySelectorAll("[class*='go']");
    portals.forEach((p) => {
      if (p.outerHTML.length < 100) out.push({ portalHint: p.outerHTML });
    });
    return out;
  });
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})();
