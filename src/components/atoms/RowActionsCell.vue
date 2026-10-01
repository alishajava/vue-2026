<template>
  <span style="display: flex; align-items: center; justify-content: center; gap: 6px; height: 100%; width: 100%">
    <a-button size="small" @click="toggleHidden">{{ params.data.hidden ? '숨김해제' : '숨기기' }}</a-button>
    <a-button size="small" danger @click="deleteRow">삭제</a-button>
  </span>
</template>

<script setup>
/**
 * atoms/RowActionsCell
 *
 * '관리' 컬럼의 ag-Grid cellRenderer. 행 단위 숨기기 토글 / 삭제 버튼.
 * - 숨기기: hidden 플래그만 뒤집는다. '구분' 컬럼의 순번은 숨겨진 행을 건너뛰고
 *   매겨지므로(DocumentLibraryPage의 valueFormatter), 숨기면 뒤 행들의 번호가 당겨진다.
 * - 삭제: 확인창 후 그리드에서 즉시 제거한다. 이미 서버에 저장된 행(id가 있는 행)이면
 *   서버에도 바로 삭제 요청을 보낸다 - '저장' 버튼을 눌러야 반영되는 등록/변경과
 *   달리, 삭제는 되돌릴 필요가 거의 없어 즉시 확정한다.
 */
import { Modal, message } from 'ant-design-vue'
import { deleteDocument } from '../../api/documentApi'

const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

function toggleHidden() {
  const row = props.params.data
  row.hidden = !row.hidden
  if (row.status === 'saved') row.status = 'dirty'
  props.params.api.applyTransaction({ update: [row] })
  // refreshCells는 지정한 컬럼의 셀 값만 다시 그릴 뿐, DocumentLibraryPage의
  // getRowStyle(흐리게 처리)은 다시 안 불러준다 - redrawRows로 이 행 자체를 통째로
  // 다시 그려야 getRowStyle도 같이 재계산된다. 이게 없으면 숨김해제해도 흐린 상태가
  // 그대로 남아있었다(이 행의 '관리' 컬럼 버튼 라벨도 같이 갱신되므로, 전용
  // refreshCells 호출은 더 이상 따로 필요 없다).
  props.params.api.redrawRows({ rowNodes: [props.params.node] })
  // 숨김 여부는 다른 행들의 순번 표시에도 영향을 주므로 '구분' 컬럼 전체를 다시 그린다.
  props.params.api.refreshCells({ columns: ['title'], force: true })
}

function deleteRow() {
  const row = props.params.data
  Modal.confirm({
    title: '삭제하시겠습니까?',
    content: row.title ? `"${row.title}" 항목을 삭제합니다.` : '이 항목을 삭제합니다.',
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: async () => {
      props.params.api.applyTransaction({ remove: [row] })
      props.params.api.refreshCells({ columns: ['title'], force: true })
      if (row.id) {
        try {
          await deleteDocument(row.id)
        } catch (err) {
          message.error('서버에서 삭제하지 못했습니다.')
          console.error(err)
        }
      }
    },
  })
}
</script>
