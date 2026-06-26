import { useId } from "react";
import { IconBarChart } from "../../common/icons";
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
  // Budget currency selector — lets the user enter the "Tutar" in TRY or USD
  // regardless of the instrument's native pricing currency (e.g. ₺10.000 of
  // AAPL). nativeCurrency is the instrument's own currency, shown on the total.
  amountCurrency = "TRY",
  setAmountCurrency = () => {},
  nativeCurrency = "TRY",
  // Crypto allows fractional lots (e.g. 0.1 BTC); everything else is whole lots.
  isCrypto = false,
}) {
  const curSym = (c) => (c === "USD" ? "$" : "₺");
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
  // Flat formatter for the read-only "Guncel Fiyat" cell — Sonar S3358
  // dings nested ternaries inline, and this expression appears twice in
  // the two input modes below.
  let currentPriceDisplay;
  if (priceLoading) {
    currentPriceDisplay = "Yukleniyor...";
  } else if (price > 0) {
    // Prefix with the instrument's own currency symbol so it's clear AAPL is
    // quoted in $ while THYAO is in ₺ (the price itself is always native).
    currentPriceDisplay = curSym(nativeCurrency) + price.toLocaleString("tr-TR");
  } else {
    currentPriceDisplay = "-";
  }
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
      busy={saving}
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
                  <div key={instrument.symbol} role="button" tabIndex={0} style={s.dropdownItem}
                    onMouseDown={() => onPickSuggestion(instrument.symbol)}
                    onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onPickSuggestion(instrument.symbol); } }}>
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
              <input id={ids.quantity} type="number" value={quantity} min={isCrypto ? 0 : 1} step={isCrypto ? "any" : "1"} onChange={(event) => setQuantity(Number(event.target.value))} style={s.input} />
            </div>
            <div style={{ display: "grid", gap: 6 }}>
              <label htmlFor={ids.currentPriceQty} style={s.label}>Guncel Fiyat</label>
              <input id={ids.currentPriceQty} value={currentPriceDisplay} readOnly style={{ ...s.input, opacity: 0.75 }} />
            </div>
          </>
        ) : (
          <>
            <div style={{ display: "grid", gap: 6 }}>
              <label htmlFor={ids.amount} style={s.label}>Tutar ({curSym(amountCurrency)})</label>
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
              {/* Budget currency: TRY/USD, independent of the instrument's
                  native pricing currency. */}
              <div style={{ display: "flex", gap: 4 }}>
                {["TRY", "USD"].map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setAmountCurrency(c)}
                    style={{
                      flex: 1,
                      padding: "5px 0",
                      border: "1px solid var(--border-card)",
                      background: amountCurrency === c ? "var(--accent)" : "var(--input-bg)",
                      color: amountCurrency === c ? "var(--accent-solid)" : "var(--text-muted)",
                      fontSize: 12,
                      fontWeight: 600,
                      cursor: "pointer",
                      borderRadius: 6,
                    }}
                  >
                    {curSym(c)} {c}
                  </button>
                ))}
              </div>
            </div>
            <div style={{ display: "grid", gap: 6 }}>
              <label htmlFor={ids.currentPriceAmt} style={s.label}>Guncel Fiyat</label>
              <input id={ids.currentPriceAmt} value={currentPriceDisplay} readOnly style={{ ...s.input, opacity: 0.75 }} />
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
                <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}><IconBarChart size={14} />Alınacak miktar:</span>
                <span style={{ fontWeight: 700, color: "var(--text-primary)", fontSize: 15 }}>
                  {effectiveQty > 0 ? effectiveQty.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) + " adet" : "—"}
                </span>
              </div>
              {amountLeftover > 0 && effectiveQty > 0 && (
                <div style={{ fontSize: 11, color: "var(--text-muted)", paddingLeft: 4 }}>
                  Kalan: {curSym(amountCurrency)}{amountLeftover.toLocaleString("tr-TR", { maximumFractionDigits: 2 })} (artık para — alımda kullanılmaz)
                </div>
              )}
              {amountCurrency !== nativeCurrency && effectiveQty > 0 && (
                <div style={{ fontSize: 11, color: "var(--text-muted)", paddingLeft: 4 }}>
                  {curSym(amountCurrency)} tutar, güncel kurdan {nativeCurrency} fiyatına çevrilerek hesaplandı.
                </div>
              )}
            </div>
          </>
        )}

        <div style={{ gridColumn: "span 2", display: "grid", gap: 6 }}>
          <label htmlFor={ids.total} style={s.label}>Toplam Tutar ({curSym(nativeCurrency)})</label>
          <input id={ids.total} value={total > 0 ? total.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"} readOnly style={{ ...s.input, opacity: 0.75, fontWeight: 600 }} />
        </div>
      </div>
      {error && <div style={{ color: "var(--danger-text)", marginTop: 8, fontSize: 13 }}>{error}</div>}
    </Modal>
  );
}
