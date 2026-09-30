<template>
  <span style="display: flex; align-items: center; width: 100%; height: 100%">
    <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600">{{
      params.valueFormatted
    }}</span>
  </span>
</template>

<script setup>
/**
 * atoms/TitleCell
 *
 * '구분' 컬럼의 ag-Grid cellRenderer. 순번+제목 텍스트만 보여준다 - 셀 자체는
 * 그리드 기본 편집(더블클릭/F2/선택 후 타이핑)이 그대로 동작한다.
 *
 * 예전엔 파일이 첨부된 행에 "미리보기" 링크가 따로 있었는데, 지금은 '파일첨부'
 * 컬럼(FileAttachCell)의 썸네일에 마우스오버하면 뜨는 👁 버튼이 그 역할을 대신하므로
 * 여기서는 뺐다.
 */
defineProps({
  params: {
    type: Object,
    required: true,
  },
})
</script>
