/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.integration.v4_1.SpringIntegrationTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.List;
import org.springframework.messaging.support.ChannelInterceptor;

public final class SpringIntegrationSingletons {

  private static final List<String> PATTERNS =
      AgentInstrumentationConfig.get()
          .getList(
              "otel.instrumentation.spring-integration.global-channel-interceptor-patterns",
              singletonList("*"));

  private static final ChannelInterceptor INTERCEPTOR =
      SpringIntegrationTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
          .setProducerSpanEnabled(
              AgentInstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.spring-integration.producer.enabled", false))
          .build()
          .newChannelInterceptor();

  public static String[] patterns() {
    return PATTERNS.toArray(new String[0]);
  }

  public static ChannelInterceptor interceptor() {
    return INTERCEPTOR;
  }

  private SpringIntegrationSingletons() {}
}
