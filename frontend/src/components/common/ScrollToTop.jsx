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
export default function ScrollToTop({ threshold = 400 }) {
    const [visible, setVisible] = useState(false);
    const { t } = useI18n();

    useEffect(() => {
        const onScroll = () => setVisible(window.scrollY > threshold);
        onScroll();
        window.addEventListener("scroll", onScroll, { passive: true });
        return () => window.removeEventListener("scroll", onScroll);
    }, [threshold]);

    const handleClick = () => {
        window.scrollTo({ top: 0, behavior: "smooth" });
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
