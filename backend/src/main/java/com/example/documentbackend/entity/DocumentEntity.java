package com.example.documentbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_library")
@Getter
@Setter
@NoArgsConstructor
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String fileName;

    private String fileType; // 'pptx' | 'pdf' | 'ppt'

    // JdbcTypeCode로 명시해야 DB마다 다른 기본 매핑(Postgres는 OID, H2는 BLOB 등)
    // 대신 항상 평범한 바이너리 컬럼(H2 BLOB, Postgres bytea)으로 고정된다 -
    // 로컬은 H2, 나중에 배포는 Postgres를 써도 코드 변경 없이 그대로 동작한다.
    // length를 명시하지 않으면 @Column 기본값(255)이 그대로 적용되어 H2에서
    // VARBINARY(255)로 생성된다 - @Lob이 있어도 JdbcTypeCode로 타입을 직접 오버라이드하면
    // 이 기본 길이가 무시되지 않는다(실제로 255바이트 초과 파일 저장 시 재현 확인).
    // Postgres(bytea)는 length를 무시하므로 여기서 큰 값을 줘도 영향 없다.
    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(length = 104_857_600) // 100MB - multipart max-file-size(50MB)보다 여유 있게
    private byte[] fileData;

    private String registrant;

    private LocalDateTime registeredAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    private boolean hidden;
}
