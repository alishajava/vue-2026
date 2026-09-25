package com.example.documentbackend.dto;

import com.example.documentbackend.entity.DocumentEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 목록/단건 조회 응답. 파일 바이너리는 절대 담지 않는다(따로 /file, /slides로 조회).
@Getter
@Setter
public class DocumentResponse {
    private Long id;
    private String title;
    private String fileName;
    private String fileType;
    private String registrant;
    private LocalDateTime registeredAt;
    private boolean hidden;

    public static DocumentResponse from(DocumentEntity entity) {
        DocumentResponse response = new DocumentResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setFileName(entity.getFileName());
        response.setFileType(entity.getFileType());
        response.setRegistrant(entity.getRegistrant());
        response.setRegisteredAt(entity.getRegisteredAt());
        response.setHidden(entity.isHidden());
        return response;
    }
}
