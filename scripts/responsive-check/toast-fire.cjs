const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1024, height: 800 } });
  const page = await ctx.newPage();
  await page.goto("http://finans-frontend/", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(2500);
  // Try firing a toast through window-exposed react-hot-toast (it isn't exported globally),
  // so instead simulate by injecting a manual toast div with the same styling — no, just inspect
  // the bundle.
  // We can't trigger a toast without auth, but we can verify the Toaster container exists by
  // checking for the unique class react-hot-toast creates: ".__react-hot-toast" or "[role='status']"
  const result = await page.evaluate(() => {
    const all = document.querySelectorAll("body > *");
    return Array.from(all).map((el) => ({
      tag: el.tagName,
      cls: el.className?.toString?.() || "",
      role: el.getAttribute("role"),
      id: el.id,
    }));
  });
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})();
