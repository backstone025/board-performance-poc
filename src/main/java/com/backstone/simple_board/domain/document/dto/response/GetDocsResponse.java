package com.backstone.simple_board.domain.document.dto.response;

import com.backstone.simple_board.domain.document.entity.Document;
import org.springframework.data.domain.Page;

import java.util.List;

public record GetDocsResponse(
        Integer totPage,
        Integer page,
        Integer pageSize,
        List<DocDto> docs
) {
    public static GetDocsResponse of(Page<Document> documents) {
        int totalPages = documents.getTotalPages();
        int pageNumber = documents.getNumber();
        int pageSize = documents.getSize();
        List<DocDto> docs = documents.getContent().stream()
                .map(DocDto::from)
                .toList();
        return new GetDocsResponse(
                totalPages,
                pageNumber,
                pageSize,
                docs
        );
    }

    public record DocDto(
            Long id,
            String title,
            String content
    ) {
        public static DocDto from(Document document) {
            return new DocDto(
                    document.getId(),
                    document.getTitle(),
                    document.getContent()
            );
        }
    }
}
