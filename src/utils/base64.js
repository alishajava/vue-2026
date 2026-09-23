/**
 * ArrayBuffer <-> base64 변환.
 * btoa/atob를 문자열 루프로 직접 구현하지 않고 FileReader/fetch의 네이티브 base64
 * 인코더/디코더를 사용한다 - 큰 파일(수십 MB ppt)에서도 콜스택/문자열 길이 제한 없이 안전하다.
 */

export function arrayBufferToBase64(buffer) {
  return new Promise((resolve, reject) => {
    const blob = new Blob([buffer])
    const reader = new FileReader()
    reader.onload = () => {
      // readAsDataURL 결과: "data:application/octet-stream;base64,AAAA..."
      const dataUrl = reader.result
      resolve(dataUrl.slice(dataUrl.indexOf(',') + 1))
    }
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}

export async function base64ToArrayBuffer(base64) {
  const res = await fetch(`data:application/octet-stream;base64,${base64}`)
  return res.arrayBuffer()
}
