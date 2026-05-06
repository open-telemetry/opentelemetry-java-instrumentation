/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetryBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Back-channel between the {@code spring-webmvc-5.3} library and the {@code
 * spring-boot-autoconfigure} starter, used to configure the {@code
 * DefaultHttpServerInstrumenterBuilder} held in a private field of {@link
 * SpringWebMvcTelemetryBuilder} without exposing it as public API.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SpringMvcBuilderUtil {

  @Nullable
  private static volatile Function<
          SpringWebMvcTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
      builderExtractor;

  @CanIgnoreReturnValue
  public static SpringWebMvcTelemetryBuilder applyCommonConfig(
      SpringWebMvcTelemetryBuilder builder, OpenTelemetry openTelemetry) {
    // builderExtractor is guaranteed non-null because the builder class registers it during
    // static initialization, before a builder instance can be passed here
    if (builderExtractor != null) {
      builderExtractor.apply(builder).configure(new CommonConfig(openTelemetry));
    }
    return builder;
  }

  public static void setBuilderExtractor(
      Function<
              SpringWebMvcTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
          builderExtractor) {
    SpringMvcBuilderUtil.builderExtractor = builderExtractor;
  }

  private SpringMvcBuilderUtil() {}
}
