package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.dto.request.CreateDocRequest;
import com.backstone.simple_board.domain.document.dto.request.UpdateDocRequest;
import com.backstone.simple_board.domain.document.dto.response.*;
import com.backstone.simple_board.domain.document.entity.Document;
import com.backstone.simple_board.domain.document.repository.DocumentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private static final int DEFAULT_PAGE_SIZE = 20;

    // Create
    @Transactional
    public CreateDocResponse createDoc(CreateDocRequest createDocRequest) {
        Document doc = Document.create(createDocRequest.title(), createDocRequest.content());
        documentRepository.save(doc);
        return CreateDocResponse.from(doc);
    }

    // Read

    /**
     * 단건 상세 조회
     * - Cache Name : "docDetail"
     * - Cache Key : doc ID
     */
    public GetDocByIdResponse getDocById(Long id) {
        Cache cache = cacheManager.getCache("docDetail");

        // 1. 캐시 조회 시도
        if (cache != null) {
            GetDocByIdResponse cacheResponse = cache.get(id, GetDocByIdResponse.class);
            if (cacheResponse != null) {
                // [Cache HIT]
                meterRegistry.counter("cache_gets_total", "result", "hit", "cache", "docDetail").increment();
                return cacheResponse;
            }
        }

        // 2. [Cache MISS]
        meterRegistry.counter("cache_gets_total", "result", "miss", "cache", "docDetail").increment();

        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서입니다. id=" + id));
        GetDocByIdResponse response = GetDocByIdResponse.from(doc);

        // 3. 캐시 적재
        if (cache != null) {
            cache.put(id, response);
        }

        return response;
    }

    /**
     * 목록 페이징 조회
     * - Cache Name: "docPage"
     * - Cache Key: pageNumber
     */
    public GetDocsResponse getDocs(Integer pageNumber) {
        Cache cache = cacheManager.getCache("docPage");

        // 1. 캐시 조회 시도
        if (cache != null) {
            GetDocsResponse cacheResponse = cache.get(pageNumber, GetDocsResponse.class);
            if (cacheResponse != null) {
                // [Cache HIT]
                meterRegistry.counter("cache_gets_total", "result", "hit", "cache", "docPage").increment();
                return cacheResponse;
            }
        }

        // 2. [Cache MISS]
        meterRegistry.counter("cache_gets_total", "result", "miss", "cache", "docPage").increment();

        Page<Document> documentPage = documentRepository.findAll(PageRequest.of(pageNumber, DEFAULT_PAGE_SIZE));
        GetDocsResponse response = GetDocsResponse.of(documentPage);

        // 3. 캐시 적재
        if (cache != null) {
            cache.put(pageNumber, response);
        }

        return response;
    }

    // Update

    /**
     * 수정 시 해당 단건 상세 조회 캐시 파기
     */
    @Transactional
    @CacheEvict(value = "docDetail", key = "#id")
    public UpdateDocResponse updateDoc(Long id, UpdateDocRequest updateDocRequest) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서입니다. id=" + id));
        doc.update(updateDocRequest.title(), updateDocRequest.content());
        return UpdateDocResponse.from(doc);
    }

    // Delete

    /**
     * 삭제 시 해당 단건 상세 조회 캐시 파기
     */
    @Transactional
    @CacheEvict(value = "docDetail", key = "#id")
    public DeleteDocResponse deleteDoc(Long id) {
        documentRepository.deleteById(id);
        return new DeleteDocResponse(true, id);
    }
}