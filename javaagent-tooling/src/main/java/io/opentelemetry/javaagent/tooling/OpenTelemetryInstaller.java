/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.config.ConfigBuilder;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.spi.ComponentInstaller;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(ComponentInstaller.class)
public class OpenTelemetryInstaller implements ComponentInstaller {
  private static final Logger log = LoggerFactory.getLogger(OpenTelemetryInstaller.class);

  static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";

  @Override
  public void beforeByteBuddyAgent() {
    installAgentTracer();
  }

  /** Register agent tracer if no agent tracer is already registered. */
  @SuppressWarnings("unused")
  public static synchronized void installAgentTracer() {
    if (Config.get().getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true)) {
      copySystemProperties();

      OpenTelemetrySdk sdk = OpenTelemetrySdkAutoConfiguration.initialize();
      OpenTelemetrySdkAccess.internalSetForceFlush(
          (timeout, unit) -> sdk.getSdkTracerProvider().forceFlush().join(timeout, unit));
    } else {
      log.info("Tracing is disabled.");
    }
  }

  // OpenTelemetrySdkAutoConfiguration currently only supports configuration from environment. We
  // massage any properties we have that aren't in the environment to system properties.
  // TODO(anuraaga): Make this less hacky
  private static void copySystemProperties() {
    Properties allProperties = Config.get().asJavaProperties();
    Properties environmentProperties =
        new ConfigBuilder()
            .readEnvironmentVariables()
            .readSystemProperties()
            .build()
            .asJavaProperties();

    allProperties.forEach(
        (key, value) -> {
          String keyStr = (String) key;
          if (!environmentProperties.containsKey(key)
              && !keyStr.equals("otel.endpoint.peer.service.mapping")
              && keyStr.startsWith("otel.")
              && !keyStr.startsWith("otel.instrumentation")) {
            System.setProperty(keyStr, (String) value);
          }
        });

    // Reread after our calls to setProperty to determine whether we need to enforce agent defaults.
    Properties environmentPropertiesWithCopies =
        new ConfigBuilder()
            .readEnvironmentVariables()
            .readSystemProperties()
            .build()
            .asJavaProperties();

    // Agent has different defaults than SDK
    if (!environmentPropertiesWithCopies.containsKey("otel.propagators")) {
      System.setProperty("otel.propagators", "tracecontext,baggage");
    }
    String traceExporter = environmentPropertiesWithCopies.getProperty("otel.trace.exporter");
    if (traceExporter == null) {
      System.setProperty("otel.trace.exporter", "otlp");
    } else if (traceExporter.equals("none")) {
      System.clearProperty("otel.trace.exporter");
    }
    String metricExporter = environmentPropertiesWithCopies.getProperty("otel.metrics.exporter");
    if (metricExporter == null) {
      System.setProperty("otel.metrics.exporter", "otlp");
    } else if (metricExporter.equals("none")) {
      System.clearProperty("otel.metrics.exporter");
    }
  }
}
