import { useId } from "react";
import Modal from "../../Modal";
import { InfoRow } from "./InfoRow";
import { portfolioStyles as s } from "./portfolioStyles";
import { usePriceDisplay } from "../../../contexts/CurrencyDisplayContext";

export function SellPositionModal({
  open,
  target,
  quantity,
  currentPrice,
  proceeds,
  saving,
  error,
  setQuantity,
  onSell,
  onClose,
}) {
  const quantityId = `${useId()}-sell-qty`;
  const { format: formatPrice } = usePriceDisplay();
  // Price values are in the position's native currency (TRY for BIST, USD for AAPL...).
  // Use symbol-based inference because PortfolioPosition doesn't carry an explicit type.
  const fmt = (v) => formatPrice(v, null, { symbol: target?.symbol });
  return (
    <Modal
      open={open}
      title={"Sat - " + (target?.symbol ?? "")}
      onClose={onClose}
      busy={saving}
      footer={
        <>
          <button style={s.ghostBtn} onClick={onClose} disabled={saving}>Vazgec</button>
          <button style={s.sellBtn} onClick={onSell} disabled={saving}>
            {saving ? "Isleniyor..." : "Sat"}
          </button>
        </>
      }
    >
      {target && (
        <div style={{ display: "grid", gap: 12 }}>
          <div style={s.infoBox}>
            <InfoRow label="Mevcut Adet" value={String(target.quantity)} />
            <InfoRow label="Alis Fiyati" value={fmt(target.avgCost)} />
            <InfoRow label="Guncel Fiyat" value={fmt(currentPrice)} />
          </div>
          <div style={{ display: "grid", gap: 6 }}>
            <label htmlFor={quantityId} style={s.label}>Satilacak Adet</label>
            <div style={{ display: "flex", gap: 8 }}>
              <input
                id={quantityId}
                type="number"
                value={quantity}
                min={1}
                max={Number(target.quantity)}
                onChange={(event) => setQuantity(Number(event.target.value))}
                style={{ ...s.input, flex: 1 }}
              />
              <button
                type="button"
                style={s.ghostBtn}
                onClick={() => setQuantity(Number(target.quantity))}
                disabled={saving}
              >
                Hepsini Sat
              </button>
            </div>
          </div>
          <div style={s.infoBox}>
            <InfoRow label="Elde Edilecek" value={fmt(proceeds)} valueColor="var(--green)" />
          </div>
          {Number(quantity) >= Number(target.quantity) && (
            <div style={s.warnBox}>Tum pozisyon satilacak ve portfoyden kaldirilacak.</div>
          )}
          {error && <div style={{ color: "var(--danger-text)", fontSize: 13 }}>{error}</div>}
        </div>
      )}
    </Modal>
  );
}
