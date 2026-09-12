/**
 * 결정적(deterministic) 의사난수 유틸리티.
 *
 * 실제 서비스에서는 API에서 받아오는 값으로 교체되어야 하지만, 화면 개발/데모 단계에서
 * "새로고침할 때마다 값이 바뀌는" 진짜 Math.random() 대신, 같은 key(년도+공장+부품 등)를
 * 넣으면 항상 같은 값을 돌려주는 시드 기반 난수를 사용한다.
 *
 * 이렇게 하면 필터를 바꿨다가 다시 원래 값으로 돌려도 그래프/그리드 값이 흔들리지 않는다.
 */

/** 문자열 -> 32bit 정수 해시 (djb2 변형) */
function hashString(str) {
  let hash = 5381
  for (let i = 0; i < str.length; i += 1) {
    hash = (hash * 33) ^ str.charCodeAt(i)
  }
  return hash >>> 0
}

/** mulberry32 PRNG - seed(정수) 하나로 0~1 사이 실수 스트림을 만든다 */
function mulberry32(seed) {
  let a = seed
  return function next() {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * key 문자열에 대해 결정적인 [min, max] 범위의 난수를 반환한다.
 * @param {string} key - 값을 결정하는 고유 키 (예: '2026-factory1-partA-3')
 * @param {number} min
 * @param {number} max
 * @param {number} [decimals=0] - 소수 자릿수
 */
export function seededRange(key, min, max, decimals = 0) {
  const rng = mulberry32(hashString(key))
  const value = min + rng() * (max - min)
  const factor = 10 ** decimals
  return Math.round(value * factor) / factor
}
