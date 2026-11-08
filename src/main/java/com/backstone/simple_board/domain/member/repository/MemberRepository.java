package com.backstone.simple_board.domain.member.repository;

import com.backstone.simple_board.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    @Query("SELECT DISTINCT m FROM Member m JOIN FETCH m.documents")
    List<Member> findAllWithDocuments();
}
