/**
 * 차트 공통 색상/스타일 토큰.
 * dataviz 가이드의 검증된 categorical 팔레트(1:blue, 2:orange, 3:aqua) 순서를 그대로 사용한다.
 * - 실적(막대)과 목표(점선)는 "같은 개체"이므로 같은 색상 계열을 공유하고,
 *   실선/막대 vs 점선으로만 구분한다 (색상은 개체를 따르고, 목표/실적 구분은 선 스타일로).
 */
export const SERIES_COLORS = {
  partA: '#2a78d6', // categorical slot 1 - blue
  partB: '#eb6834', // categorical slot 2 - orange
  total: '#1baf7a', // categorical slot 3 - aqua
}

export const CHART_TEXT = {
  primary: '#0b0b0b',
  secondary: '#52514e',
  muted: '#898781',
  grid: '#e1e0d9',
}

export const TARGET_DASH = [6, 4]

/**
 * 하단 라인 현황 그리드의 가동율 셀 배색 (양호/주의/위험).
 * 기존의 원색 신호등 배색(순수 red/green/yellow) 대신, 위 categorical 팔레트와
 * 같은 톤 계열의 차분한 색으로 맞춰 전체 디자인과 어울리게 한다.
 */
export const UTILIZATION_STATUS_COLORS = {
  good: '#1baf7a', // SERIES_COLORS.total과 동일 계열 (aqua-green)
  warning: '#c98a1f', // 차분한 amber
  critical: '#c4574a', // 차분한 terracotta
}

/** hex -> "r, g, b" 문자열 (막대 배경색 투명도 조절용) */
export function hexToRgb(hex) {
  const parsed = hex.replace('#', '')
  const bigint = parseInt(parsed, 16)
  const r = (bigint >> 16) & 255
  const g = (bigint >> 8) & 255
  const b = bigint & 255
  return `${r}, ${g}, ${b}`
}
