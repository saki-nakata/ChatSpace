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

    // OpenAPI自動生成(計画書§7: packages/shared廃止の代替。Spring Boot 4.1系に対応するv3.1.0を使用)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

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

// OpenApiDocsGenerationTestは openapi.json への書き込みという副作用を持つため、通常の `test`/`build` では実行しない
// (明示的に `generateOpenApiDocs` を実行した時のみ動く、ドキュメント生成専用のタスクとして切り離す)。
tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("com.chatspace.api.openapi.OpenApiDocsGenerationTest")
    }
}

// OpenAPI生成物の書き出し(計画書§7、フェーズ8)。openapi.json はコミット対象とし、quality-checkスキルの
// `git diff --exit-code -- openapi.json` によるドリフト検出(コントローラ変更時の再生成・コミット漏れ検知)に使う。
// OpenApiDocsGenerationTest 単体だけをTestcontainers Postgresを起動して実行する(通常の `test`/`build` には含めない)。
tasks.register<Test>("generateOpenApiDocs") {
    group = "documentation"
    description = "springdoc-openapiが生成するAPI仕様をopenapi.jsonへ書き出す"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.chatspace.api.openapi.OpenApiDocsGenerationTest")
    }
}

// STOMP宛先名の契約テスト用JSON書き出し(計画書§7、フェーズ8)。apps/web-next(フェーズ9)のVitestテストが
// このJSONとdestinations.tsの定数を突き合わせる。Springコンテキスト起動が不要なためJavaExecで直接mainを実行する。
tasks.register<JavaExec>("exportStompDestinations") {
    group = "documentation"
    description = "StompDestinationsの宛先名をJSONへ書き出す(フロントエンドとの契約テスト用)"
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.chatspace.api.realtime.StompDestinationsExporter")
    args(layout.buildDirectory.file("generated/stomp-destinations.json").get().asFile.absolutePath)
}

// 静的解析方針(計画書§1.1): ./gradlew build に spotlessCheck が自動的に含まれる(checkタスクの依存経由)
spotless {
    java {
        googleJavaFormat()
        target("src/**/*.java")
    }
}
