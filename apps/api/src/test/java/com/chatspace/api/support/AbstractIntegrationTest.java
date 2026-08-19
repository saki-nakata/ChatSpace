package com.chatspace.api.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * 認可クリティカルテスト・統合テストの共通基盤。実際のPostgreSQL(Testcontainers)に対して
 * Controller〜Repositoryを通したブラックボックステストを行う(テスト設計書§4)。
 *
 * <p>コンテナはテストクラス間で共有される(Testcontainers公式のsingleton containerパターン)。{@code @Container}
 * は使わず静的初期化ブロックで手動起動する — {@code @Container}を付けると、JUnit5のTestcontainers拡張が
 * 継承元のテストクラスごとに(コンテナが同一インスタンスであるにもかかわらず)afterAllで停止させてしまい、
 * 後続のテストクラスが「接続拒否」で失敗する。JVM終了時にRyuk(リソースリーパー)が自動的に後片付けする。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static {
    POSTGRES.start();
  }

  @Autowired protected MockMvc mockMvc;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired protected AuthorizationTestFixtures fixtures;
}
