/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.extension.noopapi.NoopOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import java.util.Arrays;
import java.util.Map;
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
    if (config.getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true)) {

      copySystemProperties(config);

      if (config.getBooleanProperty(JAVAAGENT_NOOP_CONFIG, false)) {
        GlobalOpenTelemetry.set(NoopOpenTelemetry.getInstance());
      } else {
        OpenTelemetrySdk sdk = OpenTelemetrySdkAutoConfiguration.initialize();
        OpenTelemetrySdkAccess.internalSetForceFlush(
            (timeout, unit) -> {
              CompletableResultCode traceResult = sdk.getSdkTracerProvider().forceFlush();
              CompletableResultCode flushResult = IntervalMetricReader.forceFlushGlobal();
              CompletableResultCode.ofAll(Arrays.asList(traceResult, flushResult))
                  .join(timeout, unit);
            });
      }

    } else {
      logger.info("Tracing is disabled.");
    }
  }

  // OpenTelemetrySdkAutoConfiguration currently only supports configuration from environment. We
  // massage any properties we have that aren't in the environment to system properties.
  // TODO(anuraaga): Make this less hacky
  private static void copySystemProperties(Config config) {
    Map<String, String> allProperties = config.getAllProperties();
    Map<String, String> environmentProperties =
        Config.newBuilder()
            .readEnvironmentVariables()
            .readSystemProperties()
            .build()
            .getAllProperties();

    allProperties.forEach(
        (key, value) -> {
          if (!environmentProperties.containsKey(key)
              && key.startsWith("otel.")
              && !key.startsWith("otel.instrumentation")) {
            System.setProperty(key, value);
          }
        });
  }
}
