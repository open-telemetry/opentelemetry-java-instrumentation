/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.HttpMetricsView.activeRequestsView;
import static io.opentelemetry.javaagent.tooling.HttpMetricsView.durationClientView;
import static io.opentelemetry.javaagent.tooling.HttpMetricsView.durationServerView;

import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import java.util.Arrays;

public final class OpenTelemetryInstaller {

  /**
   * Install the {@link OpenTelemetrySdk} using autoconfigure, and return the {@link
   * AutoConfiguredOpenTelemetrySdk}.
   *
   * @return the {@link AutoConfiguredOpenTelemetrySdk}
   */
  public static AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(
      ClassLoader extensionClassLoader) {

    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .setResultAsGlobal(true)
            .addMeterProviderCustomizer((builder, configProperties) -> {
              registerView(builder, "http.client.duration", durationClientView);
              registerView(builder, "http.client.request.size", durationClientView);
              registerView(builder, "http.client.response.size", durationClientView);

              registerView(builder, "http.server.active_requests", activeRequestsView);
              registerView(builder, "http.server.duration", durationServerView);
              registerView(builder, "http.server.request.size", durationServerView);
              registerView(builder, "http.server.response.size", durationServerView);

              return builder;
            })
            .setServiceClassLoader(extensionClassLoader)
            .build();
    OpenTelemetrySdk sdk = autoConfiguredSdk.getOpenTelemetrySdk();

    OpenTelemetrySdkAccess.internalSetForceFlush(
        (timeout, unit) -> {
          CompletableResultCode traceResult = sdk.getSdkTracerProvider().forceFlush();
          CompletableResultCode metricsResult = sdk.getSdkMeterProvider().forceFlush();
          CompletableResultCode logsResult = sdk.getSdkLoggerProvider().forceFlush();
          CompletableResultCode.ofAll(Arrays.asList(traceResult, metricsResult, logsResult))
              .join(timeout, unit);
        });

    return autoConfiguredSdk;
  }

  private static void registerView(SdkMeterProviderBuilder builder, String name, View view) {
    builder.registerView(InstrumentSelector.builder().setMeterName(name).build(), view);
  }

  private OpenTelemetryInstaller() {}
}
