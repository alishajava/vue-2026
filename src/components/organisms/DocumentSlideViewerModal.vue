<script setup>
/**
 * organisms/DocumentSlideViewerModal
 *
 * 그리드의 '구분' 셀을 클릭하면 뜨는 팝업. 좌측에는 모든 슬라이드/페이지가 실제
 * 문서처럼 목록으로 쭉 나열되고, 우측에는 선택된 한 장이 크게 보인다. 우측 상단
 * "크게보기"를 누르면 같은 장을 훨씬 큰 별도 팝업으로 다시 띄운다.
 * pptx(.pptx)와 pdf(.pdf) 둘 다 같은 레이아웃/조작으로 동작하지만, 렌더링 방식은
 * 완전히 달라서 내부적으로 두 갈래로 나뉜다.
 *
 * [pptx]
 * pptx-preview는 list 모드에서 슬라이드마다 `.pptx-preview-slide-wrapper-{index}`
 * 클래스를 붙인 div를 wrapper에 순서대로 append한다(라이브러리 번들 소스 확인 완료).
 * 공식 API로 노출된 클릭 콜백이 없어서, 렌더링 직후 wrapper의 자식 엘리먼트들에
 * 직접 클릭 리스너를 달아 "어떤 썸네일을 클릭했는지"를 알아낸다. 슬라이드 이동은
 * previewer 인스턴스의 renderSingleSlide()/updatePagination()으로 처리한다.
 *
 * [pdf]
 * vue-pdf-embed는 `source`에서 `page`를 생략하면 전체 페이지를 렌더링하고,
 * `after-page` 슬롯으로 각 페이지 번호를 그대로 내려준다(공식 API) - 이 슬롯에
 * 페이지 번호 캡션을 달아 클릭하면 우측 큰 미리보기의 page를 바꾼다. 우측/크게보기
 * 모두 같은 currentPage ref를 공유해서, 페이지 이동 시 버퍼를 다시 넘길 필요 없이
 * page prop만 반응형으로 바뀌면 된다(문서 자체는 처음 한 번만 파싱).
 *
 * [ppt(구버전)]
 * 구버전 .ppt는 바이너리 OLE 포맷이라 브라우저에서 파싱할 방법이 없다. 클라이언트
 * 라이브러리 대신, 부모(DocumentLibraryPage)가 서버(Apache POI)에게 슬라이드를
 * PNG로 변환시켜 그 결과(data URL 배열)를 slideImages prop으로 그대로 넘겨준다.
 * 여기서는 순수 <img> 태그로만 그리면 되고, pdf 쪽의 currentPage/totalPages를
 * 그대로 재사용해서 이전/다음 상태를 공유한다.
 */
import { ref, shallowRef, nextTick, watch, onBeforeUnmount } from 'vue'
import { init as initPptxPreview } from 'pptx-preview'
import VuePdfEmbed from 'vue-pdf-embed'
import 'vue-pdf-embed/dist/styles/annotationLayer.css'
import 'vue-pdf-embed/dist/styles/textLayer.css'

