import { useMemo } from "react";

export function LWSparkline({ data, positive, width = 100, height = 36 }) {
  const { path, fillPath } = useMemo(() => {
    if (data.length < 2) return { path: "", fillPath: "" };

    const values = data.map((d) => d.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min || 1;

    const pad = 2; // vertical padding in px
    const usableH = height - pad * 2;
    const stepX = width / (values.length - 1);

    const points = values.map((v, i) => ({
      x: i * stepX,
      y: pad + usableH - ((v - min) / range) * usableH,
    }));

    // Smooth polyline using cubic bezier
    const lineParts = [`M ${points[0].x} ${points[0].y}`];
    for (let i = 1; i < points.length; i++) {
      const prev = points[i - 1];
      const curr = points[i];
      const cpX = (prev.x + curr.x) / 2;
      lineParts.push(`C ${cpX} ${prev.y} ${cpX} ${curr.y} ${curr.x} ${curr.y}`);
    }
    const linePath = lineParts.join(" ");

    // Fill path: close down to baseline
    const fillPath =
      linePath +
      ` L ${points[points.length - 1].x} ${height} L ${points[0].x} ${height} Z`;

    return { path: linePath, fillPath };
  }, [data, width, height]);

  if (!path) {
    // No data yet — render a flat placeholder line
    return (
      <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
        <line
          x1={0} y1={height / 2}
          x2={width} y2={height / 2}
          stroke="rgba(128,128,128,0.3)"
          strokeWidth={1}
        />
      </svg>
    );
  }

  const color = positive ? "#22c55e" : "#ef4444";
  const fillId = `sf-${positive ? "g" : "r"}-${width}`;

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      style={{ display: "block", overflow: "visible" }}
    >
      <defs>
        <linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.25} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>

      {/* Fill area */}
      <path d={fillPath} fill={`url(#${fillId})`} />

      {/* Line */}
      <path
        d={path}
        fill="none"
        stroke={color}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
