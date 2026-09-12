<script setup>
/**
 * molecules/YearFactoryFilter
 * atoms/BaseSelect 2개(년도, 공장)를 조합한 molecule. 상위(페이지)에서 v-model로 값을 받는다.
 */
import BaseSelect from '../atoms/BaseSelect.vue'

const props = defineProps({
  year: {
    type: Number,
    required: true,
  },
  factory: {
    type: String,
    required: true,
  },
  years: {
    type: Array, // number[]
    required: true,
  },
  factories: {
    type: Array, // [{ code, name }]
    required: true,
  },
})

const emit = defineEmits(['update:year', 'update:factory'])

const yearOptions = () => props.years.map((y) => ({ value: y, label: `${y}년` }))
const factoryOptions = () => props.factories.map((f) => ({ value: f.code, label: f.name }))
</script>

<template>
  <div class="year-factory-filter">
    <BaseSelect
      label="년도"
      :model-value="year"
      :options="yearOptions()"
      width="120px"
      @update:model-value="(val) => $emit('update:year', val)"
    />
    <BaseSelect
      label="공장"
      :model-value="factory"
      :options="factoryOptions()"
      width="140px"
      @update:model-value="(val) => $emit('update:factory', val)"
    />
  </div>
</template>

<style scoped>
.year-factory-filter {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
</style>
