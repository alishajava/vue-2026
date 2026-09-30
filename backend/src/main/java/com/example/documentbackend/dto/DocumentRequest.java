package com.example.documentbackend.dto;

// POST /api/documents, PUT /api/documents/{id} 요청 바디.
// 프론트의 documentApi.js가 보내는 { title, fileName, fileType, fileBase64, registrant, hidden }와 1:1 대응.
public class DocumentRequest {
    private String title;
    private String fileName;
    private String fileType;
    private String fileBase64; // update에서는 생략 가능(생략하면 기존 파일 유지)
    private String registrant;
    private boolean hidden;

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

    public String getFileBase64() {
        return fileBase64;
    }

    public void setFileBase64(String fileBase64) {
        this.fileBase64 = fileBase64;
    }

    public String getRegistrant() {
        return registrant;
    }

    public void setRegistrant(String registrant) {
        this.registrant = registrant;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
