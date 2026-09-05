package com.backstone.simple_board.domain.document.repository;

import com.backstone.simple_board.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
