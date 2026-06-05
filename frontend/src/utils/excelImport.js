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

// Parse a lot/quantity cell. Accepts numbers and strings ("10", "10,5",
// "1.234"). Turkish thousand/decimal separators are tolerated.
function parseLot(v) {
    if (v == null || v === "") return NaN;
    if (typeof v === "number") return v;
    const s = String(v).trim().replace(/\s/g, "").replace(/\.(?=\d{3}\b)/g, "").replace(",", ".");
    const n = Number(s);
    return Number.isFinite(n) ? n : NaN;
}

// Normalize a date cell to "yyyy-MM-dd", or null if unparseable. Handles Excel
// date objects (cellDates) and the common TR/ISO text formats.
function parseDate(v) {
    if (v == null || v === "") return null;
    if (v instanceof Date && !Number.isNaN(v.getTime())) {
        return `${v.getFullYear()}-${pad(v.getMonth() + 1)}-${pad(v.getDate())}`;
    }
    const s = String(v).trim();
    let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/); // yyyy-MM-dd
    if (m) return `${m[1]}-${pad(m[2])}-${pad(m[3])}`;
    m = s.match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/); // dd.MM.yyyy / dd/MM/yyyy
    if (m) return `${m[3]}-${pad(m[2])}-${pad(m[1])}`;
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
