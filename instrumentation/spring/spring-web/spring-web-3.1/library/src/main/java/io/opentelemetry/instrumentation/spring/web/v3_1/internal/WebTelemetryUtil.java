/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Back-channel between the {@code spring-web-3.1} library and the {@code spring-boot-autoconfigure}
 * starter, used to configure the {@code DefaultHttpClientInstrumenterBuilder} held in a private
 * field of {@link SpringWebTelemetryBuilder} without exposing it as public API.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class WebTelemetryUtil {
  private WebTelemetryUtil() {}

  @Nullable
  private static volatile Function<
          SpringWebTelemetryBuilder,
          DefaultHttpClientInstrumenterBuilder<HttpRequest, ClientHttpResponse>>
      builderExtractor;

  @CanIgnoreReturnValue
  public static SpringWebTelemetryBuilder applyCommonConfig(
      SpringWebTelemetryBuilder builder, OpenTelemetry openTelemetry) {
    if (builderExtractor != null) {
      builderExtractor.apply(builder).configure(new CommonConfig(openTelemetry));
    }
    return builder;
  }

  public static void setBuilderExtractor(
      Function<
              SpringWebTelemetryBuilder,
              DefaultHttpClientInstrumenterBuilder<HttpRequest, ClientHttpResponse>>
          builderExtractor) {
    WebTelemetryUtil.builderExtractor = builderExtractor;
  }
}
