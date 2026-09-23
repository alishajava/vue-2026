<script setup>
/**
 * pages/DocumentPreviewPage
 *
 * PPT(.pptx)/PDF 파일을 업로드해서 미리보기 + 크게보기(모달)로 보는 독립 페이지.
 * 대시보드와는 무관한 별도 기능이라 별도 라우트(/#/preview)로 분리했다.
 *
 * [지원 범위와 한계]
 * - PDF: pdf.js 기반(vue-pdf-embed)이라 브라우저에서 바로, 정확하게 렌더링된다.
 * - PPTX(2007+, OOXML)만 지원한다. 구버전 .ppt(97-2003, 바이너리 포맷)는 브라우저에서
 *   파싱할 방법이 없어서 지원 불가 - 업로드 시 걸러내고 안내 메시지를 보여준다.
 * - PPTX 렌더링(pptx-preview)은 순수 클라이언트 구현이라 애니메이션, 일부 폰트,
 *   SmartArt, 임베드 영상 등 복잡한 요소는 실제 파워포인트와 다르게 보이거나
 *   생략될 수 있다 - "정확한" 렌더링이 필요하면 서버 변환(LibreOffice 등)이 필요하다.
 */
import { ref, shallowRef, nextTick, onBeforeUnmount } from 'vue'
import { init as initPptxPreview } from 'pptx-preview'
import VuePdfEmbed from 'vue-pdf-embed'
import 'vue-pdf-embed/dist/styles/annotationLayer.css'
import 'vue-pdf-embed/dist/styles/textLayer.css'
import BaseCard from '../atoms/BaseCard.vue'
import { arrayBufferToBase64, base64ToArrayBuffer } from '../../utils/base64'
import { saveDocument, fetchDocument } from '../../api/documentApi'

const PREVIEW_SIZE = { width: 480, height: 270 }
const MODAL_SIZE = { width: 900, height: 506 }

const fileType = ref(null) // 'pptx' | 'pdf' | null
const fileName = ref('')
const uploadError = ref('')
const previewError = ref('')
const modalOpen = ref(false)

// pptx는 Vue 컴포넌트가 아니라 컨테이너 DOM에 직접 그리는 방식이라, ArrayBuffer를
// ref/shallowRef로 들고 있다가 컨테이너가 준비된 시점에 수동으로 그려 넣는다.
const pptxArrayBuffer = shallowRef(null)
// 원본은 절대 뷰어에 직접 넘기지 않는다 - pdf.js(vue-pdf-embed)가 ArrayBuffer를
// 내부 워커로 넘기면서 소비(detach)해버려서, 인라인 미리보기와 모달에 같은
// ArrayBuffer를 그대로 재사용하면 두 번째 뷰어는 빈 채로 뜬다. 매번 슬라이스해서
// 독립된 복사본을 만들어 넘긴다.
const pdfArrayBuffer = shallowRef(null)
const pdfPreviewSource = shallowRef(null)
const pdfModalSource = shallowRef(null)

const previewContainer = ref(null)
const modalContainer = ref(null)
let previewViewer = null
let modalViewer = null

// 서버 저장(업로드) 상태 - 미리보기는 클라이언트에서 즉시 되고, 저장은 별도로 진행된다.
const savingDoc = ref(false)
const saveError = ref('')
const savedDocumentId = ref(null)

// 저장된 문서 ID로 다시 불러오기
const loadId = ref('')
const loadingDoc = ref(false)
const loadError = ref('')

function resetPreviewState() {
  uploadError.value = ''
  previewError.value = ''
  saveError.value = ''
  savedDocumentId.value = null
  fileType.value = null
  fileName.value = ''
  pptxArrayBuffer.value = null
  pdfArrayBuffer.value = null
  pdfPreviewSource.value = null
  pdfModalSource.value = null
  previewViewer?.destroy()
  previewViewer = null
}

// pptx/pdf 공통: ArrayBuffer가 로컬 업로드에서 왔든 서버 조회에서 왔든 동일하게
// 미리보기 상태에 반영한다.
async function applyPreviewBuffer(ext, buffer) {
  if (ext === 'pdf') {
    pdfArrayBuffer.value = buffer
    pdfPreviewSource.value = buffer.slice(0)
    return
  }
  // pptx: 컨테이너가 v-if로 막 나타난 시점이라, DOM에 반영될 때까지 한 틱 기다린다.
  pptxArrayBuffer.value = buffer
  await nextTick()
  await renderPptxPreview()
}

