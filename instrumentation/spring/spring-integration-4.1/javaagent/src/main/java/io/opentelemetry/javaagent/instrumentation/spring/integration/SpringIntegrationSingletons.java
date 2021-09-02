/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.spring.integration.SpringIntegrationTracing;
import java.util.List;
import org.springframework.messaging.support.ChannelInterceptor;

public final class SpringIntegrationSingletons {

  private static final List<String> PATTERNS =
      Config.get()
          .getList(
              "otel.instrumentation.spring-integration.global-channel-interceptor-patterns",
              singletonList("*"));

  private static final ChannelInterceptor INTERCEPTOR =
      SpringIntegrationTracing.create(GlobalOpenTelemetry.get()).newChannelInterceptor();

  public static String[] patterns() {
    return PATTERNS.toArray(new String[0]);
  }

  public static ChannelInterceptor interceptor() {
    return INTERCEPTOR;
  }

  private SpringIntegrationSingletons() {}
}
