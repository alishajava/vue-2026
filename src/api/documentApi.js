/**
 * 문서(ppt/pdf) 업로드/조회 API.
 * 스프링 백엔드의 /api/documents 엔드포인트와 통신한다 (base64 text로 AJAX 송수신).
 * VITE_API_BASE_URL이 비어있으면 프론트와 같은 오리진을 그대로 사용한다.
 */
import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
})

export async function saveDocument({ fileName, fileBase64 }) {
  const { data } = await client.post('/api/documents', { fileName, fileBase64 })
  return data // documentId
}

export async function fetchDocument(id) {
  const { data } = await client.get(`/api/documents/${id}`)
  return data // { fileName, fileBase64 }
}
