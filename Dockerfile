# 1단계: gradle 빌드 환경
FROM gradle:jdk21 AS builder
WORKDIR /app

# gradle 의존성 캐싱 설정
COPY build.gradle settings.gradle /app/
RUN gradle build --no-daemon || return 0

# 전체 소스 복사 및 jar 빌드
COPY . /app
RUN gradle bootJar --no-daemon -x test

# 2단계: 경량화된 실행 전용 환경(JDK 21)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 단계에서 생성된 jar 파일만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# JVM 메모리 옵션 지정 가능하도록 설정
ENV JAVA_OPTS="-Xms512m -Xmx512m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]