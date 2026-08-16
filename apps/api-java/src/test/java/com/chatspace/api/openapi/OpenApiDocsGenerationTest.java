package com.chatspace.api.openapi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatspace.api.support.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * springdoc-openapiが生成する{@code /v3/api-docs}を{@code apps/api-java/openapi.json}へ書き出す(計画書§7、フェーズ8)。
 *
 * <p>{@code ./gradlew generateOpenApiDocs}がこのテストクラス単体を実行するように{@code build.gradle.kts}で
 * フィルタしている。生成物はGitにコミットし、{@code quality-check}スキルの{@code git diff --exit-code -- openapi.json}
 * によるドリフト検出の対象とする(コントローラ変更時に再生成・コミットし忘れを検知するため)。
 */
class OpenApiDocsGenerationTest extends AbstractIntegrationTest {

  @Test
  void writesOpenApiDocsToRepositoryRoot() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    JsonNode spec = objectMapper.readTree(result.getResponse().getContentAsString());

    assertTrue(spec.has("openapi"), "OpenAPIバージョン情報を含むこと");
    assertTrue(spec.has("paths"), "エンドポイント一覧を含むこと");

    byte[] pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(spec);
    writeFile(Path.of("openapi.json"), pretty);
  }

  private void writeFile(Path path, byte[] content) throws IOException {
    Files.write(path, content);
  }
}
