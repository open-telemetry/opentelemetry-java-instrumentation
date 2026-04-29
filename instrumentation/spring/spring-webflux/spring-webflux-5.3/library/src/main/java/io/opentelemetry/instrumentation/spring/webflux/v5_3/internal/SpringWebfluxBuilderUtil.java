/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetryBuilder;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * Back-channel between the {@code spring-webflux-5.3} library and the {@code
 * spring-boot-autoconfigure} starter, used to configure the {@code
 * DefaultHttp{Client,Server}InstrumenterBuilder} held in private fields of {@link
 * SpringWebfluxClientTelemetryBuilder} / {@link SpringWebfluxServerTelemetryBuilder} without
 * exposing them as public API.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SpringWebfluxBuilderUtil {
  @Nullable
  private static volatile Function<
          SpringWebfluxClientTelemetryBuilder,
          DefaultHttpClientInstrumenterBuilder<ClientRequest, ClientResponse>>
      clientBuilderExtractor;

  @Nullable
  private static volatile Function<
          SpringWebfluxServerTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<ServerWebExchange, ServerWebExchange>>
      serverBuilderExtractor;

  @CanIgnoreReturnValue
  public static SpringWebfluxClientTelemetryBuilder applyClientCommonConfig(
      SpringWebfluxClientTelemetryBuilder builder, OpenTelemetry openTelemetry) {
    // clientBuilderExtractor is guaranteed non-null because the builder class registers it during
    // static initialization, before a builder instance can be passed here
    if (clientBuilderExtractor != null) {
      clientBuilderExtractor.apply(builder).configure(new CommonConfig(openTelemetry));
    }
    return builder;
  }

  @CanIgnoreReturnValue
  public static SpringWebfluxServerTelemetryBuilder applyServerCommonConfig(
      SpringWebfluxServerTelemetryBuilder builder, OpenTelemetry openTelemetry) {
    // serverBuilderExtractor is guaranteed non-null because the builder class registers it during
    // static initialization, before a builder instance can be passed here
    if (serverBuilderExtractor != null) {
      serverBuilderExtractor.apply(builder).configure(new CommonConfig(openTelemetry));
    }
    return builder;
  }

  public static void setServerBuilderExtractor(
      Function<
              SpringWebfluxServerTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<ServerWebExchange, ServerWebExchange>>
          serverBuilderExtractor) {
    SpringWebfluxBuilderUtil.serverBuilderExtractor = serverBuilderExtractor;
  }

  public static void setClientBuilderExtractor(
      Function<
              SpringWebfluxClientTelemetryBuilder,
              DefaultHttpClientInstrumenterBuilder<ClientRequest, ClientResponse>>
          clientBuilderExtractor) {
    SpringWebfluxBuilderUtil.clientBuilderExtractor = clientBuilderExtractor;
  }

  private SpringWebfluxBuilderUtil() {}
}
