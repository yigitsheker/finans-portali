import type { ReactNode } from "react";

export default function Card({
                                 title,
                                 right,
                                 children,
                             }: {
    title?: string;
    right?: ReactNode;
    children: ReactNode;
}) {
    return (
        <section style={s.card}>
            {(title || right) && (
                <div style={s.head}>
                    <div style={s.hTitle}>{title}</div>
                    <div>{right}</div>
                </div>
            )}
            <div>{children}</div>
        </section>
    );
}

const s: Record<string, React.CSSProperties> = {
    card: {
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 16,
        padding: 14,
    },
    head: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 10,
        marginBottom: 10,
    },
    hTitle: { fontWeight: 800, color: "var(--text-primary)" },
};