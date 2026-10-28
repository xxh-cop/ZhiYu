export interface ChartTheme {
  background: string;
  border: string;
  colors: string[];
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
