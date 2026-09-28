<script setup>
/**
 * atoms/FileAttachCell
 *
 * '파일첨부' 컬럼의 ag-Grid cellRenderer. antd-vue의 <a-upload>로 파일 선택창을
 * 띄우고, 선택된 파일을 그 행(row)의 데이터에 직접 반영한다
 * (fileName/fileBuffer/registeredAt). 서버로 실제 업로드하는 게 아니라 파일을
 * 읽어서 그리드 상태에만 반영하는 용도라, before-upload에서 항상 false를 반환해
 * <a-upload>의 자체 업로드 동작(HTTP 요청)은 막는다.
 *
 * ag-Grid cellRenderer는 메인 앱 트리 밖에서 별도로 마운트되어 <style scoped>가
 * 적용되지 않는다 - 여기서는 antd-vue 전역 컴포넌트(a-upload/a-button)만 써서 이
 * 문제를 피한다.
 */
import { message } from 'ant-design-vue'

const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

async function handleBeforeUpload(file) {
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (ext !== 'pptx' && ext !== 'pdf' && ext !== 'ppt') {
    message.error('.pptx, .pdf, .ppt 파일만 첨부할 수 있습니다.')
    return false
  }

  const buffer = await file.arrayBuffer()
  const row = props.params.data
  row.fileName = file.name
  row.fileType = ext
  row.fileBuffer = buffer
  row.registeredAt = new Date()
  // 이미 저장된 행이면, 새 파일을 첨부한 순간부터 "저장 필요" 상태로 바뀐다.
  if (row.status === 'saved') row.status = 'dirty'
  // row 객체를 직접 변형했으므로, ag-Grid에게 그 행의 셀을 다시 그리라고 알려준다.
  props.params.api.applyTransaction({ update: [row] })
  // applyTransaction은 컬럼별로 "바뀐 필드"만 골라서 다시 그린다 - title 필드 자체는
  // 안 바뀌었으니 구분 컬럼(TitleCell, fileName을 보고 "미리보기" 버튼을 표시)은
  // 그냥 두면 갱신되지 않는다. 강제로 같이 다시 그려준다.
  props.params.api.refreshCells({ columns: ['title'], force: true })

  return false // 실제 업로드(HTTP 요청)는 막는다 - 파일 읽기는 이미 위에서 끝났다.
}
</script>

<template>
  <span style="display: flex; align-items: center; height: 100%">
    <a-upload :show-upload-list="false" accept=".pptx,.pdf,.ppt" :before-upload="handleBeforeUpload">
      <a-button size="small">파일선택</a-button>
    </a-upload>
  </span>
</template>
