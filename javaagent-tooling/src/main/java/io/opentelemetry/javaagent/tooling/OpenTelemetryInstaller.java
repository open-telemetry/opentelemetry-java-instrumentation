/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.extension.noopapi.NoopOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(AgentListener.class)
public class OpenTelemetryInstaller implements AgentListener {
  private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryInstaller.class);

  static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";
  static final String JAVAAGENT_NOOP_CONFIG = "otel.javaagent.experimental.use-noop-api";

  @Override
  public void beforeAgent(Config config) {
    installAgentTracer(config);
  }

  /**
   * Register agent tracer if no agent tracer is already registered.
   *
   * @param config Configuration instance
   */
  @SuppressWarnings("unused")
  public static synchronized void installAgentTracer(Config config) {
    if (config.getBoolean(JAVAAGENT_ENABLED_CONFIG, true)) {

      if (config.getBoolean(JAVAAGENT_NOOP_CONFIG, false)) {
        GlobalOpenTelemetry.set(NoopOpenTelemetry.getInstance());
      } else {
        System.setProperty("io.opentelemetry.context.contextStorageProvider", "default");

        OpenTelemetrySdk sdk =
            AutoConfiguredOpenTelemetrySdk.builder()
                .setResultAsGlobal(true)
                .addPropertiesSupplier(config::getAllProperties)
                .setServiceClassLoader(AgentInitializer.getAgentClassLoader())
                .build()
                .getOpenTelemetrySdk();
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
      }

    } else {
      logger.info("Tracing is disabled.");
    }
  }
}
