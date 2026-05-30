import { useEffect, useRef, useState } from "react";
import PropTypes from "prop-types";
import { sendAnalysisChat } from "../../../api/analysisApi";
import { useI18n } from "../../../contexts/I18nContext";

/**
 * Finans Portalı AI chat surface.
 *
 * Local message history (resets on remount — by design; the page treats each
 * visit as a fresh conversation). Sends each turn to /api/v1/analysis/chat,
 * renders the structured reply (markdown-light body + optional scenario
 * cards) and the standard disclaimer.
 */
export default function Chatbot({ keycloak, lang = "tr" }) {
    const { t } = useI18n();
    const [messages, setMessages] = useState(() => [
        { role: "ai", reply: t("analysis.botGreeting") },
    ]);
    const [input, setInput] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    const scrollRef = useRef(null);
    // Quick-question presets resolved at render so the chip row swaps
    // locale instantly when the i18n toggle flips.
    const quickQuestions = [
        t("analysis.botQuick1"),
        t("analysis.botQuick2"),
        t("analysis.botQuick3"),
        t("analysis.botQuick4"),
        t("analysis.botQuick5"),
    ];

    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages, busy]);

    const send = async (text) => {
        const trimmed = (text ?? input).trim();
        if (!trimmed || busy) return;
        setError(null);
        setMessages((m) => [...m, { role: "user", reply: trimmed }]);
        setInput("");
        setBusy(true);
        try {
            const data = await sendAnalysisChat(keycloak, trimmed, lang);
            setMessages((m) => [...m, { role: "ai", ...data }]);
        } catch (e) {
            setError(t("analysis.botError"));
            console.error("[Chatbot]", e);
        } finally {
            setBusy(false);
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            send();
        }
    };

    return (
        <div style={s.card}>
            <div style={s.head}>
                <div style={s.headTitle}>Finans Portalı AI</div>
                <div style={s.headSub}>{t("analysis.botDisclaimerHint")}</div>
            </div>

            <div ref={scrollRef} style={s.scroll}>
                {messages.map((m, idx) => (
                    <Bubble key={idx} msg={m} />
                ))}
                {busy && (
                    <div style={{ ...s.bubble, ...s.bubbleAi }}>
                        <em style={{ color: "var(--text-muted)" }}>{t("analysis.botThinking")}</em>
                    </div>
                )}
                {error && (
                    <div style={{ ...s.bubble, ...s.bubbleAi, color: "#dc2626" }}>{error}</div>
                )}
            </div>

            <div style={s.quickRow}>
                {quickQuestions.map((q) => (
                    <button
                        key={q}
                        type="button"
                        onClick={() => send(q)}
                        disabled={busy}
                        style={s.quickBtn}
                    >
                        {q}
                    </button>
                ))}
            </div>

            <div style={s.composer}>
                <textarea
                    rows={2}
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={t("analysis.botPlaceholder")}
                    style={s.textarea}
                    disabled={busy}
                />
                <button type="button" onClick={() => send()} disabled={busy || !input.trim()} style={s.sendBtn}>
                    {t("analysis.botSend")}
                </button>
            </div>
        </div>
    );
}

Chatbot.propTypes = {
    keycloak: PropTypes.object,
    lang: PropTypes.oneOf(["tr", "en"]),
};

