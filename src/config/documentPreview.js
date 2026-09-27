// pptx를 어떻게 미리보기 렌더링할지 정하는 스위치.
//
// false (현재값): DocumentSlideViewerModal.vue에 그대로 남아있는 기존 pptx-preview
// 기반 클라이언트 렌더링 로직을 쓴다. 한때 POI 서버 변환(true)으로 옮겼었지만,
// POI가 네이티브 차트/SmartArt를 아예 못 그리고, 표 안 텍스트 처리·폰트 대체·
// 줄간격 계산에서 계속 버그가 나와서(이번 세션에서 다 고쳤지만) 결국 pptx-preview
// 쪽이 더 낫다고 판단해 되돌렸다. ppt(구버전)는 브라우저에서 파싱 자체가 안 되므로
// 이 플래그와 무관하게 항상 서버 변환을 쓴다.
//
// true로 바꾸면 .ppt와 똑같이 서버(Apache POI)가 슬라이드를 PNG로 변환해서
// 내려주는 방식으로 다시 전환된다 - 필요해지면 언제든 되돌릴 수 있도록 그 코드도
// 지우지 않고 남겨뒀다.
export const PPTX_USE_SERVER_CONVERSION = false
