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
 *
 * [슬라이드 비율]
 * pptx-preview의 'slide' 모드는 우리가 넘긴 viewPort.width/height를 기준으로
 * 슬라이드를 가운데 정렬한다(top = (viewPort.height - 실제높이)/2). 실제 슬라이드
 * 비율(4:3 등)이 우리가 고정으로 넘긴 16:9 박스와 다르면 이 top이 음수가 되어
 * 위쪽이 잘리고, 그 잘린 만큼 wrapper에 세로 스크롤이 생긴다. 그래서 렌더링 전에
 * JSZip으로 ppt/presentation.xml의 <p:sldSz>만 직접 읽어 실제 비율을 구하고,
 * 그 비율에 맞춰 viewPort.height를 계산해서 넘긴다(파싱 실패 시 16:9로 대체).
 *
 * [이전/다음 탐색]
 * pptx-preview가 'slide' 모드에서 자체적으로 그려 넣는 원형 이전/다음 버튼은
 * 라이브러리 내부 상태만 바꾸고 우리 쪽 상태(goToSlide 등)를 거치지 않아서,
 * 그 버튼으로 이동하면 좌측 목록/점 인디케이터가 따라오지 않았다. 렌더링 직후
 * 그 버튼(.pptx-preview-wrapper-next)과 페이지 표시(.pptx-preview-wrapper-pagination)를
 * DOM에서 지우고, 같은 위치/모양의 SlideNavArrows를 우리가 대신 그려서 goToSlide로
 * 연결한다. 좌측 목록/점 인디케이터/이 버튼/↑↓ 방향키가 전부 goToDot·goToSlide 하나로
 * 통일되어 항상 같이 갱신된다.
 */
import { ref, shallowRef, computed, nextTick, watch, onBeforeUnmount } from 'vue'
import { init as initPptxPreview } from 'pptx-preview'
import JSZip from 'jszip'
import VuePdfEmbed from 'vue-pdf-embed'
import 'vue-pdf-embed/dist/styles/annotationLayer.css'
import 'vue-pdf-embed/dist/styles/textLayer.css'
import SlideDotsIndicator from '../atoms/SlideDotsIndicator.vue'
import SlideNavArrows from '../atoms/SlideNavArrows.vue'

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

const LIST_WIDTH = 200
// 좌측 패널(.pptx-slide-viewer__list)은 좌우 8px씩 패딩(16px)이 있고, 세로 스크롤바가
// 뜨면 그만큼도 더 먹는다 - 실제 콘텐츠 폭은 이 여유를 뺀 값으로 넘겨야 가로 스크롤이
// 안 생긴다.
const LIST_CONTENT_WIDTH = LIST_WIDTH - 36
const PREVIEW_WIDTH = 760
const ENLARGE_WIDTH = 1180

const loadError = ref('')
const enlargeOpen = ref(false)

// --- pptx ---
const listContainer = ref(null)
const previewContainer = ref(null)
const enlargeContainer = ref(null)
const listFailed = ref(false)
const slideAspectRatio = ref(16 / 9) // presentation.xml 파싱 전 기본값
const pptxCurrentIndex = ref(0)
const pptxSlideCount = ref(0)
let listViewer = null
let previewViewer = null
let enlargeViewer = null

const previewSize = computed(() => ({
  width: PREVIEW_WIDTH,
  height: Math.round(PREVIEW_WIDTH / slideAspectRatio.value),
}))
const enlargeSize = computed(() => ({
  width: ENLARGE_WIDTH,
  height: Math.round(ENLARGE_WIDTH / slideAspectRatio.value),
}))

// --- pdf ---
const pdfListSource = shallowRef(null)
const pdfPreviewSource = shallowRef(null)
const pdfEnlargeSource = shallowRef(null)
const currentPage = ref(1)
const totalPages = ref(1)

// 좌측 목록 클릭/점 인디케이터 클릭을 타입에 상관없이 같은 방식으로 다루기 위한 공용 값.
const dotCount = computed(() => (props.fileType === 'pptx' ? pptxSlideCount.value : totalPages.value))
const dotActiveIndex = computed(() => (props.fileType === 'pptx' ? pptxCurrentIndex.value : currentPage.value - 1))
function goToDot(index) {
  if (props.fileType === 'pptx') goToSlide(index)
  else goToPage(index + 1)
}

