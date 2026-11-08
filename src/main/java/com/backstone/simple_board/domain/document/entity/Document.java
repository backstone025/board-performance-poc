package com.backstone.simple_board.domain.document.entity;

import com.backstone.simple_board.domain.member.entity.Member;
import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    // PoC #19(2026.11.8): member는 생략한다.
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
