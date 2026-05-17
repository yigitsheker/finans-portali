// Small HTTP shim around Playwright headless Chrome.
// The Spring Boot backend calls this when a target site (e.g. investing.com)
// TLS-fingerprints or Cloudflare-challenges plain Java/curl requests.
//
// Keeps one shared browser instance warm and opens a fresh incognito context
// per request so cookies don't leak between callers.

import express from 'express';
import { chromium } from 'playwright';

const PORT = process.env.PORT || 3000;
const NAV_TIMEOUT_MS = 30_000;

const app = express();
app.use(express.json({ limit: '64kb' }));

let browser;

async function getBrowser() {
    if (browser && browser.isConnected()) return browser;
    browser = await chromium.launch({
        headless: true,
        args: [
            '--no-sandbox',
            '--disable-dev-shm-usage',
            '--disable-blink-features=AutomationControlled',
        ],
    });
    return browser;
}

app.get('/health', (_req, res) => {
    res.json({ ok: browser?.isConnected() === true });
});

app.post('/fetch', async (req, res) => {
    const { url, waitForSelector, waitMs } = req.body || {};
    if (!url || typeof url !== 'string') {
        return res.status(400).json({ error: 'url is required' });
    }

    let context;
    try {
        const b = await getBrowser();
        context = await b.newContext({
            userAgent:
                'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
                + '(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
            locale: 'tr-TR',
            timezoneId: 'Europe/Istanbul',
            viewport: { width: 1366, height: 900 },
            extraHTTPHeaders: {
                'Accept-Language': 'tr-TR,tr;q=0.9,en;q=0.8',
            },
        });
        const page = await context.newPage();

        const response = await page.goto(url, {
            waitUntil: 'domcontentloaded',
            timeout: NAV_TIMEOUT_MS,
        });

        // Best-effort: wait for a content marker or a fixed delay so the
        // Cloudflare/JS challenge clears before we read the DOM.
        if (waitForSelector) {
            try {
                await page.waitForSelector(waitForSelector, { timeout: 15_000 });
            } catch {
                // fall through — we still return whatever HTML we have
            }
        } else if (waitMs) {
            await page.waitForTimeout(Math.min(waitMs, 10_000));
        }

        const html = await page.content();
        const status = response ? response.status() : 0;
        const finalUrl = page.url();

        res.json({ status, finalUrl, html });
    } catch (err) {
        console.error('fetch error:', err.message);
        res.status(502).json({ error: err.message });
    } finally {
        if (context) await context.close().catch(() => {});
    }
});

app.listen(PORT, () => {
    console.log(`playwright-service listening on :${PORT}`);
    // Pre-warm the browser so the first /fetch isn't slow.
    getBrowser().catch((e) => console.error('browser launch failed:', e));
});
