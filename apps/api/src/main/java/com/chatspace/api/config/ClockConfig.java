package com.chatspace.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 時刻取得をDI可能にする(フェーズ12)。{@code Instant.now()}を直接呼ぶ実装はテストから時間を進められず、
 * レート制限のウィンドウ経過・ブロック解除を検証するために実時間の{@code Thread.sleep}が必要になってしまうため、 {@link
 * Clock}をBeanとして注入する。テストでは{@code Clock.fixed(...)}へ差し替える。
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
