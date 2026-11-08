#	[TEST] DB 설계 및 쿼리 최적화 : B+ Tree 인덱스 구조별 Scan 범위, Table Access 및 CUD 관리 비용 검증 테스트 스크립트
# 	Test C : 인덱스 유지 관리 비용 
#	| 2026-10-31 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

-- 기본적인 상세 인덱스 조회
SHOW INDEX FROM document;
-- 인덱스 이름과 묶여있는 컬럼 구성만 한눈에 보기 (information_schema)
SELECT 
    index_name,
    GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ', ') AS indexed_columns,
    non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'document'
GROUP BY index_name, non_unique;

#	1. 더미 데이터 삽입 및 조회 쿼리 설정 
-- 1만 건 더미 데이터 삽입 프로시저 
SHOW PROCEDURE STATUS WHERE Db = DATABASE();
DROP PROCEDURE IF EXISTS InsertTestBatch;

DELIMITER //
CREATE PROCEDURE InsertTestBatch()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE startTime DATETIME(6);
    DECLARE endTime DATETIME(6);
    
    SET AUTOCOMMIT = 0;
    
    -- 프로시저 내부에서 정확히 INSERT 시작 직전에 시간 기록
    SET startTime = NOW(6);
    
    WHILE i <= 10000 DO
        INSERT INTO document (category, status, writer_id, view_count, title, content, created_at)
        VALUES (
            ELT(FLOOR(1 + (RAND() * 4)), 'NOTICE', 'FREE', 'QNA', 'EVENT'),
            ELT(FLOOR(1 + (RAND() * 3)), 'DRAFT', 'PUBLISHED', 'DELETED'),
            FLOOR(1 + (RAND() * 1000)),
            FLOOR(RAND() * 5000),
            CONCAT('Batch Insert Test Title #', i),
            CONCAT('Random Text Content Padding ', REPEAT('Padding Data ', 10)),
            NOW()
        );
        SET i = i + 1;
    END WHILE;

    COMMIT;
    SET AUTOCOMMIT = 1;
    
    -- 커밋 직후 종료 시간 기록
    SET endTime = NOW(6);
    
    -- 실행 결과(소요 시간: 초 단위)를 바로 ResultSet으로 출력
    SELECT TIMESTAMPDIFF(MICROSECOND, startTime, endTime) / 1000000.0 AS elapsed_seconds;
END //
DELIMITER ;

-- 테이블 및 인덱스 용량 조회 (Byte & MB 단위)
SELECT 
    table_name,
    data_length AS data_size_bytes,
    index_length AS index_size_bytes,
    ROUND(data_length / 1024 / 1024, 2) AS data_size_mb,
    ROUND(index_length / 1024 / 1024, 2) AS index_size_mb
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'document';

-- InnoDB Page Split / Disk I/O 수치 조회
SHOW GLOBAL STATUS LIKE 'Innodb_pages_written';

#	2. 단계별 측정 수행 
#	2.1. 인덱스 1개 상태(PK만 존재)

-- 기존 인덱스 전체 삭제
DROP INDEX idx_category ON document;
DROP INDEX idx_writer ON document;
DROP INDEX idx_cat_writer ON document;
DROP INDEX idx_writer_cat ON document;
DROP INDEX idx_covering ON document;

-- 데이터 삽입 & 수행 시간 측정
CALL InsertTestBatch();

-- 용량 및 status 확인
SELECT table_name, index_length FROM information_schema.tables WHERE table_name = 'document';
SHOW GLOBAL STATUS LIKE 'Innodb_pages_written';

#	2.2. 인덱스 3개 상태(PK + 단일 2개) 
CREATE INDEX idx_category ON document(category);
CREATE INDEX idx_writer ON document(writer_id);

-- 수행 시간 측정
CALL InsertTestBatch();

SELECT table_name, index_length FROM information_schema.tables WHERE table_name = 'document';
SHOW GLOBAL STATUS LIKE 'Innodb_pages_written';

#	2.3. 인덱스 3개 상태(PK + 단일 2개 + 복합 2개 + 커버링 1개) 
CREATE INDEX idx_cat_writer ON document(category, writer_id);
CREATE INDEX idx_writer_cat ON document(writer_id, category);
CREATE INDEX idx_covering ON document(category, status, writer_id, created_at);

-- 수행 시간 측정
CALL InsertTestBatch();

SELECT table_name, index_length FROM information_schema.tables WHERE table_name = 'document';
SHOW GLOBAL STATUS LIKE 'Innodb_pages_written';
