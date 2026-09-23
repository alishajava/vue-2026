<script setup>
/**
 * pages/DocumentLibraryPage
 *
 * PPT 자료를 행 단위로 등록/관리하는 그리드 화면. 대시보드/PPT-PDF 미리보기 페이지와는
 * 별개의 독립 라우트(/#/documents)다.
 *
 * 컬럼: 구분(제목, 클릭 시 미리보기 팝업) / 파일첨부 / 파일명 / 등록자 / 등록일시
 * - '구분'은 편집 가능한 텍스트고, 화면에는 "[행번호] 제목" 형태로 자동 표시된다
 *   (valueFormatter만 표시를 바꾸고, 편집 시에는 원래 텍스트만 보인다).
 * - '구분' 셀을 클릭하면(파일이 첨부된 행에 한해) PptxSlideViewerModal 팝업이 뜬다.
 * - '파일첨부' 셀은 FileAttachCell(cellRenderer)이 파일 선택창을 띄우고 그 행의
 *   fileName/fileBuffer/registeredAt을 직접 채운다.
 * - 이 화면은 pptx-preview로만 미리보기하므로 .pptx만 받는다 (다른 미리보기 페이지는
 *   .pdf도 지원하지만, 여기는 "ppt 슬라이드 목록" 프리젠테이션 뷰가 핵심이라 범위를 좁혔다).
 */
import { ref, shallowRef } from 'vue'
import { AgGridVue } from 'ag-grid-vue3'
import BaseCard from '../atoms/BaseCard.vue'
import FileAttachCell from '../atoms/FileAttachCell.vue'
import PptxSlideViewerModal from '../organisms/PptxSlideViewerModal.vue'

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

function createEmptyRow() {
  return {
    title: '',
    fileName: '',
    fileBuffer: null,
    registrant: '',
    registeredAt: null,
  }
}

const rowData = ref([createEmptyRow()])

function addRow() {
  rowData.value = [...rowData.value, createEmptyRow()]
}

const columnDefs = [
  {
    headerName: '구분',
    field: 'title',
    editable: true,
    flex: 1.6,
    minWidth: 180,
    cellStyle: { cursor: 'pointer', fontWeight: 600 },
    valueFormatter: (params) => `[${params.node.rowIndex + 1}] ${params.value || ''}`,
  },
  {
    headerName: '파일첨부',
    field: 'attach',
    cellRenderer: FileAttachCell,
    sortable: false,
    flex: 0.9,
    minWidth: 110,
  },
  {
    headerName: '파일명',
    field: 'fileName',
    flex: 1.6,
    minWidth: 160,
    valueFormatter: (params) => params.value || '(미첨부)',
  },
  {
    headerName: '등록자',
    field: 'registrant',
    editable: true,
    flex: 1,
    minWidth: 100,
  },
  {
    headerName: '등록일시',
    field: 'registeredAt',
    flex: 1.3,
    minWidth: 150,
    valueFormatter: (params) => (params.value ? dateTimeFormatter.format(params.value) : ''),
  },
]

const defaultColDef = {
  sortable: false,
  resizable: true,
  flex: 1,
  minWidth: 80,
}

const viewerOpen = ref(false)
const viewerFileName = ref('')
const viewerFileBuffer = shallowRef(null)

function onCellClicked(event) {
  if (event.colDef.field !== 'title') return
  if (!event.data.fileBuffer) return
  viewerFileName.value = event.data.fileName
  viewerFileBuffer.value = event.data.fileBuffer
  viewerOpen.value = true
}
</script>

<template>
  <div class="document-library-page">
    <div class="document-library-page__header">
      <h1 class="document-library-page__title">PPT 자료함</h1>
      <router-link class="document-library-page__back" to="/">← 대시보드로</router-link>
    </div>

    <BaseCard title="등록 목록">
      <template #extra>
        <a-button type="primary" @click="addRow">+ 행 추가</a-button>
      </template>

      <div class="document-library-page__grid ag-theme-alpine">
        <AgGridVue
          :column-defs="columnDefs"
          :row-data="rowData"
          :default-col-def="defaultColDef"
          dom-layout="autoHeight"
          :suppress-cell-focus="true"
          @cell-clicked="onCellClicked"
        />
      </div>
    </BaseCard>

    <PptxSlideViewerModal
      v-model:open="viewerOpen"
      :file-name="viewerFileName"
      :file-buffer="viewerFileBuffer"
    />
  </div>
</template>

<style scoped>
.document-library-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  max-width: 1100px;
  margin: 0 auto;
}
.document-library-page__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.document-library-page__title {
  font-size: 20px;
  font-weight: 700;
  color: #0b0b0b;
  margin: 0;
}
.document-library-page__back {
  font-size: 13px;
  color: #52514e;
}
.document-library-page__grid {
  width: 100%;
  --ag-header-column-resize-handle-display: none;
  --ag-row-hover-color: #f3f2ee;
}
</style>
