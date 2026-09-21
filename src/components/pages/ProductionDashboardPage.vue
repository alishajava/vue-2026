<script setup>
/**
 * pages/ProductionDashboardPage
 *
 * 화면 하나를 완성하는 최상위 조립 레이어(atomic design의 pages).
 * - 필터 상태(년도/공장)를 이 레벨에서 들고 있다가 자식들에게 내려준다.
 * - 상단 콤보 차트 + 하단 부품별(2개) 라인 그리드를 조립한다.
 */
import { ref } from 'vue'
import DashboardTemplate from '../templates/DashboardTemplate.vue'
import YearFactoryFilter from '../molecules/YearFactoryFilter.vue'
import ProductionPerformanceChart from '../organisms/ProductionPerformanceChart.vue'
import PartLineGrid from '../organisms/PartLineGrid.vue'
import { YEARS, FACTORIES, PARTS } from '../../mock/productionData'

const selectedYear = ref(YEARS[YEARS.length - 1])
const selectedFactory = ref(FACTORIES[0].code)

const [partA, partB] = PARTS
</script>

<template>
  <DashboardTemplate>
    <template #title>공장 생산품 실적 대시보드</template>

    <template #filter>
      <YearFactoryFilter
        v-model:year="selectedYear"
        v-model:factory="selectedFactory"
        :years="YEARS"
        :factories="FACTORIES"
      />
      <router-link class="production-dashboard-page__preview-link" to="/preview">문서 미리보기 →</router-link>
    </template>

    <template #chart>
      <ProductionPerformanceChart :year="selectedYear" :factory-code="selectedFactory" />
    </template>

    <template #grid-a>
      <PartLineGrid
        :year="selectedYear"
        :factory-code="selectedFactory"
        :part-code="partA.code"
        :part-name="partA.name"
      />
    </template>

    <template #grid-b>
      <PartLineGrid
        :year="selectedYear"
        :factory-code="selectedFactory"
        :part-code="partB.code"
        :part-name="partB.name"
      />
    </template>
  </DashboardTemplate>
</template>

<style scoped>
.production-dashboard-page__preview-link {
  font-size: 13px;
  color: #52514e;
  white-space: nowrap;
}
</style>
