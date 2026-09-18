<script setup>
/**
 * organisms/ProductionPerformanceChart
 *
 * 선택된 년도/공장의 1~12월 생산 실적을 보여주는 콤보 차트.
 * - 부품A/부품B 각각의 월별 실적: 막대 차트
 * - 부품A+부품B 합계 실적: 라인 차트
 * - 부품A 목표 / 부품B 목표: 각각 점선 라인 (해당 부품과 같은 색상 계열)
 * - 부품합계 목표: 별도의 점선 라인
 *
 * Chart.js는 이 컴포넌트가 아니라 charts/chartRegister.js에서 한 번만 register 하고,
 * 여기서는 그 register 모듈을 side-effect로 import해서 사용한다.
 */
import { computed } from 'vue'
import { Chart as ChartComponent } from 'vue-chartjs'
import '../../charts/chartRegister'
import { SERIES_COLORS, CHART_TEXT, TARGET_DASH, LEGEND_TARGET_DASH, hexToRgb } from '../../charts/chartTheme'
import { getChartData } from '../../mock/productionData'
import BaseCard from '../atoms/BaseCard.vue'

const props = defineProps({
  year: {
    type: Number,
    required: true,
  },
  factoryCode: {
    type: String,
    required: true,
  },
})

const numberFormatter = new Intl.NumberFormat('ko-KR')

/**
 * 실적(막대/합계 라인) 데이터포인트에 "값 + 목표 대비 달성률(%)"을 함께 표시하는
 * datalabels 설정을 만든다. targets는 같은 인덱스의 목표값 배열 - 그 값과
 * 짝지어 달성률을 계산한다 (목표가 없으면 % 라벨은 그리지 않는다).
 *
 * 값/퍼센트를 한 라벨에 두 줄로 합치면(예: 막대 중앙) 막대 폭(24px)보다 텍스트가
 * 넓어서 두 줄이 겹쳐 보인다. 그래서 datalabels의 다중 라벨 기능(`labels`)으로
 * 값은 valuePos(막대는 중앙)에, %는 항상 마크 바깥(위)에 작게 따로 그린다.
 */
function buildValueLabelConfig(
  targets,
  { valueColor, percentColor, valueAnchor, valueAlign, valueOffset = 0, percentOffset = 2 },
) {
  return {
    labels: {
      value: {
        display: true,
        color: valueColor,
        font: { size: 9, weight: '600' },
        anchor: valueAnchor,
        align: valueAlign,
        offset: valueOffset,
        formatter: (value) => `${numberFormatter.format(value)}개`,
      },
      percent: {
        display: (ctx) => targets[ctx.dataIndex] != null,
        color: percentColor,
        font: { size: 8, weight: '600' },
        anchor: 'end',
        align: 'top',
        offset: percentOffset,
        formatter: (value, ctx) => {
          const target = targets[ctx.dataIndex]
          return `(${Math.round((value / target) * 100)}%)`
        },
      },
    },
  }
}

/**
 * 레전드에서 "실선 + 가운데 점" 아이콘을 그리기 위한 캔버스를 만든다. Chart.js의
 * pointStyle은 미리 정의된 도형(line/rect/circle 등) 하나만 고를 수 있어서 "선
 * 위에 점"처럼 합성된 모양은 지원하지 않는다 - 대신 pointStyle에 HTMLCanvasElement를
 * 넘기면 그 이미지를 그대로 그려준다는 점을 이용해 직접 그려서 넘긴다.
 * (부품합계 실적처럼 실제 차트에 점 마커가 있는 선만 이 아이콘을 쓰고, 점이 없는
 * 목표 점선들은 기존처럼 pointStyle: 'line'을 그대로 쓴다.)
 */
function createLineDotIcon(lineColor, dotColor, ringColor) {
  const width = 30
  const height = 10
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  const midY = height / 2

  ctx.strokeStyle = lineColor
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(0, midY)
  ctx.lineTo(width, midY)
  ctx.stroke()

  ctx.beginPath()
  ctx.arc(width / 2, midY, 3.5, 0, Math.PI * 2)
  ctx.fillStyle = dotColor
  ctx.fill()
  ctx.lineWidth = 1.5
  ctx.strokeStyle = ringColor
  ctx.stroke()

  return canvas
}

