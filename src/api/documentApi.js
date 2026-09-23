/**
 * 문서(ppt/pdf) CRUD API.
 * 스프링 백엔드의 /api/documents 리소스와 통신한다 (base64 text로 AJAX 송수신).
 * VITE_API_BASE_URL이 비어있으면 프론트와 같은 오리진을 그대로 사용한다.
 *
 * 목록(list)은 파일 바이너리 없이 메타데이터만 내려받는다 - 그리드를 그릴 때마다
 * 모든 행의 base64 파일 데이터를 통째로 실어 나르면 낭비이므로, 실제 파일은
 * fetchDocumentFile()로 필요한 순간(미리보기 팝업을 열 때)에만 따로 받는다.
 */
import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
})

// 목록: [{ id, title, fileName, fileType, registrant, registeredAt, hidden }, ...]
export async function listDocuments() {
  const { data } = await client.get('/api/documents')
  return data
}

export async function fetchDocumentMeta(id) {
  const { data } = await client.get(`/api/documents/${id}`)
  return data
}

export async function fetchDocumentFile(id) {
  const { data } = await client.get(`/api/documents/${id}/file`)
  return data.fileBase64
}

// payload: { title, fileName, fileType, fileBase64, registrant, hidden }
export async function createDocument(payload) {
  const { data } = await client.post('/api/documents', payload)
  return data
}

// payload는 createDocument와 동일한 형태. fileBase64를 생략하면 기존 파일을 유지한다.
export async function updateDocument(id, payload) {
  const { data } = await client.put(`/api/documents/${id}`, payload)
  return data
}

export async function deleteDocument(id) {
  await client.delete(`/api/documents/${id}`)
}

// --- DocumentPreviewPage(단일 파일 미리보기)용 - 최소 필드만 저장/조회하는 얇은 래퍼 ---

export async function saveDocument({ fileName, fileBase64 }) {
  const doc = await createDocument({ fileName, fileBase64 })
  return doc.id
}

export async function fetchDocument(id) {
  const [meta, fileBase64] = await Promise.all([fetchDocumentMeta(id), fetchDocumentFile(id)])
  return { fileName: meta.fileName, fileBase64 }
}
