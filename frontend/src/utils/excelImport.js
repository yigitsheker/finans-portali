/**
 * Shared Excel/CSV parser for the portfolio + historical bulk-import features.
 *
 * Reads the first sheet, maps the header row to our canonical fields
 * (symbol / lot / date) accepting common Turkish & English column names, and
 * returns normalized rows. The caller decides what to do with unknown symbols
 * (skip + count) — this layer only parses and validates the SHAPE of the file.
 */

const norm = (k) => String(k ?? "").trim().toLowerCase();
const pad = (x) => String(x).padStart(2, "0");

// Accepted header aliases (compared case-insensitively, trimmed).
const SYMBOL_KEYS = ["symbol", "sembol", "hisse", "kod", "ticker", "hisse kodu"];
const LOT_KEYS = ["lot", "adet", "quantity", "qty", "miktar", "lot sayısı", "lot sayisi", "adet/lot"];
const DATE_KEYS = [
    "tarih", "date", "alış tarihi", "alis tarihi", "alım tarihi", "alim tarihi",
    "alınma tarihi", "alinma tarihi", "buy date", "purchase date", "alış", "alis",
];

function findKey(keys, aliases) {
    return keys.find((k) => aliases.includes(norm(k)));
}

// Parse a lot/quantity cell. Numbers pass through untouched (Excel numeric
// cells arrive as JS numbers). For text, a dot is treated as a DECIMAL point
// unless a comma is also present — only then is it the Turkish thousands
// separator ("1.234,5" → 1234.5). This avoids silently turning "1.5" lots into
// 15 or "1.234" into 1234 when no decimal comma signals TR grouping.
function parseLot(v) {
    if (v == null || v === "") return NaN;
    if (typeof v === "number") return v;
    let s = String(v).trim().replace(/\s/g, "");
    if (s.includes(",")) s = s.replace(/\./g, "").replace(",", ".");
    const n = Number(s);
    return Number.isFinite(n) ? n : NaN;
}

// True only for a real calendar date (rejects month 13, day 32, 2024-02-31…).
function validYmd(y, m, d) {
    const dt = new Date(Date.UTC(y, m - 1, d));
    return dt.getUTCFullYear() === y && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d;
}

// Normalize a date cell to "yyyy-MM-dd", or null if unparseable / out of range.
// Handles Excel date objects (cellDates) and the common TR/ISO text formats.
function parseDate(v) {
    if (v == null || v === "") return null;
    if (v instanceof Date && !Number.isNaN(v.getTime())) {
        return `${v.getFullYear()}-${pad(v.getMonth() + 1)}-${pad(v.getDate())}`;
    }
    const s = String(v).trim();
    let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/); // yyyy-MM-dd
    if (m) {
        const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
        return validYmd(y, mo, d) ? `${m[1]}-${pad(mo)}-${pad(d)}` : null;
    }
    m = s.match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/); // dd.MM.yyyy / dd/MM/yyyy
    if (m) {
        const [d, mo, y] = [Number(m[1]), Number(m[2]), Number(m[3])];
        return validYmd(y, mo, d) ? `${m[3]}-${pad(mo)}-${pad(d)}` : null;
    }
    return null;
}

/**
 * Parse an uploaded portfolio file.
 * @param {File} file
 * @param {{ requireDate?: boolean }} opts when requireDate, a date column is mandatory
 * @returns {Promise<{ok:true, rows:{symbol:string, lot:number, date:string|null}[]}
 *                   | {ok:false, reason:"parse"|"empty"|"columns"}>}
 */
export async function parsePortfolioExcel(file, { requireDate = false } = {}) {
    let wb;
    let XLSX;
    try {
        // Lazy-loaded so the ~370 KB SheetJS bundle is split into its own chunk
        // and only fetched when a user actually imports a file.
        XLSX = await import("xlsx");
        const buf = await file.arrayBuffer();
        wb = XLSX.read(buf, { type: "array", cellDates: true });
    } catch {
        return { ok: false, reason: "parse" };
    }
    const ws = wb.Sheets[wb.SheetNames[0]];
    if (!ws) return { ok: false, reason: "empty" };

    const raw = XLSX.utils.sheet_to_json(ws, { defval: null });
    if (!raw.length) return { ok: false, reason: "empty" };

    const keys = Object.keys(raw[0]);
    const symKey = findKey(keys, SYMBOL_KEYS);
    const lotKey = findKey(keys, LOT_KEYS);
    const dateKey = findKey(keys, DATE_KEYS);
    if (!symKey || !lotKey || (requireDate && !dateKey)) {
        return { ok: false, reason: "columns" };
    }

    const rows = raw.map((r) => ({
        symbol: String(r[symKey] ?? "").trim().toUpperCase(),
        lot: parseLot(r[lotKey]),
        date: dateKey ? parseDate(r[dateKey]) : null,
    }));
    return { ok: true, rows };
}
