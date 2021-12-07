/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.extension.noopapi.NoopOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import java.util.Arrays;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryInstaller {
  private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryInstaller.class);

  static final String JAVAAGENT_NOOP_CONFIG = "otel.javaagent.experimental.use-noop-api";

  /**
   * Install the {@link OpenTelemetrySdk} using autoconfigure, and return the {@link
   * AutoConfiguredOpenTelemetrySdk}.
   *
   * @return the {@link AutoConfiguredOpenTelemetrySdk}, or null if {@link #JAVAAGENT_NOOP_CONFIG}
   *     is enabled
   */
  @Nullable
  public static synchronized AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(Config config) {
    if (config.getBoolean(JAVAAGENT_NOOP_CONFIG, false)) {
      logger.info("Tracing and metrics are disabled because noop is enabled.");
      GlobalOpenTelemetry.set(NoopOpenTelemetry.getInstance());
      return null;
    }

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
          MeterProvider meterProvider = GlobalMeterProvider.get();
          final CompletableResultCode metricsResult;
          if (meterProvider instanceof SdkMeterProvider) {
            metricsResult = ((SdkMeterProvider) meterProvider).forceFlush();
          } else {
            metricsResult = CompletableResultCode.ofSuccess();
          }
          CompletableResultCode.ofAll(Arrays.asList(traceResult, metricsResult))
              .join(timeout, unit);
        });

    return autoConfiguredSdk;
  }
}
