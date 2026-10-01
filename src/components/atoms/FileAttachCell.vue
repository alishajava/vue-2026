<template>
  <span style="display: flex; align-items: center; justify-content: center; height: 100%; width: 100%">
    <a-upload v-if="!hasFile" :show-upload-list="false" accept=".pptx,.pdf,.ppt" :before-upload="handleBeforeUpload">
      <a-button size="small">파일선택</a-button>
    </a-upload>

    <div
      v-else
      ref="thumbWrapperRef"
      :style="thumbStyle"
      @mouseenter="isHovered = true"
      @mouseleave="isHovered = false"
    >
      <div :style="thumbScaledContentStyle">
        <a-spin v-if="isLoadingThumb" size="small" />
        <VuePdfEmbed v-else-if="fileType === 'pdf' && pdfThumbSource && !thumbLoadFailed" :source="pdfThumbSource" :page="1" :width="THUMB_WIDTH" />
        <div v-else-if="fileType === 'pptx' && !thumbLoadFailed" ref="thumbContainer" :style="thumbContentStyle" />
        <img v-else-if="fileType === 'ppt' && pptThumbImage && !thumbLoadFailed" :src="pptThumbImage" :style="thumbContentStyle" />
        <div v-else :style="thumbIconStyle">{{ (fileType || '').toUpperCase() }}</div>
      </div>

      <div :style="overlayStyle">
        <a-tooltip title="미리보기">
          <a-button size="small" shape="circle" @click.stop="openPreview">👁</a-button>
        </a-tooltip>
        <a-tooltip title="다운로드">
          <a-button size="small" shape="circle" @click.stop="download">⬇</a-button>
        </a-tooltip>
        <a-tooltip title="초기화">
          <a-button size="small" shape="circle" danger @click.stop="resetFile">✕</a-button>
        </a-tooltip>
      </div>
    </div>
  </span>
</template>

<script setup>
/**
 * atoms/FileAttachCell
 *
 * '파일첨부' 컬럼의 ag-Grid cellRenderer. 파일이 없으면 <a-upload> 버튼만 보이고,
 * 첨부되면 셀 전체가 그 문서의 1페이지/1슬라이드를 작게 렌더링한 썸네일로 바뀐다.
 * 마우스오버하면 반투명 오버레이와 함께 미리보기/다운로드/삭제 버튼이 뜬다.
 *
 * [썸네일 소스: 메모리 버퍼 vs 서버 재조회]
 * row.fileBuffer가 메모리에 있으면(방금 첨부했거나 아직 '저장'을 안 눌러 서버에
 * 올라가지 않은 상태) 그 버퍼로 바로 렌더링한다. 이미 저장된 행은 '저장' 시점에
 * 메모리 절약을 위해 fileBuffer를 비우므로(row.fileBuffer === null), 이 경우
 * row.id로 서버에서 파일을 다시 받아와(pdf/pptx는 fetchDocumentFile, ppt는 저장된
 * 바이트를 그대로 변환하는 fetchDocumentSlides) 렌더링한다 - 셀이 새로 마운트될
 * 때마다(예: 다른 행 첨부로 인한 강제 리프레시) 매번 다시 받아오긴 하지만, attach
 * 컬럼 리프레시는 해당 행에만 scope했으므로(rowNodes) 관계없는 행까지 재조회하진
 * 않는다. pdf는 vue-pdf-embed, pptx는 pptx-preview를 셀 크기에 맞춰 작게 띄우고,
 * ppt(구버전)는 브라우저에서 못 읽으므로 서버 변환 첫 장 이미지를 쓴다. 조회/렌더링에
 * 실패하면 파일 형식을 나타내는 작은 아이콘으로 대체한다.
 *
 * ag-Grid cellRenderer는 메인 앱 트리 밖에서 별도로 마운트되어 <style scoped>가
 * 적용되지 않는다(실제로 렌더링된 DOM에 data-v-* 속성 자체가 안 붙는 것까지 확인됨) -
 * 여기서는 antd-vue 전역 컴포넌트만 쓰고, 이 파일만의 고유 요소는 전부 :style로
 * 처리한다(클래스를 썼다간 아무 CSS도 안 먹는다 - 전에 그래서 썸네일 높이가 0이
 * 되고 오버레이가 absolute 포지션을 못 받아 박스 중간이 아니라 위쪽에 떠버렸다).
 *
 * [반응형 크기]
 * pdf(vue-pdf-embed)/pptx(pptx-preview) 둘 다 렌더링할 때 실제 캔버스/DOM 크기를
 * 픽셀 숫자로 못박아서 받는 라이브러리라, 화면 크기가 바뀔 때마다 그 값에 맞춰 매번
 * 다시 렌더링시키는 건 비용이 크다. 그래서 실제 렌더링은 항상 THUMB_WIDTH/HEIGHT
 * "원본" 크기로 한 번만 하고, 그 결과를 담은 안쪽 래퍼(thumbScaledContentStyle)에
 * CSS transform: scale()만 입혀서 바깥 박스(thumbStyle, width:100%+aspect-ratio로
 * 반응형) 크기에 맞춘다. 바깥 박스의 실제 렌더링 폭은 ResizeObserver로 지켜보다가
 * 바뀔 때마다 scale 비율만 다시 계산한다 - 렌더링을 다시 하지 않아도 되니 리사이즈
 * 중에도 가볍다.
 */
