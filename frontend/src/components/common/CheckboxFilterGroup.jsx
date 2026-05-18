/**
 * CheckboxFilterGroup — horizontal multi-select filter chips, each with a
 * checkbox affordance and an optional row-count badge.
 *
 * Behaviour:
 *  - `selected` is a Set / array of chosen keys. An empty selection means
 *    "show everything" (the row-level filter caller should short-circuit).
 *  - Tapping a chip toggles its key; the parent gets the new array.
 *
 * Props:
 *  - options:  [{ key, label, count? }, ...]
 *  - selected: string[]          // active keys
 *  - onChange: (keys: string[]) => void
 *  - allLabel: string            // label of the synthetic "all" reset chip
 *                                // (rendered only when at least one key
 *                                //  is selected so single-click can clear)
 *  - layout:   "row" | "wrap"    // chip arrangement, default "wrap"
 */
export default function CheckboxFilterGroup({
    options,
    selected = [],
    onChange,
    allLabel = "Tümü",
    layout = "wrap",
}) {
    const sel = new Set(selected);
    const allActive = sel.size === 0;

    const toggle = (key) => {
        const next = new Set(sel);
        if (next.has(key)) next.delete(key);
        else next.add(key);
        onChange([...next]);
    };

    const clear = () => onChange([]);

    const wrapStyle = layout === "row" ? { ...s.wrap, flexWrap: "nowrap", overflowX: "auto" } : s.wrap;

    return (
        <div style={wrapStyle} role="group" aria-label="Filtre">
            <button
                type="button"
                style={{ ...s.chip, ...(allActive ? s.chipActive : {}) }}
                onClick={clear}
                aria-pressed={allActive}
            >
                <span style={{ ...s.box, ...(allActive ? s.boxOn : {}) }}>
                    {allActive && <Check />}
                </span>
                <span>{allLabel}</span>
            </button>

            {options.map((opt) => {
                const on = sel.has(opt.key);
                return (
                    <button
                        key={opt.key}
                        type="button"
                        style={{ ...s.chip, ...(on ? s.chipActive : {}) }}
                        onClick={() => toggle(opt.key)}
                        aria-pressed={on}
                    >
                        <span style={{ ...s.box, ...(on ? s.boxOn : {}) }}>
                            {on && <Check />}
                        </span>
                        <span>{opt.label}</span>
                        {typeof opt.count === "number" && (
                            <span style={s.count}>{opt.count}</span>
                        )}
                    </button>
                );
            })}
        </div>
    );
}

function Check() {
    return (
        <svg width="10" height="10" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path
                d="M3 8.5l3.2 3.2L13 5"
                stroke="#04150a"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    );
}

const s = {
    wrap: {
        display: "flex",
        flexWrap: "wrap",
        gap: 6,
        alignItems: "center",
    },
    chip: {
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        padding: "7px 12px",
        borderRadius: 999,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        color: "var(--text-secondary)",
        fontSize: 13,
        fontWeight: 500,
        cursor: "pointer",
        transition: "background 0.15s, border-color 0.15s, color 0.15s",
        whiteSpace: "nowrap",
    },
    chipActive: {
        background: "var(--accent-hover-bg)",
        color: "var(--accent-solid)",
        borderColor: "var(--accent-solid)",
        fontWeight: 600,
    },
    box: {
        width: 14,
        height: 14,
        borderRadius: 4,
        border: "1.5px solid var(--border-strong, var(--text-muted))",
        background: "transparent",
        display: "grid",
        placeItems: "center",
        flexShrink: 0,
        transition: "background 0.15s, border-color 0.15s",
    },
    boxOn: {
        background: "var(--accent-solid)",
        borderColor: "var(--accent-solid)",
    },
    count: {
        marginLeft: 4,
        padding: "1px 7px",
        borderRadius: 999,
        background: "var(--input-bg)",
        color: "var(--text-muted)",
        fontSize: 11,
        fontWeight: 700,
    },
};
