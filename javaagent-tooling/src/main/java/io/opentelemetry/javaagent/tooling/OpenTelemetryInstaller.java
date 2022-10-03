/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.HeliosConfiguration.getCollectorEndpoint;
import static io.opentelemetry.javaagent.tooling.HeliosConfiguration.getHsToken;
import static io.opentelemetry.javaagent.tooling.HeliosConfiguration.getEnvironmentName;
import static io.opentelemetry.javaagent.tooling.HeliosConfiguration.getServiceName;

import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.sdk.appender.internal.DelegatingLogEmitterProvider;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.bootstrap.AgentLogEmitterProvider;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
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

    setHeliosSystemProperties();
    System.out.println(
        String.format("Helios tracing initialized (service: {1}, token: {2}*****, environment: {3})",
            new Object[] {
                getServiceName(),
                getHsToken().substring(0, 3),
                getEnvironmentName()
            })
    );
    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk = builder.build();
    OpenTelemetrySdk sdk = autoConfiguredSdk.getOpenTelemetrySdk();

    OpenTelemetrySdkAccess.internalSetForceFlush(
        (timeout, unit) -> {
          CompletableResultCode traceResult = sdk.getSdkTracerProvider().forceFlush();
          CompletableResultCode metricsResult = sdk.getSdkMeterProvider().forceFlush();
          CompletableResultCode.ofAll(Arrays.asList(traceResult, metricsResult))
              .join(timeout, unit);
        });

    SdkLogEmitterProvider sdkLogEmitterProvider =
        autoConfiguredSdk.getOpenTelemetrySdk().getSdkLogEmitterProvider();
    LogEmitterProvider logEmitterProvider =
        DelegatingLogEmitterProvider.from(sdkLogEmitterProvider);
    AgentLogEmitterProvider.set(logEmitterProvider);

    return autoConfiguredSdk;
  }

  static void setHeliosSystemProperties() {
    String hsToken = getHsToken();

    if (hsToken != null) {
      System.setProperty("otel.exporter.otlp.headers", String.format("Authorization=%s", hsToken));
      System.setProperty("otel.exporter.otlp.traces.endpoint", getCollectorEndpoint());
      System.setProperty("otel.exporter.otlp.traces.protocol", "http/protobuf");
    }
  }
}
