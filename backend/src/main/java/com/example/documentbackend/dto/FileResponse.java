package com.example.documentbackend.dto;

import lombok.Getter;
import lombok.Setter;

// GET /api/documents/{id}/file 응답.
@Getter
@Setter
public class FileResponse {
    private String fileBase64;

    public FileResponse(String fileBase64) {
        this.fileBase64 = fileBase64;
    }
}
