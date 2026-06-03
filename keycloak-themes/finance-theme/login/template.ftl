<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>

    <#-- Inline theme probe: runs synchronously before the body paints so we
         avoid a flash of dark-theme on a light-mode user. The React app
         appends ?kc_theme=light|dark to the authorize URL it builds for us.
         If absent, fall back to OS prefers-color-scheme. -->
    <script>
    (function () {
        try {
            var params = new URLSearchParams(window.location.search);
            var t = params.get("kc_theme");
            if (!t && window.matchMedia) {
                t = window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
            }
            if (t === "light") document.documentElement.setAttribute("data-theme", "light");
        } catch (e) { /* default to dark */ }
    })();
    </script>
</head>

<body class="${properties.kcBodyClass!}">
    <div class="finance-shell">

        <#-- ───────── FULL-PAGE BACKGROUND CHART ─────────
             Rendered once at the shell level so the line sweeps across both
             columns instead of being clipped at the brand/form panel seam.
             Sits behind everything via z-index; pointer-events disabled so
             it can't intercept form clicks. -->
        <svg class="finance-chart-art" viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
            <#-- Colours are driven by the theme via CSS classes (see login.css):
                 .chart-line / .chart-stroke → --chart-line-color,
                 .chart-bg-from / .chart-bg-to → --chart-bg-* . The hardcoded
                 stop-color/stroke attributes are dark-mode fallbacks if CSS
                 fails. This lets the same graph render in light mode (recoloured)
                 instead of being hidden. -->
            <defs>
                <linearGradient id="lineGlow" x1="0" y1="0" x2="1" y2="0">
                    <stop class="chart-line" offset="0%"  stop-color="#22c55e" stop-opacity="0.0"/>
                    <stop class="chart-line" offset="35%" stop-color="#22c55e" stop-opacity="0.85"/>
                    <stop class="chart-line" offset="100%" stop-color="#22c55e" stop-opacity="0.45"/>
                </linearGradient>
                <linearGradient id="areaFill" x1="0" y1="0" x2="0" y2="1">
                    <stop class="chart-line" offset="0%"   stop-color="#22c55e" stop-opacity="0.18"/>
                    <stop class="chart-line" offset="100%" stop-color="#22c55e" stop-opacity="0.0"/>
                </linearGradient>
                <radialGradient id="bgGlow" cx="35%" cy="55%" r="75%">
                    <stop class="chart-bg-from" offset="0%"   stop-color="#0c2a18" stop-opacity="1"/>
                    <stop class="chart-bg-to"   offset="100%" stop-color="#040a06" stop-opacity="1"/>
                </radialGradient>
            </defs>
            <rect width="1600" height="900" fill="url(#bgGlow)"/>
            <#-- Filled area under the line — softens the chart and gives the
                 page a more graph-like feel without dominating. -->
            <path d="M0,640 C140,615 230,680 340,545 C460,400 540,580 660,505 C780,430 880,375 980,420 C1080,465 1200,310 1330,335 C1450,355 1530,235 1600,210 L1600,900 L0,900 Z"
                  fill="url(#areaFill)"/>
            <#-- Glow (wide, fading) -->
            <path d="M0,640 C140,615 230,680 340,545 C460,400 540,580 660,505 C780,430 880,375 980,420 C1080,465 1200,310 1330,335 C1450,355 1530,235 1600,210"
                  fill="none" stroke="url(#lineGlow)" stroke-width="6"
                  stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
            <#-- Crisp foreground line -->
            <path class="chart-stroke" d="M0,640 C140,615 230,680 340,545 C460,400 540,580 660,505 C780,430 880,375 980,420 C1080,465 1200,310 1330,335 C1450,355 1530,235 1600,210"
                  fill="none" stroke="#22c55e" stroke-width="2.5"
                  stroke-linecap="round" stroke-linejoin="round" opacity="0.85"/>
        </svg>

        <#-- ───────── LEFT BRAND PANEL ───────── -->
        <aside class="finance-brand">
            <div class="finance-brand-header">
                <div class="finance-brand-logo">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5">
                        <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/>
                        <polyline points="16 7 22 7 22 13"/>
                    </svg>
                </div>
                <span class="finance-brand-name">${msg("financeBrandName")}</span>
            </div>

            <div class="finance-brand-body">
                <h1 class="finance-hero-title">
                    ${msg("financeHeroLine1")}<br/>
                    <span class="finance-hero-accent">${msg("financeHeroLine2")}</span><br/>
                    ${msg("financeHeroLine3")}
                </h1>
                <p class="finance-hero-lead">
                    ${msg("financeHeroLead")}
                </p>
                <div class="finance-badges">
                    <span>SOC 2</span>
                    <span class="finance-badge-sep">·</span>
                    <span>GDPR</span>
                    <span class="finance-badge-sep">·</span>
                    <span>ISO 27001</span>
                </div>
            </div>

            <div class="finance-brand-footer">
                ${msg("financeFooter")}
            </div>
        </aside>

        <#-- ───────── RIGHT FORM PANEL ───────── -->
        <section class="finance-form-panel">
            <div class="finance-card">
                <#-- Language switcher — uses Keycloak's native ?kc_locale=… URL
                     override. The inline script preserves all OTHER query
                     params (kc_theme, client_id, redirect_uri, state…) so the
                     auth flow doesn't break. Falls back to a plain anchor
                     when JS is disabled. -->
                <#-- Theme toggle — flips data-theme between light/dark on the
                     <html> element so the :root[data-theme="light"] CSS vars
                     apply instantly. Persists the choice in localStorage so it
                     survives the multi-step auth flow (login → 2FA → …). -->
                <button type="button" class="finance-theme-toggle" id="kc-theme-toggle"
                        aria-label="${msg('financeThemeToggleLabel')!'Theme'}" title="${msg('financeThemeToggleLabel')!'Theme'}">
                    <span class="finance-theme-icon finance-theme-icon-dark" aria-hidden="true">🌙</span>
                    <span class="finance-theme-icon finance-theme-icon-light" aria-hidden="true">☀️</span>
                </button>
                <div class="finance-lang-switch" aria-label="${msg('financeLangLabel')!'Language'}">
                    <a class="finance-lang-link ${(locale.currentLanguageTag!'')?contains('tr')?then('is-active','')}"
                       href="?kc_locale=tr"
                       data-lang="tr">TR</a>
                    <a class="finance-lang-link ${(locale.currentLanguageTag!'')?contains('en')?then('is-active','')}"
                       href="?kc_locale=en"
                       data-lang="en">EN</a>
                </div>
                <script>
                (function () {
                    var KEY = 'finance-kc-theme';
                    var root = document.documentElement;
                    // Stored choice wins over the probe's URL/OS guess.
                    try {
                        var saved = localStorage.getItem(KEY);
                        if (saved === 'light') root.setAttribute('data-theme', 'light');
                        else if (saved === 'dark') root.removeAttribute('data-theme');
                    } catch (e) { /* ignore */ }
                    var btn = document.getElementById('kc-theme-toggle');
                    if (btn) {
                        btn.addEventListener('click', function () {
                            var isLight = root.getAttribute('data-theme') === 'light';
                            if (isLight) { root.removeAttribute('data-theme'); }
                            else { root.setAttribute('data-theme', 'light'); }
                            try { localStorage.setItem(KEY, isLight ? 'dark' : 'light'); } catch (e) { /* ignore */ }
                        });
                    }
                })();
                </script>
                <script>
                (function () {
                    var links = document.querySelectorAll('.finance-lang-link');
                    links.forEach(function (a) {
                        a.addEventListener('click', function (e) {
                            e.preventDefault();
                            try {
                                var url = new URL(window.location.href);
                                // Pass both: ui_locales is honoured by the initial
                                // /auth endpoint, kc_locale sticks across steps.
                                url.searchParams.set('ui_locales', a.dataset.lang);
                                url.searchParams.set('kc_locale', a.dataset.lang);
                                window.location.assign(url.toString());
                            } catch (err) {
                                /* fall back to a plain reload */
                                window.location.search = '?ui_locales=' + a.dataset.lang +
                                                         '&kc_locale=' + a.dataset.lang;
                            }
                        });
                    });
                })();
                </script>

                <#-- Header (title + subtitle from login.ftl) -->
                <div class="finance-card-header">
                    <#nested "header">
                </div>

                <#-- Optional message banner (errors, info from Keycloak) -->
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="alert-${message.type} ${properties.kcAlertClass!} pf-m-<#if message.type = 'error'>danger<#else>${message.type}</#if>">
                        <div class="pf-c-alert__icon">
                            <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                            <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                            <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                            <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                        </div>
                        <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>

                <#-- Actual form HTML from login.ftl -->
                <#nested "form">

                <#-- Register link / footer info -->
                <#if displayInfo>
                    <div class="finance-card-footer">
                        <#nested "info">
                    </div>
                </#if>
            </div>
        </section>
    </div>
</body>
</html>
</#macro>
