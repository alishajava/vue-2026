package com.example.documentbackend.mapper;

import com.example.documentbackend.entity.DocumentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

// DocumentRepository(JPA)를 대체하는 MyBatis 매퍼. 실제 SQL은 resources/mapper/DocumentMapper.xml에 있다.
// JpaRepository.findById()처럼 Optional을 감싸주지 않으므로, 없는 id 조회 시 컨트롤러에서
// null 체크를 직접 해야 한다(applyRequest 쪽 orElseThrow -> null 체크로 바뀐 이유).
@Mapper
public interface DocumentMapper {

    List<DocumentEntity> findAll();

    DocumentEntity findById(Long id);

    void insert(DocumentEntity doc);

    void update(DocumentEntity doc);

    void deleteById(Long id);
}
