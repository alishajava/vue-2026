<script setup>
/**
 * atoms/UtilizationBadge
 *
 * 하단 라인 현황 그리드의 가동율(%) 셀을 위한 pill 배지.
 * ag-Grid cellRenderer로 쓰인다 - ag-grid-vue3가 params 객체를 prop으로 그대로 넘겨준다.
 * 셀 전체를 배경색으로 칠하던 이전 방식 대신, 값에만 옅은 배경/진한 텍스트의 칩을
 * 둘러 상태를 표시한다 (Notion/Linear류 그리드에서 흔히 쓰는 방식).
 */
import { computed } from 'vue'
import { UTILIZATION_BADGE_COLORS, getUtilizationStatus } from '../../charts/chartTheme'

const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

const value = computed(() => props.params.value)
const colors = computed(() => {
  if (value.value == null) return null
  return UTILIZATION_BADGE_COLORS[getUtilizationStatus(value.value)]
})
const text = computed(() => (value.value == null ? '' : props.params.valueFormatted ?? `${value.value}%`))

// ag-Grid가 cellRenderer로 마운트하는 컴포넌트에는 <style scoped>가 적용되지
// 않는다 (컴포넌트가 메인 앱 트리 밖에서 별도로 마운트되어 scopeId가 붙지 않음).
// 그래서 배지 모양(패딩/둥근 모서리 등)까지 전부 인라인 style로 내려준다.
const badgeStyle = computed(() => {
  if (!colors.value) return null
  return {
    display: 'inline-block',
    // ag-Grid는 셀 텍스트를 세로 중앙정렬하려고 행 높이만큼 큰 line-height를
    // 셀에 준다. 배지 span이 이를 그대로 물려받으면 패딩이 위아래로 크게
    // 벌어져 알약이 아니라 럭비공처럼 보이므로, 배지 안에서는 line-height를
    // 리셋해서 텍스트 높이 기준으로만 패딩이 붙게 한다.
    lineHeight: 'normal',
    padding: '2px 10px',
    borderRadius: '999px',
    fontWeight: '600',
    backgroundColor: colors.value.background,
    color: colors.value.text,
  }
})
</script>

<template>
  <span v-if="badgeStyle" :style="badgeStyle">{{ text }}</span>
</template>
