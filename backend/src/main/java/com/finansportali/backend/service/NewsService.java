package com.finansportali.backend.service;

import com.finansportali.backend.domain.NewsArticle;
import com.finansportali.backend.repo.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private static final List<String[]> FEEDS = List.of(
        // Türkiye Finans Haberleri
        new String[]{"https://www.aa.com.tr/tr/rss/default?cat=ekonomi", "genel-ekonomi", "Anadolu Ajansı"},
        new String[]{"https://www.hurriyet.com.tr/rss/ekonomi", "genel-ekonomi", "Hürriyet"},
        new String[]{"https://www.milliyet.com.tr/rss/rssNew/ekonomiRss.xml", "genel-ekonomi", "Milliyet"},
        new String[]{"https://www.sabah.com.tr/rss/ekonomi.xml", "genel-ekonomi", "Sabah"},
        
        // Borsa ve Hisse Haberleri
        new String[]{"https://www.bloomberght.com/rss", "borsa", "Bloomberg HT"},
        new String[]{"https://www.foreks.com/rss/news", "hisse", "Foreks"},
        new String[]{"https://www.investing.com/rss/news_285.rss", "hisse", "Investing.com"},
        
        // Döviz Haberleri
        new String[]{"https://www.dunya.com/rss/doviz.xml", "doviz", "Dünya Gazetesi"},
        
        // TCMB ve Merkez Bankası
        new String[]{"https://www.tcmb.gov.tr/rss/tcmb.xml", "tcmb", "TCMB"},
        
        // Kripto Para
        new String[]{"https://cointelegraph.com/rss", "kripto", "Cointelegraph"},
        new String[]{"https://feeds.finance.yahoo.com/rss/2.0/headline?s=BTC-USD&region=US&lang=en-US", "kripto", "Yahoo Finance"},
        
        // Uluslararası
        new String[]{"https://feeds.finance.yahoo.com/rss/2.0/headline?s=AAPL,TSLA,NVDA&region=US&lang=en-US", "uluslararasi", "Yahoo Finance"}
    );

    private final NewsArticleRepository repo;
    private final WebClient client;

    public NewsService(NewsArticleRepository repo) {
        this.repo = repo;
        this.client = WebClient.builder()
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; FinansPortali/1.0)")
                .build();
    }

    public List<NewsArticle> latest(String category) {
        if (category == null || category.isBlank()) {
            return repo.findTop20ByOrderByPublishedAtDesc();
        }
        return repo.findTop20ByCategoryOrderByPublishedAtDesc(category);
    }

    public List<String> getCategories() {
        return List.of(
            "genel-ekonomi", "hisse", "doviz", "tahvil", "kripto", 
            "emtia", "fonlar", "borsa", "tcmb", "uluslararasi"
        );
    }

    public NewsArticle getById(Long id) {
        return repo.findById(id).orElse(null);
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

        List<String> existingTitles = repo.findTop20ByOrderByPublishedAtDesc()
                .stream().map(NewsArticle::getTitle).toList();

        Pattern itemPat = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
        Matcher itemMatcher = itemPat.matcher(xml);

        int saved = 0;
        while (itemMatcher.find()) {
            String item    = itemMatcher.group(1);
            String title   = extractTag(item, "title");
            String summary = extractTag(item, "description");
            String pubDate = extractTag(item, "pubDate");
            String link    = extractTag(item, "link");

            if (title == null || title.isBlank()) continue;
            title = stripCdata(title).trim();
            if (existingTitles.contains(title)) continue;

            if (summary == null || summary.isBlank()) summary = title;
            summary = stripCdata(stripHtml(summary)).trim();

            if (title.length()   > 295)  title   = title.substring(0, 295);
            if (summary.length() > 1990) summary = summary.substring(0, 1990);

            repo.save(new NewsArticle(title, summary, category, parseRssDate(pubDate), link, sourceName));
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
        if (repo.count() > 0) return;
        repo.save(new NewsArticle(
                "Piyasa verileri yükleniyor...",
                "Borsa ve kripto haberleri yakında burada görünecek.",
                "markets",
                Instant.now(),
                "https://finansportali.com",
                "Finans Portali"
        ));
    }
}
