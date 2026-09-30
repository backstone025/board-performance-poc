package com.backstone.simple_board.domain.document.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String content;

    public static Document create(String title, String content) {
        Document document = new Document();
        document.title = title;
        document.content = content;
        return  document;
    }

    public void  update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
