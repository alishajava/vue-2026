package com.example.documentbackend.controller;

import com.example.documentbackend.dto.ConvertRequest;
import com.example.documentbackend.dto.DocumentRequest;
import com.example.documentbackend.dto.DocumentResponse;
import com.example.documentbackend.dto.FileResponse;
import com.example.documentbackend.entity.DocumentEntity;
import com.example.documentbackend.repository.DocumentRepository;
import com.example.documentbackend.service.SlideRenderService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 프론트(src/api/documentApi.js)와 1:1로 맞춘 CRUD + 변환 엔드포인트.
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final SlideRenderService slideRenderService;

    public DocumentController(DocumentRepository documentRepository, SlideRenderService slideRenderService) {
        this.documentRepository = documentRepository;
        this.slideRenderService = slideRenderService;
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentRepository.findAll().stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) {
        return DocumentResponse.from(documentRepository.findById(id).orElseThrow());
    }

    @GetMapping("/{id}/file")
    public FileResponse file(@PathVariable Long id) {
        DocumentEntity doc = documentRepository.findById(id).orElseThrow();
        return new FileResponse(Base64.getEncoder().encodeToString(doc.getFileData()));
    }

    @PostMapping
    public DocumentResponse create(@RequestBody DocumentRequest request) {
        DocumentEntity doc = new DocumentEntity();
        applyRequest(doc, request);
        doc.setRegisteredAt(LocalDateTime.now());
        return DocumentResponse.from(documentRepository.save(doc));
    }

    @PutMapping("/{id}")
    public DocumentResponse update(@PathVariable Long id, @RequestBody DocumentRequest request) {
        DocumentEntity doc = documentRepository.findById(id).orElseThrow();
        applyRequest(doc, request);
        doc.setUpdatedAt(LocalDateTime.now());
        return DocumentResponse.from(documentRepository.save(doc));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        documentRepository.deleteById(id);
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
        DocumentEntity doc = documentRepository.findById(id).orElseThrow();
        List<byte[]> slides = slideRenderService.renderToPng(new ByteArrayInputStream(doc.getFileData()), doc.getFileType());
        return toDataUrls(slides);
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