import { ref, shallowRef, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { init as initPptxPreview } from 'pptx-preview'
import VuePdfEmbed from 'vue-pdf-embed'
import { clearDocumentFile, fetchDocumentFile, convertToSlides, fetchDocumentSlides } from '../../api/documentApi'
import { arrayBufferToBase64, base64ToArrayBuffer } from '../../utils/base64'
import { stripFooterPlaceholders } from '../../utils/pptxCleanup'

const props = defineProps({
  params: {
    type: Object,
    required: true,
  },
})

const THUMB_WIDTH = 92 // 실제 렌더링(pptx-preview/vue-pdf-embed)에 넘기는 "원본" 크기 - 화면에 보이는 크기는 이걸 CSS로 스케일해서 만든다.
const THUMB_HEIGHT = 52

const isHovered = ref(false)

// --- 반응형 스케일(ResizeObserver) ---
const thumbWrapperRef = ref(null)
const renderScale = ref(1)
let resizeObserver = null

function observeThumbWrapper(el) {
  resizeObserver?.disconnect()
  resizeObserver = null
  if (!el) return
  resizeObserver = new ResizeObserver((entries) => {
    const width = entries[0]?.contentRect?.width
    if (width) renderScale.value = width / THUMB_WIDTH
  })
  resizeObserver.observe(el)
}

watch(thumbWrapperRef, observeThumbWrapper)

const thumbStyle = computed(() => ({
  position: 'relative',
  overflow: 'hidden',
  borderRadius: '4px',
  border: '1px solid #e1e0d9',
  background: '#fff',
  width: '100%',
  aspectRatio: `${THUMB_WIDTH} / ${THUMB_HEIGHT}`,
}))

// 원본 크기(THUMB_WIDTH/HEIGHT)로 그대로 렌더링한 뒤, 이 래퍼에만 scale을 입혀서
// 바깥 박스(반응형) 크기에 맞춘다. transform-origin을 left top으로 둬야 박스
// 왼쪽위를 기준으로 커지고/작아지므로, 바깥 박스와 안쪽 콘텐츠의 왼쪽위가 항상 맞는다.
const thumbScaledContentStyle = computed(() => ({
  width: THUMB_WIDTH + 'px',
  height: THUMB_HEIGHT + 'px',
  transform: `scale(${renderScale.value})`,
  transformOrigin: 'top left',
}))

const thumbContentStyle = {
  display: 'block',
  width: '100%',
  height: '100%',
  objectFit: 'contain',
  transformOrigin: 'top left',
  pointerEvents: 'none',
}

const thumbIconStyle = {
  width: '100%',
  height: '100%',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  fontSize: '11px',
  fontWeight: '700',
  color: '#898781',
  background: '#f3f2ee',
}

const overlayStyle = computed(() => ({
  position: 'absolute',
  inset: '0',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: '4px',
  background: 'rgba(0, 0, 0, 0.55)',
  opacity: isHovered.value ? 1 : 0,
  transition: 'opacity 0.15s',
  pointerEvents: isHovered.value ? 'auto' : 'none',
}))

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
  // applyTransaction은 컬럼별로 "바뀐 필드"만 골라서 다시 그린다 - attach 컬럼은
  // field: 'attach'에 대응하는 row.attach라는 데이터가 실제로는 없으므로, 이 컬럼
  // 자신은 자동 변경 감지 대상이 아니다. 강제로 다시 그려야 이 FileAttachCell
  // 인스턴스가 새로 만들어지고(파일선택 버튼 -> 썸네일) 렌더링된다.
  // 이 행에만 rowNodes로 scope한다 - 저장된 다른 행들은 썸네일을 서버에서 다시
  // 받아와야 하므로, scope 없이 전체를 강제 리프레시하면 관계없는 행들까지
  // 불필요하게 재조회하게 된다.
  props.params.api.refreshCells({ rowNodes: [props.params.node], columns: ['attach'], force: true })

  return false // 실제 업로드(HTTP 요청)는 막는다 - 파일 읽기는 이미 위에서 끝났다.
}

