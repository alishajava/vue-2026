<script setup>
/**
 * templates/DashboardTemplate
 * 페이지의 레이아웃 골격만 담당한다 (제목 + 필터 / 차트 / 하단 그리드 2분할).
 * 실제 데이터/컴포넌트는 pages 레이어에서 슬롯으로 주입한다.
 */
</script>

<template>
  <div class="dashboard-template">
    <div class="dashboard-template__header">
      <h1 class="dashboard-template__title">
        <slot name="title" />
      </h1>
      <div class="dashboard-template__filter">
        <slot name="filter" />
      </div>
    </div>

    <div class="dashboard-template__chart">
      <slot name="chart" />
    </div>

    <div class="dashboard-template__grids">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="12">
          <slot name="grid-a" />
        </a-col>
        <a-col :xs="24" :xl="12">
          <slot name="grid-b" />
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<style scoped>
.dashboard-template {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  max-width: 1440px;
  margin: 0 auto;
}
.dashboard-template__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.dashboard-template__title {
  font-size: 20px;
  font-weight: 700;
  color: #0b0b0b;
  margin: 0;
}
</style>
