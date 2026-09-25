package com.example.documentbackend.dto;

import lombok.Getter;
import lombok.Setter;

// POST /api/documents, PUT /api/documents/{id} 요청 바디.
// 프론트의 documentApi.js가 보내는 { title, fileName, fileType, fileBase64, registrant, hidden }와 1:1 대응.
@Getter
@Setter
public class DocumentRequest {
    private String title;
    private String fileName;
    private String fileType;
    private String fileBase64; // update에서는 생략 가능(생략하면 기존 파일 유지)
    private String registrant;
    private boolean hidden;
}
