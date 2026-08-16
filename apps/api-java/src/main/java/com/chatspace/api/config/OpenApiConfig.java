package com.chatspace.api.config;

import com.chatspace.api.common.CurrentUser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapiの設定(計画書§7、フェーズ8)。手書きのAPI仕様書は持たず、Swagger UI({@code /swagger-ui.html}) と{@code
 * /v3/api-docs}(生成物は{@code apps/api-java/openapi.json}へコミットし、ドリフト検出の対象とする)を正とする。
 */
@Configuration
public class OpenApiConfig {

  static {
    // @CurrentUserはCookieのJWTから解決するContoller引数であり、リクエストの実パラメータではないため
    // 生成されるスキーマから除外する(HttpServletRequest等の標準除外対象と同じ扱い)
    SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
  }

  @Bean
  public OpenAPI chatSpaceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ChatSpace API")
                .version("v1")
                .description("Slack風チャットアプリ ChatSpace のREST API。詳細はdocs/要件定義書.mdを参照。"));
  }
}
