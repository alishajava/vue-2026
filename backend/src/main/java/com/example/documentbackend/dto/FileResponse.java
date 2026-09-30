package com.example.documentbackend.dto;

// GET /api/documents/{id}/file 응답.
public class FileResponse {
    private String fileBase64;

    public FileResponse(String fileBase64) {
        this.fileBase64 = fileBase64;
    }

    public String getFileBase64() {
        return fileBase64;
    }

    public void setFileBase64(String fileBase64) {
        this.fileBase64 = fileBase64;
    }
}
