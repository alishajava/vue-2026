<script setup>
/**
 * atoms/BaseCard
 * antd-vue의 a-card를 감싸는 최소 단위 atom. 대시보드 전체에서 카드 여백/타이틀 톤을 통일한다.
 */
defineProps({
  title: {
    type: String,
    default: '',
  },
  bodyStyle: {
    type: Object,
    default: () => ({}),
  },
})
</script>

<template>
  <a-card :bordered="true" class="base-card" :body-style="bodyStyle">
    <template v-if="title || $slots.extra" #title>
      <span class="base-card__title">{{ title }}</span>
    </template>
    <template v-if="$slots.extra" #extra>
      <slot name="extra" />
    </template>
    <slot />
  </a-card>
</template>

<style scoped>
.base-card {
  border-radius: 8px;
}
.base-card__title {
  font-weight: 600;
  font-size: 15px;
}
</style>
