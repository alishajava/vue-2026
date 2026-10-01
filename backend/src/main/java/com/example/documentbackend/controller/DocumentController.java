package com.example.documentbackend.controller;

import com.example.documentbackend.dto.ConvertRequest;
import com.example.documentbackend.dto.DocumentRequest;
import com.example.documentbackend.dto.DocumentResponse;
import com.example.documentbackend.dto.FileResponse;
import com.example.documentbackend.entity.DocumentEntity;
import com.example.documentbackend.mapper.DocumentMapper;
import com.example.documentbackend.service.SlideRenderService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 프론트(src/api/documentApi.js)와 1:1로 맞춘 CRUD + 변환 엔드포인트.
// JPA(DocumentRepository) -> MyBatis(DocumentMapper)로 교체됐다. JpaRepository.save()처럼
// insert/update를 알아서 구분해주는 메서드가 없어서 컨트롤러에서 명시적으로 나눠 호출한다.
//
// PUT/DELETE를 막는 프레임워크/게이트웨이 환경이 있어서 전부 POST로 통일했다(REST
// 메서드 시맨틱이 꼭 필요한 공개 API가 아니라 내부 CRUD 화면이라 문제 없음). update는
// 기존 PUT과 같은 경로(POST /{id})를 그대로 쓰고, delete만 경로를 분리했다(POST
// /{id}로 같이 묶으면 update와 경로가 겹쳐 구분이 안 되므로 POST /{id}/delete).
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentMapper documentMapper;
    private final SlideRenderService slideRenderService;

    public DocumentController(DocumentMapper documentMapper, SlideRenderService slideRenderService) {
        this.documentMapper = documentMapper;
        this.slideRenderService = slideRenderService;
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentMapper.findAll().stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) {
        return DocumentResponse.from(findByIdOrThrow(id));
    }

    @GetMapping("/{id}/file")
    public FileResponse file(@PathVariable Long id) {
        DocumentEntity doc = findByIdOrThrow(id);
        return new FileResponse(Base64.getEncoder().encodeToString(doc.getFileData()));
    }

    @PostMapping
    public DocumentResponse create(@RequestBody DocumentRequest request) {
        DocumentEntity doc = new DocumentEntity();
        applyRequest(doc, request);
        doc.setRegisteredAt(LocalDateTime.now());
        documentMapper.insert(doc); // useGeneratedKeys가 insert 후 doc.id를 채워준다
        return DocumentResponse.from(doc);
    }

    @PostMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @RequestBody DocumentRequest request) {
        DocumentEntity doc = findByIdOrThrow(id);
        applyRequest(doc, request);
        doc.setId(id);
        doc.setUpdatedAt(LocalDateTime.now());
        // fileBase64가 없으면(파일을 새로 첨부하지 않은 경우) fileData를 null로 비워서,
        // DocumentMapper.xml의 <if test="fileData != null">가 file_data 컬럼을 건드리지
        // 않고 건너뛰게 한다 - 안 그러면 findByIdOrThrow로 읽어온 기존 바이트를 그대로
        // 다시 써넣게 되어(같은 값이긴 해도) 큰 파일마다 불필요한 쓰기가 발생한다.
        if (request.getFileBase64() == null || request.getFileBase64().isBlank()) {
            doc.setFileData(null);
        }
        documentMapper.update(doc);
        return DocumentResponse.from(doc);
    }

    @PostMapping("/{id}/delete")
    public void delete(@PathVariable Long id) {
        documentMapper.deleteById(id);
    }

    // 아직 저장 전(id 없음)인 파일을 즉석 변환 - 구버전 .ppt 미리보기용.
    @PostMapping("/convert")
    public List<String> convert(@RequestBody ConvertRequest request) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(request.getFileBase64());
        List<byte[]> slides = slideRenderService.renderToPng(new ByteArrayInputStream(bytes), request.getFileType());
        return toDataUrls(slides);
    }

    // 이미 저장된 문서를 변환 - 저장된 바이트를 그대로 쓴다.
    @GetMapping("/{id}/slides")
    public List<String> slides(@PathVariable Long id) throws IOException {
        DocumentEntity doc = findByIdOrThrow(id);
        List<byte[]> slides = slideRenderService.renderToPng(new ByteArrayInputStream(doc.getFileData()), doc.getFileType());
        return toDataUrls(slides);
    }

    // DocumentMapper.findById는 JpaRepository와 달리 Optional이 아니라 못 찾으면 null을
    // 그냥 반환한다 - 기존 documentRepository.findById(id).orElseThrow()와 동일하게
    // 동작하도록(못 찾으면 NoSuchElementException) 여기서 감싼다.
    private DocumentEntity findByIdOrThrow(Long id) {
        return Optional.ofNullable(documentMapper.findById(id)).orElseThrow();
    }

    private void applyRequest(DocumentEntity doc, DocumentRequest request) {
        doc.setTitle(request.getTitle());
        doc.setFileName(request.getFileName());
        doc.setFileType(request.getFileType());
        doc.setRegistrant(request.getRegistrant());
        doc.setHidden(request.isHidden());
        if (request.getFileBase64() != null && !request.getFileBase64().isBlank()) {
            doc.setFileData(Base64.getDecoder().decode(request.getFileBase64()));
        }
    }

    private List<String> toDataUrls(List<byte[]> slides) {
        return slides.stream()
                .map(bytes -> "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes))
                .toList();
    }
}