// --- 썸네일 렌더링 ---
const thumbContainer = ref(null) // pptx-preview용 컨테이너(template ref)
const pdfThumbSource = shallowRef(null)
const pptThumbImage = ref('')
let pptxThumbViewer = null

const hasFile = computed(() => !!props.params.data.fileName)
const fileType = computed(() => props.params.data.fileType)
const isLoadingThumb = ref(false)
const thumbLoadFailed = ref(false)

function cleanupThumb() {
  pptxThumbViewer = null
  if (thumbContainer.value) thumbContainer.value.innerHTML = ''
  pdfThumbSource.value = null
  pptThumbImage.value = ''
}

// 렌더링을 시작하는 시점의 row.id를 기억해서, 서버 응답이 늦게 도착했을 때 그 사이
// 다른 파일로 다시 첨부되었거나 행이 삭제된 경우 결과를 버리기 위한 용도.
async function renderThumbnail() {
  cleanupThumb()
  thumbLoadFailed.value = false
  const row = props.params.data
  const requestId = row.id
  if (!row.fileName) return
  if (!row.fileBuffer && !row.id) return // 버퍼도 없고 저장도 안 된 상태(있을 수 없지만 방어적으로)

  // ppt는 pdf/pptx와 달리 원본 바이트 자체를 브라우저에서 못 쓰므로, 저장된 행이면
  // 파일을 통째로 받아올 필요 없이 이미 서버가 들고 있는 바이트를 그대로 변환하는
  // fetchDocumentSlides 하나만 호출한다.
  if (row.fileType === 'ppt') {
    isLoadingThumb.value = !row.fileBuffer
    try {
      const images = row.fileBuffer
        ? await convertToSlides({ fileBase64: await arrayBufferToBase64(row.fileBuffer.slice(0)), fileType: 'ppt' })
        : await fetchDocumentSlides(row.id)
      isLoadingThumb.value = false
      if (props.params.data.id !== requestId) return // 그 사이 다른 행/파일로 바뀌었으면 버린다.
      pptThumbImage.value = images?.[0] || ''
    } catch (err) {
      isLoadingThumb.value = false
      console.error('ppt 썸네일 변환 실패', err)
      thumbLoadFailed.value = true
    }
    return
  }

  let buffer = row.fileBuffer
  if (!buffer) {
    // 저장된 행 - 메모리에 원본이 없으니 서버에서 다시 받아온다.
    isLoadingThumb.value = true
    try {
      const base64 = await fetchDocumentFile(row.id)
      buffer = await base64ToArrayBuffer(base64)
    } catch (err) {
      console.error('첨부파일 조회 실패', err)
      thumbLoadFailed.value = true
      isLoadingThumb.value = false
      return
    }
    isLoadingThumb.value = false
    // 응답이 오는 동안 이 셀이 다른 행/다른 파일을 가리키게 됐으면 결과를 버린다.
    if (props.params.data.id !== requestId || props.params.data.fileBuffer) return
  }

  if (row.fileType === 'pdf') {
    pdfThumbSource.value = buffer.slice(0)
  } else if (row.fileType === 'pptx') {
    await nextTick()
    if (!thumbContainer.value) return
    try {
      // height를 안 넘기면 pptx-preview가 실제 슬라이드 비율대로 자기 높이를 계산해버려서,
      // 92x52 박스랑 비율이 다른 슬라이드(예: 4:3)는 박스를 벗어나거나(overflow:hidden에
      // 잘림) 반대로 작게 나왔다(실제 재현 확인: 92x69로 계산돼 52px 박스에서 아래쪽이
      // 잘림). width/height를 둘 다 박스 크기로 넘기면 라이브러리가 그 안에서 실제 비율에
      // 맞춰 가운데 정렬(letterbox)해주므로, 어떤 비율의 슬라이드든 박스를 벗어나지 않는다.
      pptxThumbViewer = initPptxPreview(thumbContainer.value, { width: THUMB_WIDTH, height: THUMB_HEIGHT, mode: 'slide' })
      // 날짜/바닥글/슬라이드번호 자리표시자(DocumentSlideViewerModal과 동일한 이유로
      // pptx-preview가 구분 없이 그려버리는 것)를 썸네일에서도 먼저 잘라낸다 - 안 그러면
      // 전체 슬라이드를 그대로 축소한 썸네일 모서리에 작게 슬라이드 번호가 딸려 보인다.
      const cleanedBuffer = await stripFooterPlaceholders(buffer)
      await pptxThumbViewer.preview(cleanedBuffer.slice(0))
      // pptx-preview가 'slide' 모드에서 자체적으로 그려 넣는 원형 이전/다음 버튼 +
      // 페이지 표시(DocumentSlideViewerModal의 stripBuiltInNav와 동일한 것)를 여기서도
      // 지워야 한다 - 92x52의 작은 썸네일 박스엔 안 맞는 크기라 모서리에 잘린 조각만
      // 보이는 원인이었다. 썸네일은 클릭해서 넘기는 기능 자체가 없으니 그냥 없애면 된다.
      thumbContainer.value.querySelectorAll('.pptx-preview-wrapper-next, .pptx-preview-wrapper-pagination').forEach((el) => el.remove())
    } catch (err) {
      console.error('pptx 썸네일 렌더링 실패', err)
      thumbLoadFailed.value = true
    }
  }
}

