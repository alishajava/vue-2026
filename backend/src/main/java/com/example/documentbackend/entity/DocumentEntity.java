package com.example.documentbackend.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// MyBatis는 JPA와 달리 이 클래스를 보고 테이블을 만들거나 컬럼 타입을 추론하지 않는다 -
// 그냥 SQL 조회 결과를 담는 순수 데이터 객체(POJO)일 뿐이고, 실제 컬럼 타입/길이는
// resources/schema.sql에서 DDL로 직접 관리한다.
@Getter
@Setter
@NoArgsConstructor
public class DocumentEntity {

    private Long id;

    private String title;

    private String fileName;

    private String fileType; // 'pptx' | 'pdf' | 'ppt'

    private byte[] fileData;

    private String registrant;

    private LocalDateTime registeredAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    private boolean hidden;
}
