package com.example.documentbackend.dto;

// POST /api/documents/convert 요청 바디. 아직 저장 전인 파일을 즉석 변환할 때 쓴다.
public class ConvertRequest {
    private String fileBase64;
    private String fileType; // 'ppt' | 'pptx'

    public String getFileBase64() {
        return fileBase64;
    }

    public void setFileBase64(String fileBase64) {
        this.fileBase64 = fileBase64;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
