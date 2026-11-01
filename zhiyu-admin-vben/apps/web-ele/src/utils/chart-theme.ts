export interface ChartTheme {
  background: string;
  border: string;
  /** 固定五个槽位：主色、警示、成功、危险、中性；渐变取色时不需要再判空 */
  colors: [string, string, string, string, string];
  grid: string;
  text: string;
  tooltipBackground: string;
  tooltipBorder: string;
}

function pick(read: (name: string) => string, ...names: string[]): string {
  for (const name of names) {
    const value = read(name);
    if (value) {
      return value;
    }
  }
  return '';
}

/**
 * ECharts 渐变绘制在 canvas 上，不认 color-mix，只能拿到带 alpha 的 rgba。
 * 令牌经 Element Plus 桥接后是 rgb() 或 hex，这里统一换算；无法解析时原样返回，宁可没有渐变也不能画错色。
 */
export function withAlpha(color: string, alpha: number): string {
  const channels = parseChannels(color.trim());
  if (!channels) {
    return color;
  }
  const clamped = Math.max(0, Math.min(1, alpha));
  return `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${clamped})`;
}

function parseChannels(value: string): [number, number, number] | null {
  const hex = /^#([\da-f]{3}|[\da-f]{6})$/i.exec(value);
  if (hex?.[1]) {
    const digits =
      hex[1].length === 3 ? hex[1].split('').map((d) => d + d).join('') : hex[1];
    return [0, 2, 4].map((i) => Number.parseInt(digits.slice(i, i + 2), 16)) as [
      number,
      number,
      number,
    ];
  }
  const decimal = /([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)/.exec(value);
  if (!decimal) {
    return null;
  }
  return [
    Number(decimal[1]),
    Number(decimal[2]),
    Number(decimal[3]),
  ] as [number, number, number];
}

/**
 * Element Plus 的 -dark-2 令牌在浅色下加深、深色下提亮，
 * 比固定的 500 档更适合图表线条与图形。
 */
export function buildChartTheme(
  read: (name: string) => string,
): ChartTheme {
  return {
    background: pick(read, '--el-bg-color'),
    border: pick(read, '--el-border-color'),
    colors: [
      pick(read, '--el-color-primary-dark-2', '--el-color-primary'),
      pick(read, '--el-color-warning-dark-2', '--el-color-warning'),
      pick(read, '--el-color-success-dark-2', '--el-color-success'),
      pick(read, '--el-color-danger-dark-2', '--el-color-danger'),
      pick(read, '--el-text-color-regular'),
    ],
    grid: pick(read, '--el-border-color-lighter', '--el-border-color'),
    text: pick(read, '--el-text-color-regular'),
    tooltipBackground: pick(read, '--el-bg-color-overlay', '--el-bg-color'),
    tooltipBorder: pick(read, '--el-border-color'),
  };
}
