/**
 * 管理后台图表统一主题。
 *
 * 轻量化约束：
 * - 只按需注册用到的 Chart.js 模块，禁止 `chart.js/auto`。
 * - 颜色从 :root 的 CSS 变量读取，字重 / 分隔线 / 强调色都沿用设计 token。
 * - vue-chartjs 的类型化组件会自动注册 Bar / Doughnut 控制器；混合趋势图额外
 *   需要 LineController，这里一并显式注册，仍属按需注册（非全量）。
 */
import {
  Chart,
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  DoughnutController,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js'

export interface ChartTheme {
  ink900: string
  ink700: string
  ink500: string
  ink300: string
  ink100: string
  hairline: string
  brand: string
  surface: string
  fontFamily: string
}

const FALLBACK_THEME: ChartTheme = {
  ink900: '#1c1917',
  ink700: '#44403c',
  ink500: '#78716c',
  ink300: '#d8d2c4',
  ink100: '#f2efe9',
  hairline: '#e7e2d8',
  brand: '#b45309',
  surface: '#ffffff',
  fontFamily:
    "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
}

let registered = false

/** 幂等注册；重复调用不会重复注册，也不会覆盖已设置的主题默认值。 */
export function registerCharts(): void {
  if (registered) return

  Chart.register(
    ArcElement,
    BarElement,
    LineElement,
    PointElement,
    CategoryScale,
    LinearScale,
    Tooltip,
    Legend,
    // 控制器：类型化组件已自动注册，这里显式注册以保证 <Chart> 混合图可用
    BarController,
    DoughnutController,
    LineController,
  )

  const theme = readTheme()
  Chart.defaults.font.family = theme.fontFamily
  Chart.defaults.color = theme.ink500
  Chart.defaults.animation = false

  registered = true
}

/** 运行时读取 CSS 变量；SSR / 测试环境无 document 时回落到 token 默认值。 */
export function readTheme(): ChartTheme {
  if (typeof document === 'undefined') return { ...FALLBACK_THEME }

  const styles = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string): string => {
    const value = styles.getPropertyValue(name).trim()
    return value || fallback
  }

  return {
    ink900: read('--ink-900', FALLBACK_THEME.ink900),
    ink700: read('--ink-700', FALLBACK_THEME.ink700),
    ink500: read('--ink-500', FALLBACK_THEME.ink500),
    ink300: read('--ink-300', FALLBACK_THEME.ink300),
    ink100: read('--ink-100', FALLBACK_THEME.ink100),
    hairline: read('--hairline', FALLBACK_THEME.hairline),
    brand: read('--brand-600', FALLBACK_THEME.brand),
    surface: read('--surface', FALLBACK_THEME.surface),
    fontFamily: read('--font-sans', FALLBACK_THEME.fontFamily),
  }
}

const numberFormat = new Intl.NumberFormat('zh-CN')

/** 千分位整数（非法输入按 0 处理）。 */
export function formatNumber(n: number): string {
  return numberFormat.format(Number.isFinite(n) ? n : 0)
}

/** 保留 1 位小数的百分比。 */
export function formatPct(n: number): string {
  return `${(Number.isFinite(n) ? n : 0).toFixed(1)}%`
}

/** 给十六进制色值叠加透明度；非 hex（如 rgb()）原样返回。 */
export function withAlpha(color: string, alpha: number): string {
  const rgb = parseHex(color)
  if (!rgb) return color
  return `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${alpha})`
}

/** 同色系明度差调色板：深墨 → 中灰 → 浅灰，不依赖红绿。 */
export function monoTones(count: number): string[] {
  if (!Number.isFinite(count) || count <= 0) return []

  const theme = readTheme()
  const anchors = [theme.ink900, theme.ink700, theme.ink500, theme.ink300]
  if (count === 1) return [anchors[0]]

  const maxIndex = anchors.length - 1
  return Array.from({ length: count }, (_, index) => {
    const position = (index / (count - 1)) * maxIndex
    const low = Math.floor(position)
    const high = Math.min(maxIndex, Math.ceil(position))
    return mixHex(anchors[low], anchors[high], position - low) ?? anchors[low]
  })
}

function parseHex(color: string): [number, number, number] | null {
  const match = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(color.trim())
  if (!match) return null

  const hex =
    match[1].length === 3
      ? match[1]
          .split('')
          .map((char) => char + char)
          .join('')
      : match[1]

  return [
    parseInt(hex.slice(0, 2), 16),
    parseInt(hex.slice(2, 4), 16),
    parseInt(hex.slice(4, 6), 16),
  ]
}

function mixHex(from: string, to: string, ratio: number): string | null {
  const a = parseHex(from)
  const b = parseHex(to)
  if (!a || !b) return null

  const channel = (index: number): string => {
    const value = Math.round(a[index] + (b[index] - a[index]) * ratio)
    return value.toString(16).padStart(2, '0')
  }

  return `#${channel(0)}${channel(1)}${channel(2)}`
}
