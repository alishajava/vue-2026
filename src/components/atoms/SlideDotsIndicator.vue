<script setup>
/**
 * atoms/SlideDotsIndicator
 *
 * 슬라이드/페이지 개수만큼 타원형 점을 나열해서 현재 위치를 보여주는 인디케이터.
 * pptx/pdf/ppt 세 갈래 모두 같은 currentPage/currentIndex 개념으로 재사용한다
 * (DocumentSlideViewerModal이 각 타입에 맞는 count/activeIndex를 계산해서 내려줌).
 */
defineProps({
  count: {
    type: Number,
    required: true,
  },
  activeIndex: {
    type: Number,
    required: true,
  },
})

const emit = defineEmits(['select'])
</script>

<template>
  <div v-if="count > 1" class="slide-dots">
    <span
      v-for="i in count"
      :key="i"
      class="slide-dots__dot"
      :class="{ 'slide-dots__dot--active': i - 1 === activeIndex }"
      @click="emit('select', i - 1)"
    />
  </div>
</template>

<style scoped>
.slide-dots {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  margin-top: 10px;
}
.slide-dots__dot {
  width: 10px;
  height: 6px;
  border-radius: 3px;
  background: #d9d7d0;
  cursor: pointer;
  transition:
    width 0.15s,
    background 0.15s;
}
.slide-dots__dot:hover {
  background: #b9b7b0;
}
.slide-dots__dot--active {
  width: 18px;
  background: #1677ff;
}
</style>
