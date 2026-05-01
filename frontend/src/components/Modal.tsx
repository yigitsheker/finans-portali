import { useEffect } from "react";

type Props = {
    open: boolean;
    title: string;
    children: React.ReactNode;
    onClose: () => void;
    footer?: React.ReactNode;
};

export default function Modal({ open, title, children, onClose, footer }: Props) {
    useEffect(() => {
        function onKey(e: KeyboardEvent) { if (e.key === "Escape") onClose(); }
        if (open) window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [open, onClose]);

    if (!open) return null;

    return (
        <div style={s.backdrop} onMouseDown={onClose}>
            <div style={s.modal} onMouseDown={(e) => e.stopPropagation()}>
                <div style={s.header}>
                    <div style={{ fontWeight: 600, color: "var(--text-primary)", fontSize: 15 }}>{title}</div>
                    <button style={s.closeBtn} onClick={onClose} aria-label="Close">✕</button>
                </div>
                <div style={s.body}>{children}</div>
                {footer && <div style={s.footer}>{footer}</div>}
            </div>
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    backdrop: {
        position: "fixed", inset: 0,
        background: "rgba(0,0,0,0.65)", backdropFilter: "blur(4px)",
        display: "grid", placeItems: "center", zIndex: 50, padding: 16,
    },
    modal: {
        width: "min(560px, 96vw)", maxHeight: "90vh", overflowY: "auto",
        borderRadius: 12, background: "var(--bg-panel)",
        border: "1px solid var(--border-card)", boxShadow: "var(--shadow)",
    },
    header: {
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "14px 16px", borderBottom: "1px solid var(--border)",
    },
    closeBtn: {
        width: 32, height: 32, borderRadius: 6,
        border: "1px solid var(--border-card)", background: "var(--input-bg)",
        color: "var(--text-muted)", cursor: "pointer", fontSize: 12,
    },
    body: { padding: 16 },
    footer: {
        display: "flex", justifyContent: "flex-end", gap: 8,
        padding: "12px 16px", borderTop: "1px solid var(--border)",
    },
};
