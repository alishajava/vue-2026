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
// 날짜/바닥글/슬라이드번호 자리표시자(ftr/dt/sldNum)를 제거한 버퍼. 파일이 열릴 때
// 한 번만 만들어서 list/slide/enlarge 렌더링에 공용으로 재사용한다.
let cleanedFileBuffer = null

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

// 좌측 슬라이드/페이지 목록에서 방향키로도 이동할 수 있게. dotCount/dotActiveIndex/
// goToDot을 그대로 재사용해서 pptx/pdf/ppt 세 타입 모두 동일하게 동작한다.
// 이 모달이 열려있는 동안에만(팝업이 화면을 가리는 동안에만) 붙였다 뗀다.
// 기본(메인) 팝업은 ↑/↓, 크게보기는 좌우 화살표 버튼만 보이므로 ←/→를 쓴다.
function handleKeydown(event) {
  const prevKey = enlargeOpen.value ? 'ArrowLeft' : 'ArrowUp'
  const nextKey = enlargeOpen.value ? 'ArrowRight' : 'ArrowDown'
  if (event.key !== prevKey && event.key !== nextKey) return
  const nextIndex = dotActiveIndex.value + (event.key === prevKey ? -1 : 1)
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

// 파워포인트는 "머리글/바닥글 삽입"을 켜지 않는 한 날짜(dt)/바닥글(ftr)/슬라이드번호
// (sldNum) 자리표시자를 화면에 그리지 않는다(값은 XML에 남아있을 뿐). 그런데
// pptx-preview 라이브러리는 이 타입 구분 없이 슬라이드에 있는 도형을 전부 그려버려서,
// 실제 파워포인트에서는 안 보이는 예시 텍스트("9/3/20XX", "프레젠테이션 제목" 등)가
// 화면에 찍힌다(사용자 실제 파일의 slide3.xml에서 확인). 렌더링 전에 이 세 타입의
// 자리표시자 도형(<p:sp>)만 슬라이드 XML에서 미리 잘라내서 우회한다.
async function stripFooterPlaceholders(buffer) {
  try {
    const zip = await JSZip.loadAsync(buffer.slice(0))
    const slideFiles = Object.keys(zip.files).filter((name) => /^ppt\/slides\/slide\d+\.xml$/.test(name))
    for (const path of slideFiles) {
      let xml = await zip.file(path).async('string')
      xml = xml.replace(/<p:sp>.*?<\/p:sp>/gs, (match) =>
        /<p:ph type="(?:ftr|dt|sldNum)"/.test(match) ? '' : match,
      )
      zip.file(path, xml)
    }
    return await zip.generateAsync({ type: 'arraybuffer' })
  } catch (err) {
    console.error('머리글/바닥글 자리표시자 제거 실패 - 원본 그대로 사용합니다', err)
    return buffer
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

// pdf는 페이지마다 캡션 텍스트 색만 바뀌고 실제 썸네일(캔버스)엔 표시가 없었다.
// vue-pdf-embed는 id를 주면 각 페이지 div에 "{id}-{페이지번호}"를 붙여주므로
// (아래 템플릿의 id="pdf-list-page"), pptx 썸네일과 같은 방식으로 테두리를 직접 넣는다.
let lastPdfHighlightId = null
function highlightPdfPage(page) {
  if (lastPdfHighlightId) {
    const prev = document.getElementById(lastPdfHighlightId)
    if (prev) prev.style.outline = 'none'
  }
  const id = `pdf-list-page-${page}`
  const active = document.getElementById(id)
  if (active) {
    active.style.outline = '2px solid #1677ff'
    active.style.outlineOffset = '-2px'
    active.scrollIntoView({ block: 'nearest' })
  }
  lastPdfHighlightId = id
}

watch(currentPage, (page) => {
  if (props.fileType === 'pdf') highlightPdfPage(page)
})

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
  cleanedFileBuffer = await stripFooterPlaceholders(props.fileBuffer)

  // 좌측 목록(list 모드, 슬라이드 전체를 한 번에 렌더링)과 우측 큰 미리보기(slide 모드,
  // 첫 슬라이드만 렌더링)를 독립된 try/catch로 분리한다. list 모드는 모든 슬라이드를
  // 한꺼번에 그리다 보니 특정 슬라이드의 콘텐츠(차트/특수 도형 등)에 따라 실패할 수
  // 있는데, 이때도 하나로 묶여 있으면 훨씬 단순한 slide 모드(첫 슬라이드만)까지 같이
  // 실패해서 미리보기 자체가 안 뜨는 문제가 있었다. 분리해두면 목록 렌더링이 실패해도
  // 최소한 오른쪽 큰 미리보기는 계속 보여줄 수 있다.
  let listOk = false
  try {
    listViewer = initPptxPreview(listContainer.value, { width: LIST_CONTENT_WIDTH, mode: 'list' })
    await listViewer.preview((cleanedFileBuffer || props.fileBuffer).slice(0))
    // 라이브러리가 목록 전체를 감싸는 wrapper에 background:#000을 직접 박아 넣는데,
    // 각 슬라이드 div의 하단 10px margin 틈새로 이 검정 배경이 비쳐서 슬라이드마다
    // 밑에 검은 줄이 생긴 것처럼 보였다 - wrapper 배경을 투명하게 덮어써서 없앤다.
    listViewer.wrapper.style.background = 'transparent'
    attachThumbnailClicks()
    listOk = true
  } catch (err) {
    listFailed.value = true
    console.error('슬라이드 목록 렌더링 실패 - 오른쪽 큰 미리보기는 계속 시도합니다', err)
  }

  try {
    previewViewer = initPptxPreview(previewContainer.value, { ...previewSize.value, mode: 'slide' })
    await previewViewer.preview((cleanedFileBuffer || props.fileBuffer).slice(0))
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

// outline은 라이브러리가 각 슬라이드 div에 걸어둔 overflow:hidden에 가려 안 보였고,
// box-shadow(inset)는 클리핑은 피하지만 배경/테두리와 같은 단계(자식 콘텐츠보다 먼저)에
// 그려져서 그 위에 실제 슬라이드 내용(배경/마스터/도형)이 나중에 덮어써 결국 가려졌다.
// 실제 DOM 엘리먼트를 슬라이드 콘텐츠보다 "나중에" 자식으로 추가하면 그 위에 그려지고,
// inset:0으로 크기를 el 안쪽에 딱 맞추면 overflow:hidden에 잘리지도 않는다.
function getOrCreateHighlightOverlay(el) {
  let overlay = el.querySelector(':scope > .pptx-slide-viewer__thumb-highlight')
  if (!overlay) {
    overlay = document.createElement('div')
    overlay.className = 'pptx-slide-viewer__thumb-highlight'
    overlay.style.position = 'absolute'
    overlay.style.inset = '0'
    overlay.style.pointerEvents = 'none'
    el.appendChild(overlay)
  }
  return overlay
}

function highlightThumbnail(index) {
  if (!listViewer?.wrapper) return
  Array.from(listViewer.wrapper.children).forEach((el, i) => {
    const overlay = getOrCreateHighlightOverlay(el)
    overlay.style.boxShadow = i === index ? 'inset 0 0 0 2px #1677ff' : 'none'
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
    // enlargeContainer 안에는 라이브러리가 그릴 내용뿐 아니라 우리가 만든
    // SlideNavArrows(Vue가 렌더링)도 같이 들어있다. innerHTML=''로 지우면 그 버튼도
    // 같이 사라진다 - 이 모달은 destroy-on-close라 열릴 때마다 어차피 컨테이너가
    // 새로 만들어지므로 지울 필요 자체가 없다.
    enlargeViewer = initPptxPreview(enlargeContainer.value, { ...enlargeSize.value, mode: 'slide' })
    await enlargeViewer.preview((cleanedFileBuffer || props.fileBuffer).slice(0))
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
  cleanedFileBuffer = null
  loadError.value = ''
  listFailed.value = false
  slideAspectRatio.value = 16 / 9
  pptxCurrentIndex.value = 0
  pptxSlideCount.value = 0
  pdfListSource.value = null
  pdfPreviewSource.value = null
  lastPdfHighlightId = null
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
          <VuePdfEmbed
            v-if="pdfListSource"
            id="pdf-list-page"
            :source="pdfListSource"
            :width="LIST_CONTENT_WIDTH"
            @rendered="highlightPdfPage(currentPage)"
          >
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
