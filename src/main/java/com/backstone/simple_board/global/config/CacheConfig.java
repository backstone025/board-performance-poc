package com.backstone.simple_board.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Caffeine 캐시 기본 세부 설정
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(10000)                         // 초기 캐시 공간(10,000)
                .maximumSize(100000)                            // 최대 캐시 저장 개수(100,000)
                .expireAfterWrite(1, TimeUnit.HOURS)    // write 후 1시간 뒤 만료(Test 실행 약 21분 동안 유지)
                .recordStats());                                // Prometheus 지표 수집을 위한 캐시 통계 활성화

        return cacheManager;
    }
}
