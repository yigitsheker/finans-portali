import { useEffect, useState } from "react";
import toast from "react-hot-toast";

/**
 * Custom toast body used by notify.js via toast.custom(...).
 *
 * Layout (mirrors the design mock):
 *   ┌─────────────────────────────────────────────────┐
 *   │ ▢  TYPE · time                              ×   │
 *   │    Bold title                                   │
 *   │    Plain message text                           │
 *   │    [badge1]  [badge2]                           │
 *   └─────────────────────────────────────────────────┘
 *
 * Color accent (icon background + type label + left border) is picked from
 * the `type` prop. Theme-aware via CSS variables.
 */

const TYPE_THEME = {
    success: { color: "#22c55e", tint: "rgba(34, 197, 94, 0.12)" },
    error:   { color: "#ef4444", tint: "rgba(239, 68, 68, 0.12)" },
    warning: { color: "#f59e0b", tint: "rgba(245, 158, 11, 0.12)" },
    info:    { color: "#3b82f6", tint: "rgba(59, 130, 246, 0.12)" },
    security:{ color: "#8b5cf6", tint: "rgba(139, 92, 246, 0.12)" },
    live:    { color: "#22c55e", tint: "rgba(34, 197, 94, 0.12)" },
};

const BADGE_TONE = {
    neutral:  { fg: "var(--text-primary)", bg: "var(--bg-panel2, #f3f4f6)" },
    positive: { fg: "#22c55e",             bg: "rgba(34, 197, 94, 0.12)" },
    negative: { fg: "#ef4444",             bg: "rgba(239, 68, 68, 0.12)" },
};

export default function AppToast({
    t,
    type = "info",
    typeLabel,        // small uppercase pill — defaults to type
    title,
    message,
    icon,
    timeLabel,        // small relative time next to typeLabel
    badges,           // optional array: [{ text, tone }]
}) {
    const theme = TYPE_THEME[type] || TYPE_THEME.info;

    // react-hot-toast flips `t.visible` true on mount and false ~duration before
    // unmounting; mirror that into our own slide+fade animation.
    const [shown, setShown] = useState(false);
    useEffect(() => {
        const id = requestAnimationFrame(() => setShown(t.visible));
        return () => cancelAnimationFrame(id);
    }, [t.visible]);

    return (
        <div
            style={{
                display: "flex",
                alignItems: "flex-start",
                gap: 12,
                minWidth: 340,
                maxWidth: 460,
                padding: "14px 14px 12px",
                background: "var(--bg-card)",
                color: "var(--text-primary)",
                border: "1px solid var(--border-card)",
                borderLeft: `4px solid ${theme.color}`,
                borderRadius: 12,
                boxShadow: "0 12px 32px rgba(0, 0, 0, 0.25), 0 2px 8px rgba(0,0,0,0.08)",
                transform: shown ? "translateY(0)" : "translateY(-12px)",
                opacity: shown ? 1 : 0,
                transition: "transform 220ms ease, opacity 220ms ease",
                pointerEvents: "auto",
            }}
            role="status"
            aria-live="polite"
        >
            {/* Icon medallion */}
            <div
                style={{
                    width: 36,
                    height: 36,
                    borderRadius: 10,
                    background: theme.tint,
                    color: theme.color,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: 18,
                    fontWeight: 700,
                    flexShrink: 0,
                }}
                aria-hidden="true"
            >
                {icon || defaultIcon(type)}
            </div>

            {/* Body */}
            <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 6 }}>
                {/* Header line: TYPE · time   ......   × */}
                <div style={{ display: "flex", alignItems: "center", gap: 8, minHeight: 18 }}>
                    {typeLabel && (
                        <span style={{
                            fontSize: 11,
                            fontWeight: 800,
                            letterSpacing: 0.6,
                            color: theme.color,
                            textTransform: "uppercase",
                        }}>
                            {typeLabel}
                        </span>
                    )}
                    {(typeLabel && timeLabel) && (
                        <span style={{ color: "var(--text-muted)", fontSize: 12 }}>·</span>
                    )}
                    {timeLabel && (
                        <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{timeLabel}</span>
                    )}
                    <span style={{ flex: 1 }} />
                    <button
                        type="button"
                        onClick={() => toast.dismiss(t.id)}
                        aria-label="Bildirimi kapat"
                        style={{
                            background: "transparent",
                            border: "none",
                            padding: 0,
                            width: 22,
                            height: 22,
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            fontSize: 18,
                            lineHeight: 1,
                            color: "var(--text-muted)",
                            cursor: "pointer",
                            borderRadius: 6,
                        }}
                    >
                        ×
                    </button>
                </div>

                {/* Title */}
                {title && (
                    <div style={{
                        fontSize: 15,
                        fontWeight: 700,
                        color: "var(--text-primary)",
                        lineHeight: 1.3,
                    }}>
                        {title}
                    </div>
                )}

                {/* Message */}
                {message && (
                    <div style={{
                        fontSize: 13,
                        color: "var(--text-muted)",
                        lineHeight: 1.4,
                    }}>
                        {message}
                    </div>
                )}

                {/* Optional badges row */}
                {Array.isArray(badges) && badges.length > 0 && (
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 4 }}>
                        {badges.map((b, i) => {
                            const tone = BADGE_TONE[b.tone] || BADGE_TONE.neutral;
                            return (
                                <span key={i} style={{
                                    fontSize: 12,
                                    fontWeight: 600,
                                    padding: "3px 8px",
                                    borderRadius: 6,
                                    background: tone.bg,
                                    color: tone.fg,
                                }}>
                                    {b.text}
                                </span>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}

function defaultIcon(type) {
    switch (type) {
        case "success":  return "↗";
        case "live":     return "↗";
        case "error":    return "✕";
        case "warning":  return "!";
        case "security": return "🔐";
        default:         return "ⓘ";
    }
}
