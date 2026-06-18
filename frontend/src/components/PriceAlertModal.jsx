import { useEffect, useMemo, useRef, useState } from "react";
import { IconFlask } from "./common/icons";
import Modal from "./Modal";
import { createPriceAlert, getLatestPrice, getUserAlerts, deletePriceAlert, triggerAlertManually, searchMarketInstruments } from "../api/portfolioApi";
import { useCurrencyDisplay, usePriceDisplay, nativeCurrencyOf } from "../contexts/CurrencyDisplayContext";
import { useI18n } from "../contexts/I18nContext";
import notify from "../utils/notify";
import { clickable } from "../utils/clickable";

export default function PriceAlertModal({ open, onClose, keycloak, prefilledSymbol, prefilledPrice }) {
    const { t } = useI18n();

    // Built inside the component so labels/descriptions translate live when
    // the user toggles language.
    const ALERT_TYPES = useMemo(() => [
        { value: "PRICE_ABOVE",  label: t("alerts.typePriceAbove"),  description: t("alerts.descPriceAbove") },
        { value: "PRICE_BELOW",  label: t("alerts.typePriceBelow"),  description: t("alerts.descPriceBelow") },
        { value: "PERCENT_GAIN", label: t("alerts.typePercentGain"), description: t("alerts.descPercentGain") },
        { value: "PERCENT_LOSS", label: t("alerts.typePercentLoss"), description: t("alerts.descPercentLoss") },
    ], [t]);

    const [alerts, setAlerts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [creating, setCreating] = useState(false);

    // Form state
    const [symbol, setSymbol] = useState(prefilledSymbol || "");
    const [alertType, setAlertType] = useState("PRICE_ABOVE");
    const [targetPrice, setTargetPrice] = useState(prefilledPrice?.toString() || "");
    const [note, setNote] = useState("");
    // Captured when the user picks from autocomplete (instrument.type — "BIST",
    // "STOCK", "CRYPTO" ...). Lets us resolve the native currency when the
    // site-wide currency mode is "original".
    const [selectedType, setSelectedType] = useState(null);
    // Resolved current price for the selected symbol, formatted in the active
    // display currency. Shown as a "Mevcut Fiyat: …" hint below the target
    // field; clicking it fills the target. Stays in sync with the symbol
    // selection — clears when the user types a different symbol.
    const [currentPriceHint, setCurrentPriceHint] = useState(null);

    // Autocomplete state
    const [searchResults, setSearchResults] = useState([]);
    const [showDropdown, setShowDropdown] = useState(false);
    const [searchLoading, setSearchLoading] = useState(false);

    // Site-wide currency toggle ("original" | "TRY" | "USD")
    const { mode: currencyMode } = useCurrencyDisplay();
    const { convert: convertPrice } = usePriceDisplay();

    // Suppresses the next symbol-change search. Flipped on programmatic
    // updates (prefill from chart, autocomplete selection) so the dropdown
    // doesn't auto-reopen after a user just picked an item.
    const skipNextSearchRef = useRef(false);

    // Effective currency this alert will be created in. PERCENT_* alerts target
    // a percentage and don't really need a currency, but we still snapshot one
    // so the email's "current price" line shows the right symbol.
    const effectiveCurrency = useMemo(() => {
        if (currencyMode === "USD" || currencyMode === "TRY") return currencyMode;
        return nativeCurrencyOf(selectedType, symbol);
    }, [currencyMode, selectedType, symbol]);
    const priceSymbol = effectiveCurrency === "USD" ? "$" : "₺";

    useEffect(() => {
        if (open) {
            loadAlerts();
            if (prefilledSymbol) {
                // Prefill from the chart's "Create alert" button is a
                // programmatic update — don't fire the autocomplete.
                skipNextSearchRef.current = true;
                setSymbol(prefilledSymbol);
            }
            if (prefilledPrice) setTargetPrice(prefilledPrice.toString());
        }
    }, [open, prefilledSymbol, prefilledPrice]);

    // Search instruments when symbol changes — but only when the change came
    // from the user typing, not from prefill / autocomplete selection.
    useEffect(() => {
        if (skipNextSearchRef.current) {
            skipNextSearchRef.current = false;
            return;
        }

        // User is editing the symbol → any previous price hint is stale.
        setCurrentPriceHint(null);

        const searchInstruments = async () => {
            if (symbol.length < 2) {
                setSearchResults([]);
                setShowDropdown(false);
                return;
            }

            setSearchLoading(true);
            try {
                const results = await searchMarketInstruments(symbol);
                setSearchResults(results);
                setShowDropdown(results.length > 0);

                // If the user typed a complete symbol that matches an instrument
                // exactly (no dropdown click required), prefill the target price
                // and remember its type. Common case: they open this modal from
                // an instrument's chart, then edit the symbol field directly.
                const exact = results.find(
                    (r) => r.symbol?.toUpperCase() === symbol.toUpperCase()
                );
                if (exact) autofillTargetFromInstrument(exact);
            } catch (error) {
                console.error("Failed to search instruments:", error);
                setSearchResults([]);
                setShowDropdown(false);
            } finally {
                setSearchLoading(false);
            }
        };

        const timeoutId = setTimeout(searchInstruments, 300); // Debounce
        return () => clearTimeout(timeoutId);
        // autofillTargetFromInstrument depends on `alertType` and `convertPrice`,
        // both of which are stable across the relevant renders.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [symbol]);

    /**
     * Fetch the latest price for `instrument`, fill the target field, AND
     * surface the price as a visible "Mevcut Fiyat" hint so the user can see
     * what we have even if the input value is hard to spot. PERCENT_* alerts
     * skip the field write but still show the hint for context.
     */
    const autofillTargetFromInstrument = async (instrument) => {
        setSelectedType(instrument.type || null);
        try {
            const native = await getLatestPrice(instrument.symbol, keycloak);
            console.log("[AlertModal] Autofill native price for", instrument.symbol, "=", native);
            if (!Number.isFinite(native) || native <= 0) {
                setCurrentPriceHint(null);
                return;
            }
            const conv = convertPrice(native, instrument.type, instrument.symbol);
            const v = conv?.value;
            if (!Number.isFinite(v) || v <= 0) {
                setCurrentPriceHint(null);
                return;
            }
            const fixed = v >= 1 ? v.toFixed(2) : v.toFixed(4);
            const displayValue = String(Number.parseFloat(fixed));

            // Always surface the hint so the user can see it.
            setCurrentPriceHint({
                value: displayValue,
                currency: conv?.currency || "TRY",
                symbol: conv?.symbol || "₺",
            });

            // Don't overwrite percentage entries — the field is a % there.
            if (!alertType.includes("PERCENT")) {
                setTargetPrice(displayValue);
                console.log("[AlertModal] Autofill target →", displayValue, conv?.currency);
            }
        } catch (e) {
            console.warn("Could not prefill target price for", instrument.symbol, e);
            setCurrentPriceHint(null);
        }
    };

    const loadAlerts = async () => {
        setLoading(true);
        try {
            console.log("[AlertModal] Loading alerts for user...");
            const userAlerts = await getUserAlerts(keycloak, false);
            console.log("[AlertModal] Received alerts:", userAlerts);
            setAlerts(userAlerts);
        } catch (error) {
            console.error("[AlertModal] Failed to load alerts:", error);
            // Hata detaylarını göster
            if (error.response) {
                console.error("[AlertModal] Error response:", error.response.status, error.response.data);
            }
        } finally {
            setLoading(false);
        }
    };

    const handleCreateAlert = async (e) => {
        e.preventDefault();
        if (!symbol || !targetPrice) return;

        setCreating(true);
        try {
            const request = {
                symbol: symbol.toUpperCase(),
                alertType,
                targetPrice: Number.parseFloat(targetPrice),
                note: note.trim() || undefined,
                currency: effectiveCurrency,    // ₺/$ snapshot — backend stores & emails in this
            };

            console.log("[AlertModal] Creating alert:", request);
            const newAlert = await createPriceAlert(keycloak, request);
            console.log("[AlertModal] Alert created successfully:", newAlert);

            // Reset form
            setSymbol("");
            setTargetPrice("");
            setNote("");
            setSelectedType(null);
            setShowDropdown(false);
            setCurrentPriceHint(null);

            // Reload alerts
            console.log("[AlertModal] Reloading alerts...");
            await loadAlerts();

            console.log("[AlertModal] Alert creation completed");
        } catch (error) {
            console.error("[AlertModal] Failed to create alert:", error);
            const detail = error.response
                ? (error.response?.data?.message || `HTTP ${error.response.status}`)
                : error.message;
            notify(detail, { variant: "error", title: t("alerts.createFailedTitle") });
        } finally {
            setCreating(false);
        }
    };

    const handleSelectInstrument = (instrument) => {
        // Suppress the symbol-effect's search (otherwise the dropdown would
        // re-open with a single result after we just closed it).
        skipNextSearchRef.current = true;
        setSymbol(instrument.symbol);
        setShowDropdown(false);
        // setSelectedType + price fetch live in autofillTargetFromInstrument so
        // both this path and the exact-typed-match path stay consistent.
        autofillTargetFromInstrument(instrument);
    };

    const handleDeleteAlert = async (alertId) => {
        if (!confirm(t("alerts.confirmDelete"))) return;
        try {
            await deletePriceAlert(keycloak, alertId);
            await loadAlerts();
        } catch (error) {
            console.error("Failed to delete alert:", error);
            notify(error.message || "", {
                variant: "error",
                title: t("alerts.deleteFailedTitle"),
            });
        }
    };

    const handleTriggerTest = async (alertId) => {
        if (!confirm(t("alerts.confirmTest"))) return;
        try {
            const result = await triggerAlertManually(keycloak, alertId);
            if (result.success) {
                notify(t("alerts.testTriggeredMessage"), {
                    variant: "success",
                    title: t("alerts.testTriggeredTitle"),
                });
                await loadAlerts();
            } else {
                notify(result.message || "", {
                    variant: "error",
                    title: t("alerts.testFailedTitle"),
                });
            }
        } catch (error) {
            console.error("Failed to trigger alert:", error);
            notify(error.response?.data?.message || error.message || "", {
                variant: "error",
                title: t("alerts.testFailedTitle"),
            });
        }
    };

    const formatPrice = (price, currency) => {
        const formatted = new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 4,
        }).format(price);
        const sym = currency === "USD" ? "$" : "₺";
        return `${sym}${formatted}`;
    };

    const formatDate = (dateStr) => {
        return new Date(dateStr).toLocaleString('tr-TR');
    };

    const getAlertTypeLabel = (type) => {
        return ALERT_TYPES.find((at) => at.value === type)?.label || type;
    };

    const getStatusColor = (alert) => {
        if (!alert.active) return "#f87171"; // red
        if (alert.progressPercent && alert.progressPercent > 80) return "#fbbf24"; // yellow
        return "#4ade80"; // green
    };

    return (
        <Modal open={open} title={t("alerts.modalTitle")} onClose={onClose}>
            <div style={s.container}>
                {/* Create Alert Form */}
                <div style={s.section}>
                    <h3 style={s.sectionTitle}>{t("alerts.newAlert")}</h3>
                    <form onSubmit={handleCreateAlert} style={s.form}>
                        <div style={s.row}>
                            <div style={s.field}>
                                <label style={s.label}>{t("alerts.symbol")}</label>
                                <div style={s.autocompleteContainer}>
                                    <input
                                        type="text"
                                        value={symbol}
                                        onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                                        onFocus={() => {
                                            if (searchResults.length > 0) {
                                                setShowDropdown(true);
                                            }
                                        }}
                                        placeholder={t("alerts.symbolPlaceholder")}
                                        style={s.input}
                                        required
                                    />
                                    {showDropdown && searchResults.length > 0 && (
                                        <div style={s.dropdown}>
                                            {searchResults.map((instrument) => (
                                                <div
                                                    key={instrument.id}
                                                    role="button"
                                                    tabIndex={0}
                                                    style={s.dropdownItem}
                                                    {...clickable(() => handleSelectInstrument(instrument))}
                                                    onMouseEnter={(e) => {
                                                        e.currentTarget.style.background = "var(--bg-panel2)";
                                                    }}
                                                    onMouseLeave={(e) => {
                                                        e.currentTarget.style.background = "transparent";
                                                    }}
                                                >
                                                    <div style={s.dropdownSymbol}>{instrument.symbol}</div>
                                                    <div style={s.dropdownName}>{instrument.name}</div>
                                                    <div style={s.dropdownType}>{instrument.type}</div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                    {searchLoading && (
                                        <div style={s.searchLoading}>{t("alerts.searching")}</div>
                                    )}
                                </div>
                            </div>
                            <div style={s.field}>
                                <label style={s.label}>{t("alerts.alertType")}</label>
                                <select
                                    value={alertType}
                                    onChange={(e) => setAlertType(e.target.value)}
                                    style={s.select}
                                >
                                    {ALERT_TYPES.map(type => (
                                        <option key={type.value} value={type.value}>
                                            {type.label}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>

                        <div style={s.row}>
                            <div style={s.field}>
                                <label style={s.label}>
                                    {alertType.includes("PERCENT")
                                        ? t("alerts.targetPercentLabel")
                                        : t("alerts.targetPriceLabel", { symbol: priceSymbol })}
                                </label>
                                <div style={s.inputWithAdornment}>
                                    {!alertType.includes("PERCENT") && (
                                        <span style={s.inputAdornment}>{priceSymbol}</span>
                                    )}
                                    <input
                                        type="number"
                                        step="0.0001"
                                        value={targetPrice}
                                        onChange={(e) => setTargetPrice(e.target.value)}
                                        placeholder={
                                            alertType.includes("PERCENT")
                                                ? t("alerts.targetPercentPlaceholder")
                                                : t("alerts.targetPricePlaceholder")
                                        }
                                        style={{
                                            ...s.input,
                                            flex: 1,
                                            paddingLeft: alertType.includes("PERCENT") ? undefined : 28,
                                        }}
                                        required
                                    />
                                </div>
                                {/* Visible current-price hint — clickable to refill */}
                                {currentPriceHint && !alertType.includes("PERCENT") && (
                                    <button
                                        type="button"
                                        onClick={() => setTargetPrice(currentPriceHint.value)}
                                        style={s.currentPriceHint}
                                    >
                                        {t("alerts.currentPriceHint", {
                                            value: `${currentPriceHint.symbol}${currentPriceHint.value}`,
                                        })}
                                        <span style={s.currentPriceHintAction}>
                                            · {t("alerts.currentPriceHintUse")}
                                        </span>
                                    </button>
                                )}
                            </div>
                            <div style={s.field}>
                                <label style={s.label}>{t("alerts.note")}</label>
                                <input
                                    type="text"
                                    value={note}
                                    onChange={(e) => setNote(e.target.value)}
                                    placeholder={t("alerts.notePlaceholder")}
                                    style={s.input}
                                    maxLength={200}
                                />
                            </div>
                        </div>

                        <div style={s.typeDescription}>
                            {ALERT_TYPES.find((at) => at.value === alertType)?.description}
                        </div>

                        <button
                            type="submit"
                            disabled={creating || !symbol || !targetPrice}
                            style={{
                                ...s.createButton,
                                // Show the disabled (greyed) look whenever the button is
                                // actually disabled — not only while submitting. Before, an
                                // empty form left the button looking enabled (the inline
                                // cursor:pointer overrode button:disabled), so it seemed
                                // dead until a symbol+price were filled.
                                ...((creating || !symbol || !targetPrice) ? s.createButtonDisabled : {})
                            }}
                        >
                            {creating ? t("alerts.submitting") : t("alerts.submit")}
                        </button>
                    </form>
                </div>

                {/* Alerts List */}
                <div style={s.section}>
                    <h3 style={s.sectionTitle}>
                        {t("alerts.existingTitle", { count: alerts.length })}
                    </h3>

                    {loading ? (
                        <div style={s.loading}>{t("alerts.loading")}</div>
                    ) : alerts.length === 0 ? (
                        <div style={s.empty}>{t("alerts.empty")}</div>
                    ) : (
                        <div style={s.alertsList}>
                            {alerts.map(alert => (
                                <div key={alert.id} style={s.alertCard}>
                                    <div style={s.alertHeader}>
                                        <div style={s.alertSymbol}>
                                            {alert.symbol}
                                            <span style={s.alertInstrument}>{alert.instrumentName}</span>
                                        </div>
                                        <div
                                            style={{
                                                ...s.alertStatus,
                                                color: getStatusColor(alert)
                                            }}
                                        >
                                            {alert.active ? t("alerts.statusActive") : t("alerts.statusTriggered")}
                                        </div>
                                    </div>

                                    <div style={s.alertDetails}>
                                        <div style={s.alertRow}>
                                            <span>{t("alerts.rowType")}</span>
                                            <span>{getAlertTypeLabel(alert.alertType)}</span>
                                        </div>
                                        <div style={s.alertRow}>
                                            <span>{t("alerts.rowTarget")}</span>
                                            <span>
                                                {alert.alertType?.startsWith("PERCENT_")
                                                    ? `%${alert.targetPrice}`
                                                    : formatPrice(alert.targetPrice, alert.currency)}
                                            </span>
                                        </div>
                                        <div style={s.alertRow}>
                                            <span>{t("alerts.rowCurrent")}</span>
                                            <span>{formatPrice(alert.currentPrice, alert.currency)}</span>
                                        </div>
                                        {alert.progressPercent !== undefined && (
                                            <div style={s.alertRow}>
                                                <span>{t("alerts.rowProgress")}</span>
                                                <span>%{alert.progressPercent.toFixed(1)}</span>
                                            </div>
                                        )}
                                    </div>

                                    {alert.note && (
                                        <div style={s.alertNote}>{alert.note}</div>
                                    )}

                                    <div style={s.alertFooter}>
                                        <span style={s.alertDate}>
                                            {formatDate(alert.createdAt)}
                                        </span>
                                        <div style={s.alertActions}>
                                            {alert.active && (
                                                <button
                                                    onClick={() => handleTriggerTest(alert.id)}
                                                    style={s.testButton}
                                                >
                                                    <IconFlask size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />{t("alerts.actionTest")}
                                                </button>
                                            )}
                                            <button
                                                onClick={() => handleDeleteAlert(alert.id)}
                                                style={s.deleteButton}
                                            >
                                                {t("alerts.actionDelete")}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </Modal>
    );
}

const s = {
    container: {
        display: "flex",
        flexDirection: "column",
        gap: 24,
        maxHeight: "70vh",
        overflow: "auto",
    },
    section: {
        padding: 16,
        border: "1px solid var(--border)",
        borderRadius: 12,
        background: "var(--bg-panel)",
    },
    sectionTitle: {
        margin: "0 0 16px 0",
        fontSize: 16,
        fontWeight: 600,
        color: "var(--text-primary)",
    },
    form: {
        display: "flex",
        flexDirection: "column",
        gap: 16,
    },
    row: {
        display: "flex",
        gap: 16,
    },
    field: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        gap: 4,
    },
    autocompleteContainer: {
        position: "relative",
    },
    label: {
        fontSize: 12,
        fontWeight: 500,
        color: "var(--text-muted)",
    },
    input: {
        padding: "8px 12px",
        border: "1px solid var(--border)",
        borderRadius: 8,
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 14,
    },
    inputWithAdornment: {
        position: "relative",
        display: "flex",
        alignItems: "center",
        width: "100%",
    },
    inputAdornment: {
        position: "absolute",
        left: 10,
        color: "var(--text-muted)",
        fontSize: 14,
        fontWeight: 500,
        pointerEvents: "none",
    },
    currentPriceHint: {
        alignSelf: "flex-start",
        marginTop: 2,
        padding: "4px 10px",
        background: "rgba(34, 197, 94, 0.12)",
        color: "#22c55e",
        border: "1px solid rgba(34, 197, 94, 0.35)",
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
    },
    currentPriceHintAction: {
        color: "var(--text-muted)",
        fontWeight: 500,
    },
    select: {
        padding: "8px 12px",
        border: "1px solid var(--border)",
        borderRadius: 8,
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 14,
    },
    typeDescription: {
        fontSize: 12,
        color: "var(--text-muted)",
        fontStyle: "italic",
    },
    createButton: {
        padding: "10px 20px",
        // Solid accent green — NOT var(--accent), which is a ~14%-opacity tint
        // and made the enabled button look permanently "pale". The disabled
        // look now comes solely from createButtonDisabled (opacity 0.5), so the
        // enabled state reads as a vivid, clearly-clickable green.
        background: "var(--accent-solid)",
        color: "white",
        border: "none",
        borderRadius: 8,
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        alignSelf: "flex-start",
    },
    createButtonDisabled: {
        opacity: 0.5,
        cursor: "not-allowed",
    },
    loading: {
        textAlign: "center",
        padding: 20,
        color: "var(--text-muted)",
    },
    empty: {
        textAlign: "center",
        padding: 20,
        color: "var(--text-muted)",
        fontStyle: "italic",
    },
    alertsList: {
        display: "flex",
        flexDirection: "column",
        gap: 12,
    },
    alertCard: {
        padding: 12,
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        background: "var(--bg-panel2)",
    },
    alertHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 8,
    },
    alertSymbol: {
        fontSize: 14,
        fontWeight: 600,
        color: "var(--text-primary)",
    },
    alertInstrument: {
        fontSize: 12,
        fontWeight: 400,
        color: "var(--text-muted)",
        marginLeft: 8,
    },
    alertStatus: {
        fontSize: 12,
        fontWeight: 500,
    },
    alertDetails: {
        display: "flex",
        flexDirection: "column",
        gap: 4,
        marginBottom: 8,
    },
    alertRow: {
        display: "flex",
        justifyContent: "space-between",
        fontSize: 12,
        color: "var(--text-muted)",
    },
    alertNote: {
        fontSize: 12,
        color: "var(--text-muted)",
        fontStyle: "italic",
        marginBottom: 8,
        padding: 8,
        background: "var(--bg-panel)",
        borderRadius: 4,
    },
    alertFooter: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
    },
    alertActions: {
        display: "flex",
        gap: 8,
        alignItems: "center",
    },
    alertDate: {
        fontSize: 11,
        color: "var(--text-muted)",
    },
    deleteButton: {
        padding: "4px 8px",
        background: "transparent",
        color: "#f87171",
        border: "1px solid #f87171",
        borderRadius: 4,
        fontSize: 11,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    testButton: {
        padding: "4px 8px",
        background: "transparent",
        color: "#22c55e",
        border: "1px solid #22c55e",
        borderRadius: 4,
        fontSize: 11,
        cursor: "pointer",
        transition: "all 0.2s",
        fontWeight: 500,
    },
    dropdown: {
        position: "absolute",
        top: "100%",
        left: 0,
        right: 0,
        maxHeight: 300,
        overflowY: "auto",
        background: "var(--bg-panel)",
        border: "1px solid var(--border)",
        borderRadius: 8,
        marginTop: 4,
        zIndex: 1000,
        boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
    },
    dropdownItem: {
        padding: "10px 12px",
        cursor: "pointer",
        borderBottom: "1px solid var(--border-card)",
        transition: "background 0.2s",
    },
    dropdownSymbol: {
        fontSize: 14,
        fontWeight: 600,
        color: "var(--text-primary)",
        marginBottom: 2,
    },
    dropdownName: {
        fontSize: 12,
        color: "var(--text-muted)",
        marginBottom: 2,
    },
    dropdownType: {
        fontSize: 11,
        color: "#22c55e",
        fontWeight: 500,
    },
    searchLoading: {
        position: "absolute",
        top: "100%",
        left: 0,
        right: 0,
        padding: 8,
        background: "var(--bg-panel)",
        border: "1px solid var(--border)",
        borderRadius: 8,
        marginTop: 4,
        fontSize: 12,
        color: "var(--text-muted)",
        textAlign: "center",
    },
};
