-- MyBatis는 JPA의 ddl-auto 같은 자동 테이블 생성이 없어서, 이 DDL을 직접 관리해야 한다.
-- Spring Boot가 시작 시 spring.sql.init.mode=always 설정에 따라 이 파일을 자동 실행한다
-- (spring.sql.init.platform=h2 일 때 이 파일이, postgresql일 때 schema-postgresql.sql이 선택됨).
--
-- file_data VARBINARY(104857600): JPA 버전에서 겪었던 "length 생략 시 기본값 255로 잘리는"
-- 문제를 피하기 위해 길이를 명시한다(100MB - multipart max-file-size 50MB보다 여유 있게).
--
-- reference_month: 등록일시(자동 타임스탬프)와 별개로 사용자가 직접 고르는 "이 문서가
-- 어느 년-월 자료인지" 값. "YYYY-MM" 문자열 그대로 저장한다(DATE 타입 대신 - 일(day)
-- 개념이 없고 타임존 변환에 휘둘릴 이유도 없어서 텍스트로 충분하다).
CREATE TABLE IF NOT EXISTS document_library (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    file_name VARCHAR(255),
    file_type VARCHAR(20),
    file_data VARBINARY(104857600),
    registrant VARCHAR(255),
    registered_at TIMESTAMP,
    reference_month VARCHAR(7),
    updated_by VARCHAR(255),
    updated_at TIMESTAMP,
    hidden BOOLEAN NOT NULL DEFAULT FALSE
);
