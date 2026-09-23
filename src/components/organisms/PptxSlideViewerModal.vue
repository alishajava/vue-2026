<script setup>
/**
 * organisms/PptxSlideViewerModal
 *
 * 그리드의 '구분' 셀을 클릭하면 뜨는 팝업. 좌측에는 모든 슬라이드가 실제 파워포인트의
 * 슬라이드 패널처럼 썸네일 목록으로 쭉 나열되고(pptx-preview의 mode:'list'), 우측에는
 * 선택된 슬라이드가 큰 화면으로 보인다(mode:'slide', 자체 이전/다음 네비게이션 포함).
 * 우측 상단 "크게보기"를 누르면 같은 슬라이드를 훨씬 큰 별도 팝업으로 다시 띄운다.
 *
 * pptx-preview는 list 모드에서 슬라이드마다 `.pptx-preview-slide-wrapper-{index}` 클래스를
 * 붙인 div를 wrapper에 순서대로 append한다(라이브러리 번들 소스 확인 완료). 공식 API로
 * 노출된 클릭 콜백은 없어서, 렌더링 직후 wrapper의 자식 엘리먼트들에 직접 클릭 리스너를
 * 달아 "어떤 썸네일을 클릭했는지"를 알아낸다.
 */
import { ref, shallowRef, nextTick, watch, onBeforeUnmount } from 'vue'
import { init as initPptxPreview } from 'pptx-preview'

const props = defineProps({
  open: {
    type: Boolean,
    required: true,
  },
  fileName: {
    type: String,
    default: '',
  },
  fileBuffer: {
    type: ArrayBuffer,
    default: null,
  },
})

const emit = defineEmits(['update:open'])

const LIST_SIZE = { width: 180 }
const PREVIEW_SIZE = { width: 640, height: 360 }
const ENLARGE_SIZE = { width: 1100, height: 619 }

const listContainer = ref(null)
const previewContainer = ref(null)
const enlargeContainer = ref(null)
let listViewer = null
let previewViewer = null
let enlargeViewer = null

const loadError = ref('')
const enlargeOpen = ref(false)

watch(
  () => props.open,
  async (isOpen) => {
    if (!isOpen) {
      cleanupMain()
      return
    }
    if (!props.fileBuffer) return
    loadError.value = ''
    await nextTick()
    await initMainViewers()
  },
)

async function initMainViewers() {
  if (!listContainer.value || !previewContainer.value || !props.fileBuffer) return
  try {
    listViewer = initPptxPreview(listContainer.value, { ...LIST_SIZE, mode: 'list' })
    await listViewer.preview(props.fileBuffer.slice(0))
    attachThumbnailClicks()

    previewViewer = initPptxPreview(previewContainer.value, { ...PREVIEW_SIZE, mode: 'slide' })
    await previewViewer.preview(props.fileBuffer.slice(0))
    highlightThumbnail(0)
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
  await nextTick()
  if (!enlargeContainer.value || !props.fileBuffer) return
  try {
    enlargeContainer.value.innerHTML = ''
    enlargeViewer = initPptxPreview(enlargeContainer.value, { ...ENLARGE_SIZE, mode: 'slide' })
    await enlargeViewer.preview(props.fileBuffer.slice(0))
    const currentIndex = previewViewer?.currentIndex ?? 0
    if (currentIndex > 0) {
      enlargeViewer.renderSingleSlide(currentIndex)
      enlargeViewer.updatePagination()
    }
  } catch (err) {
    loadError.value = 'PPT 파일을 읽는 데 실패했습니다. 파일이 손상되었거나 지원하지 않는 형식일 수 있습니다.'
    console.error(err)
  }
}

function closeEnlarge() {
  enlargeOpen.value = false
  enlargeViewer = null
}

function cleanupMain() {
  listViewer = null
  previewViewer = null
  loadError.value = ''
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
    <div class="pptx-slide-viewer">
      <div ref="listContainer" class="pptx-slide-viewer__list" />

      <div class="pptx-slide-viewer__main">
        <div class="pptx-slide-viewer__main-header">
          <span class="pptx-slide-viewer__hint">슬라이드를 클릭하면 오른쪽에 크게 표시됩니다</span>
          <a-button type="primary" @click="openEnlarge">크게보기</a-button>
        </div>
        <div ref="previewContainer" class="pptx-slide-viewer__preview" />
      </div>
    </div>

    <a-alert v-if="loadError" class="pptx-slide-viewer__alert" type="error" show-icon :message="loadError" />
  </a-modal>

  <a-modal
    v-model:open="enlargeOpen"
    :title="fileName"
    width="1160px"
    :footer="null"
    destroy-on-close
    @cancel="closeEnlarge"
  >
    <div ref="enlargeContainer" class="pptx-slide-viewer__enlarge" />
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
.pptx-slide-viewer__enlarge {
  width: 1100px;
  height: 619px;
  max-width: 100%;
}
.pptx-slide-viewer__alert {
  margin-top: 12px;
}
</style>