function Bubble({ msg }) {
    const isUser = msg.role === "user";
    return (
        <div style={{ ...s.bubble, ...(isUser ? s.bubbleUser : s.bubbleAi) }}>
            <div style={s.bubbleBody}>{renderMarkdownLite(msg.reply)}</div>
            {msg.scenarios?.length > 0 && (
                <div style={s.scenarios}>
                    {msg.scenarios.map((sc, i) => (
                        <div key={i} style={s.scenarioCard}>
                            <div style={s.scenarioTitle}>{sc.label}</div>
                            <div style={s.scenarioDesc}>{sc.description}</div>
                            <ul style={s.allocList}>
                                {sc.allocations?.map((a, j) => (
                                    <li key={j} style={s.allocItem}>
                                        <span>{a.assetClass}</span>
                                        <strong>{a.percent}%</strong>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    ))}
                </div>
            )}
            {msg.disclaimer && <div style={s.disclaimer}>{msg.disclaimer}</div>}
        </div>
    );
}

Bubble.propTypes = {
    msg: PropTypes.shape({
        role: PropTypes.string,
        reply: PropTypes.string,
        scenarios: PropTypes.array,
        disclaimer: PropTypes.string,
    }).isRequired,
};

// Lightweight markdown — bold (`**text**`) and bullet/newline-aware
// paragraphs. Avoids pulling in a markdown lib for this single use case.
function renderMarkdownLite(text) {
    if (!text) return null;
    return text.split("\n").map((line, i) => {
        const trimmed = line.trim();
        if (!trimmed) return <br key={i} />;
        const isBullet = trimmed.startsWith("- ") || trimmed.startsWith("• ");
        const cleaned = isBullet ? trimmed.slice(2) : trimmed;
        return (
            <div key={i} style={{ display: "flex", gap: 6, marginBottom: 4 }}>
                {isBullet && <span style={{ color: "var(--text-muted)" }}>•</span>}
                <span>{renderInline(cleaned)}</span>
            </div>
        );
    });
}

function renderInline(text) {
    const parts = text.split(/(\*\*[^*]+\*\*)/g);
    return parts.map((p, i) =>
        p.startsWith("**") && p.endsWith("**") ? (
            <strong key={i}>{p.slice(2, -2)}</strong>
        ) : (
            <span key={i}>{p}</span>
        )
    );
}

const s = {
    card: {
        display: "flex",
        flexDirection: "column",
        gap: 10,
        padding: 14,
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        background: "var(--bg-card)",
        // Cap the whole card to the viewport. Messages scroll inside the
        // `s.scroll` flex:1 region — without this lid the card kept growing
        // with every reply and stretched the surrounding page layout.
        height: "calc(100vh - 140px)",
        minHeight: 420,
        maxHeight: "calc(100vh - 140px)",
        overflow: "hidden",
    },
    head: { display: "flex", justifyContent: "space-between", alignItems: "baseline" },
    headTitle: { fontSize: 14, fontWeight: 700, color: "var(--text-primary)" },
    headSub: { fontSize: 10, color: "var(--text-muted)" },
    scroll: {
        flex: 1,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
        gap: 10,
        paddingRight: 4,
        minHeight: 240,
    },
    bubble: {
        padding: "10px 12px",
        borderRadius: 10,
        fontSize: 13,
        lineHeight: 1.45,
        maxWidth: "92%",
    },
    bubbleUser: {
        alignSelf: "flex-end",
        background: "var(--accent-solid)",
        color: "#fff",
    },
    bubbleAi: {
        alignSelf: "flex-start",
        background: "var(--bg-card-secondary, rgba(255,255,255,0.02))",
        border: "1px solid var(--border-card)",
        color: "var(--text-primary)",
    },
    bubbleBody: { whiteSpace: "pre-wrap" },
    scenarios: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 8, marginTop: 8 },
    scenarioCard: {
        padding: 10,
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        background: "var(--bg-card)",
    },
    scenarioTitle: { fontWeight: 700, fontSize: 12, marginBottom: 4, color: "var(--accent-solid)" },
    scenarioDesc: { fontSize: 11, color: "var(--text-muted)", marginBottom: 8, lineHeight: 1.4 },
    allocList: { listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 4 },
    allocItem: { display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--text-primary)" },
    disclaimer: { marginTop: 8, fontSize: 10, color: "var(--text-muted)", fontStyle: "italic", lineHeight: 1.4 },
    quickRow: { display: "flex", flexWrap: "wrap", gap: 6 },
    quickBtn: {
        padding: "6px 10px",
        fontSize: 11,
        borderRadius: 999,
        border: "1px solid var(--border-card)",
        background: "transparent",
        cursor: "pointer",
        color: "var(--text-secondary, var(--text-muted))",
    },
    composer: { display: "flex", gap: 8, alignItems: "stretch" },
    textarea: {
        flex: 1,
        padding: 10,
        fontSize: 13,
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        background: "var(--bg-input, var(--bg-card))",
        color: "var(--text-primary)",
        outline: "none",
        resize: "none",
        fontFamily: "inherit",
    },
    sendBtn: {
        padding: "0 16px",
        fontSize: 13,
        fontWeight: 600,
        background: "var(--accent-solid)",
        color: "#fff",
        border: "none",
        borderRadius: 8,
        cursor: "pointer",
    },
};
