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
import { SERIES_COLORS, CHART_TEXT, TARGET_DASH, hexToRgb } from '../../charts/chartTheme'
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

// 실적 막대마다 값을 표시하는 datalabels 설정 (부품A/B 실적 막대에서만 켠다).
// 막대 폭이 24px로 좁아서 "1,202" 같은 4자리 숫자는 막대 안(anchor: center)에
// 넣으면 옆으로 삐져나와 옆 막대/흰 배경 위에서 잘려 보인다. dataviz 가이드대로
// 안에 안 들어가는 라벨은 막대 끝(위)으로 옮긴다 - 색도 흰 글자 대신 막대 위
// 빈 공간에 맞춰 기본 잉크색을 쓴다.
const barValueLabel = {
  display: true,
  color: CHART_TEXT.secondary,
  font: { size: 10, weight: '600' },
  formatter: (value) => numberFormatter.format(value),
  anchor: 'end',
  align: 'top',
  offset: 2,
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
        datalabels: barValueLabel,
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
        datalabels: barValueLabel,
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
        pointStyleWidth: 24,
        boxHeight: 8,
        padding: 16,
        color: CHART_TEXT.secondary,
        generateLabels(chart) {
          return chart.data.datasets.map((dataset, index) => {
            const isBar = dataset.type === 'bar'
            const color = isBar ? dataset.backgroundColor : dataset.borderColor
            return {
              text: dataset.label,
              datasetIndex: index,
              hidden: !chart.isDatasetVisible(index),
              fillStyle: color,
              strokeStyle: color,
              lineWidth: isBar ? 0 : (dataset.borderWidth ?? 2),
              lineDash: isBar ? [] : (dataset.borderDash ?? []),
              pointStyle: isBar ? 'rect' : 'line',
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
