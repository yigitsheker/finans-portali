import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { useI18n } from "../../contexts/I18nContext";

/**
 * Floating "back to top" button. Mounted globally in App.jsx so every
 * page that scrolls past {@code threshold} pixels gets the same affordance
 * — instead of every page implementing its own copy.
 *
 * The fade-in is handled with CSS opacity + transition on `.fp-scrolltop`
 * (defined in index.css) so the button doesn't pop in and out abruptly.
 */
export default function ScrollToTop({ threshold = 80 }) {
    const [visible, setVisible] = useState(false);
    const { t } = useI18n();

    useEffect(() => {
        // Read every scroll source we know about. The CSS sets
        // html/body { height: 100% } which on some browsers shifts the
        // scroll from `window` to `documentElement`, leaving
        // `window.scrollY` permanently at 0 and hiding the button forever.
        // Reading all three covers every layout we've shipped.
        const readScroll = () => Math.max(
            window.scrollY || 0,
            document.documentElement?.scrollTop || 0,
            document.body?.scrollTop || 0,
        );
        // Detect whether the document is meaningfully scrollable at all —
        // for short pages we just keep the button hidden, but as soon as
        // the user has scrolled past the threshold OR the page is taller
        // than the viewport, show the button. The second condition is a
        // safety net for browsers/layouts where the scroll event never
        // fires on the elements we listen to.
        const recompute = () => {
            const docHeight = Math.max(
                document.documentElement?.scrollHeight || 0,
                document.body?.scrollHeight || 0,
            );
            const isScrollable = docHeight > window.innerHeight + 50;
            setVisible(isScrollable && readScroll() > threshold);
        };
        recompute();
        window.addEventListener("scroll", recompute, { passive: true });
        document.addEventListener("scroll", recompute, { passive: true, capture: true });
        window.addEventListener("resize", recompute, { passive: true });
        // Some content (data tables) populates asynchronously after mount,
        // so re-evaluate on a short timer too.
        const timer = setInterval(recompute, 1500);
        return () => {
            window.removeEventListener("scroll", recompute);
            document.removeEventListener("scroll", recompute, { capture: true });
            window.removeEventListener("resize", recompute);
            clearInterval(timer);
        };
    }, [threshold]);

    const handleClick = () => {
        // Same multi-target story — scroll every plausible root so we
        // actually reach the top regardless of which element holds the
        // overflow.
        try { window.scrollTo({ top: 0, behavior: "smooth" }); } catch { /* older browsers */ }
        if (document.documentElement) document.documentElement.scrollTop = 0;
        if (document.body) document.body.scrollTop = 0;
    };

    return (
        <button
            type="button"
            onClick={handleClick}
            aria-label={t("common.backToTop") || "Sayfa başına dön"}
            className={`fp-scrolltop${visible ? " fp-scrolltop--visible" : ""}`}
        >
            ↑
        </button>
    );
}

ScrollToTop.propTypes = {
    threshold: PropTypes.number,
};
