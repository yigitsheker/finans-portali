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

        <#-- ───────── LEFT BRAND PANEL ───────── -->
        <aside class="finance-brand">
            <svg class="finance-chart-art" viewBox="0 0 1000 600" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
                <defs>
                    <linearGradient id="lineGlow" x1="0" y1="0" x2="1" y2="0">
                        <stop offset="0%"  stop-color="#22c55e" stop-opacity="0.0"/>
                        <stop offset="50%" stop-color="#22c55e" stop-opacity="0.85"/>
                        <stop offset="100%" stop-color="#22c55e" stop-opacity="0.0"/>
                    </linearGradient>
                    <radialGradient id="bgGlow" cx="40%" cy="60%" r="60%">
                        <stop offset="0%"   stop-color="#0c2a18" stop-opacity="1"/>
                        <stop offset="100%" stop-color="#040a06" stop-opacity="1"/>
                    </radialGradient>
                </defs>
                <rect width="1000" height="600" fill="url(#bgGlow)"/>
                <polyline points="0,420 80,400 150,440 230,360 320,390 410,300 500,330 590,250 690,290 800,180 900,210 1000,140"
                          fill="none" stroke="url(#lineGlow)" stroke-width="3"
                          stroke-linecap="round" stroke-linejoin="round" opacity="0.85"/>
                <polyline points="0,420 80,400 150,440 230,360 320,390 410,300 500,330 590,250 690,290 800,180 900,210 1000,140"
                          fill="none" stroke="#22c55e" stroke-width="1.5"
                          stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
            </svg>

            <div class="finance-brand-header">
                <div class="finance-brand-logo">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5">
                        <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/>
                        <polyline points="16 7 22 7 22 13"/>
                    </svg>
                </div>
                <span class="finance-brand-name">InvestHub</span>
            </div>

            <div class="finance-brand-body">
                <h1 class="finance-hero-title">
                    Hoş geldin.<br/>
                    <span class="finance-hero-accent">Kaldığın yerden</span><br/>
                    devam et.
                </h1>
                <p class="finance-hero-lead">
                    Hesabına giriş yaparak portföyüne, fiyat alarmlarına ve
                    piyasa verilerine saniyeler içinde ulaş.
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
                © 2026 InvestHub. Tüm hakları saklıdır.
            </div>
        </aside>

        <#-- ───────── RIGHT FORM PANEL ───────── -->
        <section class="finance-form-panel">
            <div class="finance-card">
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
