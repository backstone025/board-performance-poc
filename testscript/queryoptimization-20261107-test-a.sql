#	[TEST] DB 설계 및 쿼리 최적화 : 인덱스 이탈(Index Invalidation) 10가지 유발 케이스 분석 및 SARGable 쿼리 리팩토링 PoC
# 	Test A 구문적 오류로 인한 인덱스 무효화 패턴 (Syntax Anti-Patterns)
#	| 2026-11-07 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

# Test Setting

-- dummy data 50,000건 생성
CALL InsertDummyDocuments(50000);

-- dummy data(document 레코드) 초기화 
TRUNCATE TABLE document;

-- 전체 데이터 건수 및 샘플 확인
SELECT COUNT(*) FROM document;
SELECT * FROM document LIMIT 5;

-- 인덱스 이름과 묶여있는 컬럼 구성만 한눈에 보기 (information_schema)
SELECT 
    index_name,
    GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ', ') AS indexed_columns,
    non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'document'
GROUP BY index_name, non_unique;

-- 인덱스 생성
DROP INDEX idx_created_at_id ON document;

CREATE INDEX idx_created_at ON document (created_at);
CREATE INDEX idx_title ON document (title);
CREATE INDEX idx_category ON document (category);
CREATE INDEX idx_view_count ON document (view_count);
CREATE INDEX idx_status ON document (`status`);


# [Case 1] 컬럼 가공 및 날짜/시간 함수 사용
-- created_at 고유값 개수
SELECT COUNT(DISTINCT created_at) AS unique_created_at_count FROM document;
-- created_at 전체 교유값 확인 
SELECT created_at, COUNT(*) AS dup_count
FROM document
GROUP BY  created_at
HAVING COUNT(*) > 1
ORDER BY dup_count DESC LIMIT 50;

-- Anti-Pattern
EXPLAIN ANALYZE SELECT * FROM document WHERE DATE(created_at) = '2026-11-05';

-- SARGable
EXPLAIN ANALYZE SELECT * FROM document WHERE created_at >= '2026-11-05 00:00:00' AND created_at < '2026-11-06 00:00:00';

# [Case 2] Leading Wildcard 검색
-- Anti-Pattern
EXPLAIN ANALYZE SELECT * FROM document WHERE title LIKE '%최적화';

-- SARGable
EXPLAIN ANALYZE SELECT * FROM document WHERE title LIKE '최적화%';

# [Case 3] 묵시적 형변환
-- Anti-Pattern
EXPLAIN ANALYZE SELECT * FROM document WHERE category = 100;

-- SARGable
EXPLAIN ANALYZE SELECT * FROM document WHERE category = '100';

# [Case 4] 컬럼 내 산술 연산
-- Anti-Pattern
EXPLAIN ANALYZE SELECT * FROM document WHERE view_count + 100 > 1000;

-- SARGable
EXPLAIN ANALYZE SELECT * FROM document WHERE view_count > 900;

# [Case 5] 부정형 조건 사용
-- Anti-Pattern
EXPLAIN ANALYZE SELECT * FROM document WHERE status != 'DELETED';

-- SARGable
EXPLAIN ANALYZE SELECT * FROM document WHERE status IN ('NOTICE', 'QNA', 'FREE', 'EVENT'); -- 실제 테이블 내 존재하는 나머지 status 값들
