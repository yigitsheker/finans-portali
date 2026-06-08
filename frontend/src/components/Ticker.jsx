import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMarketSummary, getPositions } from "../api/portfolioApi";
import { watchlistApi } from "../api/watchlistApi";
import InstrumentChartModal from "./InstrumentChartModal";
import CompareInstrumentsModal from "./CompareInstrumentsModal";
import { usePriceDisplay } from "../contexts/CurrencyDisplayContext";

const PREFS_KEY = "finans-ticker-prefs";
const REFRESH_MS = 30_000;

// Speed multiplier: 1.0 = base speed; <1 = slower; >1 = faster.
const SPEED_MIN  = 0.25;
const SPEED_MAX  = 3.0;
const SPEED_STEP = 0.05;

// localStorage'dan tercihleri yükle; format değişimi/eski/bozuk veriye karşı korumalı.
function loadPrefs() {
    try {
        const raw = localStorage.getItem(PREFS_KEY);
        if (!raw) return null;
        const p = JSON.parse(raw);
        if (typeof p !== "object" || p === null) return null;
        const speed = Number(p.speed);
        return {
            mode: ["auto", "portfolio", "watchlist", "custom", "bist100"].includes(p.mode) ? p.mode : "auto",
            watchlistId: typeof p.watchlistId === "number" ? p.watchlistId : null,
            customSymbols: Array.isArray(p.customSymbols) ? p.customSymbols : [],
            speed: Number.isFinite(speed) && speed >= SPEED_MIN && speed <= SPEED_MAX ? speed : 1.0,
        };
    } catch {
        return null;
    }
}

function savePrefs(prefs) {
    try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch {}
}

const DEFAULT_PREFS = { mode: "auto", watchlistId: null, customSymbols: [], speed: 1.0 };

