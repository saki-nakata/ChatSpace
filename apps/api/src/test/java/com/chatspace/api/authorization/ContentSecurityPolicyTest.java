package com.chatspace.api.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.chatspace.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * XSS対策の多層防御として追加したContent-Security-Policyの回帰テスト(利用者からの指摘対応)。
 *
 * <p>Swagger UI(webjar同梱のindex.htmlがインラインscriptを使う開発補助ツール)には適用しないため、そちらは
 * ヘッダーが付かないことも合わせて確認する({@code SecurityConfig}参照)。
 */
class ContentSecurityPolicyTest extends AbstractIntegrationTest {

  @Test
  void login_hasContentSecurityPolicyHeader() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
                        + "font-src 'self'; connect-src 'self'; media-src 'self'; object-src"
                        + " 'none'; base-uri 'self'; form-action 'self'; frame-ancestors"
                        + " 'none'"));
  }

  @Test
  void health_hasContentSecurityPolicyHeader() throws Exception {
    mockMvc.perform(get("/health")).andExpect(header().exists("Content-Security-Policy"));
  }

  @Test
  void apiDocs_hasNoContentSecurityPolicyHeader() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(header().doesNotExist("Content-Security-Policy"));
  }
}
