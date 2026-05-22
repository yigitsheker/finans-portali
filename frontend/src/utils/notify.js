/**
 * Centralised toast notifier — wraps react-hot-toast with the per-category
 * preferences from Settings → Bildirimler. Renders an AppToast with a
 * type-colored medallion, uppercase type label ("CANLI", "GÜVENLİK"…),
 * relative time, bold title and message. Optional bottom-row badges
 * (e.g. symbol pill + change %) via the `badges` opt.
 *
 *   notify.tx("THYAO 10 adet alındı")
 *   notify.push("THYAO hedef fiyatınıza (195,50 ₺) ulaştı.", {
 *       title: "Fiyat Alarmı: THYAO",
 *       badges: [{ text: "THYAO" }, { text: "+%2,84", tone: "positive" }],
 *   })
 *   notify("Genel mesaj", { variant: "success" })
 */

import { createElement } from "react";
import toast from "react-hot-toast";
import AppToast from "../components/AppToast";

const PREF_KEY = "notif-preferences";
const LANG_KEY = "i18n-lang";

// Per-category presentation. Title is the user-supplied bold heading; if
// omitted we fall back to the localized type label.
const CATEGORY = {
    transactions: {
        type: "success",
        icon: "💱",
        typeLabelTr: "İŞLEM",        typeLabelEn: "TRANSACTION",
    },
    push: {
        type: "live",
        icon: "↗",
        typeLabelTr: "CANLI",        typeLabelEn: "LIVE",
    },
    security: {
        type: "security",
        icon: "🔐",
        typeLabelTr: "GÜVENLİK",     typeLabelEn: "SECURITY",
    },
    investments: {
        type: "info",
        icon: "📊",
        typeLabelTr: "YATIRIM",      typeLabelEn: "INVESTMENT",
    },
    budget: {
        type: "warning",
        icon: "💰",
        typeLabelTr: "BÜTÇE",        typeLabelEn: "BUDGET",
    },
    marketing: {
        type: "info",
        icon: "📣",
        typeLabelTr: "DUYURU",       typeLabelEn: "ANNOUNCEMENT",
    },
};

// Variant → preset for the un-gated `notify()` call.
const VARIANT_PRESET = {
    success: { type: "success", icon: "✓",  typeLabelTr: "BAŞARILI",  typeLabelEn: "SUCCESS" },
    error:   { type: "error",   icon: "✕",  typeLabelTr: "HATA",      typeLabelEn: "ERROR" },
    warning: { type: "warning", icon: "!",  typeLabelTr: "UYARI",     typeLabelEn: "WARNING" },
    loading: { type: "info",    icon: "…",  typeLabelTr: "İŞLENİYOR", typeLabelEn: "LOADING" },
    default: { type: "info",    icon: "ⓘ",  typeLabelTr: "BİLGİ",     typeLabelEn: "INFO" },
};

function lang() {
    try {
        const v = (localStorage.getItem(LANG_KEY) || "tr").toLowerCase();
        return v === "en" ? "en" : "tr";
    } catch {
        return "tr";
    }
}

function isEnabled(category) {
    try {
        const raw = localStorage.getItem(PREF_KEY);
        if (!raw) return true;
        const prefs = JSON.parse(raw);
        return prefs[category] !== false;
    } catch {
        return true;
    }
}

function localizedTypeLabel(preset, override) {
    if (override) return override;
    return lang() === "en" ? preset.typeLabelEn : preset.typeLabelTr;
}

function nowLabel() {
    return lang() === "en" ? "now" : "şimdi";
}

function showCustom(props) {
    const duration = props.duration ?? 5000;
    return toast.custom(
        (t) => createElement(AppToast, { t, ...props }),
        { duration }
    );
}

function fire(category, message, opts = {}) {
    if (!isEnabled(category)) return null;
    const preset = CATEGORY[category] || VARIANT_PRESET.default;
    return showCustom({
        type: opts.type ?? preset.type,
        typeLabel: localizedTypeLabel(preset, opts.typeLabel),
        timeLabel: opts.timeLabel ?? nowLabel(),
        title: opts.title,
        message,
        icon: opts.icon ?? preset.icon,
        badges: opts.badges,
        duration: opts.duration,
    });
}

// Un-gated default — picks a preset from `variant`.
const notify = (message, opts = {}) => {
    const variant = opts.variant ?? "default";
    const preset = VARIANT_PRESET[variant] || VARIANT_PRESET.default;
    return showCustom({
        type: opts.type ?? preset.type,
        typeLabel: localizedTypeLabel(preset, opts.typeLabel),
        timeLabel: opts.timeLabel ?? nowLabel(),
        title: opts.title,
        message,
        icon: opts.icon ?? preset.icon,
        badges: opts.badges,
        duration: opts.duration,
    });
};

notify.tx          = (msg, opts) => fire("transactions", msg, opts);
notify.budget      = (msg, opts) => fire("budget",       msg, opts);
notify.investment  = (msg, opts) => fire("investments",  msg, opts);
notify.marketing   = (msg, opts) => fire("marketing",    msg, opts);
notify.push        = (msg, opts) => fire("push",         msg, opts);
notify.security    = (msg, opts) => fire("security",     msg, opts);

// Aliases for readability at call sites.
notify.transaction = notify.tx;
notify.alert       = notify.push;

// Re-export the raw toast object for callers that need full control.
notify.raw = toast;

export default notify;
