import { useEffect, useState } from "react";
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
    const [visible, setVisible] = useState(false);

    // Show only after the page is scrolled down. Earlier attempts failed
    // because on some layouts the scroll context is an inner container, not
    // window — a plain window 'scroll' listener never fired. We listen in the
    // CAPTURE phase (scroll doesn't bubble, but it IS captured) and read the
    // scrolled element off the event target, so any scroll root is covered.
    useEffect(() => {
        const THRESHOLD = 250;
        const onScroll = (e) => {
            const tgt = e && e.target;
            const top = Math.max(
                window.scrollY || 0,
                document.documentElement ? document.documentElement.scrollTop : 0,
                document.body ? document.body.scrollTop : 0,
                tgt && tgt.nodeType === 1 ? (tgt.scrollTop || 0) : 0,
            );
            setVisible(top > THRESHOLD);
        };
        onScroll();
        window.addEventListener("scroll", onScroll, true);
        window.addEventListener("resize", onScroll);
        return () => {
            window.removeEventListener("scroll", onScroll, true);
            window.removeEventListener("resize", onScroll);
        };
    }, []);

    const handleClick = () => {
        // Try the well-known scroll roots first.
        try { window.scrollTo({ top: 0, behavior: "smooth" }); } catch { /* older browsers */ }
        if (document.documentElement) document.documentElement.scrollTop = 0;
        if (document.body) document.body.scrollTop = 0;

        // If none of those moved the page, the real scroll lives on an
        // inner container (Layout's main, #root, .fp-shell, …). Walk
        // every element on the page and zero anything that's actually
        // scrolled. Cheap O(N) sweep — a few hundred nodes at most —
        // and runs once per click so it doesn't matter perf-wise.
        const all = document.querySelectorAll("*");
        for (let i = 0; i < all.length; i++) {
            const el = all[i];
            if (el.scrollTop > 0) {
                try { el.scrollTo({ top: 0, behavior: "smooth" }); }
                catch { el.scrollTop = 0; }
            }
        }
    };

    // Inline styles override anything cascading from index.css so the
    // button can't be silently flipped invisible by a stray opacity:0
    // rule somewhere upstream.
    const style = {
        position: "fixed",
        bottom: 28,
        // Bottom-LEFT, not bottom-right: the right corner is where page action
        // buttons live (e.g. the Analiz chatbot's "Gönder" button), and a
        // fixed FAB there sat right on top of them. There's no left sidebar
        // anymore, so the bottom-left corner is empty on every page.
        left: 28,
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
            className={`fp-scrolltop${visible ? " fp-scrolltop--visible" : ""}`}
            style={style}
        >
            ↑
        </button>
    );
}
