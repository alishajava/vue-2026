<script setup>
/**
 * organisms/PartLineGrid
 *
 * 부품 하나에 대한 생산라인 현황 그리드.
 * 컬럼: 라인 / 목표 / 가동율(최근 3개월, colspan 그룹 헤더)
 * 부품A, 부품B 각각을 위해 그리드를 2개(별도 인스턴스)로 나눠서 사용한다.
 */
import { computed } from 'vue'
import { AgGridVue } from 'ag-grid-vue3'
import BaseCard from '../atoms/BaseCard.vue'
import { getLineGridRows } from '../../mock/productionData'
import { buildLineGridColumns } from '../../utils/gridColumns'

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
      />
    </div>
  </BaseCard>
</template>

<style scoped>
.part-line-grid {
  width: 100%;
  /* 세로 구분선 색상 (차트 gridline과 동일한 hairline 톤) */
  --line-grid-divider: #e1e0d9;
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
</style>
