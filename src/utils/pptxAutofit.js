/**
 * pptx-preview는 PowerPoint가 저장 시점에 계산해둔 "텍스트 자동 축소"(도형 크기에 맞춰
 * 줄이기, XML의 <a:normAutofit fontScale="...">) 값을 그대로 읽어서 적용한다. 문제는
 * 이 fontScale이 원본 폰트(예: 회사 전용 폰트)의 실제 글자 폭을 기준으로 파워포인트가
 * 계산해 파일에 박아둔 고정값이라는 것 - 그 폰트가 설치 안 된 브라우저에서 대체 폰트로
 * 렌더링하면(대체 폰트가 조금이라도 더 넓으면) 같은 축소 비율로는 부족해서 원래 한 줄로
 * 끝나야 할 텍스트의 끝부분이 다음 줄로 넘어가 보인다.
 *
 * [왜 "도형 높이 초과" 검사만으로는 부족한가]
 * 처음엔 도형(.shape-wrapper) 전체의 실제 텍스트 높이가 도형에 정의된 높이를 넘는지만
 * 봤는데, 실제 사용자 파일로는 여전히 줄바꿈이 재현됐다. 제목 같은 placeholder는 보통
 * 텍스트 한 줄보다 도형 자체 높이가 훨씬 여유 있게 잡혀 있어서(세로 정렬/여백 때문에),
 * 대체 폰트 때문에 한 줄이 두 줄로 넘어가도 그 두 줄이 도형 높이 안에 충분히 들어가
 * "넘친다"는 신호 자체가 안 떴던 것. 그래서 문단(<p>) 단위로 "원래 줄바꿈이 없어야
 * 하는 문단(<br>이 없는 문단)이 실제로 두 줄 이상으로 렌더링됐는지"를 Range.getClientRects()
 * 로 직접 세어 판단하는 걸 추가했다 - 이러면 도형에 여유 공간이 있어도 "원래 한 줄일
 * 문단이 지금 몇 줄로 보이는가"를 정확히 알 수 있다. <br>이 있는 문단(의도된 줄바꿈)은
 * 이 판정에서 제외한다.
 *
 * pptx-preview 렌더링이 끝난 뒤, 각 도형(.shape-wrapper) 안의 문단들을 검사해서 위 두
 * 조건(도형 높이 초과 / 의도치 않은 줄바꿈) 중 하나라도 해당하면 그 안 텍스트의
 * font-size를 조금씩 더 줄여나간다 - 폰트 파일 자체가 없어도 적용 가능한 우회책이다.
 */
const MAX_ITERATIONS = 12
const SHRINK_STEP = 0.95 // 파워포인트도 한 번에 10%씩 줄이는 식으로 반복 축소한다 - 유사한 정도로 완만하게
const MIN_FONT_SIZE = 6
// "문단이 줄바꿈됐다"는 신호로 몇 번이나 줄여볼지의 상한. 원래 여러 줄일 수밖에 없는
// 본문 문단(예: 작은 텍스트박스에 긴 글)은 아무리 줄여도 줄 수가 안 줄기 때문에,
// 이 횟수 안에 줄 수가 전혀 안 줄면 "줄인다고 해결될 문제가 아니다"로 보고 그 문단은
// 포기한다(도형 높이 초과 쪽 검사는 별개로 계속 동작한다).
const WRAP_GIVEUP_AFTER = 4

export function applyOverflowAutofit(container) {
  if (!container) return
  container.querySelectorAll('.shape-wrapper').forEach(shrinkShapeIfOverflowing)
}

function shrinkShapeIfOverflowing(shapeEl) {
  const textWrapper = shapeEl.querySelector(':scope > .text-wrapper')
  if (!textWrapper) return

  const sizedEls = textWrapper.querySelectorAll('[style*="font-size"]')
  if (sizedEls.length === 0) return

  // 도형에 정의된 원래 높이(px) - pptx-preview가 인라인 style로 직접 넣어준다. 없는
  // 도형(예: 텍스트 없는 도형)도 있으니 0이면 이 조건은 그냥 무시한다.
  const boxHeight = parseFloat(shapeEl.style.height) || 0

  // <br>(의도된 줄바꿈)이 있는 문단은 원래부터 여러 줄이 맞으므로 애초에 검사 대상에서
  // 뺀다.
  const candidates = [...textWrapper.querySelectorAll('p')].filter((p) => !p.querySelector('br'))

  let guard = 0
  while (guard < MAX_ITERATIONS) {
    const overflow = boxHeight > 0 && textWrapper.scrollHeight > boxHeight + 1
    // 줄바꿈 신호는 WRAP_GIVEUP_AFTER번까지만 반영한다. 글자 수가 많아 원래부터 여러
    // 줄일 수밖에 없는 본문 문단은 아무리 줄여도 1줄이 안 되는데, 그걸 계속 "아직도
    // 줄바꿈됨"으로 보고 끝까지 줄이면 본문 글자가 필요 이상으로 작아진다 - 반면 이번에
    // 고치려는 버그(원래 한 줄이어야 할 제목이 대체 폰트 때문에 살짝 넘침)는 몇 번만
    // 줄여도 금방 한 줄로 돌아온다(실측 3회 안에 해결됨 확인). 그 차이를 이용해서,
    // 일정 횟수 넘게 줄여도 안 풀리는 줄바꿈은 "줄인다고 해결될 문제가 아니다"로 보고
    // 포기한다(도형 높이 초과 쪽 검사는 이 상한과 무관하게 계속 동작한다).
    const wrapSignalActive = guard < WRAP_GIVEUP_AFTER
    const stillWrapped = wrapSignalActive && candidates.some((p) => countRenderedLines(p) > 1)
    if (!overflow && !stillWrapped) break

    let shrunkAny = false
    sizedEls.forEach((el) => {
      const current = parseFloat(el.style.fontSize)
      if (current && current > MIN_FONT_SIZE) {
        el.style.fontSize = Math.max(current * SHRINK_STEP, MIN_FONT_SIZE) + 'px'
        shrunkAny = true
      }
    })
    if (!shrunkAny) break // 이미 다 MIN_FONT_SIZE까지 줄었으면 더 반복해도 의미 없다
    guard++
  }
}

// 블록 요소(<p>) 자체의 getClientRects()는 줄 수와 무관하게 보통 사각형 1개만 준다 -
// 안쪽 텍스트가 실제로 몇 줄로 쪼개져 렌더링됐는지 보려면 Range로 안쪽 콘텐츠를 감싸서
// 재야 한다(인라인 콘텐츠는 줄마다 별도의 사각형을 반환한다).
//
// 다만 Range.getClientRects()는 내용이 한 줄로 끝나는 경우에도 위치가 사실상 동일한
// 사각형을 중복으로 2개 반환하는 경우가 있다(경계 지점 처리 관련 브라우저 특성 -
// 실제 사용자 파일로 확인됨: 한 줄짜리 제목인데도 거의 똑같은 좌표의 사각형 2개가
// 나와서 "2줄로 줄바꿈됨"으로 잘못 판정되는 버그가 있었다). top 좌표를 반올림해서
// 같은 줄의 중복 사각형은 하나로 묶고, 진짜 다른 줄(= top이 확연히 다른 사각형)만
// 센다.
function countRenderedLines(el) {
  const range = document.createRange()
  range.selectNodeContents(el)
  const tops = new Set()
  for (const rect of range.getClientRects()) {
    if (rect.width < 1 || rect.height < 1) continue // 빈 문단 등에서 나오는 축퇴 사각형 무시
    tops.add(Math.round(rect.top))
  }
  return tops.size
}
