import { useState } from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Sector } from 'recharts';

const COLORS = [
  '#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b',
  '#10b981', '#06b6d4', '#f97316', '#14b8a6'
];

// Custom active shape with center label
const renderActiveShape = (props) => {
  const {
    cx, cy, innerRadius, outerRadius, startAngle, endAngle,
    fill, payload, percent, value
  } = props;

  return (
    <g>
      <text x={cx} y={cy - 10} dy={8} textAnchor="middle" className="fill-text-primary font-semibold text-lg">
        {payload.name}
      </text>
      <text x={cx} y={cy + 10} dy={8} textAnchor="middle" className="fill-text-muted text-sm">
        {`${value.toLocaleString('tr-TR')} ₺`}
      </text>
      <text x={cx} y={cy + 30} dy={8} textAnchor="middle" className="fill-primary-500 font-medium text-base">
        {`${(percent * 100).toFixed(1)}%`}
      </text>
      <Sector
        cx={cx}
        cy={cy}
        innerRadius={innerRadius}
        outerRadius={outerRadius + 8}
        startAngle={startAngle}
        endAngle={endAngle}
        fill={fill}
      />
    </g>
  );
};

export const InteractivePieChart = ({ data }) => {
  const [activeIndex, setActiveIndex] = useState(undefined);

  const onPieEnter = (_, index) => {
    setActiveIndex(index);
  };

  const onPieLeave = () => {
    setActiveIndex(undefined);
  };

  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center h-[300px] text-text-muted">
        Portföy verisi bulunamadı
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <PieChart>
        <Pie
          activeShape={activeIndex !== undefined ? renderActiveShape : undefined}
          data={data}
          cx="50%"
          cy="50%"
          innerRadius={70}
          outerRadius={100}
          paddingAngle={2}
          dataKey="value"
          onMouseEnter={onPieEnter}
          onMouseLeave={onPieLeave}
          className="transition-all duration-200 cursor-pointer focus:outline-none"
        >
          {data.map((entry, index) => (
            <Cell
              key={`cell-${index}`}
              fill={entry.color || COLORS[index % COLORS.length]}
              className="transition-all duration-200"
            />
          ))}
        </Pie>
      </PieChart>
    </ResponsiveContainer>
  );
};
