/**
 * 파워포인트는 "머리글/바닥글 삽입"을 켜지 않는 한 날짜(dt)/바닥글(ftr)/슬라이드번호
 * (sldNum) 자리표시자를 화면에 그리지 않는다(값은 XML에 남아있을 뿐). 그런데
 * pptx-preview 라이브러리는 이 타입 구분 없이 슬라이드에 있는 도형을 전부 그려버려서,
 * 실제 파워포인트에서는 안 보이거나 작게 깔리는 자리표시자 텍스트(예시 날짜/제목,
 * 슬라이드 번호 등)가 화면에 찍힌다. 렌더링 전에 이 세 타입의 자리표시자 도형(<p:sp>)만
 * 슬라이드 XML에서 미리 잘라내서 우회한다. (DocumentSlideViewerModal의 본문 미리보기와
 * FileAttachCell의 썸네일이 공용으로 쓴다.)
 *
 * 예전엔 <p:sp>...</p:sp>를 정규식으로 찾아 지웠는데, 회사 템플릿처럼 로고/날짜/
 * 슬라이드번호를 그룹 도형(<p:grpSp>)으로 묶어둔 경우 정규식이 그룹 중첩 구조를
 * 제대로 못 따라가 못 지우는 문제가 있었다. DOMParser로 실제 XML 트리를 타고 들어가
 * <p:ph type="ftr|dt|sldNum">을 가진 <p:sp>만 정확히 찾아 제거한다(그룹의 다른
 * 도형은 그대로 둔다).
 */
export async function stripFooterPlaceholders(buffer) {
  try {
    const { default: JSZip } = await import('jszip')
    const zip = await JSZip.loadAsync(buffer.slice(0))
    const slideFiles = Object.keys(zip.files).filter((name) => /^ppt\/slides\/slide\d+\.xml$/.test(name))
    const parser = new DOMParser()
    const serializer = new XMLSerializer()
    for (const path of slideFiles) {
      const xml = await zip.file(path).async('string')
      const doc = parser.parseFromString(xml, 'application/xml')
      if (doc.getElementsByTagName('parsererror').length > 0) continue // 파싱 실패 시 해당 슬라이드는 원본 그대로 둔다.

      const shapes = Array.from(doc.getElementsByTagName('p:sp'))
      for (const shape of shapes) {
        const ph = shape.getElementsByTagName('p:ph')[0]
        const type = ph?.getAttribute('type')
        if (type === 'ftr' || type === 'dt' || type === 'sldNum') {
          shape.parentNode?.removeChild(shape)
        }
      }

      // XMLSerializer는 <?xml ...?> 선언을 안 붙여주므로, 원본에 있었다면 그대로 되살린다.
      const declaration = xml.match(/^<\?xml[^?]*\?>/)?.[0] ?? ''
      zip.file(path, declaration + serializer.serializeToString(doc))
    }
    return await zip.generateAsync({ type: 'arraybuffer' })
  } catch (err) {
    console.error('머리글/바닥글 자리표시자 제거 실패 - 원본 그대로 사용합니다', err)
    return buffer
  }
}