export default function Ticker({ keycloak }) {
    const navigate = useNavigate();
    const isAuth = !!keycloak?.authenticated;
    const { format: formatPrice } = usePriceDisplay();
    const [prefs, setPrefsState] = useState(() => loadPrefs() ?? DEFAULT_PREFS);
    const [marketData, setMarketData] = useState([]);
    const [symbols, setSymbols] = useState([]);
    const [sourceLabel, setSourceLabel] = useState("");
    const [showSettings, setShowSettings] = useState(false);
    const [watchlists, setWatchlists] = useState([]);
    const [customInput, setCustomInput] = useState("");
    const [selectedInstrument, setSelectedInstrument] = useState(null);
    const [compareTarget, setCompareTarget] = useState(null);
    const settingsRef = useRef(null);

    const setPrefs = (updater) => {
        setPrefsState((prev) => {
            const next = typeof updater === "function" ? updater(prev) : updater;
            savePrefs(next);
            return next;
        });
    };

    // Periyodik market data yenilemesi
    useEffect(() => {
        let cancelled = false;
        const fetchData = () => {
            getMarketSummary()
                .then((d) => { if (!cancelled) setMarketData(d); })
                .catch(() => {});
        };
        fetchData();
        const timer = setInterval(fetchData, REFRESH_MS);
        return () => { cancelled = true; clearInterval(timer); };
    }, []);

    // Auth değiştiğinde watchlist'leri yükle. Ayrıca panel açılınca yenile:
    // kullanıcı başka sayfada yeni liste oluşturup ticker'a dönerse görünür olsun.
    useEffect(() => {
        if (!isAuth) {
            setWatchlists([]);
            return;
        }
        watchlistApi.getWatchlists(keycloak)
            .then(setWatchlists)
            .catch(() => setWatchlists([]));
    }, [isAuth, keycloak, showSettings]);

    // Dış tıklama → settings panelini kapat
    useEffect(() => {
        if (!showSettings) return;
        const handler = (e) => {
            if (settingsRef.current && !settingsRef.current.contains(e.target)) {
                setShowSettings(false);
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, [showSettings]);

    // Sembolleri çöz: mode + auth durumu + data'ya göre ne göstereceğine karar ver
    useEffect(() => {
        if (marketData.length === 0) return;
        let cancelled = false;

        const filterBySymbols = (syms) => {
            const set = new Set(syms.map((s) => s.toUpperCase()));
            return marketData.filter((m) => set.has(m.symbol.toUpperCase()));
        };

        const bist100 = () => {
            const items = marketData.filter((m) => m.type === "BIST");
            return items.length > 0 ? items : marketData.filter((m) => m.type === "STOCK");
        };

        const resolve = async () => {
            // Anonim her zaman BIST 100
            if (!isAuth) {
                if (!cancelled) {
                    setSymbols(bist100());
                    setSourceLabel("BIST 100");
                }
                return;
            }

            // Auth user — moda göre
            if (prefs.mode === "bist100") {
                if (!cancelled) { setSymbols(bist100()); setSourceLabel("BIST 100"); }
                return;
            }

            if (prefs.mode === "custom") {
                if (!cancelled) {
                    setSymbols(filterBySymbols(prefs.customSymbols));
                    setSourceLabel("Özel Liste");
                }
                return;
            }

            if (prefs.mode === "watchlist" && prefs.watchlistId != null) {
                try {
                    const wl = await watchlistApi.getWatchlist(keycloak, prefs.watchlistId);
                    if (cancelled) return;
                    setSymbols(filterBySymbols(wl.symbols || []));
                    setSourceLabel(wl.name || "Takip Listesi");
                    return;
                } catch {
                    // düşer auto'ya
                }
            }

            // mode === "auto" veya watchlist çözülemedi → portföy → ilk liste → BIST100
            try {
                const positions = await getPositions(keycloak);
                if (positions && positions.length > 0) {
                    if (cancelled) return;
                    setSymbols(filterBySymbols(positions.map((p) => p.symbol)));
                    setSourceLabel("Portföyüm");
                    return;
                }
            } catch {}

            try {
                const lists = await watchlistApi.getWatchlists(keycloak);
                if (lists && lists.length > 0) {
                    const firstNonEmpty = lists.find((l) => l.symbols && l.symbols.length > 0);
                    if (firstNonEmpty) {
                        if (cancelled) return;
                        setSymbols(filterBySymbols(firstNonEmpty.symbols));
                        setSourceLabel(firstNonEmpty.name || "Takip Listesi");
                        return;
                    }
                }
            } catch {}

            if (!cancelled) { setSymbols(bist100()); setSourceLabel("BIST 100"); }
        };

        resolve();
        return () => { cancelled = true; };
    }, [marketData, prefs, isAuth, keycloak]);

    // Seamless loop için track'in en az 2 viewport eninde olması gerek; aksi halde
    // az sembolde (2-3 hisse) dönüşte boşluk görünür. Az sembol → çok kopya.
    // Toplam ~40 sembollük track + iki kez tekrar = stable scroll.
    const items = useMemo(() => {
        if (symbols.length === 0) return [];
        const repetitions = Math.max(2, Math.ceil(40 / symbols.length));
        const oneSet = [];
        for (let i = 0; i < repetitions; i++) oneSet.push(...symbols);
        return [...oneSet, ...oneSet]; // ikiye katla → translateX(-50%) seamless
    }, [symbols]);

    // Custom symbol input parse
    const handleCustomSave = () => {
        const symbols = customInput
            .toUpperCase()
            .split(/[\s,;\n]+/)
            .map((s) => s.trim())
            .filter(Boolean)
            .slice(0, 30);
        setPrefs((p) => ({ ...p, mode: "custom", customSymbols: symbols }));
        setShowSettings(false);
    };

    // Animasyon süresi: track ne kadar geniş olursa o kadar uzun süre. Görünür akış
    // hızı sembol başına ~0.9s olacak şekilde (rahat okunur ama yavaş değil).
    // -50% kaydırma sırasında items.length/2 sembol geçer.
    // Speed multiplier: user-controlled (0.25× – 3×). Higher = faster = smaller duration.
    const baseDuration = items.length > 0 ? Math.max(10, (items.length / 2) * 0.9) : 0;
    const speed = Number(prefs.speed) || 1.0;
    const duration = baseDuration > 0 ? baseDuration / speed : 0;

    // Boş durumlar için kullanıcıyı yönlendiren mesaj. Settings butonu daima görünür
    // olduğundan kullanıcı buradan custom semboller ekleyip ticker'ı doldurabilir.
    const emptyMessage = (() => {
        if (marketData.length === 0) return "Ticker yükleniyor...";
        if (!isAuth) return "Piyasa verisi alınamadı";
        if (prefs.mode === "custom") return "Özel sembol listesi boş — ⚙ ile ekle";
        if (prefs.mode === "watchlist" && prefs.watchlistId == null) return "Takip listesi seçilmedi — ⚙ ile seç";
        if (prefs.mode === "watchlist") return "Bu listede sembol yok";
        if (prefs.mode === "portfolio") return "Portföyde sembol yok — Yatırımlarım sekmesinden ekleyebilirsin";
        return "Sembol bulunamadı";
    })();

    return (
        <div style={st.bar} className="ticker-bar">
            <div style={st.sourceLabel} title={sourceLabel || "Ticker"}>
                <span style={st.dot} /> {sourceLabel || "Ticker"}
            </div>

            <div style={st.tape}>
                {items.length === 0 ? (
                    <div style={st.emptyText}>{emptyMessage}</div>
                ) : (
                <div
                    // Remount on speed change so the CSS animation restarts with the new
                    // duration immediately. Without this, changing animation-duration on a
                    // long-running animation (e.g. BIST 100's ~180s base) doesn't visibly
                    // take effect, so the speed control appeared to do nothing.
                    key={`ticker-spd-${speed}`}
                    style={{
                        ...st.track,
                        animationDuration: `${duration}s`,
                    }}
                    className="ticker-track"
                >
                    {items.map((item, i) => {
                        const positive = (item.changePct ?? 0) >= 0;
                        const color = positive ? "#10b981" : "#ef4444";
                        return (
                            <button
                                key={`${item.symbol}-${i}`}
                                style={st.item}
                                onClick={() => setSelectedInstrument(item)}
                                title={`${item.symbol} — ${item.name}`}
                            >
                                <span style={st.symbol}>{item.symbol}</span>
                                <span style={st.price}>
                                    {formatPrice(item.last, item.type, { symbol: item.symbol })}
                                </span>
                                <span style={{ ...st.change, color }}>
                                    {positive ? "▲" : "▼"} {positive ? "+" : ""}{(item.changePct ?? 0).toFixed(2)}%
                                </span>
                            </button>
                        );
                    })}
                </div>
                )}
            </div>

            {isAuth && (
                <div ref={settingsRef} style={st.settingsWrap}>
                    <button
                        style={st.settingsBtn}
                        onClick={() => setShowSettings((v) => !v)}
                        title="Ticker ayarları"
                        aria-label="Ticker ayarları"
                    >
                        ⚙
                    </button>
                    {showSettings && (
                        <div style={st.settingsPanel}>
                            <div style={st.panelTitle}>⏩ Akış Hızı</div>

                            <div style={st.speedControls}>
                                <button
                                    type="button"
                                    style={st.speedStepBtn}
                                    title="Yavaşlat"
                                    onClick={() => setPrefs((p) => ({
                                        ...p,
                                        speed: Math.max(SPEED_MIN, Number((Number(p.speed ?? 1) - 0.1).toFixed(2))),
                                    }))}
                                    disabled={(prefs.speed ?? 1) <= SPEED_MIN}
                                >
                                    −
                                </button>

                                <button
                                    type="button"
                                    style={st.speedValue}
                                    onClick={() => setPrefs((p) => ({ ...p, speed: 1.0 }))}
                                    title="Varsayılana sıfırla (1.00×)"
                                >
                                    {Number(prefs.speed ?? 1).toFixed(2)}×
                                </button>

                                <button
                                    type="button"
                                    style={st.speedStepBtn}
                                    title="Hızlandır"
                                    onClick={() => setPrefs((p) => ({
                                        ...p,
                                        speed: Math.min(SPEED_MAX, Number((Number(p.speed ?? 1) + 0.1).toFixed(2))),
                                    }))}
                                    disabled={(prefs.speed ?? 1) >= SPEED_MAX}
                                >
                                    +
                                </button>
                            </div>

                            <input
                                type="range"
                                min={SPEED_MIN}
                                max={SPEED_MAX}
                                step={SPEED_STEP}
                                value={prefs.speed ?? 1}
                                onChange={(e) =>
                                    setPrefs((p) => ({ ...p, speed: Number(e.target.value) }))
                                }
                                style={st.speedSlider}
                                aria-label="Ticker hızı"
                            />

                            <div style={st.speedLegend}>
                                <span>{SPEED_MIN.toFixed(2)}× Yavaş</span>
                                <span>1× Normal</span>
                                <span>{SPEED_MAX.toFixed(2)}× Hızlı</span>
                            </div>

                            <div style={{ height: 1, background: "var(--border-soft)", margin: "12px 0" }} />

                            <div style={st.panelTitle}>Ticker'da Ne Görünsün?</div>

                            {[
                                { value: "auto", label: "Otomatik (Portföy → Liste → BIST 100)" },
                                { value: "portfolio", label: "Portföyüm" },
                                { value: "bist100", label: "BIST 100" },
                                { value: "watchlist", label: "Takip Listem" },
                                { value: "custom", label: "Özel Semboller" },
                            ].map((opt) => (
                                <label key={opt.value} style={st.radioRow}>
                                    <input
                                        type="radio"
                                        name="ticker-mode"
                                        value={opt.value}
                                        checked={prefs.mode === opt.value}
                                        onChange={() => setPrefs((p) => ({ ...p, mode: opt.value }))}
                                        style={{ accentColor: "var(--accent-solid)" }}
                                    />
                                    <span style={st.radioLabel}>{opt.label}</span>
                                </label>
                            ))}

                            {prefs.mode === "watchlist" && (
                                <div style={st.subSection}>
                                    {watchlists.length === 0 ? (
                                        <div style={st.muted}>
                                            Henüz takip listen yok.{" "}
                                            <button
                                                style={st.linkBtn}
                                                onClick={() => { setShowSettings(false); navigate("/stocks"); }}
                                            >
                                                Hisse Senetleri
                                            </button>{" "}
                                            sekmesinden oluşturabilirsin.
                                        </div>
                                    ) : (
                                        <select
                                            value={prefs.watchlistId ?? ""}
                                            onChange={(e) =>
                                                setPrefs((p) => ({
                                                    ...p,
                                                    watchlistId: Number(e.target.value) || null,
                                                }))
                                            }
                                            style={st.select}
                                        >
                                            <option value="">Liste seç...</option>
                                            {watchlists.map((wl) => (
                                                <option key={wl.id} value={wl.id}>
                                                    {wl.name} ({wl.symbols.length} sembol)
                                                </option>
                                            ))}
                                        </select>
                                    )}
                                </div>
                            )}

                            {prefs.mode === "custom" && (
                                <div style={st.subSection}>
                                    <div style={st.muted}>
                                        Virgülle ayırarak sembolleri yaz (örn. THYAO, AKBNK, BIMAS, AAPL):
                                    </div>
                                    <textarea
                                        rows={3}
                                        value={customInput || prefs.customSymbols.join(", ")}
                                        onChange={(e) => setCustomInput(e.target.value)}
                                        placeholder="THYAO, AKBNK, BIMAS..."
                                        style={st.textarea}
                                    />
                                    <button style={st.saveBtn} onClick={handleCustomSave}>
                                        Kaydet
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}

            {/* Sembol grafiği — Hisse Senetleri sayfasındaki ile aynı modal */}
            <InstrumentChartModal
                instrument={selectedInstrument}
                onClose={() => setSelectedInstrument(null)}
                keycloak={keycloak}
                onCompare={(inst) => {
                    // Open the compare modal in place — navigating to /stocks just
                    // closed the chart and stranded the user, so the click looked
                    // like a no-op.
                    setSelectedInstrument(null);
                    setCompareTarget(inst);
                }}
            />

            <CompareInstrumentsModal
                baseInstrument={compareTarget}
                onClose={() => setCompareTarget(null)}
            />
        </div>
    );
}

const st = {
    bar: {
        display: "flex",
        alignItems: "center",
        gap: 12,
        height: 40,
        background: "var(--bg-card)",
        borderBottom: "1px solid var(--border-card)",
        // overflow: visible — ayar paneli aşağıya açılıyor, kırpılmamalı.
        // Sadece akan tape (içeride) overflow: hidden olur.
        overflow: "visible",
        position: "relative",
        flexShrink: 0,
        // Lift above the sticky topbar (z-index: 50 in Layout.jsx) so that
        // when the settings panel opens it can extend below and cover the
        // topbar area without the topbar punching back through.
        zIndex: 70,
    },
    sourceLabel: {
        flexShrink: 0,
        display: "flex",
        alignItems: "center",
        gap: 6,
        padding: "0 14px",
        fontSize: 11,
        fontWeight: 700,
        color: "var(--text-muted)",
        textTransform: "uppercase",
        letterSpacing: 0.6,
        borderRight: "1px solid var(--border-card)",
        height: "100%",
        background: "var(--bg-panel)",
    },
    dot: {
        width: 6,
        height: 6,
        borderRadius: "50%",
        background: "var(--accent-solid)",
        boxShadow: "0 0 6px var(--accent-solid)",
        animation: "ticker-pulse 1.6s ease-in-out infinite",
    },
    emptyText: {
        color: "var(--text-muted)",
        fontSize: 12,
        padding: "0 14px",
        height: "100%",
        display: "flex",
        alignItems: "center",
    },
    tape: {
        flex: 1,
        overflow: "hidden",
        height: "100%",
        position: "relative",
    },
    track: {
        display: "flex",
        gap: 0,
        height: "100%",
        alignItems: "center",
        animationName: "ticker-scroll",
        animationTimingFunction: "linear",
        animationIterationCount: "infinite",
        willChange: "transform",
    },
    item: {
        flexShrink: 0,
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        padding: "0 18px",
        height: "100%",
        background: "transparent",
        border: "none",
        borderRight: "1px solid var(--border-soft)",
        color: "var(--text-primary)",
        fontSize: 12,
        cursor: "pointer",
        whiteSpace: "nowrap",
    },
    symbol: { fontWeight: 700, letterSpacing: 0.3 },
    price: { color: "var(--text-secondary, var(--text-primary))", fontVariantNumeric: "tabular-nums" },
    change: { fontWeight: 700, fontSize: 11, fontVariantNumeric: "tabular-nums" },
    settingsWrap: { position: "relative", flexShrink: 0, marginRight: 8 },
    settingsBtn: {
        width: 30,
        height: 30,
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 14,
        display: "grid",
        placeItems: "center",
    },
    settingsPanel: {
        position: "absolute",
        top: "calc(100% + 8px)",
        right: 0,
        width: 340,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        boxShadow: "var(--shadow)",
        padding: 16,
        display: "flex",
        flexDirection: "column",
        gap: 6,
        zIndex: 60,
    },
    panelTitle: {
        fontSize: 13,
        fontWeight: 700,
        color: "var(--text-primary)",
        marginBottom: 4,
    },
    speedControls: {
        display: "grid",
        gridTemplateColumns: "40px 1fr 40px",
        gap: 8,
        padding: "6px 0",
        alignItems: "center",
    },
    speedStepBtn: {
        width: 40,
        height: 40,
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel2, var(--input-bg))",
        color: "var(--text-primary)",
        fontSize: 22,
        fontWeight: 700,
        cursor: "pointer",
        lineHeight: 1,
        display: "grid",
        placeItems: "center",
        padding: 0,
    },
    speedValue: {
        height: 40,
        padding: "0 8px",
        background: "var(--accent-hover-bg)",
        color: "var(--accent-solid)",
        border: "1px solid var(--accent-border)",
        borderRadius: 8,
        fontSize: 16,
        fontWeight: 800,
        cursor: "pointer",
        fontVariantNumeric: "tabular-nums",
        letterSpacing: "0.02em",
        textAlign: "center",
        whiteSpace: "nowrap",
    },
    speedSlider: {
        width: "100%",
        height: 28,
        accentColor: "var(--accent-solid)",
        cursor: "pointer",
        marginTop: 4,
        display: "block",
    },
    speedLegend: {
        display: "flex",
        justifyContent: "space-between",
        fontSize: 10,
        color: "var(--text-muted)",
        marginTop: 4,
        padding: "0 2px",
        fontVariantNumeric: "tabular-nums",
    },
    radioRow: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "4px 0",
        cursor: "pointer",
    },
    radioLabel: { fontSize: 13, color: "var(--text-primary)" },
    subSection: {
        marginTop: 4,
        padding: 10,
        background: "var(--bg-panel)",
        borderRadius: 8,
        display: "flex",
        flexDirection: "column",
        gap: 8,
    },
    muted: { fontSize: 11, color: "var(--text-muted)", lineHeight: 1.5 },
    select: {
        padding: "8px 10px",
        background: "var(--input-bg)",
        border: "1px solid var(--input-border)",
        borderRadius: 6,
        color: "var(--text-primary)",
        fontSize: 13,
    },
    textarea: {
        width: "100%",
        padding: "8px 10px",
        background: "var(--input-bg)",
        border: "1px solid var(--input-border)",
        borderRadius: 6,
        color: "var(--text-primary)",
        fontSize: 12,
        fontFamily: "inherit",
        resize: "vertical",
        outline: "none",
    },
    saveBtn: {
        alignSelf: "flex-end",
        padding: "6px 14px",
        background: "var(--accent-solid)",
        color: "#000",
        border: "none",
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
    },
    linkBtn: {
        background: "transparent",
        border: "none",
        color: "var(--accent-solid)",
        padding: 0,
        cursor: "pointer",
        fontSize: 11,
        fontWeight: 600,
        textDecoration: "underline",
    },
};
