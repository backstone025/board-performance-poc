package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.dto.request.CreateDocRequest;
import com.backstone.simple_board.domain.document.dto.request.UpdateDocRequest;
import com.backstone.simple_board.domain.document.dto.response.*;
import com.backstone.simple_board.domain.document.entity.Document;
import com.backstone.simple_board.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;
    private static final int DEFAULT_PAGE_SIZE = 20;

    // Create
    @Transactional
    public CreateDocResponse createDoc(CreateDocRequest createDocRequest) {
        Document doc = Document.create(createDocRequest.title(), createDocRequest.content());
        documentRepository.save(doc);
        return CreateDocResponse.from(doc);
    }

    // Read
    public GetDocByIdResponse getDocById(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서입니다. id=" + id));
        return GetDocByIdResponse.from(doc);
    }

    public GetDocsResponse getDocs(Integer pageNumber) {
        Page<Document> documentPage = documentRepository.findAll(PageRequest.of(pageNumber, DEFAULT_PAGE_SIZE));
        return GetDocsResponse.of(documentPage);
    }

    // Update
    @Transactional
    public UpdateDocResponse updateDoc(Long id, UpdateDocRequest updateDocRequest) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서입니다. id=" + id));
        doc.update(updateDocRequest.title(), updateDocRequest.content());
        return UpdateDocResponse.from(doc);
    }

    // Delete
    @Transactional
    public DeleteDocResponse deleteDoc(Long id) {
        documentRepository.deleteById(id);
        return new DeleteDocResponse(true, id);
    }
}
