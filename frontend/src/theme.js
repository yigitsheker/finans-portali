export function getStoredTheme() {
    return localStorage.getItem("theme") ?? "dark";
}

export function applyTheme(theme) {
    const root = document.documentElement;
    if (theme === "light") {
        root.setAttribute("data-theme", "light");
    } else {
        root.removeAttribute("data-theme");
    }
    localStorage.setItem("theme", theme);
}
