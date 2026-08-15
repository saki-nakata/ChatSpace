plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.9.0"
}

group = "com.chatspace"
version = "0.0.1-SNAPSHOT"
description = "ChatSpace backend (Java/Spring Boot redesign)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web / データアクセス / 認証 / バリデーション / マイグレーション(計画書§1のパッケージ構成に対応)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator") // `/actuator/health` を認証・認可設計の `/health` として使う
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT発行・検証(計画書§3: OAuth2 Resource Server自動構成は使わず、Nimbus JOSE+JWTを直接使う)
    implementation("com.nimbusds:nimbus-jose-jwt:10.9.1")

    runtimeOnly("org.postgresql:postgresql")
    // ローカル開発時のみ docker-compose.yml を自動起動する(本番ビルドには含めない)
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // テスト(計画書§1.1・§10: ArchUnitによる3層アーキテクチャ制約テスト、Testcontainersによる統合テスト)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 静的解析方針(計画書§1.1): ./gradlew build に spotlessCheck が自動的に含まれる(checkタスクの依存経由)
spotless {
    java {
        googleJavaFormat()
        target("src/**/*.java")
    }
}
