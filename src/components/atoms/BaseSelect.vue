<script setup>
/**
 * atoms/BaseSelect
 * antd-vue a-select를 감싸는 atom. label + select 한 쌍을 하나의 단위로 재사용한다.
 */
defineProps({
  label: {
    type: String,
    default: '',
  },
  modelValue: {
    type: [String, Number],
    default: undefined,
  },
  options: {
    type: Array, // [{ value, label }]
    required: true,
  },
  width: {
    type: String,
    default: '160px',
  },
})

defineEmits(['update:modelValue'])
</script>

<template>
  <div class="base-select">
    <span v-if="label" class="base-select__label">{{ label }}</span>
    <a-select
      :value="modelValue"
      :options="options"
      :style="{ width }"
      @update:value="(val) => $emit('update:modelValue', val)"
    />
  </div>
</template>

<style scoped>
.base-select {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.base-select__label {
  font-size: 13px;
  color: #52514e;
  white-space: nowrap;
}
</style>
