/* Generates the downloadable sample Excel files for the bulk-import feature.
 * Run: node scripts/gen-sample-xlsx.cjs  (outputs into ../public)            */
const XLSX = require("xlsx");
const path = require("path");

const PUB = path.join(__dirname, "..", "public");

function write(file, rows) {
    const ws = XLSX.utils.json_to_sheet(rows);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Portfoy");
    XLSX.writeFile(wb, path.join(PUB, file));
    console.log("wrote", file);
}

// Portföyüm: symbol + lot only
write("ornek-portfoy.xlsx", [
    { Sembol: "THYAO", Lot: 100 },
    { Sembol: "GARAN", Lot: 50 },
    { Sembol: "ASELS", Lot: 75 },
    { Sembol: "AKBNK", Lot: 120 },
]);

// Geçmişten: symbol + lot + buy date (yyyy-MM-dd)
write("ornek-gecmis.xlsx", [
    { Sembol: "THYAO", Lot: 100, "Alış Tarihi": "2024-01-15" },
    { Sembol: "GARAN", Lot: 50, "Alış Tarihi": "2023-06-10" },
    { Sembol: "ASELS", Lot: 75, "Alış Tarihi": "2024-03-20" },
    { Sembol: "KCHOL", Lot: 40, "Alış Tarihi": "2023-11-05" },
]);
