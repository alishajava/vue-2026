<script setup>
/**
 * atoms/FileAttachCell
 *
 * '파일첨부' 컬럼의 ag-Grid cellRenderer. 파일이 없으면 <a-upload> 버튼만 보이고,
 * 첨부되면 셀 전체가 그 문서의 1페이지/1슬라이드를 작게 렌더링한 썸네일로 바뀐다.
 * 마우스오버하면 반투명 오버레이와 함께 미리보기/다운로드/삭제 버튼이 뜬다.
 *
 * [썸네일을 실제로 그릴 수 있을 때 vs 아이콘으로 대체할 때]
 * row.fileBuffer가 메모리에 있을 때만(방금 첨부했거나 아직 '저장'을 안 눌러 서버에
 * 올라가지 않은 상태) 실제 내용을 렌더링한다 - pdf는 vue-pdf-embed, pptx는
 * pptx-preview를 셀 크기에 맞춰 작게 띄우고, ppt(구버전)는 브라우저에서 못 읽으므로
 * convertToSlides로 서버 변환한 첫 장 이미지를 쓴다.
 * 이미 저장된 행은 '저장' 시점에 메모리 절약을 위해 fileBuffer를 비우므로(row.fileBuffer
 * === null), 그리드를 열 때마다 저장된 행 전부에 대해 서버로 파일을 통째로 다시
 * 받아오거나(pdf/pptx) POI 변환을 새로 돌리는(ppt) 건 낭비가 크다 - 이 경우는 그냥
 * 파일 형식을 나타내는 작은 아이콘으로 대체한다.
 *
 * ag-Grid cellRenderer는 메인 앱 트리 밖에서 별도로 마운트되어 <style scoped>가
 * 적용되지 않는다 - 여기서는 antd-vue 전역 컴포넌트만 쓰고, 이 파일만의 고유 클래스는
 * 인라인 스타일이나 :style로 처리한다.
 */
import { ref, shallowRef, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { init as initPptxPreview } from 'pptx-preview'
import VuePdfEmbed from 'vue-pdf-embed'
import { deleteDocument, fetchDocumentFile, convertToSlides } from '../../api/documentApi'
import { arrayBufferToBase64, base64ToArrayBuffer } from '../../utils/base64'

const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

const THUMB_WIDTH = 92
const THUMB_HEIGHT = 52

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
  // attach 컬럼도 마찬가지: field: 'attach'에 대응하는 row.attach라는 데이터가 실제로는
  // 없으므로, 이 컬럼 자신도 자동 변경 감지 대상이 아니다 - 강제로 다시 그려야 이
  // FileAttachCell 인스턴스가 새로 만들어지고(파일선택 버튼 -> 썸네일) 렌더링된다.
  props.params.api.refreshCells({ columns: ['title', 'attach'], force: true })

  return false // 실제 업로드(HTTP 요청)는 막는다 - 파일 읽기는 이미 위에서 끝났다.
}

// --- 썸네일 렌더링 ---
const thumbContainer = ref(null) // pptx-preview용 컨테이너(template ref)
const pdfThumbSource = shallowRef(null)
const pptThumbImage = ref('')
let pptxThumbViewer = null

const hasFile = computed(() => !!props.params.data.fileName)
const fileType = computed(() => props.params.data.fileType)
const hasBufferInMemory = computed(() => !!props.params.data.fileBuffer)

function cleanupThumb() {
  pptxThumbViewer = null
  if (thumbContainer.value) thumbContainer.value.innerHTML = ''
  pdfThumbSource.value = null
  pptThumbImage.value = ''
}