// 좌측 슬라이드/페이지 목록에서 ↑/↓로도 이동할 수 있게. dotCount/dotActiveIndex/
// goToDot을 그대로 재사용해서 pptx/pdf/ppt 세 타입 모두 동일하게 동작한다.
// 이 모달이 열려있는 동안에만(팝업이 화면을 가리는 동안에만) 붙였다 뗀다.
function handleKeydown(event) {
  if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return
  const nextIndex = dotActiveIndex.value + (event.key === 'ArrowUp' ? -1 : 1)
  if (nextIndex < 0 || nextIndex >= dotCount.value) return
  event.preventDefault()
  goToDot(nextIndex)
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) window.addEventListener('keydown', handleKeydown)
    else window.removeEventListener('keydown', handleKeydown)
  },
)

async function getSlideAspectRatio(buffer) {
  try {
    const zip = await JSZip.loadAsync(buffer.slice(0))
    const presFile = zip.file('ppt/presentation.xml')
    if (!presFile) return null
    const xml = await presFile.async('string')
    const tag = xml.match(/<p:sldSz\b[^>]*\/?>/)?.[0]
    if (!tag) return null
    const cx = Number(tag.match(/cx="(\d+)"/)?.[1])
    const cy = Number(tag.match(/cy="(\d+)"/)?.[1])
    if (!cx || !cy) return null
    return cx / cy
  } catch (err) {
    console.error('슬라이드 크기 파싱 실패 - 기본 16:9 비율을 사용합니다', err)
    return null
  }
}

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

// pptx-preview가 'slide' 모드에서 자체적으로 그려 넣는 이전/다음 버튼은 라이브러리
// 내부 상태(currentIndex)만 바꾸고 우리 쪽 goToSlide()를 거치지 않는다 - 그래서 그
// 버튼으로 이동하면 좌측 목록 하이라이트/점 인디케이터가 전혀 안 따라왔다. 렌더링 직후
// 그 버튼들(+자체 페이지 표시)을 DOM에서 지우고, 우리가 만든 버튼으로 대체한다.
function stripBuiltInNav(container) {
  container?.querySelectorAll('.pptx-preview-wrapper-next, .pptx-preview-wrapper-pagination').forEach((el) => el.remove())
}

function pptxPrev() {
  if (pptxCurrentIndex.value > 0) goToSlide(pptxCurrentIndex.value - 1)
}

function pptxNext() {
  if (pptxCurrentIndex.value < pptxSlideCount.value - 1) goToSlide(pptxCurrentIndex.value + 1)
}

async function initPptxMain() {
  if (!listContainer.value || !previewContainer.value || !props.fileBuffer) return

  slideAspectRatio.value = (await getSlideAspectRatio(props.fileBuffer)) || 16 / 9

  // 좌측 목록(list 모드, 슬라이드 전체를 한 번에 렌더링)과 우측 큰 미리보기(slide 모드,
  // 첫 슬라이드만 렌더링)를 독립된 try/catch로 분리한다. list 모드는 모든 슬라이드를
  // 한꺼번에 그리다 보니 특정 슬라이드의 콘텐츠(차트/특수 도형 등)에 따라 실패할 수
  // 있는데, 이때도 하나로 묶여 있으면 훨씬 단순한 slide 모드(첫 슬라이드만)까지 같이
  // 실패해서 미리보기 자체가 안 뜨는 문제가 있었다. 분리해두면 목록 렌더링이 실패해도
  // 최소한 오른쪽 큰 미리보기는 계속 보여줄 수 있다.
  let listOk = false
  try {
    listViewer = initPptxPreview(listContainer.value, { width: LIST_CONTENT_WIDTH, mode: 'list' })
    await listViewer.preview(props.fileBuffer.slice(0))
    attachThumbnailClicks()
    listOk = true
  } catch (err) {
    listFailed.value = true
    console.error('슬라이드 목록 렌더링 실패 - 오른쪽 큰 미리보기는 계속 시도합니다', err)
  }

  try {
    previewViewer = initPptxPreview(previewContainer.value, { ...previewSize.value, mode: 'slide' })
    await previewViewer.preview(props.fileBuffer.slice(0))
    stripBuiltInNav(previewContainer.value)
    pptxSlideCount.value = previewViewer.slideCount
    pptxCurrentIndex.value = 0
    if (listOk) highlightThumbnail(0)
  } catch (err) {
    loadError.value = 'PPT 파일을 읽는 데 실패했습니다. 파일이 손상되었거나 지원하지 않는 형식일 수 있습니다.'
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
    // 방향키/점 인디케이터로 이동할 때는 마우스로 직접 스크롤하지 않으므로, 선택된
    // 슬라이드가 좌측 목록의 보이는 영역 밖에 있으면 안 따라오는 것처럼 보였다.
    if (i === index) el.scrollIntoView({ block: 'nearest' })
  })
}

