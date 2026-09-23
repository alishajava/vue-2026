<script setup>
/**
 * pages/DocumentLibraryPage
 *
 * PPT/PDF 자료를 행 단위로 등록/변경/삭제/숨기기 하는 그리드 화면. 대시보드/단일
 * 미리보기 페이지와는 별개의 독립 라우트(/#/documents)다.
 *
 * 컬럼: 구분(제목, 클릭 시 미리보기 팝업) / 파일첨부 / 파일명 / 등록자 / 등록일시 / 관리
 *
 * [행 상태(status) 흐름]
 * 'new'(추가만 하고 아직 저장 안 함) -> 저장 -> 'saved'(서버에 반영됨)
 * 'saved' 행을 수정(제목/등록자/파일/숨기기)하면 'dirty'로 바뀐다.
 * 상단 "저장" 버튼을 누르면 new/dirty 상태인 행만 모아서 서버에 반영한다
 * (new는 생성, dirty는 변경) - 이게 곧 등록/변경 기능이다. 삭제는 되돌릴 필요가
 * 거의 없는 액션이라 '관리' 컬럼(RowActionsCell)에서 즉시 서버에 반영한다.
 *
 * [순번과 숨기기]
 * '구분' 컬럼에 자동으로 붙는 "[N]"은 숨겨지지 않은 행만 세어 매긴 순번이다.
 * 숨긴 행은 그리드에서 사라지지 않고 흐리게 남아있되(다시 숨김해제할 수 있게)
 * 번호 매기기에서는 제외되므로, 숨기면 뒤 행들의 번호가 그만큼 당겨진다.
 *
 * [ag-Grid 데이터 소스]
 * 초기 목록 로드(listDocuments)만 rowData를 통째로 교체해서 넣고, 그 이후의
 * 추가/삭제/숨기기 같은 구조 변경은 전부 gridApi.applyTransaction으로 처리한다.
 * '저장'은 gridApi.forEachNode로 현재 그리드의 모든 행을 순회해서 처리하므로,
 * rowData를 별도로 동기화해서 들고 있을 필요가 없다.
 */
import { ref, shallowRef, onMounted } from 'vue'
import { AgGridVue } from 'ag-grid-vue3'
import { message } from 'ant-design-vue'
import BaseCard from '../atoms/BaseCard.vue'
import FileAttachCell from '../atoms/FileAttachCell.vue'
import RowActionsCell from '../atoms/RowActionsCell.vue'
import DocumentSlideViewerModal from '../organisms/DocumentSlideViewerModal.vue'
import { arrayBufferToBase64, base64ToArrayBuffer } from '../../utils/base64'
import {
  listDocuments,
  createDocument,
  updateDocument,
  fetchDocumentFile,
  convertToSlides,
  fetchDocumentSlides,
} from '../../api/documentApi'

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

function createEmptyRow() {
  return {
    id: null,
    title: '',
    fileName: '',
    fileType: null, // 'pptx' | 'pdf' | 'ppt'
    fileBuffer: null, // 아직 서버에 안 올라간 첨부 파일. 저장되면 비운다(메모리 절약).
    registrant: '',
    registeredAt: null,
    hidden: false,
    status: 'new', // 'new' | 'saved' | 'dirty'
  }
}

const rowData = shallowRef([createEmptyRow()])
const gridApi = ref(null)
const listLoadError = ref('')
const saving = ref(false)

function onGridReady(event) {
  gridApi.value = event.api
}

onMounted(async () => {
  try {
    const docs = await listDocuments()
    if (docs.length > 0) {
      rowData.value = docs.map((d) => ({
        id: d.id,
        title: d.title ?? '',
        fileName: d.fileName ?? '',
        fileType: d.fileType ?? null,
        fileBuffer: null,
        registrant: d.registrant ?? '',
        registeredAt: d.registeredAt ? new Date(d.registeredAt) : null,
        hidden: !!d.hidden,
        status: 'saved',
      }))
    }
  } catch (err) {
    listLoadError.value = '서버에서 목록을 불러오지 못했습니다. (백엔드 미연동이거나 서버 오류일 수 있습니다)'
    console.error(err)
  }
})

function addRow() {
  gridApi.value?.applyTransaction({ add: [createEmptyRow()] })
  refreshNumbering()
}

function refreshNumbering() {
  gridApi.value?.refreshCells({ columns: ['title'], force: true })
}

function onCellValueChanged(event) {
  if (['title', 'registrant'].includes(event.colDef.field) && event.data.status === 'saved') {
    event.data.status = 'dirty'
  }
}

async function saveAll() {
  if (!gridApi.value) return

  const dirtyRows = []
  gridApi.value.forEachNode((node) => {
    if (node.data.status === 'new' || node.data.status === 'dirty') {
      dirtyRows.push(node.data)
    }
  })

  if (dirtyRows.length === 0) {
    message.info('저장할 변경 사항이 없습니다.')
    return
  }

  saving.value = true
  try {
    for (const row of dirtyRows) {
      const payload = {
        title: row.title,
        registrant: row.registrant,
        hidden: row.hidden,
        fileName: row.fileName,
        fileType: row.fileType,
      }
      if (row.fileBuffer) {
        payload.fileBase64 = await arrayBufferToBase64(row.fileBuffer.slice(0))
      }

      if (row.id) {
        await updateDocument(row.id, payload)
      } else {
        const created = await createDocument(payload)
        row.id = created.id
      }
      row.status = 'saved'
      row.fileBuffer = null // 저장했으니 굳이 메모리에 원본을 들고 있지 않아도 됨(다시 볼 땐 서버에서 받음)
    }
    gridApi.value.refreshCells({ force: true })
    message.success(`${dirtyRows.length}건 저장되었습니다.`)
  } catch (err) {
    message.error('저장 중 오류가 발생했습니다.')
    console.error(err)
  } finally {
    saving.value = false
  }
}

