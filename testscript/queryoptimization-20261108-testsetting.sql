#	[TEST] DB 설계 및 쿼리 최적화 : DB 설계 및 쿼리 최적화: JPA N+1 문제 탐색 및 DB RTT 최적화
# 	테스트 환경 구축 및 데이터 추가
#	| 2026-11-8 작성

Use simple_board;
show tables;
describe document;
describe member;

# 프로시저 정의
USE simple_board;

SHOW PROCEDURE STATUS WHERE Db = 'simple_board';
SHOW CREATE PROCEDURE InsertDummyMembers;
SHOW CREATE PROCEDURE InsertDummyDocuments;

-- 1. Member 추가 프로시저 (100명)
DROP PROCEDURE IF EXISTS InsertDummyMembers;

DELIMITER //

CREATE PROCEDURE InsertDummyMembers()
BEGIN
    DECLARE i INT DEFAULT 1;
    
    SET AUTOCOMMIT = 0;

    WHILE i <= 100 DO
        INSERT INTO member (name)
        VALUES (CONCAT('테스트회원_', i));

        IF MOD(i, 100) = 0 THEN
            COMMIT;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
    SET AUTOCOMMIT = 1;
END //

DELIMITER ;


-- 2. Document 추가 프로시저 (1,000건 - member_id 1~100번 매핑)
DROP PROCEDURE IF EXISTS InsertDummyDocuments;

DELIMITER //

CREATE PROCEDURE InsertDummyDocuments()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE rand_content VARCHAR(255);
    DECLARE rand_title VARCHAR(255);
    DECLARE rand_member_id BIGINT;
    
    SET AUTOCOMMIT = 0;

    WHILE i <= 1000 DO
        -- 1. title
        SET rand_title = CONCAT('N+1 테스트 게시글 제목 #', i);

        -- 2. content
        SET rand_content = CONCAT('N+1 성능 테스트용 게시글 본문 내용입니다. [ID: ', i, ']');
        
        -- 3. member_id (1 ~ 100번 Member ID 랜덤 매핑)
        SET rand_member_id = FLOOR(1 + (RAND() * 100));

        -- INSERT 실행 (현재 스키마: content, title, member_id)
        INSERT INTO document (content, title, member_id)
        VALUES (rand_content, rand_title, rand_member_id);

        -- 1,000건 커밋
        IF MOD(i, 1000) = 0 THEN
            COMMIT;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
    SET AUTOCOMMIT = 1;
END //

DELIMITER ;

# 순서 지킬 것!
-- 1. member 생성
CALL InsertDummyMembers;

-- 2. document 생성
CALL InsertDummyDocuments;

# 확인 및 초기화
-- member
SELECT COUNT(*) FROM `member`;
SELECT * FROM `member` LIMIT 5;

-- document
SELECT COUNT(*) FROM document;
SELECT * FROM document LIMIT 5;

-- 초기화
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `member`;
TRUNCATE TABLE document;
SET FOREIGN_KEY_CHECKS = 1;
