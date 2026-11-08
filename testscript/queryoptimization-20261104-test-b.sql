#	[TEST] DB 설계 및 쿼리 최적화 : Offset vs Cursor 페이징의 깊이별 I/O 병목, CUD 정합성(Consistency) 한계 및 커서 키 카디널리티별 탐색 비용 검증
# 	Test B : Offset 페이징의 CUD 발생 시 데이터 정합성(Consistency) 및 단편화 영향 분석
#	| 2026-11-04 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

# 0. 기본적인 Test Setting

-- dummy data 50,000건 생성
CALL InsertDummyDocuments(50000);

-- dummy data(document 레코드) 초기화 
TRUNCATE TABLE document;

-- 전체 데이터 건수 및 샘플 확인
SELECT COUNT(*) FROM document;
SELECT * FROM document LIMIT 5;

# 1. 단편화 제어(Defragmentation)
-- 현재 단편화 상태 및 데이터 페이지 크기 확인
SELECT table_name, data_length, index_length, data_free 
FROM information_schema.tables 
WHERE table_name = 'document';

-- 단편화 제거 (테이블 리빌드 및 B+ Tree 물리 페이지 정렬)
OPTIMIZE TABLE document;

# 2. INSERT 발생 시 중복 노출 확인

-- 1. 1페이지 조회
-- offset
SELECT id, title 
FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 0;
-- cursor
SELECT id, title 
FROM document 
WHERE id < 50001
ORDER BY id DESC 
LIMIT 10 OFFSET 0;

-- 2. 신규 레코드 INSERT
INSERT INTO document (category, status, writer_id, view_count, title, content, created_at) 
VALUES ('FREE', 'ACTIVE', 1, 0, 'New Post', 'Content', NOW());

-- 3. 2페이지 조회
-- offset
SELECT id, title 
FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 10;
-- cursor
SELECT id, title 
FROM document 
WHERE id < 49991
ORDER BY id DESC 
LIMIT 10 OFFSET 0;

# 3. DELETE 발생 시 데이터 누락 확인

-- 1. 1페이지 조회
-- offset
SELECT id, title 
FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 0;
-- cursor
SELECT id, title 
FROM document 
WHERE id < 50001
ORDER BY id DESC 
LIMIT 10 OFFSET 0;

-- 2. 1페이지 내 레코드 49995 삭제
DELETE FROM document WHERE id = 49995;

-- 3. 2페이지 조회
-- offset
SELECT id, title 
FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 10;
-- cursor
SELECT id, title 
FROM document 
WHERE id < 49991
ORDER BY id DESC 
LIMIT 10 OFFSET 0;

# 4. CUD 단편화로 인한 InnoDB 단편화 영향 
-- 상태 측정(clean or After Fagmentation)
-- 현재 단편화 상태 및 데이터 페이지 크기 확인
SELECT table_name, data_length, index_length, data_free 
FROM information_schema.tables 
WHERE table_name = 'document';

-- 깊은 페이지(40,000) offset 수행시간 및 Buffer Pool Read 요청 수 측정
EXPLAIN ANALYZE
SELECT id, title 
FROM document 
ORDER BY id DESC 
LIMIT 10 OFFSET 40000;

-- InnoDB Buffer Pool 상태 확인
SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_reads';

-- CUD 부하 생성
-- 안전모드 해제 
SET SQL_SAFE_UPDATES = 0;

-- 안전모드 재활성화
SET SQL_SAFE_UPDATES = 1;

-- 버퍼 풀 캐시 효과 배제
SET GLOBAL innodb_buffer_pool_dump_now = OFF;

-- 버퍼 풀 캐시 효과 재활성화
SET GLOBAL innodb_buffer_pool_dump_now = ON;

-- 전체 데이터 건수
SELECT COUNT(*) FROM document;


-- id가 5의 배수 -> 전체의 20% 무작위 제거
DELETE FROM document WHERE id % 5 = 0;

-- 기존 없어진 20% 채우기
CALL InsertDummyDocuments(10000);


-- id가 2의 배수 -> 전체의 50% 무작위 제거
DELETE FROM document WHERE id % 2 = 0;

-- 기존 없어진 50% 채우기
CALL InsertDummyDocuments(25000);