const columnDefs = [
  {
    headerName: '구분',
    field: 'title',
    editable: true,
    flex: 1.6,
    minWidth: 180,
    cellStyle: { cursor: 'pointer', fontWeight: 600 },
    valueFormatter: (params) => {
      if (params.data.hidden) return `[숨김] ${params.value || ''}`
      let seq = 0
      for (let i = 0; i <= params.node.rowIndex; i += 1) {
        const node = params.api.getDisplayedRowAtIndex(i)
        if (node && !node.data.hidden) seq += 1
      }
      return `[${seq}] ${params.value || ''}`
    },
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
  {
    headerName: '관리',
    field: 'actions',
    cellRenderer: RowActionsCell,
    sortable: false,
    flex: 1.1,
    minWidth: 140,
  },
]

const defaultColDef = {
  sortable: false,
  resizable: true,
  flex: 1,
  minWidth: 80,
}

function getRowStyle(params) {
  if (params.data.hidden) return { opacity: 0.5, background: '#f5f5f5' }
  if (params.data.status === 'new') return { background: '#e6f4ff' }
  if (params.data.status === 'dirty') return { background: '#fffbe6' }
  return null
}

const viewerOpen = ref(false)
const viewerFileName = ref('')
const viewerFileType = ref(null)
const viewerFileBuffer = shallowRef(null)
const viewerSlideImages = shallowRef([])
const viewerLoading = ref(false)
const viewerError = ref('')

async function onCellClicked(event) {
  if (event.colDef.field !== 'title') return
  const row = event.data
  if (!row.fileName) return

  viewerFileName.value = row.fileName
  viewerFileType.value = row.fileType
  viewerFileBuffer.value = null
  viewerSlideImages.value = []
  viewerError.value = ''
  viewerOpen.value = true
  viewerLoading.value = true

  try {
    if (row.fileType === 'ppt') {
      // 구버전 .ppt는 브라우저에서 못 읽으므로 서버(Apache POI)가 PNG로 변환해준다.
      if (row.fileBuffer) {
        const fileBase64 = await arrayBufferToBase64(row.fileBuffer.slice(0))
        viewerSlideImages.value = await convertToSlides({ fileBase64, fileType: 'ppt' })
      } else if (row.id) {
        viewerSlideImages.value = await fetchDocumentSlides(row.id)
      }
    } else if (row.fileBuffer) {
      viewerFileBuffer.value = row.fileBuffer.slice(0)
    } else if (row.id) {
      const base64 = await fetchDocumentFile(row.id)
      viewerFileBuffer.value = await base64ToArrayBuffer(base64)
    }
  } catch (err) {
    if (row.fileType === 'ppt') {
      viewerError.value = '.ppt 파일을 변환하지 못했습니다. (서버 변환 기능이 아직 준비되지 않았을 수 있습니다)'
    }
    console.error(err)
  } finally {
    viewerLoading.value = false
  }
}
</script>

<template>
  <div class="document-library-page">
    <div class="document-library-page__header">
      <h1 class="document-library-page__title">PPT/PDF 자료함</h1>
      <router-link class="document-library-page__back" to="/">← 대시보드로</router-link>
    </div>

    <BaseCard title="등록 목록">
      <template #extra>
        <span class="document-library-page__toolbar">
          <a-button @click="addRow">+ 행 추가</a-button>
          <a-button type="primary" :loading="saving" @click="saveAll">저장</a-button>
        </span>
      </template>

      <a-alert v-if="listLoadError" class="document-library-page__alert" type="warning" show-icon :message="listLoadError" />

      <div class="document-library-page__grid ag-theme-alpine">
        <AgGridVue
          :column-defs="columnDefs"
          :row-data="rowData"
          :default-col-def="defaultColDef"
          :get-row-style="getRowStyle"
          dom-layout="autoHeight"
          :suppress-cell-focus="true"
          @grid-ready="onGridReady"
          @cell-clicked="onCellClicked"
          @cell-value-changed="onCellValueChanged"
        />
      </div>
    </BaseCard>

    <DocumentSlideViewerModal
      v-model:open="viewerOpen"
      :file-name="viewerFileName"
      :file-type="viewerFileType"
      :file-buffer="viewerFileBuffer"
      :slide-images="viewerSlideImages"
      :loading="viewerLoading"
      :error="viewerError"
    />
  </div>
</template>

<style scoped>
.document-library-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  max-width: 1200px;
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
.document-library-page__toolbar {
  display: flex;
  gap: 8px;
}
.document-library-page__alert {
  margin-bottom: 12px;
}
.document-library-page__grid {
  width: 100%;
  --ag-header-column-resize-handle-display: none;
  --ag-row-hover-color: #f3f2ee;
}
</style>
