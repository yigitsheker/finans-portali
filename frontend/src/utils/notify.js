/**
 * Centralised toast notifier — wraps react-hot-toast with the per-category
 * preferences from Settings → Bildirimler. Each category maps to a function;
 * if the user has the category turned off, the toast is silently skipped.
 *
 * Categories (mirrors NOTIF_ITEMS in Settings.jsx):
 *   transactions   — buy / sell confirmations
 *   budget         — budget-limit warnings (no backend feature yet, reserved)
 *   investments    — periodic portfolio summaries
 *   marketing      — news, product updates, offers
 *   push           — important activity (price alerts, system events)
 *   security       — sign-in & suspicious-activity alerts
 *
 *   notify.tx("Bought 10 THYAO @ ₺294.50")
 *   notify.push("Price alert: AAPL hit $200")
 *   notify("General message")              // un-gated, always shown
 */

import toast from "react-hot-toast";

const PREF_KEY = "notif-preferences";

// Read once at call-time so users don't need to refresh after toggling.
function isEnabled(category) {
  try {
    const raw = localStorage.getItem(PREF_KEY);
    if (!raw) return true; // default on — first-time users get notifications
    const prefs = JSON.parse(raw);
    return prefs[category] !== false;
  } catch {
    return true;
  }
}

function fire(category, message, opts = {}) {
  if (!isEnabled(category)) return null;
  const variant = opts.variant ?? "default";
  const duration = opts.duration ?? 4000;
  switch (variant) {
    case "success": return toast.success(message, { duration });
    case "error":   return toast.error(message, { duration });
    case "loading": return toast.loading(message, { duration });
    default:        return toast(message, { duration });
  }
}

const notify = (message, opts = {}) => {
  const duration = opts.duration ?? 4000;
  switch (opts.variant) {
    case "success": return toast.success(message, { duration });
    case "error":   return toast.error(message, { duration });
    case "loading": return toast.loading(message, { duration });
    default:        return toast(message, { duration });
  }
};

notify.tx          = (msg, opts) => fire("transactions", msg, { variant: "success", ...opts });
notify.budget      = (msg, opts) => fire("budget",       msg, { variant: "default", ...opts });
notify.investment  = (msg, opts) => fire("investments",  msg, { variant: "default", ...opts });
notify.marketing   = (msg, opts) => fire("marketing",    msg, { variant: "default", ...opts });
notify.push        = (msg, opts) => fire("push",         msg, { variant: "default", ...opts });
notify.security    = (msg, opts) => fire("security",     msg, { variant: "default", ...opts });

// Aliases for readability at call sites.
notify.transaction = notify.tx;
notify.alert       = notify.push;

// Re-export the raw toast object for callers that need full control
// (custom JSX content, dismiss, etc.).
notify.raw = toast;

export default notify;
