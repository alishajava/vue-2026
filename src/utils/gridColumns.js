/**
 * 하단 '부품별 라인 현황' 그리드의 컬럼 정의를 만드는 유틸.
 * 부품A/부품B 그리드 두 곳에서 동일한 구조(라인 / 목표 / 가동율 그룹)를 재사용한다.
 */
import { getRecentMonths } from '../mock/productionData'
import { UTILIZATION_STATUS_COLORS, hexToRgb } from '../charts/chartTheme'

const numberFormatter = new Intl.NumberFormat('ko-KR')

/**
 * 가동율 값에 따라 배경색을 다르게 준다 (양호/주의/위험).
 * 색상은 보조 채널일 뿐이며, 숫자(%) 값 자체가 항상 함께 표시되므로 색상에만
 * 의존하지 않는다. 텍스트는 항상 기본 잉크색을 유지해 대비를 충분히 확보한다.
 */
function utilizationCellStyle(params) {
  const value = params.value
  if (value == null) return null

  let backgroundColor
  if (value >= 90) {
    backgroundColor = `rgba(${hexToRgb(UTILIZATION_STATUS_COLORS.good)}, 0.16)`
  } else if (value >= 80) {
    backgroundColor = `rgba(${hexToRgb(UTILIZATION_STATUS_COLORS.warning)}, 0.2)`
  } else {
    backgroundColor = `rgba(${hexToRgb(UTILIZATION_STATUS_COLORS.critical)}, 0.16)`
  }

  return {
    backgroundColor,
    color: '#0b0b0b',
    fontWeight: 600,
    textAlign: 'right',
  }
}

/**
 * @returns ag-grid columnDefs: 라인 / 목표 / 가동율(그룹, 최근 3개월 하위 컬럼 colspan)
 */
export function buildLineGridColumns() {
  const recentMonths = getRecentMonths(3)

  return [
    {
      headerName: '라인',
      field: 'lineName',
      flex: 1.2,
      minWidth: 90,
      cellStyle: { fontWeight: 600 },
    },
    {
      headerName: '목표',
      field: 'target',
      flex: 1.2,
      minWidth: 90,
      type: 'rightAligned',
      valueFormatter: (p) => (p.value == null ? '' : `${numberFormatter.format(p.value)}개`),
    },
    {
      // children을 지정하면 ag-grid가 '가동율' 헤더를 하위 3개 컬럼 위에 자동으로
      // colspan 처리해서 그룹 헤더로 렌더링한다.
      headerName: '가동율',
      headerClass: 'util-group-header',
      children: recentMonths.map((rm) => ({
        headerName: rm.label,
        field: `util_${rm.key}`,
        flex: 1,
        minWidth: 80,
        type: 'rightAligned',
        valueFormatter: (p) => (p.value == null ? '' : `${p.value}%`),
        cellStyle: utilizationCellStyle,
      })),
    },
  ]
}
