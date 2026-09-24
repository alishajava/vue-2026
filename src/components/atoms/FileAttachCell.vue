<script setup>
/**
 * atoms/FileAttachCell
 *
 * '파일첨부' 컬럼의 ag-Grid cellRenderer. 버튼 클릭 시 숨겨진 <input type="file">을
 * 대신 클릭시켜 파일 선택창을 띄우고, 선택된 파일을 그 행(row)의 데이터에 직접
 * 반영한다 (fileName/fileBuffer/registeredAt).
 *
 * ag-Grid cellRenderer는 메인 앱 트리 밖에서 별도로 마운트되어 <style scoped>가
 * 적용되지 않는다 - 여기서는 antd-vue 전역 컴포넌트(a-button)만 써서 이 문제를 피한다.
 */
import { ref } from 'vue'
import { message } from 'ant-design-vue'

const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

const fileInput = ref(null)

function triggerSelect() {
  fileInput.value?.click()
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = '' // 같은 파일을 다시 선택해도 change 이벤트가 뜨도록 초기화

  if (!file) return

  const ext = file.name.split('.').pop()?.toLowerCase()
  if (ext !== 'pptx' && ext !== 'pdf' && ext !== 'ppt') {
    message.error('.pptx, .pdf, .ppt 파일만 첨부할 수 있습니다.')
    return
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
}
</script>

<template>
  <span style="display: flex; align-items: center; height: 100%">
    <a-button size="small" @click="triggerSelect">파일선택</a-button>
    <input ref="fileInput" type="file" accept=".pptx,.pdf,.ppt" style="display: none" @change="onFileChange" />
  </span>
</template>
