<script setup>
/**
 * organisms/PartLineGrid
 *
 * 부품 하나에 대한 생산라인 현황 그리드.
 * 컬럼: 라인 / 목표 / 가동율(최근 3개월, colspan 그룹 헤더)
 * 부품A, 부품B 각각을 위해 그리드를 2개(별도 인스턴스)로 나눠서 사용한다.
 *
 * 행을 클릭하면 그 라인의 최근 6개월 가동율 추이를 드로어(a-drawer)로 보여준다 -
 * 그리드는 그대로 옆에 남아있고, 그 위에 상세 정보만 슬라이드로 띄우는 형태.
 */
import { computed, ref } from 'vue'
import { AgGridVue } from 'ag-grid-vue3'
import { Chart as ChartComponent } from 'vue-chartjs'
import '../../charts/chartRegister'
import { SERIES_COLORS, CHART_TEXT } from '../../charts/chartTheme'
import { getLineGridRows, getLineUtilizationHistory } from '../../mock/productionData'
import { buildLineGridColumns } from '../../utils/gridColumns'
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
  partCode: {
    type: String,
    required: true,
  },
  partName: {
    type: String,
    required: true,
  },
})

const numberFormatter = new Intl.NumberFormat('ko-KR')

const columnDefs = computed(() => buildLineGridColumns())
const rowData = computed(() => getLineGridRows(props.year, props.factoryCode, props.partCode))

const defaultColDef = {
  sortable: false,
  resizable: true,
  // 컬럼 너비 합이 컨테이너보다 작을 때 ag-grid가 오른쪽에 남기는 빈 여백(필러 컬럼)을
  // 없애기 위해 flex를 기본값으로 준다. 각 컬럼의 flex 비율은 gridColumns.js에서 지정.
  flex: 1,
  minWidth: 80,
}

// 부품A/B 그리드가 각자 자기 부품 색(SERIES_COLORS)을 쓰도록.
const seriesColor = computed(() => (props.partCode === 'A' ? SERIES_COLORS.partA : SERIES_COLORS.partB))

const drawerOpen = ref(false)
const selectedLine = ref(null) // { lineId, lineName, target }

function onRowClicked(event) {
  selectedLine.value = {
    lineId: event.data.lineId,
    lineName: event.data.lineName,
    target: event.data.target,
  }
  drawerOpen.value = true
}

const trendHistory = computed(() => {
  if (!selectedLine.value) return []
  return getLineUtilizationHistory(props.factoryCode, props.partCode, selectedLine.value.lineId, 6)
})

const trendChartData = computed(() => ({
  labels: trendHistory.value.map((h) => h.label),
  datasets: [
    {
      type: 'line',
      label: '가동율',
      data: trendHistory.value.map((h) => h.value),
      borderColor: seriesColor.value,
      backgroundColor: seriesColor.value,
      borderWidth: 2,
      pointRadius: 4,
      pointHoverRadius: 5,
      pointBackgroundColor: seriesColor.value,
      pointBorderColor: '#fcfcfb',
      pointBorderWidth: 2,
      tension: 0.3,
    },
  ],
}))

const trendChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      callbacks: {
        label: (ctx) => `가동율: ${ctx.parsed.y}%`,
      },
    },
  },
  scales: {
    x: {
      grid: { display: false },
      ticks: { color: CHART_TEXT.muted },
    },
    y: {
      // 가동율이 대체로 70~99% 사이라 0부터 시작하면 변화가 거의 안 보인다.
      // suggestedMin이라 혹시 더 낮은 값이 들어와도 잘리지 않고 자동으로 내려간다.
      suggestedMin: 60,
      max: 100,
      grid: { color: CHART_TEXT.grid, drawTicks: false },
      border: { display: false },
      ticks: {
        color: CHART_TEXT.muted,
        callback: (val) => `${val}%`,
      },
    },
  },
}
</script>