// 파일이 (재)첨부될 때마다, 또는 셀이 새로 마운트될 때(저장된 행) 다시 그린다.
watch(() => [props.params.data.fileBuffer, props.params.data.fileType], renderThumbnail, { immediate: true })

onBeforeUnmount(() => {
  cleanupThumb()
  resizeObserver?.disconnect()
})

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

// 행 자체는 그대로 두고 첨부파일만 지워서 '파일선택' 버튼이 다시 보이는 상태로
// 되돌린다. 이미 서버에 저장된 행(id가 있는 행)이면 즉시 서버에도 반영한다 -
// RowActionsCell의 삭제와 같은 이유로, 되돌릴 필요가 거의 없는 동작이라 '저장'
// 버튼을 거치지 않고 바로 확정한다.
function resetFile() {
  const row = props.params.data
  Modal.confirm({
    title: '첨부파일을 초기화하시겠습니까?',
    content: row.fileName ? `"${row.fileName}" 파일을 제거합니다.` : '첨부된 파일을 제거합니다.',
    okText: '초기화',
    okType: 'danger',
    cancelText: '취소',
    onOk: async () => {
      row.fileName = null
      row.fileType = null
      row.fileBuffer = null
      row.registeredAt = null
      props.params.api.applyTransaction({ update: [row] })
      props.params.api.refreshCells({ rowNodes: [props.params.node], columns: ['attach'], force: true })
      if (row.id) {
        try {
          await clearDocumentFile(row.id)
        } catch (err) {
          message.error('서버에서 첨부파일을 지우지 못했습니다.')
          console.error(err)
        }
      }
    },
  })
}
</script>