const chartData = computed(() => {
  const data = getChartData(props.year, props.factoryCode)
  const labels = data.months.map((m) => `${m}월`)

  return {
    labels,
    datasets: [
      {
        type: 'bar',
        label: '부품A 실적',
        data: data.partA.actual,
        backgroundColor: `rgba(${hexToRgb(SERIES_COLORS.partA)}, 0.85)`,
        borderRadius: 4,
        borderSkipped: false,
        maxBarThickness: 24,
        order: 3,
        yAxisID: 'y',
        datalabels: buildValueLabelConfig(data.partA.target, {
          valueColor: CHART_TEXT.primary,
          percentColor: CHART_TEXT.secondary,
          valueAnchor: 'center',
          valueAlign: 'center',
        }),
      },
      {
        type: 'bar',
        label: '부품B 실적',
        data: data.partB.actual,
        backgroundColor: `rgba(${hexToRgb(SERIES_COLORS.partB)}, 0.85)`,
        borderRadius: 4,
        borderSkipped: false,
        maxBarThickness: 24,
        order: 3,
        yAxisID: 'y',
        datalabels: buildValueLabelConfig(data.partB.target, {
          valueColor: CHART_TEXT.primary,
          percentColor: CHART_TEXT.secondary,
          valueAnchor: 'center',
          valueAlign: 'center',
        }),
      },
      {
        type: 'line',
        label: '부품합계 실적',
        data: data.total.actual,
        borderColor: SERIES_COLORS.total,
        backgroundColor: SERIES_COLORS.total,
        borderWidth: 2,
        pointRadius: 4,
        pointHoverRadius: 5,
        pointBackgroundColor: SERIES_COLORS.total,
        pointBorderColor: '#fcfcfb',
        pointBorderWidth: 2,
        tension: 0.3,
        order: 1,
        yAxisID: 'y',
        datalabels: buildValueLabelConfig(data.total.target, {
          valueColor: CHART_TEXT.secondary,
          percentColor: CHART_TEXT.muted,
          valueAnchor: 'end',
          valueAlign: 'top',
          valueOffset: 4,
          // 값 라벨과 같은 top 방향이라 겹치지 않게 %는 그보다 더 위로 띄운다.
          percentOffset: 15,
        }),
      },
      {
        type: 'line',
        label: '부품A 목표',
        data: data.partA.target,
        borderColor: SERIES_COLORS.partA,
        borderDash: TARGET_DASH,
        borderWidth: 2,
        pointRadius: 0,
        pointHitRadius: 6,
        tension: 0.3,
        order: 2,
        yAxisID: 'y',
      },
      {
        type: 'line',
        label: '부품B 목표',
        data: data.partB.target,
        borderColor: SERIES_COLORS.partB,
        borderDash: TARGET_DASH,
        borderWidth: 2,
        pointRadius: 0,
        pointHitRadius: 6,
        tension: 0.3,
        order: 2,
        yAxisID: 'y',
      },
      {
        type: 'line',
        label: '부품합계 목표',
        data: data.total.target,
        borderColor: SERIES_COLORS.total,
        borderDash: TARGET_DASH,
        borderWidth: 2,
        pointRadius: 0,
        pointHitRadius: 6,
        tension: 0.3,
        order: 0,
        yAxisID: 'y',
      },
    ],
  }
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    mode: 'index',
    intersect: false,
  },
  plugins: {
    legend: {
      position: 'bottom',
      labels: {
        // 기본 레전드는 막대/선 구분 없이 전부 같은 점(box 또는 point) 모양으로
        // 그려서, 실적(막대)·실선(합계)·점선(목표)이 레전드만 봐서는 구분되지
        // 않았다. usePointStyle + pointStyleWidth(Chart.js 4의 "wide point
        // style" 옵션)를 켜고, generateLabels에서 데이터셋별로 pointStyle을
        // 지정해 막대는 사각형, 선은 실제 borderDash를 반영한 선으로 그린다.
        usePointStyle: true,
        pointStyleWidth: 30,
        boxHeight: 8,
        padding: 16,
        color: CHART_TEXT.secondary,
        generateLabels(chart) {
          return chart.data.datasets.map((dataset, index) => {
            const isBar = dataset.type === 'bar'
            const color = isBar ? dataset.backgroundColor : dataset.borderColor
            const hasPoints = !isBar && dataset.pointRadius > 0
            return {
              text: dataset.label,
              datasetIndex: index,
              hidden: !chart.isDatasetVisible(index),
              fillStyle: color,
              strokeStyle: color,
              lineWidth: isBar ? 0 : (dataset.borderWidth ?? 2),
              // 차트 실선의 dataset.borderDash를 그대로 쓰면 작은 레전드 아이콘
              // 안에서 너무 촘촘해 보여서, 점선 항목은 레전드 전용 간격을 쓴다.
              lineDash: isBar ? [] : dataset.borderDash ? LEGEND_TARGET_DASH : [],
              pointStyle: isBar
                ? 'rect'
                : hasPoints
                  ? createLineDotIcon(dataset.borderColor, dataset.pointBackgroundColor, dataset.pointBorderColor)
                  : 'line',
            }
          })
        },
      },
    },
    tooltip: {
      callbacks: {
        label(ctx) {
          const value = ctx.parsed.y
          return `${ctx.dataset.label}: ${numberFormatter.format(value)}개`
        },
      },
    },
    title: { display: false },
  },
  scales: {
    x: {
      grid: { display: false },
      ticks: { color: CHART_TEXT.muted },
      // 기본값(barPercentage 0.9)은 부품A/B 막대 사이에 눈에 띄는 틈을 남긴다.
      // 1에 가깝게 올려서 같은 달의 두 막대가 붙어 보이게 하고, categoryPercentage로
      // 달과 달 사이 그룹 간격은 유지한다.
      categoryPercentage: 0.85,
      barPercentage: 0.98,
    },
    y: {
      // 단일 y축: 실적/목표/합계 모두 같은 단위(생산 수량)이므로 축을 하나만 사용한다.
      beginAtZero: true,
      grid: { color: CHART_TEXT.grid, drawTicks: false },
      border: { display: false },
      ticks: {
        color: CHART_TEXT.muted,
        callback: (val) => numberFormatter.format(val),
      },
    },
  },
}))
</script>

<template>
  <BaseCard title="부품별 생산 실적 (단위: 개)">
    <div class="production-chart">
      <ChartComponent type="bar" :data="chartData" :options="chartOptions" />
    </div>
  </BaseCard>
</template>

<style scoped>
.production-chart {
  position: relative;
  height: 380px;
  width: 100%;
}
</style>
