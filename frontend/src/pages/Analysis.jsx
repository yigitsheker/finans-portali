import { useEffect, useState } from "react";
import { getAnalysisDetail, getAnalysisInstruments } from "../api/analysisApi";
import InstrumentsTable from "../components/features/analysis/InstrumentsTable";
import InstrumentDetailCard from "../components/features/analysis/InstrumentDetailCard";
import Chatbot from "../components/features/analysis/Chatbot";
import InstrumentChartModal from "../components/InstrumentChartModal";
import CompareInstrumentsModal from "../components/CompareInstrumentsModal";
import AddPositionModal from "../components/AddPositionModal";
import TermInfo from "../components/common/TermInfo";
import { useI18n } from "../contexts/I18nContext";

/**
 * /analysis — cross-asset analysis page.
 *
 * Two-column desktop layout (table + chatbot side-by-side), single-column
 * stacked layout on narrow screens via a CSS media query in index.css's
 * `.fp-analysis-grid` rule.
 *
 * Auth: the route is wrapped in RequireAuth in App.jsx, so by the time we
 * render here keycloak.authenticated is true.
 */
export default function Analysis({ keycloak }) {
    const { t, lang } = useI18n();

    const [items, setItems] = useState([]);
    const [loadingList, setLoadingList] = useState(true);
    const [listError, setListError] = useState(null);

    const [selectedSymbol, setSelectedSymbol] = useState(null);
    const [detail, setDetail] = useState(null);
    const [loadingDetail, setLoadingDetail] = useState(false);
    const [detailError, setDetailError] = useState(null);
    // Chart modal target — the analysis DTO carries enough metadata
    // (symbol, name, category) to feed InstrumentChartModal, which fetches
    // its own price history via the market-history API.
    const [chartTarget, setChartTarget] = useState(null);
    // Compare + add-to-portfolio modals, both reachable from inside the
    // chart modal's footer buttons. Mirrors the FinexStyleMarket wiring.
    const [compareTarget, setCompareTarget] = useState(null);
    const [addTarget, setAddTarget] = useState(null);

    useEffect(() => {
        let cancelled = false;
        setLoadingList(true);
        getAnalysisInstruments(keycloak)
            .then((data) => {
                if (!cancelled) setItems(Array.isArray(data) ? data : []);
            })
            .catch((e) => {
                console.error("[Analysis]", e);
                if (!cancelled) setListError(t("analysis.tblLoadError"));
            })
            .finally(() => {
                if (!cancelled) setLoadingList(false);
            });
        return () => {
            cancelled = true;
        };
    }, [keycloak]);

    const handleRowClick = async (row) => {
        if (!row?.symbol) return;
        setSelectedSymbol(row.symbol);
        setLoadingDetail(true);
        setDetailError(null);
        try {
            const d = await getAnalysisDetail(keycloak, row.symbol);
            setDetail(d);
        } catch (e) {
            console.error("[Analysis] detail", e);
            setDetailError(t("analysis.detailLoadError"));
            setDetail(null);
        } finally {
            setLoadingDetail(false);
        }
    };

    return (
        <div style={s.page}>
            <h1 style={s.title}>
                {t("nav.analysis") || "Analiz"} <TermInfo termKey="position" placement="bottom" />
            </h1>
            <p style={s.subtitle}>{t("analysis.pageSubtitle")}</p>

            <div className="fp-analysis-grid" style={s.grid}>
                <div style={s.leftCol}>
                    <InstrumentsTable
                        items={items}
                        loading={loadingList}
                        error={listError}
                        onRowClick={handleRowClick}
                        selectedSymbol={selectedSymbol}
                    />
                    {selectedSymbol && (
                        <InstrumentDetailCard
                            detail={detail}
                            loading={loadingDetail}
                            error={detailError}
                            onShowChart={setChartTarget}
                        />
                    )}
                </div>
                <div style={s.rightCol}>
                    <Chatbot keycloak={keycloak} lang={lang} />
                </div>
            </div>

            {/* Chart modal — opens from the detail card's "Show Chart"
                button. Shares the same modal the Stocks/Home pages use, so
                the user sees a familiar candle/sparkline view with periods
                they already know. The two callbacks wire the footer
                buttons through to the standalone Compare and AddPosition
                modals so the user can pivot from analysis → action
                without leaving the page. */}
            <InstrumentChartModal
                instrument={chartTarget}
                onClose={() => setChartTarget(null)}
                keycloak={keycloak}
                onAddToPortfolio={(inst) => {
                    setChartTarget(null);
                    setAddTarget(inst);
                }}
                onCompare={(inst) => {
                    setChartTarget(null);
                    setCompareTarget(inst);
                }}
            />

            <CompareInstrumentsModal
                baseInstrument={compareTarget}
                onClose={() => setCompareTarget(null)}
            />

            <AddPositionModal
                open={!!addTarget}
                initialSymbol={addTarget?.symbol || ""}
                initialPrice={addTarget?.value || ""}
                keycloak={keycloak}
                onClose={() => setAddTarget(null)}
                onCreated={() => setAddTarget(null)}
            />
        </div>
    );
}

const s = {
    page: { display: "flex", flexDirection: "column", gap: 12, padding: "0 4px" },
    title: { fontSize: 24, fontWeight: 700, margin: 0, color: "var(--text-primary)" },
    subtitle: { fontSize: 13, color: "var(--text-muted)", margin: "0 0 8px 0", lineHeight: 1.5 },
    grid: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) minmax(0, 360px)",
        gap: 16,
        alignItems: "start",
    },
    leftCol: { display: "flex", flexDirection: "column", gap: 12, minWidth: 0 },
    // Sticky chatbot column with a fixed viewport-relative max height so
    // the card itself never grows past the visible area — messages scroll
    // inside the bubble, the surrounding page stays put.
    rightCol: {
        position: "sticky",
        top: 12,
        minWidth: 0,
        maxHeight: "calc(100vh - 120px)",
    },
};
