// 로그인 연동이 없는 상태라, 실제 서비스에선 로그인 처리 쪽에서 세션/토큰에
// 사용자 아이디를 심어두고 이 함수가 그걸 읽어오게 바꿔야 한다.
// (지금은 sessionStorage의 'userId' 키를 그 값이 들어올 자리로 대신 쓴다.)
const SESSION_USER_ID_KEY = 'userId'

export function getCurrentUserId() {
  try {
    return sessionStorage.getItem(SESSION_USER_ID_KEY) ?? ''
  } catch {
    return ''
  }
}
