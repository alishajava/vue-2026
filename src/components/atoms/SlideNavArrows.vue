<script setup>
/**
 * atoms/SlideNavArrows
 *
 * pptx-preview가 원래 자체적으로 그려주던 하단 원형 이전/다음 버튼과 같은 위치/모양을
 * 재현한 것. 라이브러리의 내장 버튼은 우리 상태(goToSlide 등)와 연동되지 않아서
 * DOM에서 제거했는데(SlideViewerModal 참고), 시각적으로는 그 스타일이 나아서 우리
 * 버튼으로 같은 자리에 다시 그려 넣는다. 부모 쪽에 position:relative가 있어야
 * 이 컴포넌트의 absolute 포지셔닝이 미리보기 박스 기준으로 잡힌다.
 *
 * pptx 타입에서는 이 버튼들이 Vue 템플릿으로 먼저 렌더링된 뒤, pptx-preview
 * 라이브러리가 같은 컨테이너에 자기 wrapper div(배경 #000, 불투명)를 나중에
 * append한다 - DOM 순서상 나중에 붙은 쪽이 위에 그려지므로 z-index 없이는
 * 이 버튼들이 그 wrapper에 완전히 가려져 안 보인다. z-index로 항상 위에 오게 한다.
 */
defineProps({
  disabledPrev: {
    type: Boolean,
    default: false,
  },
  disabledNext: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['prev', 'next'])
</script>

<template>
  <button
    type="button"
    class="slide-nav-arrow slide-nav-arrow--prev"
    :disabled="disabledPrev"
    aria-label="이전 슬라이드"
    @click="emit('prev')"
  >
    <span class="slide-nav-arrow__chevron slide-nav-arrow__chevron--left" />
  </button>
  <button
    type="button"
    class="slide-nav-arrow slide-nav-arrow--next"
    :disabled="disabledNext"
    aria-label="다음 슬라이드"
    @click="emit('next')"
  >
    <span class="slide-nav-arrow__chevron slide-nav-arrow__chevron--right" />
  </button>
</template>

<style scoped>
.slide-nav-arrow {
  position: absolute;
  z-index: 5;
  bottom: 14px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
  padding: 0;
}
.slide-nav-arrow:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.75);
}
.slide-nav-arrow:disabled {
  opacity: 0.3;
  cursor: default;
}
.slide-nav-arrow--prev {
  right: 62px;
}
.slide-nav-arrow--next {
  right: 14px;
}
.slide-nav-arrow__chevron {
  width: 9px;
  height: 9px;
  border-left: 2px solid #fff;
  border-bottom: 2px solid #fff;
}
.slide-nav-arrow__chevron--left {
  transform: rotate(45deg);
  margin-left: 2px;
}
.slide-nav-arrow__chevron--right {
  transform: rotate(225deg);
  margin-right: -2px;
}
</style>
