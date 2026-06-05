/**
 * Pagination control — page numbers + prev/next + "rows per page" picker.
 *
 * Designed for client-side paged tables where the parent slices its dataset
 * by `page` and `pageSize`. Window logic shows 5 page numbers with leading
 * ellipsis when the current page is far in.
 *
 * Props:
 *  - page:        1-based current page
 *  - pageSize:    rows per page
 *  - total:       total row count BEFORE slicing
 *  - onPageChange:(nextPage:number) => void
 *  - onPageSizeChange:(nextSize:number) => void
 *  - pageSizeOptions: optional override of the per-page dropdown
 */
import { useI18n } from "../../contexts/I18nContext";

export default function Pagination({
    page,
    pageSize,
    total,
    onPageChange,
    onPageSizeChange,
    pageSizeOptions = [10, 25, 50, 100],
}) {
    const { t } = useI18n();
    const totalPages = Math.max(1, Math.ceil((total || 0) / pageSize));
    const safePage = Math.min(Math.max(1, page || 1), totalPages);
    const from = total === 0 ? 0 : (safePage - 1) * pageSize + 1;
    const to = Math.min(total, safePage * pageSize);

    const pages = buildPageWindow(safePage, totalPages);

    return (
        <div style={s.wrap}>
            <div style={s.info}>
                {total === 0 ? t("pagination.empty") : <><strong>{from}–{to}</strong> / {total.toLocaleString("tr-TR")} {t("pagination.records")}</>}
            </div>

            <div style={s.controls}>
                <button
                    type="button"
                    style={{ ...s.navBtn, ...(safePage === 1 ? s.navBtnDisabled : {}) }}
                    disabled={safePage === 1}
                    onClick={() => onPageChange(safePage - 1)}
                    aria-label={t("pagination.prevPage")}
                >
                    ‹
                </button>

                {pages.map((p, i) => p === "…" ? (
                    <span key={`gap-${i}`} style={s.gap}>…</span>
                ) : (
                    <button
                        key={p}
                        type="button"
                        onClick={() => onPageChange(p)}
                        style={{ ...s.pageBtn, ...(p === safePage ? s.pageBtnActive : {}) }}
                        aria-current={p === safePage ? "page" : undefined}
                    >
                        {p}
                    </button>
                ))}

                <button
                    type="button"
                    style={{ ...s.navBtn, ...(safePage === totalPages ? s.navBtnDisabled : {}) }}
                    disabled={safePage === totalPages}
                    onClick={() => onPageChange(safePage + 1)}
                    aria-label={t("pagination.nextPage")}
                >
                    ›
                </button>
            </div>

            {onPageSizeChange && (
                <label style={s.sizeWrap}>
                    <span style={s.sizeLabel}>{t("pagination.perPage")}</span>
                    <select
                        value={pageSize}
                        onChange={(e) => onPageSizeChange(Number(e.target.value))}
                        style={s.sizeSel}
                    >
                        {pageSizeOptions.map((n) => (
                            <option key={n} value={n}>{n}</option>
                        ))}
                    </select>
                </label>
            )}
        </div>
    );
}

/**
 * Build a 1..N page-number array with up to 5 entries plus optional "…" gaps
 * so the bar stays compact even when total pages is huge.
 * Examples for page=12, totalPages=30:  1 … 11 12 13 … 30
 * Examples for page=2,  totalPages=30:  1 2 3 … 30
 */
function buildPageWindow(page, totalPages) {
    if (totalPages <= 7) {
        return Array.from({ length: totalPages }, (_, i) => i + 1);
    }
    const window = new Set([1, totalPages, page - 1, page, page + 1]);
    if (page <= 3) [2, 3, 4].forEach((p) => window.add(p));
    if (page >= totalPages - 2) [totalPages - 3, totalPages - 2, totalPages - 1].forEach((p) => window.add(p));
    const sorted = [...window].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b);
    const out = [];
    for (let i = 0; i < sorted.length; i++) {
        if (i > 0 && sorted[i] - sorted[i - 1] > 1) out.push("…");
        out.push(sorted[i]);
    }
    return out;
}

const s = {
    wrap: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 14,
        flexWrap: "wrap",
        padding: "12px 4px",
    },
    info: {
        fontSize: 12.5,
        color: "var(--text-muted)",
    },
    controls: {
        display: "flex",
        alignItems: "center",
        gap: 4,
        flexWrap: "wrap",
    },
    navBtn: {
        minWidth: 32,
        height: 32,
        padding: "0 10px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-card)",
        color: "var(--text-primary)",
        fontSize: 16,
        fontWeight: 600,
        cursor: "pointer",
    },
    navBtnDisabled: {
        opacity: 0.4,
        cursor: "not-allowed",
    },
    pageBtn: {
        minWidth: 32,
        height: 32,
        padding: "0 10px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-card)",
        color: "var(--text-secondary)",
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        fontVariantNumeric: "tabular-nums",
    },
    pageBtnActive: {
        background: "var(--accent-hover-bg)",
        color: "var(--accent-solid)",
        borderColor: "var(--accent-solid)",
    },
    gap: {
        padding: "0 4px",
        color: "var(--text-muted)",
        fontSize: 13,
    },
    sizeWrap: {
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        fontSize: 12.5,
        color: "var(--text-muted)",
    },
    sizeLabel: { whiteSpace: "nowrap" },
    sizeSel: {
        padding: "5px 8px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 12.5,
        cursor: "pointer",
    },
};