async function handleFileSelected(file) {
  resetPreviewState()

  const ext = file.name.split('.').pop()?.toLowerCase()
  if (ext === 'ppt') {
    uploadError.value =
      '구버전 .ppt 파일은 지원하지 않습니다. PowerPoint에서 "PowerPoint 프레젠테이션(.pptx)"으로 다시 저장한 뒤 업로드해주세요.'
    return false
  }
  if (ext !== 'pptx' && ext !== 'pdf') {
    uploadError.value = '.pptx 또는 .pdf 파일만 업로드할 수 있습니다.'
    return false
  }

  fileType.value = ext
  fileName.value = file.name

  const buffer = await file.arrayBuffer()
  await applyPreviewBuffer(ext, buffer)

  // 미리보기는 이미 끝났으니, 서버 저장 실패가 미리보기 자체를 막지는 않는다.
  persistDocument(file.name, buffer.slice(0))

  return false // antd-vue upload의 자동 업로드(서버 전송)를 막는다 - 여기서 직접 처리
}

async function persistDocument(name, buffer) {
  savingDoc.value = true
  saveError.value = ''
  savedDocumentId.value = null
  try {
    const fileBase64 = await arrayBufferToBase64(buffer)
    savedDocumentId.value = await saveDocument({ fileName: name, fileBase64 })
  } catch (err) {
    saveError.value = '서버에 파일을 저장하지 못했습니다.'
    console.error(err)
  } finally {
    savingDoc.value = false
  }
}

async function loadDocumentById() {
  if (!loadId.value) return
  resetPreviewState()
  loadingDoc.value = true
  loadError.value = ''
  try {
    const doc = await fetchDocument(loadId.value)
    const ext = doc.fileName.split('.').pop()?.toLowerCase()
    fileType.value = ext
    fileName.value = doc.fileName
    const buffer = await base64ToArrayBuffer(doc.fileBase64)
    await applyPreviewBuffer(ext, buffer)
  } catch (err) {
    loadError.value = '문서를 불러오지 못했습니다.'
    console.error(err)
  } finally {
    loadingDoc.value = false
  }
}

async function renderPptxPreview() {
  if (!previewContainer.value || !pptxArrayBuffer.value) return
  try {
    previewViewer?.destroy()
    previewContainer.value.innerHTML = ''
    previewViewer = initPptxPreview(previewContainer.value, { ...PREVIEW_SIZE, mode: 'slide' })
    // JSZip이 ArrayBuffer를 소비하므로, 나중에(모달에서) 다시 쓸 수 있게 복사본을 넘긴다.
    await previewViewer.preview(pptxArrayBuffer.value.slice(0))
  } catch (err) {
    previewError.value = 'PPT 파일을 읽는 데 실패했습니다. 파일이 손상되었거나 지원하지 않는 형식일 수 있습니다.'
    console.error(err)
  }
}

async function openModal() {
  modalOpen.value = true
  if (fileType.value === 'pdf') {
    pdfModalSource.value = pdfArrayBuffer.value?.slice(0) ?? null
    return
  }
  if (fileType.value !== 'pptx') return
  await nextTick()
  if (!modalContainer.value || !pptxArrayBuffer.value) return
  try {
    modalViewer?.destroy()
    modalContainer.value.innerHTML = ''
    modalViewer = initPptxPreview(modalContainer.value, { ...MODAL_SIZE, mode: 'slide' })
    await modalViewer.preview(pptxArrayBuffer.value.slice(0))
  } catch (err) {
    previewError.value = 'PPT 파일을 읽는 데 실패했습니다. 파일이 손상되었거나 지원하지 않는 형식일 수 있습니다.'
    console.error(err)
  }
}

function closeModal() {
  modalOpen.value = false
  modalViewer?.destroy()
  modalViewer = null
}

onBeforeUnmount(() => {
  previewViewer?.destroy()
  modalViewer?.destroy()
})
</script>

