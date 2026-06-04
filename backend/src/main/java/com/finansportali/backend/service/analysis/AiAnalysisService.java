package com.finansportali.backend.service.analysis;

import com.finansportali.backend.dto.response.analysis.AnalysisInstrumentDto;
import com.finansportali.backend.dto.response.analysis.ChatResponseDto;
import com.finansportali.backend.dto.response.analysis.ChatResponseDto.Allocation;
import com.finansportali.backend.dto.response.analysis.ChatResponseDto.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chatbot brain for the Analysis page.
 *
 * <p>Today this is a pattern-matching mock — it inspects the user message,
 * maps it to one of a handful of intents (budget allocation, instrument
 * lookup, low-risk recommendation, general help) and returns a structured
 * answer. The {@link #generateReply(String, String)} entrypoint is the only
 * thing the controller calls, so swapping in a real LLM later means
 * replacing this method's body (e.g. with a WebClient call to OpenAI /
 * Gemini / Groq) without touching any other code.
 *
 * <p>Every response carries the same investment-advice disclaimer; the
 * frontend renders it verbatim under each AI bubble.
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private static final String DISCLAIMER_TR =
            "Bu içerik yatırım tavsiyesi değildir. Karar vermeden önce kendi araştırmanızı yapmalı veya lisanslı bir yatırım danışmanına başvurmalısınız.";
    private static final String DISCLAIMER_EN =
            "This is not investment advice. Please do your own research or consult a licensed advisor before deciding.";

    private static final String PCT_FORMAT = "%+.2f%%";

    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(\\d{2,8})\\s*(tl|tl\\.|tl,|₺|lira)", Pattern.CASE_INSENSITIVE);

    private final InstrumentAnalysisService instrumentService;
    private final LlmClient llm;

    public AiAnalysisService(InstrumentAnalysisService instrumentService, LlmClient llm) {
        this.instrumentService = instrumentService;
        this.llm = llm;
    }

    /**
     * Single entrypoint for the chatbot. Routes the message through the
     * rule-based intents (budget, instrument lookup) first, then the live
     * LLM when configured, then the remaining mock intents, and finally a
     * help message. Every reply carries the localized disclaimer footer.
     */
    public ChatResponseDto generateReply(String message, String lang) {
        String text = message == null ? "" : message.trim();
        String l = (lang == null || lang.isBlank()) ? "tr" : lang.toLowerCase(Locale.ROOT);

        // The budget intent stays rule-based — it returns the structured
        // {scenarios: [...]} payload the UI renders as cards, which is more
        // reliable than asking the LLM to emit valid JSON every time.
        ChatResponseDto budget = tryBudgetIntent(text, l);
        if (budget != null) return withFooter(budget, l);

        // Single-instrument quick-look stays rule-based too — instrument
        // data is already in memory, no need to round-trip through the LLM.
        ChatResponseDto inst = tryInstrumentIntent(text, l);
        if (inst != null) return withFooter(inst, l);

        // Free-form questions go to the real LLM when configured. Falls
        // through to the deterministic mocks when no API key is set.
        if (llm.isEnabled()) {
            ChatResponseDto live = tryLiveLlm(text, l);
            if (live != null) return withFooter(live, l);
        }

        ChatResponseDto out = tryLowRiskIntent(text, l);
        if (out != null) return withFooter(out, l);

        out = tryLongTermIntent(text, l);
        if (out != null) return withFooter(out, l);

        return withFooter(buildHelpResponse(l), l);
    }

    // ── Live LLM path ────────────────────────────────────────────────────

    private ChatResponseDto tryLiveLlm(String userMessage, String lang) {
        String system = buildSystemPrompt(lang);
        String enrichedUser = appendMarketContext(userMessage, lang);
        String reply = llm.complete(system, enrichedUser);
        if (reply == null || reply.isBlank()) return null;
        ChatResponseDto r = new ChatResponseDto();
        r.setReply(reply.trim());
        return r;
    }

    /**
     * Persona + tone + safety rules baked into the system prompt. Same
     * disclaimer the mock appends — that way the AI text body can already
     * contain risk-language and the UI footer disclaimer doesn't read as
     * a contradiction.
     */
    private String buildSystemPrompt(String lang) {
        if ("en".equals(lang)) {
            return """
                You are "Finans Portalı AI", a finance assistant inside a Turkish investment portal.
                Audience: retail investors browsing stocks, crypto, FX, commodities, funds, bonds and inflation data.

                STRICT SCOPE — finance only:
                - You ONLY answer questions about finance, economics, investments, markets, instruments
                  (stocks, crypto, FX, commodities, funds, bonds), macro indicators (inflation, interest rates,
                  monetary policy), portfolio construction, risk management and related concepts.
                - Anything else — recipes, weather, dating, programming help, general trivia, jokes, news
                  outside markets, personal advice unrelated to money — is OUT OF SCOPE.
                - When you receive an out-of-scope question, refuse politely in ONE short sentence and
                  redirect the user to finance topics. Do not engage with the off-topic content at all.
                  Do not joke about it, do not partially answer it, do not say "but if I had to…".

                Other rules — non-negotiable:
                - Never promise guaranteed returns. No "you will earn X%".
                - Never give a direct "buy" / "sell" order. Frame ideas as scenarios with risk labels: low / medium / high.
                - Always remind the user that this is general info, not personalised advice.
                - If asked about a specific symbol you don't have context on, say so plainly and avoid making numbers up.
                - When discussing TR markets, use Turkish-finance conventions (TL, BIST, TÜFE). For US markets, USD.
                - For VİOP (futures): never recommend a long/short; always explain leverage and margin risk and that P/L is on the contract size. For bonds/bills: explain interest-rate, maturity, liquidity and price-fluctuation risk. Treat every trade in this app as simulation / portfolio tracking only, not a real order.
                - Keep replies under ~250 words. Use short paragraphs and bullet points where helpful.
                - Output plain text with **bold** marks at most — no tables, no code blocks.

                Style: professional, calm, neutral. Educate; do not hype.
                """;
        }
        return """
            Sen "Finans Portalı AI"sın — bir Türk yatırım portalında çalışan finans asistanısın.
            Kitle: hisse, kripto, döviz, emtia, fon, tahvil ve enflasyon verisi takip eden bireysel yatırımcılar.

            KESİN KAPSAM — sadece finans:
            - SADECE finans, ekonomi, yatırım, piyasalar, finansal enstrümanlar (hisse, kripto, döviz,
              emtia, fon, tahvil), makro göstergeler (enflasyon, faiz, para politikası), portföy yönetimi
              ve risk yönetimi gibi konularda cevap verirsin.
            - Bunun dışındaki her şey — yemek tarifi, hava durumu, ilişki, programlama, genel kültür,
              fıkra, piyasa dışı haberler, para ile ilgisi olmayan kişisel tavsiye — KAPSAM DIŞIDIR.
            - Kapsam dışı bir soru gelirse TEK CÜMLEYLE nazikçe reddet ve kullanıcıyı finans konularına
              yönlendir. O içeriğe girme, kısmen cevaplama, "ama olsaydı..." deme. Şaka da yapma.
              Örnek: "Bu konu kapsamım dışında — yatırım, piyasa ve finans sorularında yardımcı olabilirim."

            Diğer kurallar — esnetilemez:
            - Asla garanti getiri vaadi verme. "%X kazanırsınız" gibi ifade kullanma.
            - Doğrudan "al" / "sat" emri verme. Fikirleri risk seviyesiyle senaryolar olarak sun: düşük / orta / yüksek risk.
            - Her cevapta bunun genel bilgi olduğunu, kişisel yatırım tavsiyesi olmadığını hatırlat.
            - Veri bağlamın olmayan bir sembol soruluyorsa açıkça söyle; sayı uydurma.
            - TR piyasası için TL, BIST, TÜFE; ABD piyasası için USD konvansiyonlarını kullan.
            - VİOP (vadeli işlemler) için asla long/short önerme; her zaman kaldıraç ve teminat riskini, kâr/zararın kontrat büyüklüğü üzerinden hesaplandığını açıkla. Tahvil/bono için faiz oranı, vade, likidite ve fiyat dalgalanması riskini açıkla. Bu uygulamadaki her işlem gerçek emir değil, yalnızca simülasyon / portföy takibi amaçlıdır.
            - Yanıtları ~250 kelimenin altında tut. Kısa paragraflar ve uygun yerde madde işaretleri kullan.
            - Düz metin; en fazla **kalın** kullan — tablo veya kod bloğu kullanma.

            Üslup: profesyonel, sakin, tarafsız. Eğit; abartma.
            """;
    }

    /**
     * Appends a compact snapshot of recent market levels so the LLM can
     * reference today's data instead of generic 2023-vintage knowledge.
     * Capped to ~12 lines so we don't burn tokens on the long tail.
     */
    private String appendMarketContext(String userMessage, String lang) {
        StringBuilder ctx = new StringBuilder();
        try {
            List<com.finansportali.backend.dto.response.analysis.AnalysisInstrumentDto> top = instrumentService.getAllInstruments()
                    .stream()
                    .filter(i -> i.getValue() != null)
                    .filter(i -> i.getCategory() != null
                            && !"FUND".equals(i.getCategory()))
                    .limit(12)
                    .toList();
            if (!top.isEmpty()) {
                ctx.append("en".equals(lang) ? "\n\n[Latest market data — for context only]\n"
                                              : "\n\n[Güncel piyasa verisi — sadece bağlam]\n");
                for (var d : top) {
                    ctx.append("- ").append(d.getSymbol())
                            .append(" (").append(d.getCategory()).append("): ")
                            .append(d.getValue());
                    if (d.getCurrency() != null) ctx.append(" ").append(d.getCurrency());
                    if (d.getChangeDaily() != null) {
                        ctx.append(" | daily ")
                                .append(String.format(Locale.US, PCT_FORMAT, d.getChangeDaily().doubleValue()));
                    }
                    if (d.getChangeYearly() != null) {
                        ctx.append(" | yearly ")
                                .append(String.format(Locale.US, PCT_FORMAT, d.getChangeYearly().doubleValue()));
                    }
                    ctx.append("\n");
                }
            }
        } catch (RuntimeException e) {
            // Context is a nice-to-have — proceed with bare user message if
            // the aggregator blew up.
            log.warn("[AI] market-context build failed: {}", e.getMessage());
        }
        return userMessage + ctx;
    }

    private ChatResponseDto withFooter(ChatResponseDto r, String lang) {
        r.setDisclaimer("tr".equals(lang) ? DISCLAIMER_TR : DISCLAIMER_EN);
        r.setTimestamp(Instant.now());
        return r;
    }

    // ── Intent: budget allocation ("5000 TL'm var, ne yapayım?") ─────────

    private ChatResponseDto tryBudgetIntent(String text, String lang) {
        Matcher m = BUDGET_PATTERN.matcher(text);
        if (!m.find()) return null;
        long amount;
        try {
            amount = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        boolean tr = "tr".equals(lang);

        // Explicit locales — %,d uses the JVM default otherwise, which makes
        // the same string render as "5,000 TL" on an en_US container and
        // "5.000 TL" on a tr_TR one. CI vs. local discrepancy and, worse,
        // user-facing inconsistency across environments. TR locale lives in
        // tr_TR (5.000), EN/US locale lives in en_US (5,000).
        StringBuilder reply = new StringBuilder();
        reply.append(tr
                ? String.format(Locale.forLanguageTag("tr-TR"),
                        "Belirttiğin %,d TL'lik bütçe için aşağıdaki üç senaryoyu örnek olarak değerlendirebilirsin. Hangisini seçeceğin risk iştahına ve vade beklentine bağlıdır.%n%n",
                        amount)
                : String.format(Locale.US,
                        "For your %,d TRY budget, consider the three scenarios below. The right one depends on your risk tolerance and time horizon.%n%n",
                        amount));

        Scenario safe = new Scenario(
                tr ? "Güvenli" : "Safe",
                tr ? "Anaparayı korumak öncelikliyse — düşük volatilite, sınırlı getiri."
                   : "Capital-preservation focus — low volatility, modest yield.",
                List.of(
                        new Allocation(tr ? "Para piyasası fonu" : "Money-market fund", 50),
                        new Allocation(tr ? "Devlet tahvili / TL mevduat" : "Govt. bond / TRY deposit", 30),
                        new Allocation(tr ? "USD / Altın" : "USD / Gold", 20)
                ));
        Scenario balanced = new Scenario(
                tr ? "Dengeli" : "Balanced",
                tr ? "Orta risk — büyüme potansiyeli ile koruma karışımı."
                   : "Medium risk — mix of growth potential and protection.",
                List.of(
                        new Allocation(tr ? "BIST/ABD hisse" : "Stocks (BIST + US)", 40),
                        new Allocation(tr ? "Yatırım fonu" : "Investment fund", 25),
                        new Allocation(tr ? "Döviz / Altın" : "FX / Gold", 20),
                        new Allocation(tr ? "Tahvil / mevduat" : "Bond / deposit", 15)
                ));
        Scenario aggressive = new Scenario(
                tr ? "Riskli" : "Aggressive",
                tr ? "Yüksek volatilite — yüksek getiri potansiyeli ama kayıp riski büyük."
                   : "High volatility — high upside but also higher drawdowns.",
                List.of(
                        new Allocation(tr ? "Büyüme hissesi" : "Growth stock", 50),
                        new Allocation(tr ? "Kripto para" : "Crypto", 30),
                        new Allocation(tr ? "Emtia (altın, petrol)" : "Commodity", 10),
                        new Allocation(tr ? "Nakit tampon" : "Cash buffer", 10)
                ));

        ChatResponseDto r = new ChatResponseDto();
        r.setReply(reply.toString());
        r.setScenarios(List.of(safe, balanced, aggressive));
        return r;
    }

    // ── Intent: instrument lookup ("ASELSAN kısa vadede alınır mı?") ─────

    private ChatResponseDto tryInstrumentIntent(String text, String lang) {
        boolean tr = "tr".equals(lang);
        String upper = text.toUpperCase(Locale.ROOT);

        // Match any all-caps 3-6 letter ticker that appears in our universe.
        Pattern p = Pattern.compile("\\b([A-Z]{3,6})\\b");
        Matcher m = p.matcher(upper);
        while (m.find()) {
            String token = m.group(1);
            Optional<AnalysisInstrumentDto> hit = lookupInstrument(token);
            if (hit.isPresent()) return buildInstrumentReply(hit.get(), lang);
        }
        // Also try plain crypto names from natural text.
        if (upper.contains("BITCOIN") || upper.contains("BTC")) {
            return lookupInstrument("BTCUSD").map(d -> buildInstrumentReply(d, lang)).orElse(null);
        }
        if (upper.contains("ETHEREUM") || upper.contains("ETH")) {
            return lookupInstrument("ETHUSD").map(d -> buildInstrumentReply(d, lang)).orElse(null);
        }
        if (upper.contains("ALTIN") || upper.contains("GOLD")) {
            return lookupInstrument("XAUUSD").map(d -> buildInstrumentReply(d, lang)).orElse(null);
        }
        // Catch leading triggers like "teknik analiz yap" with no ticker —
        // ask the user to specify one.
        if (text.toLowerCase(Locale.ROOT).contains("teknik analiz") && tr) {
            ChatResponseDto r = new ChatResponseDto();
            r.setReply("Teknik analiz için lütfen bir sembol belirt — örneğin THYAO, BTCUSD veya XAUUSD.");
            return r;
        }
        return null;
    }

    private Optional<AnalysisInstrumentDto> lookupInstrument(String symbol) {
        try {
            return instrumentService.getAllInstruments().stream()
                    .filter(i -> symbol.equalsIgnoreCase(i.getSymbol()))
                    .findFirst();
        } catch (RuntimeException e) {
            log.warn("[AI] instrument lookup failed for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private ChatResponseDto buildInstrumentReply(AnalysisInstrumentDto inst, String lang) {
        boolean tr = "tr".equals(lang);
        StringBuilder b = new StringBuilder();
        b.append("**").append(inst.getName())
                .append(" (").append(inst.getSymbol()).append(")**\n\n");
        if (inst.getValue() != null) {
            b.append(tr ? "- Güncel değer: " : "- Latest value: ")
                    .append(inst.getValue());
            if (inst.getCurrency() != null) b.append(" ").append(inst.getCurrency());
            b.append("\n");
        }
        appendChange(b, tr ? "Günlük" : "Daily", inst.getChangeDaily());
        appendChange(b, tr ? "Haftalık" : "Weekly", inst.getChangeWeekly());
        appendChange(b, tr ? "Aylık" : "Monthly", inst.getChangeMonthly());
        appendChange(b, tr ? "Yıllık" : "Yearly", inst.getChangeYearly());
        b.append("\n");
        b.append(tr ? "- Risk seviyesi: " : "- Risk level: ").append(localizeRisk(inst.getRiskLevel(), tr)).append("\n");
        b.append(tr ? "- Kısa vadeli sinyal: " : "- Short-term signal: ").append(localizeSignal(inst.getShortTermSignal(), tr)).append("\n");
        b.append(tr ? "- Uzun vadeli sinyal: " : "- Long-term signal: ").append(localizeSignal(inst.getLongTermSignal(), tr)).append("\n\n");
        b.append(tr
                ? "Bu özet teknik göstergelerden türetilmiştir, kesin yön tahmini değildir."
                : "This summary is derived from technical indicators only — not a directional forecast.");

        ChatResponseDto r = new ChatResponseDto();
        r.setReply(b.toString());
        return r;
    }

    private void appendChange(StringBuilder b, String label, BigDecimal v) {
        if (v == null) return;
        b.append("- ").append(label).append(": ").append(String.format(Locale.US, PCT_FORMAT, v.doubleValue())).append("\n");
    }

    private String localizeRisk(String r, boolean tr) {
        if (r == null) return "—";
        return switch (r) {
            case "LOW" -> tr ? "Düşük" : "Low";
            case "MEDIUM" -> tr ? "Orta" : "Medium";
            case "HIGH" -> tr ? "Yüksek" : "High";
            default -> r;
        };
    }

    private String localizeSignal(String s, boolean tr) {
        if (s == null) return "—";
        return switch (s) {
            case "BUY" -> tr ? "Al" : "Buy";
            case "SELL" -> tr ? "Sat" : "Sell";
            case "HOLD" -> tr ? "Tut" : "Hold";
            case "NEUTRAL" -> tr ? "Nötr" : "Neutral";
            default -> s;
        };
    }

    // ── Intent: low-risk recommendation ──────────────────────────────────

    private ChatResponseDto tryLowRiskIntent(String text, String lang) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean isLow = lower.contains("düşük risk") || lower.contains("dusuk risk")
                || lower.contains("low risk") || lower.contains("güvenli yatırım")
                || lower.contains("guvenli yatirim");
        if (!isLow) return null;

        boolean tr = "tr".equals(lang);
        String reply = tr
                ? "Düşük riskli yatırım için klasik tercihler:\n\n"
                + "- **Devlet tahvili / hazine bonosu**: TCMB faizine yakın getiri, anapara güvencesi yüksek.\n"
                + "- **Para piyasası fonu / kısa vadeli TL fonu**: Banka mevduatına alternatif, likidite yüksek.\n"
                + "- **Vadeli TL mevduat**: Mevduat sigortası kapsamında, getirisi sabit.\n"
                + "- **Altın / döviz**: Reel değer koruması — ama döviz volatilitesi düşük risk profilini bozabilir.\n\n"
                + "Bu araçların ortak özelliği: kısa vadede dalgalanma yaratacak yüksek pozisyon almazlar."
                : "Common low-risk choices:\n\n"
                + "- **Government bonds / T-bills**: Yields close to the central-bank rate, principal protected.\n"
                + "- **Money-market funds / short-duration TRY funds**: Liquid bank-deposit alternatives.\n"
                + "- **Term TRY deposits**: Insured up to a regulatory cap, fixed yield.\n"
                + "- **Gold / FX**: Real-value preservation — FX vol can erode the low-risk label.\n\n"
                + "What they share: limited short-term drawdown exposure.";

        ChatResponseDto r = new ChatResponseDto();
        r.setReply(reply);
        return r;
    }

    // ── Intent: long-term horizon ────────────────────────────────────────

    private ChatResponseDto tryLongTermIntent(String text, String lang) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean isLong = lower.contains("uzun vade") || lower.contains("long term")
                || lower.contains("long-term") || lower.contains("emeklilik");
        if (!isLong) return null;
        boolean tr = "tr".equals(lang);
        String reply = tr
                ? "Uzun vadede (5+ yıl) yatırım yaparken sık tercih edilen yaklaşımlar:\n\n"
                + "- **Endeks fonu / ETF**: BIST 100, S&P 500 gibi geniş endeksleri uzun vadede takip etmek tarihsel olarak verimli.\n"
                + "- **Kaliteli hisse + temettü**: Kar üreten, sürdürülebilir bilançolu şirketler.\n"
                + "- **Enflasyon koruması**: TR + ABD enflasyonu uzun vadede portföyün reel getirisini etkiler — altın ve dövize de yer ayır.\n"
                + "- **Kademeli alım (DCA)**: Tek seferde almak yerine zaman içinde eşit miktarda yatırım risk dağıtır.\n\n"
                + "Kripto gibi yüksek volatil araçları portföyün küçük bir oranıyla sınırlamak makul."
                : "For long-term (5y+) horizons, common approaches:\n\n"
                + "- **Index funds / ETFs**: Broad-market exposure (BIST 100, S&P 500) has historically been efficient.\n"
                + "- **Quality dividend stocks**: Cash-generative companies with sustainable balance sheets.\n"
                + "- **Inflation hedge**: Long-term TRY/USD inflation drags down real returns — allocate to gold and FX.\n"
                + "- **Dollar-cost averaging**: Spreading purchases over time reduces entry-point risk.\n\n"
                + "Cap high-volatility assets like crypto at a small share of the portfolio.";

        ChatResponseDto r = new ChatResponseDto();
        r.setReply(reply);
        return r;
    }

    // ── Fallback: short help message ─────────────────────────────────────

    private ChatResponseDto buildHelpResponse(String lang) {
        boolean tr = "tr".equals(lang);
        String reply = tr
                ? "Sana nasıl yardımcı olabileceğime dair örnekler:\n\n"
                + "- \"5000 TL'm var, nasıl bölmeliyim?\"\n"
                + "- \"ASELSAN için kısa vadeli analiz yap.\"\n"
                + "- \"Bitcoin alınır mı?\"\n"
                + "- \"Düşük riskli yatırım önerileri nelerdir?\"\n"
                + "- \"Uzun vadede hangi araçlar mantıklı?\"\n\n"
                + "Bir sembol veya konuyla sorunu sor — risk seviyesi, kısa/uzun vade ve senaryolar üzerinden değerlendireyim."
                : "Examples of what I can help with:\n\n"
                + "- \"I have 5000 TRY, how should I split it?\"\n"
                + "- \"Short-term analysis for ASELSAN.\"\n"
                + "- \"Is Bitcoin a buy?\"\n"
                + "- \"What are low-risk options?\"\n"
                + "- \"Which instruments make sense long-term?\"\n\n"
                + "Ask about a specific symbol or theme — I'll break it down by risk, short / long horizon, and example scenarios.";

        ChatResponseDto r = new ChatResponseDto();
        r.setReply(reply);
        return r;
    }
}
