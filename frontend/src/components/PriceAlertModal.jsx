import { useEffect, useState } from "react";
import Modal from "./Modal";
import { createPriceAlert, getUserAlerts, deletePriceAlert, triggerAlertManually, searchMarketInstruments } from "../api/portfolioApi";

const ALERT_TYPES = [
    { value: "PRICE_ABOVE", label: "Fiyat Üstü", description: "Fiyat hedef seviyenin üzerine çıktığında" },
    { value: "PRICE_BELOW", label: "Fiyat Altı", description: "Fiyat hedef seviyenin altına düştüğünde" },
    { value: "PERCENT_GAIN", label: "% Kazanç", description: "Belirli yüzde kazanç sağlandığında" },
    { value: "PERCENT_LOSS", label: "% Kayıp", description: "Belirli yüzde kayıp yaşandığında" },
];

export default function PriceAlertModal({ open, onClose, keycloak, prefilledSymbol, prefilledPrice }) {
    const [alerts, setAlerts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [creating, setCreating] = useState(false);

    // Form state
    const [symbol, setSymbol] = useState(prefilledSymbol || "");
    const [alertType, setAlertType] = useState("PRICE_ABOVE");
    const [targetPrice, setTargetPrice] = useState(prefilledPrice?.toString() || "");
    const [note, setNote] = useState("");

    // Autocomplete state
    const [searchResults, setSearchResults] = useState([]);
    const [showDropdown, setShowDropdown] = useState(false);
    const [searchLoading, setSearchLoading] = useState(false);

    useEffect(() => {
        if (open) {
            loadAlerts();
            if (prefilledSymbol) setSymbol(prefilledSymbol);
            if (prefilledPrice) setTargetPrice(prefilledPrice.toString());
        }
    }, [open, prefilledSymbol, prefilledPrice]);

    // Search instruments when symbol changes
    useEffect(() => {
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
    }, [symbol]);

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
                targetPrice: parseFloat(targetPrice),
                note: note.trim() || undefined,
            };

            console.log("[AlertModal] Creating alert:", request);
            const newAlert = await createPriceAlert(keycloak, request);
            console.log("[AlertModal] Alert created successfully:", newAlert);

            // Reset form
            setSymbol("");
            setTargetPrice("");
            setNote("");
            setShowDropdown(false);

            // Reload alerts
            console.log("[AlertModal] Reloading alerts...");
            await loadAlerts();

            console.log("[AlertModal] Alert creation completed");
        } catch (error) {
            console.error("[AlertModal] Failed to create alert:", error);
            if (error.response) {
                console.error("[AlertModal] Create error response:", error.response.status, error.response.data);
                alert(error.response?.data?.message || "Alarm oluşturulamadı: " + error.response.status);
            } else {
                alert("Alarm oluşturulamadı: " + error.message);
            }
        } finally {
            setCreating(false);
        }
    };

    const handleSelectInstrument = (instrument) => {
        setSymbol(instrument.symbol);
        setShowDropdown(false);
    };

    const handleDeleteAlert = async (alertId) => {
        if (!confirm("Bu alarmı silmek istediğinizden emin misiniz?")) return;

        try {
            await deletePriceAlert(keycloak, alertId);
            await loadAlerts();
            console.log("Alert deleted successfully");
        } catch (error) {
            console.error("Failed to delete alert:", error);
            alert("Alarm silinemedi");
        }
    };

    const handleTriggerTest = async (alertId) => {
        if (!confirm("Bu alarmı manuel olarak tetiklemek ve email göndermek istiyor musunuz?\n\nEmail, giriş yaptığınız hesabın email adresine gönderilecek.")) return;

        try {
            const result = await triggerAlertManually(keycloak, alertId);
            if (result.success) {
                alert("✅ Başarılı!\n\n" + result.message + "\n\nEmail adresinizi kontrol edin!");
                await loadAlerts(); // Reload to show triggered status
            } else {
                alert("❌ Hata\n\n" + result.message);
            }
        } catch (error) {
            console.error("Failed to trigger alert:", error);
            const errorMsg = error.response?.data?.message || error.message || "Bilinmeyen hata";
            alert("❌ Alarm tetiklenemedi\n\n" + errorMsg);
        }
    };

    const formatPrice = (price) => {
        return new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 4,
        }).format(price);
    };

    const formatDate = (dateStr) => {
        return new Date(dateStr).toLocaleString('tr-TR');
    };

    const getAlertTypeLabel = (type) => {
        return ALERT_TYPES.find(t => t.value === type)?.label || type;
    };

    const getStatusColor = (alert) => {
        if (!alert.active) return "#f87171"; // red
        if (alert.progressPercent && alert.progressPercent > 80) return "#fbbf24"; // yellow
        return "#4ade80"; // green
    };

    return (
        <Modal open={open} title="Fiyat Alarmları" onClose={onClose}>
            <div style={s.container}>
                {/* Create Alert Form */}
                <div style={s.section}>
                    <h3 style={s.sectionTitle}>Yeni Alarm Oluştur</h3>
                    <form onSubmit={handleCreateAlert} style={s.form}>
                        <div style={s.row}>
                            <div style={s.field}>
                                <label style={s.label}>Sembol</label>
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
                                        placeholder="AAPL, ETHUSD, KCHOL..."
                                        style={s.input}
                                        required
                                    />
                                    {showDropdown && searchResults.length > 0 && (
                                        <div style={s.dropdown}>
                                            {searchResults.map((instrument) => (
                                                <div
                                                    key={instrument.id}
                                                    style={s.dropdownItem}
                                                    onClick={() => handleSelectInstrument(instrument)}
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
                                        <div style={s.searchLoading}>Aranıyor...</div>
                                    )}
                                </div>
                            </div>
                            <div style={s.field}>
                                <label style={s.label}>Alarm Tipi</label>
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
                                    Hedef {alertType.includes("PERCENT") ? "Yüzde (%)" : "Fiyat"}
                                </label>
                                <input
                                    type="number"
                                    step="0.0001"
                                    value={targetPrice}
                                    onChange={(e) => setTargetPrice(e.target.value)}
                                    placeholder={alertType.includes("PERCENT") ? "5.0" : "100.50"}
                                    style={s.input}
                                    required
                                />
                            </div>
                            <div style={s.field}>
                                <label style={s.label}>Not (Opsiyonel)</label>
                                <input
                                    type="text"
                                    value={note}
                                    onChange={(e) => setNote(e.target.value)}
                                    placeholder="Alarm notu..."
                                    style={s.input}
                                    maxLength={200}
                                />
                            </div>
                        </div>

                        <div style={s.typeDescription}>
                            {ALERT_TYPES.find(t => t.value === alertType)?.description}
                        </div>

                        <button
                            type="submit"
                            disabled={creating || !symbol || !targetPrice}
                            style={{
                                ...s.createButton,
                                ...(creating ? s.createButtonDisabled : {})
                            }}
                        >
                            {creating ? "Oluşturuluyor..." : "Alarm Oluştur"}
                        </button>
                    </form>
                </div>

                {/* Alerts List */}
                <div style={s.section}>
                    <h3 style={s.sectionTitle}>Mevcut Alarmlar ({alerts.length})</h3>

                    {loading ? (
                        <div style={s.loading}>Yükleniyor...</div>
                    ) : alerts.length === 0 ? (
                        <div style={s.empty}>Henüz alarm oluşturmadınız</div>
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
                                            {alert.status}
                                        </div>
                                    </div>

                                    <div style={s.alertDetails}>
                                        <div style={s.alertRow}>
                                            <span>Tip:</span>
                                            <span>{getAlertTypeLabel(alert.alertType)}</span>
                                        </div>
                                        <div style={s.alertRow}>
                                            <span>Hedef:</span>
                                            <span>{formatPrice(alert.targetPrice)}</span>
                                        </div>
                                        <div style={s.alertRow}>
                                            <span>Mevcut:</span>
                                            <span>{formatPrice(alert.currentPrice)}</span>
                                        </div>
                                        {alert.progressPercent !== undefined && (
                                            <div style={s.alertRow}>
                                                <span>İlerleme:</span>
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
                                                    title="Alarmı manuel olarak tetikle ve test email'i gönder"
                                                >
                                                    🧪 Test Et
                                                </button>
                                            )}
                                            <button
                                                onClick={() => handleDeleteAlert(alert.id)}
                                                style={s.deleteButton}
                                            >
                                                Sil
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
        background: "var(--accent)",
        color: "white",
        border: "none",
        borderRadius: 8,
        fontSize: 14,
        fontWeight: 500,
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
