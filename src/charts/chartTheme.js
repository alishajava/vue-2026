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
 * 하단 라인 현황 그리드의 가동율 배지 배색 (양호/주의/위험).
 * dataviz 가이드의 "status palette" (검증된 고정 상태 색상: good/warning/
 * critical)를 뼈대로 하되, 셀 전체를 칠하는 대신 Notion/Linear류의 "칩(pill
 * badge)"로 표시한다 - 옅은 배경 + 진한 텍스트 조합. 텍스트 색은 각 배경 위에서
 * WCAG 4.5:1 이상(validate_palette.js의 contrast()로 확인: 4.71 / 4.88 / 5.67)을
 * 만족하도록 골랐다. 이 색은 SERIES_COLORS(categorical, 부품/계열 구분용)와는
 * 별도로 예약된 색이라 - 상태 색을 계열 색과 겹쳐 쓰면 "이 색이 부품을 가리키는지
 * 상태를 가리키는지" 헷갈리게 된다.
 */
export const UTILIZATION_BADGE_COLORS = {
  good: { background: '#eafbea', text: '#1a7f37' },
  warning: { background: '#fef6e6', text: '#946200' },
  critical: { background: '#fdecec', text: '#b3281f' },
}

/** 가동율 값(%) -> 상태 등급. 배지 색상과 임계값(90/80)을 한 곳에서 관리한다. */
export function getUtilizationStatus(value) {
  if (value >= 90) return 'good'
  if (value >= 80) return 'warning'
  return 'critical'
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
