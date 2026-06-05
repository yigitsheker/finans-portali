import { useEffect } from "react";
import { createPortal } from "react-dom";

export default function Modal({ open, title, children, onClose, footer, maxWidth = 560, busy = false }) {
    // When `busy` (e.g. a save/submit in flight) the modal won't close via
    // Escape, backdrop click or the ✕ — prevents a half-finished action from
    // being dismissed mid-request.
    const requestClose = () => { if (!busy) onClose(); };
    useEffect(() => {
        function onKey(e) { if (e.key === "Escape" && !busy) onClose(); }
        if (open) {
            window.addEventListener("keydown", onKey);
            // Prevent body scroll
            document.body.style.overflow = 'hidden';
        }
        return () => {
            window.removeEventListener("keydown", onKey);
            document.body.style.overflow = 'unset';
        };
    }, [open, onClose, busy]);

    if (!open) return null;

    const modalContent = (
        <div
            style={{
                position: "fixed",
                inset: 0,
                background: "rgba(0,0,0,0.85)",
                backdropFilter: "blur(4px)",
                zIndex: 9999,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                padding: 16
            }}
            onMouseDown={requestClose}
            role="presentation"
        >
            <div
                className="fp-modal-card"
                style={{
                    width: "100%",
                    maxWidth,
                    maxHeight: "90vh",
                    overflowY: "auto",
                    borderRadius: 12,
                    background: "var(--bg-panel, #1a1a1a)",
                    border: "1px solid var(--border-card, #333)",
                    boxShadow: "0 20px 60px rgba(0,0,0,0.5)",
                    position: "relative",
                    zIndex: 10000
                }}
                onMouseDown={(e) => e.stopPropagation()}
                role="dialog"
                aria-modal="true"
            >
                {/* Header */}
                <div style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "14px 16px",
                    borderBottom: "1px solid var(--border, #333)",
                    background: "var(--bg-panel, #1a1a1a)"
                }}>
                    <h2 style={{
                        margin: 0,
                        fontWeight: 600,
                        color: "var(--text-primary, #fff)",
                        fontSize: 15
                    }}>{title}</h2>
                    <button
                        style={{
                            width: 32,
                            height: 32,
                            borderRadius: 6,
                            border: "1px solid var(--border-card, #333)",
                            background: "var(--input-bg, #2a2a2a)",
                            color: "var(--text-muted, #999)",
                            cursor: "pointer",
                            fontSize: 12,
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            opacity: busy ? 0.5 : 1
                        }}
                        onClick={requestClose}
                        disabled={busy}
                        aria-label="Close"
                    >
                        ✕
                    </button>
                </div>

                {/* Body */}
                <div style={{ padding: 16, background: "var(--bg-panel, #1a1a1a)" }}>{children}</div>

                {/* Footer */}
                {footer && (
                    <div style={{
                        display: "flex",
                        justifyContent: "flex-end",
                        gap: 8,
                        padding: "12px 16px",
                        borderTop: "1px solid var(--border, #333)",
                        background: "var(--bg-panel, #1a1a1a)"
                    }}>
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );

    return createPortal(modalContent, document.body);
}