<template>
  <BaseCard :title="`${partName} 라인 현황`">
    <div class="part-line-grid ag-theme-alpine">
      <AgGridVue
        :column-defs="columnDefs"
        :row-data="rowData"
        :default-col-def="defaultColDef"
        dom-layout="autoHeight"
        :suppress-cell-focus="true"
        @row-clicked="onRowClicked"
      />
    </div>
  </BaseCard>

  <a-drawer
    v-model:open="drawerOpen"
    :title="selectedLine ? `${partName} · ${selectedLine.lineName} 상세` : ''"
    placement="right"
    width="420"
  >
    <template v-if="selectedLine">
      <div class="line-detail__meta">
        <span class="line-detail__meta-label">이번 달 목표</span>
        <span class="line-detail__meta-value">{{ numberFormatter.format(selectedLine.target) }}개</span>
      </div>
      <p class="line-detail__section-title">최근 6개월 가동율 추이</p>
      <div class="line-detail__chart">
        <ChartComponent type="line" :data="trendChartData" :options="trendChartOptions" />
      </div>
    </template>
  </a-drawer>
</template>

<style scoped>
.part-line-grid {
  width: 100%;
  /* 세로 구분선 색상 (차트 gridline과 동일한 hairline 톤) */
  --line-grid-divider: #e1e0d9;
  /*
   * ag-grid는 resizable 컬럼 경계마다 별도의 리사이즈 핸들 막대(::after)를 그린다.
   * '라인'/'목표'처럼 그룹이 없는(span-height) 컬럼은 이 막대가 헤더 높이의 거의
   * 전체를 차지해서, 바로 아래 추가한 border-right 구분선과 나란히 겹쳐 보이며
   * "선이 두 개 겹친 것처럼" 보이는 원인이 된다. 드래그로 리사이즈하는 기능 자체는
   * 유지하고, 막대만 숨겨서 구분선 하나로만 보이게 한다.
   */
  --ag-header-column-resize-handle-display: none;
  /* 기본값은 투명이라 행에 마우스를 올려도 아무 반응이 없다 - 클릭해서 상세를
     열 수 있다는 걸 알려주려면 hover 표시가 필요하다. */
  --ag-row-hover-color: #f3f2ee;
}

.part-line-grid :deep(.ag-row) {
  cursor: pointer;
}

/* '가동율' 그룹 헤더(colspan)를 살짝 강조 */
.part-line-grid :deep(.util-group-header) {
  font-weight: 700;
  background-color: #f3f2ee;
}

/*
 * ag-theme-alpine은 기본적으로 헤더 셀에는 옅은 구분선을 주지만 본문(.ag-cell)에는
 * 세로 구분선을 넣지 않는다. 그러다 보니 헤더의 구분선이 본문까지 이어지지 않고
 * 중간에 끊긴 것처럼 보이고, '라인'-'목표' 사이는 아예 구분선이 없었다.
 * 헤더 1행(그룹)·헤더 2행(리프)·본문 모두에 동일한 세로선을 넣어 위아래가 하나로
 * 이어지도록 통일한다. 각 행의 마지막 칸은 오른쪽 테두리를 지운다.
 */
.part-line-grid :deep(.ag-header-group-cell),
.part-line-grid :deep(.ag-header-cell),
.part-line-grid :deep(.ag-cell) {
  border-right: 1px solid var(--line-grid-divider);
}

.part-line-grid :deep(.ag-header-row .ag-header-group-cell:last-child),
.part-line-grid :deep(.ag-header-row .ag-header-cell:last-child),
.part-line-grid :deep(.ag-row .ag-cell:last-child) {
  border-right: none;
}

.line-detail__meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 8px;
  background-color: #f3f2ee;
  margin-bottom: 20px;
}
.line-detail__meta-label {
  font-size: 13px;
  color: #52514e;
}
.line-detail__meta-value {
  font-size: 18px;
  font-weight: 700;
  color: #0b0b0b;
}
.line-detail__section-title {
  font-size: 13px;
  font-weight: 600;
  color: #52514e;
  margin: 0 0 8px;
}
.line-detail__chart {
  position: relative;
  height: 220px;
  width: 100%;
}
</style>
