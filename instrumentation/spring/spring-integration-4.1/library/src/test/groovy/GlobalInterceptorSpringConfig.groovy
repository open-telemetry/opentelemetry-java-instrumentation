/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.spring.integration.SpringIntegrationTelemetry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.config.GlobalChannelInterceptor
import org.springframework.messaging.support.ChannelInterceptor

import static java.util.Collections.singletonList

@Configuration
class GlobalInterceptorSpringConfig {

  @GlobalChannelInterceptor
  @Bean
  ChannelInterceptor otelInterceptor() {
    SpringIntegrationTelemetry.builder(GlobalOpenTelemetry.get())
      .setCapturedHeaders(singletonList("test-message-header"))
      .build()
      .newChannelInterceptor()
  }
}
