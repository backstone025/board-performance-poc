package com.backstone.simple_board.domain.document.controller;

import com.backstone.simple_board.domain.document.dto.request.CreateDocRequest;
import com.backstone.simple_board.domain.document.dto.request.UpdateDocRequest;
import com.backstone.simple_board.domain.document.dto.response.*;
import com.backstone.simple_board.domain.document.service.DocumentService;
import com.backstone.simple_board.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocumentController {
    final DocumentService documentService;

    // Create
    @PostMapping
    public ResponseEntity<ApiResponse<CreateDocResponse>> createDoc(
            @RequestBody CreateDocRequest createDocRequest
    ) {
        CreateDocResponse response = documentService.createDoc(createDocRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Read
    @GetMapping
    public ResponseEntity<ApiResponse<GetDocsResponse>> getDocs(
            @RequestParam Integer page
    ) {
        GetDocsResponse response = documentService.getDocs(page);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GetDocByIdResponse>> getDocById(
            @PathVariable Long id
    ) {
        GetDocByIdResponse response = documentService.getDocById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Update
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UpdateDocResponse>> updateDoc(
            @PathVariable Long id,
            @RequestBody UpdateDocRequest updateDocRequest
    ) {
        UpdateDocResponse response = documentService.updateDoc(id, updateDocRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<DeleteDocResponse>> deleteDoc(
            @PathVariable Long id
    ) {
        DeleteDocResponse response = documentService.deleteDoc(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
