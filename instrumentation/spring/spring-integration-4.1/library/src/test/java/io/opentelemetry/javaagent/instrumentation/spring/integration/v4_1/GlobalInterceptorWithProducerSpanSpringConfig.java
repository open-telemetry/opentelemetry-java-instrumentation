/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.integration.v4_1.SpringIntegrationTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptor;

@Configuration
class GlobalInterceptorWithProducerSpanSpringConfig {

  @GlobalChannelInterceptor
  @Bean
  ChannelInterceptor otelInterceptor() {
    return SpringIntegrationTelemetry.builder(GlobalOpenTelemetry.get())
        .setProducerSpanEnabled(true)
        .build()
        .newChannelInterceptor();
  }
}
