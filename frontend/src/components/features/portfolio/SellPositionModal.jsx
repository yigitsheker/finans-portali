import Modal from "../../Modal";
import { InfoRow } from "./InfoRow";
import { portfolioStyles as s } from "./portfolioStyles";

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
  return (
    <Modal
      open={open}
      title={"Sat - " + (target?.symbol ?? "")}
      onClose={onClose}
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
            <InfoRow label="Alis Fiyati" value={target.avgCost ? "$" + Number(target.avgCost).toLocaleString("tr-TR") : "-"} />
            <InfoRow label="Guncel Fiyat" value={currentPrice > 0 ? "$" + currentPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"} />
          </div>
          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>Satilacak Adet</label>
            <input
              type="number"
              value={quantity}
              min={1}
              max={Number(target.quantity)}
              onChange={(event) => setQuantity(Number(event.target.value))}
              style={s.input}
            />
          </div>
          <div style={s.infoBox}>
            <InfoRow label="Elde Edilecek" value={proceeds > 0 ? "$" + proceeds.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"} valueColor="var(--green)" />
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
