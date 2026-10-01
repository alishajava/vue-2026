<template>
  <span style="display: flex; align-items: center; width: 100%; height: 100%">
    <a-date-picker
      :value="value"
      picker="month"
      size="small"
      placeholder="년-월"
      allow-clear
      style="width: 100%"
      @change="onChange"
    />
  </span>
</template>

<script setup>
/**
 * atoms/ReferenceMonthCell
 *
 * '기준년월' 컬럼의 ag-Grid cellRenderer. row.referenceMonth는 "YYYY-MM" 문자열(또는
 * null)로 저장/전송하고, 화면에서만 antd 피커가 요구하는 dayjs 객체로 변환해서 보여준다.
 * 그리드의 기본 편집(더블클릭) 대신 셀 안에 피커를 항상 띄워두고 바로 선택하게 한다 -
 * RowActionsCell의 숨기기 토글과 같은 방식으로, row 객체를 직접 바꾸고
 * applyTransaction으로 그리드에 알린다.
 */
import { computed } from 'vue'
import dayjs from 'dayjs'

const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

const value = computed(() => (props.params.data.referenceMonth ? dayjs(props.params.data.referenceMonth, 'YYYY-MM') : null))

function onChange(date) {
  const row = props.params.data
  row.referenceMonth = date ? date.format('YYYY-MM') : null
  if (row.status === 'saved') row.status = 'dirty'
  props.params.api.applyTransaction({ update: [row] })
}
</script>