async function renderThumbnail() {
  cleanupThumb()
  const row = props.params.data
  if (!row.fileBuffer) return // 저장된 행(버퍼 없음)은 아이콘으로 대체

  if (row.fileType === 'pdf') {
    pdfThumbSource.value = row.fileBuffer.slice(0)
  } else if (row.fileType === 'pptx') {
    await nextTick()
    if (!thumbContainer.value) return
    try {
      pptxThumbViewer = initPptxPreview(thumbContainer.value, { width: THUMB_WIDTH, mode: 'slide' })
      await pptxThumbViewer.preview(row.fileBuffer.slice(0))
    } catch (err) {
      console.error('pptx 썸네일 렌더링 실패', err)
    }
  } else if (row.fileType === 'ppt') {
    try {
      const fileBase64 = await arrayBufferToBase64(row.fileBuffer.slice(0))
      const images = await convertToSlides({ fileBase64, fileType: 'ppt' })
      pptThumbImage.value = images?.[0] || ''
    } catch (err) {
      console.error('ppt 썸네일 변환 실패', err)
    }
  }
}

// 파일이 (재)첨부될 때마다 다시 그린다 - fileBuffer/fileType이 같이 바뀌므로 이 둘을 감시.
watch(() => [props.params.data.fileBuffer, props.params.data.fileType], renderThumbnail, { immediate: true })

onBeforeUnmount(cleanupThumb)

// --- 오버레이 액션 ---
function openPreview() {
  props.params.context?.openPreview?.(props.params.data)
}

async function download() {
  const row = props.params.data
  try {
    let buffer = row.fileBuffer
    if (!buffer && row.id) {
      const base64 = await fetchDocumentFile(row.id)
      buffer = await base64ToArrayBuffer(base64)
    }
    if (!buffer) return
    const url = URL.createObjectURL(new Blob([buffer]))
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || 'download'
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    message.error('다운로드에 실패했습니다.')
    console.error(err)
  }
}

// RowActionsCell의 삭제 버튼과 동일한 로직(확인창 -> 그리드에서 즉시 제거 -> 저장된
// 행이면 서버에도 삭제 요청) - 여기서도 빠르게 지울 수 있게 그대로 재사용한다.
function removeRow() {
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

<template>
  <span style="display: flex; align-items: center; justify-content: center; height: 100%; width: 100%">
    <a-upload v-if="!hasFile" :show-upload-list="false" accept=".pptx,.pdf,.ppt" :before-upload="handleBeforeUpload">
      <a-button size="small">파일선택</a-button>
    </a-upload>

    <div
      v-else
      class="file-thumb"
      :style="{ width: THUMB_WIDTH + 'px', height: THUMB_HEIGHT + 'px' }"
    >
      <VuePdfEmbed v-if="fileType === 'pdf' && pdfThumbSource" :source="pdfThumbSource" :page="1" :width="THUMB_WIDTH" />
      <div v-else-if="fileType === 'pptx'" ref="thumbContainer" class="file-thumb__pptx" />
      <img v-else-if="fileType === 'ppt' && pptThumbImage" :src="pptThumbImage" class="file-thumb__img" />
      <div v-else class="file-thumb__icon">{{ (fileType || '').toUpperCase() }}</div>

      <div class="file-thumb__overlay">
        <a-tooltip title="미리보기">
          <a-button size="small" shape="circle" @click.stop="openPreview">👁</a-button>
        </a-tooltip>
        <a-tooltip title="다운로드">
          <a-button size="small" shape="circle" @click.stop="download">⬇</a-button>
        </a-tooltip>
        <a-tooltip title="삭제">
          <a-button size="small" shape="circle" danger @click.stop="removeRow">✕</a-button>
        </a-tooltip>
      </div>
    </div>
  </span>
</template>

<style scoped>
.file-thumb {
  position: relative;
  overflow: hidden;
  border-radius: 4px;
  border: 1px solid #e1e0d9;
  background: #fff;
  flex-shrink: 0;
}
.file-thumb :deep(canvas),
.file-thumb__pptx,
.file-thumb__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.file-thumb__pptx {
  transform-origin: top left;
  pointer-events: none;
}
.file-thumb__icon {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #898781;
  background: #f3f2ee;
}
.file-thumb__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.55);
  opacity: 0;
  transition: opacity 0.15s;
}
.file-thumb:hover .file-thumb__overlay {
  opacity: 1;
}
</style>
