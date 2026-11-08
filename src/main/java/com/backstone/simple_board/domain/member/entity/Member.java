package com.backstone.simple_board.domain.member.entity;

import com.backstone.simple_board.domain.document.entity.Document;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // Batch size 100으로 고정
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
    private List<Document> documents = new ArrayList<>();

    public Member(String name) {
        this.name = name;
    }
}
