#	[TEST] DB 설계 및 쿼리 최적화 : 인덱스 이탈(Index Invalidation) 10가지 유발 케이스 분석 및 SARGable 쿼리 리팩토링 PoC
# 	Test B 인덱스 구조 및 조건 결합에 따른 성능 영향 (Index Structural Boundaries)
#	| 2026-11-07 작성

USE simple_board;
SHOW tables;
DESCRIBE document;

# Test Setting

-- 인덱스 이름과 묶여있는 컬럼 구성만 한눈에 보기 (information_schema)
SELECT 
    index_name,
    GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ', ') AS indexed_columns,
    non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'document'
GROUP BY index_name, non_unique;

-- 인덱스 추가 
CREATE INDEX idx_category_status ON document (category, status);

# [Case 1] OR 조건 결합 시 인덱스 무효화 vs UNION ALL 분리 
-- Anti-pattern (OR 조건)
EXPLAIN ANALYZE
SELECT * FROM document
WHERE category = 'NOTICE' OR view_count > 5000;

-- SRAGable (UNION ALL을 통한 인덱스 스캔 분리)
EXPLAIN ANALYZE
SELECT * FROM document WHERE category = 'NOTICE'
UNION ALL
SELECT * FROM document WHERE view_count > 5000 AND category != 'NOTICE';

# [Case 2] 복합 인덱스 LPR(Leftmost Prefix Rule) 위배 vs 단일 인덱스/컬럼 포함 
-- Anti-pattern (복합 인덱스 마지막 컬럼 status 단독 조회)
EXPLAIN ANALYZE
SELECT * FROM document USE INDEX (idx_status)
WHERE `status` = 'ACTIVE';

-- SARGable (단일 인덱스 사용 혹은 선두의 컬럼들 바인딩)
EXPLAIN ANALYZE
SELECT * FROM document
WHERE `status`= 'ACTIVE';
