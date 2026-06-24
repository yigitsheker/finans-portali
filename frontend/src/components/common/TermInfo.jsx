import PropTypes from "prop-types";
import { useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useI18n } from "../../contexts/I18nContext";
import { GLOSSARY } from "../../data/glossary";

const GAP = 8;
const MAX_WIDTH = 280;
const VIEWPORT_PADDING = 8;

/**
 * Inline finance-term explainer. Drop it next to a term in the UI; the
 * rendered (i) icon shows a hover/focus tooltip with the definition from
 * `glossary.js` for the current UI language.
 *
 * Usage:
 *   <span>TÜFE Endeksi <TermInfo termKey="cpi" /></span>
 *
 * `placement` controls where the popup appears relative to the icon:
 *   "top" (default) — above the icon, arrow pointing down
 *   "bottom"        — below the icon, arrow pointing up
 *   "right"         — to the right, arrow pointing left
 *   "bottom-right" / "top-right" — anchored to the icon's right edge so the
 *   tooltip grows leftward (for terms sitting near the page's right edge)
 *
 * Positioned via a `document.body` portal with coordinates computed from the
 * trigger's bounding rect (not CSS `position:absolute` inside the normal
 * flow) — this is deliberate: almost every call site lives inside a table
 * header wrapped by a horizontally-scrollable container
 * (`.fp-card-table` / `overflowX:auto`), and an absolutely-positioned
 * descendant gets hard-clipped at that ancestor's overflow boundary. A
 * fixed-position portal escapes every ancestor's clipping/stacking context,
 * so the popup is never cut off regardless of which table it's used in.
 */
export default function TermInfo({ termKey, placement = "top" }) {
  const { lang } = useI18n();
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState(null);
  const btnRef = useRef(null);

  const entry = GLOSSARY[termKey];
  // Don't render anything (and don't blow up) for an unknown slug — that
  // way a typo in a JSX file degrades gracefully instead of crashing the
  // whole page tree.
  if (!entry) return null;

  const text = entry[lang] || entry.tr || entry.en;
  if (!text) return null;

  const show = () => {
    const el = btnRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const anchorRight = placement === "right";
    const above = placement === "top" || placement === "top-right";
    const rightAligned = placement === "bottom-right" || placement === "top-right";

    let top;
    let left;
    if (anchorRight) {
      top = r.top + r.height / 2;
      left = r.right + GAP;
    } else if (above) {
      top = r.top - GAP;
      left = rightAligned ? r.right : r.left;
    } else {
      top = r.bottom + GAP;
      left = rightAligned ? r.right : r.left;
    }

    // Clamp horizontally so the popup never runs past the viewport edge —
    // a fixed-position element has nothing else to stop it.
    const vw = window.innerWidth;
    if (!anchorRight) {
      if (rightAligned) {
        left = Math.min(left, vw - VIEWPORT_PADDING);
        left = Math.max(left, MAX_WIDTH + VIEWPORT_PADDING);
      } else {
        left = Math.max(left, VIEWPORT_PADDING);
        left = Math.min(left, vw - MAX_WIDTH - VIEWPORT_PADDING);
      }
    }

    setCoords({ top, left, anchorRight, above, rightAligned });
    setOpen(true);
  };

  const hide = () => setOpen(false);

  const tooltip = open && coords && createPortal(
    <span
      role="tooltip"
      className="fp-term-info__tip fp-term-info__tip--portal"
      style={{
        position: "fixed",
        top: coords.top,
        left: coords.anchorRight ? coords.left : undefined,
        right: !coords.anchorRight && coords.rightAligned ? window.innerWidth - coords.left : undefined,
        ...(!coords.anchorRight && !coords.rightAligned ? { left: coords.left } : {}),
        transform: coords.anchorRight
          ? "translateY(-50%)"
          : coords.above
            ? "translateY(-100%)"
            : "none",
        opacity: 1,
        pointerEvents: "none",
      }}
    >
      {text}
    </span>,
    document.body
  );

  return (
    <span className={`fp-term-info fp-term-info--${placement}`}>
      <button
        ref={btnRef}
        type="button"
        className="fp-term-info__btn"
        aria-label={text}
        tabIndex={0}
        onClick={(e) => e.stopPropagation()}
        onMouseDown={(e) => e.stopPropagation()}
        onMouseEnter={show}
        onMouseLeave={hide}
        onFocus={show}
        onBlur={hide}
      >
        i
      </button>
      {tooltip}
    </span>
  );
}

TermInfo.propTypes = {
  termKey: PropTypes.string.isRequired,
  placement: PropTypes.oneOf(["top", "bottom", "right", "bottom-right", "top-right"]),
};
