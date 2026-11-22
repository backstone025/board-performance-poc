package com.backstone.simple_board.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    public static final String STREAM_KEY = "stream:document-created";
    public static final String CONSUMER_GROUP = "notification-group";
    public static final String CONSUMER_NAME = "consumer-1";

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis Stream에서 메시지를 비동기적으로 폴링(Polling) 하여 컨슈머에 전달하는 백그라운드 컨테이너 빈 등록
     * - initMethod = start -> 스프링 컨테이너 초기화 완료 시 백그라운드 폴링 스레드 자동 시작
     * - destroyMethod = stop -> 애플리케이션 종료 시 진행 중인 작업 안전하게 마무리하고 스레드 풀 종료
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,  // redis 연결 설정
            StreamListener<String, MapRecord<String, String, String>> notificationStreamConsumer    // 알림 스트림 컨슈머
    ) {
        // 컨테이너가 스트림 구독 전 대상 스트림 키와 컨슈머 그룹이 Redis에 존재하는지 보장
        initStreamAndConsumerGroup();

        // 리스너 컨테이너 동작 옵션 설정
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))    // Redis에 새 메시지 없을 경우 블로킹 상태로 대기하는 최대 시간(100 ms 마다 폴링)
                        .build();

        // Redis 커넥션 팩토리와 옵션을 결합해 실제 메시지 수신 담당할 컨테이너 인스턴스 생성
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        // 특정 컨슈머 그룹 소속 워커로 스트림 구독 등록(내부적으로 XREADGROUP 명령 실행)
        Subscription subscription = container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),                   // 누가 메시지 가져가는가(Redis PEL 기록될 식별자)
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),     // 마지막으로 읽은 오프셋 이후 아무도 안가져간 메시지만 수신
                notificationStreamConsumer                                      // 수동 ACK(컨슈머가 명시적으로 XACK를 호출해야 PEL에서 제거)
        );

        return container;
    }

    private void initStreamAndConsumerGroup() {
        try {
            // MKSTREAM 옵션을 포함한 스트림과 그룹을 함께 생성
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("[Redis Stream 초기화] 스트림 및 컨슈머 그룹 생성 완료: {} / {}",
                    STREAM_KEY, CONSUMER_GROUP);
        } catch (Exception e) {
            log.info("[Redis Stream 초기화] 기존 컨슈머 그룹 유지: {}", CONSUMER_GROUP);
        }
    }
}
