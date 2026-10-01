package com.example.documentbackend.dto;

import com.example.documentbackend.entity.DocumentEntity;
import java.time.LocalDateTime;

// 목록/단건 조회 응답. 파일 바이너리는 절대 담지 않는다(따로 /file, /slides로 조회).
public class DocumentResponse {
    private Long id;
    private String title;
    private String fileName;
    private String fileType;
    private String registrant;
    private LocalDateTime registeredAt;
    private String referenceMonth;
    private boolean hidden;

    public static DocumentResponse from(DocumentEntity entity) {
        DocumentResponse response = new DocumentResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setFileName(entity.getFileName());
        response.setFileType(entity.getFileType());
        response.setRegistrant(entity.getRegistrant());
        response.setRegisteredAt(entity.getRegisteredAt());
        response.setReferenceMonth(entity.getReferenceMonth());
        response.setHidden(entity.isHidden());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getRegistrant() {
        return registrant;
    }

    public void setRegistrant(String registrant) {
        this.registrant = registrant;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getReferenceMonth() {
        return referenceMonth;
    }

    public void setReferenceMonth(String referenceMonth) {
        this.referenceMonth = referenceMonth;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
