#	[TEST] DB 설계 및 쿼리 최적화 : B+ Tree 인덱스 구조별 Scan 범위, Table Access 및 CUD 관리 비용 검증 테스트 스크립트
# 	테스트 환경 구축 및 데이터 추가
#	| 2026-10-30 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

# 	1. document 속성 추가
DROP TABLE IF EXISTS document;
CREATE TABLE document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY, -- PK (Clustered Index)
    category    VARCHAR(20) NOT NULL,              -- NOTICE, FREE, QNA, EVENT
    status      VARCHAR(20) NOT NULL,              -- DRAFT, PUBLISHED, DELETED
    writer_id   BIGINT NOT NULL,                   -- 사용자 ID
    view_count  INT NOT NULL DEFAULT 0,            -- 읽은 횟수 -> 범위 검색 및 ORDER BY 테스트목적
    title       VARCHAR(200) NOT NULL,             -- 문서 제목 -> SARGable / LIKE 검색 테스트
    content     TEXT NOT NULL,                     -- 문서 내용 -> Clustered Index Table Access 오버헤드 유발용 (대용량 데이터)
    created_at  DATETIME NOT NULL                  -- 생성일 -> Range Scan & No-Offset / Offset 테스트
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

#	2. 데이터 생성 프로시저 정의
DROP PROCEDURE IF EXISTS InsertDummyDocuments;

-- 프로시저 정의
DELIMITER //

CREATE PROCEDURE InsertDummyDocuments(IN targetCount INT)
BEGIN
    DECLARE i 				INT DEFAULT 1;
    DECLARE rand_cat 		VARCHAR(20);
    DECLARE rand_stat 		VARCHAR(20);
    DECLARE rand_writer 	BIGINT;
    DECLARE rand_view 		INT;
    DECLARE rand_title 		VARCHAR(200);
    DECLARE rand_content 	TEXT;
    DECLARE rand_date 		DATETIME;
    
    -- 트랜잭션 오토커밋 해제하여 속도 향상
    SET AUTOCOMMIT = 0;

    WHILE i <= targetCount DO
        -- category: 카디널리티 낮음 (4개 범주)
        SET rand_cat = ELT(FLOOR(1 + (RAND() * 4)), 'NOTICE', 'FREE', 'QNA', 'EVENT');
        
        -- status: 카디널리티 극도로 낮음 (3개 범주)
        SET rand_stat = ELT(FLOOR(1 + (RAND() * 3)), 'DRAFT', 'PUBLISHED', 'DELETED');
        
        -- writer_id: 카디널리티 높음 (1 ~ 1,000 번 유저)
        SET rand_writer = FLOOR(1 + (RAND() * 1000));
        
        -- view_count: 0 ~ 5,000 범위
        SET rand_view = FLOOR(RAND() * 5000);
        
        -- title & content: SARGable 및 Large Payload(Random Text) 구성
        SET rand_title = CONCAT('성능 최적화 테스트 게시글 제목 #', i, ' [', rand_cat, ']');
        SET rand_content = CONCAT('본문 내용입니다. ', REPEAT('Clustered Index Random I/O 부하 유발용 대용량 텍스트 패딩 데이터입니다. ', 15), ' [ID: ', i, ']');
        
        -- created_at: 최근 1년 이내 무작위 날짜
        SET rand_date = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY);

        INSERT INTO document (category, status, writer_id, view_count, title, content, created_at)
        VALUES (rand_cat, rand_stat, rand_writer, rand_view, rand_title, rand_content, rand_date);

        -- 1,000건마다 커밋
        IF MOD(i, 1000) = 0 THEN
            COMMIT;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
    SET AUTOCOMMIT = 1;
END //

DELIMITER ;

-- dummy data 50,000건 생성
CALL InsertDummyDocuments(50000);

-- dummy data(document 레코드) 초기화 
TRUNCATE TABLE document;

-- 전체 데이터 건수 및 샘플 확인
SELECT COUNT(*) FROM document;
SELECT * FROM document LIMIT 5;

-- 컬럼별 카디널리티(유니크 값 개수) 확인
SELECT 
    COUNT(DISTINCT category) AS cat_cardinality,
    COUNT(DISTINCT status) AS stat_cardinality,
    COUNT(DISTINCT writer_id) AS writer_cardinality
FROM document;

#	3. 셋팅 확인
-- 프로시져
SHOW PROCEDURE STATUS WHERE Db = DATABASE();
SHOW CREATE PROCEDURE InsertDummyData;

-- 전체 데이터 건수 및 샘플 확인 
SELECT COUNT(*) FROM document;
SELECT * FROM document LIMIT 5;

-- 컬럼별 카디널리티(유니크 값 개수) 확인
SELECT 
    COUNT(DISTINCT category) AS cat_cardinality,
    COUNT(DISTINCT status) AS stat_cardinality,
    COUNT(DISTINCT writer_id) AS writer_cardinality
FROM document;