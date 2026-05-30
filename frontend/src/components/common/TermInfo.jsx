import PropTypes from "prop-types";
import { GLOSSARY } from "../../data/glossary";
import { useI18n } from "../../contexts/I18nContext";

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
 *
 * The component is keyboard-accessible: the icon is a button, tooltip
 * opens on focus as well as hover, and screen readers see the definition
 * as the button's aria-label.
 */
export default function TermInfo({ termKey, placement = "top" }) {
  const { lang } = useI18n();
  const entry = GLOSSARY[termKey];
  // Don't render anything (and don't blow up) for an unknown slug — that
  // way a typo in a JSX file degrades gracefully instead of crashing the
  // whole page tree.
  if (!entry) return null;

  const text = entry[lang] || entry.tr || entry.en;
  if (!text) return null;

  return (
    <span className={`fp-term-info fp-term-info--${placement}`}>
      <button
        type="button"
        className="fp-term-info__btn"
        aria-label={text}
        tabIndex={0}
        onClick={(e) => e.stopPropagation()}
        onMouseDown={(e) => e.stopPropagation()}
      >
        i
      </button>
      <span className="fp-term-info__tip" role="tooltip">{text}</span>
    </span>
  );
}

TermInfo.propTypes = {
  termKey: PropTypes.string.isRequired,
  placement: PropTypes.oneOf(["top", "bottom", "right", "left"]),
};
