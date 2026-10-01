import JSZip from 'jszip'

/**
 * 파워포인트는 "머리글/바닥글 삽입"을 켜지 않는 한 날짜(dt)/바닥글(ftr)/슬라이드번호
 * (sldNum) 자리표시자를 화면에 그리지 않는다(값은 XML에 남아있을 뿐). 그런데
 * pptx-preview 라이브러리는 이 타입 구분 없이 슬라이드에 있는 도형을 전부 그려버려서,
 * 실제 파워포인트에서는 안 보이거나 작게 깔리는 자리표시자 텍스트(예시 날짜/제목,
 * 슬라이드 번호 등)가 화면에 찍힌다. 렌더링 전에 이 세 타입의 자리표시자 도형(<p:sp>)만
 * 슬라이드 XML에서 미리 잘라내서 우회한다. (DocumentSlideViewerModal의 본문 미리보기와
 * FileAttachCell의 썸네일이 공용으로 쓴다.)
 */
export async function stripFooterPlaceholders(buffer) {
  try {
    const zip = await JSZip.loadAsync(buffer.slice(0))
    const slideFiles = Object.keys(zip.files).filter((name) => /^ppt\/slides\/slide\d+\.xml$/.test(name))
    for (const path of slideFiles) {
      let xml = await zip.file(path).async('string')
      xml = xml.replace(/<p:sp>.*?<\/p:sp>/gs, (match) =>
        /<p:ph type="(?:ftr|dt|sldNum)"/.test(match) ? '' : match,
      )
      zip.file(path, xml)
    }
    return await zip.generateAsync({ type: 'arraybuffer' })
  } catch (err) {
    console.error('머리글/바닥글 자리표시자 제거 실패 - 원본 그대로 사용합니다', err)
    return buffer
  }
}
