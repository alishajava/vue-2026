<script setup>
/**
 * atoms/TitleCell
 *
 * '구분' 컬럼의 ag-Grid cellRenderer. 셀 텍스트 자체는 그리드 기본 편집(더블클릭/F2/
 * 선택 후 타이핑)이 그대로 동작하도록 손대지 않고, 파일이 첨부된 행에만 작은
 * "미리보기" 링크를 따로 두어 그걸 눌러야 팝업이 뜨게 한다.
 *
 * (이전엔 셀 전체를 클릭하면 팝업이 뜨게 했는데, 편집을 시작하려는 클릭까지
 * 팝업이 먼저 가로채버려서 파일 첨부 후에는 제목을 입력하기 어려운 문제가 있었다.
 * 클릭 대상을 이 버튼 하나로 좁혀서 셀 클릭/편집 동작과 완전히 분리했다.)
 */
const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

function openPreview(event) {
  // 셀 자체의 클릭/선택 동작(편집 시작 등)과 섞이지 않게 막는다.
  event.stopPropagation()
  props.params.context?.openPreview?.(props.params.data)
}
</script>

<template>
  <span style="display: flex; align-items: center; justify-content: space-between; width: 100%; height: 100%; gap: 6px">
    <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600">{{
      params.valueFormatted
    }}</span>
    <a-button
      v-if="params.data.fileName"
      size="small"
      type="link"
      style="flex-shrink: 0; padding: 0; height: auto"
      @click="openPreview"
    >
      미리보기
    </a-button>
  </span>
</template>
