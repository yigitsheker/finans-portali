import { portfolioStyles as s } from "./portfolioStyles";

type InfoRowProps = {
  label: string;
  value: string;
  valueColor?: string;
};

export function InfoRow({ label, value, valueColor }: InfoRowProps) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
      <span style={{ color: "var(--text-muted)", fontSize: 13 }}>{label}</span>
      <span style={{ color: valueColor ?? "var(--text-primary)", fontWeight: 600, fontSize: 13 }}>{value}</span>
    </div>
  );
}

export function SummaryCard({ label, value, sub, valueColor }: InfoRowProps & { sub?: string }) {
  return (
    <div style={s.summaryCard}>
      <div style={s.summaryLabel}>{label}</div>
      <div style={{ ...s.summaryValue, color: valueColor ?? "var(--text-primary)" }}>{value}</div>
      {sub && <div style={s.summarySub}>{sub}</div>}
    </div>
  );
}
