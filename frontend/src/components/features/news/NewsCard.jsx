import { clickable } from '../../../utils/clickable';

export const NewsCard = ({ article, onClick }) => {
  const hasImage = article.imageUrl && article.imageUrl.trim() !== '';

  return (
    <article
      className="bg-dark-surface border border-dark-border rounded-lg overflow-hidden hover:border-primary-500/50 transition-all duration-200 cursor-pointer group"
      {...clickable(onClick)}
    >
      {/* Image or Gradient Placeholder */}
      <div className="relative h-48 overflow-hidden">
        {hasImage ? (
          <img
            src={article.imageUrl}
            alt={article.title}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            onError={(e) => {
              // Fallback to gradient if image fails to load
              e.currentTarget.style.display = 'none';
              if (e.currentTarget.nextElementSibling) {
                e.currentTarget.nextElementSibling.style.display = 'flex';
              }
            }}
          />
        ) : null}

        {/* Gradient Placeholder (shown when no image or image fails) */}
        <div
          className={`${hasImage ? 'hidden' : 'flex'} w-full h-full bg-gradient-to-br from-slate-700 via-slate-800 to-slate-900 items-center justify-center`}
        >
          <svg
            className="w-16 h-16 text-slate-600"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M19 20H5a2 2 0 01-2-2V6a2 2 0 012-2h10a2 2 0 012 2v1m2 13a2 2 0 01-2-2V7m2 13a2 2 0 002-2V9a2 2 0 00-2-2h-2m-4-3H9M7 16h6M7 8h6v4H7V8z"
            />
          </svg>
        </div>
      </div>

      {/* Content */}
      <div className="p-4">
        {/* Source and Date */}
        <div className="flex items-center justify-between text-xs text-text-muted mb-2">
          <span className="font-medium">{article.source}</span>
          <time>{new Date(article.publishedAt).toLocaleDateString('tr-TR')}</time>
        </div>

        {/* Title */}
        <h3 className="text-base font-semibold text-text-primary mb-2 line-clamp-2 group-hover:text-primary-500 transition-colors">
          {article.title}
        </h3>

        {/* Summary */}
        <p className="text-sm text-text-muted line-clamp-3">
          {article.summary}
        </p>
      </div>
    </article>
  );
};
