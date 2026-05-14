package com.finansportali.backend.service.client.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class NewsContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(NewsContentFetcher.class);

    /**
     * Fetch full article content from the source URL
     */
    public String fetchArticleContent(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            log.info("Fetching article content from: {}", url);
            
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // Try different content selectors based on common news sites
            String content = tryExtractContent(doc);
            
            if (content != null && !content.isBlank()) {
                log.info("Successfully extracted {} characters from article", content.length());
                return content;
            }
            
            log.warn("Could not extract content from: {}", url);
            return null;
            
        } catch (IOException e) {
            log.error("Failed to fetch article from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String tryExtractContent(Document doc) {
        // Try common article content selectors
        String[] selectors = {
            "article",
            ".article-content",
            ".article-body",
            ".post-content",
            ".entry-content",
            ".content-body",
            "div[itemprop=articleBody]",
            ".story-body",
            ".article__body",
            "div.content"
        };

        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                Element article = elements.first();
                if (article != null) {
                    // Remove unwanted elements
                    article.select("script, style, iframe, nav, aside, footer, .advertisement, .ad, .social-share").remove();
                    
                    // Extract paragraphs
                    Elements paragraphs = article.select("p");
                    if (!paragraphs.isEmpty()) {
                        StringBuilder content = new StringBuilder();
                        for (Element p : paragraphs) {
                            String text = p.text().trim();
                            if (text.length() > 50) { // Only include substantial paragraphs
                                content.append(text).append("\n\n");
                            }
                        }
                        
                        String result = content.toString().trim();
                        if (result.length() > 200) { // Ensure we have meaningful content
                            return result;
                        }
                    }
                }
            }
        }

        // Fallback: try to get all paragraphs from body
        Elements bodyParagraphs = doc.select("body p");
        if (!bodyParagraphs.isEmpty()) {
            StringBuilder content = new StringBuilder();
            int count = 0;
            for (Element p : bodyParagraphs) {
                String text = p.text().trim();
                if (text.length() > 50 && count < 20) { // Limit to 20 paragraphs
                    content.append(text).append("\n\n");
                    count++;
                }
            }
            
            String result = content.toString().trim();
            if (result.length() > 200) {
                return result;
            }
        }

        return null;
    }
}
