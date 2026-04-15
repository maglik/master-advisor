# Multi-stage build для оптимального размера образа

# Stage 1: Сборка приложения
FROM gradle:8.4-jdk17 AS build
WORKDIR /app

# Копируем только необходимые файлы для кэширования зависимостей
COPY build.gradle settings.gradle /app/
COPY gradle /app/gradle
RUN gradle dependencies --no-daemon

# Копируем исходный код и собираем JAR
COPY src /app/src
RUN gradle bootJar --no-daemon

# Stage 2: Запуск приложения
FROM openjdk:17-jdk-slim
WORKDIR /app

# Устанавливаем curl для healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Копируем собранный JAR из предыдущего этапа
COPY --from=build /app/build/libs/*.jar app.jar

# Создаём пользователя для безопасности (не root)
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]