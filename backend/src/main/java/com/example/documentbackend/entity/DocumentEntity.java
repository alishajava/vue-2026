package com.example.documentbackend.entity;

import java.time.LocalDateTime;

// MyBatis는 JPA와 달리 이 클래스를 보고 테이블을 만들거나 컬럼 타입을 추론하지 않는다 -
// 그냥 SQL 조회 결과를 담는 순수 데이터 객체(POJO)일 뿐이고, 실제 컬럼 타입/길이는
// resources/schema.sql에서 DDL로 직접 관리한다.
public class DocumentEntity {

    private Long id;

    private String title;

    private String fileName;

    private String fileType; // 'pptx' | 'pdf' | 'ppt'

    private byte[] fileData;

    private String registrant;

    private LocalDateTime registeredAt;

    private String referenceMonth; // 사용자가 직접 고르는 "YYYY-MM" 기준년월. 등록일시와 별개.

    private String updatedBy;

    private LocalDateTime updatedAt;

    private boolean hidden;

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

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