const props = defineProps({
  open: {
    type: Boolean,
    required: true,
  },
  fileName: {
    type: String,
    default: '',
  },
  fileType: {
    type: String,
    default: null, // 'pptx' | 'pdf' | 'ppt'
  },
  fileBuffer: {
    type: ArrayBuffer,
    default: null,
  },
  // fileType === 'ppt'일 때만 쓰인다 - 서버가 변환해준 "data:image/png;base64,..." 배열.
  slideImages: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  // fileType === 'ppt'일 때, 서버 변환이 실패하면 부모가 채워서 내려준다.
  error: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:open'])

const LIST_SIZE = { width: 180 }
const PREVIEW_SIZE = { width: 640, height: 360 }
const ENLARGE_SIZE = { width: 1100, height: 619 }

const loadError = ref('')
const enlargeOpen = ref(false)

// --- pptx ---
const listContainer = ref(null)
const previewContainer = ref(null)
const enlargeContainer = ref(null)
let listViewer = null
let previewViewer = null
let enlargeViewer = null

// --- pdf ---
const pdfListSource = shallowRef(null)
const pdfPreviewSource = shallowRef(null)
const pdfEnlargeSource = shallowRef(null)
const currentPage = ref(1)
const totalPages = ref(1)

watch(
  () => props.open,
  async (isOpen) => {
    if (!isOpen) {
      cleanupMain()
      return
    }
    loadError.value = ''
    currentPage.value = 1
    if (props.fileType === 'ppt') {
      // slideImages는 부모가 비동기로 채워서 내려준다 - watch(slideImages)에서 처리.
      totalPages.value = props.slideImages?.length || 1
      return
    }
    if (!props.fileBuffer) return
    await nextTick()
    if (props.fileType === 'pdf') {
      initPdfMain()
    } else {
      await initPptxMain()
    }
  },
)

// ppt는 팝업이 열린 뒤 서버 변환이 끝나야 slideImages가 채워지므로, 별도로 지켜본다.
watch(
  () => props.slideImages,
  (images) => {
    if (props.fileType === 'ppt') totalPages.value = images?.length || 1
  },
)

function initPdfMain() {
  pdfListSource.value = props.fileBuffer.slice(0)
  pdfPreviewSource.value = props.fileBuffer.slice(0)
}

function onPdfLoaded(proxy) {
  totalPages.value = proxy.numPages
}

function goToPage(page) {
  currentPage.value = page
}

function pdfPrev() {
  if (currentPage.value > 1) currentPage.value -= 1
}

function pdfNext() {
  if (currentPage.value < totalPages.value) currentPage.value += 1
}

async function initPptxMain() {
  if (!listContainer.value || !previewContainer.value || !props.fileBuffer) return
  try {
    listViewer = initPptxPreview(listContainer.value, { ...LIST_SIZE, mode: 'list' })
    await listViewer.preview(props.fileBuffer.slice(0))
    attachThumbnailClicks()

    previewViewer = initPptxPreview(previewContainer.value, { ...PREVIEW_SIZE, mode: 'slide' })
    await previewViewer.preview(props.fileBuffer.slice(0))
    highlightThumbnail(0)
  } catch (err) {
    loadError.value = '파일을 읽는 데 실패했습니다. 파일이 손상되었거나 지원하지 않는 형식일 수 있습니다.'
    console.error(err)
  }
}

function attachThumbnailClicks() {
  if (!listViewer?.wrapper) return
  Array.from(listViewer.wrapper.children).forEach((el, index) => {
    el.style.cursor = 'pointer'
    el.addEventListener('click', () => goToSlide(index))
  })
}

function highlightThumbnail(index) {
  if (!listViewer?.wrapper) return
  Array.from(listViewer.wrapper.children).forEach((el, i) => {
    el.style.outline = i === index ? '2px solid #1677ff' : 'none'
    el.style.outlineOffset = '-2px'
  })
}

function goToSlide(index) {
  if (previewViewer) {
    previewViewer.renderSingleSlide(index)
    previewViewer.updatePagination()
  }
  if (enlargeViewer) {
    enlargeViewer.renderSingleSlide(index)
    enlargeViewer.updatePagination()
  }
  highlightThumbnail(index)
}

async function openEnlarge() {
  enlargeOpen.value = true
  loadError.value = ''
  await nextTick()

  if (props.fileType === 'ppt') return // <img>가 currentPage를 그대로 반영하므로 별도 처리 불필요

  if (!props.fileBuffer) return

  if (props.fileType === 'pdf') {
    pdfEnlargeSource.value = props.fileBuffer.slice(0)
    return
  }

  if (!enlargeContainer.value) return
  try {
    enlargeContainer.value.innerHTML = ''
    enlargeViewer = initPptxPreview(enlargeContainer.value, { ...ENLARGE_SIZE, mode: 'slide' })
    await enlargeViewer.preview(props.fileBuffer.slice(0))
    const idx = previewViewer?.currentIndex ?? 0
    if (idx > 0) {
      enlargeViewer.renderSingleSlide(idx)
      enlargeViewer.updatePagination()
    }
  } catch (err) {
    loadError.value = '파일을 읽는 데 실패했습니다. 파일이 손상되었거나 지원하지 않는 형식일 수 있습니다.'
    console.error(err)
  }
}

function closeEnlarge() {
  enlargeOpen.value = false
  enlargeViewer = null
  pdfEnlargeSource.value = null
}

function cleanupMain() {
  listViewer = null
  previewViewer = null
  loadError.value = ''
  pdfListSource.value = null
  pdfPreviewSource.value = null
  currentPage.value = 1
  totalPages.value = 1
}

function closeMain() {
  emit('update:open', false)
}

onBeforeUnmount(() => {
  cleanupMain()
  closeEnlarge()
})
</script>

<template>
  <a-modal
    :open="open"
    :title="fileName"
    width="960px"
    :footer="null"
    destroy-on-close
    @update:open="(val) => emit('update:open', val)"
    @cancel="closeMain"
  >
    <a-spin :spinning="loading">
      <div v-if="fileType === 'pdf'" class="pptx-slide-viewer">
        <div class="pptx-slide-viewer__list">
          <VuePdfEmbed v-if="pdfListSource" :source="pdfListSource" :width="160">
            <template #after-page="{ page }">
              <div
                class="pdf-page-caption"
                :class="{ 'pdf-page-caption--active': page === currentPage }"
                @click="goToPage(page)"
              >
                {{ page }}페이지
              </div>
            </template>
          </VuePdfEmbed>
        </div>

        <div class="pptx-slide-viewer__main">
          <div class="pptx-slide-viewer__main-header">
            <span class="pptx-slide-viewer__hint">{{ currentPage }} / {{ totalPages }}페이지</span>
            <span class="pptx-slide-viewer__main-actions">
              <a-button size="small" :disabled="currentPage <= 1" @click="pdfPrev">이전</a-button>
              <a-button size="small" :disabled="currentPage >= totalPages" @click="pdfNext">다음</a-button>
              <a-button type="primary" @click="openEnlarge">크게보기</a-button>
            </span>
          </div>
          <div class="pptx-slide-viewer__preview pptx-slide-viewer__preview--pdf">
            <VuePdfEmbed
              v-if="pdfPreviewSource"
              :source="pdfPreviewSource"
              :page="currentPage"
              :width="640"
              @loaded="onPdfLoaded"
            />
          </div>
        </div>
      </div>

      <div v-else-if="fileType === 'ppt'" class="pptx-slide-viewer">
        <div class="pptx-slide-viewer__list">
          <img
            v-for="(src, idx) in slideImages"
            :key="idx"
            :src="src"
            class="ppt-slide-thumb"
            :class="{ 'ppt-slide-thumb--active': idx + 1 === currentPage }"
            @click="goToPage(idx + 1)"
          />
        </div>

        <div class="pptx-slide-viewer__main">
          <div class="pptx-slide-viewer__main-header">
            <span class="pptx-slide-viewer__hint">{{ currentPage }} / {{ totalPages }}번 슬라이드</span>
            <span class="pptx-slide-viewer__main-actions">
              <a-button size="small" :disabled="currentPage <= 1" @click="pdfPrev">이전</a-button>
              <a-button size="small" :disabled="currentPage >= totalPages" @click="pdfNext">다음</a-button>
              <a-button type="primary" @click="openEnlarge">크게보기</a-button>
            </span>
          </div>
          <div class="pptx-slide-viewer__preview pptx-slide-viewer__preview--pdf">
            <img v-if="slideImages[currentPage - 1]" :src="slideImages[currentPage - 1]" class="ppt-slide-image" />
          </div>
        </div>
      </div>

      <div v-else class="pptx-slide-viewer">
        <div ref="listContainer" class="pptx-slide-viewer__list" />
        <div class="pptx-slide-viewer__main">
          <div class="pptx-slide-viewer__main-header">
            <span class="pptx-slide-viewer__hint">슬라이드를 클릭하면 오른쪽에 크게 표시됩니다</span>
            <a-button type="primary" @click="openEnlarge">크게보기</a-button>
          </div>
          <div ref="previewContainer" class="pptx-slide-viewer__preview" />
        </div>
      </div>

      <a-alert v-if="error || loadError" class="pptx-slide-viewer__alert" type="error" show-icon :message="error || loadError" />
    </a-spin>
  </a-modal>

  <a-modal
    v-model:open="enlargeOpen"
    :title="fileName"
    width="1160px"
    :footer="null"
    destroy-on-close
    @cancel="closeEnlarge"
  >
    <template v-if="fileType === 'pdf'">
      <div class="pptx-slide-viewer__enlarge-header">
        <span>{{ currentPage }} / {{ totalPages }}페이지</span>
        <span class="pptx-slide-viewer__main-actions">
          <a-button size="small" :disabled="currentPage <= 1" @click="pdfPrev">이전</a-button>
          <a-button size="small" :disabled="currentPage >= totalPages" @click="pdfNext">다음</a-button>
        </span>
      </div>
      <div class="pptx-slide-viewer__enlarge pptx-slide-viewer__enlarge--pdf">
        <VuePdfEmbed v-if="pdfEnlargeSource" :source="pdfEnlargeSource" :page="currentPage" :width="1100" />
      </div>
    </template>
    <template v-else-if="fileType === 'ppt'">
      <div class="pptx-slide-viewer__enlarge-header">
        <span>{{ currentPage }} / {{ totalPages }}번 슬라이드</span>
        <span class="pptx-slide-viewer__main-actions">
          <a-button size="small" :disabled="currentPage <= 1" @click="pdfPrev">이전</a-button>
          <a-button size="small" :disabled="currentPage >= totalPages" @click="pdfNext">다음</a-button>
        </span>
      </div>
      <div class="pptx-slide-viewer__enlarge pptx-slide-viewer__enlarge--pdf">
        <img v-if="slideImages[currentPage - 1]" :src="slideImages[currentPage - 1]" class="ppt-slide-image" />
      </div>
    </template>
    <div v-else ref="enlargeContainer" class="pptx-slide-viewer__enlarge" />
  </a-modal>
</template>

<style scoped>
.pptx-slide-viewer {
  display: flex;
  gap: 16px;
}
.pptx-slide-viewer__list {
  width: 180px;
  max-height: 480px;
  overflow-y: auto;
  flex-shrink: 0;
  border: 1px solid #e1e0d9;
  border-radius: 6px;
  background: #f3f2ee;
  padding: 8px;
}
.pptx-slide-viewer__main {
  flex: 1;
  min-width: 0;
}
.pptx-slide-viewer__main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.pptx-slide-viewer__main-actions {
  display: flex;
  gap: 6px;
}
.pptx-slide-viewer__hint {
  font-size: 12px;
  color: #898781;
}
.pptx-slide-viewer__preview {
  width: 640px;
  height: 360px;
  max-width: 100%;
  border: 1px solid #e1e0d9;
  border-radius: 6px;
}
.pptx-slide-viewer__preview--pdf {
  height: auto;
  max-height: 480px;
  overflow: auto;
  display: flex;
  justify-content: center;
}
.pptx-slide-viewer__enlarge {
  width: 1100px;
  height: 619px;
  max-width: 100%;
}
.pptx-slide-viewer__enlarge--pdf {
  height: auto;
  max-height: 75vh;
  overflow: auto;
  display: flex;
  justify-content: center;
}
.pptx-slide-viewer__enlarge-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #52514e;
}
.pptx-slide-viewer__alert {
  margin-top: 12px;
}
.pdf-page-caption {
  text-align: center;
  font-size: 12px;
  color: #52514e;
  padding: 4px 0 10px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.pdf-page-caption--active {
  color: #1677ff;
  font-weight: 600;
  border-bottom-color: #1677ff;
}
.ppt-slide-thumb {
  display: block;
  width: 100%;
  margin-bottom: 10px;
  border-radius: 4px;
  cursor: pointer;
  outline: 2px solid transparent;
  outline-offset: -2px;
}
.ppt-slide-thumb--active {
  outline-color: #1677ff;
}
.ppt-slide-image {
  max-width: 100%;
  max-height: 100%;
}
</style>
