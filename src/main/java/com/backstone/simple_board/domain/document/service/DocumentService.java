package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.dto.request.CreateDocRequest;
import com.backstone.simple_board.domain.document.dto.request.UpdateDocRequest;
import com.backstone.simple_board.domain.document.dto.response.*;
import com.backstone.simple_board.domain.document.entity.Document;
import com.backstone.simple_board.domain.document.repository.DocumentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Value("${app.cache.target-hit-rate:1.0}")
    private double targetHitRate;

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
        double rand = ThreadLocalRandom.current().nextDouble();
        boolean shouldBypassCache = rand >= targetHitRate;

        Cache cache = cacheManager.getCache("docDetail");

        // 1. 캐시 조회 시도 (바이패스 대상이 아니고, 캐시 매니저가 존재할 때)
        if (!shouldBypassCache && cache != null) {
            GetDocByIdResponse cacheResponse = cache.get(id, GetDocByIdResponse.class);
            if (cacheResponse != null) {
                // [Cache HIT]
                meterRegistry.counter("cache_gets_total", "result", "hit", "cache", "docDetail").increment();
                return cacheResponse;
            }
        }

        // 2. [Cache MISS] (또는 바이패스)
        meterRegistry.counter("cache_gets_total", "result", "miss", "cache", "docDetail").increment();

        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서입니다. id=" + id));
        GetDocByIdResponse response = GetDocByIdResponse.from(doc);

        // 3. 캐시 적재 (바이패스가 아니었고, 캐시가 비어있어 DB 조회한 경우)
        if (!shouldBypassCache && cache != null) {
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
        double rand = ThreadLocalRandom.current().nextDouble();
        boolean shouldBypassCache = rand >= targetHitRate;

        Cache cache = cacheManager.getCache("docPage");

        // 1. 캐시 조회 시도 (70% 비중의 페이징 트래픽 캐시 적용)
        if (!shouldBypassCache && cache != null) {
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
        if (!shouldBypassCache && cache != null) {
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
