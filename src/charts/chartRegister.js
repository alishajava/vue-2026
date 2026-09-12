/**
 * Chart.js 공통 register 모듈.
 *
 * Chart.js는 tree-shaking을 위해 사용할 컨트롤러/엘리먼트/플러그인을 명시적으로
 * register 해야 한다. 이 프로젝트에서는 "여러 컴포넌트가 각자 필요한 걸 register"하지
 * 않고, 이 파일 한 곳에서만 register하고 다른 곳에서는 이 파일을 import(side-effect)
 * 하거나 여기서 export하는 Chart 인스턴스를 가져다 쓰도록 통일한다.
 *
 * 사용법:
 *   import { Chart } from '@/charts/chartRegister'
 *   new Chart(ctx, { ... })
 * 또는 vue-chartjs 사용 시 main.js에서 한 번만 import './charts/chartRegister' 해서
 * 부수효과(register)만 적용하고, 컴포넌트에서는 vue-chartjs의 <Chart> 컴포넌트를 사용한다.
 */
import {
  Chart,
  BarController,
  LineController,
  BarElement,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Legend,
  Tooltip,
  Title,
  Filler,
} from 'chart.js'
import ChartDataLabels from 'chartjs-plugin-datalabels'

Chart.register(
  BarController,
  LineController,
  BarElement,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Legend,
  Tooltip,
  Title,
  Filler,
  ChartDataLabels,
)

// 전역 기본 옵션 (폰트 등 공통 톤 통일)
Chart.defaults.font.family =
  "'Pretendard', system-ui, -apple-system, 'Segoe UI', sans-serif"
Chart.defaults.color = '#52514e' // text-secondary
Chart.defaults.plugins.legend.labels.usePointStyle = true
// datalabels는 전역 등록되면 모든 데이터셋에 기본으로 그려지므로, 기본값은 꺼두고
// 값 라벨이 필요한 데이터셋(막대)에서만 개별적으로 켠다.
Chart.defaults.plugins.datalabels.display = false

export { Chart }
export default Chart
