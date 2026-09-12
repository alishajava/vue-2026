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
}

/* '가동율' 그룹 헤더(colspan)를 살짝 강조 */
.part-line-grid :deep(.util-group-header) {
  font-weight: 700;
  background-color: #f3f2ee;
}
</style>
