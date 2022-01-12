/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.sdk.appender.internal.DelegatingLogEmitterProvider;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.instrumentation.api.appender.internal.AgentLogEmitterProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import java.util.Arrays;

public class OpenTelemetryInstaller {

  /**
   * Install the {@link OpenTelemetrySdk} using autoconfigure, and return the {@link
   * AutoConfiguredOpenTelemetrySdk}.
   *
   * @return the {@link AutoConfiguredOpenTelemetrySdk}
   */
  static AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(Config config) {
    System.setProperty("io.opentelemetry.context.contextStorageProvider", "default");

    AutoConfiguredOpenTelemetrySdkBuilder builder =
        AutoConfiguredOpenTelemetrySdk.builder()
            .setResultAsGlobal(true)
            .addPropertiesSupplier(config::getAllProperties);

    ClassLoader classLoader = AgentInitializer.getExtensionsClassLoader();
    if (classLoader != null) {
      // May be null in unit tests.
      builder.setServiceClassLoader(classLoader);
    }

    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk = builder.build();
    OpenTelemetrySdk sdk = autoConfiguredSdk.getOpenTelemetrySdk();

    OpenTelemetrySdkAccess.internalSetForceFlush(
        (timeout, unit) -> {
          CompletableResultCode traceResult = sdk.getSdkTracerProvider().forceFlush();
          CompletableResultCode metricsResult = sdk.getSdkMeterProvider().forceFlush();
          CompletableResultCode.ofAll(Arrays.asList(traceResult, metricsResult))
              .join(timeout, unit);
        });

    SdkLogEmitterProvider sdkLogEmitterProvider = autoConfiguredSdk.getOpenTelemetrySdk()
        .getSdkLogEmitterProvider();
    LogEmitterProvider logEmitterProvider = DelegatingLogEmitterProvider.from(sdkLogEmitterProvider);
    AgentLogEmitterProvider.set(logEmitterProvider);

    return autoConfiguredSdk;
  }
}
