import { useId } from "react";
import Modal from "../../Modal";
import { portfolioStyles as s } from "./portfolioStyles";

export function AddPositionModal({
  open,
  symbol,
  quantity,
  price,
  total,
  priceLoading,
  saving,
  showSuggestions,
  suggestions,
  error,
  setSymbol,
  setQuantity,
  setShowSuggestions,
  onPickSuggestion,
  onSave,
  onClose,
  // New: amount-based purchase ("budget mode") props
  inputMode = "quantity",          // "quantity" | "amount"
  amount = 0,
  effectiveQty = 0,
  amountLeftover = 0,
  setInputMode = () => {},
  setAmount = () => {},
}) {
  // Stable per-instance IDs so <label htmlFor> can target each <input>
  // (Sonar S6819 — accessibility: every form control needs a programmatic
  // label association). useId is the React-blessed way to avoid clashes
  // with other modals on the page.
  const reactId = useId();
  const ids = {
    symbol: `${reactId}-symbol`,
    quantity: `${reactId}-quantity`,
    currentPriceQty: `${reactId}-current-price-qty`,
    amount: `${reactId}-amount`,
    currentPriceAmt: `${reactId}-current-price-amt`,
    total: `${reactId}-total`,
  };
  const tabBtn = (active) => ({
    flex: 1,
    padding: "8px 12px",
    border: "1px solid var(--border-card)",
    background: active ? "var(--accent)" : "var(--input-bg)",
    color: active ? "var(--accent-solid)" : "var(--text-muted)",
    fontSize: 12,
    fontWeight: 600,
    cursor: "pointer",
    borderRadius: 6,
    transition: "all 0.15s",
  });
  return (
    <Modal
      open={open}
      title="Pozisyon Ekle"
      onClose={onClose}
      footer={
        <>
          <button style={s.ghostBtn} onClick={onClose} disabled={saving}>Vazgec</button>
          <button style={s.primaryBtn} onClick={onSave} disabled={saving || priceLoading}>
            {saving ? "Kaydediliyor..." : "Kaydet"}
          </button>
        </>
      }
    >
      <div style={s.formGrid}>
        <div style={{ gridColumn: "span 2", display: "grid", gap: 6 }}>
          <label htmlFor={ids.symbol} style={s.label}>Sembol</label>
          <div style={{ position: "relative" }}>
            <input
              id={ids.symbol}
              value={symbol}
              onChange={(event) => {
                setSymbol(event.target.value);
                setShowSuggestions(true);
              }}
              onFocus={() => setShowSuggestions(true)}
              onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
              placeholder="THYAO, AAPL, BTC/USD..."
              style={s.input}
              autoComplete="off"
            />
            {showSuggestions && suggestions.length > 0 && (
              <div style={s.dropdown}>
                {suggestions.map((instrument) => (
                  <div key={instrument.symbol} style={s.dropdownItem} onMouseDown={() => onPickSuggestion(instrument.symbol)}>
                    <span style={{ fontWeight: 600 }}>{instrument.symbol}</span>
                    <span style={{ color: "var(--text-muted)", fontSize: 11, marginLeft: 8 }}>{instrument.name}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
        {/* Mode toggle: Adet (lot count) vs Tutar (budget) */}
        <div style={{ gridColumn: "span 2", display: "flex", gap: 6 }}>
          <button type="button" style={tabBtn(inputMode === "quantity")} onClick={() => setInputMode("quantity")}>
            Adet
          </button>
          <button type="button" style={tabBtn(inputMode === "amount")} onClick={() => setInputMode("amount")}>
            Tutar
          </button>
        </div>

        {inputMode === "quantity" ? (
          <>
            <div style={{ display: "grid", gap: 6 }}>
              <label htmlFor={ids.quantity} style={s.label}>Adet</label>
              <input id={ids.quantity} type="number" value={quantity} min={1} onChange={(event) => setQuantity(Number(event.target.value))} style={s.input} />
            </div>
            <div style={{ display: "grid", gap: 6 }}>
              <label htmlFor={ids.currentPriceQty} style={s.label}>Guncel Fiyat</label>
              <input id={ids.currentPriceQty} value={priceLoading ? "Yukleniyor..." : price > 0 ? price.toLocaleString("tr-TR") : "-"} readOnly style={{ ...s.input, opacity: 0.75 }} />
            </div>
          </>
        ) : (
          <>
            <div style={{ display: "grid", gap: 6 }}>
              <label htmlFor={ids.amount} style={s.label}>Tutar</label>
              <input
                id={ids.amount}
                type="number"
                value={amount || ""}
                min={0}
                step="any"
                onChange={(event) => setAmount(Number(event.target.value))}
                placeholder="örn. 5000"
                style={s.input}
              />
            </div>
            <div style={{ display: "grid", gap: 6 }}>
              <label htmlFor={ids.currentPriceAmt} style={s.label}>Guncel Fiyat</label>
              <input id={ids.currentPriceAmt} value={priceLoading ? "Yukleniyor..." : price > 0 ? price.toLocaleString("tr-TR") : "-"} readOnly style={{ ...s.input, opacity: 0.75 }} />
            </div>
            <div style={{ gridColumn: "span 2", display: "grid", gap: 6 }}>
              <div style={{
                padding: "10px 12px",
                borderRadius: 8,
                border: "1px solid var(--border-soft)",
                background: "var(--bg-panel)",
                fontSize: 12,
                color: "var(--text-muted)",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}>
                <span>📊 Alınacak miktar:</span>
                <span style={{ fontWeight: 700, color: "var(--text-primary)", fontSize: 15 }}>
                  {effectiveQty > 0 ? effectiveQty.toLocaleString("tr-TR") + " adet" : "—"}
                </span>
              </div>
              {amountLeftover > 0 && effectiveQty > 0 && (
                <div style={{ fontSize: 11, color: "var(--text-muted)", paddingLeft: 4 }}>
                  Kalan: {amountLeftover.toLocaleString("tr-TR", { maximumFractionDigits: 2 })} (artık para — alımda kullanılmaz)
                </div>
              )}
            </div>
          </>
        )}

        <div style={{ gridColumn: "span 2", display: "grid", gap: 6 }}>
          <label htmlFor={ids.total} style={s.label}>Toplam Tutar</label>
          <input id={ids.total} value={total > 0 ? total.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"} readOnly style={{ ...s.input, opacity: 0.75, fontWeight: 600 }} />
        </div>
      </div>
      {error && <div style={{ color: "var(--danger-text)", marginTop: 8, fontSize: 13 }}>{error}</div>}
    </Modal>
  );
}
