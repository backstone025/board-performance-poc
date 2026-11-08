package com.backstone.simple_board.domain.document.repository;

import com.backstone.simple_board.domain.member.entity.Member;
import com.backstone.simple_board.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("[Test A] Default Lazy Loading - N+1 문제 유발")
    void testA_defaultLazyLoading() {
        // 영속성 컨텍스트 초기화 (1차 캐시 비우기)
        entityManager.flush();
        entityManager.clear();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 1. Member 100명 전체 조회 (쿼리 1회 발생)
        List<Member> members = memberRepository.findAll();

        // 2. Loop를 돌며 연관 컬렉션 접근 (N+1 문제 발생 -> 쿼리 100회 추가)
        int totalDocumentCount = 0;
        for (Member member : members) {
            totalDocumentCount += member.getDocuments().size();
        }

        stopWatch.stop();

        System.out.println("==================================================");
        System.out.println("조회된 Member 수: " + members.size());
        System.out.println("총 Document 수: " + totalDocumentCount);
        System.out.println("Test A 실행 시간: " + stopWatch.getTotalTimeMillis() + " ms");
        System.out.println("==================================================");
    }

    @Test
    @Transactional
    @DisplayName("[Test B] JPQL Fetch Join - N+1 문제 해결")
    void testB_fetchJoin(){
        // 1. 영속성 컨텍스트 초기화 (1차 캐시 비우기)
        entityManager.flush();
        entityManager.clear();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 2. Fetch Join 쿼리 실행 (쿼리 1회 발생)
        List<Member> members = memberRepository.findAllWithDocuments();

        // 3. 연관된 documents 컬렉션 접근
        int totalDocumentCount = 0;
        for (Member member : members) {
            totalDocumentCount += member.getDocuments().size();
        }

        System.out.println("==================================================");
        System.out.println("조회된 Member 수: " + members.size());
        System.out.println("총 Document 수: " + totalDocumentCount);
        System.out.println("Test B 실행 시간: " + stopWatch.getTotalTimeMillis() + " ms");
        System.out.println("==================================================");
    }

    @Test
    @Transactional
    @DisplayName("[Test C] default_batch_fetch_size: 100 - IN절 배치 최적화")
    void testC_batchFetchSize() {
        // 1. 영속성 컨텍스트 초기화 (1차 캐시 비우기)
        entityManager.flush();
        entityManager.clear();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 2. Member 100명 전체 조회 (쿼리 1회 발생)
        List<Member> members = memberRepository.findAll();

        // 3. Loop를 돌며 연관 컬렉션 접근
        int totalDocumentCount = 0;
        for (Member member : members) {
            totalDocumentCount += member.getDocuments().size();
        }

        stopWatch.stop();

        System.out.println("==================================================");
        System.out.println("조회된 Member 수: " + members.size());
        System.out.println("총 Document 수: " + totalDocumentCount);
        System.out.println("Test C 실행 시간: " + stopWatch.getTotalTimeMillis() + " ms");
        System.out.println("==================================================");
    }
}