<template>
  <div class="document-preview-page">
    <div class="document-preview-page__header">
      <h1 class="document-preview-page__title">문서 미리보기</h1>
      <router-link class="document-preview-page__back" to="/">← 대시보드로</router-link>
    </div>

    <BaseCard title="파일 업로드">
      <a-upload-dragger
        :show-upload-list="false"
        accept=".pptx,.pdf,.ppt"
        :before-upload="handleFileSelected"
      >
        <p class="document-preview-page__upload-hint">클릭하거나 파일을 끌어다 놓으세요</p>
        <p class="document-preview-page__upload-sub">.pptx 또는 .pdf만 지원 (구버전 .ppt는 지원하지 않음)</p>
      </a-upload-dragger>

      <a-alert v-if="uploadError" class="document-preview-page__alert" type="warning" show-icon :message="uploadError" />
      <a-alert v-if="previewError" class="document-preview-page__alert" type="error" show-icon :message="previewError" />

      <p v-if="savingDoc" class="document-preview-page__save-status">서버에 저장 중...</p>
      <a-alert
        v-else-if="savedDocumentId"
        class="document-preview-page__alert"
        type="success"
        show-icon
        :message="`서버에 저장되었습니다. (문서 ID: ${savedDocumentId})`"
      />
      <a-alert v-if="saveError" class="document-preview-page__alert" type="error" show-icon :message="saveError" />
    </BaseCard>

    <BaseCard title="저장된 문서 불러오기">
      <a-input-group compact class="document-preview-page__load-group">
        <a-input v-model:value="loadId" placeholder="문서 ID" class="document-preview-page__load-input" />
        <a-button type="primary" :loading="loadingDoc" @click="loadDocumentById">불러오기</a-button>
      </a-input-group>
      <a-alert v-if="loadError" class="document-preview-page__alert" type="error" show-icon :message="loadError" />
    </BaseCard>

    <BaseCard v-if="fileType" :title="fileName">
      <template #extra>
        <a-button type="primary" @click="openModal">크게보기</a-button>
      </template>

      <div v-if="fileType === 'pptx'" ref="previewContainer" class="document-preview-page__pptx-box" />

      <div v-else-if="fileType === 'pdf'" class="document-preview-page__pdf-box">
        <VuePdfEmbed :source="pdfPreviewSource" :page="1" :width="PREVIEW_SIZE.width" />
      </div>
    </BaseCard>

    <a-modal
      v-model:open="modalOpen"
      :title="fileName"
      width="960px"
      :footer="null"
      destroy-on-close
      @cancel="closeModal"
    >
      <div v-if="fileType === 'pptx'" ref="modalContainer" class="document-preview-page__pptx-box document-preview-page__pptx-box--modal" />
      <div v-else-if="fileType === 'pdf'" class="document-preview-page__pdf-box document-preview-page__pdf-box--modal">
        <VuePdfEmbed :source="pdfModalSource" :width="900" />
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.document-preview-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
.document-preview-page__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.document-preview-page__title {
  font-size: 20px;
  font-weight: 700;
  color: #0b0b0b;
  margin: 0;
}
.document-preview-page__back {
  font-size: 13px;
  color: #52514e;
}
.document-preview-page__upload-hint {
  font-size: 14px;
  font-weight: 600;
  color: #0b0b0b;
  margin: 8px 0 4px;
}
.document-preview-page__upload-sub {
  font-size: 12px;
  color: #898781;
  margin: 0;
}
.document-preview-page__alert {
  margin-top: 12px;
}
.document-preview-page__save-status {
  font-size: 12px;
  color: #898781;
  margin: 8px 0 0;
}
.document-preview-page__load-group {
  display: flex;
}
.document-preview-page__load-input {
  max-width: 240px;
}
.document-preview-page__pptx-box {
  width: 480px;
  height: 270px;
  max-width: 100%;
  overflow: auto;
  border: 1px solid #e1e0d9;
  border-radius: 6px;
}
.document-preview-page__pptx-box--modal {
  width: 900px;
  height: 506px;
}
.document-preview-page__pdf-box {
  width: 480px;
  max-width: 100%;
  max-height: 500px;
  overflow: auto;
  border: 1px solid #e1e0d9;
  border-radius: 6px;
}
.document-preview-page__pdf-box--modal {
  width: 100%;
  max-height: 75vh;
}
</style>
