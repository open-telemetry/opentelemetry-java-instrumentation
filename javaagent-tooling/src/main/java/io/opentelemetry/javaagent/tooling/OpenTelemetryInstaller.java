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
import io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;
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

  // Configure histogram metrics similarly to how the SDK will default in 1.5.0 for early feedback.
  @AutoService(SdkMeterProviderConfigurer.class)
  public static final class OpenTelemetryMetricsConfigurer implements SdkMeterProviderConfigurer {

    @Override
    public void configure(SdkMeterProviderBuilder sdkMeterProviderBuilder) {
      sdkMeterProviderBuilder.registerView(
          InstrumentSelector.builder()
              .setInstrumentNameRegex(".*duration")
              .setInstrumentType(InstrumentType.VALUE_RECORDER)
              .build(),
          // Histogram buckets the same as the metrics prototype/prometheus.
          View.builder()
              .setAggregatorFactory(
                  AggregatorFactory.histogram(
                      Arrays.asList(
                          5d, 10d, 25d, 50d, 75d, 100d, 250d, 500d, 750d, 1_000d, 2_500d, 5_000d,
                          7_500d, 10_000d),
                      AggregationTemporality.CUMULATIVE))
              .build());
    }
  }
}
