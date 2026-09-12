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
        usePointStyle: true,
        boxWidth: 8,
        boxHeight: 8,
        padding: 16,
        color: CHART_TEXT.secondary,
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
