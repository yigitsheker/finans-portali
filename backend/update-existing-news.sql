-- Update existing news articles with sample content
-- This is a one-time script to add content to existing articles

UPDATE news_articles 
SET content = summary || E'\n\n' || 
    'Bu haber hakkında daha fazla bilgi için lütfen kaynak siteyi ziyaret edin. ' ||
    'Haberin tam içeriği kaynak sitede mevcuttur.'
WHERE content IS NULL OR content = '';

-- Show updated count
SELECT COUNT(*) as updated_count FROM news_articles WHERE content IS NOT NULL;
