// Theme stored in localStorage as "light" | "dark" | "system".
// "system" follows prefers-color-scheme and reacts to OS changes live.

const STORAGE_KEY = "theme";
const DARK_QUERY = "(prefers-color-scheme: dark)";

export function getStoredTheme() {
  const v = localStorage.getItem(STORAGE_KEY);
  if (v === "light" || v === "dark" || v === "system") return v;
  return "dark";
}

function resolveEffective(theme) {
  if (theme === "system") {
    return window.matchMedia && window.matchMedia(DARK_QUERY).matches ? "dark" : "light";
  }
  return theme;
}

function paint(effective) {
  const root = document.documentElement;
  if (effective === "light") {
    root.setAttribute("data-theme", "light");
  } else {
    root.removeAttribute("data-theme");
  }
}

let systemListenerInstalled = false;
let systemMql = null;
let systemHandler = null;

function ensureSystemListener(active) {
  if (!window.matchMedia) return;
  if (active && !systemListenerInstalled) {
    systemMql = window.matchMedia(DARK_QUERY);
    systemHandler = () => paint(systemMql.matches ? "dark" : "light");
    if (systemMql.addEventListener) systemMql.addEventListener("change", systemHandler);
    else systemMql.addListener(systemHandler);
    systemListenerInstalled = true;
  } else if (!active && systemListenerInstalled) {
    if (systemMql.removeEventListener) systemMql.removeEventListener("change", systemHandler);
    else systemMql.removeListener(systemHandler);
    systemListenerInstalled = false;
    systemMql = null;
    systemHandler = null;
  }
}

export function applyTheme(theme) {
  const t = (theme === "light" || theme === "dark" || theme === "system") ? theme : "dark";
  localStorage.setItem(STORAGE_KEY, t);
  paint(resolveEffective(t));
  ensureSystemListener(t === "system");
}
