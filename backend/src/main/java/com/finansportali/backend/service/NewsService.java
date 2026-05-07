package com.finansportali.backend.service;

import com.finansportali.backend.domain.NewsArticle;
import com.finansportali.backend.repo.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private static final List<String[]> FEEDS = List.of(
        // Genel Ekonomi
        new String[]{"https://www.aa.com.tr/tr/rss/default?cat=ekonomi", "genel-ekonomi", "Anadolu Ajansı"},
        new String[]{"https://www.hurriyet.com.tr/rss/ekonomi", "genel-ekonomi", "Hürriyet"},
        new String[]{"https://www.milliyet.com.tr/rss/rssNew/ekonomiRss.xml", "genel-ekonomi", "Milliyet"},
        new String[]{"https://www.sabah.com.tr/rss/ekonomi.xml", "genel-ekonomi", "Sabah"},
        new String[]{"https://www.dunya.com/rss/ekonomi.xml", "genel-ekonomi", "Dünya Gazetesi"},
        
        // Hisse Senetleri
        new String[]{"https://www.bloomberght.com/rss", "hisse", "Bloomberg HT"},
        new String[]{"https://www.foreks.com/rss/news", "hisse", "Foreks"},
        new String[]{"https://www.investing.com/rss/news_285.rss", "hisse", "Investing.com"},
        new String[]{"https://www.investing.com/rss/news_25.rss", "hisse", "Investing.com"},
        
        // Döviz
        new String[]{"https://www.dunya.com/rss/doviz.xml", "doviz", "Dünya Gazetesi"},
        new String[]{"https://www.bloomberght.com/rss/doviz", "doviz", "Bloomberg HT"},
        new String[]{"https://www.investing.com/rss/forex.rss", "doviz", "Investing.com"},
        
        // Tahvil & Bono
        new String[]{"https://www.bloomberght.com/rss/tahvil", "tahvil", "Bloomberg HT"},
        new String[]{"https://www.investing.com/rss/news_95.rss", "tahvil", "Investing.com"},
        
        // Kripto Para
        new String[]{"https://cointelegraph.com/rss", "kripto", "Cointelegraph"},
        new String[]{"https://www.coindesk.com/arc/outboundfeeds/rss/", "kripto", "CoinDesk"},
        new String[]{"https://feeds.finance.yahoo.com/rss/2.0/headline?s=BTC-USD&region=US&lang=en-US", "kripto", "Yahoo Finance"},
        new String[]{"https://www.investing.com/rss/news_301.rss", "kripto", "Investing.com"},
        
        // Emtia
        new String[]{"https://www.bloomberght.com/rss/emtia", "emtia", "Bloomberg HT"},
        new String[]{"https://www.investing.com/rss/commodities.rss", "emtia", "Investing.com"},
        new String[]{"https://www.dunya.com/rss/emtia.xml", "emtia", "Dünya Gazetesi"},
        
        // Yatırım Fonları
        new String[]{"https://www.bloomberght.com/rss/fonlar", "fonlar", "Bloomberg HT"},
        new String[]{"https://www.dunya.com/rss/fonlar.xml", "fonlar", "Dünya Gazetesi"},
        
        // Borsa Haberleri
        new String[]{"https://www.bloomberght.com/rss/borsa", "borsa", "Bloomberg HT"},
        new String[]{"https://www.dunya.com/rss/borsa.xml", "borsa", "Dünya Gazetesi"},
        new String[]{"https://www.investing.com/rss/stock_brokers.rss", "borsa", "Investing.com"},
        
        // TCMB Kararları
        new String[]{"https://www.tcmb.gov.tr/rss/tcmb.xml", "tcmb", "TCMB"},
        new String[]{"https://www.bloomberght.com/rss/merkez-bankasi", "tcmb", "Bloomberg HT"},
        
        // Uluslararası Piyasalar
        new String[]{"https://feeds.finance.yahoo.com/rss/2.0/headline?s=AAPL,TSLA,NVDA&region=US&lang=en-US", "uluslararasi", "Yahoo Finance"},
        new String[]{"https://www.investing.com/rss/news_1.rss", "uluslararasi", "Investing.com"},
        new String[]{"https://www.bloomberght.com/rss/dunya", "uluslararasi", "Bloomberg HT"}
    );

    private final NewsArticleRepository repo;
    private final WebClient client;
    private final NewsContentFetcher contentFetcher;

    public NewsService(NewsArticleRepository repo, NewsContentFetcher contentFetcher) {
        this.repo = repo;
        this.contentFetcher = contentFetcher;
        this.client = WebClient.builder()
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; FinansPortali/1.0)")
                .build();
    }

    public List<NewsArticle> latest(String category) {
        if (category == null || category.isBlank()) {
            return repo.findTop50ByOrderByPublishedAtDesc();
        }
        return repo.findTop50ByCategoryOrderByPublishedAtDesc(category);
    }

    public List<String> getCategories() {
        return List.of(
            "genel-ekonomi", "hisse", "doviz", "tahvil", "kripto", 
            "emtia", "fonlar", "borsa", "tcmb", "uluslararasi"
        );
    }

    public Map<String, Long> getCategoryCounts() {
        Map<String, Long> counts = new HashMap<>();
        
        // Get all categories
        List<String> categories = getCategories();
        
        // Count articles for each category
        for (String category : categories) {
            long count = repo.countByCategory(category);
            counts.put(category, count);
        }
        
        // Add total count
        counts.put("all", repo.count());
        
        return counts;
    }

    public NewsArticle getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public NewsArticle fetchContentForArticle(Long id) {
        NewsArticle article = repo.findById(id).orElse(null);
        if (article == null) {
            return null;
        }

        // If content already exists and is substantial, return as is
        if (article.getContent() != null && article.getContent().length() > 200) {
            return article;
        }

        // Try to fetch content from source URL
        if (article.getSourceUrl() != null && !article.getSourceUrl().isBlank()) {
            String content = contentFetcher.fetchArticleContent(article.getSourceUrl());
            if (content != null && !content.isBlank()) {
                article.setContent(content);
                repo.save(article);
                log.info("Fetched and saved content for article: {}", article.getTitle());
            }
        }

        return article;
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 6 * 60 * 60 * 1000L)
    public void fetchAndSaveNews() {
        log.info("Fetching news from RSS feeds...");
        for (String[] feed : FEEDS) {
            try {
                fetchRss(feed[0], feed[1], feed.length > 2 ? feed[2] : "Unknown");
            } catch (Exception e) {
                log.warn("RSS fetch failed for {}: {}", feed[0], e.getMessage());
            }
        }
    }

    private void fetchRss(String url, String category, String sourceName) {
        String xml = client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (xml == null || xml.isBlank()) return;

        List<String> existingTitles = repo.findTop50ByOrderByPublishedAtDesc()
                .stream().map(NewsArticle::getTitle).toList();

        Pattern itemPat = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
        Matcher itemMatcher = itemPat.matcher(xml);

        int saved = 0;
        while (itemMatcher.find()) {
            String item    = itemMatcher.group(1);
            String title   = extractTag(item, "title");
            String summary = extractTag(item, "description");
            String content = extractTag(item, "content:encoded"); // Try to get full content
            String pubDate = extractTag(item, "pubDate");
            String link    = extractTag(item, "link");

            if (title == null || title.isBlank()) continue;
            title = stripCdata(title).trim();
            if (existingTitles.contains(title)) continue;

            if (summary == null || summary.isBlank()) summary = title;
            summary = stripCdata(stripHtml(summary)).trim();

            // If no full content, use summary as content
            if (content == null || content.isBlank()) {
                content = summary;
            } else {
                content = stripCdata(stripHtml(content)).trim();
            }

            if (title.length()   > 295)  title   = title.substring(0, 295);
            if (summary.length() > 1990) summary = summary.substring(0, 1990);

            // Try to fetch full content from source URL
            if (link != null && !link.isBlank()) {
                String fetchedContent = contentFetcher.fetchArticleContent(link);
                if (fetchedContent != null && !fetchedContent.isBlank()) {
                    content = fetchedContent;
                }
            }

            if (content.length() > 10000) content = content.substring(0, 10000);

            repo.save(new NewsArticle(title, summary, content, category, parseRssDate(pubDate), link, sourceName));
            saved++;
        }
        log.info("Saved {} new {} articles", saved, category);
    }

    private String extractTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + "[^>]*>\\s*(.*?)\\s*</" + tag + ">", Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private String stripCdata(String s) {
        if (s == null) return "";
        return s.replaceAll("<!\\[CDATA\\[", "").replaceAll("\\]\\]>", "").trim();
    }

    private String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private Instant parseRssDate(String s) {
        if (s == null || s.isBlank()) return Instant.now();
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            return ZonedDateTime.parse(s.trim(), fmt).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    public void seedIfEmpty() {
        // Always ensure we have at least some articles with content
        long articlesWithContent = repo.findAll().stream()
                .filter(a -> a.getContent() != null && a.getContent().length() > 200)
                .count();
        
        if (articlesWithContent > 0) {
            log.info("Found {} articles with content, skipping seed", articlesWithContent);
            return;
        }
        
        // Delete old articles without content
        List<NewsArticle> oldArticles = repo.findAll();
        if (!oldArticles.isEmpty()) {
            log.info("Deleting {} old articles without content", oldArticles.size());
            repo.deleteAll(oldArticles);
        }
        
        Instant now = Instant.now();
        
        // Örnek Haber 1 - Borsa
        repo.save(new NewsArticle(
                "BIST 100 Endeksi Yeni Rekor Kırdı",
                "Borsa İstanbul'da BIST 100 endeksi, güçlü alıcı ilgisiyle tarihi zirvesini yeniledi. Bankacılık ve enerji hisseleri endeksi yukarı taşıdı.",
                "Borsa İstanbul'da BIST 100 endeksi, güçlü alıcı ilgisiyle tarihi zirvesini yeniledi. Bankacılık ve enerji hisseleri endeksi yukarı taşıdı.\n\n" +
                "Günün ilk yarısında yatay seyreden endeks, öğleden sonra alıcılı bir seyir izledi. Özellikle bankacılık hisselerindeki yükseliş endeksi destekledi.\n\n" +
                "Analistler, TCMB'nin faiz politikasındaki kararlı duruşun piyasalara güven verdiğini belirtiyor. Yabancı yatırımcıların da Türk varlıklarına ilgisinin arttığı gözlemleniyor.\n\n" +
                "Teknik analistler, endeksin 15.000 seviyesini test edebileceğini öngörüyor. Ancak kar realizasyonlarının da gündeme gelebileceği uyarısı yapılıyor.",
                "borsa",
                now.minusSeconds(3600),
                "https://example.com/bist-100-rekor",
                "Bloomberg HT"
        ));
        
        // Örnek Haber 2 - Döviz
        repo.save(new NewsArticle(
                "Dolar/TL Kuru Düşüş Trendinde",
                "Merkez Bankası'nın sıkı para politikası ve rezerv artışı dolar/TL kurunda düşüş eğilimini güçlendirdi.",
                "Merkez Bankası'nın sıkı para politikası ve rezerv artışı dolar/TL kurunda düşüş eğilimini güçlendirdi.\n\n" +
                "TCMB'nin brüt döviz rezervleri son haftalarda önemli artış gösterdi. Bu durum, piyasalarda güven ortamının oluşmasına katkı sağladı.\n\n" +
                "Ekonomistler, enflasyonla mücadelede kararlı duruşun sürmesi halinde kurlarda daha fazla gerileme görülebileceğini ifade ediyor.\n\n" +
                "Öte yandan, küresel piyasalardaki gelişmeler ve Fed'in faiz politikası da döviz kurları üzerinde etkili olmaya devam ediyor.",
                "doviz",
                now.minusSeconds(7200),
                "https://example.com/dolar-tl-kuru",
                "Anadolu Ajansı"
        ));
        
        // Örnek Haber 3 - Kripto
        repo.save(new NewsArticle(
                "Bitcoin 75.000 Dolar Seviyesini Test Ediyor",
                "Kripto para piyasalarında Bitcoin, yeni bir yükseliş dalgasıyla 75.000 dolar seviyesine yaklaştı.",
                "Kripto para piyasalarında Bitcoin, yeni bir yükseliş dalgasıyla 75.000 dolar seviyesine yaklaştı.\n\n" +
                "Kurumsal yatırımcıların Bitcoin ETF'lerine olan ilgisi artmaya devam ediyor. Bu durum, Bitcoin fiyatını yukarı taşıyan en önemli faktörlerden biri.\n\n" +
                "Ethereum da Bitcoin'i takip ederek 2.400 dolar seviyesini aştı. Altcoin'lerde de genel olarak yükseliş trendi gözlemleniyor.\n\n" +
                "Analistler, Bitcoin'in 80.000 dolar seviyesine ulaşabileceğini öngörüyor. Ancak volatilitenin yüksek olduğu ve risk yönetiminin önemli olduğu vurgulanıyor.",
                "kripto",
                now.minusSeconds(10800),
                "https://example.com/bitcoin-75000",
                "Cointelegraph"
        ));
        
        // Örnek Haber 4 - Hisse
        repo.save(new NewsArticle(
                "Teknoloji Hisseleri Yükselişte",
                "Yerli teknoloji şirketlerinin hisseleri, güçlü bilanço açıklamalarının ardından yükseliş trendine girdi.",
                "Yerli teknoloji şirketlerinin hisseleri, güçlü bilanço açıklamalarının ardından yükseliş trendine girdi.\n\n" +
                "Özellikle yazılım ve e-ticaret şirketlerinin kar artışları yatırımcıların dikkatini çekti. Bu sektördeki şirketlerin hisseleri çift haneli getiriler sağladı.\n\n" +
                "Sektör temsilcileri, dijital dönüşümün hızlanmasıyla birlikte büyüme potansiyelinin devam edeceğini belirtiyor.\n\n" +
                "Analistler, teknoloji sektörünün uzun vadede yatırımcılar için cazip fırsatlar sunabileceğini değerlendiriyor.",
                "hisse",
                now.minusSeconds(14400),
                "https://example.com/teknoloji-hisseleri",
                "Investing.com"
        ));
        
        // Örnek Haber 5 - TCMB
        repo.save(new NewsArticle(
                "TCMB Faiz Kararını Açıkladı",
                "Türkiye Cumhuriyet Merkez Bankası, Para Politikası Kurulu toplantısında politika faizini sabit tutma kararı aldı.",
                "Türkiye Cumhuriyet Merkez Bankası, Para Politikası Kurulu toplantısında politika faizini sabit tutma kararı aldı.\n\n" +
                "Merkez Bankası'nın açıklamasında, enflasyonla mücadelede kararlı duruşun sürdürüleceği vurgulandı. Sıkı para politikasının enflasyon beklentilerini düşürmede etkili olduğu belirtildi.\n\n" +
                "Ekonomistler, faiz kararının beklentiler doğrultusunda olduğunu ve piyasalarda olumlu karşılandığını ifade ediyor.\n\n" +
                "TCMB, enflasyon görünümünde kalıcı bir iyileşme sağlanana kadar sıkı para politikası duruşunu koruyacağını açıkladı.",
                "tcmb",
                now.minusSeconds(18000),
                "https://example.com/tcmb-faiz-karari",
                "TCMB"
        ));
        
        // Örnek Haber 6 - Tahvil & Bono
        repo.save(new NewsArticle(
                "Devlet Tahvili Faizleri Geriledi",
                "İç borçlanma piyasasında devlet tahvili faizlerinde düşüş yaşandı. 10 yıllık tahvil faizi yüzde 46 seviyesine indi.",
                "İç borçlanma piyasasında devlet tahvili faizlerinde düşüş yaşandı. 10 yıllık tahvil faizi yüzde 46 seviyesine indi.\n\n" +
                "Hazine'nin düzenli ihalelerinde güçlü talep görülüyor. Yabancı yatırımcıların Türk tahvillerine ilgisi artmaya devam ediyor.\n\n" +
                "Analistler, enflasyon beklentilerindeki iyileşmenin tahvil faizlerini aşağı çektiğini belirtiyor. Merkez Bankası'nın kararlı duruşu piyasalara güven veriyor.\n\n" +
                "Uzmanlar, tahvil faizlerinde daha fazla gerileme görülebileceğini, ancak küresel gelişmelerin de takip edilmesi gerektiğini vurguluyor.",
                "tahvil",
                now.minusSeconds(21600),
                "https://example.com/tahvil-faizleri",
                "Bloomberg HT"
        ));
        
        // Örnek Haber 7 - Emtia
        repo.save(new NewsArticle(
                "Altın Fiyatları Rekor Seviyede",
                "Küresel belirsizlikler ve merkez bankalarının alımları altın fiyatlarını rekor seviyelere taşıdı. Ons altın 2.400 doları aştı.",
                "Küresel belirsizlikler ve merkez bankalarının alımları altın fiyatlarını rekor seviyelere taşıdı. Ons altın 2.400 doları aştı.\n\n" +
                "Jeopolitik riskler ve enflasyon endişeleri yatırımcıları güvenli liman olarak görülen altına yönlendiriyor. Merkez bankaları da rezervlerini çeşitlendirmek için altın alımlarını artırıyor.\n\n" +
                "Türkiye'de gram altın fiyatları da yeni zirvelere ulaştı. Yatırımcılar altını hem değer koruma hem de getiri aracı olarak görüyor.\n\n" +
                "Analistler, altın fiyatlarının kısa vadede yüksek seyretmeye devam edebileceğini, ancak kar realizasyonlarının da gündeme gelebileceğini belirtiyor.",
                "emtia",
                now.minusSeconds(25200),
                "https://example.com/altin-fiyatlari",
                "Investing.com"
        ));
        
        // Örnek Haber 8 - Yatırım Fonları
        repo.save(new NewsArticle(
                "Hisse Senedi Fonları Yüksek Getiri Sağladı",
                "2026 yılının ilk çeyreğinde hisse senedi fonları yatırımcılarına yüzde 15'in üzerinde getiri sağladı.",
                "2026 yılının ilk çeyreğinde hisse senedi fonları yatırımcılarına yüzde 15'in üzerinde getiri sağladı.\n\n" +
                "Borsa İstanbul'daki yükseliş trendi, hisse senedi fonlarının performansını olumlu etkiledi. Özellikle teknoloji ve finans sektörüne yatırım yapan fonlar öne çıktı.\n\n" +
                "Fon yöneticileri, portföylerini aktif olarak yöneterek piyasa ortalamasının üzerinde getiri elde ettiler. Yatırımcıların fonlara ilgisi artmaya devam ediyor.\n\n" +
                "Uzmanlar, uzun vadeli yatırımcılar için hisse senedi fonlarının cazip fırsatlar sunabileceğini, ancak risk yönetiminin önemli olduğunu vurguluyor.",
                "fonlar",
                now.minusSeconds(28800),
                "https://example.com/yatirim-fonlari",
                "Dünya Gazetesi"
        ));
        
        log.info("Seeded {} sample news articles", 8);
    }

    @Transactional
    public Map<String, Object> cleanupOldNews() {
        log.info("Starting news cleanup...");
        
        Map<String, Object> result = new HashMap<>();
        int totalDeleted = 0;
        Map<String, Integer> deletedByCategory = new HashMap<>();
        
        // Her kategori için en yeni 50 haberi tut, geri kalanını sil
        List<String> categories = getCategories();
        
        for (String category : categories) {
            List<NewsArticle> allArticles = repo.findAll().stream()
                    .filter(a -> category.equals(a.getCategory()))
                    .sorted((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()))
                    .toList();
            
            if (allArticles.size() > 50) {
                List<NewsArticle> toDelete = allArticles.subList(50, allArticles.size());
                repo.deleteAll(toDelete);
                deletedByCategory.put(category, toDelete.size());
                totalDeleted += toDelete.size();
                log.info("Deleted {} old articles from category: {}", toDelete.size(), category);
            } else {
                deletedByCategory.put(category, 0);
            }
        }
        
        result.put("totalDeleted", totalDeleted);
        result.put("deletedByCategory", deletedByCategory);
        result.put("remainingTotal", repo.count());
        
        log.info("News cleanup completed. Deleted {} articles", totalDeleted);
        
        return result;
    }
}

