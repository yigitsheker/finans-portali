import Modal from "../../Modal";
import type { MarketInstrument } from "../../../api/portfolioApi";
import { portfolioStyles as s } from "./portfolioStyles";

type AddPositionModalProps = {
  open: boolean;
  symbol: string;
  quantity: number;
  price: number;
  total: number;
  priceLoading: boolean;
  saving: boolean;
  showSuggestions: boolean;
  suggestions: MarketInstrument[];
  error: string | null;
  setSymbol: (value: string) => void;
  setQuantity: (value: number) => void;
  setShowSuggestions: (value: boolean) => void;
  onPickSuggestion: (symbol: string) => void;
  onSave: () => void;
  onClose: () => void;
};

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
}: AddPositionModalProps) {
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
          <label style={s.label}>Sembol</label>
          <div style={{ position: "relative" }}>
            <input
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
        <div style={{ display: "grid", gap: 6 }}>
          <label style={s.label}>Adet</label>
          <input type="number" value={quantity} min={1} onChange={(event) => setQuantity(Number(event.target.value))} style={s.input} />
        </div>
        <div style={{ display: "grid", gap: 6 }}>
          <label style={s.label}>Guncel Fiyat</label>
          <input value={priceLoading ? "Yukleniyor..." : price > 0 ? price.toLocaleString("tr-TR") : "-"} readOnly style={{ ...s.input, opacity: 0.75 }} />
        </div>
        <div style={{ gridColumn: "span 2", display: "grid", gap: 6 }}>
          <label style={s.label}>Toplam Tutar</label>
          <input value={total > 0 ? "$" + total.toLocaleString("tr-TR") : "-"} readOnly style={{ ...s.input, opacity: 0.75, fontWeight: 600 }} />
        </div>
      </div>
      {error && <div style={{ color: "var(--danger-text)", marginTop: 8, fontSize: 13 }}>{error}</div>}
    </Modal>
  );
}
