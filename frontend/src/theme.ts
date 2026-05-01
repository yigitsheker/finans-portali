export type Theme = "dark" | "light";

export function getStoredTheme(): Theme {
    return (localStorage.getItem("theme") as Theme) ?? "dark";
}

export function applyTheme(theme: Theme) {
    const root = document.documentElement;
    if (theme === "light") {
        root.setAttribute("data-theme", "light");
    } else {
        root.removeAttribute("data-theme");
    }
    localStorage.setItem("theme", theme);
}