function goToSlide(index) {
  pptxCurrentIndex.value = index
  if (previewViewer) {
    previewViewer.renderSingleSlide(index)
  }
  if (enlargeViewer) {
    enlargeViewer.renderSingleSlide(index)
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
    enlargeViewer = initPptxPreview(enlargeContainer.value, { ...enlargeSize.value, mode: 'slide' })
    await enlargeViewer.preview(props.fileBuffer.slice(0))
    stripBuiltInNav(enlargeContainer.value)
    const idx = pptxCurrentIndex.value
    if (idx > 0) {
      enlargeViewer.renderSingleSlide(idx)
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
  listFailed.value = false
  slideAspectRatio.value = 16 / 9
  pptxCurrentIndex.value = 0
  pptxSlideCount.value = 0
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
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <a-modal
    :open="open"
    :title="fileName"
    width="1040px"
    :footer="null"
    destroy-on-close
    @update:open="(val) => emit('update:open', val)"
    @cancel="closeMain"
  >
    <a-spin :spinning="loading">
      <div v-if="fileType === 'pdf'" class="pptx-slide-viewer">
        <div class="pptx-slide-viewer__list">
          <VuePdfEmbed v-if="pdfListSource" :source="pdfListSource" :width="LIST_CONTENT_WIDTH">
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
            <a-button type="primary" @click="openEnlarge">크게보기</a-button>
          </div>
          <div class="pptx-slide-viewer__preview pptx-slide-viewer__preview--pdf">
            <VuePdfEmbed
              v-if="pdfPreviewSource"
              :source="pdfPreviewSource"
              :page="currentPage"
              :width="PREVIEW_WIDTH"
              @loaded="onPdfLoaded"
            />
            <SlideNavArrows :disabled-prev="currentPage <= 1" :disabled-next="currentPage >= totalPages" @prev="pdfPrev" @next="pdfNext" />
          </div>
          <SlideDotsIndicator :count="dotCount" :active-index="dotActiveIndex" @select="goToDot" />
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
            <a-button type="primary" @click="openEnlarge">크게보기</a-button>
          </div>
          <div class="pptx-slide-viewer__preview pptx-slide-viewer__preview--pdf">
            <img v-if="slideImages[currentPage - 1]" :src="slideImages[currentPage - 1]" class="ppt-slide-image" />
            <SlideNavArrows :disabled-prev="currentPage <= 1" :disabled-next="currentPage >= totalPages" @prev="pdfPrev" @next="pdfNext" />
          </div>
          <SlideDotsIndicator :count="dotCount" :active-index="dotActiveIndex" @select="goToDot" />
        </div>
      </div>

      <div v-else class="pptx-slide-viewer">
        <div class="pptx-slide-viewer__list">
          <p v-if="listFailed" class="pptx-slide-viewer__list-fallback">슬라이드 목록을 표시할 수 없습니다.</p>
          <div v-show="!listFailed" ref="listContainer" />
        </div>
        <div class="pptx-slide-viewer__main">
          <div class="pptx-slide-viewer__main-header">
            <span class="pptx-slide-viewer__hint">{{ pptxCurrentIndex + 1 }} / {{ pptxSlideCount }}번 슬라이드</span>
            <a-button type="primary" @click="openEnlarge">크게보기</a-button>
          </div>
          <div
            ref="previewContainer"
            class="pptx-slide-viewer__preview"
            :style="{ width: previewSize.width + 'px', height: previewSize.height + 'px' }"
          >
            <SlideNavArrows
              :disabled-prev="pptxCurrentIndex <= 0"
              :disabled-next="pptxCurrentIndex >= pptxSlideCount - 1"
              @prev="pptxPrev"
              @next="pptxNext"
            />
          </div>
          <SlideDotsIndicator :count="dotCount" :active-index="dotActiveIndex" @select="goToDot" />
        </div>
      </div>

      <a-alert v-if="error || loadError" class="pptx-slide-viewer__alert" type="error" show-icon :message="error || loadError" />
    </a-spin>
  </a-modal>

  <a-modal
    v-model:open="enlargeOpen"
    :title="fileName"
    width="1260px"
    :footer="null"
    destroy-on-close
    @cancel="closeEnlarge"
  >
    <template v-if="fileType === 'pdf'">
      <div class="pptx-slide-viewer__enlarge-header">
        <span>{{ currentPage }} / {{ totalPages }}페이지</span>
      </div>
      <div class="pptx-slide-viewer__enlarge pptx-slide-viewer__enlarge--pdf">
        <VuePdfEmbed v-if="pdfEnlargeSource" :source="pdfEnlargeSource" :page="currentPage" :width="ENLARGE_WIDTH" />
        <SlideNavArrows :disabled-prev="currentPage <= 1" :disabled-next="currentPage >= totalPages" @prev="pdfPrev" @next="pdfNext" />
      </div>
      <SlideDotsIndicator :count="dotCount" :active-index="dotActiveIndex" @select="goToDot" />
    </template>
    <template v-else-if="fileType === 'ppt'">
      <div class="pptx-slide-viewer__enlarge-header">
        <span>{{ currentPage }} / {{ totalPages }}번 슬라이드</span>
      </div>
      <div class="pptx-slide-viewer__enlarge pptx-slide-viewer__enlarge--pdf">
        <img v-if="slideImages[currentPage - 1]" :src="slideImages[currentPage - 1]" class="ppt-slide-image" />
        <SlideNavArrows :disabled-prev="currentPage <= 1" :disabled-next="currentPage >= totalPages" @prev="pdfPrev" @next="pdfNext" />
      </div>
      <SlideDotsIndicator :count="dotCount" :active-index="dotActiveIndex" @select="goToDot" />
    </template>
    <template v-else>
      <div class="pptx-slide-viewer__enlarge-header">
        <span>{{ pptxCurrentIndex + 1 }} / {{ pptxSlideCount }}번 슬라이드</span>
      </div>
      <div
        ref="enlargeContainer"
        class="pptx-slide-viewer__enlarge"
        :style="{ width: enlargeSize.width + 'px', height: enlargeSize.height + 'px' }"
      >
        <SlideNavArrows
          :disabled-prev="pptxCurrentIndex <= 0"
          :disabled-next="pptxCurrentIndex >= pptxSlideCount - 1"
          @prev="pptxPrev"
          @next="pptxNext"
        />
      </div>
      <SlideDotsIndicator :count="dotCount" :active-index="dotActiveIndex" @select="goToDot" />
    </template>
  </a-modal>
</template>

<style scoped>
.pptx-slide-viewer {
  display: flex;
  gap: 16px;
}
.pptx-slide-viewer__list {
  width: 200px;
  max-height: 560px;
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
  /* pptx는 실제 슬라이드 비율에 맞춰 width/height를 인라인 style로 계산해서 넣는다
     (script의 previewSize) - 고정값을 주면 실제 비율과 달라 위/아래가 잘리거나
     스크롤이 생긴다. */
  position: relative;
  max-width: 100%;
  border: 1px solid #e1e0d9;
  border-radius: 6px;
}
.pptx-slide-viewer__preview--pdf {
  width: 760px;
  height: auto;
  max-height: 560px;
  overflow: auto;
  display: flex;
  justify-content: center;
}
.pptx-slide-viewer__enlarge {
  /* pptx 크게보기도 마찬가지로 script의 enlargeSize를 인라인 style로 적용한다. */
  position: relative;
  max-width: 100%;
}
.pptx-slide-viewer__enlarge--pdf {
  width: 1180px;
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
.pptx-slide-viewer__list-fallback {
  font-size: 12px;
  color: #898781;
  text-align: center;
  margin: 12px 4px;
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
