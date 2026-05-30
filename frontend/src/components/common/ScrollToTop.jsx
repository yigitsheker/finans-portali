import { useI18n } from "../../contexts/I18nContext";

/**
 * Floating "back to top" button. Mounted globally in App.jsx so every
 * page that scrolls past {@code threshold} pixels gets the same affordance
 * — instead of every page implementing its own copy.
 *
 * The fade-in is handled with CSS opacity + transition on `.fp-scrolltop`
 * (defined in index.css) so the button doesn't pop in and out abruptly.
 */
export default function ScrollToTop() {
    const { t } = useI18n();
    // Previous iterations hid this behind a scroll-threshold check that
    // was failing on some layouts (the html/body height:100% rule moves
    // the scroll context off window onto documentElement, and the
    // listener never fired). After three attempts to make conditional
    // visibility work the user explicitly asked for an unconditional
    // button — so it's permanently visible now. Cheap (one fixed-
    // position element) and dependable.

    const handleClick = () => {
        // Target every plausible scroll root — different layouts move
        // the scroll between window / html / body, so write to all.
        try { window.scrollTo({ top: 0, behavior: "smooth" }); } catch { /* older browsers */ }
        if (document.documentElement) document.documentElement.scrollTop = 0;
        if (document.body) document.body.scrollTop = 0;
    };

    // Inline styles override anything cascading from index.css so the
    // button can't be silently flipped invisible by a stray opacity:0
    // rule somewhere upstream.
    const style = {
        position: "fixed",
        bottom: 28,
        right: 28,
        width: 52,
        height: 52,
        borderRadius: "50%",
        border: "2px solid rgba(255,255,255,0.20)",
        background: "var(--accent-solid, #10b981)",
        color: "#ffffff",
        fontSize: 24,
        fontWeight: 900,
        cursor: "pointer",
        boxShadow: "0 8px 24px rgba(0,0,0,0.45)",
        zIndex: 9000,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        lineHeight: 1,
        padding: 0,
    };

    return (
        <button
            type="button"
            onClick={handleClick}
            aria-label={t("common.backToTop") || "Sayfa başına dön"}
            className="fp-scrolltop fp-scrolltop--visible"
            style={style}
        >
            ↑
        </button>
    );
}
