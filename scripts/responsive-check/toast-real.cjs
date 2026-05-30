const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1024, height: 800 } });
  const page = await ctx.newPage();
  await page.goto("http://finans-frontend/", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(2500);

  // Try to surface react-hot-toast via the page. The library is bundled, but
  // not exposed as a global. We can't trigger it from outside without a UI hook.
  // So instead simulate one by triggering a click on something that fires notify().
  // The Settings page is auth-gated, the buy modal is auth-gated. So instead,
  // wait for the home page to load and check whether any auto-fired toast
  // (e.g. security greeting) shows up — none will, because user is anonymous.

  // Best we can do without auth: render a synthetic toast by injecting a
  // div that mimics the styling, OR check the React tree for Toaster.
  const result = await page.evaluate(() => {
    // Look for the Toaster container — it's a div with style top:16px right:16px etc.
    const candidates = document.querySelectorAll("div");
    for (const d of candidates) {
      const cs = window.getComputedStyle(d);
      if (cs.position === "fixed" && cs.top === "16px" && cs.right === "16px") {
        return { found: true, html: d.outerHTML.slice(0, 200) };
      }
      if (cs.position === "fixed" && cs.top !== "0px" && cs.zIndex !== "auto" && parseInt(cs.zIndex) > 9000) {
        return { found: true, html: d.outerHTML.slice(0, 200), hint: "high z-index fixed" };
      }
    }
    return { found: false };
  });
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})();
