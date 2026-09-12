# 공장 생산품 실적 대시보드

Vue 3 + antd-vue + Chart.js + ag-Grid로 만든 공장 생산 실적 차트/그리드 대시보드입니다.
컴포넌트는 **Atomic Design**(atoms → molecules → organisms → templates → pages) 구조로 나눴습니다.

## 실행 방법

```bash
npm install
npm run dev      # 개발 서버 (기본 http://localhost:5173)
npm run build    # 프로덕션 빌드 (dist/)
npm run preview  # 빌드 결과 미리보기
```

## 폴더 구조

```
src/
  charts/
    chartRegister.js   # Chart.js 컨트롤러/엘리먼트/플러그인을 "한 곳에서만" register
    chartTheme.js       # 차트 공통 색상 토큰 (categorical palette, 목표선 dash 등)
  mock/
    seededRandom.js     # 같은 key -> 항상 같은 값을 주는 결정적 난수 (API 붙이기 전 데모용)
    productionData.js   # 년도/공장/부품/라인 mock 데이터 + 조회 함수
  utils/
    gridColumns.js       # ag-grid 컬럼 정의 (라인/목표/가동율 colspan 그룹)
  components/
    atoms/       # BaseCard, BaseSelect
    molecules/   # YearFactoryFilter (년도+공장 셀렉트 조합)
    organisms/   # ProductionPerformanceChart(콤보 차트), PartLineGrid(부품별 그리드)
    templates/   # DashboardTemplate (레이아웃 골격, 슬롯만 정의)
    pages/       # ProductionDashboardPage (필터 상태를 들고 실제 데이터를 꽂아 조립)
  App.vue
  main.js
```

## Chart.js 공통 register 패턴

Chart.js는 사용할 컨트롤러/엘리먼트/플러그인을 명시적으로 `Chart.register(...)` 해야
동작합니다. 컴포넌트마다 각자 register하지 않고, `src/charts/chartRegister.js` **한 곳에서만**
register하도록 했습니다.

- `main.js`에서 `import './charts/chartRegister'`로 앱 시작 시 1회 register (side-effect).
- 차트가 필요한 컴포넌트(`organisms/ProductionPerformanceChart.vue`)는 `vue-chartjs`의
  `<Chart>` 컴포넌트를 사용하고, 같은 register 모듈을 다시 import해서(모듈은 캐시되므로
  실제로는 한 번만 실행됩니다) register 여부를 컴포넌트 단위로도 보장합니다.
- 새 차트를 추가할 때 필요한 Chart.js 요소가 늘어나면 `chartRegister.js` 한 곳만 수정하면
  됩니다.

## 상단 콤보 차트 구성

`organisms/ProductionPerformanceChart.vue`, 선택한 년도/공장의 1~12월 데이터를 표시합니다.

| 시리즈 | 타입 | 스타일 |
|---|---|---|
| 부품A 실적 | 막대 | 파랑 |
| 부품B 실적 | 막대 | 주황 |
| 부품합계 실적 (A+B) | 라인 | 아쿠아, 실선 |
| 부품A 목표 | 라인 | 파랑, **점선** |
| 부품B 목표 | 라인 | 주황, **점선** |
| 부품합계 목표 | 라인 | 아쿠아, **점선** |

목표선은 해당 부품(또는 합계)과 같은 색상 계열을 공유하고 점선으로만 구분해서, "같은
대상의 실적/목표 쌍"이라는 관계가 한눈에 읽히도록 했습니다. 축은 모든 시리즈가 같은 단위
(생산 수량)이므로 y축 하나만 사용합니다(이중 축 사용 안 함).

## 하단 그리드 구성

부품별로 그리드를 **2개**(부품A 그리드, 부품B 그리드)로 나눴습니다 (`organisms/PartLineGrid.vue`).
각 그리드 컬럼:

- **라인**: 해당 부품을 생산하는 생산라인 구분 (예: 1라인, 2라인)
- **목표**: 해당 라인의 당월 생산 목표
- **가동율**: 그룹 헤더(2~3개월 컬럼을 colspan)로 묶여 있으며, 최근 3개월치를 각 컬럼으로 보여줍니다.
  ag-grid의 컬럼 그룹(`children`) 기능으로 구현해서 "가동율" 헤더가 하위 3개월 컬럼 위에
  자동으로 colspan 됩니다.

## 설계 가정 (요청 내용 중 모호했던 부분)

대화 중 아래와 같이 확인하고 진행했습니다.

1. **하단 그리드의 "라인"** = 부품을 생산하는 **생산라인 구분**(1라인/2라인 등)입니다.
   각 행이 하나의 생산라인이고, 그 라인의 목표/가동율을 보여줍니다.
2. **가동율**은 실적÷목표로 계산하는 값이 아니라 **설비 가동 현황을 나타내는 별도의 데이터
   필드**로 가정했습니다 (`mock/productionData.js`의 `util_YYYY-MM` 필드).
3. **가동율의 "최근 3개월"은 상단 차트의 "년도" 필터와 무관하게, 실제 현재 시점 기준
   최근 3개월**을 보여줍니다. 설비 가동율은 과거 연도를 조회 중이어도 항상 최신 상태를
   보여주는 것이 실무적으로 자연스럽다고 보고 그렇게 구현했습니다. 다른 방식(선택한
   연도의 마지막 3개월 등)을 원하시면 `mock/productionData.js`의 `getRecentMonths()` /
   `getLineGridRows()` 호출부만 바꾸면 됩니다.
4. **목표(그리드)** 는 해당 라인의 **당월** 목표치를 부품 전체 월간 목표에서 라인별 비중으로
   나눈 값입니다 (실제로는 라인별 목표를 API에서 직접 받아오는 형태로 바뀔 가능성이 높은
   부분입니다).

## Mock 데이터 → 실제 API 연동

`src/mock/productionData.js`가 내보내는 함수 시그니처만 유지하면 됩니다.

- `getChartData(year, factoryCode)` → 상단 차트 데이터
- `getLineGridRows(year, factoryCode, partCode)` → 하단 그리드 행 데이터
- `getRecentMonths(count)` → 가동율 컬럼에 쓰일 "최근 N개월" 목록

이 3개 함수 내부만 실제 API 호출(axios/fetch 등)로 교체하면, 컴포넌트(organisms 이상)는
전혀 수정할 필요가 없습니다.

## 사용 라이브러리

- [ant-design-vue](https://antdv.com/) v4 — 셀렉트 등 UI 컴포넌트
- [Chart.js](https://www.chartjs.org/) v4 + [vue-chartjs](https://vue-chartjs.org/) v5 — 콤보(막대+라인) 차트
- [ag-Grid](https://www.ag-grid.com/vue-data-grid/) (community, v31) — 하단 그리드, 컬럼 그룹(colspan)
- [dayjs](https://day.js.org/) — 최근 N개월 계산
