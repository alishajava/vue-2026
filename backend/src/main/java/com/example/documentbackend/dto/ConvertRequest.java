package com.example.documentbackend.dto;

import lombok.Getter;
import lombok.Setter;

// POST /api/documents/convert 요청 바디. 아직 저장 전인 파일을 즉석 변환할 때 쓴다.
@Getter
@Setter
public class ConvertRequest {
    private String fileBase64;
    private String fileType; // 'ppt' | 'pptx'
}
