/**
 * 공장 생산품 실적 mock 데이터.
 *
 * 실제 서비스 연동 시에는 이 파일의 함수(getChartData, getLineGridRows 등)의 시그니처만
 * 유지한 채 내부 구현을 API 호출로 바꿔 끼우면 된다. 컴포넌트들은 이 파일이 export하는
 * 함수/상수만 알고 있으므로 데이터 소스 교체에 영향을 받지 않는다.
 *
 * [설계 가정]
 * - '부품별 라인'은 부품을 생산하는 "생산라인" 구분을 의미한다 (예: 1라인, 2라인).
 * - '가동율'은 실적/목표로 계산하는 값이 아니라, 설비 가동 현황을 나타내는 별도의
 *   데이터 필드로 가정한다. 상단 차트의 '년도' 필터와 무관하게, 실제 현재 시점 기준
 *   "최근 3개월"의 가동율을 보여준다 (설비 가동율은 과거 연도를 조회 중이어도 항상
 *   최신 상태를 보여주는 것이 실무적으로 자연스럽다고 가정).
 */
import dayjs from 'dayjs'
import { seededRange } from './seededRandom'

export const YEARS = [2024, 2025, 2026]

export const FACTORIES = [
  { code: 'F1', name: '1공장' },
  { code: 'F2', name: '2공장' },
  { code: 'F3', name: '3공장' },
]

export const PARTS = [
  { code: 'A', name: '부품A' },
  { code: 'B', name: '부품B' },
]

export const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1)

const LINES_BY_PART = {
  A: [
    { id: 'A-L1', name: '1라인' },
    { id: 'A-L2', name: '2라인' },
  ],
  B: [
    { id: 'B-L1', name: '1라인' },
    { id: 'B-L2', name: '2라인' },
    { id: 'B-L3', name: '3라인' },
  ],
}

/** 부품 코드로 생산라인 목록 조회 */
export function getLines(partCode) {
  return LINES_BY_PART[partCode] ?? []
}

function getMonthlyTarget(year, factoryCode, partCode, month) {
  const base = seededRange(`target-base-${year}-${factoryCode}-${partCode}`, 800, 1400, 0)
  const seasonal = seededRange(`target-season-${year}-${factoryCode}-${partCode}-${month}`, -60, 60, 0)
  return Math.max(0, Math.round(base + seasonal))
}

function getMonthlyActual(year, factoryCode, partCode, month) {
  const target = getMonthlyTarget(year, factoryCode, partCode, month)
  // 목표 대비 -18% ~ +12% 사이에서 흔들리는 실적치 (미달/초과 케이스가 섞이도록)
  const variance = seededRange(`actual-${year}-${factoryCode}-${partCode}-${month}`, -0.18, 0.12, 3)
  return Math.max(0, Math.round(target * (1 + variance)))
}

/**
 * 상단 콤보 차트용 데이터.
 * @returns {{ months:number[], partA:{actual:number[],target:number[]}, partB:{actual:number[],target:number[]}, total:{actual:number[],target:number[]} }}
 */
export function getChartData(year, factoryCode) {
  const months = MONTHS
  const partA = { actual: [], target: [] }
  const partB = { actual: [], target: [] }

  months.forEach((month) => {
    partA.actual.push(getMonthlyActual(year, factoryCode, 'A', month))
    partA.target.push(getMonthlyTarget(year, factoryCode, 'A', month))
    partB.actual.push(getMonthlyActual(year, factoryCode, 'B', month))
    partB.target.push(getMonthlyTarget(year, factoryCode, 'B', month))
  })

  const total = {
    actual: months.map((_, i) => partA.actual[i] + partB.actual[i]),
    target: months.map((_, i) => partA.target[i] + partB.target[i]),
  }

  return { months, partA, partB, total }
}

/**
 * 실제 "오늘" 기준 최근 N개월 정보를 반환한다 (선택된 년도 필터와 무관).
 * @returns {{key:string, year:number, month:number, label:string}[]} 과거->현재 순
 */
export function getRecentMonths(count = 3) {
  const now = dayjs()
  return Array.from({ length: count }, (_, i) => now.subtract(count - 1 - i, 'month')).map((d) => ({
    key: d.format('YYYY-MM'),
    year: d.year(),
    month: d.month() + 1,
    label: `${d.format('YY')}.${d.month() + 1}월`,
  }))
}

/**
 * 하단 그리드(부품별 라인 현황)용 row 데이터.
 * 각 row: { lineId, lineName, target, util_YYYY-MM, util_YYYY-MM, util_YYYY-MM }
 */
export function getLineGridRows(year, factoryCode, partCode) {
  const lines = getLines(partCode)
  const recentMonths = getRecentMonths(3)
  const currentMonth = dayjs().month() + 1
  const currentMonthTarget = getMonthlyTarget(year, factoryCode, partCode, currentMonth)

  return lines.map((line) => {
    const shareBase = 1 / lines.length
    const share = seededRange(`line-share-${factoryCode}-${partCode}-${line.id}`, shareBase * 0.8, shareBase * 1.2, 3)

    const row = {
      lineId: line.id,
      lineName: line.name,
      target: Math.round(currentMonthTarget * share),
    }

    recentMonths.forEach((rm) => {
      row[`util_${rm.key}`] = seededRange(
        `util-${factoryCode}-${partCode}-${line.id}-${rm.key}`,
        72,
        99,
        1,
      )
    })

    return row
  })
}
