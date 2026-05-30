import PropTypes from "prop-types";

/**
 * Inline-SVG brand for the topbar + mobile drawer. Three ascending
 * chart bars with a curved underline (mark) plus the "FİNANS PORTALI"
 * wordmark.
 *
 * All colours come off CSS vars (--accent-solid for the mark + the
 * second wordmark word, --text-primary for the first word), so the
 * logo flips correctly between light and dark themes. The earlier
 * PNG approach was dark-background only and turned into a black box
 * on the light theme.
 *
 * `size` is the icon HEIGHT in px; the wordmark scales next to it.
 * Default 38 fits the topbar; the mobile drawer header uses 44.
 */
export default function BrandLogo({ size = 38 }) {
    const markPx = size;
    const fontPx = Math.round(size * 0.42);   // 16px at size 38
    return (
        <span
            style={{
                display: "inline-flex",
                alignItems: "center",
                gap: Math.round(size * 0.22),
                lineHeight: 1,
            }}
        >
            <svg
                width={markPx}
                height={markPx}
                viewBox="0 0 40 40"
                aria-hidden="true"
                style={{ display: "block", color: "var(--accent-solid, #10b981)" }}
            >
                {/* Three ascending bars — short, medium, tall */}
                <rect x="6"  y="22" width="6" height="10" rx="1.5" fill="currentColor" />
                <rect x="15" y="14" width="6" height="18" rx="1.5" fill="currentColor" />
                <rect x="24" y="6"  width="6" height="26" rx="1.5" fill="currentColor" />
                {/* Curved "growth" underline beneath the bars */}
                <path
                    d="M3 34 Q 20 42, 37 28"
                    stroke="currentColor"
                    strokeWidth="3"
                    fill="none"
                    strokeLinecap="round"
                />
            </svg>
            <span
                style={{
                    fontSize: fontPx,
                    fontWeight: 800,
                    letterSpacing: "0.02em",
                    whiteSpace: "nowrap",
                    color: "var(--text-primary)",
                }}
            >
                FİNANS
                <span style={{ color: "var(--accent-solid, #10b981)", marginLeft: "0.35em" }}>
                    PORTALI
                </span>
            </span>
        </span>
    );
}

BrandLogo.propTypes = {
    size: PropTypes.number,
};
