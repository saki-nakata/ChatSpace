package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code SWAGGER_ENABLED=false}相当の設定で、Swagger UI/OpenAPIエンドポイントが実際に404になることを検証する
 * (レビュー指摘対応)。既定値は{@code true}(fail-open)のため、Render側で環境変数の設定漏れがあると本番で Swagger
 * UIが公開されたままになる。設定自体が効いていることを自動テストで確認する。
 */
@TestPropertySource(
    properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class SwaggerDisabledTest extends AbstractIntegrationTest {

  @Test
  void apiDocs_disabled_returnsNotFound() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
  }

  @Test
  void swaggerUi_disabled_returnsNotFound() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
  }
}
